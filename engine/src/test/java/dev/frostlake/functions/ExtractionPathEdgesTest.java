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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The edges of an extraction path: a path GET_PATH reads with the row is refused when it cannot read it, over a
 * NULL source too, a NULL path aside; a bare name takes the characters of Latin-1 past ASCII but U+00C0, U+00C1 and
 * U+00F5 to U+00FF, first and later, while the row's reading takes every one; a session variable's path is judged
 * as a literal's; and FLATTEN's PATH writes a key's control character as three octal digits, DEL too, a newline, a
 * tab, a carriage return, a backspace and a form feed by their letters (all live-verified).
 */
public class ExtractionPathEdgesTest extends MeasuredCellsTestSupport {

    @Test
    public void badPathsOverNullSources() {
        assertAnswer("REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(NULL, 'a.')");
        assertAnswer("REFUSED Bad compound object's field path name 'a.' in GET_PATH",
            "SELECT GET_PATH(NULL, p) FROM (SELECT 'a.' AS p)");
        assertAnswer("REFUSED Bad compound object's field path name 'a.' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"a\":1}'), p) FROM (SELECT 'a.' AS p)");
        assertAnswer("REFUSED Bad compound object's field path name 'a.' in GET_PATH",
            "SELECT GET_PATH(v, p) FROM (SELECT NULL::VARIANT AS v, 'a.' AS p)");
        assertAnswer("", "SELECT GET_PATH(v, p) FROM (SELECT PARSE_JSON('{}') AS v, 'a.' AS p) WHERE 1 = 0");
        assertAnswer("REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT COUNT(*) FROM (SELECT PARSE_JSON('{}') AS v) WHERE GET_PATH(v, 'a.') IS NULL");
        assertAnswer("REFUSED Bad compound object's field path name 'n.  b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"n\":{\"b\":1}}'), 'n.  b')");
        assertAnswer("", "SELECT GET_PATH(v, 'n.  b') FROM (SELECT PARSE_JSON('{}') AS v) WHERE 1 = 0");
        assertAnswer("REFUSED Bad compound object's field path name 'n.  b' in GET_PATH",
            "SELECT GET_PATH(v, 'n.  b') FROM (SELECT NULL::VARIANT AS v)");
        assertAnswer("REFUSED Bad compound object's field path name 'a.' in FLATTEN",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\":1}'), PATH => 'a.'))");
    }

