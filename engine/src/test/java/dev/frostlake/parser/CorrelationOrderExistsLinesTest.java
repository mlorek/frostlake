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
 * Where a correlated query two levels deep is refused when an ORDER BY's stands beside a select list's, and an
 * EXISTS or a NOT EXISTS written as an operand of a value operator (all live-verified).
 */
public class CorrelationOrderExistsLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void correlationOrderAndExists() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 127",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz WHERE (SELECT g.id FROM g WHERE g.v > fz.id "
                + "LIMIT 1) > 0 ORDER BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 20 unexpected 'SELECT'. | syntax error line 1 at position 35 "
                + "unexpected ')'.",
            "SELECT (1 + EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 70",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz ORDER BY (SELECT (SELECT g2.v FROM g g2 "
                + "WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 85",
            "SELECT id FROM fz WHERE (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1) > 0 ORDER BY (SELECT (SELECT "
                + "g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz WHERE (SELECT g.id FROM g WHERE g.v > fz.id "
                + "LIMIT 1) > 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 118",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1) "
                + "FROM fz ORDER BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28",
            "SELECT id FROM fz ORDER BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 69",
            "SELECT (SELECT g.id FROM g WHERE g.v > fz.id LIMIT 1) FROM fz WHERE (SELECT v FROM g WHERE g.id > fz.id "
                + "LIMIT 1) > 0 ORDER BY (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 157",
            "SELECT (SELECT (SELECT g2.v FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5) FROM fz WHERE (SELECT "
                + "g.id FROM g WHERE g.v > fz.id LIMIT 1) > 0 ORDER BY (SELECT v FROM g WHERE g.id > fz.id LIMIT 1)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 122",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) FROM fz WHERE (SELECT g.id FROM g WHERE g.v > fz.id "
                + "LIMIT 1) > 0 AND (SELECT v FROM g WHERE g.id > fz.id LIMIT 1) > 0 ORDER BY (SELECT (SELECT g2.v "
                + "FROM g g2 WHERE g2.id = fz.id) FROM g WHERE g.id = 5)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 20 unexpected 'SELECT'. | syntax error line 1 at position 35 "
                + "unexpected ')'.",
            "SELECT (1 + EXISTS (SELECT 1 FROM g))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '::'.",
            "SELECT EXISTS (SELECT 1 FROM g)::INT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'EXISTS'. | "
                + "syntax error line 1 at position 19 unexpected 'SELECT'. | syntax error line 1 at position 34 "
                + "unexpected ')'.",
            "SELECT 1 + EXISTS (SELECT 1 FROM g)::INT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '::'. | syntax "
                + "error line 1 at position 37 unexpected ')'.",
            "SELECT (EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'NOT'. | syntax "
                + "error line 1 at position 24 unexpected 'SELECT'. | syntax error line 1 at position 39 unexpected "
                + "')'.",
            "SELECT (1 + NOT EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer("2", "SELECT (1 + (EXISTS (SELECT 1 FROM g))::INT)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
