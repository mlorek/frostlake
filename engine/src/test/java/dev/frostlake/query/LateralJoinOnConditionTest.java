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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A LATERAL join's ON condition decides which lateral rows pair with each left row. It used to be read for
 * the join's TYPE alone, so every lateral row paired whatever the condition said.
 *
 * <p>How live applies it is not the textbook outer join, and every shape below is live-verified over
 * {@code lat_f (a, b)} holding (1,2) (2,3) (3,4) (4,5) and a lateral {@code (SELECT v FROM lat_g WHERE k = f.a)}
 * that yields 10 and 11 for a = 1, 20 for a = 2, 30 and 31 for a = 3, and nothing for a = 4:
 *
 * <ul>
 *   <li>a LEFT join null-extends a left row the condition leaves unpaired — but only as far as the part of
 *       the condition that reads the lateral side alone (or no column); a conjunct that reads a column of
 *       the left side is judged afterwards over the paired rows, null-extended ones included, and so can
 *       drop a left row outright;</li>
 *   <li>a LEFT join written WITHOUT ON, and a FULL or RIGHT one, answer as the inner join.</li>
 * </ul>
 */
public class LateralJoinOnConditionTest extends BaseDatabaseTest {

    private static final String LATERAL = " FROM lat_f f LEFT JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE lat_f (a INT, b INT)");
        engine.execute("INSERT INTO lat_f VALUES (1, 2), (2, 3), (3, 4), (4, 5)");
        engine.execute("CREATE OR REPLACE TABLE lat_g (k INT, v INT)");
        engine.execute("INSERT INTO lat_g VALUES (1, 10), (1, 11), (2, 20), (3, 30), (3, 31)");
    }

    /** The rows a query answers, each as its cells joined by ':', in the query's own order. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            for (int i = 0; i < rs.getColumnCount(); i++) {
                if (i > 0) {
                    out.append(':');
                }
                final Object value = rs.getValue(i);
                out.append(value == null ? "NULL" : String.valueOf(value));
            }
        }
        return out.toString();
    }

    /** The message a statement is refused with. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                rows(sql);
            }
        }, sql);
        return String.valueOf(refused.getMessage());
    }

    /** The inner lateral join keeps only the pairs its condition accepts. */
    @Test
    public void anInnerJoinKeepsOnlyThePairsItsConditionAccepts() {
        engine.execute("CREATE OR REPLACE TABLE lat_one (a INT, b INT)");
        engine.execute("INSERT INTO lat_one VALUES (1, 2)");
        assertEquals("", rows("SELECT f.a FROM lat_one f JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 0"),
            "the condition is false for the only pair");
        assertEquals("1", rows("SELECT f.a FROM lat_one f LEFT JOIN LATERAL (SELECT f.a + 1 AS z) l ON l.z = 0"),
            "and the LEFT join null-extends the row instead");
    }

    /** A condition on the lateral side pairs the rows it accepts, and null-extends a left row it pairs with none. */
    @Test
    public void aLeftJoinPairsOnlyTheLateralRowsItsConditionAccepts() {
        assertEquals("1:11 2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON l.v > 10 ORDER BY 1, 2"));
        assertEquals("1:11 2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON v > 10 ORDER BY 1, 2"),
            "an unqualified name of the lateral side reads the same");
        assertEquals("1:11:1 2:20:2 3:NULL:NULL 4:NULL:NULL", rows("""
            SELECT f.a, l.v, l.k FROM lat_f f
              LEFT JOIN LATERAL (SELECT k, v FROM lat_g WHERE lat_g.k = f.a) l ON l.v > 10 AND l.k <> 3
             ORDER BY 1, 2
            """), "a = 3 has lateral rows, but none the condition accepts");
    }

    /** A condition that accepts no lateral row null-extends every left row; one that accepts all keeps them all. */
    @Test
    public void aLeftJoinNullExtendsEveryRowItsConditionLeavesUnpaired() {
        assertEquals("1:NULL 2:NULL 3:NULL 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON FALSE ORDER BY 1, 2"));
        assertEquals("1:NULL 2:NULL 3:NULL 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON NULL ORDER BY 1, 2"));
        assertEquals("1:NULL 2:NULL 3:NULL 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON l.v IS NULL ORDER BY 1, 2"));
        assertEquals("1:10 1:11 2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON TRUE ORDER BY 1, 2"));
        assertEquals("1:10 1:11 2:20 3:30 3:31 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON COALESCE(l.v, 0) >= 0 ORDER BY 1, 2"));
    }

    /**
     * A conjunct that reads the left side is judged after pairing, over the null-extended rows too: it drops a
     * left row where the textbook outer join would null-extend it.
     */
    @Test
    public void aConjunctReadingTheLeftSideFiltersAfterPairing() {
        assertEquals("2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON f.a > 1 ORDER BY 1, 2"),
            "a = 1 is dropped, not null-extended");
        assertEquals("2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON a > 1 ORDER BY 1, 2"));
        assertEquals("2:20 3:30 3:31 4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON f.b > 2 ORDER BY 1, 2"));
        assertEquals("4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON f.a = 4 ORDER BY 1, 2"));
        assertEquals("1:10 1:11 2:20 3:30 3:31 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON f.a IS NOT NULL ORDER BY 1, 2"));
        assertEquals("1:10 2:20 3:30", rows("SELECT f.a, l.v" + LATERAL + " ON l.v = f.a * 10 ORDER BY 1, 2"),
            "a = 4 pairs with nothing, and its null-extended row fails the condition");
        assertEquals("1:10 1:11 2:20 3:30 3:31",
            rows("SELECT f.a, l.v" + LATERAL + " ON l.v >= f.a * 10 ORDER BY 1, 2"));
        assertEquals("2:20 3:30 3:31 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON f.a > 1 AND l.v > 10 ORDER BY 1, 2"));
        assertEquals("1:11 2:20 3:30 3:31 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON f.a > 1 OR l.v = 11 ORDER BY 1, 2"));
    }

    /** LEFT without ON, FULL and RIGHT all answer as the inner join: an empty lateral side drops its left row. */
    @Test
    public void everyOtherOuterSpellingIsTheInnerJoin() {
        assertEquals("1:10 1:11 2:20 3:30 3:31", rows("SELECT f.a, l.v" + LATERAL + " ORDER BY 1, 2"),
            "no ON: a = 4 is dropped");
        assertEquals("1:11 2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              FULL JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON l.v > 10 ORDER BY 1, 2
            """));
        assertEquals("", rows("""
            SELECT f.a, l.v FROM lat_f f
              FULL JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON FALSE ORDER BY 1, 2
            """));
        assertEquals("1:10 1:11 2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              FULL JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON TRUE ORDER BY 1, 2
            """));
        assertEquals("2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              FULL JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON f.a > 1 ORDER BY 1, 2
            """));
        assertEquals("1:10 1:11 2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              RIGHT JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ORDER BY 1, 2
            """));
    }

    /** The same holds for a lateral table function, which takes no ON at all: only FLATTEN's OUTER keeps a row. */
    @Test
    public void anOuterJoinedTableFunctionIsTheInnerJoin() {
        engine.execute("CREATE OR REPLACE TABLE lat_j (id INT, arr VARIANT)");
        engine.execute("INSERT INTO lat_j SELECT 1, PARSE_JSON('[1,2,3]')");
        engine.execute("INSERT INTO lat_j SELECT 2, PARSE_JSON('[]')");
        final String flattened = "1:1 1:2 1:3";
        assertEquals(flattened,
            rows("SELECT j.id, x.value::INT FROM lat_j j LEFT JOIN LATERAL FLATTEN(input => j.arr) x ORDER BY 1, 2"));
        assertEquals(flattened,
            rows("SELECT j.id, x.value::INT FROM lat_j j LEFT JOIN TABLE(FLATTEN(input => j.arr)) x ORDER BY 1, 2"));
        assertEquals(flattened,
            rows("SELECT j.id, x.value::INT FROM lat_j j FULL JOIN LATERAL FLATTEN(input => j.arr) x ORDER BY 1, 2"));
        assertEquals(flattened,
            rows("SELECT j.id, x.value::INT FROM lat_j j RIGHT JOIN LATERAL FLATTEN(input => j.arr) x ORDER BY 1, 2"));
        assertEquals("1:1 1:2 1:3 2:NULL", rows("""
            SELECT j.id, x.value::INT FROM lat_j j
              LEFT JOIN LATERAL FLATTEN(input => j.arr, outer => TRUE) x ORDER BY 1, 2
            """));
    }

    /** The condition is evaluated per pair, so a value it cannot compute refuses the statement. */
    @Test
    public void theConditionIsEvaluatedOnEveryPair() {
        assertTrue(refusal("SELECT f.a, l.v" + LATERAL + " ON 1 / (l.v - 10) > 0").contains("Division by zero"));
        assertTrue(refusal("SELECT f.a, l.v" + LATERAL + " ON l.v = 'x'")
            .contains("Numeric value 'x' is not recognized"));
        assertTrue(refusal("SELECT f.a" + LATERAL + " ON l.nosuch = 0").contains("invalid identifier 'L.NOSUCH'"));
    }

    /** The paired relation feeds the rest of the query like any joined relation. */
    @Test
    public void thePairedRowsFeedTheRestOfTheQuery() {
        assertEquals("4:NULL", rows("SELECT f.a, l.v" + LATERAL + " ON l.v > 10 WHERE l.v IS NULL ORDER BY 1, 2"));
        assertEquals("1:11 2:20 3:30 3:31 4:NULL",
            rows("SELECT f.a, l.v" + LATERAL + " ON l.v > 10 JOIN lat_f f2 ON f2.a = f.a ORDER BY 1, 2"));
        assertEquals("1:11 2:20 3:30 3:31 4:NULL", rows("""
            WITH c AS (SELECT * FROM lat_g)
            SELECT f.a, l.v FROM lat_f f LEFT JOIN LATERAL (SELECT v FROM c WHERE c.k = f.a) l ON l.v > 10
             ORDER BY 1, 2
            """));
    }

    /**
     * An INNER or RIGHT lateral join with ON aborts live with "SQL execution internal error" rather than
     * answering or refusing, so there is no account answer to pin; Frostlake keeps the pairs the condition
     * accepts, which is what the one inner shape live does answer shows.
     */
    @Test
    public void anInnerJoinOverATableAppliesItsCondition() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "INNER and RIGHT JOIN LATERAL ... ON abort live with an internal error, an account-side defect");
        assertEquals("1:11 2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON l.v > 10 ORDER BY 1, 2
            """));
        assertEquals("1:10 2:20 3:30", rows("""
            SELECT f.a, l.v FROM lat_f f
              INNER JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON l.v = f.a * 10 ORDER BY 1, 2
            """));
        assertEquals("1:11 2:20 3:30 3:31", rows("""
            SELECT f.a, l.v FROM lat_f f
              RIGHT JOIN LATERAL (SELECT v FROM lat_g WHERE lat_g.k = f.a) l ON l.v > 10 ORDER BY 1, 2
            """));
    }
}
