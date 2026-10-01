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
 * An ASOF join's MATCH_CONDITION and ON compile with the statement, over empty sides too: a name nothing resolves is
 * an invalid identifier at its place, ranked with the other join conditions' names, an unknown function and an
 * argument type are refused as in any join condition, and a USING column a side lacks is refused as USING's are. The
 * condition's own shape — one of the four comparisons, each side naming only its own side's columns — is judged
 * after every other refusal of the statement but the grouped select list, prefixed as a compilation error; a bare
 * operand is refused for its operator, not its type, and a subquery's join is judged as the statement's is. A
 * MATCH_CONDITION after any other join is a syntax error.
 */
public class AsofMatchConditionCompileTest extends BaseDatabaseTest {

    private static final String OPERATORS = "SQL compilation error:\nMATCH_CONDITION clause is invalid: Only "
        + "comparison operators '>=', '>', '<=' and '<' are allowed. Keywords such as AND and OR are not allowed.";
    private static final String SIDES = "SQL compilation error:\nMATCH_CONDITION clause is invalid: The left side "
        + "allows only column references from the left side table, and the right side allows only column references "
        + "from the right side table.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (a INT, b INT)");
        engine.execute("CREATE TABLE VT (a INT, v VARIANT, s VARCHAR, d DATE)");
        engine.execute("CREATE TABLE FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO FULL_T VALUES (1, 2)");
        engine.execute("CREATE TABLE FULL2 (a INT, b INT)");
        engine.execute("INSERT INTO FULL2 VALUES (1, 1)");
        engine.execute("CREATE TABLE RT (x INT)");
        engine.execute("INSERT INTO RT VALUES (0)");
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
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

    private static String invalid(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier '" + name + "'";
    }

    @Test
    public void anUnknownNameInTheMatchConditionIsRefusedOverEmptySides() {
        assertEquals(invalid(61, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.nosuch)"));
        assertEquals(invalid(82, "VT.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a ASOF JOIN VT MATCH_CONDITION (f.a >= VT.nosuch)"));
        assertEquals(invalid(54, "F.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.nosuch >= T.a)"));
        assertEquals(invalid(54, "NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (nosuch >= T.a)"));
        assertEquals(invalid(61, "NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= nosuch)"));
        assertEquals(invalid(67, "G.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= g.nosuch)"));
        assertEquals(invalid(67, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a + T.nosuch)"));
        assertEquals(invalid(60, "F.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a + f.nosuch >= T.a)"));
        assertEquals(invalid(61, "T.S"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.s)"));
    }

    @Test
    public void anUnknownNameOutranksTheSideRule() {
        assertEquals(invalid(54, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.nosuch >= f.a)"));
        assertEquals(invalid(54, "F.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.nosuch >= f.a)"));
        assertEquals(invalid(61, "F.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.nosuch)"));
        assertEquals(invalid(60, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a = T.nosuch)"));
    }

    @Test
    public void theOnOfAnAsofJoinCompilesFirst() {
        assertEquals(invalid(75, "T.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) ON f.b = T.nosuch"));
        assertEquals(invalid(69, "F.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) ON f.nosuch = T.a"));
        assertEquals(invalid(83, "T.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) ON f.b = T.b AND T.nosuch = 1"));
        assertEquals(invalid(80, "T.NOSUCH2"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.nosuch) ON f.b = T.nosuch2"));
        assertEquals("SQL compilation error:\nInvalid identifier NOSUCH",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) USING (nosuch)"));
    }

    @Test
    public void theConditionRanksWithTheOtherJoinConditions() {
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.nosuch)"));
        assertEquals(invalid(61, "T.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.nosuch) WHERE nosuch2 = 1"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= NOSUCHFN(T.a))"));
        assertEquals("SQL compilation error: error line 1 at position 61\ntoo many arguments for function [UPPER(1, 2)] "
            + "expected 1, got 2", refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= UPPER(1, 2))"));
        assertEquals("SQL compilation error: error line 1 at position 61\nBind variable :1 not set.",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= :1)"));
        assertEquals("SQL compilation error:\nambiguous column name 'A'",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (a >= T.a)"));
        assertEquals("SQL compilation error:\nambiguous column name 'B'",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= b)"));
    }

    @Test
    public void onlyTheFourComparisonsAreAllowed() {
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a = T.a)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a != T.a)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a AND f.b >= T.b)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a BETWEEN T.a AND T.b)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (TRUE)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (NOT f.a >= T.a)"));
    }

    @Test
    public void eachSideNamesOnlyItsOwnSideAndAtLeastOneColumn() {
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= f.b)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a + f.b)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a + T.b >= T.a)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= 1)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (1 >= T.a)"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN VT MATCH_CONDITION (VT.v >= f.a)"));
    }

    @Test
    public void theShapeIsJudgedAfterTheRestOfTheStatement() {
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a)"));
        assertEquals(invalid(72, "NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) WHERE nosuch = 1"));
        assertEquals(invalid(79, "T2.NOSUCH"), refusal(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) JOIN T t2 ON t2.nosuch = 1"));
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a = T.a)"));
        assertEquals("SQL compilation error:\n[5] is not a valid order by expression",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) ORDER BY 5"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) WHERE NOSUCHFN(1) = 1"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [F.A]",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) WHERE f.a"));
        assertEquals(SIDES, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) GROUP BY f.b"));
    }

    @Test
    public void aBareOperandIsRefusedForItsOperatorNotItsType() {
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION ((f.a))"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN VT MATCH_CONDITION (VT.v)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN VT MATCH_CONDITION (VT.s)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN VT MATCH_CONDITION (VT.d)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a + T.a)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (1)"));
        assertEquals(OPERATORS, refusal("SELECT f.a FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (g.a)"));
        assertEquals("SQL compilation error: error line 1 at position 54\ntoo many arguments for function [UPPER(1, 2)] "
            + "expected 1, got 2", refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (UPPER(1, 2))"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (NOSUCHFN(f.a))"));
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a)"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [F.B]",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a) WHERE f.b"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [F.A]",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a) GROUP BY f.a HAVING f.a"));
    }

    @Test
    public void theOnOfAnAsofJoinIsStillAPredicate() {
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [F.A]",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) ON f.a"));
        assertEquals("SQL compilation error:\nInvalid data type [VARIANT] for predicate [VT.V]",
            refusal("SELECT f.a FROM FULL_T f ASOF JOIN VT MATCH_CONDITION (f.a >= VT.a) ON VT.v"));
    }

    @Test
    public void aSubqueryJoinJudgesItsSidesAsTheStatementDoes() {
        assertEquals(SIDES, refusal("SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.b >= f.a))"));
        assertEquals(SIDES, refusal("SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a))"));
        assertEquals(SIDES, refusal(
            "SELECT a FROM FULL2 WHERE a IN (SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a))"));
        assertEquals(SIDES, refusal(
            "SELECT a FROM FULL2 WHERE a IN (SELECT f.a FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= f.b))"));
        assertEquals(SIDES, refusal("SELECT o.a, (SELECT COUNT(*) FROM FULL_T f ASOF JOIN T MATCH_CONDITION (T.a >= f.a) "
            + "WHERE f.a = o.a) FROM FULL2 o"));
        assertEquals(SIDES, refusal("SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.b >= f.a)) "
            + "FROM FULL2"));
        assertEquals(SIDES, refusal("SELECT * FROM FULL2 o, LATERAL (SELECT COUNT(*) AS n FROM FULL_T f ASOF JOIN FULL2 g "
            + "MATCH_CONDITION (f.b >= f.a) WHERE f.a = o.a)"));
        assertEquals(SIDES, refusal(
            "SELECT a FROM T WHERE EXISTS (SELECT 1 FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (g.a >= f.a))"));
        assertEquals(SIDES, refusal(
            "SELECT a FROM T WHERE a IN (SELECT f.a FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= f.b))"));
        assertEquals(OPERATORS, refusal("SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (g.a))"));
    }

    @Test
    public void aNameOfTheQueryAroundTheJoinIsNeitherSide() {
        assertEquals(SIDES, refusal(
            "SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= o.a)) FROM FULL2 o"));
        assertEquals(SIDES, refusal(
            "SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (o.a >= g.a)) FROM FULL2 o"));
        assertEquals(SIDES, refusal(
            "SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN RT MATCH_CONDITION (x >= a)) FROM (SELECT 0 AS x, 1 AS a)"));
        assertEquals(SIDES, refusal("SELECT x FROM RT WHERE x IN (SELECT COUNT(*) - 1 FROM FULL_T f ASOF JOIN FULL2 g "
            + "MATCH_CONDITION (x >= g.a))"));
        assertEquals(1L, count("SELECT (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= g.a))"));
        assertEquals(1L, count(
            "SELECT (SELECT COUNT(*) FROM FULL_T f JOIN FULL2 g ON f.a = g.a ASOF JOIN RT MATCH_CONDITION (g.b >= RT.x))"));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT o.a, (SELECT COUNT(*) FROM FULL_T f ASOF JOIN FULL2 g "
            + "MATCH_CONDITION (f.a >= g.a) WHERE f.a = o.a) FROM FULL2 o").getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void aMatchConditionAfterAnyOtherJoinIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 32 unexpected 'MATCH_CONDITION'.",
            refusal("SELECT f.a FROM FULL_T f JOIN T MATCH_CONDITION (f.a >= T.nosuch)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 37 unexpected 'MATCH_CONDITION'.",
            refusal("SELECT f.a FROM FULL_T f LEFT JOIN T MATCH_CONDITION (f.a >= T.a)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 32 unexpected 'MATCH_CONDITION'.",
            refusal("SELECT f.a FROM FULL_T f JOIN T MATCH_CONDITION (f.a >= T.a) ON f.b = T.b"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 35 unexpected 'MATCH_CONDITION'.",
            refusal("SELECT nosuch FROM FULL_T f JOIN T MATCH_CONDITION (f.a >= T.a)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 40 unexpected 'MATCH_CONDITION'.",
            refusal("SELECT f.a FROM FULL_T f NATURAL JOIN T MATCH_CONDITION (f.a >= T.a)"));
    }

    @Test
    public void aSoundConditionStillJoins() {
        assertEquals(1, engine.executeQuery(
            "SELECT f.a FROM FULL_T f ASOF JOIN FULL2 g MATCH_CONDITION (f.a >= g.a)").getRowCount());
        assertEquals(1, engine.executeQuery(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION ((f.a) >= (T.a))").getRowCount());
        assertEquals(1, engine.executeQuery(
            "SELECT f.a FROM FULL_T f ASOF JOIN T MATCH_CONDITION (f.a >= T.a) USING (a)").getRowCount());
    }
}
