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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER SCHEMA and ALTER DATABASE judge a value against its parameter's type before the parameter's level: a
 * session's BOOLEAN TELEMETRY_ENABLE_INTERACTIVE_MODE = 'x' and a warehouse's FALLBACK_WAREHOUSE = 1 are invalid
 * values there, a value of the right type for another level's parameter the access refusal (all live-verified).
 */
public class AlterParameterValueTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void valueTypeBeforeLevel() {
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'TEST_ORG_ALIAS_USER_RESOLUTION_PARAM'",
            "ALTER SCHEMA test_db.test_schema SET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'TEST_ORG_ALIAS_USER_RESOLUTION_PARAM'",
            "ALTER DATABASE test_db SET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'TEST_ORG_ALIAS_USER_RESOLUTION_PARAM'",
            "ALTER SCHEMA test_db.test_schema SET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM = 5");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'UI_ENABLE_TUTORIALS_PAGE'",
            "ALTER SCHEMA test_db.test_schema SET UI_ENABLE_TUTORIALS_PAGE = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET UI_ENABLE_TUTORIALS_PAGE = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'TELEMETRY_ENABLE_INTERACTIVE_MODE'",
            "ALTER SCHEMA test_db.test_schema SET TELEMETRY_ENABLE_INTERACTIVE_MODE = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter "
                + "'TELEMETRY_ENABLE_INTERACTIVE_MODE'",
            "ALTER SCHEMA test_db.test_schema SET TELEMETRY_ENABLE_INTERACTIVE_MODE = 5");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'UI_ENABLE_TUTORIALS_PAGE'",
            "ALTER DATABASE test_db SET UI_ENABLE_TUTORIALS_PAGE = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'TELEMETRY_ENABLE_INTERACTIVE_MODE'",
            "ALTER DATABASE test_db SET TELEMETRY_ENABLE_INTERACTIVE_MODE = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [1] for parameter 'FALLBACK_WAREHOUSE'",
            "ALTER SCHEMA test_db.test_schema SET FALLBACK_WAREHOUSE = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'FALLBACK_WAREHOUSE'",
            "ALTER SCHEMA test_db.test_schema SET FALLBACK_WAREHOUSE = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'TIMEZONE'",
            "ALTER SCHEMA test_db.test_schema SET TIMEZONE = 5");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER SCHEMA test_db.test_schema SET QUERY_TAG = 5");
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'TEST_ORG_ALIAS_USER_RESOLUTION_PARAM'",
            "ALTER SCHEMA test_db.test_schema SET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM = TRUE, "
                + "DATA_RETENTION_TIME_IN_DAYS = 1");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM");
    }
}
