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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript function's globals are kept for each place a statement calls it from: two select items of one
 * text, two arms of a UNION, two common table expressions, two scalar subqueries, two aggregates and an
 * INSERT's or an UPDATE's two items count apart, while the rows one place reads share its globals. A block's
 * own expressions are queries of their own, and a call written once in a SQL function's body is one place for
 * each call of the function; a read of a query's shape keeps nothing for its rows (live-verified).
 */
public class JavaScriptCallPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void itemsOfOneTextArePlacesOfTheirOwn() {
        try {
            assertAnswer(
                "Function CNT successfully created.",
                "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                    + "(globalThis.n || 0) + 1; return globalThis.n;'");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "1.0|1.0",
                "SELECT cnt(1), cnt(1)");
            assertAnswer(
                "1.0|1.0",
                "SELECT cnt(1) AS a, cnt(1) AS b");
            assertAnswer(
                "1.0|1.0|1.0",
                "SELECT cnt(1), cnt(1), cnt(1)");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT cnt(v), cnt(v) FROM ct ORDER BY 1");
            assertAnswer(
                "1.0 ;; 1.0",
                "SELECT * FROM (SELECT cnt(1) AS x UNION ALL SELECT cnt(1)) ORDER BY 1");
            assertAnswer(
                "1.0|1.0",
                "SELECT (SELECT cnt(1)), (SELECT cnt(1))");
            assertAnswer(
                "1.0|1.0",
                "WITH a AS (SELECT cnt(1) AS x), b AS (SELECT cnt(1) AS y) SELECT x, y FROM a, b");
            assertAnswer(
                "1.0 ;; 2.0 ;; 3.0",
                "SELECT cnt(1) FROM ct WHERE cnt(1) > 0 ORDER BY 1");
            assertAnswer(
                "[1.000000000000000e+00,1.000000000000000e+00]",
                "SELECT ARRAY_CONSTRUCT(cnt(1), cnt(1))");
            assertAnswer(
                "1.0|1.0",
                "SELECT cnt(1), cnt( 1 )");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT cnt(1), cnt(1) FROM ct ORDER BY 1");
            assertAnswer(
                "1.0|1.0",
                "SELECT cnt(1), cnt(2)");
            assertAnswer(
                "2.0",
                "SELECT cnt(1) + cnt(1)");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlw2(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlw(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS cnt(FLOAT)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void aBlocksExpressionsStartAfresh() {
        try {
            assertAnswer(
                "Function CNT successfully created.",
                "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                    + "(globalThis.n || 0) + 1; return globalThis.n;'");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || "
                    + "cnt(1)::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1|1",
                "EXECUTE IMMEDIATE $$DECLARE a FLOAT; b FLOAT; BEGIN a := cnt(1); b := cnt(1); RETURN a || '|' || "
                    + "b; END;$$");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO LET t FLOAT := "
                    + "cnt(1); s := s || t::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "yyy",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO IF (cnt(1) = 1) THEN "
                    + "s := s || 'y'; ELSE s := s || 'n'; END IF; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; t FLOAT; BEGIN FOR i IN 1 TO 3 DO SELECT "
                    + "cnt(1) INTO :t; s := s || t::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "2.0",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN RETURN cnt(1) + cnt(1); END;$$");
            assertAnswer(
                "1|1",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN RETURN cnt(1) || '|' || cnt(1); END;$$");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlw2(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlw(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS cnt(FLOAT)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void aSqlFunctionsBodyIsAPlaceForEachCall() {
        try {
            assertAnswer(
                "Function CNT successfully created.",
                "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                    + "(globalThis.n || 0) + 1; return globalThis.n;'");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "Function SQLW successfully created.",
                "CREATE OR REPLACE FUNCTION sqlw(x FLOAT) RETURNS FLOAT AS 'cnt(x)'");
            assertAnswer(
                "1.0 ;; 2.0 ;; 3.0",
                "SELECT sqlw(v) FROM ct ORDER BY 1");
            assertAnswer(
                "1.0|1.0",
                "SELECT sqlw(1), sqlw(1)");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT sqlw(v), cnt(v) FROM ct ORDER BY 1");
            assertAnswer(
                "2.0",
                "SELECT sqlw(1) + sqlw(1)");
            assertAnswer(
                "1.0|1.0",
                "SELECT sqlw(1), sqlw(2)");
            assertAnswer(
                "Function SQLQ successfully created.",
                "CREATE OR REPLACE FUNCTION sqlq(x FLOAT) RETURNS FLOAT AS 'SELECT cnt(x)'");
            assertAnswer(
                "1.0 ;; 2.0 ;; 3.0",
                "SELECT sqlq(v) FROM ct ORDER BY 1");
            assertAnswer(
                "Function SQLW2 successfully created.",
                "CREATE OR REPLACE FUNCTION sqlw2(x FLOAT) RETURNS FLOAT AS 'sqlw(x) + sqlw(x)'");
            assertAnswer(
                "2.0 ;; 4.0 ;; 6.0",
                "SELECT sqlw2(v) FROM ct ORDER BY 1");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlw2(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlw(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS cnt(FLOAT)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void proceduresAndGroupedItems() {
        try {
            assertAnswer(
                "Function CNT successfully created.",
                "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                    + "(globalThis.n || 0) + 1; return globalThis.n;'");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "Function PLOOP successfully created.",
                "CREATE OR REPLACE PROCEDURE ploop() RETURNS VARCHAR AS $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR "
                    + "i IN 1 TO 3 DO s := s || cnt(1)::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1,1,1,",
                "CALL ploop()");
            assertAnswer(
                "View JV successfully created.",
                "CREATE OR REPLACE VIEW jv AS SELECT cnt(1) AS x");
            assertAnswer(
                "1.0|1.0",
                "SELECT cnt(1), cnt(1) FROM ct WHERE v = 1");
            assertAnswer(
                "3.0|3.0",
                "SELECT MAX(cnt(v)), MAX(cnt(v)) FROM ct");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT cnt(1), cnt(1) FROM ct GROUP BY v ORDER BY 1");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlw2(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS sqlw(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS cnt(FLOAT)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void subqueriesDmlAndAggregates() {
        try {
            assertAnswer(
                "Function CNT successfully created.",
                "CREATE OR REPLACE FUNCTION cnt(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'globalThis.n = "
                    + "(globalThis.n || 0) + 1; return globalThis.n;'");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "1.0 ;; 2.0 ;; 3.0",
                "SELECT (SELECT cnt(v)) FROM ct ORDER BY 1");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT x, cnt(1) FROM (SELECT cnt(v) AS x FROM ct) ORDER BY 1");
            assertAnswer(
                "1.0 ;; 1.0 ;; 2.0 ;; 2.0 ;; 3.0 ;; 3.0",
                "SELECT * FROM (SELECT cnt(1) FROM ct UNION ALL SELECT cnt(1) FROM ct) ORDER BY 1");
            assertAnswer(
                "View JV successfully created.",
                "CREATE OR REPLACE VIEW jv AS SELECT cnt(1) AS x");
            assertAnswer(
                "1.0|1.0",
                "SELECT jv.x, cnt(1) FROM jv");
            assertAnswer(
                "Table CT2 successfully created.",
                "CREATE OR REPLACE TABLE ct2 (a FLOAT, b FLOAT)");
            assertAnswer(
                "3",
                "INSERT INTO ct2 SELECT cnt(1), cnt(1) FROM ct");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT * FROM ct2 ORDER BY 1");
            assertAnswer(
                "3|0",
                "UPDATE ct2 SET a = cnt(1), b = cnt(1)");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT * FROM ct2 ORDER BY 1");
            assertAnswer(
                "[1.000000000000000e+00,2.000000000000000e+00,3.000000000000000e+00]|[1.000000000000000e+00,"
                    + "2.000000000000000e+00,3.000000000000000e+00]",
                "SELECT ARRAY_AGG(cnt(v)) WITHIN GROUP (ORDER BY v), ARRAY_AGG(cnt(v)) WITHIN GROUP (ORDER BY v) "
                    + "FROM ct");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT SUM(cnt(v)), SUM(cnt(v)) FROM ct GROUP BY v ORDER BY 1");
            assertAnswer(
                "Function SQLW successfully created.",
                "CREATE OR REPLACE FUNCTION sqlw(x FLOAT) RETURNS FLOAT AS 'cnt(x)'");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || "
                    + "sqlw(1)::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1.0|1.0 ;; 2.0|2.0 ;; 3.0|3.0",
                "SELECT cnt(1) AS a, cnt(1) AS b FROM ct ORDER BY a");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | found QUALIFY clause but no window "
                    + "function.",
                "SELECT cnt(1) FROM ct QUALIFY cnt(1) > 0 ORDER BY 1");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP FUNCTION IF EXISTS sqlw(FLOAT)");
            answer(
                "DROP FUNCTION IF EXISTS cnt(FLOAT)");
            answer(
                "DROP TABLE IF EXISTS ct2");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void theProcedureApisMisuse() {
        try {
            assertAnswer(
                "Function PA1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa1() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$try { "
                    + "snowflake.execute({}); return 'ran'; } catch (e) { return e.message + ' | ' + e.stack; }$$");
            assertAnswer(
                "empty argument passed | Snowflake.execute, line 1 position 16",
                "CALL pa1()");
            assertAnswer(
                "Function PA2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$try { "
                    + "snowflake.createStatement({sqlText: 1}); return 'ran'; } catch (e) { return e.message + ' | ' "
                    + "+ e.stack; }$$");
            assertAnswer(
                "Function PA3 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa3() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$try { "
                    + "snowflake.execute(); return 'ran'; } catch (e) { return e.message + ' | ' + e.stack; }$$");
            assertAnswer(
                "TypeError: Cannot read properties of undefined (reading 'sqlText') | Snowflake.execute, line 1 "
                    + "position 16",
                "CALL pa3()");
            assertAnswer(
                "Function PA4 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa4() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS A'}); rs.next(); var b = rs.next(); try { return b + "
                    + "':' + String(rs.A); } catch (e) { return 'threw ' + e.message; }$$");
            assertAnswer(
                "Function PA5 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa5() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 5 AS \"next\", 6 AS \"getColumnValue\"'}); rs.next(); "
                    + "return typeof rs.next + ':' + typeof rs.getColumnValue + ':' + rs.getColumnValue(1) + ':' + "
                    + "rs.getColumnValue(2);$$");
            assertAnswer(
                "Function PA6 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS A'}); try { return String(rs.A); } catch (e) { "
                    + "return 'threw ' + e.message; }$$");
            assertAnswer(
                "undefined",
                "CALL pa6()");
            assertAnswer(
                "Function PA7 successfully created.",
                "CREATE OR REPLACE PROCEDURE pa7() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$var rs = "
                    + "snowflake.execute({sqlText: 'SELECT 1 AS A'}); rs.next(); rs.next(); try { return "
                    + "String(rs.getColumnValue(1)); } catch (e) { return 'threw ' + e.message + ' | ' + e.stack; }$$");
            assertAnswer(
                "null",
                "CALL pa7()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pa1()");
            answer(
                "DROP PROCEDURE IF EXISTS pa2()");
            answer(
                "DROP PROCEDURE IF EXISTS pa3()");
            answer(
                "DROP PROCEDURE IF EXISTS pa4()");
            answer(
                "DROP PROCEDURE IF EXISTS pa5()");
            answer(
                "DROP PROCEDURE IF EXISTS pa6()");
            answer(
                "DROP PROCEDURE IF EXISTS pa7()");
        }
    }
}
