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

package dev.frostlake.functions.aggregate;

import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Numeric helpers shared by every SUM / AVG code path (the fast-path plan, the general grouped evaluator,
 * the generic dispatch, the accumulators and the window frame). SUM preserves its argument's integer-ness
 * the way Snowflake does: summing an INTEGER / NUMBER(p,0) column yields a whole number (SUM(i)::VARCHAR is
 * "6", not "6.0"), while a DECIMAL or FLOAT argument keeps the prior double result. The integer sum is exact
 * (BigInteger), returned as a {@code Long} when it fits and a {@code BigInteger} otherwise (Snowflake's
 * NUMBER(38,0) range). A VARIANT input makes either aggregate DOUBLE (live-verified: SUM over
 * PARSE_JSON('1'), PARSE_JSON('2') is 3.0 with SYSTEM$TYPEOF FLOAT). AVG of fixed-point inputs is a
 * BigDecimal with scale = (max input scale) + 6, rounded HALF_UP (live-verified: AVG of the integers 90 and
 * 95 is exactly 92.500000).
 */
public final class AggregateNumerics {

    private AggregateNumerics() {
    }

    /**
     * SUM over {@code values} (nulls are ignored). Returns null when no non-null value is present (Snowflake
     * SUM of an empty set is NULL). All-integer input yields a Long/BigInteger; any decimal/float/VARIANT
     * input keeps the double sum (live-verified: SUM over a VARIANT column is DOUBLE even for whole-number
     * JSON values).
     *
     * @param values the aggregated argument values, in group order
     * @return the sum — Long/BigInteger for all-integral input, BigDecimal for fixed-point, Double
     *     otherwise — or null for an empty set
     */
    public static Object sum(final Iterable<Object> values) {
        return sum(values, false);
    }

    /**
     * The {@code variantArgument} flag is for a SUM whose ARGUMENT is declared VARIANT even though the
     * runtime values arrive as plain numbers — live, {@code SUM(v:b)} over
     * whole-number JSON values is DOUBLE (7.0, SYSTEM$TYPEOF FLOAT): the declared type decides the
     * tier exactly as a runtime {@link VariantValue} does.
     *
     * @param values the aggregated argument values, in group order
     * @param variantArgument whether the argument expression is statically VARIANT-typed, which forces
     *     the DOUBLE tier
     * @return the sum on the tier the inputs select, or null for an empty set
     */
    public static Object sum(final Iterable<Object> values, final boolean variantArgument) {
        // Three tiers, live-verified: integral inputs sum to an integer; FIXED-POINT decimals (incl.
        // scale-0 BigDecimals produced by ::NUMBER casts) sum to a BigDecimal keeping their scale
        // (SUM over NUMBER(5,1) is NUMBER(17,1)); any FLOAT or VARIANT input makes the sum DOUBLE.
        boolean any = false;
        boolean anyDouble = variantArgument;
        boolean anyDecimalScale = false;
        double doubleSum = 0.0;
        BigDecimal decimalSum = BigDecimal.ZERO;
        for (final Object v : values) {
            if (v == null) {
                continue;
            }
            any = true;
            if (v instanceof VariantValue) {
                anyDouble = true;
                doubleSum += variantDouble((VariantValue) v);
                continue;
            }
            if (v instanceof Double || v instanceof Float) {
                anyDouble = true;
                doubleSum += ((Number) v).doubleValue();
                continue;
            }
            final BigDecimal fixed = fixedPointValue(v);
            if (fixed != null) {
                if (fixed.stripTrailingZeros().scale() > 0) {
                    anyDecimalScale = true;
                }
                decimalSum = decimalSum.add(fixed);
                continue;
            }
            anyDouble = true;
            doubleSum += toDouble(v);
        }
        if (!any) {
            return null;
        }
        if (anyDouble) {
            // A FLOAT/VARIANT input flips the whole sum to double; fixed-point contributions join it.
            return doubleSum + decimalSum.doubleValue();
        }
        if (anyDecimalScale) {
            return decimalSum;
        }
        final BigInteger integralSum = decimalSum.toBigIntegerExact();
        if (integralSum.bitLength() < 63) {
            return integralSum.longValue();
        }
        return integralSum;
    }

