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
 * A statement whose first fault stands before a query used as a value: an operand missing before a comma, or a
 * fault inside an EXISTS, keeps its line, then the value query's SELECT is refused and, in a select list, the
 * fault inside the query follows (all live-verified).
 */
public class EarlierFaultValueQueryTest extends MeasuredCellsTestSupport {

    @Test
    public void anEarlierFaultThenTheQuery() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'. | syntax "
                + "error line 1 at position 53 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE EXISTS (SELECT 1 x y) AND a = (SELECT 2 z w)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ','. | syntax "
                + "error line 1 at position 13 unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected "
                + "'y'.",
            "SELECT 1 +, (SELECT 1 x y)");
    }
}
