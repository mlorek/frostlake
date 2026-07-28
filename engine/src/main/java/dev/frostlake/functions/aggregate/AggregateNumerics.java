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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Numeric helpers shared by every SUM code path (the fast-path plan, the general grouped evaluator, the
 * generic dispatch and the window frame). SUM preserves its argument's integer-ness the way Snowflake does:
 * summing an INTEGER / NUMBER(p,0) column yields a whole number (SUM(i)::VARCHAR is "6", not "6.0"), while a
 * DECIMAL or FLOAT argument keeps the prior double result. The integer sum is exact (BigInteger), returned as
 * a {@code Long} when it fits and a {@code BigInteger} otherwise (Snowflake's NUMBER(38,0) range).
 */
public final class AggregateNumerics {

    private AggregateNumerics() {
    }

    /**
     * SUM over {@code values} (nulls are ignored). Returns null when no non-null value is present (Snowflake
     * SUM of an empty set is NULL). All-integer input yields a Long/BigInteger; any decimal/float input keeps
     * the double sum.
     */
    public static Object sum(final Iterable<Object> values) {
        boolean any = false;
        boolean allIntegral = true;
        double doubleSum = 0.0;
        BigInteger intSum = BigInteger.ZERO;
        for (final Object v : values) {
            if (v == null) {
                continue;
            }
            any = true;
            final BigInteger integral = integralValue(v);
            if (integral != null) {
                intSum = intSum.add(integral);
                doubleSum += integral.doubleValue();
            } else {
                allIntegral = false;
                doubleSum += toDouble(v);
            }
        }
        if (!any) {
            return null;
        }
        if (allIntegral) {
            if (intSum.bitLength() < 63) {
                return intSum.longValue();
            }
            return intSum;
        }
        return doubleSum;
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
