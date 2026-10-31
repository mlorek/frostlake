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
 * Names written between backticks, which are read as part of the name, and a DESCRIBE whose property value is a
 * stage reference the account reads whole, followed by a faulty statement (all live-verified).
 */
public class BacktickedNameLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void backtickedNames() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter '`b`'", "DESCRIBE TABLE t1 `b` = 1");
        assertAnswer("X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE TABLE t1 (`b`)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`B`\"'",
            "SELECT `b` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier '\"`B`\"'",
            "SELECT 1 FROM t1 WHERE x = `b`");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'.",
            "DESCRIBE TABLE t1 (a) x = @s!=x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`B`\"'",
            "SELECT `b`");
        assertAnswer("1", "SELECT 1 AS `b`");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 9 near '32'. | syntax error "
                + "line 1 at position 11 unexpected '` '.",
            "SELECT `b c` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '``'.",
            "SELECT `` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '` '.",
            "SELECT ` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | Object '\"`T1`\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE \"`T1`\".",
            "SELECT x FROM `t1`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`X`\"'",
            "SELECT `x` FROM t1");
        assertAnswer("1", "SELECT 1 `b`");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`B`\".X'",
            "SELECT `b`.x FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T1.\"`X`\"'",
            "SELECT t1.`x` FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`B`\"'",
            "SELECT `B` FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'.",
            "DESCRIBE TABLE t1 (a) x = @s; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'.",
            "DESCRIBE TABLE t1 (a) x = 1 2; SELECT 1 x y");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
