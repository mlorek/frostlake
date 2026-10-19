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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * LET with a type and no value declares the variable NULL of that type, assignable later; LET with neither type nor
 * value and a second LET of one name in one block are refused while the block compiles, and an inner block's LET
 * shadows an outer variable of its name (all live-verified).
 */
public class LetWithoutValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aTypedLetWithoutAValueIsNull() {
        assertAnswer("null", "BEGIN LET r VARCHAR; RETURN r; END;");
        assertAnswer("a", "BEGIN LET r VARCHAR; r := 'a'; RETURN r; END;");
        assertAnswer("null", "BEGIN LET r INT; RETURN r; END;");
        assertAnswer("NUMBER(5,2)[SB4]", "BEGIN LET r NUMBER(5,2); RETURN SYSTEM$TYPEOF(r); END;");
        assertAnswer("true", "BEGIN LET r FLOAT; RETURN r IS NULL; END;");
        assertAnswer("null", "BEGIN LET r VARIANT; RETURN r; END;");
        assertAnswer("true", "BEGIN LET r DATE; r := CURRENT_DATE(); RETURN r IS NOT NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 6 |  variable 'R' must have either a "
                + "type or an initializer to infer a type from",
            "BEGIN LET r; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 |  Variable with name 'R' declared "
                + "twice.",
            "BEGIN LET r INT; LET r INT; RETURN 1; END;");
        assertAnswer("true", "BEGIN LET r INT; LET s := r + 1; RETURN s IS NULL; END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 |  Variable with name 'R' declared "
                + "twice.",
            "DECLARE r INT; BEGIN LET r INT; RETURN 1; END;");
        assertAnswer("null", "DECLARE r INT DEFAULT 5; BEGIN BEGIN LET r INT; RETURN r; END; END;");
        assertAnswer("1", "BEGIN LET r RESULTSET; RETURN 1; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : String 'abcd' "
                + "is too long and would be truncated",
            "BEGIN LET r VARCHAR(3); r := 'abcd'; RETURN r; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Numeric value "
                + "'x' is not recognized",
            "BEGIN LET r INT; r := 'x'; RETURN r; END;");
        assertAnswer("BOOLEAN[SB1]", "BEGIN LET r BOOLEAN; RETURN SYSTEM$TYPEOF(r); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 |  Variable with name 'R' declared "
                + "twice.",
            "BEGIN LET r VARCHAR; LET r := 'b'; RETURN r; END;");
        assertAnswer("true", "BEGIN LET r ARRAY; RETURN r IS NULL; END;");
    }
}
