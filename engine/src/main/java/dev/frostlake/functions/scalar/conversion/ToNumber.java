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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SignedStorageWidth;
import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ToNumber extends BuiltInFunction {

    // Compiled once — this ran String.matches (a fresh Pattern compile) per CALL.
    private static final Pattern PLAIN_NUMBER =
        Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    public ToNumber() { super("TO_NUMBER", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // A SQL BOOLEAN converts to the unscaled 1 or 0 and IGNORES the declared width: live answers
        // 1 for TO_NUMBER(TRUE), 0 for TO_NUMBER(FALSE) and 1 — not 1.0 — for TO_DECIMAL(TRUE, 5, 1),
        // all typed NUMBER(2,0); an approximate conversion of the same value is refused at compile time.
        if (args.get(0) instanceof Boolean) {
            return ((Boolean) args.get(0)).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        // A VARIANT reads through its member — a JSON number, a boolean as 1 / 0, numeric text — and
        // anything else fails the variant cast to FIXED (live: TO_NUMBER over {"x":1}).
        Object source = args.get(0);
        if (source instanceof VariantValue) {
            final Number member = VariantNumbers.numberOf((VariantValue) source, VariantNumbers.FIXED);
            if (member == null) {
                return null;
            }
            source = member;
        }
        if (source instanceof DayTimeInterval) {
            // An interval converts to its seconds, as its cast does: 90000 for +1 01:00:00 (live-verified).
            source = ((DayTimeInterval) source).seconds();
        }
        BigDecimal value = parseNumeric(source, formatModel(args));
        final Integer scale = targetScale(args);
        rejectPastDeclaredWidth(args, value, scale == null ? 0 : scale.intValue());
        // Snowflake TO_NUMBER / TO_DECIMAL / TO_NUMERIC default to NUMBER(38,0): with no explicit scale the
        // result is a whole number (e.g. TO_NUMBER(405.958) -> 406), rounded HALF_UP.
        value = value.setScale(scale != null ? scale : 0, RoundingMode.HALF_UP);
        return value;
    }

    /**
     * A value the call's own declared width cannot hold. The precision argument IS enforced — a
     * {@code TO_NUMBER(1000, 2)} refuses exactly as {@code CAST(1000 AS NUMBER(2,0))} does, and a lone
     * precision argument means scale 0 — and the two source families keep the two sentences they have
     * everywhere else: a STRING is named as text, with the decoration it was WRITTEN with rather than the
     * digits the format model left behind.
     *
     * @param args the call's arguments, for the source's form and the declared width
     * @param value the parsed value
     * @param scale the declared scale, zero when the call names only a precision
     */
    private void rejectPastDeclaredWidth(final List<Object> args, final BigDecimal value, final int scale) {
        // Past what any number holds the text was never readable as one, and live says so whether or
        // not the call declares a width — which is why this comes before the target is looked at.
        if (args.get(0) instanceof CharSequence
                && NumericRangeRefusal.pastEveryNumber(value, scale)) {
            throw new RuntimeException(
                NumericRangeRefusal.unreadableText(args.get(0).toString().trim()));
        }
        final Integer precision = targetPrecision(args);
        if (precision == null || !NumericRangeRefusal.exceeds(value, precision.intValue(), scale)) {
            return;
        }
        if (args.get(0) instanceof CharSequence) {
            throw new RuntimeException(NumericRangeRefusal.unconvertibleText(args.get(0).toString().trim()));
        }
        throw new RuntimeException(NumericRangeRefusal.typed(SignedStorageWidth.tagOf(value, scale),
            precision.intValue(), scale, false, value));
    }

    /**
     * The declared precision of a TO_NUMBER(expr [, format] [, precision [, scale]]) call — the FIRST of
     * the trailing integers, or {@code null} when the call declares no width at all and the NUMBER(38,0)
     * default applies.
     */
    private Integer targetPrecision(final List<Object> args) {
        final List<Integer> integerArgs = integerArguments(args);
        return integerArgs.isEmpty() ? null : integerArgs.get(0);
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
     * {@code TO_NUMBER('$1,234.567','$9,999.000')} are 1235. With a format the model is checked first —
     * {@code TO_NUMBER('1e5', '9e9')} is "Bad input format model '9e9' for FIXED: invalid numeric format
     * keyword: 'e9'" — and the input must FIT it: {@code TO_NUMBER('405.958','9,999.99')} fails "Can't
     * parse '405.958' as number with format '…'" (too many fraction digits), as do
     * {@code ('123','99')} (too many digits) and {@code ('1,234.56','999999')} (the format has no group
     * separator), while {@code ('405.958','999.999')} is 406 (see {@link NumericFormatModel}).
     */
    static BigDecimal parseNumeric(final Object o, final String format) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return new BigDecimal(o.toString());
        }
        if (format != null) {
            return NumericFormatModel.readOrRefuse(o.toString(), format, NumericFormatModel.FIXED);
        }
        final String text = o.toString().trim();
        if (!PLAIN_NUMBER.matcher(text).matches()) {
            throw new RuntimeException(NumericRangeRefusal.unreadableText(text));
        }
        return new BigDecimal(text);
    }

    /**
     * The target scale of a TO_NUMBER(expr [, format] [, precision, scale]) call, or {@code null} when the
     * call carries no scale information (caller then defaults to 0, matching NUMBER(38,0)): when the trailing
     * arguments include two integers (precision, scale), the second is the scale. A format string governs
     * PARSING only — it never implies a scale, so TO_NUMBER('1,234.56', '9,999.99') is 1235 while
     * TO_NUMBER('1,234.56', '9,999.99', 10, 2) is 1234.56 (live-verified). Precision itself is not enforced.
     */
    private Integer targetScale(final List<Object> args) {
        final List<Integer> integerArgs = integerArguments(args);
        if (integerArgs.size() >= 2) {
            return integerArgs.get(integerArgs.size() - 1);
        }
        return null;
    }

    /** The trailing INTEGER arguments, in order — the (precision, scale) pair a call may declare. */
    private List<Integer> integerArguments(final List<Object> args) {
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
        return integerArgs;
    }

    /** A VECTOR is refused as the conversion's own invalid type, as TO_CHAR refuses it. */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.INVALID_TYPE_PARAMETER
            : SemiStructuredRejection.NONE;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 4; }
}
