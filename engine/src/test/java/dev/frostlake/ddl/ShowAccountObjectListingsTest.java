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
 * The listings of the account's own things that no object is modelled of - RESOURCE MONITORS, SHARES,
 * CONNECTIONS, APPLICATIONS and APPLICATION PACKAGES - answer the account's columns, and read LIKE, IN ACCOUNT,
 * STARTS WITH, LIMIT with its FROM, TERSE and HISTORY. A scope that is no account - a database, a schema, a
 * table, a view, a service, an application, a class or an instance of one - is refused by its shape, naming the
 * listing's kind; a name after IN is an account's, looked up and missed, shares and connections naming the
 * account and the others answering that no object exists, and a name of three parts is no account's at all.
 * WITH PRIVILEGES is read and unsupported, each listing named as the account names it. TERSE narrows the
 * application packages to five columns and leaves the others whole. Every statement reads only; every answer
 * is live-verified.
 */
public class ShowAccountObjectListingsTest extends MeasuredCellsTestSupport {

    private static final String APPLICATIONS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), is_default:VARCHAR(16777216), "
            + "is_current:VARCHAR(16777216), source_type:VARCHAR(16777216), source:VARCHAR(16777216), "
            + "owner:VARCHAR(16777216), comment:VARCHAR(16777216), version:VARCHAR(16777216), "
            + "label:VARCHAR(16777216), patch:NUMBER(9,0), options:VARCHAR(16777216), "
            + "retention_time:VARCHAR(16777216), upgrade_state:VARCHAR(16777216), "
            + "disablement_reasons:VARCHAR(16777216), last_upgraded_on:TIMESTAMP_LTZ, "
            + "release_channel_name:VARCHAR(16777216), type:VARCHAR(16777216)";

    private static final String APPLICATION_PACKAGES_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), is_default:VARCHAR(16777216), "
            + "is_current:VARCHAR(16777216), distribution:VARCHAR(16777216), multiple_instances:VARCHAR(16777216), "
            + "owner:VARCHAR(16777216), comment:VARCHAR(16777216), options:VARCHAR(16777216), "
            + "retention_time:VARCHAR(16777216), dropped_on:TIMESTAMP_LTZ, type:VARCHAR(16777216)";

    private static final String CONNECTIONS_COLUMNS =
        "snowflake_region:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, account_name:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), comment:VARCHAR(16777216), primary:VARCHAR(16777216), "
            + "replication_group:VARCHAR(16777216), external_id:VARCHAR(16777216), connection_url:VARCHAR(16777216)";

    private static final String RESOURCE_MONITORS_COLUMNS =
        "name:VARCHAR(16777216), credit_quota:VARCHAR(16777216), used_credits:VARCHAR(16777216), "
            + "remaining_credits:VARCHAR(16777216), level:VARCHAR(16777216), frequency:VARCHAR(16777216), "
            + "start_time:TIMESTAMP_LTZ, end_time:TIMESTAMP_LTZ, notify_at:VARCHAR(16777216), "
            + "suspend_at:VARCHAR(16777216), suspend_immediately_at:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, "
            + "owner:VARCHAR(16777216), comment:VARCHAR(16777216), notify_users:VARCHAR(16777216)";

    private static final String SHARES_COLUMNS =
        "created_on:TIMESTAMP_LTZ, kind:VARCHAR(16777216), owner_account:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), database_name:VARCHAR(16777216), to:VARCHAR(16777216), "
            + "owner:VARCHAR(16777216), comment:VARCHAR(16777216), listing_global_name:VARCHAR(16777216), "
            + "secure_objects_only:VARCHAR(16777216)";

    @Test
    public void resourceMonitors() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(RESOURCE_MONITORS_COLUMNS,
            "SHOW RESOURCE MONITORS",
            "SHOW RESOURCE MONITORS LIKE 'zz%'",
            "SHOW RESOURCE MONITORS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in DATABASE'.",
            "SHOW RESOURCE MONITORS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in SCHEMA'.",
            "SHOW RESOURCE MONITORS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in DATABASE'.",
            "SHOW RESOURCE MONITORS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in SCHEMA'.",
            "SHOW RESOURCE MONITORS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW RESOURCE MONITORS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in TABLE'.",
            "SHOW RESOURCE MONITORS IN TABLE ab");
        assertShapes(RESOURCE_MONITORS_COLUMNS,
            "SHOW RESOURCE MONITORS STARTS WITH 'zz'",
            "SHOW RESOURCE MONITORS LIMIT 1",
            "SHOW RESOURCE MONITORS LIMIT 1 FROM 'zz'",
            "SHOW TERSE RESOURCE MONITORS",
            "SHOW RESOURCE MONITORS HISTORY",
            "SHOW RESOURCE MONITORS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'nosuchword'.",
            "SHOW RESOURCE MONITORS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in SCHEMA'.",
            "SHOW RESOURCE MONITORS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in DATABASE'.",
            "SHOW RESOURCE MONITORS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW RESOURCE MONITORS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW RESOURCE MONITORS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '5'.",
            "SHOW RESOURCE MONITORS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SHOW RESOURCE MONITORS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SHOW RESOURCE MONITORS LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW RESOURCE_MONITORS ... WITH PRIVILEGES <>'.",
            "SHOW RESOURCE MONITORS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in VIEW'.",
            "SHOW RESOURCE MONITORS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in SERVICE'.",
            "SHOW RESOURCE MONITORS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in APPLICATION'.",
            "SHOW RESOURCE MONITORS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in CLASS'.",
            "SHOW RESOURCE MONITORS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in INSTANCE'.",
            "SHOW RESOURCE MONITORS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in TABLE'.",
            "SHOW RESOURCE MONITORS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in TABLE'.",
            "SHOW RESOURCE MONITORS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW RESOURCE MONITORS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW RESOURCE MONITORS IN ACCOUNT LIMIT 0");
        assertShapes(RESOURCE_MONITORS_COLUMNS,
            "SHOW RESOURCE MONITORS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type RESOURCE "
                + "MONITOR in SCHEMA'.",
            "SHOW RESOURCE MONITORS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW RESOURCE MONITORS IN nosuch_a.nosuch_b.nosuch_c");
    }

    @Test
    public void shares() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SHARES_COLUMNS,
            "SHOW SHARES",
            "SHOW SHARES LIKE 'zz%'",
            "SHOW SHARES IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "DATABASE'.",
            "SHOW SHARES IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "SCHEMA'.",
            "SHOW SHARES IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "DATABASE'.",
            "SHOW SHARES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "SCHEMA'.",
            "SHOW SHARES IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Account 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW SHARES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "TABLE'.",
            "SHOW SHARES IN TABLE ab");
        assertShapes(SHARES_COLUMNS,
            "SHOW SHARES STARTS WITH 'zz'",
            "SHOW SHARES LIMIT 1",
            "SHOW SHARES LIMIT 1 FROM 'zz'",
            "SHOW TERSE SHARES",
            "SHOW SHARES HISTORY",
            "SHOW SHARES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'nosuchword'.",
            "SHOW SHARES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "SCHEMA'.",
            "SHOW SHARES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "DATABASE'.",
            "SHOW SHARES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW SHARES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SHARES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '5'.",
            "SHOW SHARES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "SHOW SHARES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SHOW SHARES LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW SHARES ... WITH PRIVILEGES <>'.",
            "SHOW SHARES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "VIEW'.",
            "SHOW SHARES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "SERVICE'.",
            "SHOW SHARES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "APPLICATION'.",
            "SHOW SHARES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "CLASS'.",
            "SHOW SHARES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "INSTANCE'.",
            "SHOW SHARES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "TABLE'.",
            "SHOW SHARES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "TABLE'.",
            "SHOW SHARES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW SHARES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SHARES IN ACCOUNT LIMIT 0");
        assertShapes(SHARES_COLUMNS,
            "SHOW SHARES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SHARE in "
                + "SCHEMA'.",
            "SHOW SHARES IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW SHARES IN nosuch_a.nosuch_b.nosuch_c");
    }

    @Test
    public void connections() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(CONNECTIONS_COLUMNS,
            "SHOW CONNECTIONS",
            "SHOW CONNECTIONS LIKE 'zz%'",
            "SHOW CONNECTIONS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in DATABASE'.",
            "SHOW CONNECTIONS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in SCHEMA'.",
            "SHOW CONNECTIONS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in DATABASE'.",
            "SHOW CONNECTIONS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in SCHEMA'.",
            "SHOW CONNECTIONS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Account 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW CONNECTIONS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in TABLE'.",
            "SHOW CONNECTIONS IN TABLE ab");
        assertShapes(CONNECTIONS_COLUMNS,
            "SHOW CONNECTIONS STARTS WITH 'zz'",
            "SHOW CONNECTIONS LIMIT 1",
            "SHOW CONNECTIONS LIMIT 1 FROM 'zz'",
            "SHOW TERSE CONNECTIONS",
            "SHOW CONNECTIONS HISTORY",
            "SHOW CONNECTIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'nosuchword'.",
            "SHOW CONNECTIONS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in SCHEMA'.",
            "SHOW CONNECTIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in DATABASE'.",
            "SHOW CONNECTIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW CONNECTIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW CONNECTIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '5'.",
            "SHOW CONNECTIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "SHOW CONNECTIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
            "SHOW CONNECTIONS LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW CONNECTIONS ... WITH PRIVILEGES <>'.",
            "SHOW CONNECTIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in VIEW'.",
            "SHOW CONNECTIONS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in SERVICE'.",
            "SHOW CONNECTIONS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in APPLICATION'.",
            "SHOW CONNECTIONS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in CLASS'.",
            "SHOW CONNECTIONS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in INSTANCE'.",
            "SHOW CONNECTIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in TABLE'.",
            "SHOW CONNECTIONS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in TABLE'.",
            "SHOW CONNECTIONS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW CONNECTIONS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW CONNECTIONS IN ACCOUNT LIMIT 0");
        assertShapes(CONNECTIONS_COLUMNS,
            "SHOW CONNECTIONS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CONNECTION "
                + "in SCHEMA'.",
            "SHOW CONNECTIONS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW CONNECTIONS IN nosuch_a.nosuch_b.nosuch_c");
    }

    @Test
    public void applications() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(APPLICATIONS_COLUMNS,
            "SHOW APPLICATIONS",
            "SHOW APPLICATIONS LIKE 'zz%'",
            "SHOW APPLICATIONS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in DATABASE'.",
            "SHOW APPLICATIONS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in SCHEMA'.",
            "SHOW APPLICATIONS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in DATABASE'.",
            "SHOW APPLICATIONS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in SCHEMA'.",
            "SHOW APPLICATIONS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATIONS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in TABLE'.",
            "SHOW APPLICATIONS IN TABLE ab");
        assertShapes(APPLICATIONS_COLUMNS,
            "SHOW APPLICATIONS STARTS WITH 'zz'",
            "SHOW APPLICATIONS LIMIT 1",
            "SHOW APPLICATIONS LIMIT 1 FROM 'zz'",
            "SHOW TERSE APPLICATIONS",
            "SHOW APPLICATIONS HISTORY",
            "SHOW APPLICATIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'nosuchword'.",
            "SHOW APPLICATIONS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in SCHEMA'.",
            "SHOW APPLICATIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in DATABASE'.",
            "SHOW APPLICATIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW APPLICATIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '5'.",
            "SHOW APPLICATIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "SHOW APPLICATIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SHOW APPLICATIONS LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW APPLICATIONS ... WITH PRIVILEGES <>'.",
            "SHOW APPLICATIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in VIEW'.",
            "SHOW APPLICATIONS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in SERVICE'.",
            "SHOW APPLICATIONS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: Cannot show objects of type APPLICATION on APPLICATION.",
            "SHOW APPLICATIONS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in CLASS'.",
            "SHOW APPLICATIONS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in INSTANCE'.",
            "SHOW APPLICATIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in TABLE'.",
            "SHOW APPLICATIONS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in TABLE'.",
            "SHOW APPLICATIONS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATIONS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW APPLICATIONS IN ACCOUNT LIMIT 0");
        assertShapes(APPLICATIONS_COLUMNS,
            "SHOW APPLICATIONS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "in SCHEMA'.",
            "SHOW APPLICATIONS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW APPLICATIONS IN nosuch_a.nosuch_b.nosuch_c");
    }

    @Test
    public void applicationPackages() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(APPLICATION_PACKAGES_COLUMNS,
            "SHOW APPLICATION PACKAGES",
            "SHOW APPLICATION PACKAGES LIKE 'zz%'",
            "SHOW APPLICATION PACKAGES IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in DATABASE'.",
            "SHOW APPLICATION PACKAGES IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in SCHEMA'.",
            "SHOW APPLICATION PACKAGES IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in DATABASE'.",
            "SHOW APPLICATION PACKAGES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in SCHEMA'.",
            "SHOW APPLICATION PACKAGES IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATION PACKAGES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in TABLE'.",
            "SHOW APPLICATION PACKAGES IN TABLE ab");
        assertShapes(APPLICATION_PACKAGES_COLUMNS,
            "SHOW APPLICATION PACKAGES STARTS WITH 'zz'",
            "SHOW APPLICATION PACKAGES LIMIT 1",
            "SHOW APPLICATION PACKAGES LIMIT 1 FROM 'zz'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), kind:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216)",
            "SHOW TERSE APPLICATION PACKAGES");
        assertShapes(APPLICATION_PACKAGES_COLUMNS,
            "SHOW APPLICATION PACKAGES HISTORY",
            "SHOW APPLICATION PACKAGES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'nosuchword'.",
            "SHOW APPLICATION PACKAGES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in SCHEMA'.",
            "SHOW APPLICATION PACKAGES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in DATABASE'.",
            "SHOW APPLICATION PACKAGES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATION PACKAGES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW APPLICATION PACKAGES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '5'.",
            "SHOW APPLICATION PACKAGES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SHOW APPLICATION PACKAGES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "SHOW APPLICATION PACKAGES LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW APPLICATION_PACKAGES ... WITH PRIVILEGES <>'.",
            "SHOW APPLICATION PACKAGES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in VIEW'.",
            "SHOW APPLICATION PACKAGES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in SERVICE'.",
            "SHOW APPLICATION PACKAGES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in APPLICATION'.",
            "SHOW APPLICATION PACKAGES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in CLASS'.",
            "SHOW APPLICATION PACKAGES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in INSTANCE'.",
            "SHOW APPLICATION PACKAGES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in TABLE'.",
            "SHOW APPLICATION PACKAGES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in TABLE'.",
            "SHOW APPLICATION PACKAGES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW APPLICATION PACKAGES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW APPLICATION PACKAGES IN ACCOUNT LIMIT 0");
        assertShapes(APPLICATION_PACKAGES_COLUMNS,
            "SHOW APPLICATION PACKAGES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type APPLICATION "
                + "PACKAGE in SCHEMA'.",
            "SHOW APPLICATION PACKAGES IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW APPLICATION PACKAGES IN ACCOUNT nosuch_a.nosuch_b.nosuch_c");
    }
}
