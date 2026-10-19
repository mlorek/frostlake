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
 * A dynamic text's refusal is placed in the text as written, whatever its placeholders are written out as: a
 * fault of its syntax and a name it does not resolve after a ? or a :n stand where the text has them, on its
 * later lines too, and a comment that opens the text is not counted. A text that is a block compiles as a
 * block of its own and reads none of the variables of the block that runs it (live-verified).
 */
public class DynamicTextWrittenPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void afterABoundPlaceholder() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
            answer(
                "INSERT INTO tq VALUES (1.5, 'ab')");
            answer(
                "CREATE OR REPLACE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS $$ 'num' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(s VARCHAR) RETURNS VARCHAR AS $$ 'var' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fw(n NUMBER) RETURNS NUMBER AS $$ n + 0.5 $$");
            answer(
                "CREATE OR REPLACE FUNCTION fw(s VARCHAR) RETURNS VARCHAR AS $$ s || '!' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fx(n NUMBER) RETURNS NUMBER AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fy(n NUMBER(10,2)) RETURNS NUMBER(12,4) AS $$ n * 2 $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 11 unexpected 'AS'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1a AS a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 's'",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN RETURN :s; END'; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ? x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 12 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 11 | invalid identifier 'NOSUCH'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1, nosuch FROM tq' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "5",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :2a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 14 unexpected 'z'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?, ? z z' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|5",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1 AS a, :2 AS b' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?a' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN RETURN 1; END'; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier '1'",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN RETURN :1; END' USING (s); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'DECLARE s INT DEFAULT 7; BEGIN RETURN s; END'; "
                    + "RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN RETURN s; END'; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 8 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN s := 9; END'; RETURN s; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :s'; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'BEGIN LET q INT := 1; END'; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid expression value (?SqlExecuteImmediateDynamic?) for "
                    + "assignment.",
                "DECLARE s INT DEFAULT 5; r INT; BEGIN r := (EXECUTE IMMEDIATE 'BEGIN RETURN :s; END'); RETURN r; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'BEGIN RETURN s + 1; "
                    + "END'); RETURN TABLE(res); END;");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fo(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fo(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fw(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fw(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS fx(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fy(NUMBER)");
        }
    }

    @Test
    public void laterLinesSeveralStatementsAndComments() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), m NUMBER(10,4), i INT, s VARCHAR)");
            answer(
                "INSERT INTO tq VALUES (1.5, 1.25, 2, 'ab')");
            answer(
                "CREATE OR REPLACE FUNCTION fx(n NUMBER) RETURNS NUMBER AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fz(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a * b'");
            answer(
                "CREATE OR REPLACE FUNCTION fc(n NUMBER) RETURNS NUMBER AS '(n + 0.5)::NUMBER(10,3)'");
            answer(
                "CREATE OR REPLACE FUNCTION fd(n NUMBER) RETURNS NUMBER AS 'n / 2'");
            answer(
                "CREATE OR REPLACE FUNCTION ff(n NUMBER) RETURNS FLOAT AS 'n + 0.5'");
            answer(
                "CREATE OR REPLACE FUNCTION fq(n NUMBER) RETURNS NUMBER AS '(SELECT n + 0.5)'");
            answer(
                "CREATE OR REPLACE FUNCTION fi(n NUMBER) RETURNS NUMBER AS 'IFF(n > 1, n, 0)'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 2 at position 5 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?,\\n ? x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 16 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT :1, :1 x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 13 | invalid identifier 'NOSUCH'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?, ?, nosuch FROM tq' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 10 | too many arguments for function [ABS(1, 2)] expected 1, "
                    + "got 2",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?, ABS(1, 2)' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : Division by zero",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?, 1/0' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "abc|1970-01-01",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ?, TO_DATE(?)' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: error line 1 at position 15 | invalid identifier 'NOSUCH.X'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ? AS a, nosuch.x FROM tq' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE '/* c */ SELECT ? x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT ? x y; SELECT 1' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                    + "error: | syntax error line 1 at position 21 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE "
                    + "IMMEDIATE 'SELECT 1; SELECT ? x y' USING (s, t)); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ? x y' "
                    + "USING (s, t); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: error line 1 at position 10 | invalid identifier 'NOSUCH'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?, nosuch "
                    + "FROM tq' USING (s, t); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | syntax error line 1 at position 16 unexpected 'z'.",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :2, :1 z "
                    + "z' USING (s, t); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: | Insert value list does not match column list expecting 1 but got 2",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'INSERT INTO tq "
                    + "(n) VALUES (?, nosuch)' USING (s, t); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: error line 1 at position 10 | too many arguments for function [ABS(1, 2)] expected 1, "
                    + "got 2",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?, ABS(1, "
                    + "2)' USING (s, t); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: error line 1 at position 45 | invalid identifier 'NOSUCH'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'CREATE OR REPLACE "
                    + "TABLE tz AS SELECT ? AS a, nosuch AS b' USING (s, t); RETURN 1; END;");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fx(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fz(NUMBER, NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fc(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fd(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS ff(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fq(NUMBER)");
            answer(
                "DROP FUNCTION IF EXISTS fi(NUMBER)");
        }
    }

    @Test
    public void aCommentThatOpensTheText() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        answer(
            "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
        answer(
            "INSERT INTO tq VALUES (1.5, 'ab')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                + "error: error line 1 at position 10 | invalid identifier 'NOSUCH'",
            "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE "
                + "'/* c */ SELECT ?, nosuch FROM tq' USING (s, t)); RETURN TABLE(res); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                + "error: | syntax error line 1 at position 11 unexpected 'y'.",
            "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE '/* c */ SELECT ? x y' "
                + "USING (s, t); RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                + "error: error line 2 at position 1 | invalid identifier 'NOSUCH'",
            "DECLARE s VARCHAR(10) DEFAULT 'abc'; t INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT ?,\\n nosuch "
                + "FROM tq' USING (s, t); RETURN 1; END;");
    }
}
