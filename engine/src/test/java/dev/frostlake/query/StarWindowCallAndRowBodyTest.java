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
 * A call over a star given a window is refused for its kind before the star is read, and a SQL function body
 * that is a list of values and one value more is the ROW it is, refused against the declared return type (all
 * live-verified).
 */
public class StarWindowCallAndRowBodyTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarWindowCallAndARowBody() {
        engine.execute("CREATE OR REPLACE TABLE pg (x INT, y INT)");
        engine.execute("INSERT INTO pg VALUES (1, 2)");
        assertAnswer("REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type "
                + "'ROW(ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))'",
            "CREATE OR REPLACE FUNCTION nf_b2(x INT) RETURNS INT AS '(1, 2), 3'");
        assertAnswer("REFUSED SQL compilation error: | Invalid function type [HASH] for window function.",
            "SELECT HASH(*) OVER () FROM pg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT x!y FROM pg");
    }
}
