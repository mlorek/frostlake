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
import java.util.List;

/**
 * Represents a FOREIGN KEY constraint (final not enforced, final metadata only)
 */
public class ForeignKeyConstraint {
    // Not final: ALTER TABLE … RENAME CONSTRAINT moves a declared constraint to a new name.
    private String constraintName;
    private final List<String> columnNames;
    private final String referencedTable;
    private final List<String> referencedColumns;
    private final ReferentialAction onDelete;
    private final ReferentialAction onUpdate;
    private final Boolean rely;  // null = not specified, true = RELY, false = NORELY

    /**
     * @param constraintName the name from an explicit {@code CONSTRAINT <name>} clause; null or blank when
     *                       the constraint was declared without one, in which case it is auto-named
     *                       {@code SYS_CONSTRAINT_<uuid>} the way Snowflake does (see {@link ConstraintNames}).
     */
    public ForeignKeyConstraint(final String constraintName, final List<String> columnNames,
                               final String referencedTable, final List<String> referencedColumns,
                               final String onDelete, final String onUpdate, final Boolean rely) {
        this.constraintName = constraintName == null || constraintName.isEmpty()
            ? ConstraintNames.generate() : constraintName;
        this.columnNames = columnNames;
        this.referencedTable = referencedTable;
        this.referencedColumns = referencedColumns;
        this.onDelete = ReferentialAction.fromString(onDelete);
        this.onUpdate = ReferentialAction.fromString(onUpdate);
        this.rely = rely;
    }

    // Backward compatibility constructor
    public ForeignKeyConstraint(final String constraintName, final List<String> columnNames,
                               final String referencedTable, final List<String> referencedColumns,
                               final String onDelete, final String onUpdate) {
        this(constraintName, columnNames, referencedTable, referencedColumns, onDelete, onUpdate, null);
    }

    public String getConstraintName() {
        return constraintName;
    }

    /** Rename it in place — ALTER TABLE … RENAME CONSTRAINT. */
    public void setConstraintName(final String constraintName) {
        this.constraintName = constraintName;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public String getReferencedTable() {
        return referencedTable;
    }

    /**
     * This constraint naming another parent table: the same name, columns, actions and RELY. A clone's foreign key
     * refers to the clone's copy of a parent cloned with it (see {@code CloneReferences}).
     *
     * @param table the parent table, as a reference is written
     * @return the copy
     */
    public ForeignKeyConstraint referencing(final String table) {
        return new ForeignKeyConstraint(constraintName, columnNames, table, referencedColumns, getOnDelete(),
            getOnUpdate(), rely);
    }

    public List<String> getReferencedColumns() {
        return referencedColumns;
    }

    /**
     * This constraint after one of its own columns was renamed: the same name, parent, actions and RELY.
     *
     * @param oldName the column's name before the rename
     * @param newName its name after it
     * @return the copy
     */
    public ForeignKeyConstraint withColumnRenamed(final String oldName, final String newName) {
        return new ForeignKeyConstraint(constraintName, renamedIn(columnNames, oldName, newName), referencedTable,
            referencedColumns, getOnDelete(), getOnUpdate(), rely);
    }

    /**
     * This constraint after a column of its parent table was renamed: the referenced column follows the rename.
     *
     * @param oldName the parent column's name before the rename
     * @param newName its name after it
     * @return the copy
     */
    public ForeignKeyConstraint withReferencedColumnRenamed(final String oldName, final String newName) {
        return new ForeignKeyConstraint(constraintName, columnNames, referencedTable,
            renamedIn(referencedColumns, oldName, newName), getOnDelete(), getOnUpdate(), rely);
    }

    /** The names with {@code oldName} replaced by {@code newName}, matched as a column is (ignoring case). */
    private static List<String> renamedIn(final List<String> names, final String oldName, final String newName) {
        final List<String> renamed = new ArrayList<>(names.size());
        for (final String name : names) {
            renamed.add(name.equalsIgnoreCase(oldName) ? newName : name);
        }
        return renamed;
    }

    public String getOnDelete() {
        return onDelete != null ? onDelete.getSqlText() : null;
    }

    public String getOnUpdate() {
        return onUpdate != null ? onUpdate.getSqlText() : null;
    }

    public Boolean getRely() {
        return rely;
    }

    @Override
    public String toString() {
        return "ForeignKeyConstraint{" +
                "constraintName='" + constraintName + '\'' +
                ", columns=" + columnNames +
                ", references=" + referencedTable + "(" + referencedColumns + ")" +
                (onDelete != null ? ", ON DELETE " + onDelete.getSqlText() : "") +
                (onUpdate != null ? ", ON UPDATE " + onUpdate.getSqlText() : "") +
                (rely != null ? (rely ? ", RELY" : ", NORELY") : "") +
                '}';
    }
}
