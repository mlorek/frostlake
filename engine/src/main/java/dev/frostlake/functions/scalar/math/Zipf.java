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

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ZIPF(s, N, gen) — an integer in 1..N drawn by Zipf's law with exponent s: k is drawn with a weight of 1 / k^s.
 * The generator argument supplies the entropy as UNIFORM's does, so a constant generator answers one value for every
 * row and RANDOM() a value per row (live-verified). An s of 0 draws uniformly.
 *
 * <p>The arguments are judged before the generator is read (live-verified):
 * <ul>
 *   <li>a NULL s is {@code Invalid parameter value: NULL. Reason: alpha must not be NULL}, and a NULL N
 *       {@code … n must not be NULL};</li>
 *   <li>an s below 0 is {@code Invalid parameter value: -1. Reason: alpha must be >= 0.0}, and an N below 1
 *       {@code Invalid parameter value: 0. Reason: n must be >= 1};</li>
 *   <li>a NULL generator answers NULL.</li>
 * </ul>
 * An N with a fraction is rounded to the nearest whole number. The value is the account's (live-verified, ZIPF(1,
 * 1000000, 1) is 4097): the generator's first draw (see {@link GeneratorDraws}) picks the first rank whose weights,
 * summed from rank 1, reach its fraction of all of them (see {@link ZipfRanks}). Past ten million ranks the value is
 * drawn by rejection-inversion (Hörmann and Derflinger) instead, at a cost that does not grow with N.
 */
public class Zipf extends NumericArgumentFunction {

    /**
     * The most ranks drawn as the account draws them, by summing every weight (see {@link ZipfRanks}); past it a draw
     * is made by rejection-inversion, which follows the law at the same cost whatever the ranks.
     */
    private static final long EXACT_RANKS = 10_000_000L;

    public Zipf() {
        super("ZIPF", NumericType.NUMBER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: alpha must not be NULL");
        }
        if (args.get(1) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: n must not be NULL");
        }
        final double exponent = args.get(0) instanceof Double ? ((Double) args.get(0)).doubleValue()
            : DecimalText.parse(args.get(0).toString().trim()).doubleValue();
        // A NaN exponent is no exponent of a law at all, refused as a negative one is.
        if (exponent < 0 || Double.isNaN(exponent)) {
            throw new RuntimeException("Invalid parameter value: " + plain(args.get(0))
                + ". Reason: alpha must be >= 0.0");
        }
        // N is read as a whole number, so a FLOAT that is none is out of its range (live-verified).
        final BigDecimal elements = finite(args.get(1))
            ? DecimalText.parse(args.get(1).toString().trim()).setScale(0, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(Random.floatSeed(((Double) args.get(1)).doubleValue(), "FIXED[SB8](18,0){not null}"));
        if (elements.signum() < 1) {
            throw new RuntimeException("Invalid parameter value: " + plain(args.get(1)) + ". Reason: n must be >= 1");
        }
        if (args.get(2) == null) {
            return null;
        }
        // The generator is read as RANDOM reads its seed, a whole number past 64 bits being no number, and its value
        // seeds the draws (see GeneratorDraws).
        final GeneratorDraws draws = new GeneratorDraws(Random.seedOf(args.get(2)));
        final long n = elements.min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
        // An infinite exponent puts every draw on the first rank (live-verified).
        if (n == 1 || Double.isInfinite(exponent)) {
            return Long.valueOf(1L);
        }
        if (n <= EXACT_RANKS) {
            return Long.valueOf(ZipfRanks.of(exponent, n).rank(draws.canonical()));
        }
        if (exponent == 0) {
            return Long.valueOf(Math.min(n, Math.max(1L, (long) Math.ceil(draws.canonical() * n))));
        }
        return Long.valueOf(sample(n, exponent, draws));
    }

    /** Whether an argument is a number that is not a NaN or infinite FLOAT. */
    private static boolean finite(final Object arg) {
        return !(arg instanceof Double) || Double.isFinite(((Double) arg).doubleValue());
    }

    /** An argument as the refusal spells it: a whole number without its fraction's zeros, nan or inf as such. */
    private static String plain(final Object value) {
        if (value instanceof Double && !Double.isFinite(((Double) value).doubleValue())) {
            return Random.generalText(((Double) value).doubleValue());
        }
        final BigDecimal number = DecimalText.parse(value.toString().trim());
        return number.stripTrailingZeros().scale() <= 0 ? number.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
            : number.stripTrailingZeros().toPlainString();
    }

    /** Rejection-inversion sampling of a Zipf variate in 1..n with this exponent. */
    private static long sample(final long n, final double exponent, final GeneratorDraws draws) {
        final double hIntegralX1 = hIntegral(1.5, exponent) - 1.0;
        final double hIntegralN = hIntegral(n + 0.5, exponent);
        final double squeeze = 2.0 - hIntegralInverse(hIntegral(2.5, exponent) - h(2.0, exponent), exponent);
        while (true) {
            final double u = hIntegralN + draws.canonical() * (hIntegralX1 - hIntegralN);
            final double x = hIntegralInverse(u, exponent);
            long k = (long) (x + 0.5);
            if (k < 1) {
                k = 1;
            } else if (k > n) {
                k = n;
            }
            if (k - x <= squeeze || u >= hIntegral(k + 0.5, exponent) - h(k, exponent)) {
                return k;
            }
        }
    }

    private static double h(final double x, final double exponent) {
        return Math.exp(-exponent * Math.log(x));
    }

    private static double hIntegral(final double x, final double exponent) {
        final double logX = Math.log(x);
        return expm1OverX((1.0 - exponent) * logX) * logX;
    }

    private static double hIntegralInverse(final double x, final double exponent) {
        double t = x * (1.0 - exponent);
        if (t < -1.0) {
            t = -1.0;
        }
        return Math.exp(log1pOverX(t) * x);
    }

    private static double log1pOverX(final double x) {
        return Math.abs(x) > 1e-8 ? Math.log1p(x) / x : 1.0 - x * (0.5 - x * (1.0 / 3.0 - 0.25 * x));
    }

    private static double expm1OverX(final double x) {
        return Math.abs(x) > 1e-8 ? Math.expm1(x) / x : 1.0 + x * 0.5 * (1.0 + x * (1.0 / 3.0) * (1.0 + 0.25 * x));
    }

    // The generator is required, as UNIFORM's is.
    @Override
    public int getMinArgCount() {
        return 3;
    }

    @Override
    public int getMaxArgCount() {
        return 3;
    }
}
