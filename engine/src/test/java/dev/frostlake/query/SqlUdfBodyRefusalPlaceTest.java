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
 * A SQL function whose query body is refused at CREATE. A syntax refusal of the body is the function's
 * compilation failing and says so; a name's, a type's and an arity's speak for themselves. Either is placed
 * where the body writes it: its lines count from the body's first line, blanks before the statement are
 * counted, and the body's first line counts from position 1. Every answer is live-verified.
 */
public class SqlUdfBodyRefusalPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theBodyAsWritten() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 23 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION flim() RETURNS INT AS 'SELECT a FROM t LIMIT ''x'''");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 24 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl2() RETURNS INT AS '(SELECT a FROM t LIMIT ''x'')'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at position 22 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl3() RETURNS INT AS $$\nSELECT a FROM t LIMIT 'x'$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn1() RETURNS INT AS 'SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn2() RETURNS INT AS '(SELECT nosuch FROM t)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn3() RETURNS INT AS $$\nSELECT nosuch FROM t$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn4() RETURNS INT AS $$SELECT nosuch FROM t$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 4 at position 2 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn5() RETURNS INT AS $$\n\n  SELECT a,\n  nosuch FROM t$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn6() RETURNS INT AS '\nSELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 10 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fn7() RETURNS INT AS '  SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_TABLE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TABLE.",
            "CREATE OR REPLACE FUNCTION ft1() RETURNS INT AS 'SELECT a FROM nosuch_table'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_TABLE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TABLE.",
            "CREATE OR REPLACE FUNCTION ft2() RETURNS INT AS $$\nSELECT a FROM nosuch_table$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fy1() RETURNS INT AS 'SELECT ''x'' + TRUE FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 11 | Invalid argument types for function "
                + "'+': (VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fy2() RETURNS INT AS $$\nSELECT 'x' + TRUE FROM t$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | too many arguments for function [UPPER(1,"
                + " 2)] expected 1, got 2",
            "CREATE OR REPLACE FUNCTION fu1() RETURNS INT AS 'SELECT UPPER(1, 2) FROM t'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 4 at position 6 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl4() RETURNS INT AS $$\n\nSELECT a FROM t\nLIMIT 'x'$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 25 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl5() RETURNS INT AS $$  SELECT a FROM t LIMIT 'x'$$");
        assertAnswer(
            "Function FL7 successfully created.",
            "CREATE OR REPLACE FUNCTION fl7() RETURNS INT AS 'SELECT a FROM t LIMIT 1.5'");
        assertAnswer(
            "Function FL8 successfully created.",
            "CREATE OR REPLACE FUNCTION fl8() RETURNS INT AS 'SELECT a FROM t LIMIT NULL'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 23 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl9() RETURNS INT AS 'SELECT a FROM t LIMIT ''x'' OFFSET 1'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 32 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION flo() RETURNS INT AS 'SELECT a FROM t LIMIT 1 OFFSET ''x'''");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fe1() RETURNS INT AS 'nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fe3() RETURNS INT AS '''x'' + TRUE'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 4 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fe4() RETURNS INT AS $$\n'x' + TRUE$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 23 "
                + "unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION ftt() RETURNS TABLE (a INT) AS 'SELECT a FROM t LIMIT ''x'''");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION ftn() RETURNS TABLE (a INT) AS 'SELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION ftm() RETURNS TABLE (a INT) AS $$\nSELECT nosuch FROM t$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''.",
            "SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''.",
            "SELECT (SELECT a FROM t LIMIT 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ''x''.",
            "CREATE OR REPLACE VIEW vl AS SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 22 unexpected ''x''.",
            "CREATE OR REPLACE VIEW vl2 AS\nSELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''.",
            "/* c */ SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ''x''.",
            "  SELECT a FROM t LIMIT 'x'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : Compilation of SQL "
                + "UDF failed: SQL compilation error: | syntax error line 1 at position 23 unexpected ''x''.",
            "BEGIN\n  CREATE OR REPLACE FUNCTION fb1() RETURNS INT AS 'SELECT a FROM t LIMIT ''x''';\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FLIM.",
            "SELECT flim()");
    }
}
