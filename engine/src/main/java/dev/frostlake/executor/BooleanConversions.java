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

import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BooleanType;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The conversions of a value to a BOOLEAN the plan settles by the value's statistics: {@code x::BOOLEAN},
 * {@code CAST(x AS BOOLEAN)} and {@code TO_BOOLEAN(x)}, each read alike (live-verified).
 */
final class BooleanConversions {

    private BooleanConversions() {
    }

    /**
     * Whether a value stands, through parentheses alone, as what a conversion to BOOLEAN converts.
     *
     * @param node the value
     * @return true for the operand of such a conversion
     */
    static boolean convertsToBoolean(final ParseTree node) {
        ParseTree operand = node;
        ParseTree parent = node.getParent();
        while (parent instanceof FrostlakeParser.ValueExprContext
                || parent instanceof FrostlakeParser.ParenExprContext) {
            operand = parent;
            parent = parent.getParent();
        }
        if (parent instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) parent;
            return cast.expression() == operand && DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(),
                DataTypeParser.CAST_STRING_DEFAULT) instanceof BooleanType;
        }
        if (parent instanceof FrostlakeParser.CastExprContext) {
            final FrostlakeParser.CastExprContext cast = (FrostlakeParser.CastExprContext) parent;
            return cast.FIELDS() == null && cast.expression() == operand && DataTypeParser.parse(cast.dataTypeName(),
                cast.typeParameters(), DataTypeParser.CAST_STRING_DEFAULT) instanceof BooleanType;
        }
        if (parent instanceof FrostlakeParser.FunctionArgContext
                && parent.getParent() instanceof FrostlakeParser.FunctionArgListContext
                && parent.getParent().getParent() instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) parent.getParent().getParent();
            return "TO_BOOLEAN".equalsIgnoreCase(call.functionName().getText()) && call.overClause() == null
                && call.functionArgList().functionArg().size() == 1;
        }
        return false;
    }
}
