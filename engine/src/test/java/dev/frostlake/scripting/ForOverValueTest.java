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
 * A FOR over a value loops over an ARRAY's elements; any other value is refused as {@code Expression in variant for
 * loop does not evaluate to an array or an object} — while the block compiles for a literal or a plain variable, as a
 * STATEMENT_ERROR at the FOR for a query's value. A field read from a plain variable is an invalid identifier while the
 * block compiles (all live-verified).
 */
public class ForOverValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aLoopOverAPlainValueIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE tq (k INT, v VARCHAR)");
        engine.execute("INSERT INTO tq VALUES (1, 'a'), (2, 'b')");
        assertCells(new String[][] {
            {"DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT 1) DO x := x + 1; END FOR; RETURN x; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : Expression in "
                + "variant for loop does not evaluate to an array or an object"},
            {"DECLARE x INT DEFAULT 0; BEGIN FOR r IN (5) DO x := x + 1; END FOR; RETURN x; END;",
                "REFUSED Expression in variant for loop does not evaluate to an array or an object"},
            {"DECLARE x INT DEFAULT 0; BEGIN FOR r IN x DO x := x + 1; END FOR; RETURN x; END;",
                "REFUSED Expression in variant for loop does not evaluate to an array or an object"},
            {"DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tq WHERE k = 1) DO x := x + 1; END FOR; "
                + "RETURN x; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : Expression in "
                + "variant for loop does not evaluate to an array or an object"},
            {"DECLARE x VARCHAR DEFAULT ''; a ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2, 3); BEGIN FOR r IN a DO x := "
                + "x || r::VARCHAR || ','; END FOR; RETURN x; END;",
                "1,2,3,"},
            {"DECLARE x INT DEFAULT 0; a ARRAY DEFAULT ARRAY_CONSTRUCT(); BEGIN FOR r IN a DO x := x + 1; END "
                + "FOR; RETURN x; END;",
                "0"},
            {"DECLARE x VARCHAR DEFAULT ''; v VARIANT DEFAULT PARSE_JSON('[1, 2]'); BEGIN FOR r IN v DO x := x "
                + "|| r::VARCHAR; END FOR; RETURN x; END;",
                "12"},
            {"DECLARE x VARCHAR DEFAULT ''; a ARRAY DEFAULT ARRAY_CONSTRUCT(1, NULL, 3); BEGIN FOR r IN a DO x "
                + ":= x || NVL(r::VARCHAR, 'n') || ','; END FOR; RETURN x; END;",
                "1,n,3,"},
            {"DECLARE x INT DEFAULT 0; y INT DEFAULT 1; BEGIN x := y.v; RETURN x; END;",
                "REFUSED SQL compilation error: error line 1 at position 53 | invalid identifier 'Y'"},
            {"DECLARE x INT DEFAULT 0; y INT DEFAULT 1; BEGIN IF (FALSE) THEN x := y.v; END IF; RETURN x; END;",
                "REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'Y'"},
            {"DECLARE x VARCHAR DEFAULT 'none'; s VARCHAR DEFAULT 'abc'; BEGIN FOR r IN s DO x := r; END FOR; "
                + "RETURN x; END;",
                "REFUSED Expression in variant for loop does not evaluate to an array or an object"},
        });
    }
}
