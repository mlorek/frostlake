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
 * The places of a fault in the text of EXECUTE IMMEDIATE are counted in that text: a comment that opens the
 * request moves none of them, and a comment that opens the text is counted as written. Every answer is
 * live-verified.
 */
public class ExecuteImmediateTextPositionsTest extends MeasuredCellsTestSupport {

    @Test
    public void afterACommentThatOpensTheRequest() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* a longer comment */ EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'.",
            "/* c */ EXECUTE IMMEDIATE 'DESCRIBE TABLE t1 ((a), b)'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "-- c\nEXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ SELECT 1 FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "EXECUTE IMMEDIATE '/* c */ SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */ EXECUTE IMMEDIATE $$SELECT 1 FROM$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "/* c */\nEXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 6 unexpected '<EOF>'.",
            "-- c\nEXECUTE IMMEDIATE 'SELECT\n1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "  EXECUTE IMMEDIATE 'SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
            "/* c */ EXECUTE IMMEDIATE 'BEGIN SELECT 1 FROM; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "/* c */ EXECUTE IMMEDIATE '/* d */ SELECT 1 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "/* c */ EXECUTE IMMEDIATE 'SELECT 1 x y'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "/* c */ EXECUTE IMMEDIATE 'SELECT nosuch FROM t1'");
        assertAnswer(
            "",
            "/* c */ EXECUTE IMMEDIATE 'SELECT 1 + ''a'' FROM t1'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 10 : SQL compilation "
                + "error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "BEGIN\n  /* c */ EXECUTE IMMEDIATE 'SELECT 1 FROM';\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "/* c */ EXECUTE IMMEDIATE 'SELECT 1; SELECT 2 FROM'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_P.",
            "/* c */ CALL nosuch_p('SELECT 1 FROM')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [UPPER(1,"
                + " 2, 3)] expected 1, got 3",
            "/* c */ EXECUTE IMMEDIATE 'SELECT UPPER(1, 2, 3)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "/* c */ EXECUTE IMMEDIATE 'SELECT nosuch'");
    }
}
