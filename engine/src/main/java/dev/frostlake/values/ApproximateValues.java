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

/**
 * The APPROXIMATE family's runtime carrier is a Java double, and these are the rules that follow from it.
 *
 * <p>★ A FLOAT COLUMN HOLDS A DOUBLE. Live-verified on every write path — INSERT … VALUES, INSERT …
 * SELECT, UPDATE, MERGE, CTAS, a column DEFAULT, a text value, an integer — a value written into a
 * FLOAT column becomes the nearest double: a stored {@code 0.1} reads back as
 * {@code 0.10000000000000000555} through {@code ::NUMBER(38,20)}, a stored nineteen-digit integer
 * becomes {@code 1234567890123456768}, and a stored {@code -0.0::FLOAT} keeps its sign. Frostlake used
 * to keep whatever class the value arrived in, so the same column held a BigDecimal, a Long or a
 * Double depending on how the row was written, and every one of those cells disagreed with the account.
 *
 * <p>★ A FLOAT BESIDE AN EXACT NUMBER COMPARES AS A DOUBLE. Live answers TRUE for
 * {@code 1234567890123456789::FLOAT = 1234567890123456768} and for
 * {@code 0.1::FLOAT = 0.10000000000000000555}: the exact side is converted to a double and the two
 * doubles are compared. Comparing through the double's shortest decimal spelling, as this engine did,
 * made both FALSE.
 *
 * <p>★ NEGATIVE ZERO IS A VALUE OF ITS OWN, and equal to zero. {@code -0.0::FLOAT = 0.0::FLOAT} is TRUE
 * and {@code -0.0::FLOAT < 0} is FALSE, while a tie between the two keeps whichever was seen FIRST —
 * GREATEST, LEAST, MIN and MAX all answer {@code -0} when it leads. ORDER BY is the one place with a
 * tie-break: {@code -0} sorts before {@code 0}.
 */
public final class ApproximateValues {

    private ApproximateValues() {
    }

    /**
     * The double a value takes when written into an approximate column, or null when the value is not
     * numeric text or a number at all (a boolean, a variant) and the caller keeps it as it is.
     *
     * @param value the value as the write produced it
     * @return its double, or null
     */
    public static Double written(final Object value) {
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof CharSequence) {
            final String text = value.toString().trim();
            final Double nonFinite = NonFiniteDoubles.parseForCast(text);
            if (nonFinite != null) {
                return nonFinite;
            }
            try {
                return Double.valueOf(Double.parseDouble(text));
            } catch (final NumberFormatException notNumeric) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        return null;
    }

    /**
     * Whether a runtime value is carried as a double.
     *
     * @param value any value
     * @return true for a Double or a Float
     */
    public static boolean isApproximate(final Object value) {
        return value instanceof Double || value instanceof Float;
    }

    /**
     * Two doubles compared as VALUES: {@code -0.0} and {@code 0.0} are equal, so a tie keeps the first
     * seen. NaN keeps its place in the total order — it equals itself and outranks everything.
     *
     * @param left  one value
     * @param right the other
     * @return negative, zero or positive
     */
    public static int compare(final double left, final double right) {
        if (left < right) {
            return -1;
        }
        if (left > right) {
            return 1;
        }
        return left == right ? 0 : Double.compare(left, right);
    }

    /**
     * Two doubles compared for SORTING, which is the one place the two zeros are told apart: {@code -0}
     * sorts before {@code 0}, as it does on the account.
     *
     * @param left  one value
     * @param right the other
     * @return negative, zero or positive
     */
    public static int order(final double left, final double right) {
        if (left < right) {
            return -1;
        }
        if (left > right) {
            return 1;
        }
        return Double.compare(left, right);
    }
}
