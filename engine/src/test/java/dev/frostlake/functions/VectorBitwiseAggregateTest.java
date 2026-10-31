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
 * BITAND_AGG, BITOR_AGG and BITXOR_AGG, as aggregates and as window functions, and CONDITIONAL_TRUE_EVENT refuse a
 * VECTOR by its type as the statement compiles, whatever rows reach them - "Invalid argument types for function
 * 'BITAND_AGG': (VECTOR(FLOAT, 2))" - where CONDITIONAL_CHANGE_EVENT takes one (all live-verified).
 */
public class VectorBitwiseAggregateTest extends MeasuredCellsTestSupport {

    @Test
    public void vectorsRefusedByType() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table VT42 successfully created.",
            "CREATE OR REPLACE TABLE vt42 (g INT, v VECTOR(FLOAT, 2), n INT, w VECTOR(INT, 2))");
        assertAnswer("2",
            "INSERT INTO vt42 SELECT 1, [1,2]::VECTOR(FLOAT,2), 1, [1,2]::VECTOR(INT,2) UNION ALL SELECT 1, [3,"
                + "4]::VECTOR(FLOAT,2), 2, [3,4]::VECTOR(INT,2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITAND_AGG(v) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITAND_AGG(v) FROM vt42 WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITOR_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITOR_AGG(v) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITOR_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITOR_AGG(v) FROM vt42 WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITXOR_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITXOR_AGG(v) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITXOR_AGG': (VECTOR(INT, 2))",
            "SELECT BITXOR_AGG(w) FROM vt42 WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (VECTOR(FLOAT, 2))",
            "SELECT BITAND_AGG(v) OVER () FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONDITIONAL_TRUE_EVENT': (VECTOR(FLOAT, 2))",
            "SELECT CONDITIONAL_TRUE_EVENT(v) OVER (ORDER BY n) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONDITIONAL_TRUE_EVENT': (VECTOR(FLOAT, 2))",
            "SELECT CONDITIONAL_TRUE_EVENT(v) OVER (ORDER BY n) FROM vt42 WHERE 1 = 0");
        assertAnswer("0 ;; 1", "SELECT CONDITIONAL_CHANGE_EVENT(v) OVER (ORDER BY n) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(VT42.V)] for parameter 'TO_BOOLEAN'",
            "SELECT BOOLAND_AGG(v) FROM vt42 WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND': (VECTOR(FLOAT, 2), NUMBER(1,0))",
            "SELECT BITAND(v, 1) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (VECTOR(INT, 2))",
            "SELECT BITAND_AGG(w) FROM vt42");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONDITIONAL_TRUE_EVENT': (VECTOR(INT, 2))",
            "SELECT CONDITIONAL_TRUE_EVENT(w) OVER (ORDER BY n) FROM vt42");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
