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
 * LPAD and RPAD over a BINARY beside a text: the argument-type refusal names an unsized binary, a conversion's
 * answer, at the 64MB maximum and a sized one at its own width, in either place and with two arguments or three;
 * a pad of BINARY over a BINARY pads byte-wise (all live-verified).
 */
public class PadBinaryArgumentTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void padBinaryArguments() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'LPAD': (BINARY(67108864), NUMBER(1,0))",
            "SELECT SYSTEM$TYPEOF(LPAD(TO_BINARY('AB', 'HEX'), 3))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'RPAD': (BINARY(67108864), NUMBER(1,0))",
            "SELECT SYSTEM$TYPEOF(RPAD(TO_BINARY('AB', 'HEX'), 3))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (BINARY(67108864), NUMBER(1,0))",
            "SELECT LPAD(TO_BINARY('AB', 'HEX'), 3)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (BINARY(67108864), NUMBER(1,0), VARCHAR(1))",
            "SELECT LPAD(TO_BINARY('AB', 'HEX'), 3, 'x')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'RPAD': (BINARY(67108864), NUMBER(1,0), VARCHAR(1))",
            "SELECT RPAD(TO_BINARY('AB', 'HEX'), 3, 'x')");
        assertAnswer("ABABAB", "SELECT LPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('AB', 'HEX'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (VARCHAR(1), NUMBER(1,0), BINARY(67108864))",
            "SELECT LPAD('a', 3, TO_BINARY('AB', 'HEX'))");
        assertAnswer("CDCDAB", "SELECT LPAD(TO_BINARY('AB', 'HEX'), 3, TO_BINARY('CD', 'HEX'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_KEYS': (BINARY(67108864))",
            "SELECT OBJECT_KEYS(TO_BINARY('AB', 'HEX'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (BINARY(4), NUMBER(1,0))",
            "SELECT LPAD(TO_BINARY('AB', 'HEX')::BINARY(4), 3)");
    }
}
