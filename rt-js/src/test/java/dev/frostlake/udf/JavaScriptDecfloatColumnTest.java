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
 * A JavaScript procedure's ResultSet reads a DECFLOAT column as its text, by getColumnValue and by
 * getColumnValueAsString alike - a string, 1e16384, never a JavaScript number that loses its digits (all
 * live-verified).
 */
public class JavaScriptDecfloatColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void decfloatReadsAsText() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function JP48 successfully created.",
            "CREATE OR REPLACE PROCEDURE jp48(t VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT DECFLOAT '\" + T + \"' d\"}); rs.next(); return typeof "
                + "rs.getColumnValue(1) + ':' + rs.getColumnValue(1) + ' ' + rs.getColumnValueAsString(1) + ' ' + "
                + "typeof rs.getColumnValueAsString(1); $$");
        assertAnswer("string:1.2345678901234567890123456789012345678e-20 1.2345678901234567890123456789012345678e-20 "
                + "string",
            "CALL jp48('1.2345678901234567890123456789012345678e-20')");
        assertAnswer("string:123.5 123.5 string", "CALL jp48('123.5')");
        assertAnswer("string:1e16384 1e16384 string", "CALL jp48('1e16384')");
        assertAnswer("string:-0.000001 -0.000001 string", "CALL jp48('-0.000001')");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
