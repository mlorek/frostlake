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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MAX_BY(value, sort_key) / MIN_BY(value, sort_key) — return the value from the row with the max / min key,
 * ignoring NULL keys, with the latest row winning a tie.
 */
public class MaxByMinByTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE s (g INTEGER, name VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO s VALUES (1,'a',10),(1,'b',30),(1,'c',20)");
        engine.execute("INSERT INTO s VALUES (2,'x',5),(2,'y',NULL)");   // NULL key is ignored
    }

    private Object cell(final ResultSet rs, final int row, final int col) {
        return rs.getRows().get(row).getValue(col);
    }

    @Test
    public void picksValueOfExtremeKeyPerGroup() {
        final ResultSet rs = engine.executeQuery(
            "SELECT g, MAX_BY(name, score), MIN_BY(name, score) FROM s GROUP BY g ORDER BY g");
        assertEquals(2, rs.getRowCount());
        // g = 1: max score 30 → 'b'; min score 10 → 'a'
        assertEquals("b", cell(rs, 0, 1).toString());
        assertEquals("a", cell(rs, 0, 2).toString());
        // g = 2: only 'x' has a non-NULL key, so it is both max and min
        assertEquals("x", cell(rs, 1, 1).toString());
        assertEquals("x", cell(rs, 1, 2).toString());
    }

    @Test
    public void tieIsResolvedByLatestRow() {
        engine.execute("CREATE TABLE tie (name VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO tie VALUES ('first',10),('second',10)");
        assertEquals("second",
            engine.executeQuery("SELECT MAX_BY(name, score) FROM tie").getRows().get(0).getValue(0).toString());
    }

    @Test
    public void allNullKeysYieldNull() {
        engine.execute("CREATE TABLE nk (name VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO nk VALUES ('a', NULL),('b', NULL)");
        assertNull(engine.executeQuery("SELECT MAX_BY(name, score) FROM nk").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT MIN_BY(name, score) FROM nk").getRows().get(0).getValue(0));
    }
}
