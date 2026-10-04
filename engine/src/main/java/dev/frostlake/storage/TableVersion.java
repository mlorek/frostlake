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

package dev.frostlake.storage;

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableStructure;

import java.util.List;

/**
 * A table as it stood at a time-travel point: the shape it had then and its rows in that shape, which is what a
 * CLONE ... AT | BEFORE copies.
 */
public final class TableVersion {

    private final TableStructure structure;
    private final List<Row> rows;

    /**
     * @param structure the shape, or null when the table keeps no structure history and the rows follow its current
     *                  columns
     * @param rows      the rows, in that shape
     */
    public TableVersion(final TableStructure structure, final List<Row> rows) {
        this.structure = structure;
        this.rows = rows;
    }

    /** The shape the table had at the point, or null when its rows follow its current columns. */
    public TableStructure getStructure() {
        return structure;
    }

    public List<Row> getRows() {
        return rows;
    }

    /**
     * Whether the table had another shape at the point than it has now, so a copy of its current definition must be
     * given the shape of then ({@link Table#restructureAsOf}).
     *
     * @param table the table now
     * @return whether its columns changed since the point
     */
    public boolean reshapes(final Table table) {
        return structure != null && !structure.describes(table.columnsView());
    }
}
