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

import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RANDOM([seed]) — a pseudo-random signed 64-bit integer (not a fraction in [0, 1)). Without a seed
 * each call is independent; with a seed the value is deterministic, so a constant seed yields the same
 * value for every row (matching Snowflake). java.util.Random is fully qualified because this class
 * shadows that name.
 *
 * <p>The seedless path is genuinely random, as Snowflake's is. It once returned a SplitMix64 scramble
 * of a monotonic counter for test reproducibility; that was removed because nothing depended on it —
 * no test asserts a value (they assert type, range and uniqueness, which real randomness satisfies and
 * the counter satisfied only trivially) and the vendor suite never calls the function. The counter also
 * restarted at zero in a fresh JVM, so it never delivered the WAL-replay exactness it appeared to
 * promise: a replayed statement drew new values either way. Reproducing values across replay is a
 * durability question about the log, not something a function can answer for itself.
 */
public class Random extends BuiltInFunction {
    // The account declares RANDOM() as NUMBER(19,0) — SYSTEM$TYPEOF(RANDOM()) reads NUMBER(19,0)[SB8],
    // and a refusal listing it as an argument spells the same pair.
    public Random() { super("RANDOM", new NumericType("NUMBER", 19, 0)); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (!args.isEmpty()) {
            return new java.util.Random(seedOf(args.get(0))).nextLong();
        }
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * The seed as a whole number, rounded half away from zero — {@code RANDOM(1.5)}, {@code RANDOM('1.5')} and
     * {@code RANDOM(1.5::FLOAT)} are {@code RANDOM(2)} (live-verified). A text seed is read as the number it
     * spells; a VARIANT reads through its member as a cast to FIXED does, a boolean as 1 or 0, and anything
     * else fails that cast. A NULL seed, SQL or JSON, is refused when the row reads it.
     */
    private static final String NULL_SEED = "Invalid parameter value: NULL. Reason: seed must not be NULL";

    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);

    /** The widest whole number a seed read from text, a FLOAT or a VARIANT may be: eighteen digits. */
    private static final BigDecimal EIGHTEEN_NINES = new BigDecimal("999999999999999999");

    /** A text that spells more digits than this before its decimal point fits no 64-bit seed. */
    private static final int MOST_WHOLE_DIGITS = 40;

