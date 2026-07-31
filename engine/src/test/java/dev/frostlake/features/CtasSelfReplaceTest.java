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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CREATE OR REPLACE TABLE ... AS SELECT with the SAME table as source: Snowflake's replace is an
 * atomic swap, so the source SELECT reads the OLD rows — and a failing source leaves the existing
 * table untouched. (The old drop-first order destroyed the source before the SELECT ran.)
 */
public class CtasSelfReplaceTest extends BaseDatabaseTest {

    @Test
    public void selfReferentialReplaceReadsTheOldRows() {
        engine.execute("CREATE TABLE roster (id INTEGER, label VARCHAR)");
        engine.execute("INSERT INTO roster VALUES (1, 'one'), (2, 'two')");

        // The collation-migration idiom: rebuild the table from itself with new column properties.
        engine.execute("""
            CREATE OR REPLACE TABLE roster (
                id INTEGER, label VARCHAR COLLATE 'en-ci'
            ) AS SELECT id, label FROM roster
            """);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*), MAX(label) FROM roster");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals("two", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void failingCtasSourceLeavesTheOldTableIntact() {
        engine.execute("CREATE TABLE keepme (id INTEGER)");
        engine.execute("INSERT INTO keepme VALUES (42)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE TABLE keepme AS SELECT id FROM no_such_source");
            }
        });

        final ResultSet rs = engine.executeQuery("SELECT id FROM keepme");
        assertEquals(42, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
