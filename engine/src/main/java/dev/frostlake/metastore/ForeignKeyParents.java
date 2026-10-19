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
import java.util.List;

/**
 * The parent tables foreign keys name, carried through a rename. A key records its parent by name, read from the
 * child's schema - a table-level key in full, a column's REFERENCES as written - and the key stays on the table it was
 * declared against: a parent table renamed, moved or swapped, or a schema or database renamed or swapped, takes its
 * keys' references along, as it takes its streams and its grants. Each key is bound to the table its name reaches
 * before the change and re-spelled after it where the name no longer reaches that table, in as many parts as it was
 * written with or as many more as reaching it now takes. A key whose parent is gone keeps its name.
 */
public final class ForeignKeyParents {

    private final List<BoundForeignKey> bound = new ArrayList<>();

    private ForeignKeyParents() {
    }

    /**
     * Binds every foreign key of the catalog to the table its recorded name reaches now: the first step of a rename,
     * a move or a swap.
     *
     * @param catalog the catalog
     * @return the bindings, for {@link #respell}
     */
    public static ForeignKeyParents bind(final Catalog catalog) {
        final ForeignKeyParents parents = new ForeignKeyParents();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table child : schema.getTables()) {
                    for (final ForeignKeyConstraint key : child.getForeignKeys()) {
                        parents.add(new BoundForeignKey(child, key, null,
                            StreamSources.named(catalog, schema, key.getReferencedTable())));
                    }
                    for (final TableColumn column : child.getColumns()) {
                        if (column.getReferencedTable() != null) {
                            parents.add(new BoundForeignKey(child, null, column,
                                StreamSources.named(catalog, schema, column.getReferencedTable())));
                        }
                    }
                }
            }
        }
        return parents;
    }

    /** Keeps a key whose name reaches a table; one whose parent is already gone has nothing to follow. */
    private void add(final BoundForeignKey key) {
        if (key.parent() != null) {
            bound.add(key);
        }
    }

    /**
     * Re-spells each bound key whose recorded name no longer reaches its parent: the last step of a rename, a move or
     * a swap.
     *
     * @param catalog the catalog
     */
    public void respell(final Catalog catalog) {
        for (final BoundForeignKey key : bound) {
            final Schema home = StreamSources.holderOf(catalog, key.child());
            final Schema holder = StreamSources.holderOf(catalog, key.parent());
            if (home == null || holder == null || StreamSources.named(catalog, home, key.recorded()) == key.parent()) {
                continue;
            }
            key.respell(spelled(home, holder, key.parent().getName(), QualifiedName.parse(key.recorded()).size()));
        }
    }

    /** The parent's name read from the child's schema, in at least {@code written} parts. */
    private static String spelled(final Schema home, final Schema holder, final String name, final int written) {
        final String shortest = StreamSources.spelled(home, holder, name);
        final int parts = QualifiedName.parse(shortest).size();
        if (parts >= written) {
            return shortest;
        }
        return written == 2 ? QualifiedName.join(holder.getName(), name) : holder.qualifiedName(name);
    }
}
