/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.http;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a response's rows off the wire token by token, so a number's own text decides its Java form.
 * An integer token is the reader's integer; a fraction is a BigDecimal exact to the digit, which is
 * what keeps a NUMBER(38,20) exact across the transport; and a fraction spelled with a minus sign
 * whose value is zero is a FLOAT's negative zero, the one number a BigDecimal cannot hold, so it
 * comes back as the double {@code -0.0}. A tree read would have dropped that sign before the client
 * could see the column was a FLOAT; the client still re-types every FLOAT cell from the column
 * metadata afterwards, so nothing else here needs the type. Anything that is not a scalar token is
 * read the way the mapper reads any untyped value.
 */
public final class WireRowsDeserializer extends ValueDeserializer<List<List<Object>>> {

    @Override
    public List<List<Object>> deserialize(final JsonParser parser, final DeserializationContext context)
            throws JacksonException {
        final List<List<Object>> rows = new ArrayList<>();
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            return rows;
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            rows.add(readRow(parser, context));
        }
        return rows;
    }

    private static List<Object> readRow(final JsonParser parser, final DeserializationContext context)
            throws JacksonException {
        final List<Object> row = new ArrayList<>();
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            row.add(readCell(parser, context));
            return row;
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            row.add(readCell(parser, context));
        }
        return row;
    }

    private static Object readCell(final JsonParser parser, final DeserializationContext context)
            throws JacksonException {
        switch (parser.currentToken()) {
            case VALUE_NULL:
                return null;
            case VALUE_TRUE:
                return Boolean.TRUE;
            case VALUE_FALSE:
                return Boolean.FALSE;
            case VALUE_STRING:
                return parser.getString();
            case VALUE_NUMBER_INT:
                return parser.getNumberValue();
            case VALUE_NUMBER_FLOAT:
                return fraction(parser.getString());
            default:
                return context.readValue(parser, Object.class);
        }
    }

    /** The exact digits of a fraction token, except a signed zero, which only a double can carry. */
    private static Object fraction(final String text) {
        final BigDecimal exact = new BigDecimal(text);
        if (exact.signum() == 0 && text.startsWith("-")) {
            return Double.valueOf(-0.0);
        }
        return exact;
    }
}
