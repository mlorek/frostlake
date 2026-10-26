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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Every database role of the SNOWFLAKE database is granted to the SNOWFLAKE application, listed by SHOW GRANTS
 * OF DATABASE ROLE with the PUBLIC grant some of them carry (all live-verified).
 */
public class SnowflakeDatabaseRoleGranteesTest extends MeasuredCellsTestSupport {

    @Test
    public void applicationGrantees() {
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.AI_FUNCTIONS_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ALERT_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ANACONDA_REPOSITORY_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.BUDGET_CREATOR");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CLASSIFICATION_ADMIN");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CLASSIFICATION_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.COPILOT_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_AGENT_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_ANALYST_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_EMBED_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_REST_API_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_SENSE_BUILDER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_SENSE_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORTEX_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.DATA_METRIC_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.DATA_PRIVACY_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.DOCUMENT_INTELLIGENCE_CREATOR");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.GOVERNANCE_ADMIN");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.GOVERNANCE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ML_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.MONITORING_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.NOTIFICATION_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.OBJECT_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ORGANIZATION_ACCOUNTS_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ORGANIZATION_BILLING_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ORGANIZATION_GOVERNANCE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ORGANIZATION_USAGE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.ORG_BUDGET_CREATOR");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.PYPI_REPOSITORY_USER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.QUOTA_CREATOR");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.READER_USAGE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SECURITY_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SHARING_USAGE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SNOWFLAKE_MARKETPLACE_INVOICE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SNOWPARK_CONNECT_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SPARK_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SPCS_REGISTRY_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.SPCS_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE,ROLE:PUBLIC",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.USAGE_ADMIN");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.USAGE_VIEWER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.WORKLOAD_INSIGHTS_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
        engine.execute("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.WORKLOAD_OPTIMIZATION_USER");
        assertAnswer("APPLICATION:SNOWFLAKE",
            "SELECT LISTAGG(\"granted_to\" || ':' || \"grantee_name\", ',') WITHIN GROUP (ORDER BY \"granted_to\") "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" <> 'ROLE' OR \"grantee_name\" = "
                + "'PUBLIC'");
    }
}