    /**
     * The seed a RANDOM argument gives, rounded half away from zero, and the range it has to fit, which is its
     * family's own (live-verified):
     * <ul>
     *   <li>a NUMBER takes the whole signed 64-bit range, and past it is {@code Numeric value '<the rounded
     *       number>' is not recognized};</li>
     *   <li>text is read as a number, which may have a sign, a fraction and an exponent: text that is none, or one
     *       past 64 bits, is {@code not recognized}, and one past eighteen digits is {@code out of range}, both
     *       quoting the text as written;</li>
     *   <li>a FLOAT becomes a FIXED[SB8](18,0), eighteen digits, and past it, or NaN or an infinity, is
     *       {@code Number out of representable range};</li>
     *   <li>a VARIANT number takes eighteen digits too, in the same words over the plain type FIXED, and a
     *       VARIANT text that is no seed is {@code Failed to cast variant value}.</li>
     * </ul>
     */
    private static long seedOf(final Object seed) {
        if (seed == null) {
            throw new RuntimeException(NULL_SEED);
        }
        if (seed instanceof VariantValue) {
            return variantSeed((VariantValue) seed);
        }
        if (seed instanceof Double || seed instanceof Float) {
            return floatSeed(((Number) seed).doubleValue(), "FIXED[SB8](18,0){not null}");
        }
        if (seed instanceof Number) {
            final BigDecimal rounded = (seed instanceof BigDecimal ? (BigDecimal) seed : new BigDecimal(seed.toString()))
                .setScale(0, RoundingMode.HALF_UP);
            if (rounded.compareTo(LONG_MAX) > 0 || rounded.compareTo(LONG_MIN) < 0) {
                throw new RuntimeException("Numeric value '" + rounded.toPlainString() + "' is not recognized");
            }
            return rounded.longValueExact();
        }
        final String text = seed.toString();
        final BigDecimal rounded = wholeNumberIn(text);
        if (rounded == null) {
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
        if (rounded.abs().compareTo(EIGHTEEN_NINES) > 0) {
            throw new RuntimeException("Numeric value '" + text + "' is out of range");
        }
        return rounded.longValueExact();
    }

    /** The whole number a text spells, rounded, or null when it spells none or one past 64 bits. */
    private static BigDecimal wholeNumberIn(final String text) {
        final BigDecimal number;
        try {
            number = new BigDecimal(text.trim());
        } catch (final NumberFormatException notANumber) {
            return null;
        }
        if (number.precision() - number.scale() > MOST_WHOLE_DIGITS) {
            return null;
        }
        final BigDecimal rounded = number.setScale(0, RoundingMode.HALF_UP);
        return rounded.compareTo(LONG_MAX) > 0 || rounded.compareTo(LONG_MIN) < 0 ? null : rounded;
    }

    private static long floatSeed(final double value, final String type) {
        if (Double.isFinite(value)) {
            final BigDecimal rounded = new BigDecimal(value).setScale(0, RoundingMode.HALF_UP);
            if (rounded.abs().compareTo(EIGHTEEN_NINES) <= 0) {
                return rounded.longValueExact();
            }
        }
        throw new RuntimeException("Number out of representable range: type " + type + ", value "
            + generalText(value));
    }

    private static long variantSeed(final VariantValue variant) {
        final JsonNode node = variant.node();
        if (node == null || node.isNull()) {
            throw new RuntimeException(NULL_SEED);
        }
        if (node.isTextual()) {
            final BigDecimal rounded = wholeNumberIn(node.asText());
            if (rounded == null || rounded.abs().compareTo(EIGHTEEN_NINES) > 0) {
                throw VariantNumbers.refusal(node, VariantNumbers.FIXED);
            }
            return rounded.longValueExact();
        }
        if (node.isFloatingPointNumber() && !node.isBigDecimal()) {
            return floatSeed(node.doubleValue(), VariantNumbers.FIXED);
        }
        // A whole or decimal number, a BOOLEAN as 1 or 0; an ARRAY or an OBJECT is refused here.
        final Number member = VariantNumbers.numberOf(variant, VariantNumbers.FIXED);
        final BigDecimal rounded = (member instanceof BigDecimal ? (BigDecimal) member : new BigDecimal(member.toString()))
            .setScale(0, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(EIGHTEEN_NINES) > 0) {
            throw new RuntimeException("Number out of representable range: type FIXED, value "
                + rounded.toPlainString());
        }
        return rounded.longValueExact();
    }

    /**
     * A double as the range refusal spells it: six significant digits with no trailing zeros, an exponent from
     * 1e+06 up and below 1e-04, and {@code nan} and {@code inf} for the values that are no number.
     */
    private static String generalText(final double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        final String scientific = String.format(Locale.ROOT, "%.5e", value);
        final int mark = scientific.indexOf('e');
        final int exponent = Integer.parseInt(scientific.substring(mark + 1));
        if (exponent >= -4 && exponent < 6) {
            return new BigDecimal(value).round(new MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros()
                .toPlainString();
        }
        String mantissa = scientific.substring(0, mark);
        while (mantissa.endsWith("0")) {
            mantissa = mantissa.substring(0, mantissa.length() - 1);
        }
        if (mantissa.endsWith(".")) {
            mantissa = mantissa.substring(0, mantissa.length() - 1);
        }
        return mantissa + scientific.substring(mark);
    }

    /** An ARRAY or OBJECT seed is refused by the argument types while the statement compiles (live-verified). */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A BOOLEAN seed is refused by the argument types while the statement compiles (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** So is a DATE, a TIME or a TIMESTAMP seed: RANDOM(CURRENT_DATE()) is 'RANDOM': (DATE) (live-verified). */
    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 1; }
}
