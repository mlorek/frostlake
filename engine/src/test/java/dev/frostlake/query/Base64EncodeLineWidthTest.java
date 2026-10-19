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
 * BASE64_ENCODE declares its width with the line breaks its second argument asks for: an integer literal L
 * below the unbroken width W adds ceil(W / L) - 1, a 0 or an L of W or more adds nothing, and any other value
 * - an expression, a cast, a NULL - is not read, so the width allows a break after every character. A table
 * created from the call holds what it stores. Every answer is live-verified.
 */
public class Base64EncodeLineWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void theWidthWithItsLineBreaks() {
        engine.execute(
            "CREATE OR REPLACE TABLE g (v NUMBER(6,0), k INT)");
        engine.execute(
            "INSERT INTO g VALUES (-1000, 1), (1, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t847 (n NUMBER(5,2), n10 NUMBER(10,2))");
        engine.execute(
            "INSERT INTO t847 VALUES (1.5, 17.25), (2.5, 3.5)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE TABLE src (g VARCHAR(5))");
        engine.execute(
            "INSERT INTO src VALUES ('\u00e9\u00e9\u00e9\u00e9\u00e9')");
        assertShape(
            "BASE64_ENCODE('A', 1):VARCHAR(15)",
            "SELECT BASE64_ENCODE('a', 1)");
        assertShape(
            "BASE64_ENCODE('A', 1 * 1):VARCHAR(15)",
            "SELECT BASE64_ENCODE('a', 1 * 1)");
        assertShape(
            "BASE64_ENCODE('A', -1 * 1):VARCHAR(15)",
            "SELECT BASE64_ENCODE('a', -1 * 1)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 2):VARCHAR(47)",
            "SELECT BASE64_ENCODE('abcdef', 2)");
        assertShape(
            "BASE64_ENCODE(G, 1):VARCHAR(55)",
            "SELECT BASE64_ENCODE(g, 1) FROM rt");
        assertShape(
            "BASE64_ENCODE('A'):VARCHAR(8)",
            "SELECT BASE64_ENCODE('a')");
        assertShape(
            "BASE64_ENCODE('A', 0):VARCHAR(8)",
            "SELECT BASE64_ENCODE('a', 0)");
        assertShape(
            "BASE64_ENCODE('ABC', 76):VARCHAR(16)",
            "SELECT BASE64_ENCODE('abc', 76)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 3):VARCHAR(42)",
            "SELECT BASE64_ENCODE('abcdef', 3)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 4):VARCHAR(39)",
            "SELECT BASE64_ENCODE('abcdef', 4)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 7):VARCHAR(36)",
            "SELECT BASE64_ENCODE('abcdef', 7)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 8):VARCHAR(35)",
            "SELECT BASE64_ENCODE('abcdef', 8)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 100):VARCHAR(32)",
            "SELECT BASE64_ENCODE('abcdef', 100)");
        assertShape(
            "BASE64_ENCODE(G, 4):VARCHAR(34)",
            "SELECT BASE64_ENCODE(g, 4) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 76):VARCHAR(28)",
            "SELECT BASE64_ENCODE(g, 76) FROM rt");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 2, '+/='):VARCHAR(47)",
            "SELECT BASE64_ENCODE('abcdef', 2, '+/=')");
        assertShape(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid argument for function "
                + "[BASE64_ENCODE] unexpected argument [maximum line length] at position 2,",
            "SELECT BASE64_ENCODE('abcdef', -2)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', NULL):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', NULL)");
        assertShape(
            "REFUSED SQL compilation error: | argument 2 to function BASE64_ENCODE needs to be constant, found "
                + "'RT.N'",
            "SELECT BASE64_ENCODE(g, n) FROM rt");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 1):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', 1)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 5):VARCHAR(38)",
            "SELECT BASE64_ENCODE('abcdef', 5)");
        assertShape(
            "BASE64_ENCODE('ABCDEFG', 4):VARCHAR(49)",
            "SELECT BASE64_ENCODE('abcdefg', 4)");
        assertShape(
            "BASE64_ENCODE('', 1):VARCHAR(15)",
            "SELECT BASE64_ENCODE('', 1)");
        assertShape(
            "BASE64_ENCODE(G):VARCHAR(28)",
            "SELECT BASE64_ENCODE(g) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 0):VARCHAR(28)",
            "SELECT BASE64_ENCODE(g, 0) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 2):VARCHAR(41)",
            "SELECT BASE64_ENCODE(g, 2) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 27):VARCHAR(29)",
            "SELECT BASE64_ENCODE(g, 27) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 28):VARCHAR(28)",
            "SELECT BASE64_ENCODE(g, 28) FROM rt");
        assertShape(
            "BASE64_ENCODE(G, 29):VARCHAR(28)",
            "SELECT BASE64_ENCODE(g, 29) FROM rt");
        assertShape(
            "HEX_ENCODE('A', 1):VARCHAR(8)",
            "SELECT HEX_ENCODE('a', 1)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 2::INT):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', 2::INT)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 1 + 1):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', 1 + 1)");
        assertShape(
            "BASE64_ENCODE(V::VARCHAR, 1):VARCHAR(134217728)",
            "SELECT BASE64_ENCODE(v::VARCHAR, 1) FROM rt");
        assertShape(
            "BASE64_ENCODE('ABCDEF', 2.5):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', 2.5)");
        assertShape(
            "BASE64_ENCODE('ABCDEF', '2'):VARCHAR(63)",
            "SELECT BASE64_ENCODE('abcdef', '2')");
        assertAnswer(
            "VARCHAR(15)[LOB]|VARCHAR(47)[LOB]",
            "SELECT SYSTEM$TYPEOF(BASE64_ENCODE('a', 1)), SYSTEM$TYPEOF(BASE64_ENCODE('abcdef', 2))");
        assertAnswer(
            "Table B64 successfully created.",
            "CREATE OR REPLACE TABLE b64 AS SELECT BASE64_ENCODE('a', 1) AS x, BASE64_ENCODE('abcdef', 2) AS y");
        assertAnswer(
            "w | 6 | n | D | q | c | O | p | w | 6 | n | D | q | Q | = | =|31",
            "SELECT BASE64_ENCODE(g, 1) AS x, LENGTH(BASE64_ENCODE(g, 1)) FROM src");
        assertAnswer(
            "Table DST successfully created.",
            "CREATE OR REPLACE TABLE dst AS SELECT BASE64_ENCODE(g, 1) AS x FROM src");
        assertAnswer(
            "31",
            "SELECT LENGTH(x) FROM dst");
        assertAnswer(
            "X|VARCHAR(55)",
            "DESCRIBE TABLE dst ->> SELECT \"name\", \"type\" FROM $1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid argument for function "
                + "[BASE64_ENCODE] unexpected argument [maximum line length] at position 2,",
            "SELECT BASE64_ENCODE('abcdef', -2)");
        assertAnswer(
            "11|10|9",
            "SELECT LENGTH(BASE64_ENCODE('abcdef', 2)), LENGTH(BASE64_ENCODE('abcdef', 3)), "
                + "LENGTH(BASE64_ENCODE('abcdef', 7))");
    }
}
