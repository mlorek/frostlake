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
 * A refusal raised while a grouped query computes an aggregate's argument is placed in the statement: a
 * correlated subquery under a conditional is refused at its SELECT, on the line it is written on, in the
 * select list and in ORDER BY alike. A fault of the row keeps its own sentence. Every answer is live-verified.
 */
public class AggregateArgumentRefusalPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void theSubqueryUnderAConditional() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 22",
            "SELECT b, MAX(IFF(b, (SELECT MAX(fz.id + g.v) FROM g), 0)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 49",
            "SELECT b FROM fz GROUP BY b ORDER BY MAX(IFF(b, (SELECT MAX(fz.id + g.v) FROM g), 0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 32",
            "SELECT b, MAX(CASE WHEN b THEN (SELECT MAX(fz.id + g.v) FROM g) END) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 24",
            "SELECT b, SUM(COALESCE((SELECT MAX(fz.id + g.v) FROM g), 0)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED Division by zero",
            "SELECT b, MAX(IFF(b, 1 / 0, 0)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED Numeric value 'x' is not recognized",
            "SELECT b, MAX(IFF(b, TO_NUMBER('x'), 0)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED Single-row subquery returns more than one row.",
            "SELECT b, MAX(IFF(b, (SELECT g.v FROM g), 0)) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 2, position 2",
            "SELECT b, MAX(IFF(b,\n (SELECT MAX(fz.id + g.v) FROM g), 0)) FROM fz GROUP BY b");
    }
}
