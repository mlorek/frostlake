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
package dev.frostlake.executor;

import dev.frostlake.storage.Row;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each row of a statement's target stands, asked as the rows its WHERE kept arrive. A row is known by
 * identity and never by its values: two stored rows of the same values are two rows, and an UPDATE or a DELETE
 * writes each at its own position. A row that stands at several positions answers each of them once, in order.
 */
final class RowPositions {

    private final Map<Row, Integer> first = new IdentityHashMap<>();

    /** The further positions of a row that stands at more than one. */
    private final Map<Row, ArrayDeque<Integer>> further = new IdentityHashMap<>();

    /**
     * Reads where the rows stand.
     *
     * @param rows the target's rows, in the order their positions count
     */
    RowPositions(final List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            if (!first.containsKey(row)) {
                first.put(row, Integer.valueOf(i));
                continue;
            }
            ArrayDeque<Integer> held = further.get(row);
            if (held == null) {
                held = new ArrayDeque<>();
                further.put(row, held);
            }
            held.add(Integer.valueOf(i));
        }
    }

    /**
     * The position of a row that reached the statement's write, each position answered once.
     *
     * @param row a row of the target
     * @return its next position not yet answered, or null when it has none left
     */
    Integer take(final Row row) {
        final Integer position = first.remove(row);
        if (position != null) {
            return position;
        }
        final ArrayDeque<Integer> held = further.get(row);
        return held == null ? null : held.poll();
    }
}
