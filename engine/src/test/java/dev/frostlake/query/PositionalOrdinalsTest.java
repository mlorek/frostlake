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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Positional (ordinal) references: {@code GROUP BY 1} and {@code ORDER BY 2} address select-list
 * items by position, including expression items, and mix freely with named references.
 */
public class PositionalOrdinalsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (r VARCHAR, n INTEGER)");
        engine.execute("INSERT INTO t VALUES ('a', 1), ('a', 2), ('b', 7), ('c', 4)");
    }

    private static long asLong(final Object value) {
        return ((Number) value).longValue();
    }

    @Test
    public void groupByOrdinalAggregates() {
        final ResultSet rs = engine.executeQuery(
            "SELECT r, SUM(n) FROM t GROUP BY 1 ORDER BY 1");
        assertEquals(3, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals(3L, asLong(rs.getRows().get(0).getValue(1)));
        assertEquals(7L, asLong(rs.getRows().get(1).getValue(1)));
        assertEquals(4L, asLong(rs.getRows().get(2).getValue(1)));
    }

    @Test
    public void orderByOrdinalDescOnAggregateColumn() {
        final ResultSet rs = engine.executeQuery(
            "SELECT r, SUM(n) FROM t GROUP BY 1 ORDER BY 2 DESC, 1");
        assertEquals("b", rs.getRows().get(0).getValue(0)); // 7
        assertEquals("c", rs.getRows().get(1).getValue(0)); // 4
        assertEquals("a", rs.getRows().get(2).getValue(0)); // 3
    }

    @Test
    public void ordinalTargetsAnExpressionItemAndMixesWithNames() {
        final ResultSet rs = engine.executeQuery(
            "SELECT UPPER(r), COUNT(*) AS c FROM t GROUP BY 1 ORDER BY c DESC, 1");
        assertEquals(3, rs.getRowCount());
        assertEquals("A", rs.getRows().get(0).getValue(0));
        assertEquals(2L, asLong(rs.getRows().get(0).getValue(1)));
        assertEquals("B", rs.getRows().get(1).getValue(0));
        assertEquals("C", rs.getRows().get(2).getValue(0));
    }

    @Test
    public void ordinalBeyondTheSelectListIsRefused() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT r, n FROM t ORDER BY 5");
            }
        });
    }
}
