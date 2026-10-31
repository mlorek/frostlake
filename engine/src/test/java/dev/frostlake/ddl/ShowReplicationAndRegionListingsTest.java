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
 * REPLICATION DATABASES, REPLICATION GROUPS and FAILOVER GROUPS read a LIKE and an account - after ACCOUNT, or
 * written alone, where DATABASE and SCHEMA are no names - and nothing else: TERSE is refused at the listing's
 * own word, HISTORY and LIMIT at theirs, a STARTS WITH stacks a second line and a WITH PRIVILEGES says its line
 * twice. A LIMIT or a STARTS written alone after IN ACCOUNT is the account's name. The account is looked up and
 * missed, the groups naming it and the databases answering that no object exists. REPLICATION ACCOUNTS reads the
 * LIKE alone. Any other word before GROUPS, DATABASES or ACCOUNTS opens no listing, and the second words of the
 * listings named by two are refused where they stand alone. DELEGATED AUTHORIZATIONS reads TERSE, a user after BY
 * USER and a security integration after TO SECURITY INTEGRATION, each looked up. REGIONS reads LIKE over the
 * region's identifier, in any case, and TERSE, HISTORY, STARTS WITH and LIMIT, and refuses any scope by writing
 * the statement back in upper case, up to the keyword of a scope that names its object. A listing that reads no
 * scope at all is refused at its IN, in one line, and a STARTS WITH a listing does not read reports a second
 * line at its prefix only straight after the listing's own words - or after a parameter listing's SESSION. The
 * words of these listings stay names: a table, a column and an alias may be called any of them. Every statement
 * reads only, but for one scratch table; every answer is live-verified.
 */
public class ShowReplicationAndRegionListingsTest extends MeasuredCellsTestSupport {

    private static final String DELEGATED_AUTHORIZATIONS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, user_name:VARCHAR(16777216), role_name:VARCHAR(16777216), "
            + "consented_roles:VARCHAR(16777216), integration_name:VARCHAR(16777216), "
            + "integration_status:VARCHAR(16777216), can_switch_roles:VARCHAR(16777216)";

    private static final String REGIONS_COLUMNS =
        "snowflake_region:VARCHAR(16777216), cloud:VARCHAR(16777216), region:VARCHAR(16777216), "
            + "display_name:VARCHAR(16777216)";

    private static final String REPLICATION_ACCOUNTS_COLUMNS =
        "snowflake_region:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, account_name:VARCHAR(16777216), "
            + "account_locator:VARCHAR(16777216), comment:VARCHAR(16777216), organization_name:VARCHAR(16777216), "
            + "is_org_admin:VARCHAR(16777216)";

    private static final String REPLICATION_DATABASES_COLUMNS =
        "snowflake_region:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, account_name:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), comment:VARCHAR(16777216), is_primary:VARCHAR(16777216), "
            + "primary:VARCHAR(16777216), replication_allowed_to_accounts:VARCHAR(16777216), "
            + "failover_allowed_to_accounts:VARCHAR(16777216), organization_name:VARCHAR(16777216), "
            + "account_locator:VARCHAR(16777216)";

    private static final String REPLICATION_GROUPS_COLUMNS =
        "snowflake_region:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, account_name:VARCHAR(16777216), "
            + "name:VARCHAR(16777216), type:VARCHAR(16777216), comment:VARCHAR(16777216), "
            + "is_primary:VARCHAR(16777216), primary:VARCHAR(16777216), object_types:VARCHAR(16777216), "
            + "allowed_integration_types:VARCHAR(16777216), allowed_accounts:VARCHAR(16777216), "
            + "organization_name:VARCHAR(16777216), account_locator:VARCHAR(16777216), "
            + "replication_schedule:VARCHAR(16777216), secondary_state:VARCHAR(16777216), "
            + "next_scheduled_refresh:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
            + "is_listing_auto_fulfillment_group:VARCHAR(16777216), error_integration:VARCHAR(16777216), "
            + "is_primary_read_only:VARCHAR(16777216), is_optimized_refresh_enabled:VARCHAR(16777216)";

