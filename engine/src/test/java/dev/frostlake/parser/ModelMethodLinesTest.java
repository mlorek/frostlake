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
 * The syntax-error lines of a model's method named inside TABLE( ... ) without its argument list: the token after
 * the method's name, then the token after the parenthesis that closes the TABLE, the end of the text included
 * (all live-verified).
 */
public class ModelMethodLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void methodWithoutArguments() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 24 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(x!y)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 't'.",
            "SELECT * FROM TABLE(x!y) t");
    }

    @Test
    public void whateverFollows() {
        engine.execute("CREATE OR REPLACE TABLE pq3 (n INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 'WHERE'.",
            "SELECT * FROM TABLE(x!y) WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 24 unexpected ','.",
            "SELECT * FROM TABLE(x!y), pq3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 'AS'.",
            "SELECT * FROM TABLE(x!y) AS t");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 't'.",
            "SELECT * FROM TABLE(x!y) t, pq3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(x!y 1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 't'.",
            "SELECT * FROM TABLE(x!y) t WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'. | syntax "
                + "error line 1 at position 29 unexpected '<EOF>'.",
            "SELECT * FROM pq3, TABLE(x!y)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 'JOIN'.",
            "SELECT * FROM TABLE(x!y) JOIN pq3 ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected 'ORDER'.",
            "SELECT * FROM TABLE(x!y) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(x.z!y)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'.",
            "SELECT x!y FROM pq3");
    }
}
