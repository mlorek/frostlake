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

import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.DecimalText;
import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    /** The widest scale span a NUMBER is rounded across. */
    private static final int MAX_SPAN = 38;

    private RoundedValue() {
    }

    /** The rounded value, or null when the input or the scale is null. */
    static Object of(final List<Object> args, final RoundingMode mode) {
        if (args.get(0) == null || args.size() > 1 && args.get(1) == null) {
            return null;
        }
        if (args.get(0) instanceof Double || args.get(0) instanceof Float) {
            // A FLOAT's scale is no fixed-point slot: past the double's range it rounds to NaN (live-verified).
            return approximatelyRounded(((Number) args.get(0)).doubleValue(),
                args.size() > 1 ? approximateScaleOf(args.get(1)) : 0, mode);
        }
        final int scale = args.size() > 1 ? scaleOf(args.get(1)) : 0;
        final BigDecimal num = DecimalText.parse(args.get(0).toString());
        if (num.scale() - scale > MAX_SPAN) {
            throw new RuntimeException(
                "Invalid parameter value: " + scale + ". Reason: Scale too large");
        }
        final BigDecimal rounded = num.setScale(scale, mode);
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }

    /**
     * The rounded value of a DECFLOAT, or null when the input or the scale is null. The scale span NUMBER refuses
     * as "Scale too large" is a rule of a declared width, which a DECFLOAT does not have: its last digit's exponent
     * goes down to -16383, so {@code ROUND(DECFLOAT '1e-50')} is 0, {@code ROUND(DECFLOAT '1e-100', 50)} is 0 and
     * {@code ROUND(DECFLOAT '1e6000', -5999)} is 1e6000, any scale the scale's slot holds taken (live-verified). The
     * result is held as a DECFLOAT holds it (see Decfloats).
     */
    static Object ofDecfloat(final List<Object> args, final RoundingMode mode) {
        if (args.get(0) == null || args.size() > 1 && args.get(1) == null) {
            return null;
        }
        final int scale = args.size() > 1 ? scaleOf(args.get(1)) : 0;
        return Decfloats.held(DecimalText.parse(args.get(0).toString()).setScale(scale, mode));
    }

    /**
     * The value rounded to a FLOAT scale, in FLOAT arithmetic and to the scale's own value, a fraction included: the
     * input divided by ten to the negated scale, rounded in the call's direction, then multiplied back (live-verified):
     *
     * <pre>
     *   ROUND(1.2345, 2::FLOAT)      1.23            a FLOAT, where a NUMBER scale keeps the NUMBER
     *   ROUND(1.2345, 2.5::FLOAT)    1.233288287     TRUNC(1.2389, 2.5::FLOAT) 1.236450565
     *   ROUND(1.005, 2::FLOAT)       1.0             the double nearest 1.005 is below it
     *   ROUND(123.456, 400::FLOAT)   NaN             ten to the negated scale underflows to zero
     *   ROUND(1e300, -300::FLOAT)    1.0E300
     * </pre>
     *
     * @param args the input and the scale
     * @param mode the direction the call rounds in
     * @return the rounded value, or null when the input or the scale is null
     */
    static Object ofFloatScale(final List<Object> args, final RoundingMode mode) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final double factor = Math.pow(10, -((Number) args.get(1)).doubleValue());
        final double shifted = ((Number) args.get(0)).doubleValue() / factor;
        final double rounded;
        if (mode == RoundingMode.CEILING) {
            rounded = Math.ceil(shifted);
        } else if (mode == RoundingMode.FLOOR) {
            rounded = Math.floor(shifted);
        } else if (mode == RoundingMode.DOWN) {
            rounded = shifted < 0 ? Math.ceil(shifted) : Math.floor(shifted);
        } else {
            // Half away from zero, judged on the fraction itself: adding a half before the floor would round the double
            // just below a half up.
            final double magnitude = Math.floor(Math.abs(shifted));
            final double away = Math.abs(shifted) - magnitude >= 0.5 ? magnitude + 1 : magnitude;
            rounded = shifted < 0 ? -away : away;
        }
        // A zero comes back positive but FLOOR's, as the FLOAT input's rounding has it (see approximatelyRounded).
        final double result = rounded * factor;
        return Double.valueOf(mode == RoundingMode.FLOOR ? result : result + 0.0);
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
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            // NaN and the infinities round to themselves, at any scale (live-verified): ROUND('inf'::FLOAT) is
            // Infinity, where rounding through a long answered the long's bound.
            return value;
        }
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
            // An infinity stays one, so a scale past the double's range ends as NaN as the other modes do
            // (live-verified: ROUND(1.25::FLOAT, 32768) is NaN), where Math.round would hand back a long's bound.
            rounded = Double.isInfinite(shifted) ? shifted : Math.round(shifted);
        }
        // CEIL and TRUNC of a small negative give a POSITIVE zero on the account — CEIL(-0.4::FLOAT)
        // and TRUNC(-0.04::FLOAT, 1) are both 0 there — where IEEE ceiling hands back -0.0. Adding a
        // positive zero turns only that value; FLOOR keeps the sign it computes, as live does.
        final double result = rounded * factor;
        return Double.valueOf(mode == RoundingMode.CEILING || mode == RoundingMode.DOWN
            ? result + 0.0 : result);
    }

    /**
     * The scale argument as a whole number, read through the FIXED[SB2](4,0) slot an exact number is rounded to
     * (live-verified): a fraction rounds, a string spells one, a whole number outside -32768..32767 is "Numeric value
     * '32768' is out of range" and one past 64 bits "... is not recognized", and a FLOAT the slot cannot hold is
     * refused as WholeNumberArgument#requireFittingFloat says. A string that spells no number at all is live's
     * ordinary numeric-conversion refusal — {@code TRUNC(n, 'MONTH')} over a NUMBER is not the date form, it is a bad
     * scale.
     */
    private static int scaleOf(final Object arg) {
        WholeNumberArgument.requireFittingFloat(arg, WholeNumberArgument.SCALE, false);
        final BigInteger whole = wholeScale(arg);
        if (whole.bitLength() > Long.SIZE - 1) {
            throw new RuntimeException("Numeric value '" + whole + "' is not recognized");
        }
        if (whole.bitLength() > Short.SIZE - 1) {
            throw new RuntimeException("Numeric value '" + whole + "' is out of range");
        }
        return whole.intValue();
    }

    /** The scale a FLOAT is rounded to: the whole number it gives, held to the range of an int. */
    private static int approximateScaleOf(final Object arg) {
        final BigInteger whole = wholeScale(arg);
        if (whole.bitLength() > Integer.SIZE - 1) {
            return whole.signum() < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        return whole.intValue();
    }

    /** The scale argument's nearest whole number, half away from zero. */
    private static BigInteger wholeScale(final Object arg) {
        try {
            return DecimalText.parse(arg.toString()).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + arg + "' is not recognized");
        }
    }
}
