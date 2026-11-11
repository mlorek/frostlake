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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Where a block's RETURN or IF condition places a name it cannot resolve - a bare word where a date or time unit is
 * read, naming none (see UnknownUnitWord): at the expression's root, counted in the expression's own text, which is
 * the condition's without IF's brackets (all live-verified):
 *
 * <pre>
 *   RETURN LAST_DAY(d, nosuch)                    0     the call
 *   RETURN (LAST_DAY(d, nosuch))                  1     the call inside the brackets
 *   RETURN LAST_DAY(d, nosuch) IS NULL            40    the IS
 *   RETURN 1 + 2 + LAST_DAY(d, nosuch) IS NULL    48    the IS
 *   RETURN LAST_DAY(d, nosuch) = CURRENT_DATE()   40    the =
 *   RETURN LAST_DAY(d, nosuch)::VARCHAR           39    the ::
 *   RETURN 'abc' || LAST_DAY(d, nosuch)           6     the ||
 *   RETURN NOT (LAST_DAY(d, nosuch) IS NULL)      0     the NOT
 *   IF (DATEADD(nosuch, 1, d) IS NULL) THEN       43    the IS
 * </pre>
 *
 * The root is the operator a first operand stands before, a bracketed expression's own root, or else the first token.
 */
final class ExpressionRootPlace {

    private ExpressionRootPlace() {
    }

    /**
     * @param expression the RETURN's value or the IF's condition
     * @return the place, as {line, position} in the expression's own text
     */
    static int[] of(final ParserRuleContext expression) {
        final Token start = expression.getStart();
        final Token root = rootOf(expression);
        if (start == null || root == null || root.getLine() < start.getLine()) {
            return new int[] {1, 0};
        }
        return root.getLine() == start.getLine()
            ? new int[] {1, root.getCharPositionInLine() - start.getCharPositionInLine()}
            : new int[] {root.getLine() - start.getLine() + 1, root.getCharPositionInLine()};
    }

    private static Token rootOf(final ParseTree node) {
        ParseTree at = node;
        while (at instanceof ParserRuleContext) {
            final ParserRuleContext rule = (ParserRuleContext) at;
            if (rule.getChildCount() == 1) {
                at = rule.getChild(0);
                continue;
            }
            if (rule instanceof FrostlakeParser.ParenExprContext) {
                at = ((FrostlakeParser.ParenExprContext) rule).booleanExpr();
                continue;
            }
            return isOperand(rule.getChildCount() > 0 ? rule.getChild(0) : null) ? operatorOf(rule)
                : rule.getStart();
        }
        return at instanceof TerminalNode ? ((TerminalNode) at).getSymbol() : null;
    }

    /** The operator a first operand stands before: a word or a symbol, or an operator rule's first token. */
    private static Token operatorOf(final ParserRuleContext rule) {
        for (int i = 1; i < rule.getChildCount(); i++) {
            final ParseTree child = rule.getChild(i);
            if (child instanceof TerminalNode) {
                return ((TerminalNode) child).getSymbol();
            }
            if (!isOperand(child)) {
                return ((ParserRuleContext) child).getStart();
            }
        }
        return rule.getStart();
    }

    private static boolean isOperand(final ParseTree child) {
        return child instanceof FrostlakeParser.ExpressionContext
            || child instanceof FrostlakeParser.BooleanExprContext;
    }
}
