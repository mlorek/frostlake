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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MERGE may carry multiple WHEN NOT MATCHED clauses — a source uses the FIRST whose condition holds (earlier
 * conditional INSERTs must not be dropped) — and the INSERT applies DEFAULT / AUTOINCREMENT to omitted columns.
 */
public class MergeNotMatchedTest extends BaseDatabaseTest {

    @Test
    public void firstMatchingNotMatchedClauseWins() {
        engine.execute("CREATE TABLE t (id INTEGER, tag VARCHAR)");
        engine.execute("CREATE TABLE s (id INTEGER)");
        engine.execute("INSERT INTO s VALUES (20), (5)");
        engine.execute("""
            MERGE INTO t USING s ON t.id = s.id
              WHEN NOT MATCHED AND s.id > 10 THEN INSERT (id, tag) VALUES (s.id, 'HI')
              WHEN NOT MATCHED THEN INSERT (id, tag) VALUES (s.id, 'LO')
            """);
        assertEquals("HI", engine.executeQuery("SELECT tag FROM t WHERE id = 20").getRows().get(0).getValue(0).toString());
        assertEquals("LO", engine.executeQuery("SELECT tag FROM t WHERE id = 5").getRows().get(0).getValue(0).toString());
    }

    @Test
    public void notMatchedInsertAppliesDefaultAndAutoincrement() {
        engine.execute("CREATE TABLE t (id INTEGER AUTOINCREMENT, v VARCHAR DEFAULT 'DEF', w VARCHAR)");
        engine.execute("CREATE TABLE s (sw VARCHAR)");
        engine.execute("INSERT INTO s VALUES ('src')");
        engine.execute("""
            MERGE INTO t USING s ON 1 = 0
              WHEN NOT MATCHED THEN INSERT (w) VALUES (s.sw)
            """);
        final ResultSet rs = engine.executeQuery("SELECT id, v, w FROM t");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());   // AUTOINCREMENT
        assertEquals("DEF", rs.getRows().get(0).getValue(1).toString());            // DEFAULT
        assertEquals("src", rs.getRows().get(0).getValue(2).toString());
    }
}
