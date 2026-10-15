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

    /**
     * Whether another table holds a foreign key on one key of a table: a foreign key references the key whose columns
     * it names, and one that names none references the primary key.
     *
     * @param database the table's database
     * @param schema   its schema
     * @param table    the table
     * @param key      the key's columns
     * @param primary  whether the key is the primary key
     * @return whether a foreign key references it
     */
    boolean referencesKey(final String database, final Schema schema, final Table table, final List<String> key,
                          final boolean primary) {
        return sweepKey(database, schema, table, key, primary, null, false);
    }

    /**
     * Drops the foreign keys other tables hold on one key of a table; the foreign keys on its other keys stay
     * (live-verified).
     */
    void releaseKey(final String database, final Schema schema, final Table table, final List<String> key,
                    final boolean primary) {
        sweepKey(database, schema, table, key, primary, null, true);
    }

    /**
     * Drops the foreign keys other tables hold on any key one column of a table belongs to, as DROP COLUMN takes the
     * key with the column (live-verified for a primary key).
     *
     * @param inPrimaryKey whether the column belongs to the primary key
     */
    void releaseColumn(final String database, final Schema schema, final Table table, final String column,
                       final boolean inPrimaryKey) {
        sweepKey(database, schema, table, null, inPrimaryKey, column, true);
    }

    /**
     * Walks the tables of the account for the foreign keys that reference one key of the target, or any key holding one
     * of its columns, a table's key on itself never one.
     *
     * @param key     the key's columns, or null to match by {@code column}
     * @param primary whether the key is the primary key (or the column belongs to it)
     * @param column  the column a matching key holds, or null to match by {@code key}
     * @param drop    whether the foreign keys found are dropped
     * @return whether any was found
     */
    private boolean sweepKey(final String database, final Schema schema, final Table table, final List<String> key,
                             final boolean primary, final String column, final boolean drop) {
        boolean found = false;
        for (final Database holder : catalog.getAllDatabases()) {
            for (final Schema holding : holder.getAllSchemas()) {
                final List<Table> children = new ArrayList<>(holding.getTables());
                children.addAll(holding.getShadowedTables());
                for (final Table child : children) {
                    for (final ForeignKeyColumns reference : referencing(child, holder.getName(), holding, database,
                            schema, table)) {
                        if (!reference.reaches(key, primary, column)) {
                            continue;
                        }
                        if (!drop) {
                            return true;
                        }
                        found = true;
                        child.dropForeignKeyColumns(reference.columns());
                    }
                }
            }
        }
        return found;
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
                    for (final ForeignKeyColumns reference : referencing(child, holder.getName(), holding, database,
                            schema, table)) {
                        found = true;
                        if (drop) {
                            child.dropForeignKeyColumns(reference.columns());
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

    /** A child's foreign keys that reference the target, in both shapes a key is kept in. */
    private static List<ForeignKeyColumns> referencing(final Table child, final String childDatabase,
                                                       final Schema childSchema, final String database,
                                                       final Schema schema, final Table table) {
        final List<ForeignKeyColumns> keys = new ArrayList<>();
        for (final TableColumn column : child.getColumns()) {
            if (column.getReferencedTable() != null && names(column.getReferencedTable(), child, childDatabase,
                    childSchema, database, schema, table)) {
                keys.add(new ForeignKeyColumns(Collections.singletonList(column.getName()),
                    column.getReferencedColumn() == null ? Collections.<String>emptyList()
                        : Collections.singletonList(column.getReferencedColumn())));
            }
        }
        for (final ForeignKeyConstraint key : child.getForeignKeys()) {
            if (names(key.getReferencedTable(), child, childDatabase, childSchema, database, schema, table)) {
                keys.add(new ForeignKeyColumns(key.getColumnNames(), key.getReferencedColumns()));
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
