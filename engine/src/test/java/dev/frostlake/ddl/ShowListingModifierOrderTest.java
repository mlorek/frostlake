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
 * A SHOW listing reads its modifiers in one order — LIKE, the scope, STARTS WITH, LIMIT … FROM — and a modifier
 * out of place is refused where it stands. SHOW NETWORK POLICIES and SHOW IMAGE REPOSITORIES read STARTS WITH
 * and LIMIT as the other listings do, a LIMIT of 0 is refused, and a fault after a user's NETWORK_POLICY is
 * refused at that word (live-verified).
 */
public class ShowListingModifierOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void modifiersOutOfOrder() {
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IN'.",
            "SHOW SCHEMAS STARTS WITH 'A' IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IN'.",
            "SHOW TABLES LIMIT 1 IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IN'.",
            "SHOW CORTEX SEARCH SERVICES STARTS WITH 'A' IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'IN'.",
            "SHOW VIEWS STARTS WITH 'A' IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IN'.",
            "SHOW TABLES LIMIT 1 FROM 'A' IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'LIKE'.",
            "SHOW TABLES STARTS WITH 'A' LIKE 'B'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'STARTS'.",
            "SHOW TABLES LIMIT 1 STARTS WITH 'A'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES IN SCHEMA test_db.test_schema STARTS WITH 'A' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN SCHEMA test_db.test_schema LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IN'.",
            "SHOW COLUMNS STARTS WITH 'A' IN TABLE test_db.test_schema.nosuch_zq7");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'IN'.",
            "SHOW FUNCTIONS STARTS WITH 'A' IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'IN'.",
            "SHOW TABLES STARTS WITH 'A' IN SCHEMA");
    }

    @Test
    public void policiesAndRepositories() {
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES STARTS WITH 'A' LIMIT 1 FROM 'B'");
        assertShape(
            "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
            "SHOW IMAGE REPOSITORIES LIMIT 10001");
        assertShape(
            "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
            "SHOW NETWORK POLICIES LIMIT 10001");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES STARTS WITH 'ZQ7'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES STARTS WITH 'A'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES LIKE 'A' STARTS WITH 'B' LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 1");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW IMAGE REPOSITORIES LIMIT 0");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIMIT 1 FROM 'A'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIKE 'ZQ7%' STARTS WITH 'ZQ7' LIMIT 1");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW NETWORK POLICIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SHOW IMAGE REPOSITORIES LIMIT x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'IN'.",
            "SHOW NETWORK POLICIES STARTS WITH 'A' IN ACCOUNT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'IN'.",
            "SHOW IMAGE REPOSITORIES STARTS WITH 'A' IN SCHEMA test_db.test_schema");
    }

    @Test
    public void aUsersNetworkPolicy() {
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 SET NETWORK_POLICY = ");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 UNSET NETWORK_POLICY COMMENT");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 SET NETWORK_POLICY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 UNSET NETWORK_POLICY, COMMENT x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'COMMENT'.",
            "ALTER USER nosuch_user_zq7 SET COMMENT = ");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'.",
            "ALTER USER nosuch_user_zq7 UNSET COMMENT NETWORK_POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 SET NETWORK_POLICY = nosuch_policy_zq7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'NETWORK_POLICY'.",
            "ALTER USER IF EXISTS nosuch_user_zq7 UNSET NETWORK_POLICY x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'NETWORK_POLICY'.",
            "ALTER USER IF EXISTS nosuch_user_zq7 SET NETWORK_POLICY =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 SET NETWORK_POLICY = ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 UNSET NETWORK_POLICY =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'NETWORK_POLICY'.",
            "ALTER USER nosuch_user_zq7 SET NETWORK_POLICY nosuch_policy_zq7");
    }

    @Test
    public void pagesStartsAndScopes() {
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIKE 'ZQ7%' LIMIT 1 FROM 'A'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES STARTS WITH 'ZQ7' LIMIT 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIKE 'ZQ7%' IN ACCOUNT");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIKE 'ZQ7%' IN ACCOUNT STARTS WITH 'ZQ7' LIMIT 2 FROM 'Z'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'.",
            "SHOW NETWORK POLICIES LIMIT 1 FROM 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'FROM'.",
            "SHOW NETWORK POLICIES FROM 'A'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIKE'.",
            "SHOW NETWORK POLICIES LIMIT 1 LIKE 'ZQ7%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'LIKE'.",
            "SHOW NETWORK POLICIES STARTS WITH 'ZQ7' LIKE 'ZQ7%'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '-'.",
            "SHOW NETWORK POLICIES LIMIT -1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "entries_in_allowed_ip_list:NUMBER(9,0), entries_in_blocked_ip_list:NUMBER(9,0), "
                + "entries_in_allowed_network_rules:NUMBER(9,0), entries_in_blocked_network_rules:NUMBER(9,0)",
            "SHOW NETWORK POLICIES LIMIT 10000");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'.",
            "SHOW NETWORK POLICIES STARTS WITH 1");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES LIKE 'ZQ7%' IN SCHEMA test_db.test_schema STARTS WITH 'ZQ7' LIMIT 1 FROM 'A'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES LIKE 'ZQ7%' IN SCHEMA test_db.test_schema");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema STARTS WITH 'ZQ7'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT 10000");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '-'.",
            "SHOW IMAGE REPOSITORIES IN SCHEMA test_db.test_schema LIMIT -1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'.",
            "SHOW IMAGE REPOSITORIES LIMIT 1 FROM 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'.",
            "SHOW IMAGE REPOSITORIES FROM 'A'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'LIKE'.",
            "SHOW IMAGE REPOSITORIES LIMIT 1 LIKE 'A'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'LIKE'.",
            "SHOW IMAGE REPOSITORIES STARTS WITH 'A' LIKE 'B'");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), repository_url:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), comment:VARCHAR(16777216), encryption:VARCHAR(16777216)",
            "SHOW IMAGE REPOSITORIES IN DATABASE test_db LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_ZQ7.",
            "SHOW IMAGE REPOSITORIES IN SCHEMA test_db.nosuch_schema_zq7 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'.",
            "SHOW IMAGE REPOSITORIES STARTS WITH 1");
    }
}
