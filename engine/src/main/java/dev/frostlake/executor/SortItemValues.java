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
import dev.frostlake.executor.expressions.VolatileCalls;
import dev.frostlake.executor.operators.WhereAliasValues;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The values of the select items an ORDER BY key reads ahead of the projection, computed once a row and kept for the
 * projection to show (see WhereAliasValues): a volatile item — a sequence's NEXTVAL, RANDOM() — that a key names by
 * its alias or by its position is drawn once a row, over a join too, and the rows come out in the order of the values
 * shown; an item that reads such an item's alias reads the value kept for it rather than drawing it again
 * (live-verified). An item computed alike each time, or one that reads the row's place, is left to the key's own
 * reading.
 */
final class SortItemValues {

    private final QueryExecutor executor;
    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;
    private final WhereAliasValues kept;
    /** Per select item: its expression as written, or null for a star. */
    private final List<String> texts = new ArrayList<>();
    /** Per select item: its alias, upper-cased as kept values are keyed, or null. */
    private final List<String> aliases = new ArrayList<>();
    /** Per select item: the earlier items whose aliases it reads. */
    private final List<List<Integer>> reads = new ArrayList<>();
    /** Per select item: whether computing it draws anew, itself or through an alias it reads. */
    private final List<Boolean> drawn = new ArrayList<>();
    /** Per select item computed here: its evaluator, and the earlier aliases' values it reads them through. */
    private final Map<Integer, ExpressionEvaluator> evaluators = new HashMap<>();
    private final Map<Integer, Map<String, Object>> bindings = new HashMap<>();

    /**
     * @param executor     the executor whose functions and catalog the items read
     * @param clause       the select clause whose items the keys name
     * @param table        the relation the rows are in
     * @param aliasToTable the FROM clause's relations by name
     * @param allTables    every relation in the FROM clause
     * @param kept         the values kept for the projection to show
     */
    SortItemValues(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext clause, final Table table,
                   final Map<String, Table> aliasToTable, final List<Table> allTables, final WhereAliasValues kept) {
        this.executor = executor;
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
        this.kept = kept;
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                texts.add(null);
                aliases.add(null);
                reads.add(Collections.<Integer>emptyList());
                drawn.add(Boolean.FALSE);
                continue;
            }
            final String text = ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
            final String alias = SelectItemAccessors.getItemAlias(item);
            Expression parsed = null;
            try {
                parsed = ExpressionEvaluator.parse(text);
            } catch (final RuntimeException unparsed) {
                // An item the expression parser does not take is left to the key's own reading.
            }
            final List<Integer> itemReads = parsed == null ? Collections.<Integer>emptyList()
                : earlierAliasesRead(parsed);
            boolean draws = parsed != null && VolatileCalls.writes(parsed) && !VolatileCalls.readsRowPlace(parsed);
            for (final Integer earlier : itemReads) {
                draws = draws || drawn.get(earlier.intValue()).booleanValue();
            }
            texts.add(parsed == null ? null : text);
            aliases.add(alias == null ? null : alias.toUpperCase(Locale.ROOT));
            reads.add(itemReads);
            drawn.add(Boolean.valueOf(parsed != null && draws));
        }
    }

    /** The earlier items whose aliases an item reads by a bare name that is no column of the FROM clause's. */
    private List<Integer> earlierAliasesRead(final Expression parsed) {
        final List<Integer> read = new ArrayList<>();
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        parsed.accept(walk);
        for (final ColumnReferenceExpression reference : walk.references()) {
            if (reference.isQualified() || reference.getColumnName() == null) {
                continue;
            }
            final String name = reference.getColumnName().toUpperCase(Locale.ROOT);
            if (isInputColumn(name)) {
                continue;
            }
            for (int earlier = 0; earlier < aliases.size(); earlier++) {
                if (name.equals(aliases.get(earlier)) && !read.contains(Integer.valueOf(earlier))) {
                    read.add(Integer.valueOf(earlier));
                }
            }
        }
        return read;
    }

    /** Whether a name is a column of the FROM clause's, which an alias of the same name does not hide. */
    private boolean isInputColumn(final String name) {
        if (table != null && table.hasColumn(name)) {
            return true;
        }
        if (allTables != null) {
            for (final Table joined : allTables) {
                if (joined != null && joined.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the sort keeps the value it computes for an item: one drawn anew each time it is computed.
     *
     * @param item the item's place in the select list, from 0, or -1 for none
     * @return true when the item's value is computed here and kept
     */
    boolean keeps(final int item) {
        return item >= 0 && item < drawn.size() && drawn.get(item).booleanValue();
    }

    /**
     * An item's value for a row: the value kept for it, or computed now — the earlier aliases it reads bound to their
     * own values — and kept, under its alias and its place, for the projection to show.
     *
     * @param row  the row
     * @param item the item's place in the select list, from 0
     * @return the value
     */
    Object valueOf(final Row row, final int item) {
        final String alias = aliases.get(item);
        if (alias != null && kept.holds(row, alias)) {
            return kept.valueOf(row, alias);
        }
        if (kept.holdsItem(row, item)) {
            return kept.itemValueOf(row, item);
        }
        ExpressionEvaluator evaluator = evaluators.get(Integer.valueOf(item));
        Map<String, Object> bound = bindings.get(Integer.valueOf(item));
        if (evaluator == null) {
            evaluator = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            evaluator.setMultiTableContext(aliasToTable, allTables);
            bound = new HashMap<>();
            evaluator.setOuterLateralContext(bound);
            evaluators.put(Integer.valueOf(item), evaluator);
            bindings.put(Integer.valueOf(item), bound);
        }
        final Map<String, Object> values = new HashMap<>();
        for (final Integer earlier : reads.get(item)) {
            values.put(aliases.get(earlier.intValue()), valueOf(row, earlier.intValue()));
        }
        bound.clear();
        bound.putAll(values);
        final Object value = evaluator.evaluate(texts.get(item), row);
        if (alias != null) {
            kept.remember(row, alias, value);
        }
        kept.rememberItem(row, item, value);
        return value;
    }
}
