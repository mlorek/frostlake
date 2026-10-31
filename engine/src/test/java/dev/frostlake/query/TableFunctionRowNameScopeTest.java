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
 * A table function's positional row argument has its names resolved before the row is typed: a name the call cannot
 * see, one belonging to a FROM item to its right or to no item at all, is an invalid identifier where it stands,
 * positionally or under a parameter name, and a row whose names all resolve to an item on the left is refused for
 * its type (all live-verified).
 */
public class TableFunctionRowNameScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowsNamesResolveBeforeItsType() {
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT, b VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'A'",
            "SELECT * FROM TABLE(FLATTEN((a, b))), t2");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "parameter '1'",
            "SELECT * FROM t2, TABLE(FLATTEN((a, b)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH'",
            "SELECT * FROM TABLE(FLATTEN((nosuch, 1)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'A'",
            "SELECT * FROM TABLE(FLATTEN((1, a))), t2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'A'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (a, b))), t2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'A'",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE((a, b), ',')), t2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'T2.A'",
            "SELECT * FROM TABLE(FLATTEN((t2.a, 1))), t2");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'A'",
            "SELECT * FROM TABLE(FLATTEN((a))), t2");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "parameter '1'",
            "SELECT * FROM t2 x, TABLE(FLATTEN((x.a, x.b)))");
    }
}
