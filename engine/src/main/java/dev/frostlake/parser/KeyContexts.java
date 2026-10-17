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

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys of an ORDER BY, a GROUP BY, a window's PARTITION BY and a MATCH_RECOGNIZE's PARTITION BY as the parse tree
 * holds them. A key is read as a select item is, as a condition: a value — the condition's {@code ValueExpr} — or one
 * such as {@code NOT b}, {@code a AND b}, {@code !EXISTS (…)} (live-verified). A key that is a value is answered as
 * its expression, so a reader of values reads it as it reads any other expression.
 */
public final class KeyContexts {

    /** Static helpers only — never instantiated. */
    private KeyContexts() {
    }

    /**
     * An ORDER BY item's key.
     *
     * @param item the item
     * @return its expression, or the condition it is
     */
    public static ParserRuleContext of(final FrostlakeParser.OrderItemContext item) {
        return key(item.booleanExpr());
    }

    /**
     * A plain GROUP BY element's key.
     *
     * @param element the element
     * @return its expression, the condition it is, or null for a ROLLUP, a CUBE or GROUPING SETS
     */
    public static ParserRuleContext of(final FrostlakeParser.GroupByElementContext element) {
        return element.booleanExpr() == null ? null : key(element.booleanExpr());
    }

    /**
     * A key as written.
     *
     * @param written the key's condition, as the grammar reads it
     * @return the expression it is when it is a value, else the condition
     */
    public static ParserRuleContext key(final FrostlakeParser.BooleanExprContext written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }

    /**
     * Each of a list of keys as written, in order.
     *
     * @param written the keys' conditions, as the grammar reads them
     * @return each key
     */
    public static List<ParserRuleContext> keys(final List<FrostlakeParser.BooleanExprContext> written) {
        final List<ParserRuleContext> keys = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext one : written) {
            keys.add(key(one));
        }
        return keys;
    }

    /**
     * The members of a ROLLUP's or a CUBE's list, in order.
     *
     * @param list the list
     * @return each member's key
     */
    public static List<ParserRuleContext> members(final FrostlakeParser.GroupByColumnListContext list) {
        return keys(list.booleanExpr());
    }

    /**
     * The members of one grouping set, in order: none for the empty set.
     *
     * @param set the set
     * @return each member's key
     */
    public static List<ParserRuleContext> members(final FrostlakeParser.GroupingSetContext set) {
        return keys(set.booleanExpr());
    }

    /**
     * Every key a GROUP BY clause names, in the order written: a plain element's, then each member of a ROLLUP, a
     * CUBE and every grouping set.
     *
     * @param groupBy the clause
     * @return the keys; none for GROUP BY ALL
     */
    public static List<ParserRuleContext> groupKeys(final FrostlakeParser.GroupByClauseContext groupBy) {
        final List<ParserRuleContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            final ParserRuleContext key = of(element);
            if (key != null) {
                keys.add(key);
            }
            if (element.groupByColumnList() != null) {
                keys.addAll(members(element.groupByColumnList()));
            }
            if (element.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                    keys.addAll(members(set));
                }
            }
        }
        return keys;
    }

    /**
     * A key for a reader that takes an expression: a value is its own, and a condition is presented as the
     * parenthesized expression it equals — {@code NOT b} as {@code (NOT b)}, which reads, evaluates and prints as the
     * condition does — standing over the condition's own node, so nothing is parsed again.
     *
     * @param key a key, as {@link #of} answers it
     * @return the expression, or null for none
     */
    public static FrostlakeParser.ExpressionContext asExpression(final ParserRuleContext key) {
        if (key == null || key instanceof FrostlakeParser.ExpressionContext) {
            return (FrostlakeParser.ExpressionContext) key;
        }
        final FrostlakeParser.ParenExprContext parenthesized = new FrostlakeParser.ParenExprContext(
            new FrostlakeParser.ExpressionContext(key.getParent() instanceof ParserRuleContext
                ? (ParserRuleContext) key.getParent() : null, key.invokingState));
        parenthesized.addAnyChild(key);
        parenthesized.start = key.getStart();
        parenthesized.stop = key.getStop();
        return parenthesized;
    }

    /**
     * A window's PARTITION BY keys, in order.
     *
     * @param clause the clause
     * @return each key
     */
    public static List<ParserRuleContext> partitionKeys(final FrostlakeParser.PartitionByClauseContext clause) {
        final List<ParserRuleContext> keys = new ArrayList<>();
        if (clause.expressionList() != null) {
            keys.addAll(clause.expressionList().expression());
            return keys;
        }
        keys.addAll(keys(clause.booleanExprList().booleanExpr()));
        return keys;
    }
}
