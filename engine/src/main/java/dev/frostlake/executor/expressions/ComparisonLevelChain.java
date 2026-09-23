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

package dev.frostlake.executor.expressions;

import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;

/**
 * The comparison-level operators of an expression, read back in the order they were written.
 *
 * <p>Live reads every comparison-level operator at ONE precedence, from the left: the six comparisons, [NOT] LIKE
 * and ILIKE, RLIKE and REGEXP, [NOT] IN, [NOT] BETWEEN, LIKE ANY, LIKE ALL, ILIKE ANY and the quantified
 * comparisons. IS [NOT] NULL, IS [NOT] DISTINCT FROM and the value operators bind tighter. So
 *
 * <pre>
 *   'a' LIKE 'a' = TRUE        is ('a' LIKE 'a') = TRUE          TRUE
 *   TRUE = 'a' LIKE 'a'        is (TRUE = 'a') LIKE 'a'          refused at the LIKE, (BOOLEAN, VARCHAR(1))
 *   'x' = 'y' IN (FALSE)       is ('x' = 'y') IN (FALSE)         TRUE
 *   FALSE = 1 BETWEEN 0 AND 2  is (FALSE = 1) BETWEEN 0 AND 2    TRUE
 *   'a' LIKE 'a' IS NULL       is 'a' LIKE ('a' IS NULL)         refused at the LIKE, (VARCHAR(1), BOOLEAN)
 * </pre>
 *
 * <p>The grammar ranks some of these above others, and a LIKE's pattern reads a whole expression, so such a chain
 * parses nested to the right. {@link #inWrittenOrder} takes that tree apart into its operands and operators, and the
 * AST builder folds them from the left.
 *
 * <p>A parenthesized operand is never taken apart, and neither is a LIKE pattern followed by ESCAPE, or BETWEEN's
 * lower bound. One parenthesized value before IN, or on each side of a comparison, is that value and no row:
 * {@code (1 = 1) IN (TRUE)} is TRUE and {@code (n) IN (SELECT …)} is an ordinary IN.
 *
 * <p>A LIKE ANY, LIKE ALL or ILIKE ANY without ESCAPE takes a VALUE as its pattern list, so the value operators
 * written after the list apply to it: {@code 'a' LIKE ANY ('a') || ''} matches 'a' || ''. The grammar applies them
 * to the whole predicate instead; the outermost of those operators stands for the step here, and the builder reads
 * the list back into its place.
 */
public final class ComparisonLevelChain {

    private ComparisonLevelChain() {
    }

    /**
     * Whether {@code node} is a value operator written after a LIKE ANY's pattern list, which applies to that list
     * rather than to the predicate the grammar gave it (see {@link #valueOperandBase}). A reader that builds the
     * operator over the whole predicate reads it unlike the AST builder.
     *
     * @param node the parsed expression
     * @return true for a value operator over a LIKE ANY, LIKE ALL or ILIKE ANY pattern list
     */
    public static boolean appliesToPatternList(final FrostlakeParser.ExpressionContext node) {
        return valueOperandBase(node) != null;
    }

    /**
     * The chain rooted at {@code top} in the order written: an operand, then each operator followed by the operand
     * it takes on its right, when it takes one.
     *
     * @param top a node {@link #isStep} accepts
     * @return the operands and the operators, alternating from an operand
     */
    static List<FrostlakeParser.ExpressionContext> inWrittenOrder(final FrostlakeParser.ExpressionContext top) {
        final List<FrostlakeParser.ExpressionContext> out = new ArrayList<>();
        flatten(top, out);
        return out;
    }

    /**
     * Whether {@code node} is a comparison-level operator the builder folds as part of a chain.
     *
     * @param node the parsed expression
     * @return true for a comparison-level step
     */
    public static boolean isStep(final FrostlakeParser.ExpressionContext node) {
        return leftOperand(node) != null;
    }

    /**
     * Whether a step takes the operand written after it — a comparison, a LIKE, an RLIKE and a BETWEEN do; an IN,
     * a quantified comparison and a LIKE ANY end at their own closing parenthesis.
     *
     * @param step a step
     * @return true when the next item of the chain is this step's right operand
     */
    static boolean takesRightOperand(final FrostlakeParser.ExpressionContext step) {
        return rightOperand(step) != null;
    }

    /**
     * The LIKE ANY, LIKE ALL or ILIKE ANY whose pattern list the value operators ending at {@code node} apply to:
     * the operand reached by following their left operands down from {@code node}, when it is such a predicate
     * without ESCAPE and written without parentheses. Null for anything else.
     *
     * @param node the parsed expression
     * @return the predicate whose list is the operand of those operators, or null
     */
    static FrostlakeParser.LikeAnyAllExprContext valueOperandBase(final FrostlakeParser.ExpressionContext node) {
        FrostlakeParser.ExpressionContext spine = valueOperand(node);
        if (spine == null) {
            return null;
        }
        while (valueOperand(spine) != null) {
            spine = valueOperand(spine);
        }
        if (spine instanceof FrostlakeParser.LikeAnyAllExprContext
                && ((FrostlakeParser.LikeAnyAllExprContext) spine).esc == null) {
            return (FrostlakeParser.LikeAnyAllExprContext) spine;
        }
        return null;
    }

