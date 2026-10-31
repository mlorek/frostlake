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
 * A name with an empty middle part in a statement that breaks off at its end is reported as in a finished one,
 * and a select item's alias followed by IN refuses the IN (all live-verified).
 */
public class UnfinishedEmptyPartLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void unfinishedAndShowLines() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x) FROM");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'FROM'. | syntax "
                + "error line 1 at position 16 unexpected '<EOF>'.",
            "SELECT T..x FROM");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IN'.",
            "SELECT SHOW TABLES IN a...c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IN'.",
            "SELECT SHOW TABLES IN a");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SHOW'",
            "SELECT SHOW TABLES");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IN'.",
            "SELECT SHOW x IN y");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreUnfinishedAndShowLines() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TABLES'.",
            "SELECT SHOW TERSE TABLES");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLE'.",
            "SELECT DESCRIBE TABLE T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIKE'.",
            "SELECT SHOW TABLES LIKE 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IN'.",
            "SELECT SHOW TABLES IN a; SELECT 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SHOW'",
            "SELECT SHOW TABLES, 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'IN'.",
            "SELECT SHOW TABLES IN");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x) FROM WHERE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x), FROM");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 14 unexpected '.'.",
            "SELECT COUNT(T..x) FROM T WHERE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ','. | syntax "
                + "error line 1 at position 23 unexpected ')'. | syntax error line 1 at position 20 unexpected '.'.",
            "SELECT T..x, COUNT(T..y) FROM");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
