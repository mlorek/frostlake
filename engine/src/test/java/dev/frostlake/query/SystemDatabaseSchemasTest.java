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
 * The schemas of the SNOWFLAKE database: the ones an account's holds are there, so a listing over one is
 * empty where a missing schema would be refused, a missing object in one is named where the schema would be,
 * and a USE reaches all but two. INFORMATION_SCHEMA.SCHEMATA lists a part of them, none with an owner. Every
 * answer is live-verified.
 */
public class SystemDatabaseSchemasTest extends MeasuredCellsTestSupport {

    @Test
    public void aListingOverASystemSchemaIsEmpty() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            engine.execute(
                "SHOW SCHEMAS LIKE 'ML' IN DATABASE SNOWFLAKE");
            assertAnswer(
                "ML|SNOWFLAKE",
                "SELECT \"name\", \"database_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'ACCOUNT_USAGE'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.ALERT LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ALERT");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'ALERT'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.ALERT");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.BCR_ROLLOUT LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.BCR_ROLLOUT");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'BCR_ROLLOUT'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.BCR_ROLLOUT");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.BILLING LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.BILLING");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'BILLING'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.BILLING");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.CORE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.CORE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CORE'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.CORE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.CORTEX LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.CORTEX");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CORTEX'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.CORTEX");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.DATA_PRIVACY LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.DATA_PRIVACY");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'DATA_PRIVACY'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.DATA_PRIVACY");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.DATA_SECURITY LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.DATA_SECURITY");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'DATA_SECURITY'. Your primary role <ROLE> must have MONITOR granted on SCHEMA "
                    + "SNOWFLAKE.DATA_SECURITY.",
                "DESCRIBE SCHEMA SNOWFLAKE.DATA_SECURITY");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.DATA_SHARING_USAGE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.DATA_SHARING_USAGE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'DATA_SHARING_USAGE'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.DATA_SHARING_USAGE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.DEFAULT_IMAGE_STORE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.DEFAULT_IMAGE_STORE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'DEFAULT_IMAGE_STORE'. Your primary role <ROLE> must have MONITOR granted on SCHEMA "
                    + "SNOWFLAKE.DEFAULT_IMAGE_STORE.",
                "DESCRIBE SCHEMA SNOWFLAKE.DEFAULT_IMAGE_STORE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.IMAGES LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.IMAGES");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'IMAGES'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.IMAGES");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.INFORMATION_SCHEMA LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.INFORMATION_SCHEMA");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.LOCAL");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'LOCAL'. Your "
                    + "primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.LOCAL.",
                "DESCRIBE SCHEMA SNOWFLAKE.LOCAL");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.MARKETPLACE_NOTIFICATION LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.MARKETPLACE_NOTIFICATION");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'MARKETPLACE_NOTIFICATION'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.MARKETPLACE_NOTIFICATION");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.ML LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'ML'. Provider "
                    + "share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.MODELS LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.MODELS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'MODELS'. Your "
                    + "primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.MODELS.",
                "DESCRIBE SCHEMA SNOWFLAKE.MODELS");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.MONITORING LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.MONITORING");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'MONITORING'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.MONITORING");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.NETWORK_SECURITY LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.NETWORK_SECURITY");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'NETWORK_SECURITY'. Your primary role <ROLE> must have MONITOR granted on SCHEMA "
                    + "SNOWFLAKE.NETWORK_SECURITY.",
                "DESCRIBE SCHEMA SNOWFLAKE.NETWORK_SECURITY");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.NOTIFICATION LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.NOTIFICATION");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'NOTIFICATION'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.NOTIFICATION");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.ORGANIZATION_USAGE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ORGANIZATION_USAGE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'ORGANIZATION_USAGE'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.ORGANIZATION_USAGE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.ORG_USAGE_LOCAL LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SCHEMA SNOWFLAKE.ORG_USAGE_LOCAL");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ORG_USAGE_LOCAL' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.ORG_USAGE_LOCAL.",
                "DESCRIBE SCHEMA SNOWFLAKE.ORG_USAGE_LOCAL");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.POSTGRES LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.POSTGRES");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'POSTGRES'. "
                    + "Your primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.POSTGRES.",
                "DESCRIBE SCHEMA SNOWFLAKE.POSTGRES");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'READER_ACCOUNT_USAGE'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.READER_ACCOUNT_USAGE");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.SNOWPARK LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.SNOWPARK");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'SNOWPARK'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.SNOWPARK");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.SNOWPARK_CONNECT LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.SNOWPARK_CONNECT");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'SNOWPARK_CONNECT'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.SNOWPARK_CONNECT");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.SPCS LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.SPCS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'SPCS'. "
                    + "Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.SPCS");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.TAGS LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.TAGS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TAGS'. Your "
                    + "primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.TAGS.",
                "DESCRIBE SCHEMA SNOWFLAKE.TAGS");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.TELEMETRY");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TELEMETRY'. "
                    + "Your primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.TELEMETRY.",
                "DESCRIBE SCHEMA SNOWFLAKE.TELEMETRY");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.TRUST_CENTER LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.TRUST_CENTER");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TRUST_CENTER'. "
                    + "Your primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.TRUST_CENTER.",
                "DESCRIBE SCHEMA SNOWFLAKE.TRUST_CENTER");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.TRUST_CENTER_INTERNAL LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SCHEMA SNOWFLAKE.TRUST_CENTER_INTERNAL");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.TRUST_CENTER_INTERNAL' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.TRUST_CENTER_INTERNAL.",
                "DESCRIBE SCHEMA SNOWFLAKE.TRUST_CENTER_INTERNAL");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.WORKLOAD_INSIGHTS LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.WORKLOAD_INSIGHTS");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'WORKLOAD_INSIGHTS'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.WORKLOAD_INSIGHTS");
            assertAnswer(
                "",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.WORKLOAD_OPTIMIZATION LIMIT 1");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.WORKLOAD_OPTIMIZATION");
            assertAnswer(
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                    + "'WORKLOAD_OPTIMIZATION'. Provider share does not have sufficient privileges.",
                "DESCRIBE SCHEMA SNOWFLAKE.WORKLOAD_OPTIMIZATION");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA SNOWFLAKE.NOSUCH_S LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SCHEMA SNOWFLAKE.NOSUCH_S");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.NOSUCH_S.",
                "DESCRIBE SCHEMA SNOWFLAKE.NOSUCH_S");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE SNOWFLAKE");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE test_db");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA test_schema");
            assertAnswer(
                "",
                "SHOW VIEWS IN SCHEMA SNOWFLAKE.ML LIMIT 1");
            assertAnswer(
                "",
                "SHOW SEQUENCES IN SNOWFLAKE.ML");
            assertAnswer(
                "",
                "SHOW STAGES IN SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SNOWFLAKE.ML.NOSUCH_T' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "SNOWFLAKE.ML.NOSUCH_T.",
                "SELECT COUNT(*) FROM SNOWFLAKE.ML.nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.NOSUCH_S.",
                "SELECT COUNT(*) FROM SNOWFLAKE.NOSUCH_S.nosuch_t");
            assertAnswer(
                "",
                "SHOW OBJECTS IN SCHEMA SNOWFLAKE.ML LIMIT 1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void namesResolveThroughASystemSchema() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            engine.execute(
                "SHOW SCHEMAS IN DATABASE SNOWFLAKE");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 122 | invalid identifier '\"budget\"'",
                "SELECT \"name\", DATE_PART(epoch_second, \"created_on\"), \"retention_time\", \"owner\", "
                    + "\"owner_role_type\", \"comment\", \"options\", \"budget\" FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW TERSE SCHEMAS IN DATABASE SNOWFLAKE LIMIT 3");
            assertAnswer(
                "ACCESS_INTELLIGENCE ;; ACCESS_INTELLIGENCE_INTERNAL ;; ACCOUNT_USAGE",
                "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "SNOWFLAKE|ML",
                "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
            assertAnswer(
                "",
                "SHOW FUNCTIONS LIKE 'NOSUCH%'");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE SNOWFLAKE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SCHEMA ORG_USAGE_LOCAL");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA ML");
            assertAnswer(
                "SNOWFLAKE|ML",
                "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
            assertAnswer(
                "Statement executed successfully.",
                "USE SNOWFLAKE.ML");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "USE SNOWFLAKE.ORG_USAGE_LOCAL");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE test_db");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'SNOWFLAKE.ML.NOSUCH_T' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "SNOWFLAKE.ML.NOSUCH_T.",
                "SELECT * FROM SNOWFLAKE.ML.nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.ML.NOSUCH_F.",
                "SELECT SNOWFLAKE.ML.nosuch_f(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.NOSUCH_S.NOSUCH_F.",
                "SELECT SNOWFLAKE.NOSUCH_S.nosuch_f(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.ML.NOSUCH_P.",
                "CALL SNOWFLAKE.ML.nosuch_p()");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'SNOWFLAKE.ML.NOSUCH_T' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "SNOWFLAKE.ML.NOSUCH_T.",
                "DESCRIBE TABLE SNOWFLAKE.ML.nosuch_t");
            assertAnswer(
                "",
                "SHOW STREAMS IN SCHEMA SNOWFLAKE.CORE");
            assertAnswer(
                "",
                "SHOW SEQUENCES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
            assertAnswer(
                "",
                "SHOW TASKS IN SCHEMA SNOWFLAKE.ALERT");
            assertAnswer(
                "",
                "SHOW PIPES IN SCHEMA SNOWFLAKE.BILLING");
            assertAnswer(
                "",
                "SHOW STAGES IN SCHEMA SNOWFLAKE.CORTEX");
            assertAnswer(
                "",
                "SHOW FILE FORMATS IN SCHEMA SNOWFLAKE.DATA_PRIVACY");
            assertAnswer(
                "",
                "SHOW DYNAMIC TABLES IN SCHEMA SNOWFLAKE.MONITORING");
            assertAnswer(
                "",
                "SHOW ALERTS IN SCHEMA SNOWFLAKE.NOTIFICATION");
            assertAnswer(
                "",
                "SHOW SECRETS IN SCHEMA SNOWFLAKE.SPCS");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
