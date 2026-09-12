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

package dev.frostlake.functions.scalar.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The row-time value CEIL, FLOOR, ROUND and TRUNC produce — everything they share except the
 * direction they round in.
 *
 * <p>All four take an optional SCALE as their second argument, defaulting to zero, and all four read
 * it the same way: a fractional scale ROUNDS (half away from zero), a string is read as the number it
 * spells, and a NULL one makes the whole call NULL rather than meaning zero. A NEGATIVE scale rounds
 * to a power of ten — {@code CEIL(123.45, -1)} is 130 — and the result is presented without an
 * exponent.
 *
 * <p>Shifting more than thirty-eight digits is refused: {@code CEIL(<NUMBER(10,2)>, -37)} would need
 * a scale span of thirty-nine, and live answers "Invalid parameter value: -37. Reason: Scale too
 * large". At ROW time, which is where live puts it — the refusal carries no compilation prefix.
 *
 * <p>An APPROXIMATE input passes through as one: a FLOAT stays a FLOAT whatever the scale does to it.
 *
 * <p>The value comes back at the scale it was ROUNDED to, which is not always the scale the column
 * DECLARES — a non-constant scale argument leaves the declared scale at the input's, so the value is
 * padded back out afterwards. That step needs the declared type and so lives in the evaluator, not
 * here.
 */
final class RoundedValue {

    private RoundedValue() {
    }

    /** The rounded value, or null when the input or the scale is null. */
    static Object of(final List<Object> args, final RoundingMode mode) {
        if (args.get(0) == null || args.size() > 1 && args.get(1) == null) {
            return null;
        }
        final int scale = args.size() > 1 ? scaleOf(args.get(1)) : 0;
        if (args.get(0) instanceof Double || args.get(0) instanceof Float) {
            return approximatelyRounded(((Number) args.get(0)).doubleValue(), scale, mode);
        }
        final BigDecimal num = new BigDecimal(args.get(0).toString());
        if (num.scale() - scale > 38) {
            throw new RuntimeException(
                "Invalid parameter value: " + scale + ". Reason: Scale too large");
        }
        final BigDecimal rounded = num.setScale(scale, mode);
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }

    /**
     * An APPROXIMATE value rounded the way ordinary floating-point arithmetic rounds it.
     *
     * <p>No scale limit applies — a FLOAT has no declared scale to exceed — and no limit is needed,
     * because the arithmetic answers for every scale on its own terms. A scale so large that the
     * power of ten overflows gives NaN rather than a refusal (live: {@code CEIL(<FLOAT>, -400)} is
     * NaN, since the value divided by infinity is zero and zero times infinity is not a number), and
     * a scale past the value's precision is simply a no-op.
     */
    private static Object approximatelyRounded(final double value, final int scale,
                                               final RoundingMode mode) {
        final double factor = Math.pow(10, -scale);
        final double shifted = value / factor;
        final double rounded;
        if (mode == RoundingMode.CEILING) {
            rounded = Math.ceil(shifted);
        } else if (mode == RoundingMode.FLOOR) {
            rounded = Math.floor(shifted);
        } else if (mode == RoundingMode.DOWN) {
            rounded = shifted < 0 ? Math.ceil(shifted) : Math.floor(shifted);
        } else {
            rounded = Math.round(shifted);
        }
        // CEIL and TRUNC of a small negative give a POSITIVE zero on the account — CEIL(-0.4::FLOAT)
        // and TRUNC(-0.04::FLOAT, 1) are both 0 there — where IEEE ceiling hands back -0.0. Adding a
        // positive zero turns only that value; FLOOR keeps the sign it computes, as live does.
        final double result = rounded * factor;
        return Double.valueOf(mode == RoundingMode.CEILING || mode == RoundingMode.DOWN
            ? result + 0.0 : result);
    }

    /**
     * The scale argument as a whole number: a fraction rounds, and a string spells one. A string that
     * spells no number at all is live's ordinary numeric-conversion refusal — {@code TRUNC(n, 'MONTH')}
     * over a NUMBER is not the date form, it is a bad scale.
     */
    private static int scaleOf(final Object arg) {
        try {
            return new BigDecimal(arg.toString()).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + arg + "' is not recognized");
        }
    }
}
