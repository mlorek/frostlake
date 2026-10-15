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
 * A binary literal is X and a string literal: a backslash escapes and a doubled quote stays inside it, and of
 * its text blanks, line feeds and carriage returns are no part of the digits. A quoted name may cross a line
 * end. A comment that never closes takes the text to its end and is told there, alone when nothing else was
 * written (live-verified).
 */
public class LexerLiteralReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void binaryLiteralsAreStrings() {
        answer(
            "CREATE OR REPLACE TABLE sc (a INT)");
        answer(
            "INSERT INTO sc VALUES (1)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM sc");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\''; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\''");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\\\'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\\\'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'\\41'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'\\41'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'4\\1'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'4\\1'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal X'41\\''; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT X'41\\'' AS c");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41''42'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41''42'");
        assertAnswer(
            "",
            "SELECT x''");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'4'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'4'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'zz'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'zz'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'42''.",
            "SELECT x'41' x'42'");
        assertAnswer(
            "41",
            "SELECT x'41\\n'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'\\x41'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'\\x41'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\''; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT LENGTH(x'41\\'')");
        assertAnswer(
            "a'; DELETE FROM sc --",
            "SELECT 'a\\'; DELETE FROM sc --'");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'. | syntax error line "
                + "1 at position 11 unexpected '<EOF>'.",
            "SELECT x'41");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error line "
                + "1 at position 13 unexpected '<EOF>'.",
            "SELECT x'41\\'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type literal 'x '41''.",
            "SELECT x '41'");
        assertAnswer(
            "AB",
            "SELECT TO_VARCHAR(x'4142', 'UTF-8')");
    }

    @Test
    public void quotedNamesOfSeveralLines() {
        answer(
            "CREATE OR REPLACE TABLE sc (a INT)");
        answer(
            "INSERT INTO sc VALUES (1)");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\nb\"");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\rb\"");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\r\nb\"");
        assertAnswer(
            "1",
            "SELECT \"a\nb\" FROM (SELECT 1 AS \"a\nb\")");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'. | syntax error line "
                + "1 at position 21 unexpected '<EOF>'.",
            "SELECT 1 AS \"unclosed");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 2 at position 8 near '<EOF>'. | syntax error line "
                + "2 at position 8 unexpected '<EOF>'.",
            "SELECT 1 AS \"unclosed\nSELECT 2");
        assertAnswer(
            "1",
            "SELECT \"a\n\nb\" FROM (SELECT 1 AS \"a\n\nb\")");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\tb\"");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\"\"\nb\"");
        assertAnswer(
            "1",
            "SELECT 1 AS \"\n\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 3 unexpected 'x'.",
            "SELECT 1 AS \"a\nb\" x");
    }

    @Test
    public void commentsThatNeverClose() {
        answer(
            "CREATE OR REPLACE TABLE sc (a INT)");
        answer(
            "INSERT INTO sc VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 40 near '<EOF>'.",
            "SELECT 1 /* never closed; DELETE FROM sc");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'.",
            "SELECT 1 /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 4 near '<EOF>'.",
            "/* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'.",
            "SELECT /* a */ 1 /* b");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.",
            "SELECT 1 /*");
        assertAnswer(
            "1",
            "SELECT 1 /* a /* b */");
        assertAnswer(
            "1",
            "SELECT 1 -- c /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'.",
            "SELECT '/*' /* x");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 2 at position 4 near '<EOF>'.",
            "SELECT 1 /* multi\nline");
        assertAnswer(
            "1",
            "SELECT 1 // c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '*'.",
            "SELECT 1 /* a /* b */ c */");
        assertAnswer(
            "1",
            "SELECT 1 /**/");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'.",
            "SELECT 1 /*/");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 3 at position 0 near '<EOF>'.",
            "SELECT 1 /* x\n\n");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 2 at position 4 near '<EOF>'. | syntax error line "
                + "2 at position 4 unexpected '<EOF>'.",
            "SELECT 1 +\n/* x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'. | parse error "
                + "line 1 at position 17 near '<EOF>'.",
            "SELECT 1 x y /* z");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error line "
                + "1 at position 14 unexpected '<EOF>'.",
            "SELECT (1 /* z");
        assertAnswer(
            "a",
            "SELECT 'a' /* z ' */");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 1 at position 21 near '<EOF>'.",
            "SELECT 1 /* z */ /* y");
    }

    @Test
    public void blanksAndLineEndsInABinaryLiteral() {
        assertAnswer(
            "41",
            "SELECT x'4\\n1'");
        assertAnswer(
            "41",
            "SELECT x'\\n41'");
        assertAnswer(
            "41",
            "SELECT x'41 '");
        assertAnswer(
            "41",
            "SELECT x' 41'");
        assertAnswer(
            "41",
            "SELECT x'4 1'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\t'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\t'");
        assertAnswer(
            "41",
            "SELECT x'41\\r'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\0'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\0'");
        assertAnswer(
            "41",
            "SELECT x'41\n'");
        assertAnswer(
            "41",
            "SELECT x'4\n1'");
        assertAnswer(
            "41",
            "SELECT x'\n41'");
        assertAnswer(
            "41",
            "SELECT x'41\\n\\n'");
        assertAnswer(
            "41",
            "SELECT x'41\\r\\n'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'\\\\'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'\\\\'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'\\''; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'\\''");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x''''; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x''''");
        assertAnswer(
            "41",
            "SELECT x'41\\ '");
        assertAnswer(
            "41",
            "SELECT x'\\ 41'");
        assertAnswer(
            "4142|4142",
            "SELECT x'4142', X'4142'");
        assertAnswer(
            "1",
            "SELECT LENGTH(x'41\\n')");
        assertAnswer(
            "true",
            "SELECT x'41\\n' = x'41'");
        assertAnswer(
            "AB",
            "SELECT TO_VARCHAR(x'41\\n42', 'UTF-8')");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\t'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\t'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'\t41'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'\t41'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\b'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\b'");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid binary literal x'41\\f'; should contain pairs of hexadecimal "
                + "digits.",
            "SELECT x'41\\f'");
    }

    @Test
    public void aNameOfSeveralLinesInATable() {
        answer(
            "CREATE OR REPLACE TABLE tq (\"a\nb\" INT, \"c d\" INT)");
        answer(
            "INSERT INTO tq VALUES (1, 2)");
        assertAnswer(
            "1",
            "SELECT \"a\nb\" FROM tq");
        assertAnswer(
            "1|2",
            "SELECT t.\"a\nb\", t.\"c d\" FROM tq t");
        assertAnswer(
            "1|2",
            "SELECT * FROM tq");
        assertAnswer(
            "2",
            "SELECT \"a\nb\" + 1 AS \"x\ny\" FROM tq");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"a |  b\"'",
            "SELECT \"a\n b\" FROM tq");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"A | B\"'",
            "SELECT \"A\nB\" FROM tq");
        assertAnswer(
            "1|2",
            "SELECT 1 AS \"a\nb\", 2 AS \"a\nb\"");
        assertAnswer(
            "1",
            "SELECT \"a\nb\" FROM tq WHERE \"a\nb\" = 1");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\nb\" FROM tq ORDER BY \"a\nb\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 0 unexpected '\"c\"'.",
            "SELECT 1 \"a\nb\"\n\"c\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 3 unexpected '\"c\"'.",
            "SELECT 1 AS \"a\nb\" \"c\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 3 unexpected 'x'.",
            "SELECT 1 AS \"a\nb\nc\" x");
        assertAnswer(
            "1",
            "SELECT 1 AS \"a\nb\" ,");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 2 at position 1 near '<EOF>'. | syntax error line "
                + "2 at position 1 unexpected '<EOF>'.",
            "SELECT \"a\nb");
        assertAnswer(
            "REFUSED SQL compilation error: | parse error line 3 at position 0 near '<EOF>'. | syntax error line "
                + "3 at position 0 unexpected '<EOF>'.",
            "SELECT 1 AS \"a\n\n");
        assertAnswer(
            "1",
            "SELECT COUNT(\"a\nb\") FROM tq");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE tq RENAME COLUMN \"a\nb\" TO \"e\nf\"");
        assertAnswer(
            "1",
            "SELECT \"e\nf\" FROM tq");
    }
}
