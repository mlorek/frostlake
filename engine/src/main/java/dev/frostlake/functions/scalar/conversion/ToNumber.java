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
        if (scale != null) {
            value = value.setScale(scale, RoundingMode.HALF_UP);
        }
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
     * The target scale of a TO_NUMBER(expr [, format] [, precision, scale]) call: when the trailing
     * arguments include two integers (precision, scale), the second is the scale and the value is
     * rounded to it. Precision itself is not enforced; a lone format string / precision has no scale.
     */
    private Integer targetScale(final List<Object> args) {
        final List<Integer> integerArgs = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            final Object a = args.get(i);
            if (a instanceof Number) {
                final double d = ((Number) a).doubleValue();
                if (!Double.isInfinite(d) && d == Math.floor(d)) {
                    integerArgs.add((int) d);
                }
            }
        }
        return integerArgs.size() >= 2 ? integerArgs.get(integerArgs.size() - 1) : null;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 4; }
}
