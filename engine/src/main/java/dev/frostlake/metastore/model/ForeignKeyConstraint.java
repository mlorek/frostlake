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

    public List<String> getReferencedColumns() {
        return referencedColumns;
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
