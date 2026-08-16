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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The collation a sorting, grouping or de-duplicating key compares under. A collation reaches a key one
 * of two ways — a column declared with one, or a COLLATE written into the key — so a query touching
 * neither can skip resolving key collations altogether.
 */
public final class KeyCollations {

    private static final String WRITTEN = "COLLATE";

    private KeyCollations() {
    }

    /**
     * Whether any key here could carry a collation at all: some relation in scope declares one on a
     * column, or some key writes COLLATE itself.
     *
     * @param keys         the key expressions as written
     * @param table        the query's base relation, or null
     * @param aliasToTable its alias map, or null
     * @param allTables    its joined relations, or null
     * @return false when no key can be collated, and the caller can compare by code point
     */
    public static boolean reachable(final List<String> keys, final Table table,
                                    final Map<String, Table> aliasToTable, final List<Table> allTables) {
        for (final String key : keys) {
            if (key != null && key.toUpperCase(Locale.ROOT).contains(WRITTEN)) {
                return true;
            }
        }
        if (declaresCollation(table)) {
            return true;
        }
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> aliased : aliasToTable.entrySet()) {
                if (declaresCollation(aliased.getValue())) {
                    return true;
                }
            }
        }
        if (allTables != null) {
            for (final Table joined : allTables) {
                if (declaresCollation(joined)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The collation each key compares under.
     *
     * @param keys      the key expressions as written
     * @param evaluator the evaluator that resolves their columns
     * @return one entry per key, null where the key carries no collation
     */
    public static CollationSpec[] resolve(final List<String> keys, final ExpressionEvaluator evaluator) {
        final CollationSpec[] rules = new CollationSpec[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            if (key == null || key.isEmpty()) {
                continue;
            }
            rules[i] = evaluator.keyCollation(ExpressionEvaluator.parse(key));
        }
        return rules;
    }

    /** Whether a relation declares a collation on any of its columns. */
    private static boolean declaresCollation(final Table relation) {
        if (relation == null) {
            return false;
        }
        for (final TableColumn column : relation.getColumns()) {
            if (column.getCollation() != null && !column.getCollation().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
