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
import java.util.regex.Pattern;

public class ToNumber extends BuiltInFunction {

    // Compiled once — these ran String.matches/replaceAll (a fresh Pattern compile) per CALL.
    private static final Pattern PLAIN_NUMBER =
        Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern SEPARATORS = Pattern.compile("[,$\\s]");
    public ToNumber() { super("TO_NUMBER", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        BigDecimal value = parseNumeric(args.get(0), formatModel(args));
        final Integer scale = targetScale(args);
        // Snowflake TO_NUMBER / TO_DECIMAL / TO_NUMERIC default to NUMBER(38,0): with no explicit scale the
        // result is a whole number (e.g. TO_NUMBER(405.958) -> 406), rounded HALF_UP.
        value = value.setScale(scale != null ? scale : 0, RoundingMode.HALF_UP);
        return value;
    }

    /**
     * The format model of a TO_NUMBER(expr [, format] [, precision, scale]) call: the first trailing
     * argument that is a string. A numeric second argument is a precision, not a format.
     */
    private String formatModel(final List<Object> args) {
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i) instanceof String) {
                return (String) args.get(i);
            }
        }
        return null;
    }

    /**
     * Parse a numeric value. Group separators, currency symbols and other decoration are accepted ONLY
     * when the format model declares them — live-verified on a real account:
     * {@code TO_NUMBER('1,234.56')} and {@code TO_NUMBER('$1,234.567', 10, 2)} both fail "Numeric value
     * '…' is not recognized", while {@code TO_NUMBER('1,234.56', '9,999.99')} and
     * {@code TO_NUMBER('$1,234.567','$9,999.000')} are 1235. With a format the input must FIT it:
     * {@code TO_NUMBER('405.958','9,999.99')} and {@code ('405.958','9,999')} fail "Can't parse '405.958'
     * as number with format '…'" (too many fraction digits) and {@code ('1,234.56','999999')} fails
     * because the format has no group separator, while {@code ('405.958','999.999')} is 406.
     */
    private BigDecimal parseNumeric(final Object o, final String format) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return new BigDecimal(o.toString());
        }
        final String text = o.toString().trim();
        if (format == null) {
            if (!PLAIN_NUMBER.matcher(text).matches()) {
                throw new RuntimeException("Numeric value '" + text + "' is not recognized");
            }
            return new BigDecimal(text);
        }
        if ((text.indexOf(',') >= 0 && format.indexOf(',') < 0)
                || (text.indexOf('$') >= 0 && format.indexOf('$') < 0)
                || fractionDigits(text) > fractionDigits(format)) {
            throw new RuntimeException("Can't parse '" + text + "' as number with format '" + format + "'");
        }
        return new BigDecimal(SEPARATORS.matcher(text).replaceAll(""));
    }

    /** How many digits follow the decimal point in a numeric literal or a format model. */
    private int fractionDigits(final String text) {
        final int dot = text.indexOf('.');
        return dot < 0 ? 0 : text.length() - dot - 1;
    }

    /**
     * The target scale of a TO_NUMBER(expr [, format] [, precision, scale]) call, or {@code null} when the
     * call carries no scale information (caller then defaults to 0, matching NUMBER(38,0)): when the trailing
     * arguments include two integers (precision, scale), the second is the scale. A format string governs
     * PARSING only — it never implies a scale, so TO_NUMBER('1,234.56', '9,999.99') is 1235 while
     * TO_NUMBER('1,234.56', '9,999.99', 10, 2) is 1234.56 (live-verified). Precision itself is not enforced.
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
        if (integerArgs.size() >= 2) {
            return integerArgs.get(integerArgs.size() - 1);
        }
        return null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 4; }
}
