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
 * A call written as a statement of a block is read and refused while the block compiles, {@code Unsupported feature
 * 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'.}, the function named as written: after a
 * RETURN, on an IF branch, in a loop, in an exception handler, in a nested block, in an EXECUTE IMMEDIATE's text and in
 * a procedure's body as it runs. A star, DISTINCT or an arrow among its arguments, and any token no value reads, is
 * refused where it stands, and a text with another fault keeps its syntax error (live-verified).
 */
public class BlockCallStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallIsNoStatementOfABlock() {
        assertCells(new String[][] {
            {"BEGIN SYSTEM$WAIT(0); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$TYPEOF(1); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$TYPEOF cannot be executed as a statement in "
                + "Snowscript'."},
            {"BEGIN UPPER('a'); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function UPPER cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$WAIT; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'."},
            {"BEGIN SYSTEM$LOG('info', 'x'); RETURN 1; END;", "1"},
            {"BEGIN IF (TRUE) THEN SYSTEM$WAIT(0); END IF; RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"DECLARE x INT; BEGIN SYSTEM$WAIT(0); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$GET_TAG('a', 'b', 'c'); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$GET_TAG cannot be executed as a statement in "
                + "Snowscript'."},
        });
    }

    @Test
    public void everyBuiltInCallIsRefusedByItsName() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"BEGIN upper('a'); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function upper cannot be executed as a statement in Snowscript'."},
            {"BEGIN ABS(1); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN CURRENT_DATE(); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function CURRENT_DATE cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$WAIT(0); END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$WAIT(0); SYSTEM$WAIT(1); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN RETURN 1; SYSTEM$WAIT(0); END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN SYSTEM$WAIT(0, 'SECONDS'); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'."},
            {"BEGIN COUNT(*); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '*'."},
        });
    }

    @Test
    public void itsArgumentsAreReadAsValues() {
        engine.execute("CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertCells(new String[][] {
            {"BEGIN ABS(); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN ABS(1, 2); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN ABS(ABS(-1)); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN ABS(1); END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"EXECUTE IMMEDIATE $$BEGIN ABS(1); RETURN 1; END;$$",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"CREATE OR REPLACE PROCEDURE pcs() RETURNS INT LANGUAGE SQL AS $$BEGIN ABS(1); RETURN 1; END$$",
                "Function PCS successfully created."},
            {"CALL pcs()",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN FOR i IN 1 TO 2 DO ABS(i); END FOR; RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN LOOP ABS(1); BREAK; END LOOP; RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN IF (FALSE) THEN RETURN 0; ELSE ABS(1); END IF; RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN ABS(1 2); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '2'."},
            {"BEGIN CURRENT_TIMESTAMP(); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function CURRENT_TIMESTAMP cannot be executed as a statement in "
                + "Snowscript'."},
            {"BEGIN ABS(*); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '*'."},
            {"BEGIN ABS(DISTINCT 1); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'DISTINCT'."},
            {"BEGIN ABS(1); END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"ABS(1);", "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'ABS'."},
            {"SELECT 1; ABS(1);",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'ABS'."},
            {"BEGIN BEGIN ABS(1); END; RETURN 1; END;",
                "REFUSED Unsupported feature 'Function ABS cannot be executed as a statement in Snowscript'."},
            {"BEGIN COALESCE(1, 2); RETURN 1; END;",
                "REFUSED Unsupported feature 'Function COALESCE cannot be executed as a statement in Snowscript'."},
            {"BEGIN UPPER(x => 'a'); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '=>'."},
        });
    }
}
