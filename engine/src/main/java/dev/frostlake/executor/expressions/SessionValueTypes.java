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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * The static type a SESSION VARIABLE carries: the type of the VALUE it was set to, measured the way a
 * literal of that value is measured. A variable remembers a value, not an expression — {@code SET n = 2 + 3}
 * is NUMBER(1,0) for the 5 it holds, {@code SET s = 'a' || 'bc'} VARCHAR(3), {@code SET d = 1.50}
 * NUMBER(2,1) — and a variable set from NULL carries no type at all (all live-verified).
 */
final class SessionValueTypes {

    private SessionValueTypes() {
    }

    /**
     * The type a value carries.
     *
     * @param value the value the variable holds
     * @return its type, or null when the value is NULL or of a kind this does not measure
     */
    static DataType of(final Object value) {
        if (value instanceof Boolean) {
            return BooleanType.BOOLEAN;
        }
        if (value instanceof Double || value instanceof Float) {
            return NumericType.FLOAT;
        }
        if (value instanceof BigDecimal || value instanceof Number) {
            final NumericType measured = NumericLiteralTypes.of(value);
            return measured != null ? measured : NumericType.NUMBER;
        }
        if (value instanceof String) {
            // One character at least: a variable set to '' is VARCHAR(1), as the literal '' is (live-verified).
            return new StringType("VARCHAR", Math.max(1, CodePointText.length((String) value)));
        }
        if (value instanceof BinaryValue) {
            // A variable's binary is spelled without a width, as everything unsized is.
            return BinaryType.UNSIZED;
        }
        if (value instanceof LocalDate || value instanceof java.sql.Date) {
            return DateTimeType.DATE;
        }
        if (value instanceof LocalTime || value instanceof java.sql.Time) {
            return DateTimeType.TIME;
        }
        // A TIMESTAMP_LTZ is carried as an OffsetDateTime and a TIMESTAMP_TZ as a ZonedDateTime, and a variable
        // keeps the flavour it was set to: SET t = CURRENT_TIMESTAMP() reads TIMESTAMP_LTZ(9) (live-verified).
        if (value instanceof OffsetDateTime) {
            return DateTimeType.TIMESTAMP_LTZ;
        }
        if (value instanceof ZonedDateTime) {
            return DateTimeType.TIMESTAMP_TZ;
        }
        return value instanceof LocalDateTime || value instanceof Timestamp ? DateTimeType.TIMESTAMP_NTZ : null;
    }
}
