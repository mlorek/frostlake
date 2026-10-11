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

import org.junit.jupiter.api.Test;

/**
 * EXECUTE IMMEDIATE ... USING binds each question mark in turn and each :n by its number, in any order and as
 * often as it is written, in a RESULTSET's statement, a statement of its own, a DML statement and inside a
 * construct; a cursor's query binds the same way from OPEN ... USING. A number the list does not reach, :0 and
 * a number written with a leading zero are binds nothing sets, and so is every :name: the text is a statement
 * of its own and reads none of the block's variables. A colon inside a text or before a path's key is no
 * bind. Every answer is live-verified.
 */
public class NumberedBindTest extends MeasuredCellsTestSupport {

    @Test
    public void usingBindsByNumber() {
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        try {
            assertAnswer(
                "abc|VARCHAR(3)[LOB]",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 "
                    + "AS a1, SYSTEM$TYPEOF(:1) AS a2' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|5",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 AS a, :2 AS b' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "ok",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'INSERT INTO ts VALUES (:1)' USING (s); RETURN "
                    + "'ok'; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "abc|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ? AS a, :1 AS b' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "5|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :2 AS a, :1 AS b' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 "
                    + "AS a, :1 AS b' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :3 not set.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :3 AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :0 not set.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :0 "
                    + "AS a' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "5",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :2 AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|5|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ? AS a, ? AS b, :1 AS c' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 AS a, ? AS b' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                ":1|abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT "
                    + "'':1'' AS a, :1 AS b' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|3",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT "
                    + ":1::VARCHAR AS a, LENGTH(:1) AS b' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :1 not set.",
                "DECLARE res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 AS a'); RETURN TABLE(res); END;");
            assertAnswer(
                "ok",
                "DECLARE a INT DEFAULT 7; b INT DEFAULT 8; BEGIN EXECUTE IMMEDIATE 'INSERT INTO ts VALUES (:1), "
                    + "(:2), (:1)' USING (a, b); RETURN 'ok'; END;");
            assertAnswer(
                "5 ;; 7 ;; 7 ;; 8",
                "SELECT a FROM ts ORDER BY a");
            assertAnswer(
                "2",
                "DECLARE a INT DEFAULT 7; BEGIN EXECUTE IMMEDIATE 'DELETE FROM ts WHERE a = :1' USING (a); RETURN "
                    + "SQLROWCOUNT; END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :1; BEGIN OPEN c USING (s); "
                    + "RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; res RESULTSET; BEGIN IF (TRUE) THEN res := (EXECUTE IMMEDIATE 'SELECT v "
                    + "FROM tp WHERE k = :1' USING (s)); END IF; RETURN TABLE(res); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; q VARCHAR DEFAULT 'SELECT v FROM tp WHERE k = :1'; res RESULTSET; BEGIN "
                    + "res := (EXECUTE IMMEDIATE :q USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "null",
                "DECLARE s INT DEFAULT 2; x INT; BEGIN EXECUTE IMMEDIATE 'SELECT v FROM tp WHERE k = :1' USING "
                    + "(s); RETURN SQLROWCOUNT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Bind variable :1 not set.",
                "EXECUTE IMMEDIATE 'SELECT :1 AS a'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Bind variable :1 not set.",
                "SELECT :1 AS a");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Bind variable :1 not set.",
                "SELECT :1 + 1");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: error line 1 at position 16 | Bind variable :x not set.",
                "DECLARE s VARCHAR DEFAULT 'x'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 AS a, "
                    + ":x AS b' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc5",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 || :2 AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "2024-01-02|VARCHAR(10)[LOB]",
                "DECLARE d DATE DEFAULT '2024-01-02'; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :1 "
                    + "AS a, SYSTEM$TYPEOF(:1) AS b' USING (d)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :01 not set.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :01 AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT : 1 AS a' USING (s, t)); RETURN TABLE(res); END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theTextReadsNoVariableOfTheBlock() {
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (c VARCHAR(10))");
        engine.execute(
            "INSERT INTO tv VALUES ('MS'), ('x')");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :s AS a'); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT :s AS a' USING "
                    + "(s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :s AS a'; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '5'.",
                "EXECUTE IMMEDIATE 'SELECT :1 AS a' USING (5)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '5'.",
                "EXECUTE IMMEDIATE 'SELECT ? AS a' USING (5)");
            assertAnswer(
                "",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 1 AS a WHERE 1 = "
                    + ":1 AND :1 < 9 AND ? = 5' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 1 AS a WHERE 5 = "
                    + ":1 AND :1 < 9 AND ? = 5' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "7|5",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT "
                    + "PARSE_JSON(''{\"a\":7}''):a AS a, :1 AS b' USING (s)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 23 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'INSERT INTO ts VALUES (:s)'; RETURN 'ok'; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 5; q VARCHAR; BEGIN q := 'SELECT ' || s || ' AS a'; EXECUTE IMMEDIATE :q; "
                    + "RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; BEGIN IF (TRUE) THEN EXECUTE IMMEDIATE 'SELECT :s AS a'; END IF; RETURN "
                    + "1; END;");
            assertAnswer(
                "ok",
                "DECLARE s INT DEFAULT 5; BEGIN IF (TRUE) THEN EXECUTE IMMEDIATE 'INSERT INTO ts VALUES (:1)' "
                    + "USING (s); END IF; RETURN 'ok'; END;");
            assertAnswer(
                "5",
                "SELECT a FROM ts ORDER BY a");
            assertAnswer(
                "SQL compilation error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :s AS a' USING (s); RETURN 1; EXCEPTION "
                    + "WHEN OTHER THEN RETURN SQLERRM; END;");
            assertAnswer(
                "5 ;; 5",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT $1 FROM VALUES "
                    + "(:1), (:2)' USING (s, s)); RETURN TABLE(res); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :1 OR k = ?; BEGIN OPEN c USING "
                    + "(s); RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :2 not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :2; BEGIN OPEN c USING (s); "
                    + "RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :1 not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :1; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 92 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :1 not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :1; x INT DEFAULT 0; BEGIN FOR "
                    + "r IN c DO x := r.v; END FOR; RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :S not set.",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :S AS a'; RETURN 1; END;");
            assertAnswer(
                "ok",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE tdyn AS SELECT :1 AS "
                    + "a' USING (s); RETURN 'ok'; END;");
            assertAnswer(
                "5",
                "SELECT a FROM tdyn");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
