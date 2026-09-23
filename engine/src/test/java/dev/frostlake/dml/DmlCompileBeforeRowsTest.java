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
package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An UPDATE or a DELETE compiles whole before it reads a row, so an empty table refuses what a full one
 * would: every subquery first, each whole and with the statement's relations in its scope, then the SET
 * targets, then the names of the SET values, the ON conditions of its FROM or USING joins and the WHERE,
 * then their function names and argument types, then its window and aggregate calls. A FROM or USING
 * source stands in scope beside the target, an alias hiding a relation's name, and a join's ON reads the
 * sources alone. Every cell is live-verified.
 */
public class DmlCompileBeforeRowsTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE DATABASE DML_COMPILE_DB");
        engine.execute("CREATE OR REPLACE TABLE DML_COMPILE_DB.PUBLIC.T (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE DML_COMPILE_DB.PUBLIC.FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO DML_COMPILE_DB.PUBLIC.FULL_T VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE TABLE DML_COMPILE_DB.PUBLIC.EMPTY_S (a INT, b INT)");
    }

    @AfterEach
    public void dropTables() {
        engine.execute("DROP DATABASE IF EXISTS DML_COMPILE_DB");
    }

    /** The statement's refusal on one line, or ACCEPTED when it runs. */
    private String answer(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    private static String unpositioned(final String detail) {
        return "SQL compilation error:|" + detail;
    }

    @Test
    public void aSubqueryCompilesFirst() {
        assertEquals(at(25, "invalid identifier 'NOSUCHCOL'"), answer("UPDATE T SET a = (SELECT nosuchcol FROM FULL_T)"));
        assertEquals(at(25, "invalid identifier 'NOSUCHCOL'"),
            answer("UPDATE T SET a = (SELECT nosuchcol FROM FULL_T) WHERE nosuchcol2 = 1"));
        assertEquals(at(33, "invalid identifier 'NOSUCH'"), answer("DELETE FROM T WHERE a IN (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(29, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = (SELECT MAX(nosuch) FROM FULL_T)"));
        assertEquals(at(56, "invalid identifier 'T.NOSUCH'"),
            answer("UPDATE T SET a = (SELECT a FROM FULL_T WHERE FULL_T.b = T.nosuch)"));
        assertEquals(at(56, "invalid identifier 'NOSUCH'"),
            answer("UPDATE T SET a = 1 WHERE nosuchcol2 = 1 AND a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("UPDATE T SET a = (SELECT nosuchfn(1)) WHERE nosuchcol2 = 1"));
        assertEquals(at(42, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = nosuchfn(1), b = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(63, "invalid identifier 'T.NOSUCH'"),
            answer("DELETE FROM T WHERE a = (SELECT a FROM FULL_T WHERE FULL_T.b = T.nosuch)"));
        assertEquals(at(52, "invalid identifier 'NOSUCH'"),
            answer("DELETE FROM T WHERE nosuchcol2 = 1 AND a IN (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(41, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = nosuchcol2, b = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(36, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET nosuchtarget = (SELECT nosuch FROM FULL_T)"));
        assertEquals(hinted(unpositioned("Object 'NOSUCH_TABLE' does not exist or not authorized.")),
            answer("UPDATE T SET a = (SELECT a FROM NOSUCH_TABLE)"));
        assertEquals(at(25, "invalid identifier 'NOSUCH1'"),
            answer("UPDATE T SET a = (SELECT nosuch1 FROM FULL_T), b = (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(at(25, "invalid identifier 'NOSUCH1'"),
            answer("UPDATE T SET a = (SELECT nosuch1 FROM FULL_T) WHERE b = (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(at(37, "invalid identifier 'NOSUCH1'"),
            answer("UPDATE T SET a = 1 WHERE a = (SELECT nosuch1 FROM FULL_T) AND b = (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."),
            answer("UPDATE T SET a = (SELECT nosuchfn(1)), b = (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(at(79, "invalid identifier 'NOSUCH2'"),
            answer("DELETE FROM T WHERE a = (SELECT (SELECT nosuch1 FROM FULL_T) FROM FULL_T WHERE nosuch2 = 1)"));
        assertEquals(at(56, "invalid identifier 'T.NOSUCH'"),
            answer("UPDATE T SET a = (SELECT a FROM FULL_T WHERE FULL_T.b = T.nosuch) WHERE nosuchcol2 = 1"));
        assertEquals(at(42, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = SUM(b) WHERE a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(44, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = TO_DATE(TRUE), b = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(25, "invalid identifier 'NOSUCH'"),
            answer("UPDATE T SET a = (SELECT nosuch FROM FULL_T) FROM FULL_T f WHERE f.nosuch2 = 1"));
        assertEquals(at(61, "invalid identifier 'NOSUCH'"),
            answer("UPDATE T SET a = f.nosuch2 FROM FULL_T f WHERE T.a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(68, "invalid identifier 'NOSUCH'"),
            answer("DELETE FROM T USING FULL_T f WHERE f.nosuch2 = 1 AND T.a IN (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(71, "invalid identifier 'T.NOSUCH'"),
            answer("UPDATE T SET a = 1 WHERE EXISTS (SELECT 1 FROM FULL_T WHERE FULL_T.a = T.nosuch)"));
        assertEquals(at(44, "invalid identifier 'NOSUCH'"), answer("DELETE FROM T WHERE a = (SELECT 1 + (SELECT nosuch FROM FULL_T))"));
        assertEquals(at(65, "invalid identifier 'T.A'"),
            answer("UPDATE T x SET a = (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = T.a)"));
        assertEquals(at(25, "invalid identifier 'T.NOSUCH'"), answer("UPDATE T SET a = (SELECT T.nosuch)"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = (SELECT 'x'::INT)"));
        assertEquals("ACCEPTED", answer("DELETE FROM T WHERE a = (SELECT a FROM FULL_T WHERE FULL_T.b = T.b)"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = 1 WHERE a = (SELECT a FROM FULL_T WHERE FULL_T.b = b)"));
        assertEquals("ACCEPTED", answer("DELETE FROM T WHERE a = (SELECT MAX(b) FROM FULL_T WHERE FULL_T.a = T.a)"));
    }

    @Test
    public void aFromOrUsingSourceIsInScopeBeforeAnyRow() {
        assertEquals(at(17, "invalid identifier 'FULL_T.NOSUCH'"), answer("UPDATE T SET a = FULL_T.nosuch FROM FULL_T"));
        assertEquals(at(17, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = nosuch FROM FULL_T"));
        assertEquals(unpositioned("ambiguous column name 'B'"), answer("UPDATE T SET a = b FROM FULL_T"));
        assertEquals(at(37, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = 1 FROM FULL_T WHERE nosuch = 1"));
        assertEquals(at(39, "invalid identifier 'F.NOSUCH'"), answer("UPDATE T SET a = 1 FROM FULL_T f WHERE f.nosuch = 1"));
        assertEquals(at(55, "invalid identifier 'S.NOSUCH'"),
            answer("UPDATE T SET a = 1 FROM (SELECT a FROM FULL_T) s WHERE s.nosuch = 1"));
        assertEquals(at(41, "invalid identifier 'T.NOSUCH'"), answer("DELETE FROM T USING FULL_T f WHERE f.a = T.nosuch"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("UPDATE T SET a = nosuchfn(1) FROM FULL_T"));
        assertEquals(at(17, "invalid identifier 'FULL_T.NOSUCH'"),
            answer("UPDATE T SET a = FULL_T.nosuch FROM FULL_T WHERE nosuchfn(1) = 1"));
        assertEquals(unpositioned("ambiguous column name 'A'"), answer("UPDATE T SET a = 1 FROM FULL_T WHERE a = 1"));
        assertEquals(at(52, "invalid identifier 'NOSUCH'"),
            answer("DELETE FROM T USING FULL_T WHERE T.a = FULL_T.a AND nosuch = 1"));
        assertEquals(at(60, "invalid identifier 'F2.NOSUCH'"),
            answer("UPDATE T SET a = 1 FROM FULL_T JOIN FULL_T f2 ON FULL_T.a = f2.nosuch"));
        assertEquals(at(85, "invalid identifier 'F.NOSUCH'"),
            answer("UPDATE T SET a = 1 FROM FULL_T, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1)) f WHERE f.nosuch = 1"));
        assertEquals(at(51, "invalid identifier 'NOSUCH'"),
            answer("UPDATE T SET a = 1 FROM FULL_T WHERE T.a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(unpositioned("ambiguous column name 'A'"), answer("UPDATE T SET a = a FROM FULL_T"));
        assertEquals(at(44, "invalid identifier 'T.NOSUCH'"), answer("UPDATE T SET a = FULL_T.a FROM FULL_T WHERE T.nosuch = 1"));
        assertEquals(at(36, "invalid identifier 'S.NOSUCH'"), answer("DELETE FROM T USING EMPTY_S s WHERE s.nosuch = T.a"));
        assertEquals(at(17, "invalid identifier 'F.NOSUCH1'"), answer("UPDATE T SET a = f.nosuch1 FROM FULL_T f WHERE f.nosuch2 = 1"));
        assertEquals(at(49, "invalid identifier 'F.NOSUCH2'"), answer("UPDATE T SET a = nosuchfn(1) FROM FULL_T f WHERE f.nosuch2 = 1"));
        assertEquals(at(13, "invalid identifier 'NOSUCHTARGET'"), answer("UPDATE T SET nosuchtarget = f.nosuch FROM FULL_T f"));
        assertEquals(hinted(unpositioned("Object 'NOSUCH_TABLE' does not exist or not authorized.")),
            answer("UPDATE T SET a = f.a FROM FULL_T f JOIN NOSUCH_TABLE n ON 1 = 1"));
        assertEquals(at(17, "invalid identifier 'F.NOSUCH1'"),
            answer("UPDATE T SET a = f.nosuch1 FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a"));
        assertEquals(at(51, "invalid identifier 'G.NOSUCH'"), answer("DELETE FROM T USING FULL_T f, FULL_T g WHERE f.a = g.nosuch"));
        assertEquals(at(17, "invalid identifier 'F.NOSUCH'"), answer("UPDATE T SET a = f.nosuch FROM FULL_T AS f"));
        assertEquals(at(17, "invalid identifier 'FULL_T.A'"), answer("UPDATE T SET a = FULL_T.a FROM FULL_T f"));
        assertEquals(at(17, "invalid identifier 'X.NOSUCH'"),
            answer("UPDATE T SET a = x.nosuch FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1))) x"));
        assertEquals(at(39, "invalid identifier 'NOSUCH2'"), answer("UPDATE T SET a = s.nosuch FROM (SELECT nosuch2 FROM FULL_T) s"));
        assertEquals(at(41, "invalid identifier 'NOSUCH'"),
            answer("UPDATE T SET a = f.a FROM FULL_T f WHERE nosuch = 1 AND SUM(f.a) > 0"));
        assertEquals(at(17, "invalid identifier 'T.NOSUCH'"), answer("UPDATE T SET a = T.nosuch FROM FULL_T f"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("DELETE FROM T USING FULL_T f WHERE nosuchfn(f.a) = 1"));
        assertEquals(at(17, "invalid identifier 'V.NOSUCH'"), answer("UPDATE T SET a = v.nosuch FROM (VALUES (1), (2)) v"));
        assertEquals(at(61, "invalid identifier 'F.NOSUCH'"),
            answer("UPDATE T SET a = f.a FROM FULL_T f WHERE f.a = T.a AND T.b = f.nosuch"));
        assertEquals(at(26, "invalid identifier 'F.NOSUCH'"), answer("UPDATE T SET a = f.a, b = f.nosuch FROM FULL_T f"));
        assertEquals(at(41, "invalid identifier 'T.A'"), answer("UPDATE T x SET a = 1 FROM FULL_T f WHERE T.a = f.a"));
        assertEquals(at(37, "invalid identifier 'T.A'"), answer("DELETE FROM T x USING FULL_T f WHERE T.a = f.a"));
        assertEquals(at(56, "invalid identifier 'T.A'"), answer("UPDATE T SET a = 1 FROM FULL_T f JOIN FULL_T g ON g.a = T.a"));
        assertEquals(at(50, "invalid identifier 'G.NOSUCH'"),
            answer("UPDATE T SET a = 1 FROM FULL_T f JOIN FULL_T g ON g.nosuch = f.a WHERE T.nosuch2 = 1"));
        assertEquals(at(76, "invalid identifier 'NOSUCH2'"),
            answer("UPDATE T SET a = 1 FROM FULL_T f JOIN FULL_T g ON nosuchfn(g.a) = f.a WHERE nosuch2 = 1"));
        assertEquals(at(93, "invalid identifier 'F.NOSUCH'"),
            answer("DELETE FROM T USING FULL_T f WHERE T.a = f.a AND EXISTS (SELECT 1 FROM EMPTY_S s WHERE s.a = f.nosuch)"));
        assertEquals(at(54, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = f.a FROM (SELECT a FROM FULL_T WHERE nosuch = 1) f"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = FULL_T.a FROM FULL_T WHERE T.a = FULL_T.a"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = s.x FROM (SELECT 1 AS x) s"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = f.value FROM FULL_T, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(1)) f"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = FULL_T.a FROM FULL_T WHERE T.a = FULL_T.a AND FULL_T.b = 'x'::INT"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = 'abc' FROM FULL_T f"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = x.value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1))) x"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = v.column1 FROM (VALUES (1), (2)) v"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = f.a FROM FULL_T f WHERE f.a = T.a AND f.b = 'x'"));
        assertEquals("ACCEPTED", answer(
            "UPDATE T SET a = 1 FROM FULL_T WHERE T.a = FULL_T.a AND FULL_T.b = (SELECT MAX(b) FROM EMPTY_S WHERE EMPTY_S.a = T.a)"));
    }

    @Test
    public void argumentTypesAreJudgedBeforeAnyRow() {
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("UPDATE T SET a = 1 WHERE TO_DATE(TRUE) IS NULL"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("DELETE FROM T WHERE TO_DATE(TRUE) IS NULL"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"), answer("UPDATE T SET a = TO_DATE(TRUE)"));
        assertEquals(at(17, "Invalid argument types for function 'UPPER': (ARRAY)"), answer("UPDATE T SET a = UPPER(ARRAY_CONSTRUCT(1))"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("UPDATE T SET a = f.b FROM FULL_T f WHERE TO_DATE(TRUE) IS NULL"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = 'abc'"));
    }

    @Test
    public void windowAndAggregateCallsAreRefusedLast() {
        final String grouped = unpositioned("[PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression");
        final String analytic = "Analytic function not allowed as target of UPDATE.";
        assertEquals(grouped, answer("UPDATE T SET a = SUM(b)"));
        assertEquals(grouped, answer("UPDATE T SET a = COUNT(*)"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(b) + 1"));
        assertEquals(grouped, answer("UPDATE FULL_T SET a = SUM(b)"));
        assertEquals(grouped, answer("UPDATE FULL_T SET a = COUNT(*)"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(b), b = COUNT(*)"));
        assertEquals(grouped, answer("UPDATE T x SET a = SUM(x.b)"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(b) WHERE SUM(a) > 0"));
        assertEquals(grouped, answer("UPDATE T SET a = ARRAY_AGG(b)[0]"));
        assertEquals(grouped, answer("UPDATE T SET a = 'x' || SUM(b)"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(f.a) FROM FULL_T f"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(s.b) FROM EMPTY_S s"));
        assertEquals(grouped, answer("UPDATE T SET a = SUM(DISTINCT b)"));
        assertEquals(grouped, answer("UPDATE T SET a = LISTAGG(b)"));
        assertEquals(grouped, answer("UPDATE T SET a = 1, b = SUM(b)"));
        assertEquals(grouped, answer("UPDATE T SET a = ANY_VALUE(b)"));
        assertEquals(grouped, answer("UPDATE T SET a = ARRAY_SIZE(ARRAY_AGG(b))"));
        assertEquals(grouped, answer("UPDATE T SET a = CASE WHEN SUM(b) > 0 THEN 1 END"));
        assertEquals(grouped, answer("UPDATE T SET a = (SELECT 1) + SUM(b)"));
        assertEquals(unpositioned("GROUPING function without a GROUP BY."), answer("UPDATE T SET a = GROUPING(b)"));
        assertEquals(analytic, answer("UPDATE T SET a = ROW_NUMBER() OVER (ORDER BY b)"));
        assertEquals(analytic, answer("UPDATE T SET a = SUM(b) OVER ()"));
        assertEquals(analytic, answer("UPDATE T SET a = SUM(b), b = ROW_NUMBER() OVER (ORDER BY b)"));
        assertEquals(analytic, answer("UPDATE T SET a = ROW_NUMBER() OVER (ORDER BY b), b = SUM(b)"));
        assertEquals(analytic, answer("UPDATE T SET a = 1 WHERE ROW_NUMBER() OVER (ORDER BY b) = 1"));
        assertEquals(analytic, answer("UPDATE T SET a = COUNT(*) OVER ()"));
        assertEquals(analytic, answer("UPDATE T SET a = SUM(a) OVER (PARTITION BY b)"));
        assertEquals(unpositioned("Window function [ROW_NUMBER() OVER (ORDER BY T.B ASC NULLS LAST)] appears outside of SELECT,"
            + " QUALIFY, and ORDER BY clauses."), answer("DELETE FROM T WHERE ROW_NUMBER() OVER (ORDER BY b) = 1"));
        assertEquals(unpositioned("Window function [COUNT(*) OVER ()] appears outside of SELECT, QUALIFY, and ORDER BY clauses."),
            answer("DELETE FROM T WHERE a = 1 AND COUNT(*) OVER () > 0"));
        assertEquals(unpositioned("Invalid aggregate function in where clause [SUM(T.B)]"),
            answer("UPDATE T SET a = 1 FROM FULL_T WHERE SUM(T.b) > 0"));
        assertEquals(unpositioned("Invalid aggregate function in where clause [SUM(T.B)]"), answer("DELETE FROM T WHERE a = SUM(b)"));
        assertEquals(unpositioned("Invalid aggregate function in where clause [SUM(T.B)]"),
            answer("DELETE FROM T WHERE a = (SELECT MAX(b) FROM FULL_T) AND SUM(b) > 0"));
        assertEquals(at(30, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = SUM(b) WHERE nosuch = 1"));
        assertEquals(at(21, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = MAX(nosuch)"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("UPDATE T SET a = SUM(b) WHERE nosuchfn(1) = 1"));
        assertEquals(unpositioned("ambiguous column name 'B'"), answer("UPDATE T SET a = SUM(b) FROM FULL_T"));
        assertEquals(at(45, "invalid identifier 'NOSUCH'"), answer("UPDATE T SET a = ROW_NUMBER() OVER (ORDER BY nosuch)"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("UPDATE T SET a = SUM(b) WHERE TO_DATE(TRUE) IS NULL"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"), answer("UPDATE T SET a = SUM(TO_DATE(TRUE))"));
        assertEquals("ACCEPTED", answer("UPDATE T SET a = (SELECT SUM(b) FROM FULL_T)"));
    }
}
