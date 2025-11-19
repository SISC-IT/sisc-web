package org.sejongisc.backend.backtest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.backtest.dto.BacktestRunRequest;
import org.sejongisc.backend.backtest.dto.TradeLog;
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
    //@Transactional(propagation = Propagation.REQUIRES_NEW) @Async는 새로운 쓰레드에서 실행되므로 DB 작업 수행 시 주석 제거 필요
    public void execute(Long backtestRunId) {
        log.info("백테스팅 실행이 시작됩니다. 실행 ID : {}", backtestRunId);
        BacktestRun backtestRun = backtestRunRepository.findById(backtestRunId)
            .orElseThrow(() -> new CustomException(ErrorCode.BACKTEST_NOT_FOUND));
        // 거래 로그 리스트 초기화
        List<TradeLog> tradeLogs = new ArrayList<>();

        try {
            backtestRun.setStatus(BacktestStatus.RUNNING);
            backtestRun.setStartedAt(LocalDateTime.now());
            backtestRunRepository.save(backtestRun);
            log.debug("백테스팅 상태 RUNNING 으로 변경됨. ID : {}", backtestRunId);

            log.debug("paramsJson: {}", backtestRun.getParamsJson());
            BacktestRunRequest strategyDto = objectMapper.readValue(backtestRun.getParamsJson(), BacktestRunRequest.class);
            String ticker = strategyDto.getTicker();
            log.info("백테스팅 대상 티커: {}", ticker);

            List<PriceData> priceDataList = priceDataRepository.findByTickerAndDateBetweenOrderByDateAsc(
                ticker, backtestRun.getStartDate(), backtestRun.getEndDate());
            log.info("가격 데이터 로드 완료. 데이터 개수: {}", priceDataList.size());
            if (priceDataList.isEmpty()) {
                throw new CustomException(ErrorCode.PRICE_DATA_NOT_FOUND);
            }

            BarSeries series = ta4jHelper.createBarSeries(priceDataList);
            Map<String, Indicator<Num>> indicatorCache = new HashMap<>();
            log.debug("BarSeries 생성 완료. 바 개수: {}", series.getBarCount());

            Rule buyRule = ta4jHelper.buildCombinedRule(strategyDto.getBuyConditions(), series, indicatorCache);
            Rule sellRule = ta4jHelper.buildCombinedRule(strategyDto.getSellConditions(), series, indicatorCache);

            BigDecimal initialCapital = strategyDto.getInitialCapital();
            BigDecimal cash = initialCapital;
            BigDecimal shares = BigDecimal.ZERO;
            int tradesCount = 0;

            // MDD 및 수익률 추적용 리스트
            List<BigDecimal> dailyPortfolioValue = new ArrayList<>();
            // 일일 수익률 리스트 (샤프 비율 계산에 사용)
            List<BigDecimal> dailyReturns = new ArrayList<>();

            BigDecimal peakValue = initialCapital;
            BigDecimal maxDrawdown = BigDecimal.ZERO;
            BigDecimal previousValue = initialCapital; // 전날 포트폴리오 가치

            for (int i = 0; i < series.getBarCount(); i++) {
                LocalDateTime currentTime = series.getBar(i).getEndTime().toLocalDateTime();
                Num numClosePrice = series.getBar(i).getClosePrice();
                BigDecimal currentClosePrice = new BigDecimal(numClosePrice.toString());

                boolean shouldBuy = buyRule.isSatisfied(i);
                boolean shouldSell = sellRule.isSatisfied(i);

                // "매수"
                if (shares.compareTo(BigDecimal.ZERO) == 0 && shouldBuy) {
                    BigDecimal buyShares = cash.divide(currentClosePrice, 8, RoundingMode.HALF_UP);

                    // 거래 로그 기록
                    tradeLogs.add(new TradeLog(TradeLog.Type.BUY, currentTime, currentClosePrice, buyShares));

                    shares = buyShares;
                    cash = BigDecimal.ZERO;
                    tradesCount++;
                    log.info("[{}] BUY at {}", currentTime.toLocalDate(), currentClosePrice);
                }
                // "매도"
                else if (shares.compareTo(BigDecimal.ZERO) > 0 && shouldSell) {
                    BigDecimal tradeShares = shares; // 매도 주식 수
                    BigDecimal tradeValue = shares.multiply(currentClosePrice);

                    // 거래 로그 기록
                    tradeLogs.add(new TradeLog(TradeLog.Type.SELL, currentTime, currentClosePrice, tradeShares));

                    cash = tradeValue;
                    shares = BigDecimal.ZERO;
                    log.info("[{}] SELL at {}", currentTime.toLocalDate(), currentClosePrice);
                }

                // 일일 포트폴리오 가치 계산
                BigDecimal currentTotalValue = cash.add(shares.multiply(currentClosePrice));
                dailyPortfolioValue.add(currentTotalValue);

                // 일일 수익률 계산 및 MDD 갱신
                if (i > 0) {
                    BigDecimal dailyReturn = currentTotalValue.subtract(previousValue)
                        .divide(previousValue, 8, RoundingMode.HALF_UP);
                    dailyReturns.add(dailyReturn);
                }
                previousValue = currentTotalValue;

                if (currentTotalValue.compareTo(peakValue) > 0) peakValue = currentTotalValue;
                BigDecimal drawdown = peakValue.subtract(currentTotalValue).divide(peakValue, 8, RoundingMode.HALF_UP);
                if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;
            }

            // 최종 지표 계산 및 저장
            BacktestRunMetrics metrics = calculateMetrics(backtestRun, initialCapital, tradeLogs, dailyPortfolioValue, dailyReturns, maxDrawdown, tradesCount);

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

    // ----------------------------------------------------------------------
    // 지표 계산 보조 메서드
    // ----------------------------------------------------------------------
    private BacktestRunMetrics calculateMetrics(BacktestRun backtestRun, BigDecimal initialCapital,
                                                List<TradeLog> tradeLogs, List<BigDecimal> dailyPortfolioValue,
                                                List<BigDecimal> dailyReturns, BigDecimal maxDrawdown, int tradesCount) {

        BigDecimal finalPortfolioValue = dailyPortfolioValue.getLast();
        BigDecimal totalReturnPct = finalPortfolioValue.divide(initialCapital, 4, RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE);

        BigDecimal maxDrawdownPct = maxDrawdown.multiply(BigDecimal.valueOf(-100)).setScale(4, RoundingMode.HALF_UP);

        BigDecimal sharpeRatio = calculateSharpeRatio(dailyReturns);
        BigDecimal avgHoldDays = calculateAvgHoldDays(tradeLogs);

        return BacktestRunMetrics.builder()
            .backtestRun(backtestRun)
            .totalReturn(totalReturnPct)
            .maxDrawdown(maxDrawdownPct)
            .sharpeRatio(sharpeRatio)
            .avgHoldDays(avgHoldDays)
            .tradesCount(tradesCount)
            .build();
    }

    private BigDecimal calculateSharpeRatio(List<BigDecimal> dailyReturns) {
        if (dailyReturns.isEmpty()) return BigDecimal.ZERO;

        BigDecimal sum = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(dailyReturns.size()), 8, RoundingMode.HALF_UP);

        BigDecimal varianceSum = dailyReturns.stream()
            .map(r -> r.subtract(mean))
            .map(d -> d.multiply(d))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(dailyReturns.size()), 8, RoundingMode.HALF_UP);
        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        if (standardDeviation.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        // 연율화 샤프 비율 (거래일 기준 252일 가정)
        BigDecimal annualizationFactor = BigDecimal.valueOf(Math.sqrt(252));
        BigDecimal sharpeRatio = mean.divide(standardDeviation, 8, RoundingMode.HALF_UP).multiply(annualizationFactor);

        return sharpeRatio.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAvgHoldDays(List<TradeLog> tradeLogs) {
        List<Long> holdDurations = new ArrayList<>();
        LocalDateTime currentBuyTime = null;

        for (TradeLog log : tradeLogs) {
            if (log.type == TradeLog.Type.BUY) {
                currentBuyTime = log.time;
            } else if (log.type == TradeLog.Type.SELL && currentBuyTime != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(currentBuyTime.toLocalDate(), log.time.toLocalDate());
                holdDurations.add(days);
                currentBuyTime = null;
            }
        }

        if (holdDurations.isEmpty()) return BigDecimal.ZERO;

        long totalDays = holdDurations.stream().reduce(0L, Long::sum);
        BigDecimal avgHoldDays = BigDecimal.valueOf(totalDays)
            .divide(BigDecimal.valueOf(holdDurations.size()), 2, RoundingMode.HALF_UP);

        return avgHoldDays;
    }
}