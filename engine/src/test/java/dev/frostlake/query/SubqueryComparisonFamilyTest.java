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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A comparison whose operand is a subquery is held to the family rule of every comparison while the statement
 * compiles, over empty tables too: a scalar subquery is named as its plan re-prints it, an EXISTS as
 * {@code EXISTS(…)} and a NOT EXISTS as {@code NOT EXISTS(…)}, and a membership test — {@code [NOT] IN (SELECT …)},
 * {@code op ANY / SOME / ALL (SELECT …)}, a tuple against a subquery of as many columns — as {@code ANY(…)} or
 * {@code ALL(…)} around the re-print. A join's ON condition is held to the same rule. Every cell is live-verified.
 */
public class SubqueryComparisonFamilyTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE ft (d DATE, n NUMBER, s VARCHAR, b BOOLEAN, tm TIME, ts TIMESTAMP_NTZ,"
            + " bi BINARY, o OBJECT, a ARRAY, f FLOAT)");
        engine.execute("INSERT INTO ft SELECT '2024-01-01'::DATE, 1, 'x', TRUE, '10:00:00'::TIME,"
            + " '2024-01-01 00:00:00'::TIMESTAMP_NTZ, TO_BINARY('00', 'HEX'), OBJECT_CONSTRUCT('k', 1),"
            + " ARRAY_CONSTRUCT(1), 1.5");
        engine.execute("CREATE OR REPLACE TABLE re (a INT)");
        engine.execute("CREATE OR REPLACE TABLE fte (d DATE)");
        engine.execute("CREATE OR REPLACE TABLE fe (d DATE, n NUMBER)");
    }


    private static String convert(final String parameter, final String offered, final String expected) {
        return ERROR + "Can not convert parameter '" + parameter + "' of type [" + offered + "] into expected type ["
            + expected + "]";
    }

    @Test
    public void aScalarSubqueryIsNamedAsItsPlanReprintsIt() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM re WHERE a = (SELECT d FROM fte)",
                convert("(SELECT FTE.D AS \"D\" FROM FTE AS FTE)", "DATE", "NUMBER(38,0)")},
            {"SELECT n = (SELECT MAX(d) FROM ft) FROM ft",
                convert("(SELECT MAX(FT.D) AS \"MAX(D)\" FROM FT AS FT)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM re WHERE a = (SELECT x.d FROM fte x)",
                convert("(SELECT X.D AS \"D\" FROM FTE AS X)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM re WHERE a = (SELECT d AS dd FROM fte)",
                convert("(SELECT FTE.D AS \"DD\" FROM FTE AS FTE)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM re WHERE (SELECT d FROM fte) = a", convert("RE.A", "NUMBER(38,0)", "DATE")},
            {"SELECT IFF(n = (SELECT b FROM ft), 'y', 'n') FROM ft", "y"},
        });
    }

    @Test
    public void anExistsIsNamedByItsOwnWord() {
        assertCells(engine, new String[][] {
            {"SELECT n = (EXISTS (SELECT 1)) FROM ft",
                convert("EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)", "BOOLEAN", "NUMBER(38,0)")},
            {"SELECT n = (EXISTS (SELECT 1 FROM ft)) FROM ft",
                convert("EXISTS(SELECT 1 AS \"1\" FROM FT AS FT)", "BOOLEAN", "NUMBER(38,0)")},
            {"SELECT n = (NOT EXISTS (SELECT 1)) FROM ft",
                convert("NOT EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)", "BOOLEAN", "NUMBER(38,0)")},
        });
    }

    @Test
    public void aMembershipTestAcrossFamiliesThatDoNotMeetIsRefused() {
        final String anyD = "ANY(SELECT FT.D AS \"D\" FROM FT AS FT)";
        final String allD = "ALL(SELECT FT.D AS \"D\" FROM FT AS FT)";
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE n = ANY (SELECT d FROM ft)", convert(anyD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d FROM ft)", convert(anyD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n NOT IN (SELECT d FROM ft)", convert(allD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n = ALL (SELECT d FROM ft)", convert(allD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n > ANY (SELECT d FROM ft)", convert(anyD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n <> ALL (SELECT d FROM ft)", convert(allD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n < SOME (SELECT d FROM ft)", convert(anyD, "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE d IN (SELECT n FROM ft)",
                convert("ANY(SELECT FT.N AS \"N\" FROM FT AS FT)", "NUMBER(38,0)", "DATE")},
            {"SELECT 1 FROM ft WHERE n = ANY (SELECT MAX(d) FROM fte)",
                convert("ANY(SELECT MAX(FTE.D) AS \"MAX(D)\" FROM FTE AS FTE)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d FROM fte UNION SELECT d FROM fte)",
                convert("ANY(SELECT SET_COMBINE(WB$0.D, WB$1.D) AS \"D\" FROM (SELECT OB$0.D AS \"D\" FROM (SELECT"
                    + " FTE.D AS \"D\" FROM FTE AS FTE) OB$0) WB$0 UNION (SELECT OB$1.D AS \"D\" FROM (SELECT FTE.D AS"
                    + " \"D\" FROM FTE AS FTE) OB$1) WB$1)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d FROM fte WHERE fte.d = ft.d)",
                convert("ANY(SELECT FTE.D AS \"D\" FROM FTE AS FTE WHERE FTE.D = CORRELATION(FT.D))", "DATE",
                    "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE 1 IN (SELECT d FROM fte)",
                convert("ANY(SELECT FTE.D AS \"D\" FROM FTE AS FTE)", "DATE", "NUMBER(1,0)")},
            {"SELECT 1 FROM ft WHERE n + 1 IN (SELECT d FROM fte)",
                convert("ANY(SELECT FTE.D AS \"D\" FROM FTE AS FTE)", "DATE", "NUMBER(38,0)")},
        });
    }

    @Test
    public void aValuesListOrATableFunctionIsReprintedFromThePlan() {
        final String valuesDate = "(VALUES (CAST('2024-01-01' AS DATE)))";
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE n IN (SELECT column1 FROM VALUES ('2024-01-01'::DATE))",
                convert("ANY(SELECT VALUES.COLUMN1 AS \"COLUMN1\" FROM " + valuesDate + " \"VALUES\")", "DATE",
                    "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n NOT IN (SELECT column1 FROM (VALUES ('2024-01-01'::DATE)))",
                convert("ALL(SELECT VALUES.COLUMN1 AS \"COLUMN1\" FROM " + valuesDate + " \"VALUES\")", "DATE",
                    "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n = (SELECT column1 FROM VALUES ('2024-01-01'::DATE))",
                convert("(SELECT VALUES.COLUMN1 AS \"COLUMN1\" FROM " + valuesDate + " \"VALUES\")", "DATE",
                    "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT c FROM VALUES ('2024-01-01'::DATE) AS t(c))",
                convert("ANY(SELECT T.C AS \"C\" FROM " + valuesDate + " T)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT v.column1 FROM VALUES ('2024-01-01'::DATE) v)",
                convert("ANY(SELECT V.COLUMN1 AS \"COLUMN1\" FROM " + valuesDate + " V)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE d IN (SELECT column1 FROM VALUES (1), (2.5))",
                convert("ANY(SELECT VALUES.COLUMN1 AS \"COLUMN1\" FROM (VALUES (1), (2.5)) \"VALUES\")",
                    "NUMBER(2,1)", "DATE")},
            {"SELECT 1 FROM ft WHERE d IN (SELECT column1 FROM VALUES (NULL), (1))",
                convert("ANY(SELECT VALUES.COLUMN1 AS \"COLUMN1\" FROM (VALUES (null), (1)) \"VALUES\")",
                    "NUMBER(1,0)", "DATE")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT f.value::DATE FROM"
                + " TABLE(FLATTEN(ARRAY_CONSTRUCT('2024-01-01'))) f)",
                convert("ANY(SELECT CAST(F.VALUE AS DATE) AS \"F.VALUE::DATE\" FROM TABLE (FLATTEN(1 =>"
                    + " ARRAY_CONSTRUCT('2024-01-01'))))", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT value::DATE FROM TABLE(FLATTEN(INPUT =>"
                + " ARRAY_CONSTRUCT('2024-01-01'))))",
                convert("ANY(SELECT CAST(FLATTEN.VALUE AS DATE) AS \"VALUE::DATE\" FROM TABLE (FLATTEN(INPUT =>"
                    + " ARRAY_CONSTRUCT('2024-01-01'))))", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT TO_DATE(f.value) FROM LATERAL"
                + " FLATTEN(ARRAY_CONSTRUCT('2024-01-01')) f)",
                convert("ANY(SELECT CAST(F.VALUE AS DATE) AS \"TO_DATE(F.VALUE)\" FROM TABLE (FLATTEN(1 =>"
                    + " ARRAY_CONSTRUCT('2024-01-01'))))", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT TO_DATE(s.value) FROM"
                + " TABLE(SPLIT_TO_TABLE('2024-01-01', ',')) s)",
                convert("ANY(SELECT CAST(S.VALUE AS DATE) AS \"TO_DATE(S.VALUE)\" FROM TABLE (SPLIT_TO_TABLE(1 =>"
                    + " '2024-01-01', 2 => ',')))", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft WHERE d IN (SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 3)))",
                convert("ANY(SELECT SEQ4() AS \"SEQ4()\" FROM TABLE (GENERATOR(ROWCOUNT => 3)))", "NUMBER(10,0)",
                    "DATE")},
            {"SELECT 1 FROM ft WHERE d IN (SELECT f.index FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1))) f)",
                convert("ANY(SELECT F.INDEX AS \"INDEX\" FROM TABLE (FLATTEN(1 => ARRAY_CONSTRUCT(1))))",
                    "NUMBER(38,0)", "DATE")},
        });
    }

    @Test
    public void everyFamilyThatDoesNotMeetIsNamed() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE tm IN (SELECT ts FROM ft)",
                ERROR + "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT 1 FROM ft WHERE ts IN (SELECT tm FROM ft)",
                ERROR + "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT 1 FROM ft WHERE bi IN (SELECT s FROM ft)",
                convert("ANY(SELECT FT.S AS \"S\" FROM FT AS FT)", "VARCHAR(16777216)", "BINARY(8388608)")},
            {"SELECT 1 FROM ft WHERE s IN (SELECT bi FROM ft)",
                convert("ANY(SELECT FT.BI AS \"BI\" FROM FT AS FT)", "BINARY(8388608)", "VARCHAR(16777216)")},
            {"SELECT 1 FROM ft WHERE o IN (SELECT s FROM ft)",
                convert("ANY(SELECT FT.S AS \"S\" FROM FT AS FT)", "VARCHAR(16777216)", "OBJECT")},
            {"SELECT 1 FROM ft WHERE a IN (SELECT n FROM ft)",
                convert("ANY(SELECT FT.N AS \"N\" FROM FT AS FT)", "NUMBER(38,0)", "ARRAY")},
            {"SELECT 1 FROM ft WHERE f IN (SELECT d FROM ft)",
                convert("ANY(SELECT FT.D AS \"D\" FROM FT AS FT)", "DATE", "FLOAT")},
            {"SELECT 1 FROM ft WHERE n = ANY (SELECT tm FROM ft)",
                convert("ANY(SELECT FT.TM AS \"TM\" FROM FT AS FT)", "TIME(9)", "NUMBER(38,0)")},
        });
    }

    @Test
    public void familiesThatMeetAreAnswered() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE n IN (SELECT NULL)", "no row"},
            {"SELECT 1 FROM ft WHERE s IN (SELECT 1 = 1)", "no row"},
            {"SELECT 1 FROM ft WHERE (n, n) IN (SELECT n, n FROM ft)", "1"},
            {"SELECT 1 FROM ft WHERE 'x' IN (SELECT d FROM fte)", "no row"},
            {"SELECT 1 FROM ft WHERE NULL IN (SELECT d FROM fte)", "no row"},
        });
    }

    @Test
    public void theRefusalHoldsInEveryClauseAndRanksInWrittenOrder() {
        final String anyFte = convert("ANY(SELECT FTE.D AS \"D\" FROM FTE AS FTE)", "DATE", "NUMBER(38,0)");
        final String anyFt = convert("ANY(SELECT FT.D AS \"D\" FROM FT AS FT)", "DATE", "NUMBER(38,0)");
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE n = ANY (SELECT d FROM fte) OR TRUE", anyFte},
            {"SELECT CASE WHEN n IN (SELECT d FROM ft) THEN 1 END FROM ft", anyFt},
            {"SELECT n IN (SELECT d FROM ft) FROM ft", anyFt},
            {"SELECT 1 FROM ft HAVING MAX(n) IN (SELECT d FROM ft)", anyFt},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n IN (SELECT d FROM fte)", anyFte},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = ANY (SELECT d FROM fte)", anyFte},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d FROM fte) AND d = n", anyFte},
            {"SELECT 1 FROM ft WHERE d = n AND n IN (SELECT d FROM fte)", convert("FT.N", "NUMBER(38,0)", "DATE")},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d FROM fte) AND nosuch = 1",
                "SQL compilation error: error line 1 at position 52|invalid identifier 'NOSUCH'"},
            {"SELECT 1 FROM ft WHERE n IN (SELECT d, d FROM fte)",
                "SQL compilation error: error line 1 at position 25|Invalid argument types for function '=':"
                    + " (NUMBER(38,0), ROW(DATE, DATE))"},
        });
    }

    @Test
    public void aTupleMeetsTheSubqueryColumnByColumn() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft WHERE (n, n) IN (SELECT d, d FROM ft)",
                convert("ANY(SELECT FT.D AS \"D\", FT.D AS \"D\" FROM FT AS FT)", "ROW(DATE, DATE)",
                    "ROW(NUMBER(38,0), NUMBER(38,0))")},
            {"SELECT 1 FROM ft WHERE (n, d) IN (SELECT d, n FROM ft)",
                convert("ANY(SELECT FT.D AS \"D\", FT.N AS \"N\" FROM FT AS FT)", "ROW(DATE, NUMBER(38,0))",
                    "ROW(NUMBER(38,0), DATE)")},
            {"SELECT 1 FROM ft WHERE (n, n) IN (SELECT n, d FROM ft)",
                convert("ANY(SELECT FT.N AS \"N\", FT.D AS \"D\" FROM FT AS FT)", "ROW(NUMBER(38,0), DATE)",
                    "ROW(NUMBER(38,0), NUMBER(38,0))")},
            {"SELECT 1 FROM ft WHERE (n, n) NOT IN (SELECT d, d FROM ft)",
                convert("ALL(SELECT FT.D AS \"D\", FT.D AS \"D\" FROM FT AS FT)", "ROW(DATE, DATE)",
                    "ROW(NUMBER(38,0), NUMBER(38,0))")},
            {"SELECT 1 FROM ft WHERE (1, 2) IN (SELECT d, d FROM ft)",
                convert("ANY(SELECT FT.D AS \"D\", FT.D AS \"D\" FROM FT AS FT)", "ROW(DATE, DATE)",
                    "ROW(NUMBER(1,0), NUMBER(1,0))")},
            {"SELECT 1 FROM ft WHERE (tm, n) IN (SELECT ts, n FROM ft)",
                ERROR + "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
        });
    }

    @Test
    public void aJoinConditionIsHeldToTheFamilyRule() {
        final String bd = convert("B.D", "DATE", "NUMBER(38,0)");
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = b.d", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.d = b.n", convert("B.N", "NUMBER(38,0)", "DATE")},
            {"SELECT 1 FROM fe a JOIN fe b ON a.n = b.d", bd},
            {"SELECT 1 FROM ft a LEFT JOIN ft b ON a.n = b.d", bd},
            {"SELECT 1 FROM ft a FULL JOIN ft b ON a.n = b.d", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n < b.d", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n IN (b.d)", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n BETWEEN b.d AND b.d", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = b.n OR a.d = b.n", convert("B.N", "NUMBER(38,0)", "DATE")},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = b.d JOIN ft c ON c.d = a.n", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = COALESCE(b.d, b.d)",
                convert("IFNULL(B.D, B.D)", "DATE", "NUMBER(38,0)")},
            {"SELECT 1 FROM ft a JOIN ft b ON EQUAL_NULL(a.n, b.d)", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n IS DISTINCT FROM b.d", bd},
            {"SELECT 1 FROM ft a JOIN ft b ON a.b = b.n", "1"},
            {"SELECT 1 FROM ft a JOIN ft b ON UPPER(a.o) = b.s",
                "SQL compilation error: error line 1 at position 32|Invalid argument types for function 'UPPER':"
                    + " (OBJECT)"},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = ABS(b.d)",
                "SQL compilation error: error line 1 at position 38|Invalid argument types for function 'ABS':"
                    + " (DATE)"},
            {"SELECT 1 FROM ft a JOIN ft b ON a.n = b.d WHERE nosuch = 1",
                "SQL compilation error: error line 1 at position 48|invalid identifier 'NOSUCH'"},
        });
    }
}
