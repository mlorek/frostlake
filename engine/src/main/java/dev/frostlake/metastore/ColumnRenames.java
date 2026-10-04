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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What follows a column outside its own table when ALTER TABLE ... RENAME COLUMN renames it: the foreign keys of the
 * tables that name it as their parent's column, declared as constraints or on a column. Everything the renamed table
 * holds by the column's name moves with the rename itself ({@code Table.renameColumn}).
 *
 * <p>A foreign key's parent is read where the child lives, as {@link CloneReferences} reads it: a bare name in the
 * child's schema, a two-part one in its database.
 */
public final class ColumnRenames {

    private ColumnRenames() {
    }

    /**
     * Points every foreign key that references the renamed column at its new name.
     *
     * @param catalog the catalog
     * @param home    the schema holding the renamed column's table
     * @param parent  the table
     * @param oldName the column's name before the rename
     * @param newName its name after it
     */
    public static void followInForeignKeys(final Catalog catalog, final Schema home, final Table parent,
                                           final String oldName, final String newName) {
        final String[] parentName = {home.getDatabaseName(), home.getName(), parent.getName()};
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                final String[] where = {database.getName(), schema.getName()};
                final List<Table> children = new ArrayList<>(schema.getTables());
                children.addAll(schema.getShadowedTables());
                for (final Table child : children) {
                    follow(child, where, parentName, oldName, newName);
                }
            }
        }
    }

    private static void follow(final Table child, final String[] where, final String[] parentName,
                               final String oldName, final String newName) {
        boolean followed = false;
        for (final ForeignKeyConstraint foreignKey : child.getForeignKeys()) {
            if (names(foreignKey.getReferencedTable(), where, parentName)) {
                child.replaceForeignKey(foreignKey, foreignKey.withReferencedColumnRenamed(oldName, newName));
                followed = true;
            }
        }
        for (final TableColumn column : child.columnsView()) {
            if (column.getReferencedTable() != null && oldName.equalsIgnoreCase(column.getReferencedColumn())
                    && names(column.getReferencedTable(), where, parentName)) {
                column.setReferencedColumn(newName);
                followed = true;
            }
        }
        if (followed) {
            // The child's columns now say so; a clone as of a later point takes that.
            child.recordStructure();
        }
    }

    /** Whether a parent reference, read where its child lives, names the table. */
    private static boolean names(final String written, final String[] where, final String[] table) {
        if (written == null) {
            return false;
        }
        final String[] parts = QualifiedName.parse(written).parts();
        final String[] full;
        if (parts.length == 3) {
            full = parts;
        } else if (parts.length == 2) {
            full = new String[] {where[0], parts[0], parts[1]};
        } else if (parts.length == 1) {
            full = new String[] {where[0], where[1], parts[0]};
        } else {
            return false;
        }
        return Arrays.equals(full, table);
    }
}
