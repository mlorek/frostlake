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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failure inside a grouped evaluation is the query's answer, never an empty cell. The grouped
 * operator used to catch everything an aggregate, an aggregate's argument, a grouping-set key or a
 * GROUP BY key threw and hand back NULL (or an error group), so a refusal live raises — and any bug —
 * became a query that answered. Live-verified:
 *
 * <ul>
 *   <li>an aggregate argument that cannot be computed fails the query at the row: {@code SUM(TO_NUMBER(s))}
 *       over text that spells no number is "Numeric value 'x' is not recognized", not a NULL sum;</li>
 *   <li>a GROUP BY key that cannot be computed fails the query rather than collecting every row under
 *       one error key;</li>
 *   <li>the regression members that read ONE side of a pair never convert the other: {@code
 *       REGR_AVGX(bo, n)} over a BOOLEAN and a NUMBER is the average of n, {@code REGR_COUNT(d, n)} the
 *       pair count — answers that used to be NULLs from a swallowed conversion failure;</li>
 *   <li>the one shape that legitimately reads NULL — a super-group subtotal's item derived from a
 *       dimension that row aggregates away — reads a PUBLISHED NULL: {@code COALESCE(c, 'x')} on a
 *       ROLLUP total row is 'x', where a swallowed failure made the whole item NULL.</li>
 * </ul>
 */
public class GroupedFailureSurfacesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gf (city VARCHAR(10), n NUMBER(10,2), bo BOOLEAN, d DATE, s VARCHAR(5))");
        engine.execute("INSERT INTO gf SELECT 'Berlin', 1.5, TRUE, '2020-01-01', '3'");
        engine.execute("INSERT INTO gf SELECT 'Berlin', 2.5, FALSE, '2020-01-02', 'x'");
        engine.execute("INSERT INTO gf SELECT 'Oslo', 3.5, TRUE, '2020-01-03', '5'");
    }

    private String cells(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(String.valueOf(rs.getValue(i)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A failing aggregate argument fails the query. */
    @Test
    public void aFailingAggregateArgumentFailsTheQuery() {
        assertEquals("REFUSED Numeric value 'x' is not recognized",
            cells("SELECT SUM(TO_NUMBER(s)) FROM gf"));
        assertEquals("REFUSED Numeric value 'x' is not recognized",
            cells("SELECT city, SUM(TO_NUMBER(s)) FROM gf GROUP BY city"));
        assertEquals("REFUSED Numeric value 'x' is not recognized",
            cells("SELECT MAX(TO_NUMBER(s)) FROM gf GROUP BY ROLLUP(city)"), "a super-group too");
        assertEquals("REFUSED Division by zero", cells("SELECT city, MAX(1 / 0) FROM gf GROUP BY city"));
        assertEquals("8", cells("SELECT SUM(TO_NUMBER(s)) FROM gf WHERE s <> 'x'"), "and answers when nothing fails");
    }

    /** ★ A failing GROUP BY key fails the query. */
    @Test
    public void aFailingGroupKeyFailsTheQuery() {
        assertEquals("REFUSED Numeric value 'x' is not recognized",
            cells("SELECT TO_NUMBER(s), COUNT(*) FROM gf GROUP BY TO_NUMBER(s)"));
        assertEquals("REFUSED Division by zero", cells("SELECT COUNT(*) FROM gf GROUP BY n / 0"));
    }

    /** ★ A one-sided regression member never converts the side it does not read. */
    @Test
    public void oneSidedRegressionMembersReadOneSideOnly() {
        assertEquals("2.5", cells("SELECT REGR_AVGX(bo, n) FROM gf"), "the average of n over three pairs");
        assertEquals("2.5", cells("SELECT REGR_AVGX(d, n) FROM gf"));
        assertEquals("2.5", cells("SELECT REGR_AVGY(n, bo) FROM gf"));
        assertEquals("3", cells("SELECT REGR_COUNT(bo, n) FROM gf"));
        assertEquals("3", cells("SELECT REGR_COUNT(d, n) FROM gf"));
        assertEquals("2.0", cells("SELECT REGR_SXX(bo, n) FROM gf"), "the centred sum of squares of n");
        assertEquals("Berlin, 2.0 | Oslo, 3.5", cells("SELECT city, REGR_AVGX(bo, n) FROM gf GROUP BY city ORDER BY city"));
    }

    /** ★ A subtotal row's derived item reads a published NULL, not a swallowed failure. */
    @Test
    public void aSubtotalRowsDerivedItemReadsNull() {
        assertEquals("Berlin, berlin, Berlin, 2 | Oslo, oslo, Oslo, 1 | null, null, x, 3",
            cells("SELECT city AS c, LOWER(c) AS lc, COALESCE(c, 'x') AS filled, COUNT(1) AS cnt"
                + " FROM gf GROUP BY ROLLUP(city) ORDER BY c NULLS LAST"));
        assertTrue(cells("SELECT city AS c, LENGTH(c) AS len, COUNT(1) FROM gf GROUP BY CUBE(city) ORDER BY c NULLS LAST")
            .endsWith("null, null, 3"));
    }
}