    @Test
    public void namesPastAscii() {
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u0080')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u0080')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u009f')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u009f')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00a0')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00a0')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00a1')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00a1')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00aa')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00aa')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00b4')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00b4')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00bf')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00bf')");
        assertAnswer("REFUSED Invalid extraction path '\u00c0': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00c0')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00c0': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00c0')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00d6')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00d6')");
        assertAnswer("REFUSED Invalid extraction path '\u00f7': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00f7')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00f7': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00f7')");
        assertAnswer("REFUSED Invalid extraction path '\u00fe': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00fe')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00fe': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00fe')");
    }

    @Test
    public void theLatin1EdgesFirst() {
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u0080')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00bf')");
        assertAnswer("REFUSED Invalid extraction path '\u00c0': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00c0')");
        assertAnswer("REFUSED Invalid extraction path '\u00c1': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00c1')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00c2')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00f4')");
        assertAnswer("REFUSED Invalid extraction path '\u00f5': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00f5')");
        assertAnswer("REFUSED Invalid extraction path '\u00ff': invalid token at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), '\u00ff')");
    }

    @Test
    public void theLatin1EdgesLater() {
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u0080')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00bf')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00c0': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00c0')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00c1': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00c1')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00c2')");
        assertAnswer("null", "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00f4')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00f5': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00f5')");
        assertAnswer("REFUSED Invalid extraction path 'x\u00ff': invalid field at position 0.",
            "SELECT GET_PATH(PARSE_JSON('{\"x\":1}'), 'x\u00ff')");
    }

    @Test
    public void flattenPathKeys() {
        assertAnswer("\u00ff", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u00ff\":1}')))");
        assertAnswer("\u0100", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u0100\":1}')))");
        assertAnswer("\u03b1", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u03b1\":1}')))");
        assertAnswer("\u4e2d", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u4e2d\":1}')))");
        assertAnswer("\u00b5", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u00b5\":1}')))");
        assertAnswer("\u00d7", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u00d7\":1}')))");
        assertAnswer("\u00a0", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u00a0\":1}')))");
        assertAnswer("\u00bf", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\u00bf\":1}')))");
        assertAnswer("['a\\rb']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\rb\":1}')))");
        assertAnswer("['a\\bb']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\bb\":1}')))");
        assertAnswer("['a\\fb']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\fb\":1}')))");
        assertAnswer("['a\\001b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u0001b\":1}')))");
    }

    @Test
    public void controlCharacterKeys() {
        assertAnswer("['a\\000b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u0000b\":1}')))");
        assertAnswer("['a\\002b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u0002b\":1}')))");
        assertAnswer("['a\\013b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u000bb\":1}')))");
        assertAnswer("['a\\037b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u001fb\":1}')))");
        assertAnswer("['a\\177b']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u007fb\":1}')))");
        assertAnswer("a\u0080b", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u0080b\":1}')))");
        assertAnswer("a\u009fb", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"a\\\\u009fb\":1}')))");
        assertAnswer("['\\001']", "SELECT path FROM TABLE(FLATTEN(INPUT => PARSE_JSON('{\"\\\\u0001\":1}')))");
    }

    @Test
    public void sessionVariablePaths() {
        assertAnswer("Statement executed successfully.", "SET fl_p1 = 'a.'");
        assertAnswer("REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(PARSE_JSON('{}'), $fl_p1)");
        assertAnswer("REFUSED Invalid extraction path 'a.': dangling dot at position 2.",
            "SELECT GET_PATH(v, $fl_p1) FROM (SELECT PARSE_JSON('{}') AS v) WHERE 1 = 0");
        assertAnswer("Statement executed successfully.", "SET fl_p2 = 'a'");
        assertAnswer("1", "SELECT GET_PATH(PARSE_JSON('{\"a\":1}'), $fl_p2)");
        assertAnswer("Statement executed successfully.", "SET fl_p3 = 'n.  b'");
        assertAnswer("REFUSED Bad compound object's field path name 'n.  b' in GET_PATH",
            "SELECT GET_PATH(PARSE_JSON('{\"n\":{\"b\":1}}'), $fl_p3)");
    }

    @Test
    public void nullPathsAndSources() {
        assertAnswer("null", "SELECT GET_PATH(NULL, NULL)");
        assertAnswer("null", "SELECT GET_PATH(v, p) FROM (SELECT NULL::VARIANT AS v, NULL::VARCHAR AS p)");
        assertAnswer("null", "SELECT GET_PATH(v, p) FROM (SELECT PARSE_JSON('{}') AS v, 'a' AS p)");
        assertAnswer("REFUSED Bad compound object's field path name 'a[' in GET_PATH",
            "SELECT GET_PATH(NULL, p) FROM (SELECT 'a[' AS p)");
        assertAnswer("REFUSED Bad compound object's field path name '' in GET_PATH",
            "SELECT GET_PATH(NULL, p) FROM (SELECT '' AS p)");
        assertAnswer("REFUSED Bad compound object's field path name 'a..b' in GET_PATH",
            "SELECT GET_PATH(NULL, p) FROM (SELECT 'a..b' AS p)");
        assertAnswer("null", "SELECT GET_PATH(NULL, p) FROM (SELECT 'a' AS p)");
        assertAnswer("null", "SELECT GET_PATH(NULL, p) FROM (SELECT '\u00c0' AS p)");
        assertAnswer("REFUSED Invalid extraction path '\u00c0': invalid token at position 0.",
            "SELECT GET_PATH(v, '\u00c0') FROM (SELECT NULL::VARIANT AS v)");
        assertAnswer("null", "SELECT GET_PATH(v, p) FROM (SELECT NULL::VARIANT AS v, '\u00c0' AS p)");
    }
}
