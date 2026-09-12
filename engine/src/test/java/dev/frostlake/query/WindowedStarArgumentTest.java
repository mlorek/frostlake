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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A windowed bare {@code *} argument expands to the in-scope column list exactly as the plain
 * aggregate's does, so a variadic aggregate answers the same value from both spellings — live-verified:
 * {@code HASH_AGG(*) OVER ()} and {@code HASH_AGG(a, b, c) OVER ()} agree, and both agree with the
 * plain {@code HASH_AGG(*)} over the same rows. The star used to reach the window stage as no argument
 * at all, so the windowed call hashed an empty tuple per row and disagreed with its written-out twin.
 *
 * <p>The hash VALUE itself is not pinned: it is Snowflake's own function and Frostlake's differs; the
 * two spellings agreeing within one engine is the measured fact.
 */
public class WindowedStarArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sa (a INT, b INT, c INT)");
        engine.execute("INSERT INTO sa VALUES (1, 2, 3), (4, 5, 6)");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aWindowedStarExpandsToTheColumnList() {
        final String written = cell("SELECT HASH_AGG(a, b, c) OVER () FROM sa");
        assertNotNull(written);
        assertEquals(written, cell("SELECT HASH_AGG(*) OVER () FROM sa"));
        assertEquals(written, cell("SELECT HASH_AGG(*) FROM sa"));
        assertEquals(written, cell("SELECT HASH_AGG(a, b, c) FROM sa"));
        // The expansion follows the relation's own column order, whatever the select list says.
        assertEquals(written, cell("SELECT HASH_AGG(*) OVER () FROM (SELECT * FROM sa)"));
        assertEquals("true", cell("SELECT HASH_AGG(*) OVER () = HASH_AGG(a, b, c) OVER () FROM sa"));
        // A partitioned star expands the same way within each partition.
        assertEquals(cell("SELECT HASH_AGG(a, b, c) OVER (PARTITION BY a) FROM sa ORDER BY a"),
            cell("SELECT HASH_AGG(*) OVER (PARTITION BY a) FROM sa ORDER BY a"));
    }
}
