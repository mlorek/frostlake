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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.executor.SqlCompilationError;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The scale a timestamp cast of a VARIANT takes after it: {@code AS_TIMESTAMP_NTZ(v, 0)} is a TIMESTAMP_NTZ(0),
 * the fraction past the scale cut, not rounded; a scale outside 0 to 9 is refused, {@code invalid value [10] for
 * parameter 'AS_TIMESTAMP_TZ(?, ?, scale)'}, and one that is no whole number, a NULL included, as {@code argument 2
 * to function AS_TIMESTAMP_NTZ needs to be an integer, found: 'null'}. With none the timestamp keeps all nine digits
 * (live-verified).
 */
final class VariantTimestampScale {

    /** The scale a timestamp has with none written. */
    private static final int FULL = 9;

    private VariantTimestampScale() {
    }

    /**
     * The scale a call writes.
     *
     * @param function the function's name
     * @param args     the call's arguments
     * @return the scale, nine where none is written
     */
    static int of(final String function, final List<Object> args) {
        if (args.size() < 2) {
            return FULL;
        }
        final Object written = args.get(1);
        if (!(written instanceof Number) || new BigDecimal(written.toString()).stripTrailingZeros().scale() > 0) {
            // The scale is a whole number: a NULL, a fraction and a text are named as written (live-verified).
            final String found = written == null ? "null"
                : written instanceof String ? "'" + written + "'" : written.toString();
            throw new RuntimeException(SqlCompilationError.of("argument 2 to function " + function
                + " needs to be an integer, found: '" + found + "'"));
        }
        final long scale = ((Number) written).longValue();
        if (scale < 0 || scale > FULL) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + scale + "] for parameter '"
                + function + "(?, ?, scale)'"));
        }
        return (int) scale;
    }

    /**
     * A timestamp cut to a scale.
     *
     * @param value the timestamp, a LocalDateTime, an OffsetDateTime or a ZonedDateTime
     * @param scale the digits of the fraction kept
     * @return the timestamp with the digits past the scale dropped
     */
    static Object cut(final Object value, final int scale) {
        int divisor = 1;
        for (int i = scale; i < FULL; i++) {
            divisor *= 10;
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime local = (LocalDateTime) value;
            return local.withNano(local.getNano() - local.getNano() % divisor);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime offset = (OffsetDateTime) value;
            return offset.withNano(offset.getNano() - offset.getNano() % divisor);
        }
        final ZonedDateTime zoned = (ZonedDateTime) value;
        return zoned.withNano(zoned.getNano() - zoned.getNano() % divisor);
    }
}
