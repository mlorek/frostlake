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

import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

/**
 * One foreign key bound to the parent table its recorded name reached: a table-level key, or a column's REFERENCES
 * (see {@link ForeignKeyParents}).
 */
final class BoundForeignKey {

    private final Table child;
    private final ForeignKeyConstraint key;
    private final TableColumn column;
    private final Table parent;

    /**
     * @param child  the table holding the key
     * @param key    the table-level key, or null for a column's
     * @param column the column holding the key, or null for a table-level one
     * @param parent the table the key's name reached
     */
    BoundForeignKey(final Table child, final ForeignKeyConstraint key, final TableColumn column, final Table parent) {
        this.child = child;
        this.key = key;
        this.column = column;
        this.parent = parent;
    }

    Table child() {
        return child;
    }

    Table parent() {
        return parent;
    }

    /** The parent's name as the key records it. */
    String recorded() {
        return key != null ? key.getReferencedTable() : column.getReferencedTable();
    }

    /** Records the parent under a new name. */
    void respell(final String name) {
        if (key != null) {
            child.replaceForeignKey(key, key.referencing(name));
        } else {
            column.setReferencedTable(name);
        }
    }
}