    @Test
    public void replicationDatabases() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(REPLICATION_DATABASES_COLUMNS,
            "SHOW REPLICATION DATABASES",
            "SHOW REPLICATION DATABASES LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'.",
            "SHOW REPLICATION DATABASES IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'.",
            "SHOW REPLICATION DATABASES IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "SHOW REPLICATION DATABASES IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'test_db'.",
            "SHOW REPLICATION DATABASES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'test_db'.",
            "SHOW REPLICATION DATABASES IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TABLE'.",
            "SHOW REPLICATION DATABASES IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 39 unexpected ''zz''.",
            "SHOW REPLICATION DATABASES STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'LIMIT'.",
            "SHOW REPLICATION DATABASES LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'LIMIT'.",
            "SHOW REPLICATION DATABASES LIMIT 1 FROM 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'REPLICATION'.",
            "SHOW TERSE REPLICATION DATABASES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'HISTORY'.",
            "SHOW REPLICATION DATABASES HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected ''zz''.",
            "SHOW REPLICATION DATABASES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'nosuchword'.",
            "SHOW REPLICATION DATABASES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'nosuch_schema_x9'.",
            "SHOW REPLICATION DATABASES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'nosuch_db_x9'.",
            "SHOW REPLICATION DATABASES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'LIMIT'.",
            "SHOW REPLICATION DATABASES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '5'.",
            "SHOW REPLICATION DATABASES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "SHOW REPLICATION DATABASES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "SHOW REPLICATION DATABASES LIKE");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN ACCOUNT nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW REPLICATION DATABASES IN nosuch_a.nosuch_b.nosuch_c");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'PRIVILEGES'. | "
                + "syntax error line 1 at position 32 unexpected 'PRIVILEGES'.",
            "SHOW REPLICATION DATABASES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN ACCOUNT LIMIT");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES LIKE 'zz%' IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'LIMIT'.",
            "SHOW REPLICATION DATABASES IN ACCOUNT nosuch_x9 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW REPLICATION DATABASES IN \"nosuch lower\".\"b c\"");
    }

    @Test
    public void replicationGroups() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(REPLICATION_GROUPS_COLUMNS,
            "SHOW REPLICATION GROUPS",
            "SHOW REPLICATION GROUPS LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'.",
            "SHOW REPLICATION GROUPS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "SHOW REPLICATION GROUPS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "SHOW REPLICATION GROUPS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'test_db'.",
            "SHOW REPLICATION GROUPS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'test_db'.",
            "SHOW REPLICATION GROUPS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Account 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TABLE'.",
            "SHOW REPLICATION GROUPS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 36 unexpected ''zz''.",
            "SHOW REPLICATION GROUPS STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LIMIT'.",
            "SHOW REPLICATION GROUPS LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LIMIT'.",
            "SHOW REPLICATION GROUPS LIMIT 1 FROM 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'REPLICATION'.",
            "SHOW TERSE REPLICATION GROUPS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'HISTORY'.",
            "SHOW REPLICATION GROUPS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ''zz''.",
            "SHOW REPLICATION GROUPS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'nosuchword'.",
            "SHOW REPLICATION GROUPS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'nosuch_schema_x9'.",
            "SHOW REPLICATION GROUPS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'nosuch_db_x9'.",
            "SHOW REPLICATION GROUPS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LIMIT'.",
            "SHOW REPLICATION GROUPS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '5'.",
            "SHOW REPLICATION GROUPS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SHOW REPLICATION GROUPS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SHOW REPLICATION GROUPS LIKE");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS IN nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_B' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS IN ACCOUNT nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW REPLICATION GROUPS IN nosuch_a.nosuch_b.nosuch_c");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'PRIVILEGES'. | "
                + "syntax error line 1 at position 29 unexpected 'PRIVILEGES'.",
            "SHOW REPLICATION GROUPS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Account 'LIMIT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS IN ACCOUNT LIMIT");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW REPLICATION GROUPS LIKE 'zz%' IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'LIMIT'.",
            "SHOW REPLICATION GROUPS IN ACCOUNT nosuch_x9 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Account '\"b c\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT \"b c\".",
            "SHOW REPLICATION GROUPS IN \"nosuch lower\".\"b c\"");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 36 unexpected ''zz''.",
            "SHOW REPLICATION GROUPS STARTS WITH 'zz' LIMIT 1");
    }

    @Test
    public void failoverGroups() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(REPLICATION_GROUPS_COLUMNS,
            "SHOW FAILOVER GROUPS",
            "SHOW FAILOVER GROUPS LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "SHOW FAILOVER GROUPS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SHOW FAILOVER GROUPS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "SHOW FAILOVER GROUPS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'test_db'.",
            "SHOW FAILOVER GROUPS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'test_db'.",
            "SHOW FAILOVER GROUPS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Account 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'TABLE'.",
            "SHOW FAILOVER GROUPS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 33 unexpected ''zz''.",
            "SHOW FAILOVER GROUPS STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIMIT'.",
            "SHOW FAILOVER GROUPS LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIMIT'.",
            "SHOW FAILOVER GROUPS LIMIT 1 FROM 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FAILOVER'.",
            "SHOW TERSE FAILOVER GROUPS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'HISTORY'.",
            "SHOW FAILOVER GROUPS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ''zz''.",
            "SHOW FAILOVER GROUPS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'nosuchword'.",
            "SHOW FAILOVER GROUPS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'nosuch_schema_x9'.",
            "SHOW FAILOVER GROUPS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'nosuch_db_x9'.",
            "SHOW FAILOVER GROUPS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIMIT'.",
            "SHOW FAILOVER GROUPS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '5'.",
            "SHOW FAILOVER GROUPS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "SHOW FAILOVER GROUPS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SHOW FAILOVER GROUPS LIKE");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS IN nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_B' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS IN ACCOUNT nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: account identifier 'NOSUCH_A.NOSUCH_B.NOSUCH_C' has too many "
                + "qualifiers",
            "SHOW FAILOVER GROUPS IN nosuch_a.nosuch_b.nosuch_c");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'PRIVILEGES'. | "
                + "syntax error line 1 at position 26 unexpected 'PRIVILEGES'.",
            "SHOW FAILOVER GROUPS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Account 'LIMIT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS IN ACCOUNT LIMIT");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW FAILOVER GROUPS LIKE 'zz%' IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'LIMIT'.",
            "SHOW FAILOVER GROUPS IN ACCOUNT nosuch_x9 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Account '\"b c\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT \"b c\".",
            "SHOW FAILOVER GROUPS IN \"nosuch lower\".\"b c\"");
    }

    @Test
    public void replicationAccounts() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(REPLICATION_ACCOUNTS_COLUMNS,
            "SHOW REPLICATION ACCOUNTS",
            "SHOW REPLICATION ACCOUNTS LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ACCOUNT'.",
            "SHOW REPLICATION ACCOUNTS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'.",
            "SHOW REPLICATION ACCOUNTS LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'REPLICATION'.",
            "SHOW TERSE REPLICATION ACCOUNTS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'HISTORY'.",
            "SHOW REPLICATION ACCOUNTS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'STARTS'.",
            "SHOW REPLICATION ACCOUNTS STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'nosuch_x9'.",
            "SHOW REPLICATION ACCOUNTS IN nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "SHOW REPLICATION ACCOUNTS LIKE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'nosuchword'.",
            "SHOW REPLICATION ACCOUNTS nosuchword");
    }

    @Test
    public void delegatedAuthorizations() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(DELEGATED_AUTHORIZATIONS_COLUMNS,
            "SHOW DELEGATED AUTHORIZATIONS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIKE'.",
            "SHOW DELEGATED AUTHORIZATIONS LIKE 'zz%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'STARTS'.",
            "SHOW DELEGATED AUTHORIZATIONS STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIMIT'.",
            "SHOW DELEGATED AUTHORIZATIONS LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIMIT'.",
            "SHOW DELEGATED AUTHORIZATIONS LIMIT 1 FROM 'zz'");
        assertShapes(DELEGATED_AUTHORIZATIONS_COLUMNS,
            "SHOW TERSE DELEGATED AUTHORIZATIONS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'HISTORY'.",
            "SHOW DELEGATED AUTHORIZATIONS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIKE'.",
            "SHOW DELEGATED AUTHORIZATIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'nosuchword'.",
            "SHOW DELEGATED AUTHORIZATIONS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIMIT'.",
            "SHOW DELEGATED AUTHORIZATIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIKE'.",
            "SHOW DELEGATED AUTHORIZATIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DELEGATED AUTHORIZATIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIKE'.",
            "SHOW DELEGATED AUTHORIZATIONS LIKE");
        assertShape(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
            "SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_user_x9");
        assertShape(
            "REFUSED SQL compilation error: | Integration 'NOSUCH_INT_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_INT_X9.",
            "SHOW DELEGATED AUTHORIZATIONS TO SECURITY INTEGRATION nosuch_int_x9");
        assertShape(
            "REFUSED SQL compilation error: | Invalid or missing object type. Please specify a valid object type",
            "SHOW DELEGATED AUTHORIZATIONS BY USER");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '<EOF>'.",
            "SHOW DELEGATED AUTHORIZATIONS TO SECURITY INTEGRATION");
        assertShape(
            "REFUSED SQL compilation error: | Invalid or missing object type. Please specify a valid object type",
            "SHOW DELEGATED AUTHORIZATIONS BY nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'LIMIT'.",
            "SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_user_x9 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'nosuch_x9'.",
            "SHOW DELEGATED AUTHORIZATIONS BY ROLE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
            "SHOW TERSE DELEGATED AUTHORIZATIONS BY USER nosuch_user_x9");
        assertShape(
            "REFUSED SQL compilation error: | User '\"nosuch lower\"' does not exist or not authorized.",
            "SHOW DELEGATED AUTHORIZATIONS BY USER \"nosuch lower\"");
        assertShape(
            "REFUSED SQL compilation error: | Integration '\"nosuch lower\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on INTEGRATION \"nosuch lower\".",
            "SHOW DELEGATED AUTHORIZATIONS TO SECURITY INTEGRATION \"nosuch lower\"");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'nosuch_x9'.",
            "SHOW DELEGATED AUTHORIZATIONS TO INTEGRATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'TO'.",
            "SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_x9 TO SECURITY INTEGRATION nosuch_y9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SHOW DELEGATED AUTHORIZATIONS BY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SHOW DELEGATED AUTHORIZATIONS TO");
    }

    @Test
    public void regions() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS",
            "SHOW REGIONS LIKE 'zz%'");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN DATABASE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN DATABASE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN SCHEMA'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN SCHEMA");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN DATABASE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN DATABASE test_db");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN SCHEMA'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TEST_DB.TEST_SCHEMA'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN test_db.test_schema");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TABLE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN TABLE ab");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS STARTS WITH 'zz'",
            "SHOW REGIONS LIMIT 1",
            "SHOW REGIONS LIMIT 1 FROM 'zz'",
            "SHOW TERSE REGIONS",
            "SHOW REGIONS HISTORY");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS LIKE 'ZZ%' IN ACCOUNT STARTS WITH 'ZZ' LIMIT 1'; use 'SHOW "
                + "REGIONS IN DATA EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'nosuchword'.",
            "SHOW REGIONS nosuchword");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN SCHEMA'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN DATABASE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW REGIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '5'.",
            "SHOW REGIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'.",
            "SHOW REGIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "SHOW REGIONS LIKE");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS");
        assertAnswer(
            "AWS_US_WEST_2|aws|us-west-2|US West (Oregon)",
            "SHOW REGIONS LIKE 'AWS_US_WEST_2'");
        assertAnswer(
            "AWS_US_WEST_2|aws|us-west-2|US West (Oregon)",
            "SHOW REGIONS LIKE 'aws_us_west_2'");
        assertAnswer(
            "AWS_US_WEST_2|aws|us-west-2|US West (Oregon)",
            "SHOW REGIONS LIKE 'AWS_US_WEST%'");
        assertShape(
            "REFUSED Unsupported feature 'SHOW DEPLOYMENT_LOCATIONS ... WITH PRIVILEGES <>'.",
            "SHOW REGIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN VIEW'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN VIEW abv");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN SERVICE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TOK_APPLICATION_INSTANCE'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN CLASS'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN NOSUCH_CLASS_X9 INST_X9'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TABLE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN TABLE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TABLE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN NOSUCH_X9'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT LIMIT 0'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT LIMIT 0");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS HISTORY LIMIT 1");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN SCHEMA'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN SCHEMA test_db.nosuch_schema_x9");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS STARTS WITH 'AWS_US'",
            "SHOW REGIONS STARTS WITH 'aws_us'",
            "SHOW REGIONS LIKE '%oregon%'");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS LIKE 'aws'");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS LIKE 'us-west-2'");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShapes(REGIONS_COLUMNS,
            "SHOW REGIONS LIKE '%'",
            "SHOW REGIONS HISTORY",
            "SHOW TERSE REGIONS");
        assertShape(
            "REFUSED Unsupported feature 'SHOW TERSE REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW TERSE REGIONS IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS HISTORY IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS HISTORY IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW TERSE REGIONS HISTORY IN ACCOUNT'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW TERSE REGIONS HISTORY IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT nosuch_x9 LIMIT 1");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT LIMIT 1 FROM 'ZZ''; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT LIMIT 1 FROM 'zz'");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT LIMIT 0'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT LIMIT 0");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN \"QUOTED LOWER\"'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN \"quoted lower\"");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN NOSUCH_X9'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN NOSUCH_CLASS_X9 INST_X9'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT STARTS WITH 'ZZ''; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT STARTS WITH 'zz'");
        assertShape(
            "REFUSED Unsupported feature 'SHOW DEPLOYMENT_LOCATIONS ... WITH PRIVILEGES <>'.",
            "SHOW REGIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT WITH PRIVILEGES USAGE'; use 'SHOW REGIONS IN "
                + "DATA EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "show regions in account");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS LIKE 'A''B' IN ACCOUNT'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS LIKE 'a''b' IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN IDENTIFIER('NOSUCH_X9')'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN IDENTIFIER('nosuch_x9')");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN NOSUCH_X9 LIMIT 1'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN nosuch_x9 LIMIT 1");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN VIEW'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN VIEW");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS  IN   ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS  IN   ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TOK_APPLICATION_PACKAGE'; use 'SHOW REGIONS IN DATA "
                + "EXCHANGE <exchange_name>' instead.",
            "SHOW REGIONS IN APPLICATION PACKAGE nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN TOK_COMPUTE_POOL'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN COMPUTE POOL nosuch_x9");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS /* C */ IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS /* c */ IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN ACCOUNT'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN ACCOUNT;");
        assertShape(
            "REFUSED Unsupported feature 'SHOW REGIONS IN PIPE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN PIPE nosuch_x9");
    }

    @Test
    public void wordsThatOpenNoListing() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'DATABASES'.",
            "SHOW FAILOVER DATABASES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'DATABASES'.",
            "SHOW FOO_X9 DATABASES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'GROUPS'.",
            "SHOW FOO_X9 GROUPS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'GROUPS'.",
            "SHOW TERSE FOO_X9 GROUPS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SHOW REPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW FAILOVER");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'GROUPS'.",
            "SHOW GROUPS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'MONITORS'.",
            "SHOW MONITORS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW EXPORTED");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "SHOW DELEGATED");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'AUTHORIZATIONS'.",
            "SHOW AUTHORIZATIONS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW RESOURCE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '<EOF>'.",
            "SHOW GIT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SHOW SEMANTIC");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SHOW DATA METRIC");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SHOW APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'REPOSITORIES'.",
            "SHOW REPOSITORIES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'POLICIES'.",
            "SHOW POLICIES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x9'.",
            "SHOW VIEWS IN EXPORTED x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x9'.",
            "SHOW VIEWS IN MONITORS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x9'.",
            "SHOW VIEWS IN SHARES x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x9'.",
            "SHOW VIEWS IN INDEXES x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x9'.",
            "SHOW VIEWS IN REGIONS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x9'.",
            "SHOW VIEWS IN GROUPS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x9'.",
            "SHOW VIEWS IN CHANNELS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x9'.",
            "SHOW VIEWS IN SNAPSHOTS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x9'.",
            "SHOW VIEWS IN DELEGATED x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x9'.",
            "SHOW VIEWS IN AUTHORIZATIONS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x9'.",
            "SHOW VIEWS IN CONNECTIONS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x9'.",
            "SHOW VIEWS IN APPLICATIONS x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x9'.",
            "SHOW VIEWS IN REPOSITORIES x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ACCOUNTS'.",
            "SHOW FAILOVER ACCOUNTS");
        assertAnswer(
            "Table GROUPS successfully created.",
            "CREATE OR REPLACE TABLE groups (shares INT, regions INT, channels INT, indexes INT, snapshots INT, "
                + "monitors INT, exported INT, delegated INT, authorizations INT, connections INT, applications INT)");
        assertAnswer(
            "1",
            "INSERT INTO groups (shares, regions) VALUES (1, 2)");
        assertAnswer(
            "1|2|null|1",
            "SELECT shares, connections.regions, channels AS monitors, 1 exported FROM groups AS connections");
        assertShape(
            "CREATED_ON:TIMESTAMP_LTZ, KIND:VARCHAR(134217728), OWNER_ACCOUNT:VARCHAR(134217728), "
                + "NAME:VARCHAR(134217728), DATABASE_NAME:VARCHAR(134217728), TO:VARCHAR(134217728), "
                + "OWNER:VARCHAR(134217728), COMMENT:VARCHAR(134217728), LISTING_GLOBAL_NAME:VARCHAR(134217728), "
                + "SECURE_OBJECTS_ONLY:VARCHAR(134217728)",
            "SELECT * FROM information_schema.shares LIMIT 0");
        assertShape(
            "table_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), column_name:VARCHAR(16777216), "
                + "data_type:VARCHAR(16777216), null?:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "kind:VARCHAR(16777216), expression:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), autoincrement:VARCHAR(16777216), "
                + "schema_evolution_record:VARCHAR(16777216), write_default:VARCHAR(16777216)",
            "SHOW COLUMNS IN TABLE groups");
    }

    @Test
    public void aListingThatReadsNoScopeIsRefusedAtItsIn() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IN'.",
            "SHOW VARIABLES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IN'.",
            "SHOW VARIABLES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'IN'.",
            "SHOW ORGANIZATION ACCOUNTS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IN'.",
            "SHOW VARIABLES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IN'.",
            "SHOW VARIABLES IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'STARTS'.",
            "SHOW ORGANIZATION ACCOUNTS STARTS WITH 'b'");
    }

    @Test
    public void aStartsWithAListingDoesNotReadStacksItsPrefixOnlyAfterTheListingsOwnWords() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 27 unexpected ''zz''.",
            "SHOW VARIABLES STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 23 unexpected ''zz''.",
            "SHOW LOCKS STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'STARTS'.",
            "SHOW VARIABLES LIKE 'a' STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 27 unexpected ''b''.",
            "SHOW VARIABLES STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'STARTS'.",
            "SHOW GRANTS LIKE 'a' STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 24 unexpected ''b''.",
            "SHOW GRANTS STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'STARTS'.",
            "SHOW PARAMETERS LIKE 'a' STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 28 unexpected ''b''.",
            "SHOW PARAMETERS STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 39 unexpected ''b''.",
            "SHOW PARAMETERS IN SESSION STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''b''.",
            "SHOW LOCKS IN ACCOUNT STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ''b''.",
            "SHOW TRANSACTIONS IN ACCOUNT STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 30 unexpected ''b''.",
            "SHOW TRANSACTIONS STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'STARTS'.",
            "SHOW GRANTS ON TABLE ab STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'STARTS'.",
            "SHOW GRANTS TO ROLE PUBLIC STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'STARTS'.",
            "SHOW PRIMARY KEYS IN TABLE ab STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'STARTS'.",
            "SHOW PRIMARY KEYS STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'STARTS'.",
            "SHOW IMPORTED KEYS IN SCHEMA STARTS WITH 'b'");
    }
}
