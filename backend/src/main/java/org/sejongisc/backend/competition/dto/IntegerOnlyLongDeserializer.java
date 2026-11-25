package org.sejongisc.backend.competition.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

// JSON 정수(1, 10)만 Long으로 읽는다. 소수(1.9)나 문자열("10")은 MismatchedInputException으로 거부한다.
class IntegerOnlyLongDeserializer extends StdDeserializer<Long> {

    IntegerOnlyLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        return parser.getLongValue();
    }
}
