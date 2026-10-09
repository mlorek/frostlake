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

package dev.frostlake.executor.commands;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The foreign keys that reference what a DROP removes. A table another table's foreign key references is not
 * dropped under RESTRICT — {@code Cannot drop the table because of dependencies} — and neither is a schema or a
 * database that holds such a table, whether the referencing table stands outside it or inside; a container that
 * holds only the referencing side is dropped. Without RESTRICT the drop goes ahead and takes the foreign keys other
 * tables hold with it, so the child keeps its columns and loses the constraint. A table's reference to itself
 * holds nothing back (live-verified).
 */
final class ForeignKeyDependents {

    private final Catalog catalog;

    ForeignKeyDependents(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Whether another table holds a foreign key that references a table.
     *
     * @param database the database the table lives in
     * @param schema   its schema
     * @param table    the table
     * @return whether the table is referenced from another one
     */
    boolean references(final String database, final Schema schema, final Table table) {
        return sweep(database, schema, table, true, false);
    }

    /** Whether a schema holds a table that another table, inside the schema or outside it, references. */
    boolean referencesInto(final String database, final Schema schema) {
        return sweep(database, schema, null, true, false);
    }

    /** Whether a database holds a table that another table, of the database or of another one, references. */
    boolean referencesInto(final String database) {
        return sweep(database, null, null, true, false);
    }

    /** Drops every foreign key another table holds on a table, leaving the referencing columns in place. */
    void release(final String database, final Schema schema, final Table table) {
        sweep(database, schema, table, true, true);
    }

    /** Drops every foreign key a table outside a schema holds on one of the schema's tables. */
    void releaseFromOutside(final String database, final Schema schema) {
        sweep(database, schema, null, false, true);
    }

    /** Drops every foreign key a table outside a database holds on one of the database's tables. */
    void releaseFromOutside(final String database) {
        sweep(database, null, null, false, true);
    }

    /**
     * Walks the tables that may hold a foreign key on the target — a table, or every table of a schema or of a
     * database — and finds the keys that reference it; a table's key on itself is never one.
     *
     * @param database the target's database
     * @param schema   the target's schema, or null when a whole database is the target
     * @param table    the target table, or null when a whole schema or database is the target
     * @param inner    whether the tables the target itself holds are walked too
     * @param drop     whether the foreign keys found are dropped
     * @return whether any was found
     */
    private boolean sweep(final String database, final Schema schema, final Table table, final boolean inner,
                          final boolean drop) {
        boolean found = false;
        for (final Database holder : catalog.getAllDatabases()) {
            for (final Schema holding : holder.getAllSchemas()) {
                final boolean inside = holder.getName().equals(database) && (schema == null || holding == schema);
                if (inside && !inner) {
                    continue;
                }
                final List<Table> children = new ArrayList<>(holding.getTables());
                children.addAll(holding.getShadowedTables());
                for (final Table child : children) {
                    for (final List<String> columns : referencing(child, holder.getName(), holding, database, schema,
                            table)) {
                        found = true;
                        if (drop) {
                            child.dropForeignKeyColumns(columns);
                        }
                    }
                    if (found && !drop) {
                        return true;
                    }
                }
            }
        }
        return found;
    }

    /** The column lists of a child's foreign keys that reference the target, in both shapes a key is kept in. */
    private static List<List<String>> referencing(final Table child, final String childDatabase, final Schema childSchema,
                                                  final String database, final Schema schema, final Table table) {
        final List<List<String>> keys = new ArrayList<>();
        for (final TableColumn column : child.getColumns()) {
            if (column.getReferencedTable() != null && names(column.getReferencedTable(), child, childDatabase,
                    childSchema, database, schema, table)) {
                keys.add(Collections.singletonList(column.getName()));
            }
        }
        for (final ForeignKeyConstraint key : child.getForeignKeys()) {
            if (names(key.getReferencedTable(), child, childDatabase, childSchema, database, schema, table)) {
                keys.add(new ArrayList<>(key.getColumnNames()));
            }
        }
        return keys;
    }

    /**
     * Whether a REFERENCES target, read from where its table stands — a bare name in the child's own schema, a
     * schema-qualified one in the child's database — names the target, and is not the child itself.
     */
    private static boolean names(final String referenced, final Table child, final String childDatabase,
                                 final Schema childSchema, final String database, final Schema schema,
                                 final Table table) {
        final QualifiedName name = QualifiedName.parse(referenced);
        final String inDatabase = name.size() >= 3 ? name.part(name.size() - 3) : childDatabase;
        final String inSchema = name.size() >= 2 ? name.part(name.size() - 2) : childSchema.getName();
        final boolean itself = inDatabase.equals(childDatabase) && inSchema.equals(childSchema.getName())
            && name.last().equals(child.getName());
        return !itself && inDatabase.equals(database) && (schema == null || inSchema.equals(schema.getName()))
            && (table == null || name.last().equals(table.getName()));
    }
}
