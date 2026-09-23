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

package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A value operator written after a quantified comparison. Live refuses it while the statement compiles — before
 * a table or a column of the statement is resolved — naming the operator as it was written (all live-verified):
 *
 * <pre>
 *   SELECT 1 = ANY (SELECT 1) || ''            Invalid query block: ||.
 *   SELECT 1 = ANY (SELECT 1)::VARCHAR         Invalid query block: ::.
 *   SELECT 1 &lt; ALL (SELECT 2) + 1              Invalid query block: +.
 *   SELECT 1 = ANY (SELECT 1) IS NULL          Invalid query block: IS.
 *   SELECT 1 = ANY (SELECT 1) COLLATE 'de'     Invalid query block: COLLATE.
 *   SELECT 1 = ANY (SELECT 1) :x               Invalid query block: :.
 *   SELECT 1 = ANY (SELECT 1) [0]              Invalid query block: [.
 *   SELECT 1 = ANY (SELECT 1) (+)              Invalid query block: +.
 * </pre>
 *
 * <p>The same holds in a WHERE, an ORDER BY, a CASE and a call's argument. A comparison-level operator after
 * it reads as written — {@code = TRUE}, {@code IN (TRUE)} and {@code BETWEEN FALSE AND TRUE} answer — and a
 * parenthesized quantified comparison is an ordinary BOOLEAN operand. Only the first such operator, in the
 * order written, is reported.
 */
public final class QuantifiedComparisonOperand {

    private QuantifiedComparisonOperand() {
    }

    /**
     * Refuse the first value operator, in the order written, whose operand is a quantified comparison.
     *
     * @param script the parsed script, or one statement of it
     */
    public static void requireNoValueOperator(final ParseTree script) {
        if (script == null) {
            return;
        }
        Token first = null;
        String spelled = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            final Token refused = refusedOperator(node);
            if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                first = refused;
                spelled = spelling(node, refused);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (first != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid query block: " + spelled + "."));
        }
    }

    /** The token of the operator {@code node} applies to a quantified comparison, or null. */
    private static Token refusedOperator(final ParseTree node) {
        if (node instanceof FrostlakeParser.ConcatExprContext) {
            final FrostlakeParser.ConcatExprContext concat = (FrostlakeParser.ConcatExprContext) node;
            return quantified(concat.expression(0)) ? concat.PIPE_PIPE().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext) {
            final FrostlakeParser.AdditiveExprContext sum = (FrostlakeParser.AdditiveExprContext) node;
            return quantified(sum.expression(0)) ? sum.op : null;
        }
        if (node instanceof FrostlakeParser.MultiplicativeExprContext) {
            final FrostlakeParser.MultiplicativeExprContext product = (FrostlakeParser.MultiplicativeExprContext) node;
            return quantified(product.expression(0)) ? product.op : null;
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) node;
            return quantified(cast.expression()) ? cast.DOUBLE_COLON().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.CollateExprContext) {
            final FrostlakeParser.CollateExprContext collate = (FrostlakeParser.CollateExprContext) node;
            return quantified(collate.expression()) ? collate.COLLATE().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            final FrostlakeParser.IsNullExprContext test = (FrostlakeParser.IsNullExprContext) node;
            return quantified(test.expression()) ? test.IS().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.IsDistinctExprContext) {
            final FrostlakeParser.IsDistinctExprContext test = (FrostlakeParser.IsDistinctExprContext) node;
            return quantified(test.expression(0)) ? test.IS().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.ObjectAccessExprContext) {
            final FrostlakeParser.ObjectAccessExprContext path = (FrostlakeParser.ObjectAccessExprContext) node;
            return quantified(path.expression()) ? path.COLON(0).getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.ArrayAccessExprContext) {
            final FrostlakeParser.ArrayAccessExprContext subscript = (FrostlakeParser.ArrayAccessExprContext) node;
            return quantified(subscript.expression(0)) ? subscript.LBRACKET().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            final FrostlakeParser.OuterJoinOperandExprContext marker = (FrostlakeParser.OuterJoinOperandExprContext) node;
            return quantified(marker.expression()) ? marker.PLUS().getSymbol() : null;
        }
        return null;
    }

    /** How live names the refused operator: as written, COLLATE and IS as their keywords. */
    private static String spelling(final ParseTree node, final Token operator) {
        if (node instanceof FrostlakeParser.CollateExprContext) {
            return "COLLATE";
        }
        if (node instanceof FrostlakeParser.IsNullExprContext || node instanceof FrostlakeParser.IsDistinctExprContext) {
            return "IS";
        }
        return operator.getText();
    }

    /** Whether an operand is a quantified comparison written without parentheses. */
    private static boolean quantified(final ParseTree operand) {
        return operand instanceof FrostlakeParser.QuantifiedComparisonExprContext;
    }
}
