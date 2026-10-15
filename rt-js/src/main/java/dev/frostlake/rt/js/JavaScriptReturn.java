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

package dev.frostlake.rt.js;

import dev.frostlake.executor.ColumnLengthException;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.HandlerJson;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * What a JavaScript handler's invoker hands back, as the value of the declared RETURNS type (live-verified; see
 * {@code JavaScriptHandler.typedReturn} for the JavaScript side). A number of milliseconds is a DATE in the
 * session's zone, a TIMESTAMP_NTZ at the session's wall clock for that instant and a zoned TIMESTAMP at the
 * instant; hex is a BINARY; JSON is a VARIANT, an ARRAY or an OBJECT, read by {@link HandlerJson} — a
 * procedure's keeps a {@code Date} and a {@code Uint8Array} as their texts, a function's as a TIMESTAMP_LTZ and
 * a BINARY; a text longer than a declared VARCHAR(n) and bytes longer than a declared BINARY(n) are refused; and
 * a value the type does not take is the refusal the account words: {@code JavaScript execution error: Return
 * value is not an ARRAY}.
 */
final class JavaScriptReturn {

    /** How the invoker's source spells the prefix of a refusal it hands back in place of a value. */
    static final String REFUSAL_IN_SOURCE = "\\u0001R:";

    /** How the invoker's source spells the prefix of a refusal the account words without the execution error's. */
    static final String BARE_REFUSAL_IN_SOURCE = "\\u0001B:";

    /** How the invoker's source spells the text it writes for an array element that is {@code undefined}. */
    static final String HOLE_IN_SOURCE = "\\u0000undefined\\u0000";

    /**
     * How the invoker's source spells what it leads a text answer with when the text itself begins like a
     * refusal, so that it stays the text it is.
     */
    static final String TEXT_IN_SOURCE = "\\u0001S:";

    private static final String REFUSAL = "\u0001R:";

    private static final String BARE_REFUSAL = "\u0001B:";

    private static final String TEXT = "\u0001S:";

    private JavaScriptReturn() {
    }

    /**
     * Converts the invoker's result.
     *
     * @param result    what the invoker handed back
     * @param returns   the routine's declared RETURNS type, or null when the answer is not typed
     * @param procedure whether the routine is a procedure
     * @return the value the routine answers
     */
    static Object toSql(final Object result, final DataType returns, final boolean procedure) {
        if (result == null || returns == null) {
            return result;
        }
        if (result instanceof String && ((String) result).startsWith(REFUSAL)) {
            throw new RuntimeException("JavaScript execution error: " + ((String) result).substring(REFUSAL.length()));
        }
        if (result instanceof String && ((String) result).startsWith(BARE_REFUSAL)) {
            // A value that holds itself: Object contains cyclic references (live-verified).
            throw new RuntimeException(((String) result).substring(BARE_REFUSAL.length()));
        }
        if (returns instanceof StringType) {
            final String written = String.valueOf(result);
            final String text = written.startsWith(TEXT) ? written.substring(TEXT.length()) : written;
            final int limit = ((StringType) returns).getMaxLength();
            if (limit > 0 && CodePointText.length(text) > limit) {
                throw new ColumnLengthException(limit, text);
            }
            return text;
        }
        if (returns instanceof NumericType && result instanceof Number) {
            return Double.valueOf(((Number) result).doubleValue());
        }
        if (returns instanceof DateTimeType && result instanceof Number) {
            return temporal(Instant.ofEpochMilli(((Number) result).longValue()), returns);
        }
        if (returns instanceof BinaryType && result instanceof String) {
            final BinaryValue bytes = BinaryValue.fromHex((String) result);
            final int limit = ((BinaryType) returns).getMaxLength();
            if (limit > 0 && bytes.length() > limit) {
                throw ColumnLengthException.forBinary(limit, bytes.toHex());
            }
            return bytes;
        }
        if (returns.getCategory() == TypeCategory.SEMI_STRUCTURED && result instanceof String) {
            return procedure ? HandlerJson.toVariant((String) result) : HandlerJson.toValue((String) result);
        }
        return result;
    }

    private static Object temporal(final Instant instant, final DataType returns) {
        final String name = returns.getName() == null ? "" : returns.getName().toUpperCase(Locale.ROOT);
        final ZonedDateTime inSession = instant.atZone(SessionZone.current());
        if ("DATE".equals(name)) {
            return inSession.toLocalDate();
        }
        if ("TIME".equals(name)) {
            return inSession.toLocalTime();
        }
        if (name.endsWith("LTZ") || name.endsWith("_TZ")) {
            return OffsetDateTime.ofInstant(instant, SessionZone.current());
        }
        return inSession.toLocalDateTime();
    }
}
