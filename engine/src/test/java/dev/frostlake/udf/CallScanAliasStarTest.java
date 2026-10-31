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
 * A plain star over a CALL's scan hands out an answer wider than the procedure's RETURNS VARCHAR(2) with an
 * alias, written bare or after AS, as without one; an alias's star is refused (all live-verified).
 */
public class CallScanAliasStarTest extends MeasuredCellsTestSupport {

    @Test
    public void aliasedScanStar() {
        engine.execute("CREATE OR REPLACE PROCEDURE jw() RETURNS VARCHAR(2) LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$\nimport "
            + "com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s) {\n    return "
            + "\"abcdef\";\n  }\n}\n$$");
        assertAnswer("abcdef", "CALL jw()");
        assertAnswer("abcdef", "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r");
        assertAnswer("abcdef", "CALL jw()");
        assertAnswer("abcdef", "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) AS r");
        assertAnswer("abcdef", "CALL jw()");
        assertAnswer("REFUSED String 'abcdef' is too long and would be truncated",
            "SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r");
        assertAnswer("abcdef", "CALL jw()");
        assertAnswer("abcdef", "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
