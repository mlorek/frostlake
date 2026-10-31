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
 * The SNOWFLAKE database as a share: three of its schemas are described as missing though listed, its schemas
 * and itself list no parameter, and its database roles are listed whole, a page or a prefix refused (all
 * live-verified).
 */
public class SystemDatabaseShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void systemSchemas() {
        assertAnswer("REFUSED Unsupported feature 'SHOW DATABASE ROLES IN DATABASE <shared database> [STARTS WITH "
                + "<s>] [LIMIT <l>]'; use 'SHOW DATABASE ROLES IN DATABASE <shared database>' instead.",
            "SHOW DATABASE ROLES IN DATABASE SNOWFLAKE STARTS WITH 'U'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                + "'ACCOUNT_USAGE'. Provider share does not have sufficient privileges.",
            "DESCRIBE SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'CORE'. "
                + "Provider share does not have sufficient privileges.",
            "DESCRIBE SCHEMA SNOWFLAKE.CORE");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TAGS'. Your "
                + "primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.TAGS.",
            "DESCRIBE SCHEMA SNOWFLAKE.TAGS");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'LOCAL'. Your "
                + "primary role <ROLE> must have MONITOR granted on SCHEMA SNOWFLAKE.LOCAL.",
            "DESCRIBE SCHEMA SNOWFLAKE.LOCAL");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema "
                + "'ORGANIZATION_USAGE'. Provider share does not have sufficient privileges.",
            "DESCRIBE SCHEMA SNOWFLAKE.ORGANIZATION_USAGE");
        assertAnswer("", "SHOW PARAMETERS LIKE 'DATA_RETENTION%' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertAnswer("", "SHOW PARAMETERS LIKE 'DATA_RETENTION%' IN SCHEMA SNOWFLAKE.TAGS");
        assertAnswer("", "SHOW PARAMETERS LIKE 'DATA_RETENTION%' IN DATABASE SNOWFLAKE");
    }

    @Test
    public void hiddenSchemasAndRolePages() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ACCESS_INTELLIGENCE_INTERNAL' does not exist "
                + "or not authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "SCHEMA SNOWFLAKE.ACCESS_INTELLIGENCE_INTERNAL.",
            "DESCRIBE SCHEMA SNOWFLAKE.ACCESS_INTELLIGENCE_INTERNAL");
        assertAnswer("REFUSED SQL compilation error: | Schema 'SNOWFLAKE.ORG_USAGE_LOCAL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "SNOWFLAKE.ORG_USAGE_LOCAL.",
            "DESCRIBE SCHEMA SNOWFLAKE.ORG_USAGE_LOCAL");
        assertAnswer("REFUSED SQL compilation error: | Schema 'SNOWFLAKE.TRUST_CENTER_INTERNAL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "SNOWFLAKE.TRUST_CENTER_INTERNAL.",
            "DESCRIBE SCHEMA SNOWFLAKE.TRUST_CENTER_INTERNAL");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'SPCS'. "
                + "Provider share does not have sufficient privileges.",
            "DESCRIBE SCHEMA SNOWFLAKE.SPCS");
        assertAnswer("REFUSED Unsupported feature 'SHOW DATABASE ROLES IN DATABASE <shared database> [STARTS WITH "
                + "<s>] [LIMIT <l>]'; use 'SHOW DATABASE ROLES IN DATABASE <shared database>' instead.",
            "SHOW DATABASE ROLES IN DATABASE SNOWFLAKE LIMIT 2 FROM 'A'");
        assertAnswer("REFUSED Unsupported feature 'SHOW DATABASE ROLES IN DATABASE <shared database> [STARTS WITH "
                + "<s>] [LIMIT <l>]'; use 'SHOW DATABASE ROLES IN DATABASE <shared database>' instead.",
            "SHOW DATABASE ROLES LIKE 'X%' IN DATABASE SNOWFLAKE LIMIT 1");
        assertAnswer("REFUSED Unsupported feature 'SHOW DATABASE ROLES IN DATABASE <shared database> [STARTS WITH "
                + "<s>] [LIMIT <l>]'; use 'SHOW DATABASE ROLES IN DATABASE <shared database>' instead.",
            "SHOW DATABASE ROLES IN DATABASE SNOWFLAKE STARTS WITH 'U' LIMIT 1");
    }

    @Test
    public void theSystemDatabasesRoles() {
        answer("SHOW DATABASE ROLES IN DATABASE SNOWFLAKE");
        assertAnswer(
            "AI_FUNCTIONS_USER|N|N|Y|0|0||| ;; ALERT_VIEWER|N|N|Y|0|0||| ;;"
            + " ANACONDA_REPOSITORY_USER|N|N|Y|0|0||| ;; BUDGET_CREATOR|N|N|Y|0|0||| ;;"
            + " CLASSIFICATION_ADMIN|N|N|Y|0|0||| ;; CLASSIFICATION_VIEWER|N|N|Y|0|0||| ;;"
            + " COPILOT_USER|N|N|Y|0|0||| ;; CORE_VIEWER|N|N|Y|0|0||| ;; CORTEX_AGENT_USER|N|N|Y|0|0||| ;;"
            + " CORTEX_ANALYST_USER|N|N|Y|0|0||| ;; CORTEX_EMBED_USER|N|N|Y|0|0||| ;;"
            + " CORTEX_REST_API_USER|N|N|Y|0|0||| ;; CORTEX_SENSE_BUILDER|N|N|Y|0|0||| ;;"
            + " CORTEX_SENSE_USER|N|N|Y|0|0||| ;; CORTEX_USER|N|N|Y|0|0||| ;; DATA_METRIC_USER|N|N|Y|0|0||| ;;"
            + " DATA_PRIVACY_VIEWER|N|N|Y|0|0||| ;; DOCUMENT_INTELLIGENCE_CREATOR|N|N|Y|0|0||| ;;"
            + " GOVERNANCE_ADMIN|N|N|Y|0|0||| ;; GOVERNANCE_VIEWER|N|N|Y|0|0||| ;; ML_USER|N|N|Y|0|0||| ;;"
            + " MONITORING_VIEWER|N|N|Y|0|0||| ;; NOTIFICATION_VIEWER|N|N|Y|0|0||| ;; OBJECT_VIEWER|N|N|Y|0|0|||"
            + " ;; ORGANIZATION_ACCOUNTS_VIEWER|N|N|Y|0|0||| ;; ORGANIZATION_BILLING_VIEWER|N|N|Y|0|0||| ;;"
            + " ORGANIZATION_GOVERNANCE_VIEWER|N|N|Y|0|0||| ;; ORGANIZATION_USAGE_VIEWER|N|N|Y|0|0||| ;;"
            + " ORG_BUDGET_CREATOR|N|N|Y|0|0||| ;; PYPI_REPOSITORY_USER|N|N|Y|0|0||| ;; QUOTA_CREATOR|N|N|Y|0|0|||"
            + " ;; READER_USAGE_VIEWER|N|N|Y|0|0||| ;; SECURITY_VIEWER|N|N|Y|0|0||| ;;"
            + " SHARING_USAGE_VIEWER|N|N|Y|0|0||| ;; SNOWFLAKE_MARKETPLACE_INVOICE_VIEWER|N|N|Y|0|0||| ;;"
            + " SNOWPARK_CONNECT_VIEWER|N|N|Y|0|0||| ;; SPARK_USER|N|N|Y|0|0||| ;;"
            + " SPCS_REGISTRY_VIEWER|N|N|Y|0|0||| ;; SPCS_VIEWER|N|N|Y|0|0||| ;; USAGE_ADMIN|N|N|Y|0|0||| ;;"
            + " USAGE_VIEWER|N|N|Y|0|0||| ;; WORKLOAD_INSIGHTS_USER|N|N|Y|0|0||| ;;"
            + " WORKLOAD_OPTIMIZATION_USER|N|N|Y|0|0|||",
            "SELECT \"name\", \"is_default\", \"is_current\", \"is_inherited\", \"granted_to_database_roles\","
            + " \"granted_database_roles\", \"owner\", \"comment\", \"owner_role_type\" FROM"
            + " TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
