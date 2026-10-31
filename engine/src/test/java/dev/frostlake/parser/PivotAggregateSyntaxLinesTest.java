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
 * The syntax-error lines of a PIVOT that lists more than one aggregate: the comma after the first, a bracket
 * after the word a comma leads to, and the FOR column's first token (all live-verified).
 */
public class PivotAggregateSyntaxLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void pivotAggregateLines() {
        engine.execute("CREATE OR REPLACE TABLE s2088 (mon VARCHAR, amt INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax "
                + "error line 1 at position 51 unexpected '('. | syntax error line 1 at position 66 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt) AS total, COUNT(amt) AS n FOR mon IN ('JAN', 'FEB'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 52 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) FOR mon IN ('JAN', 'FEB'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax "
                + "error line 1 at position 51 unexpected '('. | syntax error line 1 at position 66 unexpected '('. | "
                + "syntax error line 1 at position 76 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt) AS total, COUNT(amt) AS n, MAX(amt) FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 61 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(DISTINCT amt) FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 50 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(*) FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 52 unexpected "
                + "'s2088'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) FOR s2088.mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 52 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) FOR mon IN (ANY))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 47 unexpected ')'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 57 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) AS n FOR mon IN ('JAN')) AS q (a, b, c)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 52 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) FOR mon IN ('JAN') DEFAULT ON NULL (0))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 54 unexpected '1'. | "
                + "syntax error line 1 at position 62 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(ROUND(amt, 1)) FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 45 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), amt FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax "
                + "error line 1 at position 48 unexpected '('. | syntax error line 1 at position 60 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt) total, COUNT(amt) n FOR mon IN ('JAN'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax "
                + "error line 1 at position 42 unexpected '('. | syntax error line 1 at position 52 unexpected 'mon'.",
            "SELECT * FROM s2088 PIVOT (SUM(amt), COUNT(amt) FOR mon IN ('JAN'), x)");
    }
}
