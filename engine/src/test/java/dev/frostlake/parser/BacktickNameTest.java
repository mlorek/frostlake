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
 * A name between backticks keeps them and folds as an unquoted name does; at a character it cannot hold the
 * lexer gives it up there, and a backtick no name character follows is a character no token opens with (all
 * live-verified).
 */
public class BacktickNameTest extends MeasuredCellsTestSupport {

    @Test
    public void backtickedNames() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\"'",
            "SELECT `a` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\"'",
            "SELECT `a`");
        assertAnswer("1", "SELECT 1 AS `x`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 11 unexpected '` '.",
            "SELECT `a b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '``'.",
            "SELECT `` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\"'",
            "SELECT `a``b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | Object '\"`T1`\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE \"`T1`\".",
            "SELECT * FROM `t1`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T1.\"`A`\"'",
            "SELECT t1.`a` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`T1`\".A'",
            "SELECT `t1`.a FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 10 near '32'.",
            "SELECT a`b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.", "SELECT 1 `a");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 12 near '32'.",
            "SELECT 1 `ab cd");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '`'.",
            "SELECT 1 `");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '` '.",
            "SELECT 1 ` 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '` | '.",
            "SELECT 1 `\n2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`1`\"'",
            "SELECT `1`");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '`'.",
            "SELECT 1 x`");
        assertAnswer("1", "SELECT 1 `a` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 1 AS `a");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '` '.",
            "SELECT `a`b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier '\"`A`\"'",
            "SELECT 1 FROM t1 WHERE `a` = 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '\"`B`\"'",
            "SELECT 1 FROM t1 WHERE a = `b`");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '` '.",
            "SELECT 1 ` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '``'.",
            "SELECT 1 `` 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '``'.",
            "SELECT 1 ``");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '`'.", "SELECT `");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '` '.",
            "SELECT ` FROM t1");
    }

    @Test
    public void backtickedNamesAndTheirCharacters() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A.B`\"'",
            "SELECT `a.b`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A-B`\"'",
            "SELECT `a-b`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '34'. | syntax error "
                + "line 1 at position 11 unexpected '`'.",
            "SELECT `a\"b`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '39'. | syntax error "
                + "line 1 at position 11 unexpected '`'.",
            "SELECT `a'b`");
        assertAnswer("1|2", "SELECT 1 AS `a`, 2 AS `b`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\"'",
            "SELECT `a` `b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\".\"`B`\"'",
            "SELECT `a`.`b` FROM t1");
        assertAnswer("1", "SELECT 1 FROM t1 `x`");
        assertAnswer("1", "SELECT `x`.a FROM t1 `x`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`SELECT`\"'",
            "SELECT `SELECT`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A$B`\"'",
            "SELECT `a$b`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '32'. | syntax error "
                + "line 1 at position 13 unexpected '` '.",
            "SELECT 1 `a b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 13 unexpected '`'.",
            "SELECT `a b c`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '32'. | syntax error "
                + "line 1 at position 27 unexpected '` '.",
            "SELECT 1 FROM t1 WHERE `a b` = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '`\u00e4'.",
            "SELECT `\u00e4`");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'b'.",
            "SELECT 1 AS `a`b");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"`A`\".", "SELECT `a`(1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier '\"`A`\"'",
            "SELECT ABS(`a`) FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier '\"`A`\"'",
            "SELECT 1 FROM t1 ORDER BY `a`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\"'",
            "SELECT `a`::INT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '`	'.",
            "SELECT 1 `	2");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '10'. | syntax error "
                + "line 2 at position 1 unexpected '`'.",
            "SELECT `a\nb`");
        assertAnswer("1", "SELECT 1 AS `A` ORDER BY `a`");
        assertAnswer("1", "SELECT * FROM (SELECT 1 AS `q`) WHERE `q` = 1");
    }
}
