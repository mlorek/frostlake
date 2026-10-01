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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The LATERAL derived tables the account cannot evaluate are refused while the statement compiles: a correlated body
 * under LIMIT, one reading a table function over the outer row, and a FROM-less one under a LEFT or FULL join with an
 * ON — unless the relation to its left holds one row and nothing outside reads the derived table. Their neighbours
 * are answered.
 */
public class LateralUnsupportedSubqueryTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "SQL compilation error:\nUnsupported subquery type cannot be evaluated";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2), (2, 3), (3, 4), (4, 5)");
        engine.execute("CREATE TABLE full1 (a INT, b INT)");
        engine.execute("INSERT INTO full1 VALUES (1, 2)");
        engine.execute("CREATE TABLE g (k INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (1, 11), (2, 20), (3, 30), (3, 31)");
        engine.execute("CREATE TABLE j (id INT, arr VARIANT)");
        engine.execute("INSERT INTO j SELECT 1, PARSE_JSON('[1,2,3]') UNION ALL SELECT 2, PARSE_JSON('[]')");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? ";" : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aFromLessBodyUnderAnOuterJoinWithAnOnIsRefused() {
        assertEquals(UNSUPPORTED, refusal(
            "SELECT f.a, l.z FROM full_t f LEFT JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 3 ORDER BY 1"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT f.a, l.z FROM full_t f FULL JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 3 ORDER BY 1"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT f.a, l.z FROM full_t f LEFT JOIN LATERAL (SELECT 1 AS z WHERE f.a > 2) l ON TRUE ORDER BY 1, 2"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT f.a FROM full_t f LEFT JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 3 ORDER BY 1"));
        // One row to its left, and nothing outside reading it: evaluated.
        assertEquals("1", rows("SELECT f.a FROM full1 f LEFT JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 0"));
        // No ON: evaluated.
        assertEquals("3|1;4|1", rows(
            "SELECT f.a, l.z FROM full_t f LEFT JOIN LATERAL (SELECT 1 AS z WHERE f.a > 2) l ORDER BY 1, 2"));
        assertEquals("1|2;2|3;3|4;4|5", rows("SELECT f.a, l.z FROM full_t f, LATERAL (SELECT f.a + 1 AS z) l ORDER BY 1"));
    }

    @Test
    public void aCorrelatedBodyUnderLimitIsRefused() {
        assertEquals(UNSUPPORTED, refusal("SELECT f.a, l.v FROM full_t f LEFT JOIN LATERAL "
            + "(SELECT v FROM g WHERE g.k = f.a ORDER BY v LIMIT 1) l ON l.v > 10 ORDER BY 1, 2"));
        assertEquals(UNSUPPORTED, refusal("SELECT f.a, l.v FROM full_t f, LATERAL "
            + "(SELECT v FROM g WHERE g.k = f.a ORDER BY v LIMIT 1) l ORDER BY 1, 2"));
        assertEquals(UNSUPPORTED, refusal("SELECT f.a, l.v FROM full_t f, LATERAL "
            + "(SELECT v FROM g WHERE g.k = f.a LIMIT 1) l ORDER BY 1, 2"));
        // Not correlated: evaluated.
        assertEquals("1|10;2|10;3|10;4|10",
            rows("SELECT f.a, l.v FROM full_t f, LATERAL (SELECT v FROM g ORDER BY v LIMIT 1) l ORDER BY 1, 2"));
        // A body reading a table of its own under an outer join with an ON: evaluated.
        assertEquals("1|11;2|20;3|30;3|31;4|null", rows("SELECT f.a, l.v FROM full_t f LEFT JOIN LATERAL "
            + "(SELECT v FROM g WHERE g.k = f.a) l ON l.v > 10 ORDER BY 1, 2"));
    }

    @Test
    public void aBodyOverATableFunctionOfTheOuterRowIsRefused() {
        assertEquals(UNSUPPORTED, refusal("SELECT j.id, x.value FROM j LEFT JOIN LATERAL "
            + "(SELECT value FROM TABLE(FLATTEN(input => j.arr))) x ON x.value > 1 ORDER BY 1, 2"));
        assertEquals(UNSUPPORTED, refusal("SELECT j.id, x.value FROM j LEFT JOIN LATERAL "
            + "(SELECT value FROM TABLE(FLATTEN(input => j.arr))) x ORDER BY 1, 2"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT j.id, x.c FROM j, LATERAL (SELECT COUNT(*) AS c FROM TABLE(FLATTEN(j.arr))) x ORDER BY 1"));
        assertEquals("1|1;1|2;1|3", rows("SELECT j.id, f.value FROM j, LATERAL FLATTEN(j.arr) f ORDER BY 1, 2"));
    }
}
