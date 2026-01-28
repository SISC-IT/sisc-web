package org.sejongisc.backend.backtest.service;

import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.backtest.dto.StrategyCondition;
import org.sejongisc.backend.backtest.dto.StrategyOperand;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.entity.PriceData;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.*;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;

import java.time.ZoneId;
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
        // BarSeries 이름에 Ticker 추가
        BarSeries series = new BaseBarSeries(priceDataList.getFirst().getTicker());
        for (PriceData p : priceDataList) {
            series.addBar(
                p.getDate().atStartOfDay(ZoneId.of("Asia/Seoul")),      // 시작 시간을 한국 시간대로 설정
                p.getOpen(), p.getHigh(), p.getLow(), p.getClosePrice(), p.getVolume()
            );
        }
        return series;
    }

    /**
     * DTO 조건(List<StrategyCondition>)을 ta4j의 Rule 객체로 빌드합니다.
     * "isAbsolute" 로직을 포함합니다.
     */
    public Rule buildCombinedRule(List<StrategyCondition> conditions, BarSeries series, Map<String, Indicator<Num>> indicatorCache) {
        // 기본 참/거짓 Rule 생성
        Num sampleNum = series.getBar(0).getClosePrice();
        Num one = sampleNum.numOf(1);
        Num zero = sampleNum.numOf(0);

        Indicator<Num> indicatorOne = new ConstantIndicator<>(series, one);
        Indicator<Num> indicatorZero = new ConstantIndicator<>(series, zero);

        Rule falseRule = new IsEqualRule(indicatorOne, indicatorZero);
        Rule trueRule = new IsEqualRule(indicatorOne, indicatorOne);

        if (conditions == null || conditions.isEmpty()) {
            return falseRule;
        }

        Map<Boolean, List<StrategyCondition>> partitioned = conditions.stream()
            .collect(Collectors.partitioningBy(StrategyCondition::isAbsolute));
        List<StrategyCondition> absoluteConditions = partitioned.get(true);
        List<StrategyCondition> standardConditions = partitioned.get(false);
        Rule absoluteRule;
        Rule standardRule;

        // '무조건' 조건들을 OR로 묶음
        if (absoluteConditions.isEmpty()) {
            absoluteRule = falseRule;
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

        // '일반' 조건들을 AND로 묶음
        if (standardConditions.isEmpty()) {
            standardRule = falseRule;
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
        return new OrRule(absoluteRule, standardRule);
    }

    /**
     * 개별 조건(StrategyCondition)을 ta4j Rule 객체로 변환
     */
    private Rule buildSingleRule(StrategyCondition condition, BarSeries series,
                                 Map<String, Indicator<Num>> indicatorCache) {
        Indicator<Num> left = resolveOperand(condition.leftOperand(), series, indicatorCache);
        Indicator<Num> right = resolveOperand(condition.rightOperand(), series, indicatorCache);

        return switch (condition.operator()) {
            case "GT" -> new OverIndicatorRule(left, right);
            case "GTE" -> new IsEqualRule(left, right).or(new OverIndicatorRule(left, right));
            case "LT" -> new UnderIndicatorRule(left, right);
            case "LTE" -> new IsEqualRule(left, right).or(new UnderIndicatorRule(left, right));
            case "EQ" -> new IsEqualRule(left, right);
            case "CROSSES_ABOVE" -> new CrossedUpIndicatorRule(left, right);
            case "CROSSES_BELOW" -> new CrossedDownIndicatorRule(left, right);
            default -> throw new CustomException(ErrorCode.INVALID_BACKTEST_PARAMS);
        };
    }

    /**
     * StrategyOperand DTO를 ta4j Indicator 객체로 번역
     */
    private Indicator<Num> resolveOperand(StrategyOperand operand, BarSeries series,
                                          Map<String, Indicator<Num>> indicatorCache) {
        if (operand == null) return null;
        validateOperand(operand);
        String key = generateIndicatorKey(operand);
        if (indicatorCache.containsKey(key)) {
            return indicatorCache.get(key);
        }


        Indicator<Num> indicator = switch (operand.type()) {
            case "price" -> createPriceIndicator(operand.priceField(), series);
            case "indicator" -> {
                Indicator<Num> baseIndicator = createIndicator(operand, series, indicatorCache);
                // Transform 로직 추가
                //if ("ATR".equals(operand.getIndicatorCode()) && "pctOfPrice".equals(operand.getTransform())) {
                    // ATR/ClosePrice 비율을 계산하는 Indicator 생성
                    //Indicator<Num> closePrice = createPriceIndicator("Close", series);
                    // ATR / ClosePrice = ATR * (1 / ClosePrice)
                    //yield new MultiplierIndicator(baseIndicator, new DivisionIndicator(new ConstantIndicator<>(series, indicatorOne), closePrice));
                //}
                yield baseIndicator;
            }
            case "const" -> {
                Num constValue = series.getBar(0).getClosePrice().numOf(operand.constantValue());
                yield new ConstantIndicator<>(series, constValue);
            }
            default -> throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
        };

        indicatorCache.put(key, indicator);
        return indicator;
    }

    // 팩토리 헬퍼 1: 원본 가격 지표 생성
    private Indicator<Num> createPriceIndicator(String field, BarSeries series) {
        return switch (field) {
            case "Open" -> new OpenPriceIndicator(series);
            case "High" -> new HighPriceIndicator(series);
            case "Low" -> new LowPriceIndicator(series);
            case "Volume" -> new VolumeIndicator(series, 0);
            case "Close" -> new ClosePriceIndicator(series);
            default -> throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
        };
    }

    // 팩토리 헬퍼 2: 보조 지표 생성
    private Indicator<Num> createIndicator(StrategyOperand operand, BarSeries series,
                                           Map<String, Indicator<Num>> cache) {
        String code = operand.indicatorCode();
        Map<String, Object> params = operand.params();

        Indicator<Num> baseIndicator = createPriceIndicator("Close", series);
        Indicator<Num> closePrice = createPriceIndicator("Close", series);

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
                Indicator<Num> signalLine = new EMAIndicator(macd, signal);

                return switch (operand.output()) {
                    case "macd" -> macd;
                    case "signal" -> signalLine;
                    case "hist" -> new ManualMACDHistogramIndicator(macd, signalLine);
                    default -> macd;
                };
            // 1. 볼린저 밴드 (Bollinger Bands)
            case "BB":
                int bbLength = ((Number) params.get("length")).intValue(); // 보통 20
                double bbK = ((Number) params.get("k")).doubleValue();     // 보통 2.0

                // 이동평균선(SMA) 생성
                SMAIndicator bbSma = new SMAIndicator(closePrice, bbLength);

                // SMA를 BollingerBandsMiddleIndicator로 감싸기 중요.
                BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(bbSma);

                // 표준편차 생성
                StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, bbLength);

                return switch (operand.output() != null ? operand.output() : "middle") {
                    // bbSma 대신 bbMiddle을 전달해야 함
                    case "upper" -> new BollingerBandsUpperIndicator(bbMiddle, sd, series.numOf(bbK));
                    case "lower" -> new BollingerBandsLowerIndicator(bbMiddle, sd, series.numOf(bbK));
                    default -> bbMiddle; // middle 밴드 반환
                };

            // 2. 스토캐스틱 (Stochastic Oscillator)
            case "STOCH":
                int kLength = ((Number) params.get("kLength")).intValue(); // 보통 14
                int dLength = ((Number) params.get("dLength")).intValue(); // 보통 3 (SMA smoothing) (선택 사항 처리가능)

                // High, Low, Close 데이터 필요
                Indicator<Num> maxPrice = createPriceIndicator("High", series);
                Indicator<Num> minPrice = createPriceIndicator("Low", series);

                StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, kLength);

                if ("d".equalsIgnoreCase(operand.output())) {
                    // D 라인은 보통 K 라인의 SMA 입니다.
                    return new SMAIndicator(stochK, dLength);
                }
                return stochK; // 기본값 K

            // 3. CCI (Commodity Channel Index)
            case "CCI":
                int cciLength = ((Number) params.get("length")).intValue(); // 보통 14 또는 20
                return new CCIIndicator(series, cciLength);

            // 4. ATR (Average True Range) - 변동성 지표
            case "ATR":
                int atrLength = ((Number) params.get("length")).intValue(); // 보통 14
                return new ATRIndicator(series, atrLength);

            // 5. ADX (Average Directional Index) - 추세 강도
            case "ADX":
                int adxLength = ((Number) params.get("length")).intValue(); // 보통 14
                return new ADXIndicator(series, adxLength);
            default:
                throw new CustomException(ErrorCode.BACKTEST_INDICATOR_NOT_FOUND);
        }
    }

    // Operand DTO 로부터 Map의 키를 생성
    private String generateIndicatorKey(StrategyOperand operand) {
        if (operand == null) return "null_operand";
        switch (operand.type()) {
            case "price":
                return operand.priceField();
            case "const":
                return "const_" + operand.constantValue().toString();
            case "indicator":
                String params = operand.params().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue().toString())
                    .collect(Collectors.joining(","));
                String key = String.format("%s(%s)", operand.indicatorCode(), params);
                if (operand.output() != null && !"value".equals(operand.output())) {
                    key += "." + operand.output();
                }
                // Transform 정보도 Key에 포함
                //if (operand.getTransform() != null) {
                  //  key += "~" + operand.getTransform();
                //}
                return key;
            default:
                return "unknown_operand";
        }
    }

    /**
     * MACD 히스토그램 수동 계산 클래스
     * (MACDIndicator - EMAIndicator(MACDIndicator, signalLength))
     */
    private static class ManualMACDHistogramIndicator extends CachedIndicator<Num> {
        private final Indicator<Num> macd;
        private final Indicator<Num> signal;

        public ManualMACDHistogramIndicator(Indicator<Num> macd, Indicator<Num> signal) {
            super(macd);
            this.macd = macd;
            this.signal = signal;
        }

        @Override
        protected Num calculate(int index) {
            return macd.getValue(index).minus(signal.getValue(index));
        }
    }

    private void validateOperand(StrategyOperand operand) {
        if (operand.type() == null) {
            throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
        }

        switch (operand.type()) {
            case "price":
                if (operand.priceField() == null) {
                    throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
                }

                break;
            case "indicator":
                if (operand.indicatorCode() == null || operand.params() == null) {
                    throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
                }
                break;
            case "const":
                if (operand.constantValue() == null) {
                    throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
                }
                break;
            default:
                throw new CustomException(ErrorCode.BACKTEST_OPERAND_INVALID);
        }
    }
}