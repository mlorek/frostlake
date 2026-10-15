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
 * The scopes of SHOW NETWORK RULES, SHOW SECRETS, SHOW PASSWORD POLICIES and SHOW NETWORK POLICIES. A table, a
 * view and a class hold none of these kinds — Cannot show objects of type SECRET in TABLE, with the name or
 * without it — an application is looked up, and a bare name is a schema of the current database, never a
 * database. A network rule listing names no scope it misses; a network policy is the account's, so any kind of
 * object for a scope holds none and a bare name is another account's. SHOW PASSWORD POLICIES takes ON ACCOUNT
 * and ON USER, says a database lists none, and refuses any other ON where it stands (live-verified).
 */
public class SecurityListingScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void networkRules() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE nosuch_t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in CLASS'.",
            "SHOW NETWORK RULES IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in VIEW'.",
            "SHOW NETWORK RULES IN VIEW t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES LIKE 'x' IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE t LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "type:VARCHAR(16777216), mode:VARCHAR(16777216), entries_in_valuelist:NUMBER(9,0), "
                + "owner_role_type:VARCHAR(16777216)",
            "SHOW NETWORK RULES IN SCHEMA test_db.test_schema");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "type:VARCHAR(16777216), mode:VARCHAR(16777216), entries_in_valuelist:NUMBER(9,0), "
                + "owner_role_type:VARCHAR(16777216)",
            "SHOW NETWORK RULES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN SCHEMA nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN DATABASE nosuch_db_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN nosuch_s_zq7");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "type:VARCHAR(16777216), mode:VARCHAR(16777216), entries_in_valuelist:NUMBER(9,0), "
                + "owner_role_type:VARCHAR(16777216)",
            "SHOW NETWORK RULES IN test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE test_schema.t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SHOW NETWORK RULES IN TABLE t x");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN CLASS");
    }

    @Test
    public void secrets() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE nosuch_t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_ZQ7.",
            "SHOW SECRETS IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_ZQ7.",
            "SHOW SECRETS IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "CLASS'.",
            "SHOW SECRETS IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "VIEW'.",
            "SHOW SECRETS IN VIEW t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS LIKE 'x' IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE t LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "secret_type:VARCHAR(16777216), oauth_scopes:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW SECRETS IN SCHEMA test_db.test_schema");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "secret_type:VARCHAR(16777216), oauth_scopes:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW SECRETS IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW SECRETS IN SCHEMA nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW SECRETS IN DATABASE nosuch_db_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW SECRETS IN nosuch_s_zq7");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), owner:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "secret_type:VARCHAR(16777216), oauth_scopes:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW SECRETS IN test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE test_schema.t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SHOW SECRETS IN TABLE t x");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.APPLICATION' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.APPLICATION.",
            "SHOW SECRETS IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.CLASS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.CLASS.",
            "SHOW SECRETS IN CLASS");
    }

    @Test
    public void passwordPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE nosuch_t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_ZQ7.",
            "SHOW PASSWORD POLICIES IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_ZQ7.",
            "SHOW PASSWORD POLICIES IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in CLASS'.",
            "SHOW PASSWORD POLICIES IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in VIEW'.",
            "SHOW PASSWORD POLICIES IN VIEW t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES LIKE 'x' IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE t LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW PASSWORD POLICIES IN SCHEMA test_db.test_schema");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW PASSWORD POLICIES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW PASSWORD POLICIES IN SCHEMA nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW PASSWORD POLICIES IN DATABASE nosuch_db_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW PASSWORD POLICIES IN nosuch_s_zq7");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW PASSWORD POLICIES IN test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE test_schema.t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'.",
            "SHOW PASSWORD POLICIES IN TABLE t x");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.APPLICATION' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.APPLICATION.",
            "SHOW PASSWORD POLICIES IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.CLASS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.CLASS.",
            "SHOW PASSWORD POLICIES IN CLASS");
    }

    @Test
    public void theOtherListingsInATable() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type MASKING "
                + "POLICY in TABLE'.",
            "SHOW MASKING POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type ROW ACCESS "
                + "POLICY in TABLE'.",
            "SHOW ROW ACCESS POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PROJECTION "
                + "POLICY in TABLE'.",
            "SHOW PROJECTION POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type AGGREGATION "
                + "POLICY in TABLE'.",
            "SHOW AGGREGATION POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type JOIN POLICY "
                + "in TABLE'.",
            "SHOW JOIN POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in TABLE'.",
            "SHOW SESSION POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in TABLE'.",
            "SHOW AUTHENTICATION POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in TABLE'.",
            "SHOW PACKAGES POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in TABLE'.",
            "SHOW PRIVACY POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in TABLE'.",
            "SHOW NETWORK POLICIES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TAG in "
                + "TABLE'.",
            "SHOW TAGS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEQUENCE in "
                + "TABLE'.",
            "SHOW SEQUENCES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FILE FORMAT "
                + "in TABLE'.",
            "SHOW FILE FORMATS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type STAGE in "
                + "TABLE'.",
            "SHOW STAGES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type STREAM in "
                + "TABLE'.",
            "SHOW STREAMS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TASK in "
                + "TABLE'.",
            "SHOW TASKS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PIPE in "
                + "TABLE'.",
            "SHOW PIPES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW FUNCTIONS IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PROCEDURE "
                + "in TABLE'.",
            "SHOW PROCEDURES IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES ON TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES IN TABLE t ON ACCOUNT");
    }

    @Test
    public void networkPolicies() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in DATABASE'.",
            "SHOW NETWORK POLICIES IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in DATABASE'.",
            "SHOW NETWORK POLICIES IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in SCHEMA'.",
            "SHOW NETWORK POLICIES IN SCHEMA test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in SCHEMA'.",
            "SHOW NETWORK POLICIES IN SCHEMA");
        assertShape(
            "REFUSED SQL compilation error: | Account 'NOSUCH_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW NETWORK POLICIES IN nosuch_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in VIEW'.",
            "SHOW NETWORK POLICIES IN VIEW t");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in VIEW'.",
            "SHOW NETWORK POLICIES IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in CLASS'.",
            "SHOW NETWORK POLICIES IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in APPLICATION'.",
            "SHOW NETWORK POLICIES IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in APPLICATION PACKAGE'.",
            "SHOW NETWORK POLICIES IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in TABLE'.",
            "SHOW NETWORK POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in TABLE'.",
            "SHOW NETWORK POLICIES LIKE 'x' IN TABLE t");
        assertShape(
            "REFUSED SQL compilation error: | Account 'TEST_DB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW NETWORK POLICIES IN test_db");
        assertShape(
            "REFUSED SQL compilation error: | Account 'CLASS' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW NETWORK POLICIES IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: | Account 'APPLICATION' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SHOW NETWORK POLICIES IN APPLICATION");
        shape(
            "SHOW NETWORK POLICIES IN TABLE t LIMIT 1");
    }

    @Test
    public void moreScopesOfTheThreeListings() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in VIEW'.",
            "SHOW NETWORK RULES IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in CLASS'.",
            "SHOW NETWORK RULES IN CLASS nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN APPLICATION nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'.",
            "SHOW NETWORK RULES IN APPLICATION PACKAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE t STARTS WITH 'x'");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN test_db");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN nosuch_db_zq7.nosuch_s");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN test_db.nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN DATABASE test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN SCHEMA test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in TABLE'.",
            "SHOW NETWORK RULES IN TABLE t.u.v.w");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "RULE in VIEW'.",
            "SHOW NETWORK RULES IN VIEW nosuch_v");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "VIEW'.",
            "SHOW SECRETS IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "CLASS'.",
            "SHOW SECRETS IN CLASS nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SECRETS IN APPLICATION nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "SHOW SECRETS IN APPLICATION PACKAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE t STARTS WITH 'x'");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW SECRETS IN test_db");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW SECRETS IN nosuch_db_zq7.nosuch_s");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW SECRETS IN test_db.nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Database 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_SCHEMA.",
            "SHOW SECRETS IN DATABASE test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW SECRETS IN SCHEMA test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "TABLE'.",
            "SHOW SECRETS IN TABLE t.u.v.w");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SECRET in "
                + "VIEW'.",
            "SHOW SECRETS IN VIEW nosuch_v");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in VIEW'.",
            "SHOW PASSWORD POLICIES IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in CLASS'.",
            "SHOW PASSWORD POLICIES IN CLASS nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW PASSWORD POLICIES IN APPLICATION nosuch_a.nosuch_b");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'.",
            "SHOW PASSWORD POLICIES IN APPLICATION PACKAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE t STARTS WITH 'x'");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW PASSWORD POLICIES IN test_db");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW PASSWORD POLICIES IN nosuch_db_zq7.nosuch_s");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "SHOW PASSWORD POLICIES IN test_db.nosuch_s_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Database 'TEST_SCHEMA' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_SCHEMA.",
            "SHOW PASSWORD POLICIES IN DATABASE test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW PASSWORD POLICIES IN SCHEMA test_db");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in TABLE'.",
            "SHOW PASSWORD POLICIES IN TABLE t.u.v.w");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PASSWORD "
                + "POLICY in VIEW'.",
            "SHOW PASSWORD POLICIES IN VIEW nosuch_v");
    }

    @Test
    public void theHolderOfAPasswordPolicy() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES ON SCHEMA test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES ON x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES ON");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216), "
                + "set_on:VARCHAR(16777216)",
            "SHOW PASSWORD POLICIES ON ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW PASSWORD POLICIES ON DATABASE'.",
            "SHOW PASSWORD POLICIES ON DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'ON'.",
            "SHOW PASSWORD POLICIES LIKE 'x' ON TABLE t");
        shape(
            "SHOW NETWORK RULES ON ACCOUNT");
        shape(
            "SHOW SECRETS ON ACCOUNT");
    }
}
