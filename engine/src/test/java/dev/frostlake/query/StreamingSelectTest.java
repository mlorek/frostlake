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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Simple single-table {@code SELECT ... LIMIT} queries take the pull-based streaming path
 * (scan -> WHERE -> LIMIT, short-circuiting once LIMIT is met). These assertions verify it produces
 * exactly the same results as the materializing path across the streamable shapes — WHERE+LIMIT,
 * LIMIT+OFFSET, LIMIT 0, LIMIT beyond the row count, projection over a limited result — and that
 * non-streamable shapes (ORDER BY + LIMIT) still fall back and stay correct. Rows are produced in
 * scan (insertion) order, the same as the materializing path.
 */
public class StreamingSelectTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nums (id INTEGER)");
        for (int i = 1; i <= 10; i++) {
            engine.execute("INSERT INTO nums VALUES (" + i + ")");
        }
    }

    private static List<Long> ids(final ResultSet rs, final String column) {
        final List<Long> out = new ArrayList<>();
        rs.reset();
        while (rs.next()) {
            out.add(((Number) rs.getValue(column)).longValue());
        }
        return out;
    }

    @Test
    public void testWhereLimitTakesFirstMatches() {
        // First 2 rows (scan order) with id >= 3 -> 3, 4.
        final ResultSet result = engine.executeQuery("SELECT id FROM nums WHERE id >= 3 LIMIT 2");
        assertEquals(List.of(3L, 4L), ids(result, "id"));
    }

    @Test
    public void testLimitOffset() {
        // Skip 2, take 3 -> 3, 4, 5.
        final ResultSet result = engine.executeQuery("SELECT id FROM nums LIMIT 3 OFFSET 2");
        assertEquals(List.of(3L, 4L, 5L), ids(result, "id"));
    }

    @Test
    public void testLimitZero() {
        final ResultSet result = engine.executeQuery("SELECT id FROM nums LIMIT 0");
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testLimitBeyondMatches() {
        // Only 2 rows match (9, 10); LIMIT 100 yields both.
        final ResultSet result = engine.executeQuery("SELECT id FROM nums WHERE id > 8 LIMIT 100");
        assertEquals(List.of(9L, 10L), ids(result, "id"));
    }

    @Test
    public void testProjectionAppliedToLimitedRows() {
        // Projection is applied after the streamed WHERE+LIMIT: first 2 rows with id <= 5 are 1, 2.
        final ResultSet result = engine.executeQuery("SELECT id + 100 AS x FROM nums WHERE id <= 5 LIMIT 2");
        assertEquals(List.of(101L, 102L), ids(result, "x"));
    }

    @Test
    public void testOrderByLimitFallsBackCorrectly() {
        // ORDER BY disables streaming (needs a full sort); the top-2 descending must still be correct.
        final ResultSet result = engine.executeQuery("SELECT id FROM nums ORDER BY id DESC LIMIT 2");
        assertEquals(List.of(10L, 9L), ids(result, "id"));
    }
}
