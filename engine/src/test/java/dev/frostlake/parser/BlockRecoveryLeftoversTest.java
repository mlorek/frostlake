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
 * Block recovery: an exception handler without its THEN refuses the token in its place twice; every argument list
 * of a statement that ends in a comma is refused in turn and the block read on; an operator's operand missing at a
 * plain bracket's ')' is refused alone, the statement read on (all live-verified).
 */
public class BlockRecoveryLeftoversTest extends MeasuredCellsTestSupport {

    @Test
    public void handlersListsAndBrackets() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 37 unexpected 'RETURN'.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER RETURN 2; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax "
                + "error line 1 at position 28 unexpected ')'. | syntax error line 1 at position 47 unexpected '1'.",
            "BEGIN SELECT ABS(1,), ABS(2,) FROM t; RETURN 1 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'. | syntax "
                + "error line 1 at position 41 unexpected '1'.",
            "BEGIN SELECT ABS((1 +)) FROM t; RETURN 1 1; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.", "DECLARE q r; BEGIN RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 47 unexpected 'RETURN'.",
            "BEGIN RETURN 1; EXCEPTION WHEN STATEMENT_ERROR RETURN 2; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax "
                + "error line 1 at position 28 unexpected ')'. | syntax error line 1 at position 37 unexpected ')'.",
            "BEGIN SELECT ABS(1,), ABS(2,), ABS(3,) FROM t; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ')'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "SELECT ABS(1,), ABS(2,) FROM t");
    }
}
