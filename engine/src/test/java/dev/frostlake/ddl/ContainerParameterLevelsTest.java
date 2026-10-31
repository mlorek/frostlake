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
 * ALTER SCHEMA and ALTER DATABASE SET and UNSET of names the parameter lists hold at some level: a parameter of
 * another level - the account's, a database's, a warehouse's, a user's or a session's - is refused for want of
 * access, a name no level lists is an invalid property, and one of the object's own level is taken (all
 * live-verified).
 */
public class ContainerParameterLevelsTest extends MeasuredCellsTestSupport {

    @Test
    public void parameterLevels() {
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET ENABLE_HORIZON_IRC_UNMANAGED_CREDENTIAL_VENDING = TRUE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET ENABLE_HORIZON_IRC_UNMANAGED_CREDENTIAL_VENDING");
        assertAnswer("Statement executed successfully.",
            "ALTER DATABASE test_db SET ENABLE_HORIZON_IRC_UNMANAGED_CREDENTIAL_VENDING = TRUE");
        assertAnswer("Statement executed successfully.",
            "ALTER DATABASE test_db UNSET ENABLE_HORIZON_IRC_UNMANAGED_CREDENTIAL_VENDING");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET FALLBACK_WAREHOUSE = 'nosuch_wh'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET FALLBACK_WAREHOUSE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db SET FALLBACK_WAREHOUSE = 'nosuch_wh'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET FALLBACK_WAREHOUSE");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA test_db.test_schema SET REPLICABLE_WITH_FAILOVER_GROUPS = 'NO'");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA test_db.test_schema UNSET REPLICABLE_WITH_FAILOVER_GROUPS");
        assertAnswer("Statement executed successfully.",
            "ALTER DATABASE test_db SET REPLICABLE_WITH_FAILOVER_GROUPS = 'NO'");
        assertAnswer("Statement executed successfully.",
            "ALTER DATABASE test_db UNSET REPLICABLE_WITH_FAILOVER_GROUPS");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET TELEMETRY_ENABLE_INTERACTIVE_MODE = TRUE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET TELEMETRY_ENABLE_INTERACTIVE_MODE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db SET TELEMETRY_ENABLE_INTERACTIVE_MODE = TRUE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET TELEMETRY_ENABLE_INTERACTIVE_MODE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET FEATURE_COMMENT_PROPAGATION = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET FEATURE_COMMENT_PROPAGATION");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db SET FEATURE_COMMENT_PROPAGATION = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET FEATURE_COMMENT_PROPAGATION");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db SET SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET UI_ENABLE_TUTORIALS_PAGE = TRUE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET UI_ENABLE_TUTORIALS_PAGE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db SET UI_ENABLE_TUTORIALS_PAGE = TRUE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET UI_ENABLE_TUTORIALS_PAGE");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION' "
                + "for 'SCHEMA'",
            "ALTER SCHEMA test_db.test_schema SET ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION' "
                + "for 'SCHEMA'",
            "ALTER SCHEMA test_db.test_schema UNSET ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION' "
                + "for 'DATABASE'",
            "ALTER DATABASE test_db SET ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION' "
                + "for 'DATABASE'",
            "ALTER DATABASE test_db UNSET ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA test_db.test_schema SET CHANGE_TRACKING_DEFAULT = TRUE");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA test_db.test_schema UNSET CHANGE_TRACKING_DEFAULT");
        assertAnswer("Statement executed successfully.", "ALTER DATABASE test_db SET CHANGE_TRACKING_DEFAULT = TRUE");
        assertAnswer("Statement executed successfully.", "ALTER DATABASE test_db UNSET CHANGE_TRACKING_DEFAULT");
    }

    @Test
    public void userLevelParameter() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema UNSET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'.",
            "ALTER DATABASE test_db UNSET TEST_ORG_ALIAS_USER_RESOLUTION_PARAM");
    }
}