    /**
     * The operand a value operator applies to — the left one of a binary operator — or null when {@code node} is
     * no value operator.
     *
     * @param node the parsed expression
     * @return the operand, or null
     */
    static FrostlakeParser.ExpressionContext valueOperand(final FrostlakeParser.ExpressionContext node) {
        if (node instanceof FrostlakeParser.ConcatExprContext) {
            return ((FrostlakeParser.ConcatExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext) {
            return ((FrostlakeParser.AdditiveExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.MultiplicativeExprContext) {
            return ((FrostlakeParser.MultiplicativeExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            return ((FrostlakeParser.CastExpr2Context) node).expression();
        }
        if (node instanceof FrostlakeParser.CollateExprContext) {
            return ((FrostlakeParser.CollateExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.ObjectAccessExprContext) {
            return ((FrostlakeParser.ObjectAccessExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.ArrayAccessExprContext) {
            return ((FrostlakeParser.ArrayAccessExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.FieldAccessExprContext) {
            return ((FrostlakeParser.FieldAccessExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            return ((FrostlakeParser.IsNullExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.IsDistinctExprContext) {
            return ((FrostlakeParser.IsDistinctExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            return ((FrostlakeParser.OuterJoinOperandExprContext) node).expression();
        }
        return null;
    }

    /**
     * The one value a parenthesized list of one holds: before IN, the tuple spellings of IN read it as a scalar.
     *
     * @param list the list
     * @return its value, or null when it holds more than one
     */
    static FrostlakeParser.ExpressionContext soleValue(final FrostlakeParser.ExpressionListContext list) {
        return list != null && list.expression().size() == 1 ? list.expression(0) : null;
    }

    private static void flatten(final FrostlakeParser.ExpressionContext node,
                                final List<FrostlakeParser.ExpressionContext> out) {
        final FrostlakeParser.ExpressionContext left = leftOperand(node);
        if (left == null) {
            out.add(node);
            return;
        }
        if (parenthesizedLeft(node) != null) {
            out.add(left);
        } else {
            flatten(left, out);
        }
        out.add(node);
        final FrostlakeParser.ExpressionContext right = rightOperand(node);
        if (right == null) {
            return;
        }
        if (rightStaysWhole(node)) {
            out.add(right);
        } else {
            flatten(right, out);
        }
    }

    /** The operand a step takes on its left, or null when {@code node} is no step. */
    private static FrostlakeParser.ExpressionContext leftOperand(final FrostlakeParser.ExpressionContext node) {
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.LikeExprContext) {
            return ((FrostlakeParser.LikeExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.RlikeExprContext) {
            return ((FrostlakeParser.RlikeExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            return ((FrostlakeParser.BetweenExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.InListExprContext) {
            return ((FrostlakeParser.InListExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.LikeAnyAllExprContext) {
            return ((FrostlakeParser.LikeAnyAllExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.ScalarRowComparisonExprContext) {
            return ((FrostlakeParser.ScalarRowComparisonExprContext) node).expression(0);
        }
        final FrostlakeParser.LikeAnyAllExprContext listBase = valueOperandBase(node);
        return listBase != null ? listBase.expression(0) : parenthesizedLeft(node);
    }

    /**
     * The value a step's parenthesized list of one holds on its left: one value before IN, in any of the tuple
     * spellings, or on each side of a parenthesized comparison. Null for every other node.
     */
    private static FrostlakeParser.ExpressionContext parenthesizedLeft(final FrostlakeParser.ExpressionContext node) {
        if (node instanceof FrostlakeParser.TupleInFlatListExprContext) {
            return soleValue(((FrostlakeParser.TupleInFlatListExprContext) node).expressionList(0));
        }
        if (node instanceof FrostlakeParser.TupleInListExprContext) {
            return soleValue(((FrostlakeParser.TupleInListExprContext) node).expressionList());
        }
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return soleValue(((FrostlakeParser.TupleInSubqueryExprContext) node).expressionList());
        }
        if (node instanceof FrostlakeParser.RowComparisonExprContext) {
            final FrostlakeParser.RowComparisonExprContext row = (FrostlakeParser.RowComparisonExprContext) node;
            return soleValue(row.right) != null ? soleValue(row.left) : null;
        }
        return null;
    }

    /** The operand a step takes on its right, or null when it takes none. */
    private static FrostlakeParser.ExpressionContext rightOperand(final FrostlakeParser.ExpressionContext node) {
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) node).expression(1);
        }
        if (node instanceof FrostlakeParser.LikeExprContext) {
            return ((FrostlakeParser.LikeExprContext) node).expression(1);
        }
        if (node instanceof FrostlakeParser.RlikeExprContext) {
            return ((FrostlakeParser.RlikeExprContext) node).expression(1);
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            return ((FrostlakeParser.BetweenExprContext) node).expression(2);
        }
        if (node instanceof FrostlakeParser.RowComparisonExprContext && parenthesizedLeft(node) != null) {
            return soleValue(((FrostlakeParser.RowComparisonExprContext) node).right);
        }
        return null;
    }

    /**
     * Whether a step's right operand is taken whole: a parenthesized one, and a LIKE pattern an ESCAPE follows —
     * the pattern the grammar read up to the ESCAPE stays that LIKE's own.
     */
    private static boolean rightStaysWhole(final FrostlakeParser.ExpressionContext node) {
        if (node instanceof FrostlakeParser.RowComparisonExprContext) {
            return true;
        }
        return node instanceof FrostlakeParser.LikeExprContext
            && ((FrostlakeParser.LikeExprContext) node).escapeOperand() != null;
    }
}
