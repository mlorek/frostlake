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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A flow chain in a block (live-verified): assigned to a variable that is no RESULTSET it is refused while the block
 * compiles as the block the account reads it as, {@code Invalid expression value (?SqlPsmBlock?) for assignment.},
 * and a chain that fails is placed at its last arrow, whichever stage fails, under the declared form and the
 * assignment form alike.
 */
public class FlowChainLastArrowTest extends MeasuredCellsTestSupport {

    @Test
    public void aChainInABlock() {
        assertCells(new String[][] {
            {"BEGIN LET x INT := (SELECT 1 ->> SELECT 2); RETURN x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'SELECT'."},
            {"BEGIN LET x INT; x := (SELECT 1 ->> SELECT 2); RETURN x; END",
                "REFUSED SQL compilation error: | Invalid expression value (?SqlPsmBlock?) for assignment."},
            {"BEGIN LET rs RESULTSET; rs := (SELECT 1 a ->> SELECT nosuch FROM $1); RETURN TABLE(rs); END",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 1 at position 15 : SQL compilation error: error line "
                + "1 at position 7 | invalid identifier 'NOSUCH'"},
            {"BEGIN LET rs RESULTSET := (SELECT 1 a ->> SELECT a FROM $1 ->> SELECT nosuch FROM $1); RETURN "
                + "TABLE(rs); END",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 59 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 1 at position 36 : SQL compilation error: error line "
                + "1 at position 7 | invalid identifier 'NOSUCH'"},
            {"BEGIN LET rs RESULTSET := (SELECT 1 a ->> SELECT nosuch FROM $1 ->> SELECT 1); RETURN TABLE(rs); END",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 1 at position 15 : SQL compilation error: error line "
                + "1 at position 7 | invalid identifier 'NOSUCH'"},
            {"BEGIN LET rs RESULTSET := (SELECT 1 a ->> SELECT a + 1 b FROM $1 ->> SELECT b * 2 FROM $1); "
                + "RETURN TABLE(rs); END",
                "4"},
        });
    }
}
