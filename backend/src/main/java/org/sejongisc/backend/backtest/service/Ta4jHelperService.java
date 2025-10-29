package org.sejongisc.backend.backtest.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.backtest.dto.StrategyCondition;
import org.sejongisc.backend.backtest.dto.StrategyOperand;
import org.sejongisc.backend.stock.entity.PriceData;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CachedIndicator; // ⭐️ MACD Hist 구현용
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.*; // IsEqualRule, AndRule, OrRule, OverIndicatorRule 등

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Ta4jHelperService {

    /**
     * PriceData 리스트를 ta4j의 BarSeries로 변환합니다.
     */
    public BarSeries createBarSeries(List<PriceData> priceDataList) {
        // ⭐️ (수정) BarSeries 이름에 Ticker 추가
        BarSeries series = new BaseBarSeries(priceDataList.get(0).getTicker());
        for (PriceData p : priceDataList) {
            series.addBar(
                p.getDate().atStartOfDay(ZoneId.systemDefault()),
                p.getOpen(), p.getHigh(), p.getLow(), p.getClosePrice(), p.getVolume()
            );
        }
        return series;
    }

    /**
     * DTO 조건(List<StrategyCondition>)을 ta4j의 Rule 객체로 빌드합니다.
     * "isAbsolute" 로직(✳️무조건 OR ⚪️일반)을 포함합니다.
     */
    public Rule buildCombinedRule(List<StrategyCondition> conditions, BarSeries series,
                                  Map<String, Indicator<Num>> indicatorCache) {

        if (series.isEmpty()) {
            throw new IllegalArgumentException("Cannot build rules on an empty series.");
        }

        // ⭐️ (수정) "1"과 "0"에 해당하는 Num 객체를 시리즈에서 가져옴
        Num sampleNum = series.getBar(0).getClosePrice();
        Num one = sampleNum.numOf(1);
        Num zero = sampleNum.numOf(0);

        // ⭐️ (수정) "1"과 "0"에 해당하는 Indicator를 '먼저' 생성
        Indicator<Num> indicatorOne = new ConstantIndicator<>(series, one);
        Indicator<Num> indicatorZero = new ConstantIndicator<>(series, zero);

        // "FalseRule" 대체: "1 == 0" 규칙 (항상 false)
        Rule falseRule = new IsEqualRule(indicatorOne, indicatorZero);
        // "TrueRule" 대체: "1 == 1" 규칙 (항상 true)
        Rule trueRule = new IsEqualRule(indicatorOne, indicatorOne);

        if (conditions == null || conditions.isEmpty()) {
            return falseRule; // 조건이 없으면 항상 false
        }

        // 1. ✳️ '무조건' 조건과 ⚪️ '일반' 조건으로 분리
        Map<Boolean, List<StrategyCondition>> partitioned = conditions.stream()
            .collect(Collectors.partitioningBy(StrategyCondition::isAbsolute));

        List<StrategyCondition> absoluteConditions = partitioned.get(true);
        List<StrategyCondition> standardConditions = partitioned.get(false);

        Rule absoluteRule;
        Rule standardRule;

        // 2. ✳️ '무조건' 조건들을 OR로 묶음
        if (absoluteConditions.isEmpty()) {
            absoluteRule = falseRule; // ✳️ 조건 없음
        } else {
            Rule combinedOrRule = buildSingleRule(absoluteConditions.get(0), series, indicatorCache);
            for (int i = 1; i < absoluteConditions.size(); i++) {
                combinedOrRule = new OrRule(
                    buildSingleRule(absoluteConditions.get(i), series, indicatorCache),
                    combinedOrRule
                );
            }
            absoluteRule = combinedOrRule;
        }

        // 3. ⚪️ '일반' 조건들을 AND로 묶음
        if (standardConditions.isEmpty()) {
            standardRule = falseRule; // ⚪️ 조건 없음
        } else {
            Rule combinedAndRule = buildSingleRule(standardConditions.get(0), series, indicatorCache);
            for (int i = 1; i < standardConditions.size(); i++) {
                combinedAndRule = new AndRule(
                    buildSingleRule(standardConditions.get(i), series, indicatorCache),
                    combinedAndRule
                );
            }
            standardRule = combinedAndRule;
        }

        // 4. 최종 결합: (✳️무조건 OR ⚪️일반)
        return new OrRule(absoluteRule, standardRule);
    }

    /**
     * 개별 조건(StrategyCondition)을 ta4j Rule 객체로 변환
     */
    private Rule buildSingleRule(StrategyCondition condition, BarSeries series,
                                 Map<String, Indicator<Num>> indicatorCache) {

        Indicator<Num> left = resolveOperand(condition.getLeftOperand(), series, indicatorCache);
        Indicator<Num> right = resolveOperand(condition.getRightOperand(), series, indicatorCache);

        // ⭐️ (스크린샷 0.13 버전 규칙 기준)
        switch (condition.getOperator()) {
            case "GT":
                return new OverIndicatorRule(left, right);
            case "GTE":
                return new IsEqualRule(left, right).or(new OverIndicatorRule(left, right));
            case "LT":
                return new UnderIndicatorRule(left, right);
            case "LTE":
                return new IsEqualRule(left, right).or(new UnderIndicatorRule(left, right));
            case "EQ":
                return new IsEqualRule(left, right);
            case "CROSSES_ABOVE":
                return new CrossedUpIndicatorRule(left, right);
            case "CROSSES_BELOW":
                return new CrossedDownIndicatorRule(left, right);
            default:
                throw new IllegalArgumentException("Unknown operator: " + condition.getOperator());
        }
    }

    /**
     * StrategyOperand DTO를 ta4j Indicator 객체로 "번역"
     */
    private Indicator<Num> resolveOperand(StrategyOperand operand, BarSeries series,
                                          Map<String, Indicator<Num>> indicatorCache) {
        if (operand == null) return null;

        String key = generateIndicatorKey(operand);
        if (indicatorCache.containsKey(key)) {
            return indicatorCache.get(key);
        }

        Indicator<Num> indicator;
        switch (operand.getType()) {
            case "price":
                indicator = createPriceIndicator(operand.getPriceField(), series);
                break;
            case "indicator":
                indicator = createIndicator(operand, series, indicatorCache);
                break;
            case "const":
                Num constValue = series.getBar(0).getClosePrice().numOf(operand.getConstantValue());
                indicator = new ConstantIndicator<>(series, constValue);
                break;
            default:
                throw new IllegalArgumentException("Unknown operand type: " + operand.getType());
        }

        indicatorCache.put(key, indicator);
        return indicator;
    }

    // 팩토리 헬퍼 1: 원본 가격 지표 생성
    private Indicator<Num> createPriceIndicator(String field, BarSeries series) {
        switch (field) {
            case "Open":
                return new OpenPriceIndicator(series);
            case "High":
                return new HighPriceIndicator(series);
            case "Low":
                return new LowPriceIndicator(series);
            case "Volume":
                return new VolumeIndicator(series, 0);
            case "Close":
            default:
                return new ClosePriceIndicator(series);
        }
    }

    // 팩토리 헬퍼 2: 보조 지표 생성
    private Indicator<Num> createIndicator(StrategyOperand operand, BarSeries series,
                                           Map<String, Indicator<Num>> cache) {
        String code = operand.getIndicatorCode();
        Map<String, Object> params = operand.getParams();

        Indicator<Num> baseIndicator = resolveOperand(
            new StrategyOperand("price", null, null, null, "Close", null),
            series, cache
        );

        switch (code) {
            case "SMA":
                int smaLength = ((Number) params.get("length")).intValue();
                return new SMAIndicator(baseIndicator, smaLength);
            case "EMA":
                int emaLength = ((Number) params.get("length")).intValue();
                return new EMAIndicator(baseIndicator, emaLength);
            case "RSI":
                int rsiLength = ((Number) params.get("length")).intValue();
                return new RSIIndicator(baseIndicator, rsiLength);
            case "MACD":
                int fast = ((Number) params.get("fast")).intValue();
                int slow = ((Number) params.get("slow")).intValue();
                int signal = ((Number) params.get("signal")).intValue();

                MACDIndicator macd = new MACDIndicator(baseIndicator, fast, slow);
                Indicator<Num> signalLine = new EMAIndicator(macd, signal); // Signal 라인 생성

                switch (operand.getOutput()) {
                    case "macd":
                        return macd;
                    case "signal":
                        return signalLine;
                    case "hist":
                        // ⭐️ (변경) MACDHistogramIndicator -> 수동 계산 클래스
                        return new ManualMACDHistogramIndicator(macd, signalLine);
                    default:
                        return macd;
                }
                // TODO: ATR, 볼린저 밴드 등 다른 지표 추가...
            default:
                throw new IllegalArgumentException("Unknown indicator code: " + code);
        }
    }

    // Operand DTO로부터 Map의 키를 생성
    private String generateIndicatorKey(StrategyOperand operand) {
        if (operand == null) return "null_operand";
        switch (operand.getType()) {
            case "price":
                return operand.getPriceField();
            case "const":
                return "const_" + operand.getConstantValue().toString();
            case "indicator":
                String params = operand.getParams().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue().toString())
                    .collect(Collectors.joining(","));
                String key = String.format("%s(%s)", operand.getIndicatorCode(), params);
                if (operand.getOutput() != null && !"value".equals(operand.getOutput())) {
                    key += "." + operand.getOutput();
                }
                return key;
            default:
                return "unknown_operand";
        }
    }

    /**
     * ⭐️ (신규) MACD 히스토그램 수동 계산 클래스
     * (MACDIndicator - EMAIndicator(MACDIndicator, signalLength))
     */
    private static class ManualMACDHistogramIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> macd;
        private final Indicator<Num> signal;

        public ManualMACDHistogramIndicator(Indicator<Num> macd, Indicator<Num> signal) {
            // 부모 클래스에 BarSeries를 전달해야 함 (macd에서 가져옴)
            super(macd);
            this.macd = macd;
            this.signal = signal;
        }

        @Override
        protected Num calculate(int index) {
            // MACD 값 - Signal 값
            return macd.getValue(index).minus(signal.getValue(index));
        }
    }
}

