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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Oracle {@code (+)} marker in a WHERE outer-joins the marked column's relation to the one other relation its
 * conjunct names. A marked column of a relation nothing outer-joins, a relation outer-joined to two others, a cycle and
 * a marker under an OR are refused while compiling, after every name and before any type; a column written bare is
 * outer-joined as a qualified one is.
 */
public class OuterJoinMarkerRulesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE t2 (b INT)");
        engine.execute("CREATE TABLE t3 (c INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
        engine.execute("INSERT INTO t2 VALUES (1)");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private static String notOuterJoined(final String column) {
        return "SQL compilation error:\nColumn '" + column + "' not from an outer joined table.";
    }

    private static String cycle(final String first, final String second) {
        return "SQL compilation error:\nOuter join predicates form a cycle between '" + first + "' and '" + second + "'.";
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void aMarkedColumnOfARelationNothingOuterJoinsIsRefused() {
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t WHERE a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t WHERE 1 = a (+)"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t WHERE UPPER(a (+)) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t, t2 WHERE t.a (+) = 1"));
        assertEquals(notOuterJoined("T2.B(+)"), refusal("SELECT 1 FROM t, t2 WHERE b (+) = 1"));
        assertEquals(notOuterJoined("X.A(+)"), refusal("SELECT 1 FROM t x WHERE x.a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t JOIN t2 ON a = b WHERE a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = 1 AND a = b (+)"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = 1 AND b (+) = 1"));
        assertEquals(notOuterJoined("T3.C(+)"), refusal("SELECT 1 FROM t, t2, t3 WHERE a = b (+) AND c (+) = 1"));
        assertEquals(notOuterJoined("T2.B(+)"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a + c"));
        assertEquals(notOuterJoined("T2.B(+)"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) + c (+) = a"));
    }

    @Test
    public void subqueriesViewsAndFunctionBodiesAreJudgedToo() {
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT (SELECT COUNT(*) FROM t WHERE a (+) = 1)"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT * FROM (SELECT a FROM t WHERE a (+) = 1)"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("CREATE VIEW v1 AS SELECT 1 AS x FROM t WHERE a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"),
            refusal("CREATE FUNCTION f1() RETURNS INT AS 'SELECT 1 FROM t WHERE a (+) = 1'"));
        assertEquals(notOuterJoined("T.A(+)"),
            refusal("CREATE FUNCTION f2() RETURNS INT AS 'SELECT 1 FROM t, t2 WHERE a (+) = 1'"));
        assertEquals(cycle("T", "T2"),
            refusal("CREATE FUNCTION f3() RETURNS INT AS 'SELECT 1 FROM t, t2 WHERE a (+) = b (+)'"));
        engine.execute("CREATE FUNCTION f4() RETURNS INT AS 'SELECT 1 FROM t, t2 WHERE a = b (+)'");
    }

    @Test
    public void relationsOuterJoinedToEachOtherFormACycle() {
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = b (+)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t2, t WHERE a (+) = b (+)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2 WHERE a (+) + b (+) = 1"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND b = a (+)"));
        assertEquals(cycle("X", "Y"), refusal("SELECT 1 FROM t x, t2 y WHERE x.a (+) = y.b (+)"));
        assertEquals(cycle("Y", "Z"), refusal("SELECT 1 FROM t z, t2 y WHERE a (+) = b (+)"));
        assertEquals(cycle("T2", "T3"), refusal("SELECT 1 FROM t, t2, t3 WHERE a (+) = b AND b (+) = c AND c (+) = a"));
        assertEquals(cycle("T", "T3"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a AND c (+) = b AND a (+) = c"));
        assertEquals(cycle("X", "Z"),
            refusal("SELECT 1 FROM t z, t2 y, t3 x WHERE a (+) = b AND b (+) = c AND c (+) = a"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2, t3 WHERE c (+) = 1 AND a (+) = b (+)"));
    }

    @Test
    public void aRelationOuterJoinedToTwoOthersIsRefusedFirst() {
        assertEquals("SQL compilation error:\nTable 'T2' is outer joined to multiple tables: 'T3' and 'T'.",
            refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a AND b (+) = c"));
        assertEquals("SQL compilation error:\nTable 'T2' is outer joined to multiple tables: 'T' and 'T3'.",
            refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = c AND b (+) = a"));
        assertEquals("SQL compilation error:\nTable 'T2' is outer joined to multiple tables: 'T3' and 'T'.",
            refusal("SELECT 1 FROM t, t2, t3 WHERE a (+) = b AND b (+) = a AND b (+) = c"));
        assertEquals("SQL compilation error:\nTable 'T2' is outer joined to multiple tables: 'T3' and 'T'.",
            refusal("SELECT 1 FROM t, t2, t3 WHERE c (+) = 1 AND b (+) = a AND b (+) = c"));
    }

    @Test
    public void aMarkerUnderAnOrIsRefused() {
        assertEquals("SQL compilation error:\nOuter join column 'T2.B(+)' appears in an OR predicate.",
            refusal("SELECT 1 FROM t, t2 WHERE a = b (+) OR a = 1"));
        assertEquals("SQL compilation error:\nOuter join column 'T2.B(+)' appears in an OR predicate.",
            refusal("SELECT 1 FROM t, t2 WHERE (a = b (+) OR a (+) = 1)"));
        assertEquals("SQL compilation error:\nOuter join column 'T.A(+)' appears in an OR predicate.",
            refusal("SELECT 1 FROM t, t2 WHERE a (+) = 1 OR b = 1"));
        assertEquals("SQL compilation error:\nOuter join column 'T2.B(+)' appears in an OR predicate.",
            refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND (b (+) = 1 OR b (+) = 2)"));
        assertEquals(notOuterJoined("T3.C(+)"), refusal("SELECT 1 FROM t, t2, t3 WHERE c (+) = 1 AND (a = 1 OR b (+) = 1)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2, t3 WHERE a (+) = b (+) AND (c = 1 OR b (+) = 1)"));
    }

    @Test
    public void theRulesComeAfterEveryNameAndBeforeAnyType() {
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusal("SELECT nosuch FROM t WHERE a (+) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 36\ninvalid identifier 'NOSUCH'",
            refusal("SELECT 1 FROM t WHERE a (+) = 1 AND nosuch = 1"));
        assertEquals("SQL compilation error: error line 1 at position 50\ninvalid identifier 'NOSUCH'",
            refusal("SELECT a FROM t WHERE a (+) = 1 GROUP BY 1 HAVING nosuch = 1"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT 1 FROM t WHERE a (+) = 1 AND NOSUCHFN(a) = 1"));
        assertEquals("SQL compilation error:\n[5] is not a valid order by expression",
            refusal("SELECT 1 FROM t WHERE a (+) = 1 ORDER BY 5"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT UPPER(1, 2) FROM t WHERE a (+) = 1"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT 1 FROM t WHERE a (+) = 1 AND UPPER(1, 2) = 'x'"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT a FROM t WHERE a (+) = 1 GROUP BY 1 HAVING a"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT b FROM t, t2 WHERE a (+) = 1 GROUP BY a"));
    }

    @Test
    public void anUnknownMarkedColumnIsEchoedWithItsMarker() {
        assertEquals("SQL compilation error: error line 1 at position 22\ninvalid identifier 'NOSUCH(+)'",
            refusal("SELECT 1 FROM t WHERE nosuch (+) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 26\ninvalid identifier 'T.NOSUCH(+)'",
            refusal("SELECT 1 FROM t, t2 WHERE t.nosuch (+) = b"));
        assertEquals("SQL compilation error: error line 1 at position 24\ninvalid identifier 'X.NOSUCH(+)'",
            refusal("SELECT 1 FROM t x WHERE x.nosuch (+) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 36\ninvalid identifier 'T2.NOSUCH'",
            refusal("SELECT 1 FROM t, t2 WHERE t.a (+) = t2.nosuch"));
    }

    @Test
    public void aBareMarkedColumnOuterJoinsItsRelation() {
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE a (+) = b"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE t.a = t2.b (+)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE b (+) = a AND a > 0"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE UPPER(b (+)) = a"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE a (+) + b = 1"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND a = b (+)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND b (+) = 1"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE b (+) = 1 AND a = b (+)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE a (+) = b AND a (+) = 1"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE NOT (a = b (+))"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = CASE WHEN b (+) = 1 THEN 1 END"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a IN (b (+), 2)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, (SELECT b FROM t2) d WHERE a = d.b (+)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND b (+) IS NULL"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND b IS NULL"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND b (+) <> 1"));
    }

    @Test
    public void aMarkerOutsideTheWhereIsReadAsItsColumn() {
        assertEquals(1, engine.executeQuery("SELECT 1 FROM t JOIN t2 ON a = b (+)").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT a FROM t, t2 GROUP BY a HAVING MAX(b (+)) = a").getRowCount());
        assertEquals(0L, count("SELECT COUNT(*) FROM t3 WHERE c = 1"));
    }

    @Test
    public void aParenthesizedConjunctionIsReadAsItsConjuncts() {
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE (a = b (+) AND a = 2)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE (t.a = t2.b (+) AND t.a = 2)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE (a = b (+)) AND a = 2"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE ((a = b (+) AND a = 2))"));
        assertEquals(1L, count("SELECT COUNT(*) FROM t, t2 WHERE a = 2 AND (a = b (+) AND b (+) = 1)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, t2 WHERE NOT (a = b (+) AND a = 2)"));
        assertEquals(notOuterJoined("T.A(+)"), refusal("SELECT COUNT(*) FROM t, t2 WHERE (a = b (+) AND a (+) = 1)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT COUNT(*) FROM t, t2 WHERE (a = b (+) AND b = a (+))"));
    }

    @Test
    public void aConjunctReadingARelationMarkedAndUnmarkedIsRefused() {
        engine.execute("CREATE TABLE tab (a INT, b2 INT)");
        engine.execute("INSERT INTO tab VALUES (1, 1)");
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT COUNT(*) FROM t, t2 WHERE a = b (+) AND b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND UPPER(b (+)) = b"));
        assertEquals(mixed("T.A(+)", "T.A"), refusal("SELECT 1 FROM t WHERE t.a (+) = t.a"));
        assertEquals(mixed("T.A(+)", "T.A"), refusal("SELECT 1 FROM t, t2 WHERE t.a (+) = t.a + 1"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT 1 FROM tab WHERE a (+) = b2"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT 1 FROM tab WHERE tab.b2 = tab.a (+)"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT COUNT(*) FROM t, tab WHERE t.a = tab.a (+) + tab.b2"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"),
            refusal("SELECT COUNT(*) FROM t, tab WHERE t.a = tab.a (+) AND tab.a (+) = tab.b2"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT 1 FROM t, tab WHERE t.a = tab.a (+) AND b2 = tab.a (+)"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT 1 FROM t, tab WHERE GREATEST(tab.a (+), tab.b2) = t.a"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a + b"));
        assertEquals(mixed("Y.B(+)", "Y.B"), refusal("SELECT COUNT(*) FROM t x, t2 y WHERE x.a = y.b (+) AND y.b (+) = y.b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND NOT (b (+) = 1 AND b = 1)"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT COUNT(*) FROM t, t2 WHERE (a = b (+) AND b (+) = b)"));
        assertEquals(2L, count("SELECT COUNT(*) FROM t, tab WHERE t.a = tab.a (+) AND tab.a (+) = tab.b2 (+)"));
    }

    @Test
    public void theLastMarkedAndTheLastUnmarkedColumnAreNamed() {
        engine.execute("CREATE TABLE tzy (z INT, y INT, x INT)");
        assertEquals(mixed("TZY.X(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) = tzy.y + tzy.z"));
        assertEquals(mixed("TZY.X(+)", "TZY.Y"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) = tzy.z + tzy.y"));
        assertEquals(mixed("TZY.Z(+)", "TZY.Y"), refusal("SELECT 1 FROM tzy WHERE tzy.z (+) = tzy.x + tzy.z + tzy.y"));
        assertEquals(mixed("TZY.X(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.y + tzy.z = tzy.x (+)"));
        assertEquals(mixed("TZY.Y(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) + tzy.y (+) = tzy.z"));
        assertEquals(mixed("TZY.X(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.z = tzy.y (+) + tzy.x (+)"));
        assertEquals(mixed("TZY.X(+)", "TZY.X"), refusal("SELECT 1 FROM tzy WHERE tzy.y + tzy.x = tzy.x (+)"));
        assertEquals(mixed("TZY.X(+)", "TZY.Y"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) = tzy.y AND tzy.x (+) = tzy.z"));
        assertEquals(mixed("TZY.X(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) BETWEEN tzy.y AND tzy.z"));
        assertEquals(mixed("TZY.X(+)", "TZY.Z"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) IN (tzy.y, tzy.z)"));
        assertEquals(mixed("TZY.X(+)", "TZY.Y"), refusal("SELECT 1 FROM tzy WHERE tzy.x (+) = GREATEST(tzy.z, tzy.y)"));
        assertEquals(mixed("TZY.X(+)", "TZY.Z"),
            refusal("SELECT 1 FROM tzy WHERE tzy.x (+) = CASE WHEN tzy.y = 1 THEN tzy.z END"));
        assertEquals(mixed("TZY.X(+)", "TZY.Y"), refusal("SELECT 1 FROM tzy WHERE NOT (tzy.x (+) = tzy.y)"));
    }

    @Test
    public void theMixedRuleIsJudgedConjunctByConjunctAlongsideThePartners() {
        engine.execute("CREATE TABLE tab (a INT, b2 INT)");
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = b AND b (+) = a AND b (+) = c"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a AND b (+) = b AND b (+) = c"));
        assertEquals("SQL compilation error:\nTable 'T2' is outer joined to multiple tables: 'T3' and 'T'.",
            refusal("SELECT 1 FROM t, t2, t3 WHERE b (+) = a AND b (+) = c AND b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE b (+) = b AND a (+) = b (+)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = b (+) AND b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE b (+) = b AND a = b (+) AND b = a (+)"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE a = b (+) AND b (+) = b AND b = a (+)"));
        assertEquals("SQL compilation error:\nOuter join column 'T2.B(+)' appears in an OR predicate.",
            refusal("SELECT 1 FROM t, t2 WHERE a = b (+) OR b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE (a = 1 OR b (+) = 1) AND b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE b (+) = b AND (a = 1 OR b (+) = 1)"));
        assertEquals(cycle("T", "T2"), refusal("SELECT 1 FROM t, t2 WHERE (a = 1 OR b (+) = 1) AND a (+) = b (+)"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE a (+) = 1 AND b (+) = b"));
        assertEquals(mixed("T2.B(+)", "T2.B"), refusal("SELECT 1 FROM t, t2 WHERE b (+) = b AND a (+) = 1"));
        assertEquals(mixed("T2.B(+)", "T2.B"),
            refusal("SELECT 1 FROM t, t2, t3 WHERE c (+) = 1 AND b (+) = b AND a (+) = b (+)"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"),
            refusal("SELECT 1 FROM t, tab, t3 WHERE t.a = tab.a (+) + tab.b2 AND tab.a (+) = t3.c"));
        assertEquals(mixed("TAB.A(+)", "TAB.B2"), refusal("SELECT 1 FROM t, tab WHERE t.a (+) = tab.a (+) + tab.b2"));
    }

    private static String mixed(final String marked, final String unmarked) {
        return "SQL compilation error:\nOuter join column '" + marked + "' appears in expression with non-outer join column '"
            + unmarked + "'.";
    }
}
