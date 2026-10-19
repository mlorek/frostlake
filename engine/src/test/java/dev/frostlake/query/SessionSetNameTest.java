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
 * A session SET whose name its '=' does not follow is refused at the name, whatever stands after it: a
 * number, a word, a string, an operator, a clause keyword. When the statement ends straight after the name
 * its semicolon or the end of the input is refused too, and the statement after it is passed over while
 * the ones after that are read. The same holds for a SET that is not the script's first statement and for
 * one inside a block. A SET that assigns with ':=' is a block's statement: written as a script's own it
 * is refused at its name, and nothing more is reported. Every answer is live-verified.
 */
public class SessionSetNameTest extends MeasuredCellsTestSupport {

    @Test
    public void theNameIsRefusedAndTheStatementsEndAfterIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected '<EOF>'.",
            "SET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '\"x\"'. | syntax "
                + "error line 1 at position 7 unexpected '<EOF>'.",
            "SET \"x\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'.",
            "SET x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '\"x\"'. | syntax "
                + "error line 1 at position 7 unexpected ';'.",
            "SET \"x\"; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'. | syntax error line 1 at position 28 unexpected 'y'.",
            "SET x; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "SET x 1; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 16 unexpected 'x'. | syntax error line 1 at position 17 unexpected "
                + "';'. | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 +; SET x; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'y'.",
            "SET x = 1 y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '1'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "SET (x, y) 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SET x = ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ';'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SET x =; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 'q'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '$x'.",
            "SET $x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x := 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
            "UNSET x 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected '<EOF>'.",
            "UNSET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 3 unexpected '<EOF>'.",
            "SET");
    }

    @Test
    public void whateverFollowsTheName() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x (1) = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x y = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1 = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'. | syntax "
                + "error line 1 at position 16 unexpected ';'. | syntax error line 1 at position 39 unexpected 'y'.",
            "SET x = 1; SET y; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x [1]");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x::int");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '1'.",
            "SET 1 = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected ''x''.",
            "SET 'x' = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '='.",
            "SET = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
            "SET (x) 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '<EOF>'.",
            "SET (x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
            "SET (x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '<EOF>'.",
            "SET (x) =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x := 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x AS y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x TO 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x LIKE 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'.",
            "SET x;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'.",
            "SET x;;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 6 unexpected ';'.",
            "SET x ;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 2 at position 0 unexpected ';'.",
            """
                SET x
                ; SELECT 1 x y""");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '\"x\"'.",
            "SET \"x\" 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ';'. | syntax "
                + "error line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 14 unexpected "
                + "';'. | syntax error line 1 at position 37 unexpected 'y'.",
            "SET x =; SET y; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'. | syntax error line 1 at position 35 unexpected 'y'.",
            "SET x; SET y; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'. | syntax error line 1 at position 32 unexpected 'y'.",
            "SET x; SELECT 2 x y; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SET x 1; SELECT 2 x y; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'.",
            "SET x; SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1; SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x 1;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'.",
            "SET x; x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected '<EOF>'.",
            "SELECT 1; SET x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SELECT 1; SET x 1; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 5 unexpected ';'. | syntax error line 1 at position 38 unexpected 'y'.",
            "SET x; SELECT 2; SELECT 3; SELECT 4 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x BETWEEN 1 AND 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x - 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x * 2 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x || 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x : y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x => 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x <> 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x < 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x != 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '='.",
            "SET x = = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected '$x'.",
            "SET $x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x $1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x ?");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x @s");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "SET x 'q'; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x WHERE 1 = 1");
    }

    @Test
    public void inABlockAndWithTheAssignmentOperator() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, s VARCHAR(5), o OBJECT, v VARIANT, tm TIME, d DATE, ts "
            + "TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bo BOOLEAN)");
        engine.execute("INSERT INTO tt SELECT 86400, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), PARSE_JSON('{\"x\": "
            + "\"2024-01-02\"}'), '03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, TRUE");
        assertAnswer(
            "2",
            "BEGIN LET y := 1; SET y := 2; RETURN y; END");
        assertAnswer(
            "2",
            "DECLARE y INT; BEGIN SET y := 2; RETURN y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 11 unexpected ';'.",
            "BEGIN SET x; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
            "BEGIN LET a := 1; SET x 1; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 4 unexpected 'x'.",
            "SET x := 1");
    }
}
