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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code SELECT *} over a USING / NATURAL join projects the join key ONCE, FIRST — all live-verified
 * on a real account. {@code t1(a,k,b) JOIN t2(c,k,d) USING (k)} projects {@code K A B C D}
 * (the key moves to the front, not the left table's position); {@code USING (y, x)} surfaces the keys
 * in USING-list order, a NATURAL join in left-table order; a chained {@code … JOIN t3 USING (j)} puts
 * J before the earlier K; and the keys stay in front through a later ON join. On an outer join the
 * merged column is the first NON-NULL of the two sides' copies, and a BARE reference to the key name
 * resolves to that merged value in every context (SELECT, WHERE, GROUP BY, ORDER BY) while a QUALIFIED
 * reference still reads its own side, nulls included.
 *
 * <p>Frostlake used to return the combined row un-projected — 4 declared columns but 5 values per row,
 * every consumer reading by index silently shifted one slot.
 */
public class UsingJoinProjectionTest extends BaseDatabaseTest {

    /** Column names, space-separated. */
    private String cols(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn col : engine.executeQuery(sql).getColumns()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(col.getName());
        }
        return out.toString();
    }

    /** All rows as {@code [a, b]; [c, d]}, asserting every row is exactly as wide as the column list. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            assertEquals(rs.getColumns().size(), row.getValues().size(),
                "row width must match the declared column list");
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(row.getValues());
        }
        return out.toString();
    }

    private void seedDepts() {
        engine.execute("CREATE TABLE emp (dept_id INT, name VARCHAR, salary INT)");
        engine.execute("CREATE TABLE dept (dept_id INT, dept_name VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (10, 'ann', 100), (99, 'zed', 999)");
        engine.execute("INSERT INTO dept VALUES (10, 'eng'), (77, 'hr')");
    }

    @Test
    public void aStarOverAUsingJoinProjectsTheKeyOnce() {
        seedDepts();
        assertEquals("DEPT_ID NAME SALARY DEPT_NAME",
            cols("SELECT * FROM emp JOIN dept USING (dept_id)"));
        assertEquals("[10, ann, 100, eng]",
            rows("SELECT * FROM emp JOIN dept USING (dept_id)"));
    }

    /** The merged key moves to the FRONT — not the left table's position. */
    @Test
    public void theMergedKeyLeadsTheStarEvenFromMidPosition() {
        engine.execute("CREATE TABLE t1 (a INT, k INT, b INT)");
        engine.execute("CREATE TABLE t2 (c INT, k INT, d INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 5, 2)");
        engine.execute("INSERT INTO t2 VALUES (3, 5, 4)");
        assertEquals("K A B C D", cols("SELECT * FROM t1 JOIN t2 USING (k)"));
        assertEquals("[5, 1, 2, 3, 4]", rows("SELECT * FROM t1 JOIN t2 USING (k)"));
        assertEquals("K A B C D", cols("SELECT * FROM t1 NATURAL JOIN t2"));
    }

    /** USING keys surface in USING-list order; NATURAL keys in left-table order. */
    @Test
    public void multiKeyOrderFollowsTheUsingListAndNaturalTheLeftTable() {
        engine.execute("CREATE TABLE m1 (x INT, y INT, p INT)");
        engine.execute("CREATE TABLE m2 (y INT, x INT, r INT)");
        engine.execute("INSERT INTO m1 VALUES (1, 2, 7)");
        engine.execute("INSERT INTO m2 VALUES (2, 1, 8)");
        assertEquals("Y X P R", cols("SELECT * FROM m1 JOIN m2 USING (y, x)"));
        assertEquals("X Y P R", cols("SELECT * FROM m1 NATURAL JOIN m2"));
        assertEquals("[2, 1, 7, 8]", rows("SELECT * FROM m1 JOIN m2 USING (y, x)"));
    }

    /** A chained USING puts ITS key before the earlier one; keys stay in front through an ON join. */
    @Test
    public void chainedJoinsAccumulateTheirKeysInFront() {
        engine.execute("CREATE TABLE u1 (j INT, k INT, a INT)");
        engine.execute("CREATE TABLE u2 (k INT, b INT)");
        engine.execute("CREATE TABLE u3 (j INT, c INT)");
        engine.execute("CREATE TABLE u4 (x INT, d INT)");
        engine.execute("INSERT INTO u1 VALUES (100, 5, 1)");
        engine.execute("INSERT INTO u2 VALUES (5, 2)");
        engine.execute("INSERT INTO u3 VALUES (100, 3)");
        engine.execute("INSERT INTO u4 VALUES (5, 9)");
        assertEquals("J K A B C", cols("SELECT * FROM u1 JOIN u2 USING (k) JOIN u3 USING (j)"));
        assertEquals("[100, 5, 1, 2, 3]", rows("SELECT * FROM u1 JOIN u2 USING (k) JOIN u3 USING (j)"));
        assertEquals("K J A B X D", cols("SELECT * FROM u1 JOIN u2 USING (k) JOIN u4 ON u4.x = k"));
        assertEquals("K J A X D B", cols("SELECT * FROM u1 JOIN u4 ON u4.x = u1.k JOIN u2 USING (k)"));
    }

    /** A same-name key joined twice still projects once. */
    @Test
    public void aThreeWayChainOnTheSameKeyProjectsItOnce() {
        engine.execute("CREATE TABLE t1 (a INT, k INT, b INT)");
        engine.execute("CREATE TABLE t2 (c INT, k INT, d INT)");
        engine.execute("CREATE TABLE t3 (k INT, e INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 5, 2)");
        engine.execute("INSERT INTO t2 VALUES (3, 5, 4)");
        engine.execute("INSERT INTO t3 VALUES (5, 6)");
        assertEquals("K A B C D E", cols("SELECT * FROM t1 JOIN t2 USING (k) JOIN t3 USING (k)"));
        assertEquals("[5, 1, 2, 3, 4, 6]", rows("SELECT * FROM t1 JOIN t2 USING (k) JOIN t3 USING (k)"));
    }

    /** The merged column of an outer join carries the non-null side's key. */
    @Test
    public void aFullOuterJoinCoalescesTheMergedKey() {
        seedDepts();
        assertEquals("[10, ann, 100, eng]; [77, null, null, hr]; [99, zed, 999, null]",
            rows("SELECT * FROM emp FULL OUTER JOIN dept USING (dept_id) ORDER BY dept_id"));
    }

    /** A bare key reference reads the merged value in WHERE, GROUP BY and ORDER BY alike. */
    @Test
    public void aBareKeyReferenceResolvesToTheMergedValueEverywhere() {
        seedDepts();
        assertEquals("[77]",
            rows("SELECT dept_id FROM emp FULL OUTER JOIN dept USING (dept_id) WHERE dept_id = 77"));
        assertEquals("[10, 1]; [77, 1]; [99, 1]",
            rows("SELECT dept_id, COUNT(*) AS n FROM emp FULL OUTER JOIN dept USING (dept_id)"
                + " GROUP BY dept_id ORDER BY dept_id"));
    }

    /** A QUALIFIED reference still reads its own side — nulls included on the null-extended side. */
    @Test
    public void aQualifiedKeyReferenceStillReadsItsOwnSide() {
        seedDepts();
        assertEquals("[10, 10]; [99, null]; [null, 77]",
            rows("SELECT emp.dept_id, dept.dept_id FROM emp FULL OUTER JOIN dept USING (dept_id)"
                + " ORDER BY 1"));
    }

    /** Qualified stars keep each side's full layout, merged nothing. */
    @Test
    public void qualifiedStarsKeepTheirOwnSidesColumns() {
        seedDepts();
        assertEquals("DEPT_ID NAME SALARY", cols("SELECT emp.* FROM emp JOIN dept USING (dept_id)"));
        assertEquals("DEPT_ID DEPT_NAME", cols("SELECT dept.* FROM emp JOIN dept USING (dept_id)"));
    }

    @Test
    public void starModifiersApplyToTheMergedColumn() {
        seedDepts();
        assertEquals("NAME SALARY DEPT_NAME",
            cols("SELECT * EXCLUDE (dept_id) FROM emp JOIN dept USING (dept_id)"));
    }

    /** The window projection reshapes rows itself, so the star must project there too. */
    @Test
    public void aStarBesideAWindowFunctionKeepsTheMergedShape() {
        seedDepts();
        assertEquals("DEPT_ID NAME SALARY DEPT_NAME RN",
            cols("SELECT *, ROW_NUMBER() OVER (ORDER BY name) AS rn FROM emp JOIN dept USING (dept_id)"));
        assertEquals("[10, ann, 100, eng, 1]",
            rows("SELECT *, ROW_NUMBER() OVER (ORDER BY name) AS rn FROM emp JOIN dept USING (dept_id)"));
    }

    /** The merged shape flows into CTAS — three declared columns, not four. */
    @Test
    public void aCtasThroughTheJoinDeclaresTheMergedShape() {
        seedDepts();
        engine.execute("CREATE TABLE ct AS SELECT * FROM emp JOIN dept USING (dept_id)");
        assertEquals("[DEPT_ID]; [NAME]; [SALARY]; [DEPT_NAME]",
            rows("SELECT column_name FROM test_db.information_schema.columns"
                + " WHERE table_name = 'CT' ORDER BY ordinal_position"));
        assertEquals("[10, ann, 100, eng]", rows("SELECT * FROM ct"));
    }
}
