package org.sejongisc.backend.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.dto.BacktestRunRequest;
import org.sejongisc.backend.backtest.entity.BacktestRun;
import org.sejongisc.backend.backtest.entity.BacktestRunMetrics;
import org.sejongisc.backend.backtest.entity.BacktestStatus;
import org.sejongisc.backend.backtest.repository.BacktestRunMetricsRepository;
import org.sejongisc.backend.backtest.repository.BacktestRunRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.entity.PriceData;
import org.sejongisc.backend.stock.repository.PriceDataRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestingEngine {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestRunMetricsRepository backtestRunMetricsRepository;
    private final PriceDataRepository priceDataRepository;
    private final Ta4jHelperService ta4jHelper;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void execute(Long backtestRunId) {
        log.info("백테스팅 실행이 시작됩니다. 실행 ID : {}", backtestRunId);
        BacktestRun backtestRun = backtestRunRepository.findById(backtestRunId)
            .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_NOT_FOUND));
        try {
            backtestRun.setStatus(BacktestStatus.RUNNING);
            backtestRun.setStartedAt(LocalDateTime.now());
            backtestRunRepository.save(backtestRun);
            log.debug("백테스팅 상태 RUNNING 으로 변경됨. ID : {}", backtestRunId);

            // 전략(JSON)을 DTO로 파싱
            log.debug("paramsJson: {}", backtestRun.getParamsJson());
            BacktestRunRequest strategyDto = objectMapper.readValue(backtestRun.getParamsJson(), BacktestRunRequest.class);
            String ticker = strategyDto.getTicker();
            log.debug("백테스팅 대상 티커: {}", ticker);

            // DB에서 가격 데이터 로드
            List<PriceData> priceDataList = priceDataRepository.findByTickerAndDateBetweenOrderByDateAsc(
                ticker, backtestRun.getStartDate(), backtestRun.getEndDate());
            log.debug("가격 데이터 로드 완료. 데이터 개수: {}", priceDataList.size());
            if (priceDataList.isEmpty()) {
                throw new CustomException(ErrorCode.PRICE_DATA_NOT_FOUND);
            }

            // Ta4j BarSeries 생성
            BarSeries series = ta4jHelper.createBarSeries(priceDataList);
            Map<String, Indicator<Num>> indicatorCache = new HashMap<>();
            log.debug("BarSeries 생성 완료. 바 개수: {}", series.getBarCount());

            // 매수/매도 룰 생성
            Rule buyRule = ta4jHelper.buildCombinedRule(strategyDto.getBuyConditions(), series, indicatorCache);
            Rule sellRule = ta4jHelper.buildCombinedRule(strategyDto.getSellConditions(), series, indicatorCache);

            // 포트폴리오 초기화
            BigDecimal initialCapital = strategyDto.getInitialCapital();
            BigDecimal cash = initialCapital;
            BigDecimal shares = BigDecimal.ZERO;
            int tradesCount = 0;

            // MDD 및 수익률 추적용 리스트
            List<BigDecimal> dailyPortfolioValue = new ArrayList<>();
            BigDecimal peakValue = initialCapital;
            BigDecimal maxDrawdown = BigDecimal.ZERO;

            for (int i = 0; i < series.getBarCount(); i++) {
                // 오늘 날짜의 종가 가져오기 (Num -> BigDecimal)
                Num numClosePrice = series.getBar(i).getClosePrice(); // Num 객체 반환
                BigDecimal currentClosePrice = new BigDecimal(numClosePrice.toString());

                // 전략 평가
                boolean shouldBuy = buyRule.isSatisfied(i);
                boolean shouldSell = sellRule.isSatisfied(i);

                // 거래 실행 및 포트폴리오 관리
                // "매수"
                if (shares.compareTo(BigDecimal.ZERO) == 0 && shouldBuy) {
                    shares = cash.divide(currentClosePrice, 8, RoundingMode.HALF_UP);
                    cash = BigDecimal.ZERO;
                    tradesCount++;
                    log.debug("[{}] BUY at {}", series.getBar(i).getEndTime().toLocalDate(), currentClosePrice);

                }
                // "매도"
                else if (shares.compareTo(BigDecimal.ZERO) > 0 && shouldSell) {
                    cash = shares.multiply(currentClosePrice);
                    shares = BigDecimal.ZERO;
                    log.debug("[{}] SELL at {}", series.getBar(i).getEndTime().toLocalDate(), currentClosePrice);
                }
                // 일일 포트폴리오 가치 계산
                BigDecimal currentTotalValue = cash.add(shares.multiply(currentClosePrice));
                dailyPortfolioValue.add(currentTotalValue);
                if (currentTotalValue.compareTo(peakValue) > 0) peakValue = currentTotalValue;
                BigDecimal drawdown = peakValue.subtract(currentTotalValue).divide(peakValue, 4, RoundingMode.HALF_UP);
                // MDD 갱신
                if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;
            }
            // 최종 지표 계산
            BigDecimal finalPortfolioValue = dailyPortfolioValue.getLast();
            // 총수익률 = (최종자산 / 초기자본) - 1
            BigDecimal totalReturnPct = finalPortfolioValue.divide(initialCapital, 4, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
            // MDD (백분율로 변환)
            BigDecimal maxDrawdownPct = maxDrawdown.multiply(BigDecimal.valueOf(-100));

            BacktestRunMetrics metrics = BacktestRunMetrics.builder()
                .backtestRun(backtestRun)
                .totalReturn(totalReturnPct)
                .maxDrawdown(maxDrawdownPct)
                .sharpeRatio(BigDecimal.ZERO) // TODO: Sharpe 계산 (일일 수익률 표준편차 필요)
                .avgHoldDays(BigDecimal.ZERO) // TODO: 평균 보유일 계산 (거래 로그 필요)
                .tradesCount(tradesCount)
                .build();

            backtestRunMetricsRepository.save(metrics);
            backtestRun.setStatus(BacktestStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Backtest execution failed for run ID: {}", backtestRunId, e);
            backtestRun.setStatus(BacktestStatus.FAILED);
            backtestRun.setErrorMessage(e.getMessage());
        } finally {
            backtestRun.setFinishedAt(LocalDateTime.now());
            backtestRunRepository.save(backtestRun);
        }
    }
}
