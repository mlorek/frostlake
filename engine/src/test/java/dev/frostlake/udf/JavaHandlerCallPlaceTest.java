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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Java function's class state is kept for each place a statement calls it from: two select items of one
 * text, the arms of a UNION, two common table expressions and two scalar subqueries each count from 1, while
 * the rows one place reads share its state. A block's own expressions are queries of their own, so a call in
 * a loop starts afresh on each turn, and a call written once in a SQL function's body is one place for each
 * call of the function, its rows sharing it (live-verified).
 */
public class JavaHandlerCallPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void itemsOfOneTextArePlacesOfTheirOwn() {
        try {
            assertAnswer(
                "Function JFC successfully created.",
                "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "1|1",
                "SELECT jfc(1), jfc(1)");
            assertAnswer(
                "1|1",
                "SELECT jfc(1) AS a, jfc(1) AS b");
            assertAnswer(
                "1|1|1",
                "SELECT jfc(1), jfc(1), jfc(1)");
            assertAnswer(
                "1|1 ;; 2|2 ;; 3|3",
                "SELECT jfc(v), jfc(v) FROM ct ORDER BY 1");
            assertAnswer(
                "1 ;; 1",
                "SELECT * FROM (SELECT jfc(1) AS x UNION ALL SELECT jfc(1)) ORDER BY 1");
            assertAnswer(
                "1|1",
                "SELECT (SELECT jfc(1)), (SELECT jfc(1))");
            assertAnswer(
                "1|1",
                "WITH a AS (SELECT jfc(1) AS x), b AS (SELECT jfc(1) AS y) SELECT x, y FROM a, b");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT jfc(1) FROM ct WHERE jfc(1) > 0 ORDER BY 1");
            assertAnswer(
                "[1,1]",
                "SELECT ARRAY_CONSTRUCT(jfc(1), jfc(1))");
            assertAnswer(
                "1|1",
                "SELECT jfc(1), jfc( 1 )");
            assertAnswer(
                "1|1 ;; 2|2 ;; 3|3",
                "SELECT jfc(1), jfc(1) FROM ct ORDER BY 1");
            assertAnswer(
                "1",
                "SELECT jfc(1)");
            assertAnswer(
                "1",
                "SELECT jfc(1)");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS sqlj(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jinst(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jfc(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void aBlocksExpressionsStartAfresh() {
        try {
            assertAnswer(
                "Function JFC successfully created.",
                "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO s := s || "
                    + "jfc(1)::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1|1",
                "EXECUTE IMMEDIATE $$DECLARE a NUMBER; b NUMBER; BEGIN a := jfc(1); b := jfc(1); RETURN a || '|' "
                    + "|| b; END;$$");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO LET t NUMBER := "
                    + "jfc(1); s := s || t::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "yyy",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR i IN 1 TO 3 DO IF (jfc(1) = 1) THEN "
                    + "s := s || 'y'; ELSE s := s || 'n'; END IF; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1,1,1,",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; t NUMBER; BEGIN FOR i IN 1 TO 3 DO SELECT "
                    + "jfc(1) INTO :t; s := s || t::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "2",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN RETURN jfc(1) + jfc(1); END;$$");
            assertAnswer(
                "1|1",
                "EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT ''; BEGIN RETURN jfc(1) || '|' || jfc(1); END;$$");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS sqlj(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jinst(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jfc(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void aSqlFunctionsBodyIsAPlaceForEachCall() {
        try {
            assertAnswer(
                "Function JFC successfully created.",
                "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "Function SQLJ successfully created.",
                "CREATE OR REPLACE FUNCTION sqlj(x NUMBER) RETURNS NUMBER AS 'jfc(x)'");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT sqlj(v) FROM ct ORDER BY 1");
            assertAnswer(
                "1|1",
                "SELECT sqlj(1), sqlj(1)");
            assertAnswer(
                "1|1 ;; 2|2 ;; 3|3",
                "SELECT sqlj(v), jfc(v) FROM ct ORDER BY 1");
            assertAnswer(
                "2",
                "SELECT sqlj(1) + sqlj(1)");
            assertAnswer(
                "Function SQLQ successfully created.",
                "CREATE OR REPLACE FUNCTION sqlq(x NUMBER) RETURNS NUMBER AS 'SELECT jfc(x)'");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT sqlq(v) FROM ct ORDER BY 1");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS sqlj(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jinst(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jfc(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }

    @Test
    public void proceduresAndInstanceHandlers() {
        try {
            assertAnswer(
                "Function JFC successfully created.",
                "CREATE OR REPLACE FUNCTION jfc(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { static int n = 0; public static int f(int x) { n = n + 1; return n; } } $$");
            assertAnswer(
                "Table CT successfully created.",
                "CREATE OR REPLACE TABLE ct (v NUMBER)");
            assertAnswer(
                "3",
                "INSERT INTO ct VALUES (1), (2), (3)");
            assertAnswer(
                "Function PLOOP successfully created.",
                "CREATE OR REPLACE PROCEDURE ploop() RETURNS VARCHAR AS $$DECLARE s VARCHAR DEFAULT ''; BEGIN FOR "
                    + "i IN 1 TO 3 DO s := s || jfc(1)::VARCHAR || ','; END FOR; RETURN s; END;$$");
            assertAnswer(
                "1,1,1,",
                "CALL ploop()");
            assertAnswer(
                "1,1,1,",
                "CALL ploop()");
            assertAnswer(
                "View JV successfully created.",
                "CREATE OR REPLACE VIEW jv AS SELECT jfc(1) AS x");
            assertAnswer(
                "1|1",
                "SELECT jfc(1), jfc(1) FROM ct WHERE v = 1");
            assertAnswer(
                "Function JINST successfully created.",
                "CREATE OR REPLACE FUNCTION jinst(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                    + "class H { int n = 0; public int f(int x) { n = n + 1; return n; } } $$");
            assertAnswer(
                "1|1",
                "SELECT jinst(1), jinst(2)");
            assertAnswer(
                "1|1",
                "SELECT jinst(1), jinst(1)");
            assertAnswer(
                "1 ;; 2 ;; 3",
                "SELECT jinst(v) FROM ct ORDER BY 1");
            assertAnswer(
                "2",
                "SELECT jinst(1) + jinst(1)");
        } finally {
            answer(
                "DROP VIEW IF EXISTS jv");
            answer(
                "DROP PROCEDURE IF EXISTS ploop()");
            answer(
                "DROP FUNCTION IF EXISTS sqlq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS sqlj(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jinst(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS jfc(NUMBER)");
            answer(
                "DROP TABLE IF EXISTS ct");
        }
    }
}
