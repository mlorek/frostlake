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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A fault inside a CAST that stands right after a WHERE: the rest of the condition is read on, a later faulty
 * cast's fault told before the token that ends the clause; in a block a SELECT's cast comma reads on to the next
 * statement (all live-verified).
 */
public class CastFaultReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void castsReadOn() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 4 at position 15 unexpected ','. | syntax "
                + "error line 4 at position 18 unexpected ')'. | syntax error line 5 at position 2 unexpected "
                + "'RETURN'.",
            "DECLARE\n  x INT;\nBEGIN\n  SELECT CAST(1, 2) INTO :x;\n  RETURN x;\nEND");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '2'. | syntax "
                + "error line 1 at position 60 unexpected '4'. | syntax error line 1 at position 66 unexpected "
                + "'<EOF>'.",
            "SELECT CAST(1 AS INT) FROM t WHERE CAST(1 2) = 1 AND CAST(3 4) = 2");
    }
}
