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

import java.util.Locale;

/**
 * How an echo of the expression a statement compiled spells a call that appends an element to a structured ARRAY:
 * the element converted to the array's element type as the plan converts it, a text into a NUMBER(p,s) element as
 * {@code TO_NUMBER(text, p, s)} and a number as written (live-verified, for ARRAY_APPEND in a VALUES clause:
 * {@code ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS ARRAY(NUMBER(10,2))), TO_NUMBER('zz', 10, 2))}).
 */
public final class StructuredElementEcho implements CallEcho {

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        if (!"ARRAY_APPEND".equals(call.getFunctionName().toUpperCase(Locale.ROOT))
                || call.getArguments().size() != 2) {
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
