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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When a target row joins to more than one source row, MERGE must treat every matching source as matched —
 * it must not let the surplus source rows fall through to WHEN NOT MATCHED and get inserted as duplicates
 * (Snowflake ERROR_ON_NONDETERMINISTIC_MERGE = FALSE: one update, no phantom insert).
 */
public class MergeMultiMatchTest extends BaseDatabaseTest {

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void multiMatchedSourceDoesNotInsertADuplicate() {
        engine.execute("CREATE TABLE tgt (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO tgt VALUES (1, 'x')");
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (1, 'b')");

        engine.execute("""
            MERGE INTO tgt USING src ON tgt.id = src.id
              WHEN MATCHED THEN UPDATE SET v = src.v
              WHEN NOT MATCHED THEN INSERT (id, v) VALUES (src.id, src.v)
            """);

        // The target keeps its single row (updated) — the second matching source is NOT inserted.
        assertEquals(1L, count("tgt"));
    }
}
