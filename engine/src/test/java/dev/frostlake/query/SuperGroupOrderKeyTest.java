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

/**
 * A ROLLUP / CUBE / GROUPING SETS query ordering by a key its SELECT list does not project. The plain
 * GROUP BY case was fixed by the grouped-window work; these were left behind by it, and they failed
 * two different ways:
 *
 * <pre>
 *   SELECT ROW_NUMBER() OVER (ORDER BY SUM(b)) … GROUP BY ROLLUP(a) ORDER BY a
 *       refused outright — "invalid identifier 'A'"
 *   SELECT SUM(b) … GROUP BY ROLLUP(a) ORDER BY a DESC
 *       did NOT SORT, which is worse: the rollup's own order came back looking like an answer
 * </pre>
 *
 * <p>The cause was one missing argument. Every grouping set runs its own grouping operator, and the
 * super-group branch never passed the sink that captures each group's SOURCE ROWS — so the map from
 * output row to source group stayed empty and every resolver keyed off it answered UNRESOLVED.
 *
 * <p>Passing the sink through is NOT the whole job, and the ASC cells are what hide that. A
 * super-group row's value for a dimension OUTSIDE its own set is NULL, not the group's first row:
 * evaluating {@code a} over the grand-total group would answer 1 and sort that row among the details.
 * The rows handed to the resolver therefore have those columns BLANKED, so the key evaluates to NULL
 * on its own.
 *
 * <p>THE FIXTURE MATTERS. With a = 1,2,3 the unsorted rollup order is already the ASC-with-NULLS-LAST
 * answer, so {@code ORDER BY a} and {@code ORDER BY a NULLS LAST} passed before any of this was
 * fixed. Only DESC and NULLS FIRST can tell a sort from a no-op here, which is why they lead.
 */
public class SuperGroupOrderKeyTest extends BaseDatabaseTest {

    private static final String WIN = "ROW_NUMBER() OVER (ORDER BY SUM(b))";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 30), (2, 10), (3, 50)");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    all.append("/");
                }
                all.append(String.valueOf(rs.getValue(c)));
            }
        }
        return all.toString();
    }

    /** The cells that can tell a SORT from a no-op: the rollup's own order is not the answer. */
    @Test
    public void theSortActuallyHappens() {
        assertEquals("90,50,10,30", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a DESC"),
            "DESC puts the grand total FIRST, because its key is NULL");
        assertEquals("90,30,10,50", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a NULLS FIRST"));
        assertEquals("50,10,30,90", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a DESC NULLS LAST"));
    }

    /** ASC agrees too — but only these two are evidence of anything, per the note above. */
    @Test
    public void ascendingAgreesAsWell() {
        assertEquals("30,10,50,90", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("30,10,50,90", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a NULLS LAST"));
    }

    /** A WINDOW over the super-group rows resolves the unprojected key instead of refusing. */
    @Test
    public void aWindowOverSuperGroupRowsResolvesTheKey() {
        assertEquals("2,1,3,4", answer(
            "SELECT " + WIN + " FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("2,1,3,4", answer(
            "SELECT " + WIN + " FROM gw GROUP BY CUBE(a) ORDER BY a"));
        assertEquals("2,1,3", answer(
            "SELECT " + WIN + " FROM gw GROUP BY GROUPING SETS ((a)) ORDER BY a"));
        assertEquals("1,2,3,4", answer(
            "SELECT " + WIN + " FROM gw GROUP BY ROLLUP(a) ORDER BY SUM(b)"),
            "and an unprojected AGGREGATE key too");
    }

    /** The three super-group spellings sort alike, and a plain GROUP BY is unchanged. */
    @Test
    public void everySuperGroupSpellingSorts() {
        assertEquals("30,10,50,90", answer("SELECT SUM(b) FROM gw GROUP BY CUBE(a) ORDER BY a"));
        assertEquals("30,10,50", answer(
            "SELECT SUM(b) FROM gw GROUP BY GROUPING SETS ((a)) ORDER BY a"));
        assertEquals("30,10,50,90", answer(
            "SELECT SUM(b) FROM gw GROUP BY GROUPING SETS ((a), ()) ORDER BY a"));
        assertEquals("30,10,50", answer("SELECT SUM(b) FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2,1,3", answer("SELECT " + WIN + " FROM gw GROUP BY a ORDER BY a"));
    }

    /** What already worked must not move: a PROJECTED key, an aggregate key, and GROUPING(). */
    @Test
    public void theProjectedAndAggregateKeysAreUnchanged() {
        assertEquals("1/30,2/10,3/50,null/90", answer(
            "SELECT a, SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("null/90,3/50,2/10,1/30", answer(
            "SELECT a, SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a DESC"));
        assertEquals("10,30,50,90", answer(
            "SELECT SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY SUM(b)"));
        assertEquals("0/30,0/10,0/50,1/90", answer(
            "SELECT GROUPING(a), SUM(b) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
    }
}
