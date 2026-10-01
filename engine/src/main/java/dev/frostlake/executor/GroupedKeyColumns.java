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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The columns a GROUP BY groups, each with the relation its key names. A reference qualified by one joined relation
 * is grouped only when that relation's column is a key: with {@code A} and {@code AB} both holding {@code x},
 * {@code SELECT AB.x … GROUP BY A.x} is refused as live refuses it (live-verified). A key that names no relation —
 * an unqualified name, or a qualifier this cannot place — groups its column for every relation, and so does a
 * column a USING or NATURAL join merges, which both sides spell.
 */
final class GroupedKeyColumns {

    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;
    /** Every grouped column name, upper-cased. */
    private final Set<String> names = new HashSet<String>();
    /** Grouped column names whose key names no relation this can place. */
    private final Set<String> unplaced = new HashSet<String>();
    /** The relations each placed column is grouped for, compared by identity. */
    private final Map<String, Map<Table, Boolean>> relations = new HashMap<String, Map<Table, Boolean>>();
    /** Column names a USING join merges; null when a NATURAL join merges every shared name. */
    private final Set<String> merged;

    /**
     * The grouped columns of one query block.
     *
     * @param table        the FROM's first relation
     * @param aliasToTable its relations by alias
     * @param allTables    every joined relation
     * @param clause       the query block, whose joins say which names are merged
     */
    GroupedKeyColumns(final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables,
                      final FrostlakeParser.SelectClauseContext clause) {
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
        this.merged = mergedNames(clause);
    }

    /**
     * Record one grouping key as written, or in one of its rewrites.
     *
     * @param keyText the key's text
     */
    void add(final String keyText) {
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(keyText);
        } catch (final RuntimeException notAnExpression) {
            return;
        }
        if (!(parsed instanceof ColumnReferenceExpression)) {
            return;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) parsed;
        final String column = reference.getColumnName().toUpperCase();
        names.add(column);
        final Table relation = reference.getTableName() == null ? null : relationOf(reference.getTableName());
        if (relation == null) {
            unplaced.add(column);
            return;
        }
        Map<Table, Boolean> grouped = relations.get(column);
        if (grouped == null) {
            grouped = new IdentityHashMap<Table, Boolean>();
            relations.put(column, grouped);
        }
        grouped.put(relation, Boolean.TRUE);
    }

    /** Whether any key groups a column of this name, whichever relation it names. */
    boolean hasName(final String column) {
        return names.contains(column.toUpperCase());
    }

    /**
     * Whether a reference to {@code column} qualified by {@code qualifier} reads a grouped column.
     *
     * @param qualifier the reference's qualifier, its last part used, or null for an unqualified reference
     * @param column    the column name
     * @return true when the column is grouped for the relation the qualifier names
     */
    boolean grouped(final String qualifier, final String column) {
        final String name = column.toUpperCase();
        if (!names.contains(name)) {
            return false;
        }
        if (qualifier == null || unplaced.contains(name) || merged == null || merged.contains(name)) {
            return true;
        }
        final Table relation = relationOf(qualifier);
        final Map<Table, Boolean> grouped = relations.get(name);
        return relation == null || grouped == null || grouped.containsKey(relation);
    }

    /** The relation a qualifier names — by alias first, then by table name — or null when it names none. */
    private Table relationOf(final String qualifier) {
        final int dot = qualifier.lastIndexOf('.');
        final String last = unquoted(dot < 0 ? qualifier : qualifier.substring(dot + 1));
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(last)) {
                    return entry.getValue();
                }
            }
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && last.equalsIgnoreCase(candidate.getName())) {
                    return candidate;
                }
            }
        }
        return table != null && last.equalsIgnoreCase(table.getName()) ? table : null;
    }

    private static String unquoted(final String part) {
        return part.length() > 1 && part.startsWith("\"") && part.endsWith("\"") ? part.substring(1, part.length() - 1)
            : part;
    }

    /** The names a USING join merges, or null when a NATURAL join merges every shared name. */
    private static Set<String> mergedNames(final FrostlakeParser.SelectClauseContext clause) {
        final Set<String> merged = new HashSet<String>();
        if (clause == null || clause.tableExpression() == null) {
            return merged;
        }
        final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<FrostlakeParser.JoinClauseContext>(
            clause.tableExpression().joinClause());
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            if (join.NATURAL() != null) {
                return null;
            }
            if (join.usingColumnList() != null) {
                for (final FrostlakeParser.QualifiedNameContext name : join.usingColumnList().qualifiedName()) {
                    final String[] parts = ParseTreeText.qualifiedNameParts(name);
                    merged.add(parts[parts.length - 1].toUpperCase());
                }
            }
        }
        return merged;
    }
}
