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

package dev.frostlake.executor.udf;

import dev.frostlake.storage.Row;

import java.util.List;

/**
 * What a table function's handler wrote for one partition of its input: the rows it wrote while it read each
 * input row, in the input's order, and the rows it wrote once the partition was through. A row holds one value
 * per declared column, already the column's type.
 */
public final class TablePartitionRows {

    private final List<List<Row>> written;
    private final List<Row> atEnd;

    /**
     * @param written the rows written for each input row, in the input's order
     * @param atEnd   the rows written once the partition was through
     */
    public TablePartitionRows(final List<List<Row>> written, final List<Row> atEnd) {
        this.written = written;
        this.atEnd = atEnd;
    }

    /**
     * @param input an input row's place in the partition, from 0
     * @return the rows the handler wrote while it read that row
     */
    public List<Row> writtenFor(final int input) {
        return written.get(input);
    }

    /**
     * @return the rows the handler wrote once the partition was through
     */
    public List<Row> atEnd() {
        return atEnd;
    }
}
