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
 * One UNIQUE constraint, spanning one or more columns.
 *
 * <p>Frostlake enforces uniqueness through a per-column flag on {@link TableColumn}; this record sits
 * ALONGSIDE those flags and remembers the two things a boolean flag cannot express — the name an explicit
 * {@code CONSTRAINT <name> UNIQUE (...)} clause gave the constraint, and the fact that several columns
 * belong to ONE constraint rather than to one constraint each. A bare column-level {@code UNIQUE} needs
 * neither, so it has no record; {@link Table#uniqueConstraintName(String)} names it instead and
 * {@link Table#getUniqueConstraints()} synthesizes its single-column entry.
 */
public class UniqueConstraint {

    private final String constraintName;
    private final List<String> columnNames;

    /**
     * @param constraintName the name from an explicit {@code CONSTRAINT <name>} clause; null or blank when
     *                       the constraint was declared without one, in which case it is auto-named
     *                       {@code SYS_CONSTRAINT_<uuid>} the way Snowflake does (see {@link ConstraintNames}).
     */
    public UniqueConstraint(final String constraintName, final List<String> columnNames) {
        this.constraintName = constraintName == null || constraintName.isEmpty()
            ? ConstraintNames.generate() : constraintName;
        this.columnNames = new ArrayList<>(columnNames);
    }

    public String getConstraintName() {
        return constraintName;
    }

    public List<String> getColumnNames() {
        return new ArrayList<>(columnNames);
    }

    /** True when this constraint spans the named column (case-insensitively). */
    public boolean covers(final String columnName) {
        for (final String name : columnNames) {
            if (name.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    /** Follow a column rename: the constraint is unchanged, only the name of a column it spans moved. */
    public void renameColumn(final String oldName, final String newName) {
        for (int i = 0; i < columnNames.size(); i++) {
            if (columnNames.get(i).equalsIgnoreCase(oldName)) {
                columnNames.set(i, newName);
            }
        }
    }

    @Override
    public String toString() {
        return "UniqueConstraint{constraintName='" + constraintName + "', columns=" + columnNames + "}";
    }
}
