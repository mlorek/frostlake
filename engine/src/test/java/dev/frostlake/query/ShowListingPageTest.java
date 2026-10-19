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
 * The page a SHOW's LIMIT asks for: zero is no page size and a number past 2,147,483,647 no value for the
 * clause, both judged before the listing's scope is looked up; a size past 10,000 is refused once the scope
 * is found, for every listing that reads a LIMIT. SHOW CORTEX SEARCH SERVICES reads LIKE, IN, STARTS WITH and
 * LIMIT ... FROM in that order and refuses another. Every answer is live-verified.
 */
public class ShowListingPageTest extends MeasuredCellsTestSupport {

    @Test
    public void cortexSearchServicesReadTheModifiers() {
        try {
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES STARTS WITH 'A'");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES LIMIT 1");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES LIMIT 1 FROM 'A'");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES LIKE 'a%' STARTS WITH 'A' LIMIT 1 FROM 'A'");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES IN SCHEMA test_db.test_schema STARTS WITH 'A' LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'STARTS'.",
                "SHOW CORTEX SEARCH SERVICES LIMIT 1 STARTS WITH 'A'");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW CORTEX SEARCH SERVICES LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '-'.",
                "SHOW CORTEX SEARCH SERVICES LIMIT -1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'A'.",
                "SHOW CORTEX SEARCH SERVICES STARTS WITH A");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '<EOF>'.",
                "SHOW CORTEX SEARCH SERVICES LIMIT 1 FROM");
            assertAnswer(
                "",
                "SHOW TERSE CORTEX SEARCH SERVICES");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES IN ACCOUNT");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES IN DATABASE test_db");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_P1305' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                    + "NOSUCH_DB_P1305.",
                "SHOW CORTEX SEARCH SERVICES IN DATABASE nosuch_db_p1305");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SHOW CORTEX SEARCH SERVICES IN SCHEMA test_db.nosuch_schema");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW CORTEX SEARCH SERVICES LIMIT 10001");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1.5'.",
                "SHOW CORTEX SEARCH SERVICES LIMIT 1.5");
            assertAnswer(
                "",
                "SHOW CORTEX SEARCH SERVICES STARTS WITH ''");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), target_lag:VARCHAR(16777216), warehouse:VARCHAR(16777216), "
                    + "search_column:VARCHAR(16777216), attribute_columns:VARCHAR(16777216), "
                    + "columns:VARCHAR(16777216), definition:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "embedding_model:VARCHAR(16777216), indexing_state:VARCHAR(16777216), "
                    + "serving_state:VARCHAR(16777216), source_data_num_rows:NUMBER(38,0), "
                    + "primary_key_columns:VARCHAR(16777216), scoring_profile_count:NUMBER(9,0), "
                    + "auto_suspend:NUMBER(9,0), refresh_mode:VARCHAR(16777216), vector_indexes:VARCHAR(16777216)",
                "SHOW CORTEX SEARCH SERVICES");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void thePageSizeAShowTakes() {
        try {
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW TABLES LIMIT 10001");
            assertAnswer(
                "",
                "SHOW TABLES LIMIT 10000");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW SCHEMAS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW DATABASES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW TERSE TABLES LIMIT 99999");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW GRANTS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW STAGES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW USERS LIMIT 10001");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES LIMIT 0");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW VIEWS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW SEQUENCES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW FILE FORMATS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW ROLES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW WAREHOUSES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW STREAMS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW TASKS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW PIPES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW FUNCTIONS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW PROCEDURES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW COLUMNS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW OBJECTS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW TAGS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW MASKING POLICIES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW NETWORK RULES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW SECRETS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW DYNAMIC TABLES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW MATERIALIZED VIEWS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW EVENT TABLES LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW ALERTS LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW DATABASE ROLES IN DATABASE test_db LIMIT 10001");
            assertAnswer(
                "REFUSED The LIMIT value for SHOW commands cannot exceed 10,000.",
                "SHOW TABLES LIMIT 10001 FROM 'A'");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA test_db.nosuch_schema LIMIT 10001");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [99999999999999999999] for parameter 'LIMIT_COUNT'",
                "SHOW TABLES LIMIT 99999999999999999999");
            assertAnswer(
                "",
                "SHOW TABLES LIMIT 010000");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES LIMIT 00");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIKE'.",
                "SHOW TABLES LIMIT 1 LIKE 'a%'");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
