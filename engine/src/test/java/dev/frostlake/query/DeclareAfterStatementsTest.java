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
 * A DECLARE section written after a block's statements wants its BEGIN: the END that stands there instead is
 * the token refused, and nothing after it is read - in an anonymous block, in a procedure's, a function's and a
 * task's unquoted body and in a quoted one. A nested block with its own DECLARE section is no fault. Every
 * answer is live-verified.
 */
public class DeclareAfterStatementsTest extends MeasuredCellsTestSupport {

    @Test
    public void theEndIsRefused() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 134 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pu() RETURNS INT LANGUAGE SQL AS BEGIN LET n INT := 1; RETURN (SELECT a "
                + "FROM t LIMIT 'x'); DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 109 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pu2() RETURNS INT LANGUAGE SQL AS BEGIN LET n INT := 1; RETURN n; "
                + "DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pu3() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET n INT := 1; RETURN n; "
                + "DECLARE q INT; END $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function PU3.",
            "CALL pu3()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'END'.",
            "BEGIN LET n INT := 1; RETURN n; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 116 unexpected '2'.",
            "CREATE OR REPLACE PROCEDURE pu4() RETURNS INT LANGUAGE SQL AS BEGIN LET n INT := 1; RETURN n; "
                + "DECLARE q INT; RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '2'.",
            "BEGIN LET n INT := 1; RETURN n; DECLARE q INT; RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "BEGIN DECLARE q INT; RETURN 1; END");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; DECLARE q INT; BEGIN RETURN 2; END; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'END'.",
            "BEGIN LET n := 1; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'END'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN 1; DECLARE q INT; END $$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'.",
            "BEGIN RETURN 1; DECLARE; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT; r INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT DEFAULT 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT; q := 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'END'.",
            "DECLARE p INT; BEGIN RETURN 1; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 0 unexpected 'END'.",
            "BEGIN RETURN 1;\nDECLARE q INT;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '<EOF>'.",
            "BEGIN RETURN 1; DECLARE q INT; BEGIN RETURN 2; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'END'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 2; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'END'.",
            "BEGIN IF (TRUE) THEN RETURN 1; DECLARE q INT; END IF; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE c CURSOR FOR SELECT 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE e EXCEPTION; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 93 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pu5() RETURNS INT LANGUAGE SQL AS BEGIN RETURN 1; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 108 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pu6() RETURNS INT LANGUAGE SQL AS DECLARE p INT; BEGIN RETURN 1; DECLARE "
                + "q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 78 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION fu() RETURNS INT AS BEGIN RETURN 1; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'END'.",
            "CREATE OR REPLACE TASK tku AS BEGIN RETURN 1; DECLARE q INT; END");
    }
}
