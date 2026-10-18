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
 * Two echoes of the tag functions' arguments (live-verified): a wrong count of SYSTEM$GET_TAG's arguments
 * echoes each column as written, {@code [SYSTEM$GET_TAG(N, 'tt')]}, its relation only where the column
 * names one; and a SYSTEM$GET_TAG_ALLOWED_VALUES argument the plan does not fold prints what it folds early as
 * its value — a context function, UPPER over a literal — and a text joined to the empty string alone.
 */
public class TagCallEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anArgumentIsEchoedAsThePlanReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('a', 1)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) OVER () FROM tsrc",
                "REFUSED SQL compilation error: | Invalid function type [SYSTEM$GET_TAG_ALLOWED_VALUES] for window "
                + "function."},
            {"SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE'), ABS(i) OVER () FROM tsrc",
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG(n, 'tt') FROM tsrc",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(N, 'tt')], expected 3, got 2"},
            {"SELECT SYSTEM$GET_TAG(tsrc.n, 'tt') FROM tsrc",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(TSRC.N, 'tt')], expected 3, got 2"},
            {"SELECT SYSTEM$GET_TAG(n || 'x', 'tt') FROM tsrc",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(N || 'x', 'tt')], expected 3, got 2"},
            {"SELECT SYSTEM$GET_TAG(UPPER(n), 'tt') FROM tsrc",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(UPPER(N), 'tt')], expected 3, got 2"},
            {"SELECT SYSTEM$GET_TAG(i, 'tt') FROM tsrc",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(I, 'tt')], expected 3, got 2"},
            {"SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE', 'x') FROM tsrc",
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR || '')",
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', "
                + "parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1' || '')",
                "REFUSED SQL compilation error: | Tag 'E1' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG E1."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TRIM('e1'))",
                "REFUSED SQL compilation error: | Invalid value [TRIM('e1')] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR)",
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', "
                + "parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER('e1')::VARCHAR || 'x')",
                "REFUSED SQL compilation error: | Invalid value [('E1') || 'x'] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_SCHEMA()::VARCHAR)",
                "REFUSED SQL compilation error: | Invalid value ['TEST_SCHEMA'] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(1::VARCHAR || '')",
                "REFUSED SQL compilation error: | Invalid value [CAST(1 AS VARCHAR(134217728))] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER('e1'))",
                "REFUSED SQL compilation error: | Tag 'E1' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG E1."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(LOWER('E1') || 'x')",
                "REFUSED SQL compilation error: | Tag 'E1X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG E1X."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('' || 'e1')",
                "REFUSED SQL compilation error: | Tag 'E1' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG E1."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_DATABASE())",
                "REFUSED SQL compilation error: | Tag 'TEST_DB' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TEST_DB."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CONCAT('e', '1'))",
                "REFUSED SQL compilation error: | Tag 'E1' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG E1."},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR(10))",
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', "
                + "parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(2::VARCHAR)",
                "REFUSED SQL compilation error: | Invalid value [CAST(2 AS VARCHAR(134217728))] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e' || 1)",
                "REFUSED SQL compilation error: | Invalid value ['e' || (CAST(1 AS VARCHAR(134217728)))] for "
                + "function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
            {"SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(SUBSTR('xe1', 2))",
                "REFUSED SQL compilation error: | Invalid value [SUBSTR('xe1', 2)] for function "
                + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"},
        });
    }
}
