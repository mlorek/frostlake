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
 * A LET that gives its variable a type and no value declares it NULL of the type, as a DECLARE section's item does:
 * it reads NULL, takes an assignment, a SELECT … INTO and a CALL … INTO, and a second LET of the name is refused as
 * declared twice; a LET with neither a type nor a value is refused (all live-verified).
 */
public class LetTypedNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aTypedLetWithNoValueIsNull() {
        assertCells(new String[][] {
            {"BEGIN LET q INT; RETURN q; END", "null"},
            {"BEGIN LET r VARCHAR; CALL upper('a') INTO :r; RETURN r; END;", "A"},
            {"BEGIN LET q; RETURN q; END",
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from"},
            {"BEGIN LET q INT; q := 5; RETURN q; END", "5"},
            {"BEGIN LET q NUMBER(10, 2); RETURN SYSTEM$TYPEOF(q); END", "NUMBER(10,2)[SB8]"},
            {"BEGIN LET q INT DEFAULT 3; RETURN q; END", "3"},
            {"BEGIN LET q VARCHAR; RETURN COALESCE(q, 'none'); END", "none"},
            {"BEGIN LET q INT; LET q INT; RETURN q; END",
                "REFUSED SQL compilation error: error line 1 at position 17 |  Variable with name 'Q' declared twice."},
            {"BEGIN LET q INT; RETURN q + 1; END", "null"},
            {"BEGIN LET q BOOLEAN; IF (q IS NULL) THEN RETURN 'null'; END IF; RETURN 'set'; END", "null"},
            {"BEGIN LET q DATE; RETURN q; END", "null"},
            {"BEGIN LET q INT; SELECT 5 INTO :q; RETURN q; END", "5"},
        });
    }
}
