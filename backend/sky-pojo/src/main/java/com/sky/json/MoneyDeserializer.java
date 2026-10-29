package com.sky.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

public class MoneyDeserializer extends StdDeserializer<BigDecimal> {

    public MoneyDeserializer() {
        super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(
            JsonParser parser,
            DeserializationContext context) throws IOException {

        JsonToken token = parser.currentToken();

        if (token != JsonToken.VALUE_NUMBER_INT
                && token != JsonToken.VALUE_NUMBER_FLOAT) {
            return context.reportInputMismatch(
                    BigDecimal.class,
                    "价格必须是JSON数字"
            );
        }

        return parser.getDecimalValue();
    }
}