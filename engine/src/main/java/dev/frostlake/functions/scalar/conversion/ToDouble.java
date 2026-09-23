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
import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.HexDoubleText;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantValue;

import java.util.List;

/**
 * TO_DOUBLE(expr [, format]). A text with no format is read as the FLOAT cast reads it, and refused with
 * the cast's own sentence (all live-verified):
 *
 * <pre>
 *   TO_DOUBLE(' 1.5 ')      1.5       the text is trimmed
 *   TO_DOUBLE('NaN')        NaN       'nan', 'inf', '-inf', 'Infinity' alike, as '…'::DOUBLE reads them
 *   TO_DOUBLE('1e400')      inf
 *   TO_DOUBLE('-0x10')      -16       a hexadecimal number, see HexDoubleText
 *   TO_DOUBLE('abc')        Numeric value 'abc' is not recognized
 *   TO_DOUBLE(' ')          Numeric value '' is not recognized       the text echoed trimmed
 * </pre>
 */
public class ToDouble extends BuiltInFunction {
    public ToDouble() { super("TO_DOUBLE", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof Number) return ((Number) args.get(0)).doubleValue();
        // A VARIANT reads through its member, and anything that is no number fails its cast to REAL.
        if (args.get(0) instanceof VariantValue) {
            final Number member = VariantNumbers.numberOf((VariantValue) args.get(0), VariantNumbers.REAL);
            return member == null ? null : Double.valueOf(member.doubleValue());
        }
        if (args.size() > 1 && args.get(1) != null) {
            return formatted(args.get(0).toString(), args.get(1).toString(), NumericFormatModel.REAL);
        }
        return textValue(args.get(0).toString());
    }

    /**
     * A text read as the FLOAT cast reads it: trimmed, a non-finite word accepted, and anything else that
     * spells no number refused with the cast's sentence, the text echoed trimmed.
     *
     * @param raw the text as written
     * @return the double it spells
     */
    static Double textValue(final String raw) {
        final String text = raw.trim();
        final Double nonFinite = NonFiniteDoubles.parseForCast(text);
        if (nonFinite != null) {
            return nonFinite;
        }
        try {
            return Double.valueOf(Double.parseDouble(text));
        } catch (final NumberFormatException notNumeric) {
            final Double hex = HexDoubleText.withoutExponent(text, true);
            if (hex != null) {
                return hex;
            }
            throw new RuntimeException(NumericRangeRefusal.unreadableText(text));
        }
    }

    /**
     * A text read under a numeric FORMAT model, which only a text source takes (a number beside a
     * format is too many arguments, refused before a row is read): the model is checked first and a
     * model that is not one is refused naming {@code target} — live, TO_DOUBLE('1e5', '9e9') is "Bad
     * input format model '9e9' for REAL: invalid numeric format keyword: 'e9'" — then the text must fit
     * it, group separators and currency included, as TO_NUMBER reads them: TO_DOUBLE('1,234.5',
     * '9,999.9') is 1234.5 and TO_DOUBLE('123', '99') "Can't parse '123' as number with format '99'"
     * (see {@link NumericFormatModel}).
     *
     * @param text   the source text
     * @param format the format model
     * @param target the target a refusal of the model names
     * @return the double
     */
    static Double formatted(final String text, final String format, final String target) {
        return Double.valueOf(NumericFormatModel.readOrRefuse(text, format, target).doubleValue());
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
