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
 * A NOT, AND or OR over values written for a name in a function's or FLATTEN's argument list binds its BOOLEAN,
 * and an incomplete one is refused at its ')' (all live-verified).
 */
public class NamedLogicalArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void namedLogicalArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION fb(x BOOLEAN) RETURNS BOOLEAN AS 'x'");
        engine.execute("CREATE OR REPLACE FUNCTION fb2(x BOOLEAN, y INT) RETURNS INT AS 'IFF(x, y, -y)'");
        engine.execute("CREATE OR REPLACE TABLE t1c (a INT)");
        engine.execute("INSERT INTO t1c VALUES (1)");
        assertAnswer("false", "SELECT fb(x => NOT TRUE)");
        assertAnswer("false", "SELECT fb(x => TRUE AND FALSE)");
        assertAnswer("true", "SELECT fb(x => TRUE OR FALSE)");
        assertAnswer("true", "SELECT fb(x => NOT TRUE OR TRUE)");
        assertAnswer("false", "SELECT fb(x => (NOT TRUE))");
        assertAnswer("-2", "SELECT fb2(x => NOT TRUE, y => 2)");
        assertAnswer("2", "SELECT fb2(y => 2, x => TRUE AND TRUE)");
        assertAnswer("-2", "SELECT fb2(NOT TRUE, 2)");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), OUTER => NOT FALSE))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,[2]]'), RECURSIVE => NOT FALSE))");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), OUTER => TRUE AND TRUE))");
        assertAnswer("1", "SELECT COUNT(*) FROM TABLE(FLATTEN(PARSE_JSON('[]'), OUTER => NOT FALSE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ')'.",
            "SELECT fb(x => NOT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "SELECT fb(x => NOT TRUE AND)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT fb(x => TRUE AND)");
        assertAnswer("true", "SELECT fb(x => 1 = 1 AND 2 = 2)");
        assertAnswer("true", "SELECT fb(x => NOT 1 = 2)");
        assertAnswer("BOOLEAN[SB1]", "SELECT SYSTEM$TYPEOF(fb(x => NOT TRUE))");
        assertAnswer("false", "SELECT fb(x => NOT TRUE) FROM t1c");
        assertAnswer("1",
            "SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[]'), OUTER => NOT FALSE AND TRUE, MODE => "
                + "'BOTH'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT fb(x => TRUE OR)");
        assertAnswer("true", "SELECT fb(x => NOT NOT TRUE)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
