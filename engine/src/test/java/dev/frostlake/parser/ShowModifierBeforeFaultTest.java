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
 * A SHOW whose LIMIT or STARTS WITH is written again, or whose STARTS WITH stands after its LIMIT, before a later
 * fault: the modifier's word is refused, alone at the top level and with the token after it in a block (all
 * live-verified).
 */
public class ShowModifierBeforeFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void repeatedModifiers() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIKE'.",
            "SHOW TABLES LIKE 'a' LIKE 'b' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIKE'.",
            "SHOW TABLES LIKE 'a' LIKE 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IN'.",
            "SHOW TABLES IN SCHEMA s IN SCHEMA s x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'STARTS'.",
            "SHOW TABLES STARTS WITH 'a' STARTS WITH 'b' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SHOW TABLES LIMIT 1 x LIMIT 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 FROM 'a' LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'.",
            "SHOW TERSE TABLES LIMIT 1 LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIMIT'.",
            "SHOW VIEWS LIMIT 1 LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIMIT'.",
            "SHOW SCHEMAS LIMIT 1 LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'LIKE'.",
            "SHOW TABLES LIKE 'a' IN SCHEMA s LIKE 'b' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IN'.",
            "SHOW TABLES IN ACCOUNT IN ACCOUNT x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 ;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 x; SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FROM'.",
            "SHOW TABLES LIMIT 1 FROM 'a' FROM 'b' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'HISTORY'.",
            "SHOW TABLES HISTORY HISTORY x");
    }

    @Test
    public void repeatedModifiersInBlocks() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 x y; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'STARTS'. | "
                + "syntax error line 1 at position 41 unexpected 'WITH'.",
            "BEGIN SHOW TABLES STARTS WITH 'a' STARTS WITH 'b' x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'STARTS'.",
            "SHOW TABLES STARTS WITH 'a' STARTS WITH 'b'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'STARTS'.",
            "SHOW TABLES STARTS WITH 'a' LIMIT 1 STARTS WITH 'b' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'STARTS'.",
            "SHOW TABLES LIMIT 1 STARTS WITH 'a' LIMIT 2 x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 FROM 'a' x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 LIKE 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
            "SHOW TABLES LIMIT 1 LIMIT 2 IN SCHEMA s x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 3; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 32 unexpected '2'.",
            "BEGIN SHOW TABLES LIMIT 1 LIMIT 2 x; RETURN 1; END;");
    }
}
