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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The operators a finished predicate cannot take as their left operand — live's syntax errors, judged once
 * the statement has parsed. A predicate is finished when it has read everything it can: {@code IS [NOT]
 * NULL}, a LIKE or ILIKE whose ESCAPE operand is written, a {@code LIKE ANY} or {@code ILIKE ANY} list with
 * its ESCAPE, an IN list, an IN subquery, the tuple forms of IN, and EXISTS. The grammar lets any operator
 * follow such a predicate, so the refusal is made here, at the operator:
 *
 * <pre>
 *   SELECT 'a' IS NULL || ''                     unexpected '||'
 *   SELECT 1 IN (1, 2)::INT                      unexpected '::'
 *   SELECT 'a' LIKE 'a' ESCAPE '!' + 1           unexpected '+'
 *   SELECT 1 IN (1, 2) IS DISTINCT FROM TRUE     unexpected 'IS'
 *   SELECT 'a' IS NULL COLLATE 'de'              unexpected ''de''     COLLATE reads as an alias
 *   SELECT EXISTS (SELECT 1) = TRUE              unexpected '='
 * </pre>
 *
 * <p>The operators refused are the value operators: concatenation, the arithmetic ones, a cast, a path, a
 * subscript, the outer-join marker, IS, and a COLLATE, which live reads as an alias and so refuses at its
 * specification. A comparison, LIKE, RLIKE, BETWEEN or IN after the predicate is taken as written, except after
 * EXISTS, which takes no operator at all. An IS DISTINCT FROM is finished for an IS alone. A parenthesized predicate is an ordinary operand, and a predicate
 * standing on the right of an operator is never the one refused: {@code TRUE = 'a' IS NULL} answers.
 *
 * <p>Only the first refusal is reported, in the order written, with the line live's recovery stacks after it. Inside
 * parentheses the recovery closes them before the operator and reads on: {@code (1 IN (1, 2)::INT + 1)} is '::' and
 * then the ')' left over. In a select item, a CASE's WHEN or a call's argument it drops the operator and every token up
 * to the next name, AS or clause word, reads that name as the item's alias and refuses the first token that cannot
 * follow it: {@code 1 IN (1, 2)::INT + 1} is '::' and then '+', {@code 1 IS DISTINCT FROM 2 IS DISTINCT FROM 3} is 'IS'
 * and then the '3' of a FROM clause — except in a call, where a name right before the call's ')' is dropped as well,
 * and after EXISTS, which stacks nothing. In a WHERE, HAVING or ORDER BY nothing is stacked (all live-verified).
 */
public final class FinishedPredicateSyntax {

    private FinishedPredicateSyntax() {
    }

    /**
     * Refuse the first operator, in the order written, whose left operand is a finished predicate.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     */
    public static void requireOpenOperands(final FrostlakeParser.SqlScriptContext script,
                                           final TokenStream tokens, final String sql) {
        if (script == null || tokens == null || !mayHoldFinishedPredicate(tokens)) {
            return;
        }
        Token first = null;
        ParseTree firstNode = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            final Token refused = refusedOperator(node);
            if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                first = refused;
                firstNode = node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (first != null) {
            refuse(first, FinishedPredicateRecovery.stackedLines(firstNode, first, tokens, sql), sql);
        }
    }

