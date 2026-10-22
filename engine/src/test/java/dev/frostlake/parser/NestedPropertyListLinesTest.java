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
 * The syntax-error lines of a list of property lists whose last property follows a comma: the ')' is refused
 * where a name is due and taken for it, so the next ')' closes the item (all live-verified).
 */
public class NestedPropertyListLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void trailingComma() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'. | syntax "
                + "error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = ((a = 1,))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'. | syntax "
                + "error line 1 at position 39 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 x = ((a = 1, b = 2,))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'. | syntax "
                + "error line 1 at position 33 unexpected '('.",
            "DESCRIBE TABLE t1 x = ((a = 1,), (b = 2))");
    }
}
