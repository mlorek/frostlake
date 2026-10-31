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

import dev.frostlake.values.ValueRange;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * A VARIANT number an element or a path reads out of a stored table's row — {@code a[0]}, {@code o:n},
 * {@code v[0]}, {@code GET(a, 0)} — converts to a DATE, a TIME or a TIMESTAMP_NTZ, where the same number a
 * constant holds, or a VARIANT column read whole, is refused for a DATE and a TIME and read in the session's time
 * zone for a TIMESTAMP_NTZ (live-verified):
 *
 * <pre>
 *   a[0] holding 1     ::DATE           1970-01-02   days from the epoch, half away from zero: 1.5 is 1970-01-03
 *                      ::TIME           00:00:01     seconds, the time of day in UTC
 *                      ::TIMESTAMP_NTZ  1970-01-01 00:00:01   seconds from the epoch in UTC
 *   [1][0]::DATE, v::DATE over a VARIANT column holding 1   Failed to cast variant value 1 to DATE
 *   TO_DATE(a[0]), TO_TIME(a[0])   1970-01-02, 00:00:01, as the casts read it
 * </pre>
 *
 * A TIMESTAMP_LTZ or a TIMESTAMP_TZ reads the number as seconds from the epoch, the instant, either way. A constant
 * relation's element folds into a constant, so {@code a[0]::DATE FROM (SELECT [1] AS a)} is refused as
 * {@code [1][0]::DATE} is.
 */
final class VariantRowElements {

    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);
    private static final BigInteger NANOS_PER_DAY = BigInteger.valueOf(86_400_000_000_000L);

    private VariantRowElements() {
    }

    /**
     * The temporal an element's VARIANT number converts to.
     *
     * @param value  the element's value: the VARIANT, or the number a call's argument hands on unwrapped
     * @param target the type the conversion names: DATE, TIME or a timestamp flavour
     * @return the DATE, TIME or TIMESTAMP_NTZ value, or null where the value holds no number or the target is
     *         none of those
     */
    static Object converted(final Object value, final String target) {
        final BigDecimal number = numberOf(value);
        if (number == null) {
            return null;
        }
        try {
            switch (target) {
                case "DATE":
                    return LocalDate.ofEpochDay(number.setScale(0, RoundingMode.HALF_UP).longValueExact());
                case "TIME":
                    return LocalTime.ofNanoOfDay(nanosOf(number).mod(NANOS_PER_DAY).longValueExact());
                case "TIMESTAMP_NTZ":
                    final BigInteger[] parts = nanosOf(number).divideAndRemainder(NANOS_PER_SECOND.toBigInteger());
                    final boolean below = parts[1].signum() < 0;
                    final long seconds = parts[0].longValueExact() - (below ? 1 : 0);
                    final long nanos = below ? parts[1].longValueExact() + 1_000_000_000L : parts[1].longValueExact();
                    return LocalDateTime.ofEpochSecond(seconds, (int) nanos, ZoneOffset.UTC);
                default:
                    return null;
            }
        } catch (final ArithmeticException | DateTimeException beyondRange) {
            throw VariantTemporalCasts.failure(value, target);
        }
    }

    /**
     * What an element or a path is read out of: a subscript's array, a colon path's base, a GET's first argument.
     *
     * @param operand a cast's operand or a call's argument
     * @return the expression read from, or null where the operand reads no element
     */
    static Expression elementBase(final Expression operand) {
        if (operand instanceof ArrayAccessExpression) {
            return ((ArrayAccessExpression) operand).getArray();
        }
        if (operand instanceof ObjectAccessExpression) {
            return ((ObjectAccessExpression) operand).getBase();
        }
        if (operand instanceof FunctionCallExpression && ((FunctionCallExpression) operand).getFunctionName() != null
                && "GET".equals(((FunctionCallExpression) operand).getFunctionName().toUpperCase(Locale.ROOT))
                && !((FunctionCallExpression) operand).getArguments().isEmpty()) {
            return ((FunctionCallExpression) operand).getArguments().get(0);
        }
        return null;
    }

    /** The number a VARIANT holds, or one handed on unwrapped; null for anything else. */
    private static BigDecimal numberOf(final Object value) {
        if (value instanceof VariantValue) {
            return ((VariantValue) value).node().isNumber() ? ((VariantValue) value).node().decimalValue() : null;
        }
        return value instanceof Number ? ValueRange.exactOf(value) : null;
    }

    /** A number of seconds as whole nanoseconds, a finer fraction cut toward zero. */
    private static BigInteger nanosOf(final BigDecimal seconds) {
        return seconds.multiply(NANOS_PER_SECOND).setScale(0, RoundingMode.DOWN).toBigIntegerExact();
    }
}
