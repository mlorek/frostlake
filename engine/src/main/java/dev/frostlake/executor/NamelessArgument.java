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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Whether an argument of a call in a block's own expression reads no name: literals, and the operators, conversions,
 * conditionals and calls over them. Such an argument means the same to SQL as it means to the block, so where its
 * value alone does not say what was written — it folds to NULL — the call can be judged over the argument as
 * written: {@code RETURN RANDOM(NULL + 1)} is refused for an argument that is no constant, where {@code
 * RANDOM(NULL)} is refused for its seed (live-verified).
 *
 * <p>A name — a variable, a bind, a column of a subquery — is the block's to resolve, and anything this class does
 * not know to be free of one counts as reading one.
 */
final class NamelessArgument {

    private NamelessArgument() {
    }

    /**
     * Whether {@code node} reads no name.
     *
     * @param node an argument of a call, or a part of one
     * @return true when every expression under it is a literal or an operator, a conversion, a conditional or a call
     *         over nameless expressions
     */
    static boolean readsNoName(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionNameContext || node instanceof FrostlakeParser.DataTypeNameContext
                || node instanceof FrostlakeParser.TypeParametersContext) {
            // The call's own name and a conversion's target are no names the block resolves.
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameContext || node instanceof FrostlakeParser.IdentifierContext
                || node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.ExpressionContext && !isNamelessKind((FrostlakeParser.ExpressionContext) node)) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsNoName(node.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether an expression is of a kind that reads a name only through its operands. */
    private static boolean isNamelessKind(final FrostlakeParser.ExpressionContext expression) {
        if (expression instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) expression).overClause() == null;
        }
        return expression instanceof FrostlakeParser.LiteralExprContext
            || expression instanceof FrostlakeParser.ParenExprContext
            || expression instanceof FrostlakeParser.UnaryExprContext
            || expression instanceof FrostlakeParser.AdditiveExprContext
            || expression instanceof FrostlakeParser.MultiplicativeExprContext
            || expression instanceof FrostlakeParser.ConcatExprContext
            || expression instanceof FrostlakeParser.CastExprContext
            || expression instanceof FrostlakeParser.CastExpr2Context
            || expression instanceof FrostlakeParser.TryCastExprContext
            || expression instanceof FrostlakeParser.CaseExprContext
            || expression instanceof FrostlakeParser.ComparisonExprContext
            || expression instanceof FrostlakeParser.IsNullExprContext;
    }
}
