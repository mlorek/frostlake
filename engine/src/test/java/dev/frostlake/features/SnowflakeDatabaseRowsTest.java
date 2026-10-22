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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The SNOWFLAKE database's own rows: SHOW DATABASES lists it as the APPLICATION of origin SNOWFLAKE.ACCOUNT_USAGE,
 * owned by no role and retained for no day; DESCRIBE DATABASE refuses it, asking the role for MONITOR;
 * INFORMATION_SCHEMA.DATABASES leaves it out; and every database's INFORMATION_SCHEMA tells no creation or
 * alteration instant in SCHEMATA (all live-verified).
 */
public class SnowflakeDatabaseRowsTest extends MeasuredCellsTestSupport {

    /** The SHOW DATABASES row's cells but its creation instant, which differs from account to account. */
    @Test
    public void theShowDatabasesRow() {
        assertAnswer("SNOWFLAKE|N|SNOWFLAKE.ACCOUNT_USAGE||0|APPLICATION|",
            "SHOW DATABASES LIKE 'SNOWFLAKE' ->> SELECT \"name\", \"is_default\", \"origin\", \"owner\", "
                + "\"retention_time\", \"kind\", \"owner_role_type\" FROM $1");
    }

    @Test
    public void theSystemDatabaseRows() {
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on database 'SNOWFLAKE'. "
                + "Your primary role <ROLE> must have MONITOR granted on DATABASE SNOWFLAKE.",
            "DESCRIBE DATABASE SNOWFLAKE");
        assertAnswer("true|true",
            "SELECT created IS NULL, last_altered IS NULL FROM SNOWFLAKE.INFORMATION_SCHEMA.SCHEMATA WHERE "
                + "schema_name = 'INFORMATION_SCHEMA'");
        assertAnswer("true|true",
            "SELECT created IS NULL, last_altered IS NULL FROM TEST_DB.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name "
                + "= 'INFORMATION_SCHEMA'");
        assertAnswer("false|false",
            "SELECT created IS NULL, last_altered IS NULL FROM SNOWFLAKE.INFORMATION_SCHEMA.SCHEMATA WHERE "
                + "schema_name = 'ACCOUNT_USAGE'");
        assertAnswer("SNOWFLAKE|null|NO|NO|1|Views describing the contents of schemas in this database",
            "SELECT catalog_name, schema_owner, is_transient, is_managed_access, retention_time, comment FROM "
                + "SNOWFLAKE.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = 'INFORMATION_SCHEMA'");
        assertAnswer("TEST_DB|null|NO|NO|1|Views describing the contents of schemas in this database",
            "SELECT catalog_name, schema_owner, is_transient, is_managed_access, retention_time, comment FROM "
                + "TEST_DB.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = 'INFORMATION_SCHEMA'");
        assertAnswer("",
            "SELECT database_name, database_owner, is_transient, type, retention_time FROM "
                + "SNOWFLAKE.INFORMATION_SCHEMA.DATABASES WHERE database_name = 'SNOWFLAKE'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                + "'ACCOUNT_USAGE'. Provider share does not have sufficient privileges.",
            "DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertAnswer("Statement executed successfully.", "USE SCHEMA SNOWFLAKE.ACCESS_INTELLIGENCE");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        assertAnswer("Statement executed successfully.", "USE SCHEMA SNOWFLAKE.NATIVE_APPS_SPCS");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }
}
