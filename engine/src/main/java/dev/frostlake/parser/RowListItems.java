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

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * ORDER BY and GROUP BY read a ROW's values one by one (live-verified): {@code ORDER BY (b, a) DESC} orders by
 * {@code b DESC, a DESC}, {@code ORDER BY (2, 1)} by the second and the first select item as the numbers do on their
 * own, and {@code GROUP BY (a, b)} groups by {@code a} and by {@code b}. A row inside a row, and a row behind
 * parentheses of its own, read the same way.
 *
 * <p>The clause is rewritten as it is parsed: an item that is a row is replaced by one item for each of its values,
 * so everything that reads the clause reads the list the account orders or groups by.
 */
final class RowListItems {

    private RowListItems() {
    }

    /**
     * Replaces each ORDER BY item that is a row by one item for each value, each under the row's own ASC, DESC and
     * NULLS words.
     *
     * @param clause the ORDER BY clause
     */
    static void splitOrderItems(final FrostlakeParser.OrderByClauseContext clause) {
        if (clause.children == null) {
            return;
        }
        final List<ParseTree> rebuilt = new ArrayList<>();
        boolean split = false;
        for (final ParseTree child : clause.children) {
            final List<FrostlakeParser.ExpressionContext> values = child instanceof FrostlakeParser.OrderItemContext
                ? valuesOf(((FrostlakeParser.OrderItemContext) child).expression()) : null;
            if (values == null) {
                rebuilt.add(child);
                continue;
            }
            split = true;
            final FrostlakeParser.OrderItemContext written = (FrostlakeParser.OrderItemContext) child;
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    rebuilt.add(separator(clause));
                }
                final FrostlakeParser.OrderItemContext item =
                    new FrostlakeParser.OrderItemContext(clause, written.invokingState);
                final FrostlakeParser.ExpressionContext value = values.get(i);
                value.setParent(item);
                item.addChild(value);
                for (int word = 1; word < written.getChildCount(); word++) {
                    if (written.getChild(word) instanceof TerminalNode) {
                        item.addChild(new TerminalNodeImpl(((TerminalNode) written.getChild(word)).getSymbol()));
                    }
                }
                item.start = value.getStart();
                item.stop = value.getStop();
                rebuilt.add(item);
            }
        }
        if (split) {
            clause.children = rebuilt;
        }
    }

    /**
     * Replaces each GROUP BY element that is a row by one element for each value.
     *
     * @param clause the GROUP BY clause
     */
    static void splitGroupElements(final FrostlakeParser.GroupByClauseContext clause) {
        if (clause.children == null) {
            return;
        }
        final List<ParseTree> rebuilt = new ArrayList<>();
        boolean split = false;
        for (final ParseTree child : clause.children) {
            final List<FrostlakeParser.ExpressionContext> values = child instanceof FrostlakeParser.GroupByElementContext
                ? valuesOf(((FrostlakeParser.GroupByElementContext) child).expression()) : null;
            if (values == null) {
                rebuilt.add(child);
                continue;
            }
            split = true;
            final FrostlakeParser.GroupByElementContext written = (FrostlakeParser.GroupByElementContext) child;
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    rebuilt.add(separator(clause));
                }
                final FrostlakeParser.GroupByElementContext element =
                    new FrostlakeParser.GroupByElementContext(clause, written.invokingState);
                final FrostlakeParser.ExpressionContext value = values.get(i);
                value.setParent(element);
                element.addChild(value);
                element.start = value.getStart();
                element.stop = value.getStop();
                rebuilt.add(element);
            }
        }
        if (split) {
            clause.children = rebuilt;
        }
    }

    /** The values of the row an expression is, a row among them read as its own values; null for no row. */
    private static List<FrostlakeParser.ExpressionContext> valuesOf(final ParseTree written) {
        final FrostlakeParser.RowExprContext row = rowBehind(written);
        if (row == null) {
            return null;
        }
        final List<FrostlakeParser.ExpressionContext> values = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext value : row.rowValues) {
            final List<FrostlakeParser.ExpressionContext> nested = valuesOf(value);
            if (nested == null) {
                values.add(value);
            } else {
                values.addAll(nested);
            }
        }
        return values;
    }

    /** The row an expression is, behind any parentheses around it; null for any other expression. */
    private static FrostlakeParser.RowExprContext rowBehind(final ParseTree written) {
        if (written instanceof FrostlakeParser.RowExprContext) {
            return (FrostlakeParser.RowExprContext) written;
        }
        if (written instanceof FrostlakeParser.ValueExprContext) {
            return rowBehind(((FrostlakeParser.ValueExprContext) written).expression());
        }
        return written instanceof FrostlakeParser.ParenExprContext
            ? rowBehind(((FrostlakeParser.ParenExprContext) written).booleanExpr()) : null;
    }

    private static TerminalNode separator(final FrostlakeParser.OrderByClauseContext clause) {
        final TerminalNodeImpl comma = new TerminalNodeImpl(new CommonToken(FrostlakeParser.COMMA, ","));
        comma.setParent(clause);
        return comma;
    }

    private static TerminalNode separator(final FrostlakeParser.GroupByClauseContext clause) {
        final TerminalNodeImpl comma = new TerminalNodeImpl(new CommonToken(FrostlakeParser.COMMA, ","));
        comma.setParent(clause);
        return comma;
    }
}
