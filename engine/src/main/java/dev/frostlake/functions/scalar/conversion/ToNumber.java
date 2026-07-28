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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class ToNumber extends BuiltInFunction {
    public ToNumber() { super("TO_NUMBER", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        BigDecimal value = parseNumeric(args.get(0));
        final Integer scale = targetScale(args);
        // Snowflake TO_NUMBER / TO_DECIMAL / TO_NUMERIC default to NUMBER(38,0): with no explicit scale the
        // result is a whole number (e.g. TO_NUMBER(405.958) -> 406), rounded HALF_UP.
        value = value.setScale(scale != null ? scale : 0, RoundingMode.HALF_UP);
        return value;
    }

    /** Parse a numeric value, tolerating group separators, currency symbols and spaces in strings. */
    private BigDecimal parseNumeric(final Object o) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return new BigDecimal(o.toString());
        }
        return new BigDecimal(o.toString().trim().replaceAll("[,$\\s]", ""));
    }

    /**
     * The target scale of a TO_NUMBER(expr [, format] [, precision, scale]) call, or {@code null} when the
     * call carries no scale information (caller then defaults to 0, matching NUMBER(38,0)): when the trailing
     * arguments include two integers (precision, scale), the second is the scale; otherwise a format string
     * (e.g. '9,999.99') implies a scale equal to its fractional digit count. Precision itself is not enforced.
     */
    private Integer targetScale(final List<Object> args) {
        final List<Integer> integerArgs = new ArrayList<>();
        String format = null;
        for (int i = 1; i < args.size(); i++) {
            final Object a = args.get(i);
            if (a instanceof Number) {
                final double d = ((Number) a).doubleValue();
                if (!Double.isInfinite(d) && d == Math.floor(d)) {
                    integerArgs.add((int) d);
                }
            } else if (a != null && format == null) {
                format = a.toString();
            }
        }
        if (integerArgs.size() >= 2) {
            return integerArgs.get(integerArgs.size() - 1);
        }
        if (format != null) {
            return scaleFromFormat(format);
        }
        return null;
    }

    /** Scale implied by a numeric format model: the count of digit placeholders after its decimal point. */
    private Integer scaleFromFormat(final String format) {
        final int dot = format.indexOf('.');
        if (dot < 0) {
            return 0;
        }
        int count = 0;
        for (final char ch : format.substring(dot + 1).toCharArray()) {
            if (ch == '0' || ch == '9') {
                count++;
            }
        }
        return count;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 4; }
}
