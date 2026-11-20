package org.sejongisc.backend.competition.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompetitionTradeRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("수량이 정수면 그대로 읽는다")
    void readsIntegerQuantity() throws Exception {
        CompetitionTradeRequest request = objectMapper.readValue(json("10"), CompetitionTradeRequest.class);

        assertThat(request.quantity()).isEqualTo(10L);
    }

    @Test
    @DisplayName("수량이 소수면 버려서 받지 않고 거부한다")
    void rejectsDecimalQuantity() {
        assertThatThrownBy(() -> objectMapper.readValue(json("1.9"), CompetitionTradeRequest.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("수량이 문자열이면 숫자로 바꿔 받지 않고 거부한다")
    void rejectsStringQuantity() {
        assertThatThrownBy(() -> objectMapper.readValue(json("\"10\""), CompetitionTradeRequest.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("수량이 불리언, 배열, Long 범위를 넘는 수면 거부한다")
    void rejectsOtherNonIntegerQuantity() {
        for (String quantity : new String[] {"true", "[10]", "99999999999999999999"}) {
            assertThatThrownBy(() -> objectMapper.readValue(json(quantity), CompetitionTradeRequest.class))
                    .as("quantity=%s", quantity)
                    .isInstanceOf(JsonProcessingException.class);
        }
    }

    @Test
    @DisplayName("수량이 null이면 읽기는 통과하고 @NotNull 검증에서 걸린다")
    void nullQuantityIsLeftToValidation() throws Exception {
        CompetitionTradeRequest request = objectMapper.readValue(json("null"), CompetitionTradeRequest.class);

        assertThat(request.quantity()).isNull();
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("수량은 필수입니다.");
    }

    private static String json(String quantity) {
        return """
                {"ticker": "005930", "side": "BUY", "quantity": %s, "closePriceId": "6f1f3c2e-0000-4000-8000-000000000001"}
                """.formatted(quantity);
    }
}
