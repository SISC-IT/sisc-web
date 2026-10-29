package org.sejongisc.backend.board.repository.performance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.board.entity.Board;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.repository.PostRepository;
import org.sejongisc.backend.common.auth.entity.UserOauthAccount;
import org.sejongisc.backend.common.entity.postgres.BasePostgresEntity;
import org.sejongisc.backend.user.entity.Role;
import org.sejongisc.backend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringJUnitConfig(PostRepositoryPerformanceTest.JpaTestConfig.class)
@Transactional
class PostRepositoryPerformanceTest {

  private static final Logger log = LoggerFactory.getLogger(PostRepositoryPerformanceTest.class);

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("sisc_test")
      .withUsername("sisc")
      .withPassword("sisc");

  private static final int POST_COUNT = 100;

  private static void startPostgres() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
  }


  @Autowired
  private TestPostRepository postRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  private Board board;
  private Statistics statistics;

  @BeforeEach
  void setUp() {
    statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);

    LocalDateTime now = LocalDateTime.now();
    User boardOwner = user("owner", "게시판 관리자", now);
    entityManager.persist(boardOwner);

    board = Board.builder()
        .boardName("성능 테스트 게시판")
        .createdBy(boardOwner)
        .build();
    applyAuditFields(board, now);
    entityManager.persist(board);

    for (int i = 0; i < POST_COUNT; i++) {
      User writer = user("writer" + i, "작성자 " + i, now.plusSeconds(i + 1));
      entityManager.persist(writer);

      Post post = Post.builder()
          .board(board)
          .user(writer)
          .title("게시글 " + i)
          .content("본문 " + i)
          .contentText("본문 " + i)
          .likeCount(i)
          .commentCount(0)
          .bookmarkCount(0)
          .build();
      applyAuditFields(post, now.plusMinutes(i + 1));
      entityManager.persist(post);
    }

    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("게시글 목록 조회 - PostgreSQL에서 기존 lazy 조회 대비 fetch join 성능 비교")
  void findAllByBoard_FetchJoin_ReducesSqlStatementsComparedToLegacyShape() {
    Pageable pageable = PageRequest.of(0, POST_COUNT);

    warmUp(pageable);

    QueryMeasurement legacy = measure(() -> postRepository.findAllByBoardWithoutFetchJoin(board, pageable));
    entityManager.clear();
    QueryMeasurement fetchJoin = measure(() -> postRepository.findAllByBoard(board, pageable));

    printResult(legacy, fetchJoin);

    assertThat(fetchJoin.postCount()).isEqualTo(POST_COUNT);
    assertThat(legacy.postCount()).isEqualTo(POST_COUNT);
    assertThat(legacy.statementCount()).isGreaterThanOrEqualTo(POST_COUNT + 2L);
    assertThat(fetchJoin.statementCount()).isLessThanOrEqualTo(2L);
    assertThat(fetchJoin.statementCount()).isLessThan(legacy.statementCount());
  }

  private void warmUp(Pageable pageable) {
    measure(() -> postRepository.findAllByBoardWithoutFetchJoin(board, pageable));
    entityManager.clear();
    measure(() -> postRepository.findAllByBoard(board, pageable));
    entityManager.clear();
  }

  private QueryMeasurement measure(Supplier<Page<Post>> query) {
    statistics.clear();
    long startedAt = System.nanoTime();
    Page<Post> posts = query.get();
    touchPostResponseFields(posts.getContent());
    double elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000.0;
    return new QueryMeasurement(posts.getContent().size(), statistics.getPrepareStatementCount(), elapsedMillis);
  }

  private void printResult(QueryMeasurement legacy, QueryMeasurement fetchJoin) {
    log.info("[PostRepositoryPerformanceTest] 기존 lazy 조회: {} ms, SQL {}회, posts {}건",
        formatMillis(legacy.elapsedMillis()), legacy.statementCount(), legacy.postCount());
    log.info("[PostRepositoryPerformanceTest] fetch join 조회: {} ms, SQL {}회, posts {}건",
        formatMillis(fetchJoin.elapsedMillis()), fetchJoin.statementCount(), fetchJoin.postCount());
    log.info("[PostRepositoryPerformanceTest] 시간 차이: {} ms, SQL 감소: {}회",
        formatMillis(legacy.elapsedMillis() - fetchJoin.elapsedMillis()),
        legacy.statementCount() - fetchJoin.statementCount());
  }

  private String formatMillis(double millis) {
    return String.format("%.3f", millis);
  }

  private void touchPostResponseFields(List<Post> posts) {
    posts.forEach(post -> {
      post.getPostId();
      post.getTitle();
      post.getCreatedDate();
      post.getBoard().getBoardName();
      post.getUser().getName();
    });
  }

  private User user(String studentIdSuffix, String name, LocalDateTime timestamp) {
    User user = User.builder()
        .studentId("2026" + studentIdSuffix)
        .name(name)
        .role(Role.TEAM_MEMBER)
        .point(0)
        .build();
    applyAuditFields(user, timestamp);
    return user;
  }

  private void applyAuditFields(BasePostgresEntity entity, LocalDateTime timestamp) {
    entity.setCreatedDate(timestamp);
    entity.setUpdatedDate(timestamp);
  }

  private record QueryMeasurement(int postCount, long statementCount, double elapsedMillis) {
  }

  interface TestPostRepository extends PostRepository {

    @Query(
        value = "select p from Post p where p.board = :board",
        countQuery = "select count(p) from Post p where p.board = :board"
    )
    Page<Post> findAllByBoardWithoutFetchJoin(@Param("board") Board board, Pageable pageable);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = PostRepositoryPerformanceTest.class,
      considerNestedRepositories = true
  )
  static class JpaTestConfig {

    @Bean
    DataSource dataSource() {
      startPostgres();

      DriverManagerDataSource dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName(POSTGRES.getDriverClassName());
      dataSource.setUrl(POSTGRES.getJdbcUrl());
      dataSource.setUsername(POSTGRES.getUsername());
      dataSource.setPassword(POSTGRES.getPassword());
      return dataSource;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
      HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
      vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");

      LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setJpaVendorAdapter(vendorAdapter);
      factory.setEntityManagerFactoryInterface(EntityManagerFactory.class);
      factory.setPackagesToScan(
          Post.class.getPackageName(),
          Board.class.getPackageName(),
          User.class.getPackageName(),
          UserOauthAccount.class.getPackageName()
      );

      Properties jpaProperties = new Properties();
      jpaProperties.put("hibernate.hbm2ddl.auto", "create-drop");
      jpaProperties.put("hibernate.generate_statistics", "true");
      jpaProperties.put("hibernate.session.events.log", "false");
      jpaProperties.put("hibernate.show_sql", "false");
      jpaProperties.put("hibernate.format_sql", "false");
      factory.setJpaProperties(jpaProperties);
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }
  }
}
