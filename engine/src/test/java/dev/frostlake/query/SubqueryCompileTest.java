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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subquery is compiled with the statement it sits in, before any row reaches it, so a query over an empty
 * table refuses what a full one would. Its names are judged in its own scope with the query around it in
 * scope, the alias of a relation hiding that relation's name, after the names and function names of the
 * query around it and ahead of what that query's own rules say about their meaning. A subquery whose table
 * function reads the outer row answers it. Every cell is live-verified.
 */
public class SubqueryCompileTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE DATABASE SUBQUERY_COMPILE_DB");
        engine.execute("CREATE OR REPLACE TABLE SUBQUERY_COMPILE_DB.PUBLIC.T (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE SUBQUERY_COMPILE_DB.PUBLIC.FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO SUBQUERY_COMPILE_DB.PUBLIC.FULL_T VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE TABLE SUBQUERY_COMPILE_DB.PUBLIC.EMPTY_S (a INT, b INT)");
    }

    @AfterEach
    public void dropTables() {
        engine.execute("DROP DATABASE IF EXISTS SUBQUERY_COMPILE_DB");
    }

    /** The statement's answer: its rows, a comma between cells and a bar between rows, or its refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 0; i < row.getValues().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(row.getValue(i));
                }
            }
            return out.toString();
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
    public void aSubqueryInEveryClauseIsCompiledOverAnEmptyTable() {
        assertEquals(at(15, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(at(35, "invalid identifier 'NOSUCH'"), answer("SELECT a FROM T WHERE a IN (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(57, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T WHERE EXISTS (SELECT 1 FROM FULL_T WHERE nosuch = 1)"));
        assertEquals(at(38, "invalid identifier 'NOSUCH'"), answer("SELECT a FROM T WHERE a = ANY (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(33, "invalid identifier 'NOSUCH'"), answer("SELECT a FROM T ORDER BY (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(46, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T GROUP BY a HAVING a > (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(36, "invalid identifier 'NOSUCH'"), answer("SELECT a FROM T QUALIFY a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(54, "invalid identifier 'NOSUCH'"),
            answer("SELECT T.a FROM T LEFT JOIN FULL_T f ON f.a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(68, "invalid identifier 'NOSUCH'"),
            answer("SELECT T.a FROM T LEFT JOIN FULL_T f ON f.a = T.a AND f.b = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(33, "invalid identifier 'NOSUCH'"),
            answer("SELECT * FROM T, LATERAL (SELECT nosuch FROM FULL_T WHERE FULL_T.a = T.a) l"));
        assertEquals(at(15, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT nosuch FROM FULL_T) FROM T LIMIT 0"));
        assertEquals(at(15, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT nosuch FROM FULL_T) FROM T WHERE FALSE"));
        assertEquals(at(34, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T WHERE a = (SELECT nosuch FROM FULL_T) LIMIT 0"));
        assertEquals(at(40, "invalid identifier 'NOSUCH'"),
            answer("SELECT (SELECT SUM(a) FROM FULL_T WHERE nosuch = 1) FROM T WHERE FALSE"));
        assertEquals(at(19, "invalid identifier 'NOSUCH'"), answer("SELECT 1 + (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(at(30, "invalid identifier 'NOSUCH'"), answer("SELECT a FROM T WHERE (SELECT nosuch FROM FULL_T) IS NULL"));
        assertEquals(at(36, "invalid identifier 'NOSUCH'"),
            answer("SELECT CASE WHEN a = 1 THEN (SELECT nosuch FROM FULL_T) END FROM T"));
        assertEquals(at(30, "invalid identifier 'NOSUCH'"),
            answer("SELECT * FROM (SELECT (SELECT nosuch FROM FULL_T) AS x FROM T)"));
        assertEquals(at(41, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T UNION ALL SELECT (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(at(46, "invalid identifier 'NOSUCH'"),
            answer("CREATE OR REPLACE VIEW vsub AS SELECT (SELECT nosuch FROM FULL_T) AS x FROM T"));
        assertEquals(at(29, "invalid identifier 'NOSUCH'"), answer("INSERT INTO T SELECT (SELECT nosuch FROM FULL_T), 1 FROM T"));
        assertEquals(at(48, "invalid identifier 'NOSUCH'"),
            answer("WITH c AS (SELECT a FROM FULL_T) SELECT (SELECT nosuch FROM c) FROM T"));
        assertEquals(at(68, "invalid identifier 'NOSUCH'"),
            answer("WITH c AS (SELECT a FROM FULL_T) SELECT a FROM T WHERE a IN (SELECT nosuch FROM c)"));
        assertEquals(hinted(unpositioned("Object 'NOSUCH_TABLE' does not exist or not authorized.")),
            answer("SELECT (SELECT a FROM NOSUCH_TABLE) FROM T"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("SELECT (SELECT nosuchfn(a) FROM FULL_T) FROM T"));
    }

    /** Every clause of the subquery itself, a subquery nested in it, and a subquery with no FROM. */
    @Test
    public void everyClauseOfTheSubqueryIsCompiled() {
        assertEquals(at(56, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T WHERE a = (SELECT a FROM FULL_T QUALIFY nosuch = 1)"));
        assertEquals(at(57, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T WHERE a = (SELECT a FROM FULL_T ORDER BY nosuch LIMIT 1)"));
        assertEquals(at(62, "invalid identifier 'NOSUCH'"),
            answer("SELECT a FROM T WHERE a IN (SELECT a FROM FULL_T UNION SELECT nosuch FROM FULL_T)"));
        assertEquals(at(35, "invalid identifier 'NOSUCH'"),
            answer("SELECT (SELECT MAX(x) FROM (SELECT nosuch AS x FROM FULL_T)) FROM T"));
        assertEquals(at(38, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT 1 FROM FULL_T GROUP BY nosuch) FROM T"));
        assertEquals(at(27, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT 1 + (SELECT nosuch FROM FULL_T)) FROM FULL_T"));
        assertEquals(at(36, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT a FROM EMPTY_S WHERE nosuch = 1)"));
        assertEquals(at(27, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT 1 + (SELECT nosuch FROM EMPTY_S))"));
        assertEquals("", answer("SELECT a FROM T WHERE a = (SELECT 1/0)"));
        assertEquals("", answer("SELECT (SELECT 'x'::INT) FROM T"));
        assertEquals("", answer("SELECT a FROM T WHERE a = (SELECT a FROM FULL_T WHERE b = 'x'::INT)"));
    }

    /** The query around a subquery is in its scope, under the names its FROM gives each relation. */
    @Test
    public void theQueryAroundASubqueryIsInItsScope() {
        assertEquals(at(46, "invalid identifier 'T.NOSUCH'"),
            answer("SELECT (SELECT a FROM FULL_T WHERE FULL_T.b = T.nosuch) FROM T"));
        assertEquals(at(58, "invalid identifier 'NOSUCHOUTER'"),
            answer("SELECT a FROM T WHERE a = (SELECT a FROM FULL_T WHERE b = nosuchouter)"));
        assertEquals(at(34, "invalid identifier 'Z.NOSUCH'"), answer("SELECT a FROM T WHERE a = (SELECT z.nosuch FROM FULL_T z)"));
        assertEquals(at(68, "invalid identifier 'T.NOSUCH'"),
            answer("SELECT a FROM T WHERE a IN (SELECT a FROM EMPTY_S WHERE EMPTY_S.b = T.nosuch)"));
        assertEquals(at(53, "invalid identifier 'X.NOSUCH'"),
            answer("SELECT (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = x.nosuch) FROM T x"));
        assertEquals(at(53, "invalid identifier 'T.A'"),
            answer("SELECT (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = T.a) FROM T x"));
        assertEquals(at(50, "invalid identifier 'B2'"), answer("SELECT (SELECT COUNT(*) FROM FULL_T f WHERE f.a = b2) FROM T"));
        assertEquals(at(71, "invalid identifier 'T1.NOSUCH'"),
            answer("SELECT a FROM T t1 WHERE EXISTS (SELECT 1 FROM FULL_T WHERE FULL_T.a = t1.nosuch)"));
        assertEquals(at(38, "invalid identifier 'T1.NOSUCH'"), answer("SELECT a FROM T t1 WHERE a IN (SELECT t1.nosuch FROM FULL_T)"));
        assertEquals(at(91, "invalid identifier 'T.NOSUCH'"),
            answer("SELECT a FROM T GROUP BY a HAVING COUNT(*) > (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = T.nosuch)"));
        assertEquals(at(23, "invalid identifier 'X'"), answer("SELECT a AS x, (SELECT x) FROM T"));
        assertEquals(at(61, "invalid identifier 'X'"), answer("SELECT a AS x, (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = x) FROM T"));
        assertEquals(at(18, "invalid identifier 'X'"), answer("SELECT a, (SELECT x FROM FULL_T) FROM T"));
        assertEquals(at(15, "invalid identifier 'T.NOSUCH'"), answer("SELECT (SELECT T.nosuch) FROM T"));
        assertEquals(at(15, "invalid identifier 'NOSUCH'"), answer("SELECT (SELECT nosuch) FROM T"));
        assertEquals(at(15, "invalid identifier 'T.A'"), answer("SELECT (SELECT T.a) FROM T x"));
        assertEquals("", answer("SELECT (SELECT a FROM FULL_T WHERE FULL_T.b = T.b) FROM T"));
        assertEquals("", answer("SELECT (SELECT T.a) FROM T"));
        assertEquals("", answer("SELECT (SELECT a) FROM T"));
        assertEquals("", answer("SELECT (SELECT x.b FROM FULL_T x WHERE x.a = T.a LIMIT 1) FROM T"));
        assertEquals("", answer("SELECT a, (SELECT COUNT(*) FROM FULL_T WHERE FULL_T.a = a) FROM T"));
        assertEquals("", answer("SELECT a FROM T WHERE a IN (SELECT T.a FROM FULL_T)"));
        assertEquals("", answer("SELECT (SELECT a FROM FULL_T x WHERE x.a = T.a) FROM T"));
        assertEquals("", answer("SELECT a FROM T x WHERE EXISTS (SELECT 1 FROM FULL_T WHERE FULL_T.a = x.a)"));
    }

    /** With two faults: the query around's names and function names first, then each subquery whole, then the rest. */
    @Test
    public void theQueryAroundIsJudgedFirst() {
        assertEquals(at(29, "invalid identifier 'NOSUCHCOL'"), answer("SELECT (SELECT nosuchfn(1)), nosuchcol FROM T"));
        assertEquals(at(7, "invalid identifier 'NOSUCHCOL'"), answer("SELECT nosuchcol, (SELECT nosuchfn(1)) FROM T"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("SELECT nosuchfn(1), (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(at(36, "invalid identifier 'NOSUCHCOL2'"), answer("SELECT (SELECT nosuch FROM FULL_T), nosuchcol2 FROM T"));
        assertEquals(at(22, "invalid identifier 'NOSUCHCOL2'"),
            answer("SELECT a FROM T WHERE nosuchcol2 = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(30, "invalid identifier 'NOSUCH'"), answer("SELECT TO_DATE(TRUE), (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."), answer("SELECT (SELECT TO_DATE(TRUE)), nosuchfn(1) FROM T"));
        assertEquals(at(15, "invalid identifier 'NOSUCH1'"),
            answer("SELECT (SELECT nosuch1 FROM FULL_T) FROM T WHERE a = (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(at(49, "invalid identifier 'NOSUCH2'"),
            answer("SELECT (SELECT nosuch1 FROM FULL_T) FROM T WHERE nosuch2 = 1"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."),
            answer("SELECT (SELECT nosuchfn(1)), (SELECT nosuch FROM FULL_T) FROM T"));
        assertEquals(at(62, "invalid identifier 'NOSUCH2'"),
            answer("SELECT (SELECT (SELECT nosuch1 FROM FULL_T) FROM FULL_T WHERE nosuch2 = 1) FROM T"));
        assertEquals(at(34, "invalid identifier 'NOSUCH1'"),
            answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) ORDER BY (SELECT nosuch2 FROM FULL_T)"));
        assertEquals(at(42, "invalid identifier 'NOSUCH'"), answer("SELECT SUM(a), b FROM T WHERE a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(60, "invalid identifier 'NOSUCH2'"),
            answer("SELECT a FROM T WHERE a IN (SELECT nosuch1 FROM FULL_T) AND nosuch2 = 1"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."),
            answer("SELECT a FROM T WHERE EXISTS (SELECT nosuch1 FROM FULL_T) AND nosuchfn(1) = 1"));
        assertEquals(at(15, "invalid identifier 'T.NOSUCH1'"),
            answer("SELECT (SELECT T.nosuch1 FROM FULL_T WHERE nosuch2 = 1) FROM T"));
    }

    /** A table function in a subquery reads the row of the query around it, and a name it cannot resolve is refused. */
    @Test
    public void aTableFunctionInASubqueryReadsTheOuterRow() {
        assertEquals("2", answer("SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.a, 5)))) FROM FULL_T"));
        assertEquals("1", answer("SELECT a FROM FULL_T WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.a))))"));
        assertEquals("1", answer("SELECT a FROM FULL_T WHERE a IN (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.a))))"));
        assertEquals("1", answer("SELECT (SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(FULL_T.a::VARCHAR, ','))) FROM FULL_T"));
        final String refused = answer("SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.nosuch, 5)))) FROM EMPTY_S E");
        assertTrue(refused.contains("invalid identifier 'E.NOSUCH'"), refused);
    }
}
