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

package dev.frostlake.metastore.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One refresh of a dynamic table as a read at a past point sees it: where it falls in the order table snapshots are
 * taken in, when it ran, and the rows it left (see {@link DynamicTable#refreshAt}).
 */
public final class DynamicTableRefresh {

    private final long sequence;
    private final long millis;
    private final List<List<Object>> rows;

    /**
     * @param sequence where the refresh falls in the order table snapshots are taken in
     * @param millis   when it ran, in epoch milliseconds
     * @param rows     the rows it left, each as its values
     */
    public DynamicTableRefresh(final long sequence, final long millis, final List<List<Object>> rows) {
        this.sequence = sequence;
        this.millis = millis;
        this.rows = new ArrayList<>();
        for (final List<Object> row : rows) {
            this.rows.add(new ArrayList<>(row));
        }
    }

    /** Where the refresh falls in the order table snapshots are taken in. */
    public long getSequence() {
        return sequence;
    }

    /** When the refresh ran, in epoch milliseconds. */
    public long getMillis() {
        return millis;
    }

    /** The rows the refresh left, each as its values. */
    public List<List<Object>> getRows() {
        final List<List<Object>> copy = new ArrayList<>();
        for (final List<Object> row : rows) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }
}
