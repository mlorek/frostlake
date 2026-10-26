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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A call given an argument type it does not take is refused while the statement compiles, placed at the call:
 * BOOLAND and BOOLOR over BOOLEANs, VECTOR_AVG and VECTOR_SUM over a VARIANT, a bit aggregate over a BOOLEAN or a
 * DATE - at the top level, and inside a subquery holding an outer aggregate ahead of the aggregate's place
 * (all live-verified).
 */
public class PlacedArgumentTypeRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void placedRefusals() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, f FLOAT, d NUMBER(10,2), dt DATE, "
            + "va VARIANT)");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'a', 1.5, 2.25, '2020-01-01'::DATE, PARSE_JSON('1')");
        engine.execute("INSERT INTO fz SELECT 7, FALSE, 'b', 2.5, 3.25, '2020-01-02'::DATE, PARSE_JSON('2')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'VECTOR_AVG': (VARIANT)",
            "SELECT VECTOR_AVG(va) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (BOOLEAN)",
            "SELECT BITAND_AGG(b) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (DATE)",
            "SELECT BITAND_AGG(dt) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT BOOLAND(b, b) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for "
                + "function 'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT COUNT_IF(BOOLAND(b, b)) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITOR_AGG': (BOOLEAN)",
            "SELECT BITOR_AGG(b) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'VECTOR_SUM': (VARIANT)",
            "SELECT VECTOR_SUM(va) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITXOR_AGG': (DATE)",
            "SELECT BITXOR_AGG(dt) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BOOLOR': (BOOLEAN, BOOLEAN)",
            "SELECT BOOLOR(b, b) FROM fz");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function 'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE BOOLAND(b, b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'VECTOR_AVG': (VARIANT)",
            "SELECT VECTOR_AVG(va) FROM fz WHERE id > 100");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'VECTOR_AVG': (VARIANT)",
            "SELECT id FROM fz WHERE EXISTS (SELECT VECTOR_AVG(fz.va) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | Invalid argument types for "
                + "function 'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(BOOLAND(fz.b, fz.b)) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITAND_AGG': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.b) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITAND_AGG': (DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITAND_AGG(fz.dt) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITOR_AGG': (BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITOR_AGG(fz.b) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'VECTOR_SUM': (VARIANT)",
            "SELECT id FROM fz WHERE EXISTS (SELECT VECTOR_SUM(fz.va) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 39 | Invalid argument types for "
                + "function 'BITXOR_AGG': (DATE)",
            "SELECT id FROM fz WHERE EXISTS (SELECT BITXOR_AGG(fz.dt) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 48 | Invalid argument types for "
                + "function 'BOOLOR': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT COUNT_IF(BOOLOR(fz.b, fz.b)) FROM g)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 43 | Invalid argument types for "
                + "function 'BOOLAND': (BOOLEAN, BOOLEAN)",
            "SELECT id FROM fz WHERE EXISTS (SELECT MAX(BOOLAND(fz.b, fz.b)) FROM g)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
