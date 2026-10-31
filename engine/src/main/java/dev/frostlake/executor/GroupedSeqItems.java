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

import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The items of a grouped select that a SEQ call numbers by the rows the query answers (live-verified). An item that
 * calls SEQ1, SEQ2, SEQ4 or SEQ8 outside an aggregate and a window is numbered over the rows answered, in the order
 * they are answered - after the ORDER BY, a DISTINCT and the row limits - unless the ORDER BY reads it, by its alias,
 * its position or its own text: then it is numbered over the groups the HAVING keeps, in the order they were formed,
 * before the rows are sorted. An item that is a group key shows the value its group was formed by, and a SEQ inside
 * an aggregate numbers the scan the grouping read; neither is numbered again here.
 */
final class GroupedSeqItems {

    private static final GroupedSeqItems NONE =
        new GroupedSeqItems(Collections.<Integer>emptyList(), Collections.<Integer>emptyList());

    private final List<Integer> ordered;
    private final List<Integer> answered;

    private GroupedSeqItems(final List<Integer> ordered, final List<Integer> answered) {
        this.ordered = ordered;
        this.answered = answered;
    }

    /** The items numbered before the rows are sorted, because the ORDER BY reads them. */
    List<Integer> ordered() {
        return ordered;
    }

    /** The items numbered over the rows answered. */
    List<Integer> answered() {
        return answered;
    }

    /**
     * The numbered items of a grouped select.
     *
     * @param statement the statement, whose ORDER BY may read an item
     * @param select    the grouped select
     * @param registry  the registry that tells an aggregate call
     * @return the items, by their place in the select list
     */
    static GroupedSeqItems of(final FrostlakeParser.SelectStatementContext statement,
                              final FrostlakeParser.SelectClauseContext select, final FunctionRegistry registry) {
        if (select.groupByClause() == null || select.groupByClause().ALL() != null) {
            return NONE;
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (SelectItemAccessors.isStarItem(item)) {
                return NONE;
            }
        }
        final List<ParserRuleContext> keys = KeyContexts.groupKeys(select.groupByClause());
        final List<Integer> ordered = new ArrayList<>();
        final List<Integer> answered = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final ParserRuleContext written = SelectItemAccessors.getItemExpression(items.get(i));
            if (written == null || !numbered(written, registry)) {
                continue;
            }
            final String text = ParseTreeText.getOriginalText(written);
            final String alias = SelectItemAccessors.getItemAlias(items.get(i));
            if (names(keys, i, text, alias)) {
                continue;
            }
            if (statement != null && statement.orderByClause() != null
                    && names(orderKeys(statement.orderByClause()), i, text, alias)) {
                ordered.add(Integer.valueOf(i));
            } else {
                answered.add(Integer.valueOf(i));
            }
        }
        return ordered.isEmpty() && answered.isEmpty() ? NONE : new GroupedSeqItems(ordered, answered);
    }

    /** Whether an item calls SEQ outside its aggregates, and holds no query of its own. */
    private static boolean numbered(final ParserRuleContext written, final FunctionRegistry registry) {
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(written));
        } catch (final RuntimeException unparsed) {
            return false;
        }
        final GroupedSeqWalk walk = new GroupedSeqWalk(registry);
        parsed.accept(walk);
        return walk.numbered() && !walk.holdsQuery();
    }

    private static List<ParserRuleContext> orderKeys(final FrostlakeParser.OrderByClauseContext orderBy) {
        final List<ParserRuleContext> keys = new ArrayList<>();
        for (final FrostlakeParser.OrderItemContext item : orderBy.orderItem()) {
            final ParserRuleContext key = KeyContexts.of(item);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Whether one of the keys names the item at {@code index}: by its position, its alias or its own text. */
    private static boolean names(final List<ParserRuleContext> keys, final int index, final String text,
                                 final String alias) {
        for (final ParserRuleContext key : keys) {
            final String written = ParseTreeText.getOriginalText(key);
            if (written.equalsIgnoreCase(text)) {
                return true;
            }
            final Expression parsed;
            try {
                parsed = ExpressionEvaluator.parse(written);
            } catch (final RuntimeException unparsed) {
                continue;
            }
            if (parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.INTEGER
                    && ((LiteralExpression) parsed).getValue() instanceof Number
                    && ((Number) ((LiteralExpression) parsed).getValue()).longValue() == index + 1) {
                return true;
            }
            if (alias == null) {
                continue;
            }
            final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
            parsed.accept(names);
            for (final ColumnReferenceExpression name : names.references()) {
                if (!name.isQualified() && alias.equalsIgnoreCase(name.getColumnName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
