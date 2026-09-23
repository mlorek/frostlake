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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.values.DayTimeInterval;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * The run-time arithmetic of a day-time interval, all live-verified:
 *
 * <ul>
 *   <li>a TIMESTAMP minus a TIMESTAMP of any flavours is the interval between them. Two NTZ values
 *       subtract as wall clocks; beside an LTZ or a TZ an NTZ is read at the session's time zone, so
 *       under America/Los_Angeles an LTZ at 10:00 UTC minus an NTZ of 10:00:00.6 is
 *       {@code -0 08:00:00.600000000};</li>
 *   <li>a timestamp plus or minus an interval, and an interval plus a timestamp, is a timestamp of the
 *       same flavour; a DATE moved by one becomes a TIMESTAMP_NTZ from its midnight;</li>
 *   <li>an interval plus or minus an interval, times an exact number in either order, divided by one,
 *       or negated, is an interval. Division by zero is "Interval division by zero".</li>
 * </ul>
 *
 * <p>Every other pairing is refused while the statement compiles (see {@link BinaryOperationTypes}), so
 * each method answers null for a pair it does not own and leaves the caller's own rules in charge.
 */
final class DayTimeIntervalArithmetic {

    private DayTimeIntervalArithmetic() {
    }

    /**
     * {@code left + right} when an interval takes part, or null.
     *
     * @param left the left operand's value
     * @param right the right operand's value
     * @return the sum, or null when neither operand is an interval
     */
    static Object add(final Object left, final Object right) {
        if (left instanceof DayTimeInterval && right instanceof DayTimeInterval) {
            return ((DayTimeInterval) left).plus((DayTimeInterval) right);
        }
        if (right instanceof DayTimeInterval) {
            return shifted(left, ((DayTimeInterval) right).toDuration());
        }
        if (left instanceof DayTimeInterval) {
            return shifted(right, ((DayTimeInterval) left).toDuration());
        }
        return null;
    }

    /**
     * {@code left - right} when an interval takes part or two timestamps meet, or null.
     *
     * @param left the left operand's value
     * @param right the right operand's value
     * @return the difference, or null when the pair is neither
     */
    static Object subtract(final Object left, final Object right) {
        if (left instanceof DayTimeInterval && right instanceof DayTimeInterval) {
            return ((DayTimeInterval) left).minus((DayTimeInterval) right);
        }
        if (right instanceof DayTimeInterval) {
            return shifted(left, ((DayTimeInterval) right).toDuration().negated());
        }
        if (!isTimestamp(left) || !isTimestamp(right)) {
            return null;
        }
        if (left instanceof LocalDateTime && right instanceof LocalDateTime) {
            return DayTimeInterval.of(Duration.between((LocalDateTime) right, (LocalDateTime) left));
        }
        return DayTimeInterval.of(Duration.between(instantOf(right), instantOf(left)));
    }

    /**
     * {@code left * right} when one side is an interval and the other a number, or null.
     *
     * @param left the left operand's value
     * @param right the right operand's value
     * @return the scaled interval, or null
     */
    static Object multiply(final Object left, final Object right) {
        if (left instanceof DayTimeInterval && right instanceof Number) {
            return ((DayTimeInterval) left).times(exact((Number) right));
        }
        if (right instanceof DayTimeInterval && left instanceof Number) {
            return ((DayTimeInterval) right).times(exact((Number) left));
        }
        return null;
    }

    /**
     * {@code left / right} for an interval over a number, or null. The quotient is truncated toward zero to the
     * nanosecond: a one-nanosecond interval halved is zero, either sign (live-verified).
     *
     * @param left the dividend's value
     * @param right the divisor's value
     * @return the quotient, or null
     */
    static Object divide(final Object left, final Object right) {
        if (!(left instanceof DayTimeInterval) || !(right instanceof Number)) {
            return null;
        }
        final BigDecimal divisor = exact((Number) right);
        if (divisor.signum() == 0) {
            throw new RuntimeException("Interval division by zero");
        }
        return DayTimeInterval.ofSeconds(((DayTimeInterval) left).seconds()
            .divide(divisor, DayTimeInterval.SCALE, RoundingMode.DOWN));
    }

    /** Whether the value is a timestamp of any flavour — a DATE and a text are not. */
    private static boolean isTimestamp(final Object value) {
        return value instanceof LocalDateTime || value instanceof OffsetDateTime || value instanceof ZonedDateTime;
    }

    /** The instant a timestamp names, an NTZ read at the session's time zone. */
    private static Instant instantOf(final Object timestamp) {
        if (timestamp instanceof OffsetDateTime) {
            return ((OffsetDateTime) timestamp).toInstant();
        }
        if (timestamp instanceof ZonedDateTime) {
            return ((ZonedDateTime) timestamp).toInstant();
        }
        return ((LocalDateTime) timestamp).atZone(SessionZone.current()).toInstant();
    }

    /** A temporal value moved by a span, or null when it is no DATE or timestamp. */
    private static Object shifted(final Object temporal, final Duration span) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plus(span);
        }
        if (temporal instanceof OffsetDateTime) {
            return ((OffsetDateTime) temporal).plus(span);
        }
        if (temporal instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporal).plus(span);
        }
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().plus(span);
        }
        return null;
    }

    private static BigDecimal exact(final Number number) {
        return number instanceof Double || number instanceof Float
            ? BigDecimal.valueOf(number.doubleValue()) : new BigDecimal(number.toString());
    }
}
