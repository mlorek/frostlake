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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A table's columns as they stood over one stretch of its history: from the moment a DDL gave the table this shape
 * until the next one changed it. A CLONE ... AT | BEFORE takes the shape of the point (documented: a clone inherits
 * "the name and structure of the source object current at the time the statement runs, or at a specified
 * time/point in the past"), while a time-travel SELECT reads every point through the table's CURRENT columns.
 *
 * <p>The columns are copies, never handed out: a structure is shared by every snapshot taken while it stood, and
 * nothing that alters the live table reaches it.
 */
public final class TableStructure {

    /** When the shape took effect, in epoch milliseconds: the instant of the statement that gave it. */
    private final long fromMillis;
    /** Where it took effect in the order snapshots are taken in (see {@code SnapshotSequence}). */
    private final long fromSequence;
    private final List<TableColumn> columns;
    /** The highest position the table had handed out by then, so the next column a clone adds follows it. */
    private final int highestOrdinal;

    TableStructure(final long fromMillis, final long fromSequence, final List<TableColumn> live,
                   final int highestOrdinal) {
        this.fromMillis = fromMillis;
        this.fromSequence = fromSequence;
        this.highestOrdinal = highestOrdinal;
        final List<TableColumn> copies = new ArrayList<>(live.size());
        for (final TableColumn column : live) {
            copies.add(column.copy());
        }
        this.columns = Collections.unmodifiableList(copies);
    }

    /**
     * A shape a snapshot of the engine's state kept, put back as it stood.
     *
     * @param fromMillis     when it took effect, in epoch milliseconds
     * @param fromSequence   where it took effect in the order snapshots are taken in
     * @param columns        its columns, which the shape copies
     * @param highestOrdinal the highest position the table had handed out by then
     * @return the shape
     */
    public static TableStructure restored(final long fromMillis, final long fromSequence,
                                          final List<TableColumn> columns, final int highestOrdinal) {
        return new TableStructure(fromMillis, fromSequence, columns, highestOrdinal);
    }

    public long getFromMillis() {
        return fromMillis;
    }

    public long getFromSequence() {
        return fromSequence;
    }

    /** The columns, in their order then. Each is the structure's own: copy one before changing it. */
    public List<TableColumn> getColumns() {
        return columns;
    }

    /** The highest position the table had given a column by then, a dropped column's included. */
    public int getHighestOrdinal() {
        return highestOrdinal;
    }

    /**
     * Whether the live columns still have this shape: the same columns in the same order, each with the name, type,
     * nullability, default, key flags, IDENTITY, comment, collation, position and foreign-key reference it had. The
     * policies and tags attached to a column are not its shape; a clone takes those as they are now.
     *
     * @param live the table's columns now
     * @return whether nothing about them changed
     */
    public boolean describes(final List<TableColumn> live) {
        if (live.size() != columns.size()) {
            return false;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (!sameShape(columns.get(i), live.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameShape(final TableColumn then, final TableColumn now) {
        // A retype installs a new type object, so the type is compared by identity: two spellings of one type compare
        // equal by name alone.
        return then.getName().equals(now.getName()) && then.getDataType() == now.getDataType()
            && then.isNullable() == now.isNullable() && Objects.equals(then.getDefaultValue(), now.getDefaultValue())
            && then.isPrimaryKey() == now.isPrimaryKey() && then.isUnique() == now.isUnique()
            && then.isAutoIncrement() == now.isAutoIncrement() && then.getIdentityStart() == now.getIdentityStart()
            && then.getIdentityIncrement() == now.getIdentityIncrement()
            && Objects.equals(then.getComment(), now.getComment())
            && Objects.equals(then.getCollation(), now.getCollation())
            && then.getOrdinalPosition() == now.getOrdinalPosition()
            && Objects.equals(then.getReferencedTable(), now.getReferencedTable())
            && Objects.equals(then.getReferencedColumn(), now.getReferencedColumn());
    }
}
