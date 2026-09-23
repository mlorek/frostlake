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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A subquery whose body is a set operation is re-printed from its plan wherever a refusal quotes it: the arms
 * combined under one {@code SET_COMBINE} per column, each wrapped with its own relations, its column cast to the
 * type the arms meet in and, under UNION, marked {@code ENSURE_NULLABLE} where it holds no NULL but the combined
 * column may. INTERSECT binds before the other operators, a run of one operator is one combination, and the
 * relations are numbered in the order the plan completes them. Each expected answer is the account's own.
 */
public class SetOperationSubqueryEchoTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|[(";
    private static final String NOT_VALID = ")] is not a valid order by expression";
    private static final String DUAL = " FROM (VALUES (null)) DUAL";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT, w INT NOT NULL, x VARCHAR(3))");
        engine.execute("INSERT INTO g VALUES (5, 50, 1, 'a'), (6, 60, 2, 'b')");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The grouped query that refuses a subquery as its ORDER BY key, quoting it. */
    private String orderedBy(final String subquery) {
        return answer("SELECT id FROM fz GROUP BY id ORDER BY (" + subquery + ")");
    }

    @Test
    public void eachOperatorCombinesItsArms() {
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.\"MAX(V)\", WB$1.\"1\") AS \"MAX(V)\" FROM (SELECT OB$0.\"MAX(V)\""
                + " AS \"MAX(V)\" FROM (SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G) OB$0) WB$0 UNION ALL (SELECT"
                + " ENSURE_NULLABLE(CAST(OB$1.\"1\" AS NUMBER(38,0))) AS \"1\" FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$1) WB$1"
                + NOT_VALID,
            orderedBy("SELECT MAX(v) FROM g UNION ALL SELECT 1"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.V, WB$1.\"1\") AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM (SELECT G.V"
                + " AS \"V\" FROM G AS G) OB$0) WB$0 UNION (SELECT ENSURE_NULLABLE(CAST(OB$1.\"1\" AS NUMBER(38,0))) AS \"1\""
                + " FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT v FROM g UNION SELECT 1"));
        final String minus = REFUSED + "SELECT SET_COMBINE(WB$0.V, WB$1.\"1\") AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM"
            + " (SELECT G.V AS \"V\" FROM G AS G) OB$0) WB$0 MINUS (SELECT CAST(OB$1.\"1\" AS NUMBER(38,0)) AS \"1\" FROM"
            + " (SELECT 1 AS \"1\"" + DUAL + ") OB$1) WB$1" + NOT_VALID;
        assertEquals(minus, orderedBy("SELECT v FROM g MINUS SELECT 1"));
        assertEquals(minus, orderedBy("SELECT v FROM g EXCEPT SELECT 1"), "EXCEPT is spelled MINUS");
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.V, WB$1.\"1\") AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM (SELECT G.V"
                + " AS \"V\" FROM G AS G) OB$0) WB$0 INTERSECT (SELECT CAST(OB$1.\"1\" AS NUMBER(38,0)) AS \"1\" FROM (SELECT 1"
                + " AS \"1\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT v FROM g INTERSECT SELECT 1"));
    }

    @Test
    public void theArmsMeetInOneTypeAndOneNullability() {
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.V, WB$1.W) AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM (SELECT G.V AS"
                + " \"V\" FROM G AS G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(OB$1.W) AS \"W\" FROM (SELECT G.W AS \"W\""
                + " FROM G AS G) OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT v FROM g UNION ALL SELECT w FROM g"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"1.5\") AS \"1\" FROM (SELECT CAST(OB$0.\"1\" AS"
                + " NUMBER(2,1)) AS \"1\" FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$0) WB$0 UNION ALL (SELECT OB$1.\"1.5\" AS"
                + " \"1.5\" FROM (SELECT 1.5 AS \"1.5\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT 1 UNION ALL SELECT 1.5"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.X, WB$1.\"'BCDEF'\") AS \"X\" FROM (SELECT CAST(OB$0.X AS VARCHAR(5))"
                + " AS \"X\" FROM (SELECT G.X AS \"X\" FROM G AS G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(OB$1.\"'BCDEF'\")"
                + " AS \"'BCDEF'\" FROM (SELECT 'bcdef' AS \"'BCDEF'\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT x FROM g UNION ALL SELECT 'bcdef'"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.V, WB$1.\"1.5::FLOAT\") AS \"V\" FROM (SELECT CAST(OB$0.V AS FLOAT) AS"
                + " \"V\" FROM (SELECT G.V AS \"V\" FROM G AS G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(OB$1.\"1.5::FLOAT\")"
                + " AS \"1.5::FLOAT\" FROM (SELECT CAST(1.5 AS FLOAT) AS \"1.5::FLOAT\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT v FROM g UNION ALL SELECT 1.5::FLOAT"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.NULL, WB$1.\"1\") AS \"NULL\" FROM (SELECT SYSTEM$NULL_TO_FIXED(OB$0.NULL)"
                + " AS \"NULL\" FROM (SELECT null AS \"NULL\"" + DUAL + ") OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(OB$1.\"1\")"
                + " AS \"1\" FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$1) WB$1" + NOT_VALID,
            orderedBy("SELECT NULL UNION ALL SELECT 1"));
    }

    @Test
    public void intersectBindsFirstAndARunCombinesAtOnce() {
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\", WB$2.\"3\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\""
                + " FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\""
                + DUAL + ") OB$1) WB$1 UNION ALL (SELECT OB$2.\"3\" AS \"3\" FROM (SELECT 3 AS \"3\"" + DUAL + ") OB$2) WB$2"
                + NOT_VALID,
            orderedBy("SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$2.\"1\", WB$3.\"3\") AS \"1\" FROM (SELECT OB$2.\"1\" AS \"1\" FROM (SELECT"
                + " SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\"" + DUAL
                + ") OB$0) WB$0 UNION (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\"" + DUAL + ") OB$1) WB$1) OB$2) WB$2"
                + " UNION ALL (SELECT OB$3.\"3\" AS \"3\" FROM (SELECT 3 AS \"3\"" + DUAL + ") OB$3) WB$3" + NOT_VALID,
            orderedBy("SELECT 1 UNION SELECT 2 UNION ALL SELECT 3"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$2.\"1\", WB$3.\"2\") AS \"1\" FROM (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT"
                + " 1 AS \"1\"" + DUAL + ") OB$0) WB$2 UNION ALL (SELECT OB$3.\"2\" AS \"2\" FROM (SELECT SET_COMBINE(WB$0.\"2\","
                + " WB$1.\"3\") AS \"2\" FROM (SELECT OB$1.\"2\" AS \"2\" FROM (SELECT 2 AS \"2\"" + DUAL + ") OB$1) WB$0 INTERSECT"
                + " (SELECT OB$2.\"3\" AS \"3\" FROM (SELECT 3 AS \"3\"" + DUAL + ") OB$2) WB$1) OB$3) WB$3" + NOT_VALID,
            orderedBy("SELECT 1 UNION ALL SELECT 2 INTERSECT SELECT 3"));
        assertEquals(REFUSED + "SELECT SET_COMBINE(WB$3.\"1\", WB$4.\"4\") AS \"1\" FROM (SELECT OB$4.\"1\" AS \"1\" FROM (SELECT"
                + " SET_COMBINE(OB$2.\"1\", WB$2.\"3\") AS \"1\" FROM (SELECT SET_COMBINE(WB$0.\"1\", WB$1.\"2\") AS \"1\" FROM"
                + " (SELECT OB$0.\"1\" AS \"1\" FROM (SELECT 1 AS \"1\"" + DUAL + ") OB$0) WB$0 UNION ALL (SELECT OB$1.\"2\" AS"
                + " \"2\" FROM (SELECT 2 AS \"2\"" + DUAL + ") OB$1) WB$1) OB$2 MINUS (SELECT OB$3.\"3\" AS \"3\" FROM (SELECT 3"
                + " AS \"3\"" + DUAL + ") OB$3) WB$2) OB$4) WB$3 UNION ALL (SELECT OB$5.\"4\" AS \"4\" FROM (SELECT 4 AS \"4\""
                + DUAL + ") OB$5) WB$4" + NOT_VALID,
            orderedBy("SELECT 1 UNION ALL SELECT 2 MINUS SELECT 3 UNION ALL SELECT 4"));
    }

    @Test
    public void aMembershipTestReadsTheSameCombination() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1 = ANY("
                + "SELECT SET_COMBINE(WB$0.V, WB$1.\"1\") AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM (SELECT G.V AS \"V\" FROM G"
                + " AS G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(CAST(OB$1.\"1\" AS NUMBER(38,0))) AS \"1\" FROM (SELECT 1"
                + " AS \"1\"" + DUAL + ") OB$1) WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT v FROM g UNION ALL SELECT 1))"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [CHARINDEX(1 = ANY("
                + "SELECT SET_COMBINE(WB$0.V, WB$1.W) AS \"V\" FROM (SELECT OB$0.V AS \"V\" FROM (SELECT G.V AS \"V\" FROM G AS"
                + " G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(OB$1.W) AS \"W\" FROM (SELECT G.W AS \"W\" FROM G AS G) OB$1)"
                + " WB$1))], expected 2, got 1",
            answer("SELECT CHARINDEX(1 IN (SELECT v FROM g UNION ALL SELECT w FROM g))"));
    }
}
