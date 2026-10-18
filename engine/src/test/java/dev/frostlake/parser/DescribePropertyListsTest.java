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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DESCRIBE property's value that is a list of property lists, and a property left without its name before a
 * block statement's ';' (live-verified): {@code x = ((a = 1 b = 2), (c = (1, 2)))} is read and the property
 * refused by its name, TYPE by the kind of its value; an item no property opens is refused at its first token
 * and skipped to its ')'; a value where an '=' is due is read with the '=' named as missing; a token between
 * items is passed over before a ',' or a ')' and otherwise gives the list up; and a name due at a block
 * statement's ';' reads the next statement's first word as that name.
 */
public class DescribePropertyListsTest extends MeasuredCellsTestSupport {

    @Test
    public void aListOfPropertyListsIsReadAsTheAccountReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE t7 (x INT)");
        assertCells(new String[][] {
            {"DESCRIBE TABLE t1 x = ((1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax error "
                + "line 1 at position 26 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((1), 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax error "
                + "line 1 at position 28 unexpected '2'."},
            {"DESCRIBE TABLE t1 x = ((a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = (((1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                + "line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 TYPE = ((COLUMNS))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'."},
            {"DESCRIBE VIEW t1 x = ((1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'. | syntax error "
                + "line 1 at position 25 unexpected ')'."},
            {"BEGIN DESCRIBE TABLE t7 x = ((1)); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax error "
                + "line 1 at position 32 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'."},
            {"DESCRIBE TABLE t1 x = ((a INT))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'INT'."},
            {"DESCRIBE TABLE t1 x = ((a = 1))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a), (b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax error "
                + "line 1 at position 30 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a b), c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'. | syntax error "
                + "line 1 at position 30 unexpected 'c'."},
            {"DESCRIBE TABLE t1 x = ((a, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a.b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax error "
                + "line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a) y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax error "
                + "line 1 at position 27 unexpected 'y'."},
            {"DESCRIBE TABLE t1 x = ((a b c))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'. | syntax error "
                + "line 1 at position 29 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'."},
            {"DESCRIBE TABLE t1 x = ((a 'b'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''b''."},
            {"DESCRIBE TABLE t1 x = (('a'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = (())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ')'. | syntax error "
                + "line 1 at position 25 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a b, c d))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'. | syntax error "
                + "line 1 at position 31 unexpected 'd'."},
            {"DESCRIBE TABLE t1 x = (1, (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('."},
            {"DESCRIBE TABLE t1 x = (a, (b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('."},
            {"DESCRIBE TABLE t1 x = ((a b) y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'. | syntax error "
                + "line 1 at position 29 unexpected 'y'."},
            {"DESCRIBE TABLE t1 x = ((a = 1, b = 2))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((-1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '-'. | syntax error "
                + "line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((NULL))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'NULL'. | syntax "
                + "error line 1 at position 29 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a b)) y = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'. | syntax error line 1 at position 38 unexpected '1'."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'. | syntax error line 1 at position 34 unexpected ';'. | "
                + "syntax error line 1 at position 35 unexpected '<EOF>'."},
            {"BEGIN DESCRIBE TABLE t7 (a) x = ,; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ';'. | syntax error line 1 at position 42 unexpected '1'."},
            {"BEGIN DESCRIBE TABLE t7 x = , y; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 31 unexpected ';'."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; LET y := 1; RETURN y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'. | syntax error line 1 at position 35 unexpected 'y'. | "
                + "syntax error line 1 at position 37 unexpected ':='."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; RETURN; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'. | syntax error line 1 at position 37 unexpected ';'."},
            {"BEGIN DESCRIBE TABLE t7 x =; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'."},
            {"BEGIN DESCRIBE TABLE t7 x = 1,; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax error "
                + "line 1 at position 39 unexpected '1'."},
            {"BEGIN DESCRIBE TABLE t7 x = ,; y := 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ';'. | syntax error line 1 at position 33 unexpected ':='."},
            {"DESCRIBE TABLE t7 x = ,;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ';'."},
        });
    }

    @Test
    public void itsPropertiesAndTheirValues() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        assertCells(new String[][] {
            {"DESCRIBE TABLE t1 TYPE = ((a = 1))",
                "REFUSED SQL compilation error: | invalid value [TOK_LIST] for parameter 'TYPE'"},
            {"DESCRIBE TABLE t1 x = ((a = 1), (b = 2))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = 1), 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '2'."},
            {"DESCRIBE TABLE t1 x = ((a = 1 b = 2))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = (1, 2)))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = ((b = 1))))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((TRUE))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a = TRUE))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((\"a\"))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a =))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a = 1) y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'."},
            {"DESCRIBE TABLE t1 TYPE = STAGE x = ((a = 1))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((1 = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax error "
                + "line 1 at position 30 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a.b = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'."},
            {"DESCRIBE TABLE t1 x = ((a b c d))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'b'. | syntax error "
                + "line 1 at position 30 unexpected 'd'."},
            {"DESCRIBE TABLE t1 x = ((a = b))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = 1, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a = 'v'))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = (('a' = 1))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = -1))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = 1)) y = 2", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = 1)), y = 2", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((a = 1) (b = 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                + "line 1 at position 37 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a 1, b 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax error "
                + "line 1 at position 31 unexpected '2'."},
            {"DESCRIBE TABLE t1 x = ((a = 1)(b = 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax error "
                + "line 1 at position 36 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((FALSE = 1))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = ((NULL = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'NULL'. | syntax "
                + "error line 1 at position 33 unexpected ')'."},
            {"DESCRIBE TABLE t1 x = ((a = NULL))", "REFUSED SQL compilation error: | invalid parameter 'x'"},
            {"DESCRIBE TABLE t1 x = (((a = 1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected ')'."},
        });
    }
}
