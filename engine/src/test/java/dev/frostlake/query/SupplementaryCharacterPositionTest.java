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
 * A position after a character outside the Basic Multilingual Plane counts that character twice, as the
 * two UTF-16 code units it is written in: after one such character a syntax error's position, a compile
 * error's place and a position inside an EXECUTE IMMEDIATE's text are one further on, in a string, a
 * quoted name, a dollar-quoted text and a comment alike. A character inside the plane counts once, and a
 * later line is not affected. The lines that rewrite the text by its tokens follow: a word run in a
 * bracket and a JOIN without a table are read after such a character as before it. Every answer is
 * live-verified.
 */
public class SupplementaryCharacterPositionTest extends MeasuredCellsTestSupport {

    @Test
    public void aPositionCountsUtf16CodeUnits() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT '😀' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'y'.",
            "SELECT '😀😀' x y");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH'",
            "SELECT '😀', nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'RENAME'. | "
                + "syntax error line 1 at position 50 unexpected 'y'.",
            "SELECT '😀'; ALTER TABLE t1 RENAME TO; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 48 unexpected 'TABLE'. | syntax error line 1 at position 56 "
                + "unexpected '('.",
            "BEGIN SELECT '😀'; LET a := 1 CREATE OR REPLACE TABLE u (a INT); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT 'é' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 4 unexpected 'y'.",
            "SELECT '😀'\n  x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT '𠀀' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT 1 \"😀\" y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "/* 😀 */ SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT 1 /* 😀 */ x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'y'.",
            "SELECT $$😀$$ x y");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCH'",
            "SELECT '😀' || nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2",
            "SELECT '😀', ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT '😀' x y; SELECT '😀' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 9 unexpected 'y'.",
            """
                SELECT '😀'
                , '😀' x y""");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "SELECT 'a😀b😀c' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "EXECUTE IMMEDIATE 'SELECT ''😀'' x y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT '😀' x y -- c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT 'é' x y");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH'",
            "SELECT '😀', a FROM t WHERE nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'x'.",
            "CREATE OR REPLACE TABLE e9 (a INT COMMENT '😀' x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
            "SELECT '😀' AS \"😀\", 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ';'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "SELECT '😀' +; SELECT '😀' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '2'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT '😀' FROM t WHERE a = 1 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT ('😀' x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 36 unexpected 'x'.",
            "SELECT '😀' FROM t JOIN WHERE a = 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x '😀'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected 'y'.",
            "SELECT '😀', (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SELECT '😀', 1 +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "SELECT '😀'::INT x y");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH'",
            "SELECT '😀', nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "SELECT nosuch, '😀'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH'",
            "SELECT '😀' = nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH'",
            "SELECT 1 FROM t WHERE b = '😀' AND nosuch = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'T.NOSUCH'",
            "SELECT '😀', t.nosuch FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | too many arguments for function "
                + "[TO_DATE(1, 2, 3)] expected 2, got 3",
            "SELECT '😀', TO_DATE(1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | Invalid argument types for function "
                + "'=': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT '😀', 1 IN (SELECT 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT '퟿' x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT '￮' x y");
    }
}
