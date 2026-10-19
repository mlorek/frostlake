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
 * SHOW DATASETS and SHOW CLASSES. The datasets' listing is empty, in seven columns and five when TERSE. The
 * classes' lists the thirteen classes every account holds in its SNOWFLAKE database, by schema and name, for
 * the schemas its scope covers: the account, a database, a schema, or with no scope the current schema. A
 * LIKE narrows it, a STARTS WITH and a LIMIT's FROM do not, and a LIMIT keeps the classes that come first in
 * the account's own order. Both refuse a LIMIT 0, a WITH PRIVILEGES as an unsupported feature, a name after
 * IN ACCOUNT and a path after IN DATABASE; a scope that does not exist is named by the classes' listing and
 * not by the datasets'. Written quoted or qualified the two words name a class, after DROP they are refused
 * as keywords, and a scope's word they are not. Every answer is live-verified.
 */
public class ShowDatasetsAndClassesTest extends MeasuredCellsTestSupport {

    @Test
    public void theTwoListings() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "",
                "SHOW DATASETS");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), comment:VARCHAR(16777216), owner:VARCHAR(16777216), "
                    + "versions:VARCHAR(16777216)",
                "SHOW DATASETS");
            assertAnswer(
                "",
                "SHOW DATASETS LIKE 'x'");
            assertAnswer(
                "",
                "SHOW TERSE DATASETS");
            assertAnswer(
                "",
                "SHOW DATASETS IN ACCOUNT");
            assertAnswer(
                "",
                "SHOW DATASETS IN DATABASE");
            assertAnswer(
                "",
                "SHOW DATASETS IN DATABASE test_db");
            assertAnswer(
                "",
                "SHOW DATASETS IN SCHEMA");
            assertAnswer(
                "",
                "SHOW DATASETS IN SCHEMA test_schema");
            assertAnswer(
                "",
                "SHOW DATASETS IN SCHEMA test_db.test_schema");
            assertAnswer(
                "",
                "SHOW DATASETS IN test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN DATABASE nosuch_db_g7");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW DATASETS LIMIT 0");
            assertAnswer(
                "",
                "SHOW DATASETS LIMIT 1");
            assertAnswer(
                "",
                "SHOW DATASETS STARTS WITH 'a'");
            assertAnswer(
                "",
                "SHOW DATASETS LIMIT 1 FROM 'a'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
                "SHOW DATASETS IN DATASET y");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW DATASETS ... WITH PRIVILEGES <>'.",
                "SHOW DATASETS WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'DATASET'.",
                "SHOW DATASET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'DATASET'.",
                "SHOW TERSE DATASET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'DATASET'.",
                "SHOW DATASET IN SCHEMA PUBLIC");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN a.b.c");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), kind:VARCHAR(16777216), "
                    + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216)",
                "SHOW TERSE DATASETS");
            assertAnswer(
                "",
                "SHOW CLASSES");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "owner:VARCHAR(16777216), is_service_class:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
                "SHOW CLASSES");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE");
            assertAnswer(
                "BUDGET|SNOWFLAKE|CORE|null|null||false||false ;; QUOTA|SNOWFLAKE|CORE|null|null||false||false ;; "
                    + "CLASSIFICATION_PROFILE|SNOWFLAKE|DATA_PRIVACY|null|null||false||false ;; "
                    + "CUSTOM_CLASSIFIER|SNOWFLAKE|DATA_PRIVACY|null|null||false||false ;; "
                    + "MARKETPLACE_ANALYTICS_NOTIFICATION|SNOWFLAKE|MARKETPLACE_NOTIFICATION|null|null||false||false "
                    + ";; ANOMALY_DETECTION|SNOWFLAKE|ML|null|null||false||false ;; "
                    + "CLASSIFICATION|SNOWFLAKE|ML|null|null||false||false ;; "
                    + "DOCUMENT_INTELLIGENCE|SNOWFLAKE|ML|null|null||false||false ;; "
                    + "FORECAST|SNOWFLAKE|ML|null|null||false||false ;; "
                    + "TOP_INSIGHTS|SNOWFLAKE|ML|null|null||false||false ;; "
                    + "ANOMALY_INSIGHTS|SNOWFLAKE|WORKLOAD_INSIGHTS|null|null||false||false ;; "
                    + "COST_INSIGHTS|SNOWFLAKE|WORKLOAD_INSIGHTS|null|null||false||false ;; "
                    + "PERFORMANCE_EXPLORER|SNOWFLAKE|WORKLOAD_OPTIMIZATION|null|null||false||false",
                "SELECT \"name\", \"database_name\", \"schema_name\", \"version\", \"comment\", \"owner\", "
                    + "\"is_service_class\", \"owner_role_type\", \"created_on\" IS NULL FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW CLASSES LIKE 'F%'");
            engine.execute(
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN ACCOUNT");
            assertAnswer(
                "BUDGET|SNOWFLAKE|CORE ;; QUOTA|SNOWFLAKE|CORE ;; CLASSIFICATION_PROFILE|SNOWFLAKE|DATA_PRIVACY "
                    + ";; CUSTOM_CLASSIFIER|SNOWFLAKE|DATA_PRIVACY ;; "
                    + "MARKETPLACE_ANALYTICS_NOTIFICATION|SNOWFLAKE|MARKETPLACE_NOTIFICATION ;; "
                    + "ANOMALY_DETECTION|SNOWFLAKE|ML ;; CLASSIFICATION|SNOWFLAKE|ML ;; "
                    + "DOCUMENT_INTELLIGENCE|SNOWFLAKE|ML ;; FORECAST|SNOWFLAKE|ML ;; TOP_INSIGHTS|SNOWFLAKE|ML ;; "
                    + "ANOMALY_INSIGHTS|SNOWFLAKE|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|SNOWFLAKE|WORKLOAD_INSIGHTS ;; "
                    + "PERFORMANCE_EXPLORER|SNOWFLAKE|WORKLOAD_OPTIMIZATION",
                "SELECT \"name\", \"database_name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW CLASSES IN DATABASE");
            assertAnswer(
                "",
                "SHOW CLASSES IN DATABASE test_db");
            assertAnswer(
                "",
                "SHOW CLASSES IN SCHEMA");
            assertAnswer(
                "",
                "SHOW CLASSES IN SCHEMA test_schema");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_S.",
                "SHOW CLASSES IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
                "SHOW CLASSES IN DATABASE nosuch_db_g7");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NOSUCH' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.NOSUCH.",
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.NOSUCH");
            assertAnswer(
                "",
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "owner:VARCHAR(16777216), is_service_class:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
                "SHOW TERSE CLASSES");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW CLASSES LIMIT 0");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 2");
            assertAnswer(
                "BUDGET|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE STARTS WITH 'A'");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|WORKLOAD_INSIGHTS ;; "
                    + "PERFORMANCE_EXPLORER|WORKLOAD_OPTIMIZATION",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 2 FROM 'C'");
            assertAnswer(
                "BUDGET|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW CLASSES IN a.b.c");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW CLASSS ... WITH PRIVILEGES <>'.",
                "SHOW CLASSES WITH PRIVILEGES USAGE");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aLimitKeepsTheClassesOfTheAccountsOwnOrder() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 1");
            assertAnswer(
                "BUDGET|CORE",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 2");
            assertAnswer(
                "BUDGET|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 3");
            assertAnswer(
                "BUDGET|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; CUSTOM_CLASSIFIER|DATA_PRIVACY",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 4");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 5");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 6");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 7");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 8");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 9");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 10");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 11");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 12");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|WORKLOAD_INSIGHTS",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 13");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|WORKLOAD_INSIGHTS ;; "
                    + "PERFORMANCE_EXPLORER|WORKLOAD_OPTIMIZATION",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN DATABASE SNOWFLAKE");
            assertAnswer(
                "BUDGET|1688009477 ;; QUOTA|1771148528 ;; CLASSIFICATION_PROFILE|1702303797 ;; "
                    + "CUSTOM_CLASSIFIER|1689026031 ;; MARKETPLACE_ANALYTICS_NOTIFICATION|1724768594 ;; "
                    + "ANOMALY_DETECTION|1681865048 ;; CLASSIFICATION|1698670975 ;; DOCUMENT_INTELLIGENCE|1684974876 "
                    + ";; FORECAST|1685016488 ;; TOP_INSIGHTS|1722955744 ;; ANOMALY_INSIGHTS|1731322206 ;; "
                    + "COST_INSIGHTS|1698671595 ;; PERFORMANCE_EXPLORER|1744388965",
                "SELECT \"name\", DATE_PART(epoch_second, \"created_on\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theScopeIsReadAsAListingsIs() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'DATASETS'. | "
                    + "syntax error line 1 at position 14 unexpected 'x'.",
                "DROP DATASETS x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'CLASSES'. | "
                    + "syntax error line 1 at position 13 unexpected 'x'.",
                "DROP CLASSES x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW FUNCTIONS IN DATASETS x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN CLASSES x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
                "SHOW TABLES IN DATASETS x y");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'DATASETS' does not exist or not authorized.",
                "SHOW \"DATASETS\"");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'CLASSES' does not exist or not authorized.",
                "SHOW \"CLASSES\"");
            assertAnswer(
                "",
                "SHOW datasets");
            assertAnswer(
                "",
                "SHOW classes");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.DATASETS' does not "
                    + "exist or not authorized.",
                "SHOW test_schema.DATASETS");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.CLASSES' does not exist "
                    + "or not authorized.",
                "SHOW test_schema.CLASSES");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.CLASSES' does not exist "
                    + "or not authorized.",
                "DROP test_schema.CLASSES x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW DATASETS IN SCHEMA nosuch_s LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW DATASETS IN ACCOUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW DATASETS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS LIKE 'x' IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TERSE DATASETS IN DATABASE nosuch_db_g7");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW DATASETS STARTS WITH 'a' LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN SCHEMA nosuch_db_g7.s");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW CLASSES IN ACCOUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW CLASSES IN DATABASE a.b");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW CLASSES IN SCHEMA nosuch_s LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_S.",
                "SHOW CLASSES LIKE 'x' IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW CLASSES IN DATABASE SNOWFLAKE LIMIT 0");
            engine.execute(
                "SHOW CLASSES LIKE '%INSIGHTS' IN ACCOUNT");
            assertAnswer(
                "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|WORKLOAD_INSIGHTS",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES LIKE 'A%' IN DATABASE SNOWFLAKE LIMIT 1");
            assertAnswer(
                "ANOMALY_DETECTION|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.ML LIMIT 2");
            assertAnswer(
                "ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.INFORMATION_SCHEMA");
            assertAnswer(
                "",
                "SHOW CLASSES IN SCHEMA SNOWFLAKE.LOCAL");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW CLASSES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
                "SHOW CLASSES IN SCHEMA nosuch_db_g7.s");
            engine.execute(
                "SHOW CLASSES LIKE 'F_RECAST' IN ACCOUNT");
            assertAnswer(
                "FORECAST|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theCurrentSchemaIsListedUnscoped() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "SNOWFLAKE|ML",
                "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
            engine.execute(
                "SHOW CLASSES");
            assertAnswer(
                "ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW DATASETS");
            engine.execute(
                "SHOW CLASSES IN DATABASE");
            assertAnswer(
                "BUDGET|CORE ;; QUOTA|CORE ;; CLASSIFICATION_PROFILE|DATA_PRIVACY ;; "
                    + "CUSTOM_CLASSIFIER|DATA_PRIVACY ;; MARKETPLACE_ANALYTICS_NOTIFICATION|MARKETPLACE_NOTIFICATION "
                    + ";; ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML ;; ANOMALY_INSIGHTS|WORKLOAD_INSIGHTS ;; COST_INSIGHTS|WORKLOAD_INSIGHTS ;; "
                    + "PERFORMANCE_EXPLORER|WORKLOAD_OPTIMIZATION",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute(
                "SHOW CLASSES IN SCHEMA");
            assertAnswer(
                "ANOMALY_DETECTION|ML ;; CLASSIFICATION|ML ;; DOCUMENT_INTELLIGENCE|ML ;; FORECAST|ML ;; "
                    + "TOP_INSIGHTS|ML",
                "SELECT \"name\", \"schema_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "",
                "SHOW TABLES");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE test_db");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA test_schema");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
