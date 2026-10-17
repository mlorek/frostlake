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
 * A handler's answer wider than its RETURNS column, written from the CALL's scan into a table — INSERT … SELECT,
 * CREATE TABLE … AS SELECT — is refused with the scan's own sentence, {@code String 'abc' is too long and would be
 * truncated}, and no DML sentence around it; a column wide enough for it is no matter (live-verified).
 */
public class HandlerScanWriteTest extends MeasuredCellsTestSupport {

    @Test
    public void aWriteOverTheScanIsRefusedBare() {
        engine.execute("CREATE OR REPLACE PROCEDURE r4() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
            + "com.snowflake.snowpark_java.*; class H { public static String run(Session s) { return "
            + "\"abc\"; } } $$");
        engine.execute("CREATE OR REPLACE TABLE rt (v VARCHAR(10))");
        engine.execute("CREATE OR REPLACE TABLE rn (v VARCHAR(2))");
        assertCells(new String[][] {
            {"CALL r4()", "abc"},
            {"INSERT INTO rt SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"CREATE OR REPLACE TABLE r4t AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"INSERT INTO rt SELECT $1 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"CALL r4()", "abc"},
            {"INSERT INTO rn SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED String 'abc' is too long and would be truncated"},
            {"SELECT COUNT(*) FROM rt", "0"},
            {"INSERT INTO rn VALUES ('abc')",
                "REFUSED DML operation to table RN failed on column V with error: String 'abc' is too long and "
                + "would be truncated"},
            {"CALL r4()", "abc"},
        });
    }
}
