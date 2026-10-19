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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * DECFLOAT's own SQRT, CBRT, LN, LOG, EXP and POWER, each to 38 significant digits rounded half away from zero, as
 * the account answers them (live-verified): {@code SQRT(2::DECFLOAT)} is 1.4142135623730950488016887242096980786,
 * {@code LN(2::DECFLOAT)} 0.69314718055994530941723212145817656808 and {@code EXP(1::DECFLOAT)}
 * 2.7182818284590452353602874713526624978. Each is computed with guard digits and rounded once, except LOG, which
 * divides the two logarithms each already held to 38 digits: {@code LOG(2::DECFLOAT, 8)} is
 * 2.9999999999999999999999999999999999999.
 *
 * <p>An argument with no value is refused naming the operation with its arguments' text —
 * {@code Decfloat invalid operation: SQRT(-1)}, {@code LOG(e, 0)} for LN, {@code POW(0, -1)} — and a result past the
 * type's range as {@code Decfloat out of representable range, operation: EXP(100000)}.
 */
public final class DecfloatMath {

    /** The digits computed before the one rounding to 38. */
    private static final MathContext WORKING = new MathContext(Decfloats.DIGITS + 22, RoundingMode.HALF_EVEN);

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    /** ln(2), to the working digits, computed once. */
    private static final BigDecimal LN2 = lnNear1(TWO);

    /** ln(10) = 3 ln(2) + ln(1.25), to the working digits. */
    private static final BigDecimal LN10 = LN2.multiply(THREE, WORKING).add(lnNear1(new BigDecimal("1.25")), WORKING);

    private DecfloatMath() {
    }

    /** The refusal of an operation DECFLOAT has no value for. */
    private static RuntimeException invalid(final String operation) {
        return new RuntimeException("Decfloat invalid operation: " + operation);
    }

    /** A result held as a DECFLOAT, a result past the range refused naming the operation. */
    private static BigDecimal held(final BigDecimal value, final String operation) {
        try {
            return Decfloats.held(value);
        } catch (final DecfloatRangeException outOfRange) {
            throw DecfloatRangeException.naming(operation);
        }
    }

    /**
     * The square root; a negative argument has none.
     *
     * @param value the argument
     * @return the root, held
     */
    public static BigDecimal sqrt(final BigDecimal value) {
        if (value.signum() < 0) {
            throw invalid("SQRT(" + Decfloats.text(value) + ")");
        }
        return held(value.sqrt(WORKING), "SQRT(" + Decfloats.text(value) + ")");
    }

    /**
     * The cube root, of either sign.
     *
     * @param value the argument
     * @return the root, held
     */
    public static BigDecimal cbrt(final BigDecimal value) {
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        final BigDecimal magnitude = value.abs();
        // magnitude = m * 10^(3k) with m in [1, 1000): the double estimate of m's root never overflows.
        final int k = Math.floorDiv(magnitude.precision() - magnitude.scale() - 1, 3);
        final BigDecimal m = magnitude.movePointLeft(3 * k);
        BigDecimal root = new BigDecimal(Math.cbrt(m.doubleValue())).movePointRight(k);
        for (int i = 0; i < 200; i++) {
            final BigDecimal next = root.subtract(root.pow(3, WORKING).subtract(magnitude, WORKING)
                .divide(THREE.multiply(root.pow(2, WORKING), WORKING), WORKING), WORKING);
            if (next.compareTo(root) == 0) {
                break;
            }
            root = next;
        }
        return held(value.signum() < 0 ? root.negate() : root, "CBRT(" + Decfloats.text(value) + ")");
    }

    /** The natural logarithm of a positive argument, to the working digits. */
    private static BigDecimal ln(final BigDecimal value) {
        // value = m * 10^e with m in [1, 10), and m = n * 2^j with n in [1, 2).
        final int e = value.precision() - value.scale() - 1;
        BigDecimal m = value.movePointLeft(e);
        int j = 0;
        while (m.compareTo(TWO) >= 0) {
            m = m.divide(TWO, WORKING);
            j++;
        }
        return lnNear1(m).add(LN2.multiply(BigDecimal.valueOf(j), WORKING), WORKING)
            .add(LN10.multiply(BigDecimal.valueOf(e), WORKING), WORKING);
    }

