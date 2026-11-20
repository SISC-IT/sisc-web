package org.sejongisc.backend.competition.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sejongisc.backend.competition.entity.CompetitionStock;
import org.sejongisc.backend.competition.entity.Market;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(CompetitionJpaTestConfig.class)
@Transactional
class CompetitionStockSearchRepositoryTest {

  private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by("ticker"));

  @Autowired CompetitionStockRepository stockRepository;

  @BeforeEach
  void setUp() {
    saveStock("005930", "삼성전자", Market.KR);
    saveStock("005935", "삼성전자우", Market.KR);
    saveStock("000660", "SK하이닉스", Market.KR);
    saveStock("AAPL", "Apple Inc.", Market.US);
  }

  @Test
  void 검색어가_없으면_전체_종목을_티커순으로_가져온다() {
    Page<CompetitionStock> result = stockRepository.search(null, "%%", FIRST_PAGE);

    assertThat(result.getTotalElements()).isEqualTo(4);
    assertThat(result).extracting(CompetitionStock::getTicker)
        .containsExactly("000660", "005930", "005935", "AAPL");
  }

  @Test
  void 회사명이나_티커_일부로_검색한다() {
    assertThat(stockRepository.search(null, "%삼성%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("005930", "005935");
    assertThat(stockRepository.search(null, "%0059%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("005930", "005935");
  }

  @Test
  void 대소문자를_구분하지_않는다() {
    assertThat(stockRepository.search(null, "%apple%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("AAPL");
    assertThat(stockRepository.search(null, "%aapl%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("AAPL");
    assertThat(stockRepository.search(null, "%sk%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("000660");
  }

  @Test
  void 시장을_지정하면_그_시장_종목만_가져온다() {
    assertThat(stockRepository.search(Market.US, "%%", FIRST_PAGE))
        .extracting(CompetitionStock::getTicker).containsExactly("AAPL");
    assertThat(stockRepository.search(Market.KR, "%apple%", FIRST_PAGE)).isEmpty();
  }

  @Test
  void 페이지를_나눠도_전체_건수는_그대로다() {
    Page<CompetitionStock> second = stockRepository.search(null, "%%", PageRequest.of(1, 3, Sort.by("ticker")));

    assertThat(second.getTotalElements()).isEqualTo(4);
    assertThat(second).extracting(CompetitionStock::getTicker).containsExactly("AAPL");
  }

  private void saveStock(String ticker, String name, Market market) {
    stockRepository.saveAndFlush(CompetitionStock.builder()
        .ticker(ticker)
        .name(name)
        .market(market)
        .build());
  }
}
