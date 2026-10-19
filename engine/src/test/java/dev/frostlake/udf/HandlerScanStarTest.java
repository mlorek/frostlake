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
 * The scan of a Java procedure's answer wider than its RETURNS VARCHAR(2): a plain {@code SELECT * FROM
 * TABLE(RESULT_SCAN(…))} hands the answer out as the CALL did, where every other statement over the scan is refused
 * as it reads the cell — a named column, {@code $1}, an alias's star, a star beside another item, a WHERE, an ORDER
 * BY, a LIMIT, a DISTINCT, EXCLUDE and RENAME, a subquery's star, a UNION — and a count of its rows answers
 * (live-verified).
 */
public class HandlerScanStarTest extends MeasuredCellsTestSupport {

    @Test
    public void aPlainStarHandsTheAnswerOut() {
        engine.execute("CREATE OR REPLACE PROCEDURE r4() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
            + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
            + "\"abc\"; } } $$");
        assertCells(new String[][] {
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "abc"},
            {"CALL r4()", "abc"},
            {"SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT \"R4\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT *, 1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "1"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE TRUE",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) LIMIT 1",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"R4\" IS NOT NULL",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * EXCLUDE (\"R4\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"CALL r4()", "abc"},
            {"SELECT * RENAME (\"R4\" AS z) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT DISTINCT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) UNION ALL SELECT 'x'",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"SELECT TYPEOF(\"R4\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TYPEOF': (VARCHAR(2))"},
            {"CALL r4()", "abc"},
            {"SELECT \"R4\" AS r FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
        });
    }
}
