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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a refusal's echo re-prints a membership test over a subquery whose shape goes past a plain select: a set
 * operation, a derived table or a CTE, a NATURAL, USING or ASOF join, text meeting a number, and a reference to
 * the query around the subquery. Each expected answer is the account's own.
 */
public class SubqueryMembershipShapeEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (g VARCHAR(10), n NUMBER(5,0), v VARIANT, b BOOLEAN)");
        engine.execute("CREATE TABLE re (a NUMBER(10,2), i NUMBER(10,0), f FLOAT)");
        engine.execute("CREATE TABLE one (g VARCHAR(10))");
        engine.execute("CREATE TABLE two (g VARCHAR(10), k NUMBER(5,0))");
    }

    /** The refusal as one line, each line break as |, or the rows' first cells after ACCEPTED:. */
    private String answer(final String sql) {
        try {
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            for (final Object value : firstColumn(sql)) {
                all.append(' ').append(value);
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private List<Object> firstColumn(final String sql) {
        final List<Object> values = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            values.add(row.getValue(0));
        }
        return values;
    }

    /** A set operation of one-column arms: each arm wrapped and cast to the type the arms meet in, SET_COMBINE over them. */
    @Test
    public void aSetOperationIsRePrintedAsItsCombinedArms() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SET_COMBINE(WB$0.\"'ABC'\", WB$1.\"'D'\") AS \"'ABC'\" FROM (SELECT OB$0.\"'ABC'\" AS \"'ABC'\" FROM (SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION (SELECT CAST(OB$1.\"'D'\" AS VARCHAR(3)) AS \"'D'\" FROM (SELECT 'd' AS \"'D'\" FROM (VALUES (null)) DUAL) OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' UNION SELECT 'd'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SET_COMBINE(WB$0.G, WB$1.G) AS \"G\" FROM (SELECT OB$0.G AS \"G\" FROM (SELECT RT.G AS \"G\" FROM RT AS RT) OB$0) WB$0 UNION ALL (SELECT OB$1.G AS \"G\" FROM (SELECT ONE.G AS \"G\" FROM ONE AS ONE) OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt UNION ALL SELECT g FROM one))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SET_COMBINE(WB$0.G, WB$1.G) AS \"G\" FROM (SELECT OB$0.G AS \"G\" FROM (SELECT RT.G AS \"G\" FROM RT AS RT) OB$0) WB$0 MINUS (SELECT OB$1.G AS \"G\" FROM (SELECT ONE.G AS \"G\" FROM ONE AS ONE) OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt EXCEPT SELECT g FROM one))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' != ALL(SELECT SET_COMBINE(WB$0.\"'ABC'\", WB$1.\"'D'\") AS \"'ABC'\" FROM (SELECT OB$0.\"'ABC'\" AS \"'ABC'\" FROM (SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION (SELECT CAST(OB$1.\"'D'\" AS VARCHAR(3)) AS \"'D'\" FROM (SELECT 'd' AS \"'D'\" FROM (VALUES (null)) DUAL) OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN (SELECT 'abc' UNION SELECT 'd'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('b' = ANY(SELECT SET_COMBINE(WB$0.\"'ABC'\", WB$1.\"'D'\") AS \"'ABC'\" FROM (SELECT OB$0.\"'ABC'\" AS \"'ABC'\" FROM (SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION (SELECT CAST(OB$1.\"'D'\" AS VARCHAR(3)) AS \"'D'\" FROM (SELECT 'd' AS \"'D'\" FROM (VALUES (null)) DUAL) OB$1) WB$1), 1)] expected 1, got 2",
            answer("SELECT UPPER('b' IN (SELECT 'abc' UNION SELECT 'd'), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SET_COMBINE(WB$0.\"'ABC'\", WB$1.\"'D'\") AS \"'ABC'\" FROM (SELECT OB$0.\"'ABC'\" AS \"'ABC'\" FROM (SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 INTERSECT (SELECT CAST(OB$1.\"'D'\" AS VARCHAR(3)) AS \"'D'\" FROM (SELECT 'd' AS \"'D'\" FROM (VALUES (null)) DUAL) OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' INTERSECT SELECT 'd'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SET_COMBINE(WB$0.\"'ABC'\", WB$1.\"'D'\", WB$2.\"'E'\") AS \"'ABC'\" FROM (SELECT OB$0.\"'ABC'\" AS \"'ABC'\" FROM (SELECT 'abc' AS \"'ABC'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT CAST(OB$1.\"'D'\" AS VARCHAR(3)) AS \"'D'\" FROM (SELECT 'd' AS \"'D'\" FROM (VALUES (null)) DUAL) OB$1) WB$1 UNION ALL (SELECT CAST(OB$2.\"'E'\" AS VARCHAR(3)) AS \"'E'\" FROM (SELECT 'e' AS \"'E'\" FROM (VALUES (null)) DUAL) OB$2) WB$2))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 'abc' UNION ALL SELECT 'd' UNION ALL SELECT 'e'))"));
    }

    /** A derived table, a CTE and a LATERAL body are inlined in brackets under their name, an unaliased one as values. */
    @Test
    public void aDerivedTableOrCteIsInlinedByItsName() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT \"values\".X AS \"X\" FROM (SELECT RT.G AS \"X\" FROM RT AS RT) values))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT x FROM (SELECT g AS x FROM rt)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT D.G AS \"G\" FROM (SELECT RT.G AS \"G\" FROM RT AS RT) D))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM (SELECT g FROM rt) d))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT D.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\" FROM RT AS RT WHERE RT.N > 1) D))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT d.g FROM (SELECT g, n FROM rt WHERE n > 1) d))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' != ALL(SELECT \"values\".X AS \"X\" FROM (SELECT RT.G AS \"X\" FROM RT AS RT) values))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' NOT IN (SELECT x FROM (SELECT g AS x FROM rt)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT C.G AS \"G\" FROM (SELECT RT.G AS \"G\" FROM RT AS RT) C))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (WITH c AS (SELECT g FROM rt) SELECT g FROM c))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT, (SELECT ONE.G AS \"G\" FROM ONE AS ONE) L))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt, LATERAL (SELECT g FROM one) l))"));
    }

    /** A NATURAL or USING join reads through SYS_VW; an ASOF join keeps its MATCH_CONDITION; a CROSS join is an inner join. */
    @Test
    public void aJoinIsRePrintedAsThePlanHoldsIt() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\" FROM RT AS RT INNER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt NATURAL JOIN one))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\" FROM RT AS RT INNER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt JOIN one USING (g)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\" FROM RT AS RT INNER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt JOIN one USING (g)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\", ONE.G AS \"G_0\" FROM RT AS RT LEFT OUTER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt LEFT JOIN one USING (g)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\", TWO.G AS \"G_0\", TWO.K AS \"K\" FROM RT AS RT LEFT OUTER JOIN TWO AS TWO ON (RT.G = TWO.G)) SYS_VW))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt NATURAL LEFT JOIN two))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT SYS_VW.G AS \"G\" FROM (SELECT RT.G AS \"G\", RT.N AS \"N\", RT.V AS \"V\", RT.B AS \"B\" FROM RT AS RT INNER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW WHERE SYS_VW.N > 1))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT g FROM rt JOIN one USING (g) WHERE n > 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT ASOF JOIN TWO AS TWO MATCH_CONDITION (RT.N >= TWO.K)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt ASOF JOIN two MATCH_CONDITION (rt.n >= two.k)))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT ASOF JOIN TWO AS TWO MATCH_CONDITION (RT.N >= TWO.K) ON (RT.G = TWO.G)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt ASOF JOIN two MATCH_CONDITION (rt.n >= two.k) ON rt.g = two.g))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT RT.G AS \"G\" FROM RT AS RT INNER JOIN ONE AS ONE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g FROM rt CROSS JOIN one))"));
    }

    /** Text meeting an exact number converts both sides to NUMBER(18,5); text meeting a FLOAT is cast to FLOAT. */
    @Test
    public void textMeetsANumberInNumber18Scale5() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(RT.N AS NUMBER(18,5)) AS \"N\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT n FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(1 AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER(RT.G, 18, 5) AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT g FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(5 AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER('5', 18, 5) AS \"'5'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(5 IN (SELECT '5'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(5 AS NUMBER(18,5)) AS \"5\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 5 FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(12.5 AS NUMBER(18,5)) AS \"12.5\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT 12.5))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(RT.N + 1 AS NUMBER(18,5)) AS \"N + 1\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT n + 1 FROM rt))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(RT.N AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER('x', 18, 5) AS \"'X'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(n IN (SELECT 'x')) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(RT.N AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER(RT.G, 18, 5) AS \"G\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(n IN (SELECT g FROM rt)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER(RT.G, 18, 5)) = ANY(SELECT CAST(RT.N AS NUMBER(18,5)) AS \"N\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT n FROM rt)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(RE.A AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER('x', 18, 5) AS \"'X'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(a IN (SELECT 'x')) FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(RE.A AS NUMBER(18,5)) AS \"A\" FROM RE AS RE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT a FROM re))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('b', 18, 5)) = ANY(SELECT CAST(RE.I AS NUMBER(18,5)) AS \"I\" FROM RE AS RE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT i FROM re))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST('b' AS FLOAT)) = ANY(SELECT RE.F AS \"F\" FROM RE AS RE))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT f FROM re))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER('bb', 18, 5)) = ANY(SELECT CAST(123 AS NUMBER(18,5)) AS \"123\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('bb' IN (SELECT 123))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((CAST(12.5 AS NUMBER(18,5))) = ANY(SELECT TO_NUMBER('x', 18, 5) AS \"'X'\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(12.5 IN (SELECT 'x'))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER(RT.G, 18, 5)) = ANY(SELECT CAST(5 AS NUMBER(18,5)) AS \"5\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT 5)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX((TO_NUMBER(RT.G, 18, 5)) = ANY(SELECT CAST(5 AS NUMBER(18,5)) AS \"5\" FROM RT AS RT))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT 5 FROM rt)) FROM rt"));
    }

    /** A reference to the query around the subquery prints as CORRELATION of the outer column. */
    @Test
    public void aReferenceToTheOuterQueryIsACorrelation() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(RT.G = ANY(SELECT O.G AS \"G\" FROM ONE AS O WHERE O.G = CORRELATION(RT.G)))], expected 2, got 1",
            answer("SELECT CHARINDEX(g IN (SELECT o.g FROM one o WHERE o.g = rt.g)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT ONE.G AS \"G\" FROM ONE AS ONE WHERE ONE.G = CORRELATION(RT.G)))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT one.g FROM one WHERE one.g = rt.g)) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX('b' = ANY(SELECT CORRELATION(RT.G) AS \"G\" FROM (VALUES (null)) DUAL))], expected 2, got 1",
            answer("SELECT CHARINDEX('b' IN (SELECT rt.g)) FROM rt"));
    }
}
