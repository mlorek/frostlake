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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A value PRESENTED at the type a conditional fold declares — the conversion the account plans as a
 * CAST on every branch, so the branch handed back reads at the fold whatever it was written as:
 *
 * <pre>
 *   LAG(a, 1, 0)      over a NUMBER(10,2)     0 reads 0.00, a reads 1.00
 *   LAG(a, 1, 1.555)  over a NUMBER(10,2)     the fold is NUMBER(11,3): a reads 1.000
 *   LAG(a, 1, TRUE)   over a NUMBER(10,2)     the fold is BOOLEAN: 1.00 reads true
 *   LAG(d, 1, ts)     over a DATE             the fold is TIMESTAMP_NTZ: d reads 2020-01-01 00:00:00.000
 *   LAG(a, 1, 'x')    over a NUMBER(10,2)     the fold is NUMBER(18,5): 'x' is refused as not a number
 * </pre>
 *
 * <p>An exact NUMBER fold sets the scale; a FLOAT fold takes the double; a BOOLEAN or temporal fold
 * casts; a string fold leaves the value alone. No fold, no change.
 */
public final class FoldedValues {

    private FoldedValues() {
    }

    /**
     * @param value    the branch's own value
     * @param declared the fold, or null when none is known
     * @return the value at the fold
     */
    public static Object presented(final Object value, final DataType declared) {
        if (value == null || declared == null) {
            return value;
        }
        if (declared instanceof NumericType) {
            if (NumericType.isApproximate(declared)) {
                return value instanceof Number
                    ? Double.valueOf(((Number) value).doubleValue()) : ValueCaster.castValue(value, "FLOAT");
            }
            final NumericType fold = (NumericType) declared;
            final int scale = fold.getScale();
            // Text is read at the fold's own width, so '7.5' keeps its digits before the scale is set
            // and 'x' is refused as not a number; an integral value at a scale of zero stays the
            // integer it was.
            final Object exact = value instanceof Number ? value
                : ValueCaster.castValue(value, "NUMBER(" + fold.getPrecision() + "," + scale + ")");
            if (exact instanceof Double || exact instanceof Float || !(exact instanceof Number)) {
                return exact;
            }
            if (scale == 0 && !(exact instanceof BigDecimal)) {
                return exact;
            }
            final BigDecimal decimal = exact instanceof BigDecimal
                ? (BigDecimal) exact : new BigDecimal(exact.toString());
            return decimal.scale() == scale ? decimal : decimal.setScale(scale, RoundingMode.HALF_UP);
        }
        if (declared instanceof BooleanType) {
            // A BOOLEAN fold converts every branch the way a cast does: a number is its zero test,
            // a text is read strictly ('yes', 'no', 'on', 'off', 't', 'f', '1', '0' and the two words)
            // and anything else is live's row-time sentence, "Boolean value 'x' is not recognized".
            return value instanceof Boolean ? value : ExpressionArithmetic.strictBooleanOrNull(value);
        }
        if (declared instanceof DateTimeType) {
            // A temporal fold hands the chosen branch back AS the fold: a DATE beside a TIMESTAMP is
            // the midnight timestamp, an NTZ beside an LTZ moves to the session zone, and a text is
            // read as the fold's type — "Date 'abc' is not recognized" when it reads as none.
            return ValueCaster.castValue(value, declared.getName());
        }
        return value;
    }
}
