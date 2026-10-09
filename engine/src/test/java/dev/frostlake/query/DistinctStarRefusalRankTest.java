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
 * A bare star under DISTINCT as a function's whole argument is refused for the statement's text alone: ahead of
 * every name, in whichever clause it stands, windowed or not.
 */
public class DistinctStarRefusalRankTest extends BaseDatabaseTest {

    private static final String NO_STAR = "Unsupported feature 'TOK_STAR'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ3 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ3 VALUES (5, TRUE), (7, FALSE), (5, TRUE)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** Each row's cells joined by bars, rows by semicolons. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int r = 0; r < rs.getRowCount(); r++) {
            out.append(r > 0 ? " ; " : "");
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c > 0 ? "|" : "").append(rs.getRows().get(r).getValue(c));
            }
        }
        return out.toString();
    }

    @Test
    public void theRefusalComesAheadOfEveryName() {
        assertEquals(NO_STAR, refusalOf("SELECT COUNT(DISTINCT *), nosuch FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT nosuch, COUNT(DISTINCT *) FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT COUNT(DISTINCT *) FROM nosuch_t"));
    }

    @Test
    public void itIsTheSameInEveryClauseAndUnderOver() {
        assertEquals(NO_STAR, refusalOf("SELECT COUNT(DISTINCT *) FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT HASH_AGG(DISTINCT *) FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT COUNT(DISTINCT *) OVER () FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT 1 FROM FZ3 HAVING COUNT(DISTINCT *) > 0"));
        assertEquals(NO_STAR, refusalOf("SELECT 1 FROM FZ3 WHERE COUNT(DISTINCT *) > 0"));
        assertEquals(NO_STAR, refusalOf("SELECT b FROM FZ3 GROUP BY b ORDER BY COUNT(DISTINCT *)"));
        assertEquals(NO_STAR, refusalOf("SELECT COUNT_IF(DISTINCT *) FROM FZ3"));
        assertEquals(NO_STAR, refusalOf("SELECT (SELECT COUNT(DISTINCT *) FROM FZ3)"));
        assertEquals(NO_STAR, refusalOf("CREATE OR REPLACE TABLE dsc AS SELECT COUNT(DISTINCT *) AS c FROM FZ3"));
    }

    @Test
    public void aStarThatNamesItsRelationOrIsFilteredIsCounted() {
        assertEquals("2", rows("SELECT COUNT(DISTINCT fz3.*) FROM FZ3"));
        assertEquals("2", rows("SELECT COUNT(DISTINCT * EXCLUDE (b)) FROM FZ3"));
        assertEquals("2", rows("SELECT COUNT(DISTINCT * ILIKE 'b') FROM FZ3"));
        assertEquals("3", rows("SELECT COUNT(*) FROM FZ3"));
    }
}
