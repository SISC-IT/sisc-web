package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.DailyClosePrice;
import org.sejongisc.backend.competition.entity.Market;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V6 마이그레이션으로 만든 테이블에 엔티티·레포지토리가 맞는지 검증한다.
 * 운영과 같은 hbm2ddl=validate로 띄우므로, 엔티티와 V6가 어긋나면 컨텍스트 로딩 단계에서 실패한다.
 */
@SpringJUnitConfig(CompetitionPriceRepositoryTest.JpaTestConfig.class)
@Transactional
class CompetitionPriceRepositoryTest {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("sisc_test")
      .withUsername("sisc")
      .withPassword("sisc");

  private static final LocalDate THU = LocalDate.of(2026, 11, 5);
  private static final LocalDate FRI = LocalDate.of(2026, 11, 6);
  private static final LocalDate MON = LocalDate.of(2026, 11, 9);
  private static final LocalTime OPEN = LocalTime.of(16, 30);

  @Autowired CompetitionStockRepository stockRepository;
  @Autowired DailyClosePriceRepository priceRepository;
  @Autowired EntityManager entityManager;

  @Test
  void 저장한_종목과_종가를_그대로_다시_읽는다() {
    CompetitionStock samsung = saveStock("005930");
    DailyClosePrice saved = savePrice(samsung, MON, "71000.1234");
    entityManager.flush();
    entityManager.clear(); // 1차 캐시가 아니라 DB에서 다시 읽도록

    DailyClosePrice found = priceRepository.findById(saved.getClosePriceId()).orElseThrow();
    assertThat(found.getPriceDate()).isEqualTo(MON);
    assertThat(found.getAvailableDate()).isEqualTo(MON.atTime(OPEN));
    assertThat(found.getClosePrice()).isEqualByComparingTo("71000.1234");
    assertThat(found.getStock().getTicker()).isEqualTo("005930");
    assertThat(found.getCreatedDate()).isNotNull();

    CompetitionStock stock = stockRepository.findByTicker("005930").orElseThrow();
    assertThat(stock.getName()).isEqualTo("종목005930");
    assertThat(stock.getMarket()).isEqualTo(Market.KR);
    assertThat(stockRepository.findByTicker("999999")).isEmpty();

    assertThat(priceRepository.existsByStockAndPriceDate(stock, MON)).isTrue();
    assertThat(priceRepository.existsByStockAndPriceDate(stock, FRI)).isFalse();
  }

  @Test
  void 최신_종가와_직전_거래일_종가를_최신순으로_가져온다() {
    CompetitionStock samsung = saveStock("005930");
    CompetitionStock hynix = saveStock("000660");
    savePrice(samsung, THU, "100");
    savePrice(samsung, FRI, "101");
    savePrice(samsung, MON, "102"); // 토·일은 행이 없다
    savePrice(hynix, MON, "999");

    List<DailyClosePrice> result = latestTwo(samsung, MON.atTime(17, 0));

    assertThat(result).extracting(DailyClosePrice::getPriceDate).containsExactly(MON, FRI);
  }

  @Test
  void 거래_가능_시각_전에는_오늘_종가가_나오지_않는다() {
    CompetitionStock samsung = saveStock("005930");
    savePrice(samsung, THU, "100");
    savePrice(samsung, FRI, "101");
    savePrice(samsung, MON, "102");

    assertThat(latestTwo(samsung, MON.atTime(16, 29, 59)))
        .extracting(DailyClosePrice::getPriceDate).containsExactly(FRI, THU);
    assertThat(latestTwo(samsung, MON.atTime(OPEN)))
        .extracting(DailyClosePrice::getPriceDate).containsExactly(MON, FRI);
  }

  @Test
  void 종가가_1건이면_1건_없으면_빈_결과다() {
    CompetitionStock samsung = saveStock("005930");
    CompetitionStock hynix = saveStock("000660");
    savePrice(samsung, MON, "102");

    assertThat(latestTwo(samsung, MON.atTime(17, 0))).hasSize(1);
    assertThat(latestTwo(hynix, MON.atTime(17, 0))).isEmpty();
  }

  @Test
  void 같은_종목_같은_날짜_종가는_두_번_저장할_수_없다() {
    CompetitionStock samsung = saveStock("005930");
    savePrice(samsung, MON, "102");

    assertThatThrownBy(() -> savePrice(samsung, MON, "103"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 같은_티커는_두_번_저장할_수_없다() {
    saveStock("005930");

    assertThatThrownBy(() -> saveStock("005930"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 종가가_있는_종목은_삭제할_수_없다() {
    CompetitionStock samsung = saveStock("005930");
    savePrice(samsung, MON, "102");
    entityManager.clear(); // 영속성 컨텍스트가 종가를 들고 있으면 Hibernate가 DELETE를 보내지 않아 DB 제약까지 가지 않는다

    assertThatThrownBy(() -> {
      stockRepository.deleteById(samsung.getStockId());
      stockRepository.flush();
    }).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 종가는_0일_수_없다() {
    CompetitionStock samsung = saveStock("005930");

    assertThatThrownBy(() -> savePrice(samsung, MON, "0"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private CompetitionStock saveStock(String ticker) {
    return stockRepository.saveAndFlush(CompetitionStock.builder()
        .ticker(ticker)
        .name("종목" + ticker)
        .market(Market.KR)
        .build());
  }

  private DailyClosePrice savePrice(CompetitionStock stock, LocalDate priceDate, String closePrice) {
    return priceRepository.saveAndFlush(DailyClosePrice.builder()
        .stock(stock)
        .priceDate(priceDate)
        .availableDate(priceDate.atTime(OPEN))
        .closePrice(new BigDecimal(closePrice))
        .build());
  }

  private List<DailyClosePrice> latestTwo(CompetitionStock stock, LocalDateTime now) {
    return priceRepository.findTop2ByStockAndAvailableDateLessThanEqualOrderByAvailableDateDesc(stock, now);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaAuditing
  @EnableJpaRepositories(basePackageClasses = DailyClosePriceRepository.class)
  static class JpaTestConfig {

    @Bean
    DataSource dataSource() {
      if (!POSTGRES.isRunning()) {
        POSTGRES.start();
      }
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
      // V1 기본 스키마가 레포에 없어 전체 마이그레이션은 빈 DB에서 실패한다. V6는 다른 테이블을 참조하지 않으므로 단독 실행한다.
      new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__competition_stock_price.sql"))
          .execute(dataSource);
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
}
