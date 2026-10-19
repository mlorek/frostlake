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
 * A SQL function whose body is a block. One that compiles is created and called; one whose block does not
 * compile - a string LIMIT inside it - is read as the function's expression instead and refused where that
 * frame stops, in the frame's positions; a statement or an expression a function's block may not hold is
 * refused in its own sentence, and a syntax fault in an unquoted block is the statement's own. Every answer
 * is live-verified.
 */
public class FunctionBlockBodyRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockBody() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute(
            "INSERT INTO t VALUES (1, 2)");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fb() RETURNS INT AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB.",
            "SELECT fb()");
        assertAnswer(
            "Function FB2 successfully created.",
            "CREATE OR REPLACE FUNCTION fb2() RETURNS INT AS BEGIN RETURN 1; END");
        assertAnswer(
            "1",
            "SELECT fb2()");
        assertAnswer(
            "Function FB3 successfully created.",
            "CREATE OR REPLACE FUNCTION fb3() RETURNS INT AS $$ BEGIN RETURN 1; END $$");
        assertAnswer(
            "1",
            "SELECT fb3()");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION fb4() RETURNS INT AS BEGIN RETURN (SELECT 1); END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB4.",
            "SELECT fb4()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB5.",
            "SELECT fb5()");
        assertAnswer(
            "Function FB6 successfully created.",
            "CREATE OR REPLACE FUNCTION fb6() RETURNS INT AS BEGIN RETURN nosuch; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH'",
            "SELECT fb6()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB7.",
            "SELECT fb7()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB8.",
            "SELECT fb8()");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 16 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fb9() RETURNS INT AS $$ BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB9.",
            "SELECT fb9()");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION fb10() RETURNS INT AS $$ BEGIN RETURN (SELECT 1); END $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB10.",
            "SELECT fb10()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB11.",
            "SELECT fb11()");
        assertAnswer(
            "Function FB12 successfully created.",
            "CREATE OR REPLACE FUNCTION fb12() RETURNS INT AS $$ BEGIN RETURN nosuch; END $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH'",
            "SELECT fb12()");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION fb13() RETURNS INT AS 'BEGIN RETURN (SELECT 1); END'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB13.",
            "SELECT fb13()");
        assertAnswer(
            "Function FB14 successfully created.",
            "CREATE OR REPLACE FUNCTION fb14(x INT) RETURNS INT AS BEGIN RETURN x + 1; END");
        assertAnswer(
            "2",
            "SELECT fb14(1)");
        assertAnswer(
            "Function FB15 successfully created.",
            "CREATE OR REPLACE FUNCTION fb15(x INT) RETURNS INT AS BEGIN LET y := x + 1; RETURN y; END");
        assertAnswer(
            "2",
            "SELECT fb15(1)");
        assertAnswer(
            "Function FB16 successfully created.",
            "CREATE OR REPLACE FUNCTION fb16() RETURNS INT AS BEGIN RETURN 'a' + TRUE; END");
        assertAnswer(
            "Function FB17 successfully created.",
            "CREATE OR REPLACE FUNCTION fb17() RETURNS INT AS BEGIN RETURN; END");
        assertAnswer(
            "null",
            "SELECT fb17()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION fb18() RETURNS INT AS BEGIN END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB18.",
            "SELECT fb18()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB19.",
            "SELECT fb19()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION fb20() RETURNS INT AS BEGIN RETURN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB20.",
            "SELECT fb20()");
        assertAnswer(
            "Function FB21 successfully created.",
            "CREATE OR REPLACE FUNCTION fb21() RETURNS INT AS DECLARE v INT; BEGIN RETURN 1; END");
        assertAnswer(
            "1",
            "SELECT fb21()");
        assertAnswer(
            "Function FB22 successfully created.",
            "CREATE OR REPLACE FUNCTION fb22() RETURNS INT AS BEGIN IF (TRUE) THEN RETURN 1; END IF; END");
        assertAnswer(
            "1",
            "SELECT fb22()");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCHFN(1)",
            "CREATE OR REPLACE FUNCTION fb23() RETURNS INT AS BEGIN RETURN nosuchfn(1); END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB23.",
            "SELECT fb23()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB24.",
            "SELECT fb24()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION fb25() RETURNS INT AS BEGIN RETURN 1 +; END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB25.",
            "SELECT fb25()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB26.",
            "SELECT fb26()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB27.",
            "SELECT fb27()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB28.",
            "SELECT fb28()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB29.",
            "SELECT fb29(1)");
        assertAnswer(
            "Function FB30 successfully created.",
            "CREATE OR REPLACE FUNCTION fb30() RETURNS INT AS BEGIN RETURN UPPER(1, 2); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 96 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION fb31() RETURNS INT AS BEGIN LET n INT := 1; RETURN n; DECLARE q INT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB31.",
            "SELECT fb31()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB32.",
            "SELECT fb32()");
        assertAnswer(
            "Function FB33 successfully created.",
            "CREATE OR REPLACE FUNCTION fb33() RETURNS INT AS BEGIN RETURN 1; RETURN 2; END");
        assertAnswer(
            "1",
            "SELECT fb33()");
        assertAnswer(
            "REFUSED Unsupported statement type for Snowscript UDF: cursor declaration",
            "CREATE OR REPLACE FUNCTION fb34() RETURNS INT AS BEGIN LET c CURSOR FOR SELECT a FROM t; RETURN 1; "
                + "END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB34.",
            "SELECT fb34()");
        assertAnswer(
            "Function FB35 successfully created.",
            "CREATE OR REPLACE FUNCTION fb35() RETURNS INT AS BEGIN RETURN (1); END");
        assertAnswer(
            "1",
            "SELECT fb35()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION fb36() RETURNS INT AS BEGIN RETURN (SELECT a FROM t LIMIT 'x') END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB36.",
            "SELECT fb36()");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at position 2 "
                + "unexpected 'RETURN'. | syntax error line 2 at position 10 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fb37() RETURNS INT AS BEGIN\n  RETURN (SELECT a FROM t LIMIT 'x');\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB37.",
            "SELECT fb37()");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 3 at position 2 "
                + "unexpected 'RETURN'. | syntax error line 3 at position 10 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fb38() RETURNS INT AS $$\nBEGIN\n  RETURN (SELECT a FROM t LIMIT "
                + "'x');\nEND\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function FB38.",
            "SELECT fb38()");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fl1() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT "
                + "'x'); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 86 unexpected 'FROM'.",
            "CREATE OR REPLACE FUNCTION fl2() RETURNS TABLE (a INT) AS BEGIN RETURN TABLE(SELECT a FROM t); END");
        assertAnswer(
            "Function FL3 successfully created.",
            "CREATE OR REPLACE FUNCTION fl3() RETURNS VARCHAR AS BEGIN RETURN 'x'; END");
        assertAnswer(
            "x",
            "SELECT fl3()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''x''.",
            "CREATE OR REPLACE PROCEDURE pb1() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT "
                + "'x'); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''x''.",
            "CREATE OR REPLACE PROCEDURE pb2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT a FROM t LIMIT "
                + "'x'); END $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function PB2.",
            "CALL pb2()");
        assertAnswer(
            "Function PB3 successfully created.",
            "CREATE OR REPLACE PROCEDURE pb3() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT nosuch FROM t); "
                + "END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
            "CALL pb3()");
        assertAnswer(
            "FB12|FB12() RETURN NUMBER ;; FB14|FB14(NUMBER) RETURN NUMBER ;; FB15|FB15(NUMBER) RETURN NUMBER ;; "
                + "FB16|FB16() RETURN NUMBER ;; FB17|FB17() RETURN NUMBER ;; FB2|FB2() RETURN NUMBER ;; FB21|FB21() "
                + "RETURN NUMBER ;; FB22|FB22() RETURN NUMBER ;; FB3|FB3() RETURN NUMBER ;; FB30|FB30() RETURN "
                + "NUMBER ;; FB33|FB33() RETURN NUMBER ;; FB35|FB35() RETURN NUMBER ;; FB6|FB6() RETURN NUMBER",
            "SHOW USER FUNCTIONS LIKE 'FB%' ->> SELECT \"name\", \"arguments\" FROM $1 ORDER BY \"name\"");
    }
}
