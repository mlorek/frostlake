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

package dev.frostlake.streaming;

import dev.frostlake.executor.streaming.FilterRowStream;
import dev.frostlake.executor.streaming.LimitRowStream;
import dev.frostlake.executor.streaming.ListRowStream;
import dev.frostlake.executor.streaming.ProjectRowStream;
import dev.frostlake.executor.streaming.RowMapper;
import dev.frostlake.executor.streaming.RowPredicate;
import dev.frostlake.executor.streaming.RowStream;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pull-based (volcano) streaming operators: pipeline composition and — crucially — that
 * a LIMIT stops pulling from its source once satisfied (no full materialization of the upstream).
 */
public class RowStreamTest {

    private static List<Row> intRows(final int count) {
        final List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Row(Integer.valueOf(i)));
        }
        return rows;
    }

    private static List<Object> drain(final RowStream stream) {
        final List<Object> out = new ArrayList<>();
        Row row = stream.next();
        while (row != null) {
            out.add(row.getValue(0));
            row = stream.next();
        }
        stream.close();
        return out;
    }

    @Test
    public void testFilterProjectLimitPipeline() {
        // rows 0..99 -> keep evens -> double them -> take 5  => 0,4,8,12,16
        final RowStream pipeline = new LimitRowStream(
            new ProjectRowStream(
                new FilterRowStream(new ListRowStream(intRows(100)), new RowPredicate() {
                    @Override
                    public boolean test(final Row row) {
                        return ((Integer) row.getValue(0)) % 2 == 0;
                    }
                }),
                new RowMapper() {
                    @Override
                    public Row map(final Row row) {
                        return new Row(((Integer) row.getValue(0)) * 2);
                    }
                }),
            5, 0);

        assertEquals(List.of(0, 4, 8, 12, 16), drain(pipeline));
    }

    @Test
    public void testOffsetAndLimit() {
        // rows 0..19, OFFSET 3 LIMIT 4 => 3,4,5,6
        final RowStream pipeline = new LimitRowStream(new ListRowStream(intRows(20)), 4, 3);
        assertEquals(List.of(3, 4, 5, 6), drain(pipeline));
    }

    @Test
    public void testLimitShortCircuitsSource() {
        // A counting source over 1000 rows; a LIMIT 3 on top must only pull ~3 rows, not all 1000.
        final List<Row> rows = intRows(1000);
        final int[] pulled = { 0 };
        final RowStream counting = new RowStream() {
            private int index;

            @Override
            public Row next() {
                if (index >= rows.size()) {
                    return null;
                }
                pulled[0]++;
                return rows.get(index++);
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        final List<Object> out = drain(new LimitRowStream(counting, 3, 0));

        assertEquals(3, out.size());
        assertTrue(pulled[0] <= 4, "LIMIT did not short-circuit the source; it pulled " + pulled[0] + " rows");
    }
}