    /** ln of an argument in [1, 2], by the series 2 atanh((x - 1) / (x + 1)). */
    private static BigDecimal lnNear1(final BigDecimal x) {
        final BigDecimal z = x.subtract(BigDecimal.ONE).divide(x.add(BigDecimal.ONE), WORKING);
        final BigDecimal z2 = z.multiply(z, WORKING);
        final BigDecimal tolerance = BigDecimal.ONE.movePointLeft(WORKING.getPrecision() + 2);
        BigDecimal term = z;
        BigDecimal sum = z;
        for (int n = 3; n < 100000; n += 2) {
            term = term.multiply(z2, WORKING);
            final BigDecimal next = term.divide(BigDecimal.valueOf(n), WORKING);
            if (next.abs().compareTo(tolerance) < 0) {
                break;
            }
            sum = sum.add(next, WORKING);
        }
        return sum.multiply(TWO, WORKING);
    }

    /**
     * The natural logarithm, held; an argument not above 0 is refused as {@code LOG(e, x)}.
     *
     * @param value the argument
     * @return ln(value)
     */
    public static BigDecimal lnHeld(final BigDecimal value) {
        if (value.signum() <= 0) {
            throw invalid("LOG(e, " + Decfloats.text(value) + ")");
        }
        return Decfloats.held(ln(value));
    }

    /**
     * The logarithm to a base: ln(value) / ln(base), each logarithm held to 38 digits before the division.
     *
     * @param base  a positive base other than 1
     * @param value a positive argument
     * @return the logarithm
     */
    public static BigDecimal log(final BigDecimal base, final BigDecimal value) {
        if (base.signum() <= 0 || base.compareTo(BigDecimal.ONE) == 0 || value.signum() <= 0) {
            throw invalid("LOG(" + Decfloats.text(base) + ", " + Decfloats.text(value) + ")");
        }
        return Decfloats.divide(Decfloats.held(ln(value)), Decfloats.held(ln(base)));
    }

    /** e to a power, to the working digits. */
    private static BigDecimal exp(final BigDecimal value) {
        if (value.signum() == 0) {
            return BigDecimal.ONE;
        }
        // e^x = (e^(x / 2^r))^(2^r), the reduced argument small enough for the series to converge at once.
        int r = 0;
        BigDecimal reduced = value;
        while (reduced.abs().compareTo(BigDecimal.ONE) > 0 || r < 8) {
            reduced = reduced.divide(TWO, WORKING);
            r++;
        }
        final BigDecimal tolerance = BigDecimal.ONE.movePointLeft(WORKING.getPrecision() + 2);
        BigDecimal term = BigDecimal.ONE;
        BigDecimal sum = BigDecimal.ONE;
        for (int n = 1; n < 100000; n++) {
            term = term.multiply(reduced, WORKING).divide(BigDecimal.valueOf(n), WORKING);
            if (term.abs().compareTo(tolerance) < 0) {
                break;
            }
            sum = sum.add(term, WORKING);
        }
        for (int i = 0; i < r; i++) {
            sum = sum.multiply(sum, WORKING);
        }
        return sum;
    }

    /**
     * e to a power, held.
     *
     * @param value the power
     * @return e^value
     */
    public static BigDecimal expHeld(final BigDecimal value) {
        return held(exp(value), "EXP(" + Decfloats.text(value) + ")");
    }

    /**
     * A power: an integral exponent multiplies exactly, any other one is e^(y ln x) over a positive base.
     *
     * @param base     the base
     * @param exponent the exponent
     * @return base^exponent, held
     */
    public static BigDecimal power(final BigDecimal base, final BigDecimal exponent) {
        final String operation = "POW(" + Decfloats.text(base) + ", " + Decfloats.text(exponent) + ")";
        final BigDecimal whole = exponent.stripTrailingZeros();
        if (whole.scale() <= 0 && whole.abs().compareTo(BigDecimal.valueOf(999999999)) <= 0) {
            final int n = whole.intValueExact();
            if (n >= 0) {
                return held(base.pow(n, WORKING), operation);
            }
            if (base.signum() == 0) {
                throw invalid(operation);
            }
            return held(BigDecimal.ONE.divide(base.pow(-n, WORKING), WORKING), operation);
        }
        if (base.signum() < 0 || base.signum() == 0 && exponent.signum() < 0) {
            throw invalid(operation);
        }
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return held(exp(exponent.multiply(ln(base), WORKING)), operation);
    }
}
