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

package dev.frostlake.metastore.model;

import dev.frostlake.types.DataType;

import java.util.Collections;
import java.util.List;

/**
 * One column position of a relation a FROM clause assembles, as a positional reference ({@code $n}) reads
 * it: a column of one of the joined relations, a position past a staged file's fields, which reads NULL
 * (see {@link StagePositions}), or the merged key of a USING / NATURAL join, which reads the first
 * non-NULL of the copies it merges.
 */
public final class RelationSlot {

    private final Table relation;
    private final int column;
    private final String name;
    private final List<RelationSlot> copies;

    private RelationSlot(final Table relation, final int column, final String name,
                         final List<RelationSlot> copies) {
        this.relation = relation;
        this.column = column;
        this.name = name;
        this.copies = copies;
    }

    /**
     * A column of one relation.
     *
     * @param relation the relation, by identity
     * @param column   the column's index in the relation
     * @return the slot
     */
    public static RelationSlot column(final Table relation, final int column) {
        return new RelationSlot(relation, column, relation.getColumns().get(column).getName(),
            Collections.<RelationSlot>emptyList());
    }

    /**
     * The column one relation's {@code $position} reads: its column at that position, or over a staged-file
     * query the field there, or NULL past the fields up to the positions the query reads — never the
     * METADATA$ columns after the fields.
     *
     * @param relation the relation, by identity
     * @param position the reference's N, 1-based
     * @return the slot, or null when the relation has no such position
     */
    public static RelationSlot at(final Table relation, final int position) {
        final StagePositions staged = relation.getStagePositions();
        final int fields = staged == null ? relation.getColumns().size() : staged.fields();
        final int positions = staged == null ? fields : staged.positions();
        if (position < 1 || position > positions) {
            return null;
        }
        if (position <= fields) {
            return column(relation, position - 1);
        }
        return new RelationSlot(relation, -1, "$" + position, Collections.<RelationSlot>emptyList());
    }

    /**
     * Whether a position past one relation's positions names a column that does not exist rather than an
     * invalid identifier: so it does over a CSV query of a named or user stage (see
     * {@link StagePositions#missingPastPositions}).
     *
     * @param relation the relation
     * @return true for such a query
     */
    public static boolean refusesPastPositionsAsMissing(final Table relation) {
        return relation.getStagePositions() != null && relation.getStagePositions().missingPastPositions();
    }

    /**
     * The merged key of a USING / NATURAL join.
     *
     * @param name   the key's name
     * @param copies the key's copies on the join's sides, left first
     * @return the slot
     */
    public static RelationSlot mergedKey(final String name, final List<RelationSlot> copies) {
        return new RelationSlot(null, -1, name, Collections.unmodifiableList(copies));
    }

    /** @return the relation a column slot reads, or null for a merged key */
    public Table getRelation() {
        return relation;
    }

    /** @return the column's index in its relation, or -1 for a merged key */
    public int getColumn() {
        return column;
    }

    /** @return the column's name */
    public String getName() {
        return name;
    }

    /** @return the copies a merged key reads, first non-NULL first; empty for a column slot */
    public List<RelationSlot> getCopies() {
        return copies;
    }

    /** @return whether this slot is a USING / NATURAL join's merged key */
    public boolean isMergedKey() {
        return relation == null;
    }

    /** @return whether this slot is a position past a staged file's fields, which reads NULL */
    public boolean isPastFields() {
        return relation != null && column < 0;
    }

    /** @return the column's declared type: a merged key takes its first copy's, a position past a staged
     *  file's fields the fields' */
    public DataType getDataType() {
        if (isPastFields()) {
            return relation.getStagePositions().fieldType();
        }
        if (relation != null) {
            return relation.getColumns().get(column).getDataType();
        }
        return copies.isEmpty() ? null : copies.get(0).getDataType();
    }
}