    /** Whether the statement has a word that can finish a predicate: IS, IN, EXISTS or ESCAPE. */
    private static boolean mayHoldFinishedPredicate(final TokenStream tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (type == FrostlakeLexer.IS || type == FrostlakeLexer.IN || type == FrostlakeLexer.EXISTS
                    || type == FrostlakeLexer.ESCAPE) {
                return true;
            }
        }
        return false;
    }

    /** The operator {@code node} applies to a finished predicate on its left, or null. */
    private static Token refusedOperator(final ParseTree node) {
        if (node instanceof FrostlakeParser.ConcatExprContext) {
            final FrostlakeParser.ConcatExprContext concat = (FrostlakeParser.ConcatExprContext) node;
            return refusedAfter(concat.expression(0), concat.PIPE_PIPE(), false);
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) node;
            return refusedAfter(cast.expression(), cast.DOUBLE_COLON(), false);
        }
        if (node instanceof FrostlakeParser.MultiplicativeExprContext) {
            final FrostlakeParser.MultiplicativeExprContext product = (FrostlakeParser.MultiplicativeExprContext) node;
            return finished(product.expression(0), false) ? product.op : null;
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext) {
            final FrostlakeParser.AdditiveExprContext sum = (FrostlakeParser.AdditiveExprContext) node;
            return finished(sum.expression(0), false) ? sum.op : null;
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            final FrostlakeParser.IsNullExprContext test = (FrostlakeParser.IsNullExprContext) node;
            return test.expression() instanceof FrostlakeParser.IsDistinctExprContext
                ? test.IS().getSymbol() : refusedAfter(test.expression(), test.IS(), false);
        }
        if (node instanceof FrostlakeParser.IsDistinctExprContext) {
            return refusedDistinct((FrostlakeParser.IsDistinctExprContext) node);
        }
        if (node instanceof FrostlakeParser.ObjectAccessExprContext) {
            final FrostlakeParser.ObjectAccessExprContext path = (FrostlakeParser.ObjectAccessExprContext) node;
            return refusedAfter(path.expression(), path.COLON(0), false);
        }
        if (node instanceof FrostlakeParser.ArrayAccessExprContext) {
            final FrostlakeParser.ArrayAccessExprContext subscript = (FrostlakeParser.ArrayAccessExprContext) node;
            return refusedAfter(subscript.expression(0), subscript.LBRACKET(), false);
        }
        if (node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            final FrostlakeParser.OuterJoinOperandExprContext marker = (FrostlakeParser.OuterJoinOperandExprContext) node;
            return refusedAfter(marker.expression(), marker.LPAREN(), false);
        }
        if (node instanceof FrostlakeParser.CollateExprContext) {
            final FrostlakeParser.CollateExprContext collate = (FrostlakeParser.CollateExprContext) node;
            final TerminalNode spec = collate.STRING_LITERAL() != null
                ? collate.STRING_LITERAL() : collate.DOLLAR_QUOTED_STRING();
            return refusedAfter(collate.expression(), spec, false);
        }
        return refusedComparison(node);
    }

    /**
     * The IS an IS DISTINCT FROM meets. Its right operand takes the value operators after it, so {@code 1 IS
     * DISTINCT FROM 2 || ''} answers, but not an IS: {@code 1 IS DISTINCT FROM 2 IS NULL} is refused at that
     * IS, which the grammar reads inside the right operand, and so is a second IS DISTINCT FROM.
     */
    private static Token refusedDistinct(final FrostlakeParser.IsDistinctExprContext test) {
        if (test.expression(0) instanceof FrostlakeParser.IsDistinctExprContext) {
            return test.IS().getSymbol();
        }
        if (test.expression(1) instanceof FrostlakeParser.IsNullExprContext) {
            return ((FrostlakeParser.IsNullExprContext) test.expression(1)).IS().getSymbol();
        }
        return refusedAfter(test.expression(0), test.IS(), false);
    }

    /** The comparison-level operator {@code node} applies to EXISTS on its left, or null. */
    private static Token refusedComparison(final ParseTree node) {
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) node;
            return finished(comparison.expression(0), true) ? comparison.op : null;
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            final FrostlakeParser.QuantifiedComparisonExprContext comparison =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            return finished(comparison.expression(), true) ? comparison.op : null;
        }
        if (node instanceof FrostlakeParser.LikeExprContext) {
            final FrostlakeParser.LikeExprContext like = (FrostlakeParser.LikeExprContext) node;
            return refusedAfter(like.expression(0), firstOf(like.NOT(), like.LIKE(), like.ILIKE()), true);
        }
        if (node instanceof FrostlakeParser.LikeAnyAllExprContext) {
            final FrostlakeParser.LikeAnyAllExprContext like = (FrostlakeParser.LikeAnyAllExprContext) node;
            return refusedAfter(like.expression(0), firstOf(null, like.LIKE(), like.ILIKE()), true);
        }
        if (node instanceof FrostlakeParser.RlikeExprContext) {
            final FrostlakeParser.RlikeExprContext rlike = (FrostlakeParser.RlikeExprContext) node;
            return refusedAfter(rlike.expression(0), firstOf(rlike.NOT(), rlike.RLIKE(), rlike.REGEXP()), true);
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            final FrostlakeParser.BetweenExprContext between = (FrostlakeParser.BetweenExprContext) node;
            return refusedAfter(between.expression(0), firstOf(between.NOT(), between.BETWEEN(), null), true);
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            final FrostlakeParser.InSubqueryExprContext in = (FrostlakeParser.InSubqueryExprContext) node;
            return refusedAfter(in.expression(), firstOf(in.NOT(), in.IN(), null), true);
        }
        if (node instanceof FrostlakeParser.InListExprContext) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) node;
            return refusedAfter(in.expression(), firstOf(in.NOT(), in.IN(), null), true);
        }
        return null;
    }

    /** {@code operator}'s token when {@code left} is a predicate it may not follow, or null. */
    private static Token refusedAfter(final ParseTree left, final TerminalNode operator, final boolean existsOnly) {
        return operator != null && finished(left, existsOnly) ? operator.getSymbol() : null;
    }

    /** The first of the terminals present, in the order given. */
    private static TerminalNode firstOf(final TerminalNode first, final TerminalNode second, final TerminalNode third) {
        if (first != null) {
            return first;
        }
        return second != null ? second : third;
    }

    /**
     * Whether {@code operand} is a finished predicate, written without parentheses.
     *
     * @param operand    the left operand
     * @param existsOnly whether only EXISTS counts, as it does before a comparison-level operator
     */
    private static boolean finished(final ParseTree operand, final boolean existsOnly) {
        if (operand instanceof FrostlakeParser.ExistsExprContext) {
            return true;
        }
        if (existsOnly) {
            return false;
        }
        if (operand instanceof FrostlakeParser.LikeExprContext) {
            return ((FrostlakeParser.LikeExprContext) operand).escapeOperand() != null;
        }
        if (operand instanceof FrostlakeParser.LikeAnyAllExprContext) {
            return ((FrostlakeParser.LikeAnyAllExprContext) operand).esc != null;
        }
        return operand instanceof FrostlakeParser.IsNullExprContext
            || operand instanceof FrostlakeParser.InListExprContext
            || operand instanceof FrostlakeParser.InSubqueryExprContext
            || operand instanceof FrostlakeParser.TupleInSubqueryExprContext
            || operand instanceof FrostlakeParser.TupleInListExprContext
            || operand instanceof FrostlakeParser.TupleInFlatListExprContext;
    }

    private static void refuse(final Token token, final List<String> stacked, final String sql) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        final String line = "syntax error line " + shown[0] + " at position " + shown[1]
            + " unexpected '" + token.getText() + "'.";
        final List<String> lines = new ArrayList<>();
        lines.add(line);
        lines.addAll(stacked);
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }
}
