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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Two columns of one relation whose names differ only in case — a quoted {@code "x"} beside {@code X}. Where a
 * relation holds such a pair, a name matches only the column spelled exactly as it resolves: {@code GROUP BY X}
 * groups {@code X} and not {@code "x"} (live-verified).
 */
final class CaseVariantColumns {

    private CaseVariantColumns() {
    }

    /**
     * Whether a relation in scope holds two or more columns of this name in different cases.
     *
     * @param table     the query's first relation
     * @param allTables every relation in scope, or null
     * @param name      the name, in any case
     * @return whether the name has case variants
     */
    static boolean present(final Table table, final List<Table> allTables, final String name) {
        if (variantsIn(table, name)) {
            return true;
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (variantsIn(candidate, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the relation holds this name in two or more SPELLINGS. Two columns spelled alike — the same column
     * of two joined relations — are no case variants: each is reached through its own relation.
     */
    private static boolean variantsIn(final Table table, final String name) {
        if (table == null || name == null) {
            return false;
        }
        final Set<String> spellings = new HashSet<>();
        for (final TableColumn column : table.getColumns()) {
            if (name.equalsIgnoreCase(column.getName())) {
                spellings.add(column.getName());
            }
        }
        return spellings.size() > 1;
    }
}
