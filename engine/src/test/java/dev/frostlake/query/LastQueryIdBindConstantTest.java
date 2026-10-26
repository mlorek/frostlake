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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * LAST_QUERY_ID's index named for a DESCRIBE RESULT or a RESULT_SCAN is judged a constant before a bind in it is
 * read: an unset :x, :1 or ? there is no constant, echoed TO_NUMBER(:x, 18, 0), where a select of the call reads
 * the bind first (all live-verified).
 */
public class LastQueryIdBindConstantTest extends MeasuredCellsTestSupport {

    @Test
    public void bindIndexIsNoConstant() {
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER(:x, 18, 0)'",
            "DESCRIBE RESULT LAST_QUERY_ID(:x)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Bind variable :x not set.",
            "SELECT LAST_QUERY_ID(:x)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER(:x, 18, 0)'",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(:x)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER(:1, 18, 0)'",
            "DESCRIBE RESULT LAST_QUERY_ID(:1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Bind variable :1 not set.",
            "SELECT LAST_QUERY_ID(:1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | Session variable '$NOSUCHVAR' does "
                + "not exist",
            "DESCRIBE RESULT LAST_QUERY_ID($nosuchvar)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Session variable '$NOSUCHVAR' does "
                + "not exist",
            "SELECT LAST_QUERY_ID($nosuchvar)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 26 | Bind variable :x not set.",
            "SELECT LAST_QUERY_ID(-1 + :x)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 31 | Bind variable :x not set.",
            "SELECT LAST_QUERY_ID(TO_NUMBER(:x))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Bind variable :x not set.",
            "SELECT LAST_QUERY_ID(:x::INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Bind variable ? not set.",
            "SELECT LAST_QUERY_ID(?)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'TO_NUMBER(?, 18, 0)'",
            "DESCRIBE RESULT LAST_QUERY_ID(?)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCHCOL'",
            "SELECT LAST_QUERY_ID(nosuchcol)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT LAST_QUERY_ID(nosuchfn(1))");
        engine.execute("SET qv = -1");
        assertAnswer("true", "SELECT LAST_QUERY_ID($qv) IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Bind variable :x not set.",
            "SELECT LAST_QUERY_ID(:x) FROM (SELECT 1 AS c)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 29 | Bind variable :x not set.",
            "SELECT 1 WHERE LAST_QUERY_ID(:x) IS NULL");
    }
}
