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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a foreign key points at, judged as the account judges it when the key is declared (live-verified):
 *
 * <pre>
 *   REFERENCES nosuch (id)            Table 'DB.S.NOSUCH' does not exist or not authorized.   also for a key
 *                                     the account leaves out
 *   REFERENCES p (a, b) from one      Number of columns in foreign key specification does not match number of
 *   column                            columns in foreign key reference
 *   REFERENCES p (x), x no key        primary or unique key does not exist for the columns referenced by the
 *                                     foreign key   the columns have to be a key of the parent in the key's own
 *                                     order; a view has none
 *   REFERENCES p, no key that fits    could not find matching constraints for the foreign key
 * </pre>
 *
 * A key that names no columns of its parent points at the parent's primary key when that has as many columns as
 * the foreign key, else at its first unique key that has. A table may point at itself. The key keeps its parent
 * by the parent's full name, DATABASE.SCHEMA.TABLE, whatever its statement wrote.
 */
final class ForeignKeyTargets {

    private static final String NO_KEY = "primary or unique key does not exist for the columns referenced by the"
        + " foreign key";

    private ForeignKeyTargets() {
    }

    /**
     * The parent a foreign key names has to exist, whether or not the key is kept.
     *
     * @param catalog    the catalog the name resolves in
     * @param parentName the parent as written
     */
    static void requireParent(final Catalog catalog, final String parentName) {
        if (viewNamed(catalog, parentName)) {
            return;
        }
        catalog.resolveTable(QualifiedName.parse(parentName));
    }

    /**
     * Settles every foreign key a new table declares: each one's target is judged, a key written without its
     * parent's columns is given them, and the parent is kept by its full name.
     *
     * @param catalog     the catalog the parents resolve in
     * @param childSchema the schema the table is created in
     * @param child       the table, its own keys already in place
     */
    static void settle(final Catalog catalog, final Schema childSchema, final Table child) {
        for (final TableColumn column : child.getColumns()) {
            if (column.getReferencedTable() == null) {
                continue;
            }
            final List<String> written = column.getReferencedColumn() == null ? new ArrayList<String>()
                : Collections.singletonList(column.getReferencedColumn());
            column.setReferencedColumn(referenced(catalog, child, column.getReferencedTable(),
                Collections.singletonList(column.getName()), written).get(0));
            column.setReferencedTable(fullName(catalog, childSchema, child, column.getReferencedTable()));
        }
        for (final ForeignKeyConstraint key : child.getForeignKeys()) {
            child.replaceForeignKey(key, settled(catalog, childSchema, child, key));
        }
    }

    /**
     * One foreign key with its target judged, its parent's columns named and its parent kept by its full name.
     *
     * @param catalog     the catalog the parent resolves in
     * @param childSchema the schema the key's table lives in
     * @param child       the table the key belongs to
     * @param key         the key as written
     * @return the key as the table keeps it
     */
    static ForeignKeyConstraint settled(final Catalog catalog, final Schema childSchema, final Table child,
                                        final ForeignKeyConstraint key) {
        final List<String> referenced = referenced(catalog, child, key.getReferencedTable(), key.getColumnNames(),
            key.getReferencedColumns());
        return new ForeignKeyConstraint(key.getConstraintName(), key.getColumnNames(),
            fullName(catalog, childSchema, child, key.getReferencedTable()), referenced, key.getOnDelete(),
            key.getOnUpdate(), key.getRely());
    }

    /** The parent's full name: the child's own when the key points back at it. */
    private static String fullName(final Catalog catalog, final Schema childSchema, final Table child,
                                   final String parentName) {
        final QualifiedName name = QualifiedName.parse(parentName);
        if (name.size() == 1 && name.last().equalsIgnoreCase(child.getName())) {
            return childSchema.qualifiedName(child.getName());
        }
        if (viewNamed(catalog, parentName)) {
            return parentName;
        }
        return catalog.resolveOwningSchema(parentName).qualifiedName(catalog.resolveTable(name).getName());
    }

    /** The parent's columns a key points at: the written ones when they are a key of the parent, else its own. */
    private static List<String> referenced(final Catalog catalog, final Table child, final String parentName,
                                           final List<String> columns, final List<String> written) {
        final Table parent = parentOf(catalog, child, parentName);
        final List<List<String>> keys = parent == null ? new ArrayList<List<String>>() : keysOf(parent);
        if (written.isEmpty()) {
            for (final List<String> key : keys) {
                if (key.size() == columns.size()) {
                    return key;
                }
            }
            throw new RuntimeException(SqlCompilationError.of("could not find matching constraints for the foreign"
                + " key"));
        }
        if (written.size() != columns.size()) {
            throw new RuntimeException(SqlCompilationError.of("Number of columns in foreign key specification does"
                + " not match number of columns in foreign key reference\n"));
        }
        for (final List<String> key : keys) {
            if (sameColumns(key, written)) {
                return key;
            }
        }
        throw new RuntimeException(SqlCompilationError.of(NO_KEY));
    }

    /** The parent table, the child itself when the key points back at it, or null for a view. */
    private static Table parentOf(final Catalog catalog, final Table child, final String parentName) {
        final QualifiedName name = QualifiedName.parse(parentName);
        if (name.size() == 1 && name.last().equalsIgnoreCase(child.getName())) {
            return child;
        }
        if (viewNamed(catalog, parentName)) {
            return null;
        }
        return catalog.resolveTable(name);
    }

    private static boolean viewNamed(final Catalog catalog, final String name) {
        try {
            return catalog.resolveView(name) != null;
        } catch (final RuntimeException notAView) {
            return false;
        }
    }

    /** A table's keys, the primary key first, then its unique keys as declared. */
    private static List<List<String>> keysOf(final Table table) {
        final List<List<String>> keys = new ArrayList<>();
        if (!table.getPrimaryKeys().isEmpty()) {
            keys.add(new ArrayList<>(table.getPrimaryKeys()));
        }
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            keys.add(unique.getColumnNames());
        }
        return keys;
    }

    private static boolean sameColumns(final List<String> key, final List<String> written) {
        if (key.size() != written.size()) {
            return false;
        }
        for (int i = 0; i < key.size(); i++) {
            if (!key.get(i).equalsIgnoreCase(written.get(i))) {
                return false;
            }
        }
        return true;
    }
}
