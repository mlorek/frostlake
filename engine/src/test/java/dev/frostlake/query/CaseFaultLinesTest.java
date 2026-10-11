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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A fault inside a CASE expression of a query is refused alone, whichever branch holds it and wherever the CASE
 * stands: no line names the CASE, and nothing after the fault is reported. A fault in a simple CASE's operand
 * refuses the WHEN after it. Every answer is live-verified.
 */
public class CaseFaultLinesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
    }

    @Test
    public void theFaultAlone() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN x y END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN x y END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "SELECT CASE id WHEN 1 THEN x y END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 ELSE x y END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "SELECT CASE WHEN x y THEN 1 END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM FZ WHERE CASE WHEN 1 = 1 THEN x y END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'y'.",
            "SELECT 1 FROM FZ WHERE id = 1 AND CASE WHEN 1 = 1 THEN x y END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'.",
            "SELECT 1 FROM FZ ORDER BY CASE WHEN 1 = 1 THEN x y END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT IFF(TRUE, x y, 1) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'y'.",
            "SELECT (x y) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 WHEN x y THEN 2 END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'. | syntax error "
                + "line 1 at position 16 unexpected 'WHEN'.",
            "SELECT CASE x y WHEN 1 THEN 2 END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 END x y FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'.",
            "SELECT 1, CASE WHEN 1 = 1 THEN x y END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN x y END, 2 FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'.",
            "SELECT UPPER(CASE WHEN 1 = 1 THEN x y END) FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN CASE WHEN 2 = 2 THEN x y END END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 x END FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN x y");
    }
}
