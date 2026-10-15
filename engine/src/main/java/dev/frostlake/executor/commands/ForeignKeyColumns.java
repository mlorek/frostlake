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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** One foreign key a child holds: its own columns, and the parent's columns it names — none naming the primary key. */
final class ForeignKeyColumns {

    private final List<String> columns;
    private final List<String> referenced;

    ForeignKeyColumns(final List<String> columns, final List<String> referenced) {
        this.columns = new ArrayList<String>(columns);
        this.referenced = referenced == null ? new ArrayList<String>() : new ArrayList<String>(referenced);
    }

    /** The child's columns, as the key is dropped by them. */
    List<String> columns() {
        return columns;
    }

    /**
     * Whether this foreign key references a key of the parent: the key whose columns it names, or the primary key when
     * it names none; matched by column instead, any key that holds the column.
     *
     * @param key     the key's columns, or null when matching by column
     * @param primary whether the key is the primary key, or the column belongs to it
     * @param column  the column, or null when matching by key
     * @return whether it references that key
     */
    boolean reaches(final List<String> key, final boolean primary, final String column) {
        if (referenced.isEmpty()) {
            return primary;
        }
        if (column != null) {
            return referenced.contains(column);
        }
        return new HashSet<String>(referenced).equals(new HashSet<String>(key));
    }
}
