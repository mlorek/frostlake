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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A comma or a named argument's arrow where a call's first argument should stand names the call's parenthesis after
 * its own line, {@code SELECT ABS(, 1)} at 11 and then 10; SYSTEM$PIPE_STATUS names a missing pipe as its argument
 * writes it; a CALL of a SYSTEM$ function refuses a name written twice at its second writing and echoes a named
 * argument's value alone in an argument-count refusal (live-verified).
 */
public class SystemCallShapeLineTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallsShapeIsReadAsLiveReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(=> 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '=>'. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT SYSTEM$TYPEOF(, 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT ABS(, 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ','. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT ABS(=> 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '=>'. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT UPPER(, 'a')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 12 unexpected '('."},
            {"SELECT f_nosuch(, 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 15 unexpected '('."},
            {"SELECT ABS(1, , 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ','."},
            {"SELECT CONCAT(, 'a') FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ','. | syntax "
                + "error line 1 at position 13 unexpected '('."},
            {"DECLARE x INT; BEGIN x := test_db.test_schema.nosuch_f(1); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.NOSUCH_F."},
            {"BEGIN RETURN test_schema.nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown user-defined function TEST_SCHEMA.NOSUCH_F."},
            {"SELECT SYSTEM$PIPE_STATUS(a => 'x')",
                "REFUSED SQL compilation error: | Pipe 'x' does not exist or not authorized."},
            {"SELECT SYSTEM$PIPE_STATUS('x')",
                "REFUSED SQL compilation error: | Pipe 'x' does not exist or not authorized."},
            {"SELECT SYSTEM$PIPE_STATUS(pipe_name => 'x')",
                "REFUSED SQL compilation error: | Pipe 'x' does not exist or not authorized."},
            {"SELECT SYSTEM$PIPE_STATUS(a => 'test_schema.x')",
                "REFUSED SQL compilation error: | Pipe 'test_schema.x' does not exist or not authorized."},
            {"CALL SYSTEM$TYPEOF(a => 1, a => 2)",
                "REFUSED SQL compilation error: error line 1 at position 27 | duplicate named argument A for "
                + "function SYSTEM$TYPEOF"},
            {"CALL SYSTEM$TYPEOF(a => 1, b => 2)",
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                + "[SYSTEM$TYPEOF(1, 2)] expected 1, got 2"},
            {"SELECT SYSTEM$TYPEOF(a => 1, a => 2)",
                "REFUSED SQL compilation error: error line 1 at position 34 | duplicate named argument A for "
                + "function SYSTEM$TYPEOF"},
            {"CALL SYSTEM$WAIT(a => 0, a => 1)",
                "REFUSED SQL compilation error: error line 1 at position 25 | duplicate named argument A for "
                + "function SYSTEM$WAIT"},
        });
    }
}
