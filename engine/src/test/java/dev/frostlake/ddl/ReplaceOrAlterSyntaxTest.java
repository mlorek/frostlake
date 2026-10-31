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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * CREATE OR REPLACE followed by a second OR is refused with the one line naming that OR, whatever kind and body
 * follow; CREATE OR ALTER OR REPLACE is refused at its REPLACE (all live-verified).
 */
public class ReplaceOrAlterSyntaxTest extends MeasuredCellsTestSupport {

    @Test
    public void aSecondOrIsTheOneLine() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER TASK tk AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER TABLE t (i INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER VIEW v AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER SCHEMA s");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER FUNCTION f() RETURNS INT AS '1'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'OR'.",
            "CREATE OR ALTER OR REPLACE TASK tk AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER STAGE st");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER TASK tk");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'OR'.",
            "CREATE OR REPLACE OR ALTER DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = w AS SELECT 1");
    }
}
