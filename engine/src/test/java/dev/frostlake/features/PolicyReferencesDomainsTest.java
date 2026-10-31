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
 * INFORMATION_SCHEMA.POLICY_REFERENCES over the REF_ENTITY_DOMAIN values past a table, a view and a column
 * (live-verified): a domain that takes no policy is an invalid value, spelled with underscores; an INTEGRATION
 * or an EXTERNAL TABLE names an object that must exist; an APPLICATION is never found; HYBRID TABLE is an
 * unknown domain; and an existing tag, the current user and the account, its locator in any case, have no rows.
 */
public class PolicyReferencesDomainsTest extends MeasuredCellsTestSupport {

    @Test
    public void domainsPastTheMeasuredOnes() {
        engine.execute("CREATE OR REPLACE TABLE pr_t (a INT)");
        engine.execute("CREATE OR REPLACE VIEW pr_v AS SELECT a FROM pr_t");
        engine.execute("CREATE OR REPLACE STREAM pr_s ON TABLE pr_t");
        engine.execute("CREATE OR REPLACE TAG pr_tg");
        engine.execute("CREATE OR REPLACE SEQUENCE pr_q");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_wh', "
                + "REF_ENTITY_DOMAIN => 'WAREHOUSE'))",
                "REFUSED SQL compilation error: Invalid value 'WAREHOUSE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_r', "
                + "REF_ENTITY_DOMAIN => 'ROLE'))",
                "REFUSED SQL compilation error: Invalid value 'ROLE' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_i', "
                + "REF_ENTITY_DOMAIN => 'INTEGRATION'))",
                "REFUSED SQL compilation error: | Integration 'NOSUCH_I' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_I."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_s', "
                + "REF_ENTITY_DOMAIN => 'STREAM'))",
                "REFUSED SQL compilation error: Invalid value 'STREAM' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_s', "
                + "REF_ENTITY_DOMAIN => 'STREAM'))",
                "REFUSED SQL compilation error: Invalid value 'STREAM' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_k', "
                + "REF_ENTITY_DOMAIN => 'TASK'))",
                "REFUSED SQL compilation error: Invalid value 'TASK' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_f', "
                + "REF_ENTITY_DOMAIN => 'FUNCTION'))",
                "REFUSED SQL compilation error: Invalid value 'FUNCTION' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_p', "
                + "REF_ENTITY_DOMAIN => 'PROCEDURE'))",
                "REFUSED SQL compilation error: Invalid value 'PROCEDURE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_al', "
                + "REF_ENTITY_DOMAIN => 'ALERT'))",
                "REFUSED SQL compilation error: Invalid value 'ALERT' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_pp', "
                + "REF_ENTITY_DOMAIN => 'PIPE'))",
                "REFUSED SQL compilation error: Invalid value 'PIPE' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_q', "
                + "REF_ENTITY_DOMAIN => 'SEQUENCE'))",
                "REFUSED SQL compilation error: Invalid value 'SEQUENCE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_v', "
                + "REF_ENTITY_DOMAIN => 'VIEW'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_v', "
                + "REF_ENTITY_DOMAIN => 'VIEW'))",
                "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_V."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_et', "
                + "REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))",
                "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_ET' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ET."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_t', "
                + "REF_ENTITY_DOMAIN => 'ICEBERG TABLE'))",
                "REFUSED SQL compilation error: Invalid value 'ICEBERG_TABLE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_t', "
                + "REF_ENTITY_DOMAIN => 'EVENT TABLE'))",
                "REFUSED SQL compilation error: Invalid value 'EVENT_TABLE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_nr', "
                + "REF_ENTITY_DOMAIN => 'NETWORK RULE'))",
                "REFUSED SQL compilation error: Invalid value 'NETWORK_RULE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_sc', "
                + "REF_ENTITY_DOMAIN => 'SECRET'))",
                "REFUSED SQL compilation error: Invalid value 'SECRET' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_tg', "
                + "REF_ENTITY_DOMAIN => 'TAG'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_t', "
                + "REF_ENTITY_DOMAIN => 'TABLE'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_t', "
                + "REF_ENTITY_DOMAIN => 'table'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pr_t', "
                + "REF_ENTITY_DOMAIN => 'Table'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => CURRENT_USER(), "
                + "REF_ENTITY_DOMAIN => 'USER'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => CURRENT_ACCOUNT(), "
                + "REF_ENTITY_DOMAIN => 'ACCOUNT'))",
                "0"},
        });
    }

    @Test
    public void moreDomainsAndTheAccount() {
        engine.execute("CREATE OR REPLACE TABLE p2_t (a INT)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'DATABASE ROLE'))",
                "REFUSED SQL compilation error: Invalid value 'DATABASE_ROLE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'COMPUTE POOL'))",
                "REFUSED SQL compilation error: Invalid value 'COMPUTE_POOL' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'SERVICE'))",
                "REFUSED SQL compilation error: Invalid value 'SERVICE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'NOTEBOOK'))",
                "REFUSED SQL compilation error: Invalid value 'NOTEBOOK' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'MODEL'))",
                "REFUSED SQL compilation error: Invalid value 'MODEL' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'DATASET'))",
                "REFUSED SQL compilation error: Invalid value 'DATASET' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'STREAMLIT'))",
                "REFUSED SQL compilation error: Invalid value 'STREAMLIT' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'APPLICATION'))",
                "REFUSED SQL compilation error: | Application 'NOSUCH_X' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'FILE FORMAT'))",
                "REFUSED SQL compilation error: Invalid value 'FILE_FORMAT' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'SHARE'))",
                "REFUSED SQL compilation error: Invalid value 'SHARE' for argument 'ref_entity_domain' for function "
                + "'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'NETWORK POLICY'))",
                "REFUSED SQL compilation error: Invalid value 'NETWORK_POLICY' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'AUTHENTICATION POLICY'))",
                "REFUSED SQL compilation error: Invalid value 'AUTHENTICATION_POLICY' for argument "
                + "'ref_entity_domain' for function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'PACKAGES POLICY'))",
                "REFUSED SQL compilation error: Invalid value 'PACKAGES_POLICY' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'ORGANIZATION'))",
                "REFUSED SQL compilation error: Invalid value 'ORGANIZATION' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'INTEGRATION'))",
                "REFUSED SQL compilation error: | Integration 'NOSUCH_X' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_X."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'p2_t', "
                + "REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'HYBRID TABLE'))",
                "REFUSED Unknown domain: HYBRID TABLE."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'DYNAMIC_TABLE'))",
                "REFUSED SQL compilation error: Invalid value 'DYNAMIC_TABLE' for argument 'ref_entity_domain' for "
                + "function 'information_schema.policy_references'"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'EXTERNAL_TABLE'))",
                "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'USER'))",
                "REFUSED SQL compilation error: | User 'NOSUCH_X' does not exist or not authorized."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'nosuch_x', "
                + "REF_ENTITY_DOMAIN => 'ACCOUNT'))",
                "REFUSED SQL compilation error: | Account 'NOSUCH_X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => "
                + "LOWER(CURRENT_ACCOUNT()), REF_ENTITY_DOMAIN => 'ACCOUNT'))",
                "0"},
        });
    }
}
