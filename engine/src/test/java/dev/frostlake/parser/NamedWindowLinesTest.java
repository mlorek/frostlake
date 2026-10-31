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
 * The syntax-error lines of a named window, a WINDOW clause the account does not take: every name an OVER reads,
 * then the WINDOW clause's own name and the token after its ORDER BY's first item, or the WINDOW itself after a
 * WHERE (all live-verified).
 */
public class NamedWindowLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void namedWindows() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'w'. | syntax "
                + "error line 1 at position 36 unexpected 'w'. | syntax error line 1 at position 53 unexpected 'ROWS'.",
            "SELECT SUM(v) OVER w FROM ev WINDOW w AS (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'w'. | syntax "
                + "error line 1 at position 36 unexpected 'w'. | syntax error line 1 at position 52 unexpected ')'.",
            "SELECT SUM(v) OVER w FROM ev WINDOW w AS (ORDER BY v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'w'. | syntax "
                + "error line 1 at position 36 unexpected 'w'. | syntax error line 1 at position 67 unexpected ')'.",
            "SELECT SUM(v) OVER w FROM ev WINDOW w AS (PARTITION BY v ORDER BY v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'w'. | syntax "
                + "error line 1 at position 40 unexpected ')'.",
            "SELECT v FROM ev WINDOW w AS (ORDER BY v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'w'. | syntax "
                + "error line 1 at position 34 unexpected 'w'. | syntax error line 1 at position 51 unexpected 'w'. | "
                + "syntax error line 1 at position 67 unexpected ')'.",
            "SELECT SUM(v) OVER w, MAX(v) OVER w FROM ev WINDOW w AS (ORDER BY v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'w'. | syntax "
                + "error line 1 at position 41 unexpected 'WINDOW'.",
            "SELECT SUM(v) OVER w FROM ev WHERE v > 0 WINDOW w AS (ORDER BY v)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'w'. | syntax "
                + "error line 1 at position 38 unexpected 'w'. | syntax error line 1 at position 54 unexpected ')'.",
            "SELECT SUM(v) OVER (w) FROM ev WINDOW w AS (ORDER BY v)");
    }
}
