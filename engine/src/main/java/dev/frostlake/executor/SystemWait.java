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

package dev.frostlake.executor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * SYSTEM$WAIT(amount [, unit]): waits, and answers what it waited in its unit, lower-cased and always plural —
 * "waited 0 seconds", "waited 1 seconds", "waited 2 milliseconds", "waited 0 hours" (live-verified).
 *
 * <p>The amount is a whole number: a FLOAT or a numeric text is rounded half away from zero ('1.5' and 1.5::FLOAT
 * wait 2, '-1.5' answers -2), and a text that is no number is refused while the call compiles. The account also
 * refuses, "needs to be constant", an amount its planner does not fold to a constant — a column, an expression
 * over one, a scaled NUMBER such as 1.5 — which this engine does not model: it waits the amount rounded.
 *
 * <p>The unit is SECONDS unless written, and is one of DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS, MICROSECONDS
 * and NANOSECONDS spelled exactly so — 'seconds' or ' SECONDS ' is refused, in live's own unfilled sentence. A NULL
 * amount or unit is refused before the unit is read. The refusals come in that order: the amount's text, a NULL,
 * the unit.
 *
 * <p>The wait itself is capped at thirty seconds, which is all a local run can afford; a negative amount does not
 * wait.
 */
public final class SystemWait {

    /** The longest the engine actually sleeps, in nanoseconds. */
    private static final long LONGEST_SLEEP_NANOS = 30_000_000_000L;

    /** The unit a call that names none waits in. */
    private static final String DEFAULT_UNIT = "SECONDS";

    /** A text amount that is no number, as live words it: the argument's own text inside the implicit cast. */
    private static final String NOT_CONSTANT = """
        argument 0 to function SqlIdentifier{qualifierNames=[], identifierName=SYSTEM$WAIT} needs to be constant, \
        found 'TO_NUMBER('%s', 18, 0)'""";

    /** A unit live does not know, in its own sentence, whose placeholders it leaves unfilled. */
    private static final String INVALID_UNIT = """
        %s error line %s at position {1}

        Invalid unit of time '{2}'.
        Use one of DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS.""";

    private SystemWait() {
    }

    /**
     * Wait as SYSTEM$WAIT's arguments ask, and answer its sentence.
     *
     * @param args the amount and, optionally, the unit
     * @return the sentence
     */
    public static String waitFor(final List<Object> args) {
        final Object amountArg = args.isEmpty() ? null : args.get(0);
        final boolean unitWritten = args.size() > 1;
        final Object unitArg = unitWritten ? args.get(1) : DEFAULT_UNIT;
        final long amount = amountOf(amountArg);
        if (amountArg == null || unitArg == null) {
            throw new RuntimeException("inputs may not be null");
        }
        final String unit = unitArg.toString();
        final long nanosPerUnit = nanosPer(unit);
        if (nanosPerUnit <= 0) {
            throw new RuntimeException(String.format(INVALID_UNIT, SqlCompilationError.PREFIX, unit));
        }
        // Neither a negative amount nor one past the cap is multiplied out: either would overflow a long.
        final long unitsInLongestSleep = LONGEST_SLEEP_NANOS / nanosPerUnit;
        final long nanos = amount <= 0 ? 0L
            : amount > unitsInLongestSleep ? LONGEST_SLEEP_NANOS : amount * nanosPerUnit;
        if (nanos > 0) {
            try {
                Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return "waited " + amount + " " + unit.toLowerCase(Locale.ROOT);
    }

    /**
     * The whole number an amount argument waits: numbers and numeric text round half away from zero, a text that is
     * no number is refused, and anything else (NULL included, which the caller refuses next) waits nothing.
     */
    private static long amountOf(final Object amount) {
        final BigDecimal exact;
        if (amount instanceof BigDecimal) {
            exact = (BigDecimal) amount;
        } else if (amount instanceof BigInteger) {
            exact = new BigDecimal((BigInteger) amount);
        } else if (amount instanceof Double || amount instanceof Float) {
            final double value = ((Number) amount).doubleValue();
            exact = Double.isNaN(value) || Double.isInfinite(value) ? BigDecimal.ZERO : BigDecimal.valueOf(value);
        } else if (amount instanceof Number) {
            exact = BigDecimal.valueOf(((Number) amount).longValue());
        } else if (amount instanceof String) {
            try {
                exact = new BigDecimal(((String) amount).trim());
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException(SqlCompilationError.of(String.format(NOT_CONSTANT, amount)));
            }
        } else {
            exact = BigDecimal.ZERO;
        }
        return exact.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Nanoseconds in one of the units live knows, spelled exactly; zero for any other spelling. */
    private static long nanosPer(final String unit) {
        switch (unit) {
            case "DAYS":
                return 86_400_000_000_000L;
            case "HOURS":
                return 3_600_000_000_000L;
            case "MINUTES":
                return 60_000_000_000L;
            case "SECONDS":
                return 1_000_000_000L;
            case "MILLISECONDS":
                return 1_000_000L;
            case "MICROSECONDS":
                return 1_000L;
            case "NANOSECONDS":
                return 1L;
            default:
                return 0L;
        }
    }
}
