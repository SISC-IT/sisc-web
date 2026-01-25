package org.sejongisc.backend.backtest.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sejongisc.backend.backtest.dto.StrategyCondition;
import org.sejongisc.backend.backtest.dto.StrategyOperand;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.stock.entity.PriceData;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class Ta4jHelperServiceTest {

    @InjectMocks
    private Ta4jHelperService ta4jHelperService;

    // 테스트용 더미 데이터 생성 헬퍼
    private List<PriceData> createDummyPriceData() {
        PriceData p1 = PriceData.builder()
                .ticker("AAPL")
                .date(LocalDate.now().minusDays(1))
                .open(BigDecimal.valueOf(100))
                .high(BigDecimal.valueOf(110))
                .low(BigDecimal.valueOf(90))
                .closePrice(BigDecimal.valueOf(105))
                .volume(1000L)
                .build();
        return List.of(p1);
    }

    @Test
    @DisplayName("createBarSeries - PriceData 리스트를 BarSeries로 변환 성공")
    void createBarSeries_success() {
        // given
        List<PriceData> priceDataList = createDummyPriceData();

        // when
        BarSeries series = ta4jHelperService.createBarSeries(priceDataList);

        // then
        assertThat(series).isNotNull();
        assertThat(series.getName()).isEqualTo("AAPL");
        assertThat(series.getBarCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("buildCombinedRule - 단순 조건(RSI > 30) 파싱 성공")
    void buildCombinedRule_simpleCondition() {
        // given
        BarSeries series = ta4jHelperService.createBarSeries(createDummyPriceData());

        // 조건: RSI(14) > 30
        StrategyOperand left = new StrategyOperand(
                "indicator", "RSI", "Close", null, null, Map.of("length", 14)
        );
        StrategyOperand right = new StrategyOperand(
                "const", null, null, 30.0, null, null
        );

        StrategyCondition condition = new StrategyCondition(left, "GT", right, false);

        // when
        Rule rule = ta4jHelperService.buildCombinedRule(List.of(condition), series, new HashMap<>());

        // then
        assertThat(rule).isNotNull();
        // 실제 로직 동작 여부(Exception 안 나는지) 확인
    }

    @Test
    @DisplayName("validateOperand - 필수 값 누락 시 예외 발생 (ErrorCode 확인)")
    void validateOperand_exception() {
        // given
        BarSeries series = ta4jHelperService.createBarSeries(createDummyPriceData());

        // type이 없는 잘못된 피연산자
        StrategyOperand invalidOperand = new StrategyOperand(
                null, "RSI", "Close", null, null, Map.of("length", 14)
        );
        StrategyCondition condition = new StrategyCondition(invalidOperand, "GT", invalidOperand, false);

        // when & then
        assertThatThrownBy(() ->
                ta4jHelperService.buildCombinedRule(List.of(condition), series, new HashMap<>())
        )
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.BACKTEST_OPERAND_INVALID.getMessage());
    }

    @Test
    @DisplayName("createIndicator - 지원하지 않는 지표 코드 시 예외 발생")
    void createIndicator_unknownCode() {
        // given
        BarSeries series = ta4jHelperService.createBarSeries(createDummyPriceData());

        StrategyOperand unknownIndicator = new StrategyOperand(
                "indicator", "UNKNOWN_CODE", "Close", null, null, Map.of("length", 14)
        );
        StrategyOperand constOperand = new StrategyOperand(
                "const", null, null, 10.0, null, null
        );
        StrategyCondition condition = new StrategyCondition(unknownIndicator, "GT", constOperand, false);

        // when & then
        assertThatThrownBy(() ->
                ta4jHelperService.buildCombinedRule(List.of(condition), series, new HashMap<>())
        )
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.BACKTEST_INDICATOR_NOT_FOUND.getMessage());
    }
}