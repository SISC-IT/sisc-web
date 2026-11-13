package org.sejongisc.backend.competition.repository;

import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;
import javax.sql.DataSource;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 수익률 대결 마이그레이션(V6, V7)을 그대로 실행한 DB에 운영과 같은 hbm2ddl=validate로 엔티티를 띄운다.
 * 엔티티와 마이그레이션이 어긋나면 컨텍스트 로딩 단계에서 실패한다.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing
@EnableJpaRepositories(basePackageClasses = CompetitionJpaTestConfig.class)
class CompetitionJpaTestConfig {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("sisc_test")
      .withUsername("sisc")
      .withPassword("sisc");

  @Bean
  DataSource dataSource() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    // V1 기본 스키마가 레포에 없어 전체 마이그레이션은 빈 DB에서 실패한다.
    // V7이 FK로 참조하는 users는 PK만 있는 최소 테이블로 대신한다.
    new ResourceDatabasePopulator(
        new ByteArrayResource("create table users (user_id uuid primary key);".getBytes()),
        new ClassPathResource("db/migration/V6__competition_stock_price.sql"),
        new ClassPathResource("db/migration/V7__competition_participant_account.sql")
    ).execute(dataSource);
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
    factory.setPackagesToScan(CompetitionStock.class.getPackageName());

    Properties jpaProperties = new Properties();
    jpaProperties.put("hibernate.hbm2ddl.auto", "validate"); // 운영과 같은 검사
    // Spring Boot 기본값과 같게: closePriceId -> close_price_id
    jpaProperties.put("hibernate.physical_naming_strategy",
        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
    factory.setJpaProperties(jpaProperties);
    return factory;
  }

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
