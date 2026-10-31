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
 * The account's parameters as SHOW PARAMETERS IN ACCOUNT lists them: JSON_INDENT at the account's own value,
 * and the parameters the account has added or changed the default of (all live-verified).
 */
public class AccountParameterListingTest extends MeasuredCellsTestSupport {

    @Test
    public void accountParameters() {
        assertAnswer("JSON_INDENT|2|2||Width of indentation in JSON output (0 for compact)|NUMBER",
            "SHOW PARAMETERS LIKE 'JSON_INDENT' IN ACCOUNT");
        assertAnswer("DYNAMIC_TABLES_VIEW_VERSION|13|13||INFORMATION_SCHEMA.DYNAMIC_TABLES table function definition "
                + "version|NUMBER",
            "SHOW PARAMETERS LIKE 'DYNAMIC_TABLES_VIEW_VERSION' IN ACCOUNT");
        assertAnswer("JS_QUERY_RESULT_FORMAT|JSON|JSON||Format used to serialize query result to send back to the "
                + "JavaScript driver|STRING",
            "SHOW PARAMETERS LIKE 'JS_QUERY_RESULT_FORMAT' IN ACCOUNT");
        assertAnswer("", "SHOW PARAMETERS LIKE 'TELEMETRY%' IN ACCOUNT");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
