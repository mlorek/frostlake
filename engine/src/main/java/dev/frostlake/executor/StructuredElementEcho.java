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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.CallEcho;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * How an echo of the expression a statement compiled spells a call the plan holds in another form. A call that
 * appends an element to a structured ARRAY has the element converted to the array's element type as the plan
 * converts it, a text into a NUMBER(p,s) element as {@code TO_NUMBER(text, p, s)} and a number as written
 * (live-verified, for ARRAY_APPEND in a VALUES clause:
 * {@code ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS ARRAY(NUMBER(10,2))), TO_NUMBER('zz', 10, 2))}). A conversion to
 * VARIANT is the cast it is, a number converted to FLOAT in it folded to that FLOAT: TO_VARIANT(0.1::FLOAT) is
 * {@code CAST(0.1 AS VARIANT)} (live-verified).
 */
public final class StructuredElementEcho implements CallEcho {

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
        if ((call.getNameParts() == null || call.getNameParts().size() == 1) && "TO_VARIANT".equals(name)
                && call.getArguments().size() == 1) {
            final String folded = floatConstant(call.getArguments().get(0));
            return "CAST(" + (folded != null ? folded : printer.printed(call.getArguments().get(0))) + " AS VARIANT)";
        }
        if (!"ARRAY_APPEND".equals(name) || call.getArguments().size() != 2) {
            return null;
        }
        final NumericType element = numberElement(call.getArguments().get(0));
        final Expression value = call.getArguments().get(1);
        if (element == null || !(value instanceof LiteralExpression)
                || ((LiteralExpression) value).getType() != LiteralType.STRING) {
            return null;
        }
        return "ARRAY_APPEND(" + printer.printed(call.getArguments().get(0)) + ", TO_NUMBER("
            + printer.printed(value) + ", " + element.getPrecision() + ", " + element.getScale() + "))";
    }

    /**
     * The FLOAT a number literal converted to FLOAT folds to, as the plan prints it — its shortest digits, never by
     * its exponent: 0.1, 1e3 as 1000 — or null for any other expression.
     */
    private static String floatConstant(final Expression value) {
        if (!(value instanceof CastExpression) || ((CastExpression) value).isTryMode()
                || ((CastExpression) value).isTypedLiteral()
                || !NumericType.isApproximate(((CastExpression) value).getTargetDataType())) {
            return null;
        }
        final Expression operand = ((CastExpression) value).getExpression();
        if (!(operand instanceof LiteralExpression) || !(((LiteralExpression) operand).getValue() instanceof Number)
                || ((LiteralExpression) operand).getType() != LiteralType.INTEGER
                && ((LiteralExpression) operand).getType() != LiteralType.DECIMAL) {
            return null;
        }
        final double number = ((Number) ((LiteralExpression) operand).getValue()).doubleValue();
        return Double.isInfinite(number) ? null : BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    /** The NUMBER element type of a cast to a structured ARRAY, or null for any other array. */
    private static NumericType numberElement(final Expression array) {
        if (!(array instanceof CastExpression)) {
            return null;
        }
        final DataType target = ((CastExpression) array).getDeclaredTarget();
        return target instanceof ArrayType && ((ArrayType) target).getElementType() instanceof NumericType
            ? (NumericType) ((ArrayType) target).getElementType() : null;
    }
}
