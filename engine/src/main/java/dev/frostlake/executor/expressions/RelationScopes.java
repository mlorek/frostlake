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

import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.RelationSlot;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * The name scopes a column reference resolves against in a FROM clause of several relations (see
 * {@link JoinedRelations}): which scopes carry a bare name, and which column a positional reference
 * ({@code $n}) reads. Live answers a bare name or a {@code $n} that two scopes can resolve with
 * {@code ambiguous column name 'X'}, a {@code $n} no scope is wide enough for with
 * {@code invalid identifier '$n'}, and otherwise reads the one scope's column. A staged-file query is as
 * wide as the positions it reads (see {@code StagePositions}).
 */
public final class RelationScopes {

    /** The relations in scope, in combined-row order. */
    private final List<Table> relations;
    /** Their name scopes as the joins recorded them, or null when no join recorded any. */
    private final JoinedRelations joined;

    /**
     * @param table     the relation the FROM clause assembles (a join's merged relation), or null
     * @param relations every relation in scope, in combined-row order
     */
    public RelationScopes(final Table table, final List<Table> relations) {
        this.relations = relations;
        this.joined = table == null ? null : table.getJoinedRelations();
    }

    /**
     * How many scopes carry a column of exactly this name — a quoted name verbatim, an unquoted one
     * upper-cased, as the reference means it.
     *
     * @param name the name
     * @return the number of scopes
     */
    public int scopesCarrying(final String name) {
        final List<Integer> scopes = new ArrayList<>();
        int unscoped = 0;
        for (final Table relation : relations) {
            if (relation == null || !carries(relation, name)) {
                continue;
            }
            final int scope = joined == null ? -1 : joined.scopeOf(relation);
            if (scope < 0) {
                unscoped++;
            } else if (!scopes.contains(scope)) {
                scopes.add(scope);
            }
        }
        return scopes.size() + unscoped;
    }

    /**
     * The columns a positional reference {@code $ordinal} can read: one per scope at least that wide.
     *
     * @param ordinal the reference's N, 1-based
     * @return the candidate slots; one means the reference resolves
     */
    public List<RelationSlot> positional(final int ordinal) {
        final List<RelationSlot> candidates = new ArrayList<>();
        final List<Integer> seen = new ArrayList<>();
        for (final Table relation : relations) {
            if (relation == null) {
                continue;
            }
            final int scope = joined == null ? -1 : joined.scopeOf(relation);
            if (scope >= 0 && joined.scopeMembers(scope).size() > 1) {
                // A USING or NATURAL join's merged relation, in its own layout.
                if (seen.contains(scope)) {
                    continue;
                }
                seen.add(scope);
                final List<RelationSlot> layout = joined.scopeLayout(scope);
                if (ordinal >= 1 && ordinal <= layout.size()) {
                    candidates.add(layout.get(ordinal - 1));
                }
            } else {
                final RelationSlot slot = RelationSlot.at(relation, ordinal);
                if (slot != null) {
                    candidates.add(slot);
                }
            }
        }
        return candidates;
    }

    /**
     * Whether a positional reference no scope can read names a column that does not exist rather than an
     * invalid identifier: so it does when a staged-file query in scope reads past its fields, and the
     * position lies past even those (see {@link RelationSlot#refusesPastPositionsAsMissing}).
     *
     * @return true when such a query is in scope
     */
    public boolean refusesPastPositionsAsMissing() {
        for (final Table relation : relations) {
            if (relation != null && RelationSlot.refusesPastPositionsAsMissing(relation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The column {@code relation.$ordinal} reads: the relation's own Nth column (see {@link RelationSlot#at}),
     * unless a USING or NATURAL join merged the relation into a wider one, whose sides no longer answer by
     * position (live refuses {@code f.$1} over {@code f JOIN g USING (a)}).
     *
     * @param relation the relation the qualifier names
     * @param ordinal  the reference's N, 1-based
     * @return the slot, or null when the reference names no column
     */
    public RelationSlot qualifiedPositional(final Table relation, final int ordinal) {
        if (relation == null) {
            return null;
        }
        if (joined != null) {
            final int scope = joined.scopeOf(relation);
            if (scope >= 0 && joined.scopeMembers(scope).size() > 1) {
                return null;
            }
        }
        return RelationSlot.at(relation, ordinal);
    }

    /**
     * Whether a row is the combined row of these relations, so a slot can be read from it by position.
     *
     * @param row the row
     * @return true when its width is the relations' total width
     */
    public boolean fits(final Row row) {
        int width = 0;
        for (final Table relation : relations) {
            width += relation == null ? 0 : relation.getColumns().size();
        }
        return row != null && row.getValues().size() == width;
    }

    /**
     * The value a slot reads in a combined row: its relation's column, or the first non-NULL copy of a
     * merged key.
     *
     * @param slot the slot
     * @param row  the combined row, as {@link #fits} accepts it
     * @return the value
     */
    public Object valueOf(final RelationSlot slot, final Row row) {
        if (slot.isPastFields()) {
            return null;
        }
        if (slot.isMergedKey()) {
            for (final RelationSlot copy : slot.getCopies()) {
                final Object value = valueOf(copy, row);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        int offset = 0;
        for (final Table relation : relations) {
            if (relation == slot.getRelation()) {
                return row.getValue(offset + slot.getColumn());
            }
            offset += relation == null ? 0 : relation.getColumns().size();
        }
        return null;
    }

    private static boolean carries(final Table relation, final String name) {
        for (final TableColumn column : relation.getColumns()) {
            if (column.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
