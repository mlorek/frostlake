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
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether the statistics pin a column a GENERATOR bound reads to one value on every row, which the account reads the
 * bound as (all live-verified): over {@code g1} holding the one row 1, {@code FROM g1, TABLE(GENERATOR(ROWCOUNT =>
 * g1.n + 1))} runs two rows a row, as over two rows of 5 {@code ROWCOUNT => g3.n} runs five, and a scalar, EXISTS or
 * LATERAL subquery reading the outer column alike. A catalog table's column is pinned where it has rows and every row
 * holds one value - the NULL included, which a bound reads as no rows - as whatever DELETE or UPDATE last left them;
 * a derived relation's column - a subquery's, a CTE's, a view's - where it passes such a column through or projects
 * a literal that is not NULL (see {@link DerivedColumnLineage}), or computes one value from pinned columns, from the
 * aggregates the statistics answer or from a UNION of one value (see {@link DerivedRelationPins}). An empty table's
 * column is not, nor one holding a NULL beside a value, nor a VALUES list's, nor one an outer join may extend with
 * NULLs.
 */
final class BoundColumnPins {

    private final StoredColumnValues values;
    private final DerivedRelationPins derived;
    private final Map<String, Table> relations = new HashMap<>();

    /**
     * @param executor the executor whose storage the columns are read from
     * @param scope    the relations a bound may read, by the names they answer to, or null
     */
    BoundColumnPins(final QueryExecutor executor, final Map<String, Table> scope) {
        this.values = new StoredColumnValues(executor);
        this.derived = new DerivedRelationPins(executor, values);
        if (scope != null) {
            for (final Map.Entry<String, Table> relation : scope.entrySet()) {
                if (relation.getValue() != null) {
                    relations.put(relation.getKey().toUpperCase(Locale.ROOT), relation.getValue());
                }
            }
        }
    }

    /**
     * Whether a column a bound reads holds one value on every row of its relation.
     *
     * @param reference the column as the bound writes it
     * @return true where the statistics pin it
     */
    boolean pins(final FrostlakeParser.QualifiedNameExprContext reference) {
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        final String column = parts[parts.length - 1];
        final String qualifier = parts.length > 1 ? parts[parts.length - 2].toUpperCase(Locale.ROOT) : null;
        final String key = qualifier != null ? qualifier : keyCarrying(column);
        final Table owner = key == null ? null : relations.get(key);
        if (owner == null || !owner.hasColumn(column) || nullExtended(reference).contains(key)) {
            return false;
        }
        try {
            final String declared = owner.getColumn(column).getName();
            if (owner.residentSource() != null) {
                return holdsOneValue(owner, declared);
            }
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, declared);
            if (lineage != null
                    && (lineage.isLiteral() || holdsOneValue(lineage.storedTable(), lineage.storedColumn()))) {
                return true;
            }
            return derived.pins(reference, key, declared);
        } catch (final RuntimeException unreadable) {
            return false;
        }
    }

    private boolean holdsOneValue(final Table owner, final String column) {
        return values.holdsOneValue(owner, column) || values.holdsOnlyNulls(owner, column);
    }

    /** The name of the one relation carrying a bare column, or null where none or several do. */
    private String keyCarrying(final String column) {
        String found = null;
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (!relation.getValue().hasColumn(column)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = relation.getKey();
        }
        return found;
    }

    /**
     * The names of the relations an outer join extends with NULLs, in every FROM around a node: a LEFT join's right
     * side, what a RIGHT join's left side holds since the last comma, both sides of a FULL one.
     */
    private static Set<String> nullExtended(final ParseTree node) {
        final Set<String> extended = new HashSet<>();
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.TableExpressionContext)) {
                continue;
            }
            final List<String> group = new ArrayList<>();
            for (int i = 0; i < at.getChildCount(); i++) {
                final ParseTree child = at.getChild(i);
                if (child instanceof FrostlakeParser.TableReferenceContext) {
                    group.clear();
                    addName(group, (FrostlakeParser.TableReferenceContext) child);
                } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                    final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) child;
                    final List<String> joined = new ArrayList<>();
                    addName(joined, join.tableReference());
                    final FrostlakeParser.JoinTypeContext type = join.joinType();
                    if (type != null && (type.LEFT() != null || type.FULL() != null)) {
                        extended.addAll(joined);
                    }
                    if (type != null && (type.RIGHT() != null || type.FULL() != null)) {
                        extended.addAll(group);
                    }
                    group.addAll(joined);
                }
            }
        }
        return extended;
    }

    private static void addName(final List<String> into, final FrostlakeParser.TableReferenceContext reference) {
        final String name = reference == null ? null : GeneratorBounds.qualifierOf(reference);
        if (name != null) {
            into.add(name);
        }
    }
}
