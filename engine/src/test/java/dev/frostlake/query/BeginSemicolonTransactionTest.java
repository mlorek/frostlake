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
 * BEGIN followed by its semicolon begins a transaction, as BEGIN WORK and BEGIN TRANSACTION do: the
 * statements after it are a script's own, so a lone fault is refused where it stands and a later
 * statement's fault is reported too, where a scripting block left open would be refused at the end of
 * the input. A scripting statement after it is refused as one written outside a block. Every answer is
 * live-verified.
 */
public class BeginSemicolonTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void theStatementsAfterItAreTheScriptsOwn() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "BEGIN; SELECT 1 +; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "BEGIN; SELECT 1 +; SELECT 1 x y; COMMIT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "BEGIN; SELECT 1 FROM; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "BEGIN TRANSACTION; SELECT 1 +; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "START TRANSACTION; SELECT 1 +; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SELECT 1 +; BEGIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "BEGIN; SELECT 1 +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected '<EOF>'.",
            "BEGIN; SELECT 1 +; SELECT 1 FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y; SELECT 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y; COMMIT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "BEGIN ; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 11 unexpected 'y'.",
            """
                BEGIN;
                SELECT 1 x y""");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "begin; select 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
            "BEGIN; BEGIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'LET'.",
            "BEGIN; LET a := 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'END'.",
            "BEGIN; SELECT 1; END; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'.",
            "BEGIN; SELECT 1 +; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'IF'.",
            "BEGIN; IF (TRUE) THEN SELECT 1; END IF; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'RETURN'.",
            "BEGIN; RETURN 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'x'.",
            "BEGIN; x := 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'.",
            "BEGIN WORK; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "BEGIN TRANSACTION; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
            "BEGIN NAME n; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "START TRANSACTION; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "BEGIN; COMMIT; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y; ROLLBACK");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "COMMIT; BEGIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'.",
            "BEGIN; CREATE TABLE zz9 (a INT) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "BEGIN; SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected ';'. | syntax error line 1 at position 35 unexpected 'y'.",
            "BEGIN; SET x; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "BEGIN; SELECT 1 FROM t JOIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'z'.",
            "BEGIN; SELECT 1 x; SELECT 2 y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'.",
            "BEGIN; SELECT 1; SELECT 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '2'. | syntax "
                + "error line 1 at position 49 unexpected 'y'.",
            "BEGIN; SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "BEGIN; COMMIT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'. | syntax "
                + "error line 1 at position 27 unexpected 'END'.",
            "BEGIN; BEGIN SELECT 1 x y; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'.",
            "BEGIN; DECLARE a INT; BEGIN RETURN 1 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "BEGIN; SELECT 1 x y;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "BEGIN;; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'END'.",
            "BEGIN; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'END'.",
            "BEGIN; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'x'.",
            "BEGIN; x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
            "BEGIN; 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'.",
            "BEGIN; SELECT 1 +; x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 13 unexpected 'y'.",
            """
                BEGIN -- c
                ; SELECT 1 x y""");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "BEGIN /* c */ ; SELECT 1 +; SELECT 1 x y");
    }
}
