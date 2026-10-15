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
 * A SQL procedure's body is read when the procedure is created: a faulty signature or property of a DROP or a
 * DESCRIBE in it, a word run in after a statement and a call in a FROM form no name accepts refuse the CREATE
 * with the body's own lines, and no procedure is left to call (live-verified).
 */
public class ProcedureBodyReadingAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyRefusedAtCreate() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        answer(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 17 unexpected 'VIEW'. | syntax "
                + "error line 3 at position 21 unexpected ')'.",
            "CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  DROP TABLE t7 (VIEW);\n  "
                + "RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P1.",
            "CALL p1()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 21 unexpected '1'.",
            "CREATE OR REPLACE PROCEDURE p2() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  DESCRIBE TABLE t7 (1);\n  "
                + "RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P2.",
            "CALL p2()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 32 unexpected 'VIEW'. | syntax "
                + "error line 4 at position 2 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE p3() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  IF (TRUE) THEN DROP TABLE "
                + "t7 (VIEW); END IF;\n  RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P3.",
            "CALL p3()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 27 unexpected 'b'. | syntax error "
                + "line 3 at position 27 unexpected 'b'. | syntax error line 3 at position 28 unexpected ')'.",
            "CREATE OR REPLACE PROCEDURE p4() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  DESCRIBE TABLE t7 x = (a "
                + "b);\n  RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P4.",
            "CALL p4()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 13 unexpected 'y'. | syntax error "
                + "line 4 at position 2 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE p5() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  SELECT 1 x y;\n  RETURN "
                + "1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P5.",
            "CALL p5()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 23 unexpected 'FROM'. | syntax "
                + "error line 3 at position 40 unexpected ')'.",
            "CREATE OR REPLACE PROCEDURE p7() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  SELECT EXTRACT('wks' FROM "
                + "CURRENT_DATE);\n  RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P7.",
            "CALL p7()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 20 unexpected 'x'. | syntax error "
                + "line 4 at position 2 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE p8() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  DROP TABLE t7 (a) x;\n  "
                + "RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "CREATE OR REPLACE PROCEDURE p9() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  DESCRIBE TABLE t7 (a) x = "
                + "1;\n  RETURN 1;\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function P9.",
            "CALL p9()");
        assertAnswer(
            "Function P10 successfully created.",
            "CREATE OR REPLACE PROCEDURE p10() RETURNS INT LANGUAGE SQL AS\n$$\nBEGIN\n  LET q := 1;\n  RETURN "
                + "1;\nEND;\n$$");
        assertAnswer(
            "1",
            "CALL p10()");
        assertAnswer(
            "Drop statement executed successfully (P1 already dropped).",
            "DROP PROCEDURE IF EXISTS p1()");
        assertAnswer(
            "Drop statement executed successfully (P2 already dropped).",
            "DROP PROCEDURE IF EXISTS p2()");
        assertAnswer(
            "Drop statement executed successfully (P3 already dropped).",
            "DROP PROCEDURE IF EXISTS p3()");
        assertAnswer(
            "Drop statement executed successfully (P4 already dropped).",
            "DROP PROCEDURE IF EXISTS p4()");
        assertAnswer(
            "Drop statement executed successfully (P5 already dropped).",
            "DROP PROCEDURE IF EXISTS p5()");
        assertAnswer(
            "Drop statement executed successfully (P7 already dropped).",
            "DROP PROCEDURE IF EXISTS p7()");
        assertAnswer(
            "Drop statement executed successfully (P9 already dropped).",
            "DROP PROCEDURE IF EXISTS p9()");
        assertAnswer(
            "P10 successfully dropped.",
            "DROP PROCEDURE IF EXISTS p10()");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t7");
    }
}
