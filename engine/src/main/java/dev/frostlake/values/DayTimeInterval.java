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

package dev.frostlake.values;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * A day-time interval, the value of an {@code INTERVAL DAY(9) TO SECOND(9)}: a signed count of seconds
 * carried to the nanosecond. Its text is live's conversion text, {@code +1 01:00:00.000000000}: the sign
 * always spelled (a zero is {@code +0 00:00:00.000000000} and a negative span under a day
 * {@code -0 02:00:00.000000000}), the whole days unpadded, then two-digit hours, minutes and seconds and
 * nine fractional digits.
 *
 * <p>Scaling is rounded half away from zero at the nanosecond: one nanosecond times 0.5 is one nanosecond,
 * and minus one nanosecond times 0.5 is minus one (live-verified).
 */
public class DayTimeInterval implements Comparable<DayTimeInterval>, Serializable {

    private static final long serialVersionUID = 1L;

    /** The fractional digits every interval carries. */
    public static final int SCALE = 9;

    private static final BigInteger SECONDS_PER_DAY = BigInteger.valueOf(86_400);
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;

    /** The span in seconds, always at {@link #SCALE}. */
    private final BigDecimal seconds;

    /**
     * The interval spanning {@code seconds}. Open to a subclass that carries more than the span — an
     * interval LITERAL keeps the fields it was written in — while equality, order and hashing stay the
     * span's alone, so two literals of different units and a TIMESTAMP difference of the same span are
     * one value.
     *
     * @param seconds the span in seconds, rounded half away from zero to the nanosecond
     */
    protected DayTimeInterval(final BigDecimal seconds) {
        this.seconds = seconds.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The interval spanning {@code seconds}, rounded half away from zero to the nanosecond.
     *
     * @param seconds the span in seconds
     * @return the interval
     */
    public static DayTimeInterval ofSeconds(final BigDecimal seconds) {
        return new DayTimeInterval(seconds);
    }

    /**
     * The interval a duration spans.
     *
     * @param duration the span
     * @return the interval
     */
    public static DayTimeInterval of(final Duration duration) {
        return new DayTimeInterval(BigDecimal.valueOf(duration.getSeconds())
            .add(BigDecimal.valueOf(duration.getNano(), SCALE)));
    }

    /** @return the span in seconds, at nine fractional digits */
    public BigDecimal seconds() {
        return seconds;
    }

    /**
     * The span as a duration, for moving a timestamp by it.
     *
     * @return the duration
     */
    public Duration toDuration() {
        final BigDecimal[] parts = seconds.divideAndRemainder(BigDecimal.ONE);
        return Duration.ofSeconds(parts[0].longValueExact(), parts[1].movePointRight(SCALE).longValueExact());
    }

    /** @return this interval plus {@code other} */
    public DayTimeInterval plus(final DayTimeInterval other) {
        return new DayTimeInterval(seconds.add(other.seconds));
    }

    /** @return this interval minus {@code other} */
    public DayTimeInterval minus(final DayTimeInterval other) {
        return new DayTimeInterval(seconds.subtract(other.seconds));
    }

    /** @return the interval with its sign flipped */
    public DayTimeInterval negated() {
        return new DayTimeInterval(seconds.negate());
    }

    /**
     * This interval scaled by an exact factor, rounded half away from zero to the nanosecond.
     *
     * @param factor the factor
     * @return the scaled interval
     */
    public DayTimeInterval times(final BigDecimal factor) {
        return new DayTimeInterval(seconds.multiply(factor));
    }

    /**
     * This interval divided by an exact divisor, truncated toward zero to the nanosecond: two seconds over
     * three is {@code .666666666}, either sign, and a nanosecond halved is zero (live-verified). A product
     * rounds half away from zero instead (see {@link #times}).
     *
     * @param divisor the divisor, not zero
     * @return the quotient
     */
    public DayTimeInterval dividedBy(final BigDecimal divisor) {
        return new DayTimeInterval(seconds.divide(divisor, SCALE, RoundingMode.DOWN));
    }

    @Override
    public final int compareTo(final DayTimeInterval other) {
        return seconds.compareTo(other.seconds);
    }

    @Override
    public final boolean equals(final Object other) {
        return other instanceof DayTimeInterval && seconds.equals(((DayTimeInterval) other).seconds);
    }

    @Override
    public final int hashCode() {
        return seconds.hashCode();
    }

    @Override
    public String toString() {
        final BigDecimal magnitude = seconds.abs();
        final BigInteger whole = magnitude.toBigInteger();
        final BigInteger[] dayParts = whole.divideAndRemainder(SECONDS_PER_DAY);
        final int rest = dayParts[1].intValue();
        final String fraction = magnitude.subtract(new BigDecimal(whole)).movePointRight(SCALE)
            .setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        return (seconds.signum() < 0 ? "-" : "+") + dayParts[0] + " "
            + String.format("%02d:%02d:%02d.", rest / SECONDS_PER_HOUR,
                rest % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, rest % SECONDS_PER_MINUTE)
            + "000000000".substring(fraction.length()) + fraction;
    }
}
