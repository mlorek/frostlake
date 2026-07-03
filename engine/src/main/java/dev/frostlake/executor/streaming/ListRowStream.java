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

package dev.frostlake.executor.streaming;

import dev.frostlake.storage.Row;

import java.util.List;

/** Leaf source that streams rows from an existing list lazily (by index, without copying it). */
public class ListRowStream implements RowStream {

    private final List<Row> rows;
    private int index;

    public ListRowStream(final List<Row> rows) {
        this.rows = rows;
    }

    @Override
    public Row next() {
        if (index >= rows.size()) {
            return null;
        }
        return rows.get(index++);
    }

    @Override
    public void close() {
        // nothing to release
    }
}