    /**
     * AVG over {@code values} (nulls are ignored). Returns null when no non-null value is present (Snowflake
     * AVG of an empty set is NULL). All fixed-point input (Long/Integer/BigDecimal — not Double/Float) yields
     * a BigDecimal with scale = (max input scale) + 6, rounded HALF_UP and with trailing zeros KEPT — AVG of
     * the integers 90 and 95 is exactly 92.500000 and AVG(2, 2) renders 2.000000, matching live Snowflake.
     * Any Double/Float or VARIANT input keeps the double average.
     *
     * @param values the aggregated argument values, in group order
     * @return the average — a scaled BigDecimal for all fixed-point input, a Double otherwise — or
     *     null for an empty set
     */
    public static Object avg(final Iterable<Object> values) {
        return avg(values, false);
    }

    /**
     * See {@link #sum(Iterable, boolean)} — the same declared-VARIANT rule applied to AVG.
     *
     * @param values the aggregated argument values, in group order
     * @param variantArgument whether the argument expression is statically VARIANT-typed, which keeps
     *     the average on the double path
     * @return the average on the tier the inputs select, or null for an empty set
     */
    public static Object avg(final Iterable<Object> values, final boolean variantArgument) {
        boolean any = false;
        boolean anyDouble = variantArgument;
        int maxScale = 0;
        BigDecimal decimalSum = BigDecimal.ZERO;
        double doubleSum = 0.0;
        long count = 0L;
        for (final Object v : values) {
            if (v == null) {
                continue;
            }
            any = true;
            count++;
            if (v instanceof VariantValue) {
                anyDouble = true;
                doubleSum += variantDouble((VariantValue) v);
                continue;
            }
            final BigDecimal fixed = fixedPointValue(v);
            if (fixed == null) {
                anyDouble = true;
                doubleSum += toDouble(v);
                continue;
            }
            decimalSum = decimalSum.add(fixed);
            doubleSum += fixed.doubleValue();
            if (fixed.scale() > maxScale) {
                maxScale = fixed.scale();
            }
        }
        if (!any) {
            return null;
        }
        if (anyDouble) {
            return doubleSum / count;
        }
        return decimalSum.divide(BigDecimal.valueOf(count), maxScale + 6, RoundingMode.HALF_UP);
    }

    /**
     * The exact whole-number value of {@code v} when it is an integer-typed number (Long/Integer/Short/Byte/
     * BigInteger) or a numeric string with no fractional part, else null. A BigDecimal / Double / Float is
     * deliberately treated as non-integral so a DECIMAL / FLOAT column's SUM stays a double.
     */
    private static BigInteger integralValue(final Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return BigInteger.valueOf(((Number) v).longValue());
        }
        if (v instanceof BigInteger) {
            return (BigInteger) v;
        }
        if (v instanceof BigDecimal) {
            // A scale-0 BigDecimal (e.g. a ::NUMBER cast result) IS integral; a scaled one is not.
            final BigDecimal decimal = (BigDecimal) v;
            return decimal.stripTrailingZeros().scale() <= 0 ? decimal.toBigIntegerExact() : null;
        }
        if (v instanceof Number) {
            return null;
        }
        try {
            final BigDecimal decimal = new BigDecimal(v.toString().trim());
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return decimal.toBigIntegerExact();
            }
        } catch (final NumberFormatException notNumeric) {
            // fall through — treated as non-integral / zero
        }
        return null;
    }

    /**
     * The exact fixed-point value of {@code v} when it is an integer-typed number (scale 0), a BigDecimal
     * (its own scale), or an integral numeric string, else null. Double / Float — and fractional strings,
     * mirroring {@link #integralValue(Object)} — are deliberately non-fixed-point so they keep AVG on the
     * double path.
     */
    private static BigDecimal fixedPointValue(final Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof BigInteger) {
            return new BigDecimal((BigInteger) v);
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return null;
        }
        try {
            final BigDecimal decimal = new BigDecimal(v.toString().trim());
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return new BigDecimal(decimal.toBigIntegerExact());
            }
        } catch (final ArithmeticException | NumberFormatException notIntegral) {
            // fall through — treated as non-fixed-point
        }
        return null;
    }

    /**
     * The double value of a numeric VARIANT (a JSON number, or a JSON string holding a number). Live
     * Snowflake makes SUM / AVG over VARIANT values DOUBLE regardless of the JSON number's shape.
     */
    private static double variantDouble(final VariantValue v) {
        final JsonNode node = v.node();
        if (node.isNumber()) {
            return node.decimalValue().doubleValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim()).doubleValue();
            } catch (final NumberFormatException notNumeric) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static double toDouble(final Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return new BigDecimal(v.toString().trim()).doubleValue();
        } catch (final NumberFormatException notNumeric) {
            return 0.0;
        }
    }
}
