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
    public void execute(BacktestRun backtestRun) {
        Long backtestRunId = backtestRun.getId();
        log.info("백테스팅 실행이 시작됩니다. 실행 ID : {}", backtestRunId);
        // 거래 로그 리스트 초기화
        List<TradeLog> tradeLogs = new ArrayList<>();
        try {
            // 백테스팅 상태 RUNNING 으로 변경
            backtestRun.setStatus(BacktestStatus.RUNNING);
            backtestRun.setStartedAt(LocalDateTime.now());
            backtestRunRepository.save(backtestRun);
            log.debug("백테스팅 상태 RUNNING 으로 변경됨. ID : {}", backtestRunId);

            // 백테스팅 파라미터 로드
            BacktestRunRequest strategyDto = objectMapper.readValue(backtestRun.getParamsJson(), BacktestRunRequest.class);
            String ticker = strategyDto.getTicker();
            log.debug("백테스팅 대상 티커: {}", ticker);

            // 가격 데이터 로드
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

            // 백테스팅 시뮬레이션 변수 초기화
            BigDecimal initialCapital = strategyDto.getInitialCapital();    // 초기 자본금
            BigDecimal cash = initialCapital;                               // 잔고 = 초기 자본금
            BigDecimal shares = BigDecimal.ZERO;                            // 보유 주식 수
            int tradesCount = 0;                                            // 총 거래 횟수
            List<BigDecimal> dailyPortfolioValue = new ArrayList<>();       // MDD 및 수익률 추적용 리스트
            List<BigDecimal> dailyReturns = new ArrayList<>();              // 일일 수익률 리스트 (샤프 비율 계산에 사용)
            BigDecimal peakValue = initialCapital;                          // 최고 포트폴리오 가치
            BigDecimal maxDrawdown = BigDecimal.ZERO;                       // 최대 낙폭
            BigDecimal previousValue = initialCapital;                      // 전날 포트폴리오 가치
            BigDecimal buyRatio = convertPercentToRatio(10, BigDecimal.ONE);    // TODO : DTO 매수 비중 설정
            BigDecimal sellRatio = convertPercentToRatio(100, BigDecimal.ONE);   // TODO : DTO 매도 비중 설정
            Integer buyBarIndex = null;	                                    // 현재 보유 주식의 매수 시점 바(Bar) 인덱스
            int defaultExitDays = strategyDto.getDefaultExitDays();	        // 기본 청산 기간
            // 백테스팅 메인 반복문
            for (int i = 0; i < series.getBarCount(); i++) {
                LocalDateTime currentTime = series.getBar(i).getEndTime().toLocalDateTime();                // 장 종료 시간
                BigDecimal currentClosePrice = new BigDecimal(series.getBar(i).getClosePrice().toString()); // 현재 종가
                // 매수/매도 신호 평가
                boolean shouldBuy = buyRule.isSatisfied(i);
                boolean shouldSell = sellRule.isSatisfied(i);
                // 기본 청산 기간(Default Exit Days) 조건
                boolean shouldExitByDays = false;
                // 주식을 보유하고 있고, 매수 시점 기록이 있으며, 청산 기간이 0보다 큰 경우에만 체크
                if (shares.compareTo(BigDecimal.ZERO) > 0 && buyBarIndex != null && defaultExitDays > 0) {
                    if (i - buyBarIndex >= defaultExitDays) {
                        shouldExitByDays = true; // 매수 후 기본 청산 기간 도달
                        log.debug("[{}] DEFAULT EXIT by {} days", currentTime.toLocalDate(), defaultExitDays);
                    }
                }
                // 매수
                if (shouldBuy) {
                    BigDecimal cashToUse = cash.multiply(buyRatio); // 매수 비중 적용
                    // 거래 가능한 최대 주식 개수
                    BigDecimal buyShares = cashToUse.divide(currentClosePrice, 8, RoundingMode.DOWN);
                    // 거래 로그 기록
                    if (buyShares.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal transactionCost = buyShares.multiply(currentClosePrice);
                        tradeLogs.add(new TradeLog(TradeLog.Type.BUY, currentTime, currentClosePrice, buyShares));
                        shares = shares.add(buyShares);         // 매수 주식 수
                        cash = cash.subtract(transactionCost);  // 잔고에서 매수 대금 차감
                        tradesCount++;                          // 거래 횟수 증가
                        if (buyBarIndex == null) {
                            buyBarIndex = i;                    // 첫 매수 시점에만 인덱스 기록
                        }
                        log.debug("[{}] BUY at {}", currentTime.toLocalDate(), currentClosePrice);
                    }
                }
                // 매도
                else if (shares.compareTo(BigDecimal.ZERO) > 0 && (shouldSell || shouldExitByDays)) {
                    // 매도 대금 계산 - 주식 수 * 현재가
                    BigDecimal sharesToSell = shares.multiply(sellRatio).setScale(8, RoundingMode.DOWN);   // 매도 비중 적용
                    BigDecimal tradeValue = sharesToSell.multiply(currentClosePrice);
                    // 거래 로그 기록
                    TradeLog.Type logType = shouldExitByDays ? TradeLog.Type.SELL_FORCED : TradeLog.Type.SELL;  // 강제 청산 여부에 따른 로그 타입 설정
                    tradeLogs.add(new TradeLog(logType, currentTime, currentClosePrice, sharesToSell));
                    shares = shares.subtract(sharesToSell);     // 매도 주식 수 차감
                    cash = cash.add(tradeValue);                // 잔고에서 매도 대금 추가
                    tradesCount++;                              // 거래 횟수 증가
                    buyBarIndex = null;                         // 매도 시점 인덱스 초기화
                    log.debug("[{}] SELL at {}", currentTime.toLocalDate(), currentClosePrice);
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
                // 최대 낙폭 계산
                if (currentTotalValue.compareTo(peakValue) > 0) peakValue = currentTotalValue;
                BigDecimal drawdown = peakValue.subtract(currentTotalValue).divide(peakValue, 8, RoundingMode.HALF_UP);
                if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;
            }
            // 백테스팅 메인 반복문 종료

            // 자산 곡선 JSON 변환
            String assetCurveJson = objectMapper.writeValueAsString(dailyPortfolioValue);
            // 최종 지표 계산 및 저장
            backtestRunMetricsRepository.save(
                calculateMetrics(backtestRun, initialCapital, tradeLogs, dailyPortfolioValue, dailyReturns, maxDrawdown, tradesCount, assetCurveJson)
            );
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
                                                List<BigDecimal> dailyReturns, BigDecimal maxDrawdown, int tradesCount, String assetCurveJson) {
        // 총 수익률 계산 - 백분율로 변환
        BigDecimal totalReturnPct = dailyPortfolioValue.getLast()       // 최종 포트폴리오 가치
            .divide(initialCapital, 8, RoundingMode.HALF_UP)      // 초기 자본 대비 비율, 소수점 8자리 반올림
            .subtract(BigDecimal.ONE)                                   // 비율 (0.10)
            .multiply(BigDecimal.valueOf(100))                          // 백분율 (10.00)
            .setScale(4, RoundingMode.HALF_UP);                // 소수점 4자리 반올림
        // 최대 낙폭 백분율 변환 - -100 곱한 후 소수점 4자리 반올림
        BigDecimal maxDrawdownPct = maxDrawdown.multiply(BigDecimal.valueOf(-100)).setScale(4, RoundingMode.HALF_UP);
        // 샤프 비율 계산
        BigDecimal sharpeRatio = calculateSharpeRatio(dailyReturns);
        // 평균 보유 기간 계산
        BigDecimal avgHoldDays = calculateAvgHoldDays(tradeLogs);

        return BacktestRunMetrics.fromDto(backtestRun, totalReturnPct, maxDrawdownPct, sharpeRatio, avgHoldDays, tradesCount, assetCurveJson);
    }

    private BigDecimal calculateSharpeRatio(List<BigDecimal> dailyReturns) {
        if (dailyReturns.isEmpty()) return BigDecimal.ZERO;
        // 일일 수익률의 합계와 평균 계산
        BigDecimal sum = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(dailyReturns.size()), 8, RoundingMode.HALF_UP);

        // 분산, 표준편차 계산
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

        // 매수-매도 쌍을 찾아 보유 기간 계산
        for (TradeLog log : tradeLogs) {
            if (log.type == TradeLog.Type.BUY) {
                currentBuyTime = log.time;
            } else if ((log.type == TradeLog.Type.SELL || log.type == TradeLog.Type.SELL_FORCED) && currentBuyTime != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(currentBuyTime.toLocalDate(), log.time.toLocalDate());
                holdDurations.add(days);
                currentBuyTime = null;
            }
        }
        if (holdDurations.isEmpty()) return BigDecimal.ZERO;
        // 총 기간 합산
        long totalDays = holdDurations.stream().reduce(0L, Long::sum);
        // 평균 보유 일수 계산 후 소수점 2자리 반올림
        return BigDecimal.valueOf(totalDays)
          .divide(BigDecimal.valueOf(holdDurations.size()), 2, RoundingMode.HALF_UP);
    }

    // ----------------------------------------------------------------------
    // 퍼센티지(int)를 소수점 비율(BigDecimal)로 변환하는 헬퍼 함수
    // ----------------------------------------------------------------------
    private BigDecimal convertPercentToRatio(Integer percent, BigDecimal defaultValue) {
        if (percent == null || percent < 0 || percent > 100) {
            // 유효하지 않은 값이거나 null일 경우 기본값(1.00 또는 정의된 값) 반환
            return defaultValue;
        }
        // 정수 %를 BigDecimal로 변환 후 100으로 나누어 비율을 만듦
        // (예: 10 -> 0.10)
        return BigDecimal.valueOf(percent)
            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}