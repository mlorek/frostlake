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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function body holding a WINDOW clause after another clause is refused at its first word and the WINDOW,
 * where it was created (live-verified).
 */
public class SqlUdfBodyWindowClauseTest extends MeasuredCellsTestSupport {

    @Test
    public void windowClauseAfterWhere() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'WINDOW'.",
            "CREATE OR REPLACE FUNCTION g2534() RETURNS NUMBER AS 'SELECT a FROM t WHERE a > 0 WINDOW w AS (ORDER BY "
                + "a)'");
    }
}
