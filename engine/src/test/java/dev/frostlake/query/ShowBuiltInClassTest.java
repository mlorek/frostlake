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
 * SHOW of a class's name lists the class's instances: for the thirteen classes every account holds in its
 * SNOWFLAKE database an empty listing in eight columns, for any other name the refusal of a class that does
 * not exist. A WITH PRIVILEGES is a syntax error at PRIVILEGES, and a LIMIT 0 is refused before the class is
 * looked up; a missing class is refused before its scope is read; and a built-in class's scope is read as a
 * listing's: a name after IN ACCOUNT and a path after IN DATABASE ask for a full search path, a schema's name
 * of three parts names nothing, a schema or a database that does not exist is named. A class resolves as any
 * name does, in the current schema and database. Every answer is live-verified.
 */
public class ShowBuiltInClassTest extends MeasuredCellsTestSupport {

    @Test
    public void aBuiltInClassListsItsInstances() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.ANOMALY_DETECTION");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.CLASSIFICATION");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.TOP_INSIGHTS");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.DOCUMENT_INTELLIGENCE");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.CORE.BUDGET");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.CORE.QUOTA");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.DATA_PRIVACY.CUSTOM_CLASSIFIER");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.DATA_PRIVACY.CLASSIFICATION_PROFILE");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.MARKETPLACE_NOTIFICATION.MARKETPLACE_ANALYTICS_NOTIFICATION");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.WORKLOAD_INSIGHTS.ANOMALY_INSIGHTS");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.WORKLOAD_INSIGHTS.COST_INSIGHTS");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.WORKLOAD_OPTIMIZATION.PERFORMANCE_EXPLORER");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), current_version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "owner:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
                "SHOW SNOWFLAKE.ML.FORECAST");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), current_version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "owner:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
                "SHOW TERSE SNOWFLAKE.ML.FORECAST");
            assertShape(
                "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                    + "schema_name:VARCHAR(16777216), current_version:VARCHAR(16777216), comment:VARCHAR(16777216), "
                    + "owner:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
                "SHOW SNOWFLAKE.CORE.BUDGET");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SNOWFLAKE.ML.NOSUCH' does not exist or not "
                    + "authorized.",
                "SHOW SNOWFLAKE.ML.NOSUCH");
            assertAnswer(
                "",
                "SHOW TERSE SNOWFLAKE.ML.FORECAST");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST LIKE 'x'");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN ACCOUNT");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN DATABASE");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH.",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA nosuch");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW SNOWFLAKE.ML.FORECAST LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH.",
                "SHOW SNOWFLAKE.ML.FORECAST IN DATABASE nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH.",
                "SHOW SNOWFLAKE.ML.FORECAST IN nosuch");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN test_schema");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA test_schema");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA test_db.test_schema");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN DATABASE test_db");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA nosuch LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW SNOWFLAKE.ML.FORECAST IN ACCOUNT x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW SNOWFLAKE.ML.FORECAST IN DATABASE nosuch LIMIT 0");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST STARTS WITH 'a' LIMIT 5");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST LIMIT 5 FROM 'a'");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'PRIVILEGES'.",
                "SHOW SNOWFLAKE.ML.FORECAST WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SNOWFLAKE.ML.FORECAST IN a.b.c");
            assertAnswer(
                "",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCHDB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCHDB.",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA nosuchdb.x");
            assertAnswer(
                "",
                "SHOW snowflake.ml.forecast");
            assertAnswer(
                "",
                "SHOW IDENTIFIER('SNOWFLAKE.ML.FORECAST')");
            assertAnswer(
                "",
                "SHOW \"SNOWFLAKE\".\"ML\".\"FORECAST\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Database '\"snowflake\"' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"snowflake\".",
                "SHOW \"snowflake\".\"ml\".\"forecast\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.ML' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.ML.",
                "SHOW ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FORECAST' does not exist or not authorized.",
                "SHOW FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.PUBLIC' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.PUBLIC.",
                "SHOW SNOWFLAKE..FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.NOSUCH' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.NOSUCH.",
                "SHOW SNOWFLAKE.NOSUCH.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
                "SHOW NOSUCH_DB_G7.ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW SNOWFLAKE.ML.FORECAST x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'SNOWFLAKE.CORE.AVG' does not exist or not "
                    + "authorized.",
                "SHOW SNOWFLAKE.CORE.AVG");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'SNOWFLAKE.PUBLIC' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "SNOWFLAKE.PUBLIC.",
                "SHOW SNOWFLAKE.PUBLIC.FORECAST");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aClassResolvesInTheCurrentSchema() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE SNOWFLAKE");
            assertAnswer(
                "",
                "SHOW ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FORECAST' does not exist or not authorized.",
                "SHOW FORECAST");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA SNOWFLAKE.ML");
            assertAnswer(
                "",
                "SHOW ANOMALY_DETECTION");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'BUDGET' does not exist or not authorized.",
                "SHOW BUDGET");
            assertAnswer(
                "",
                "SHOW CORE.BUDGET");
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

    @Test
    public void modifiersAndScopes() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'PRIVILEGES'.",
                "SHOW nosuch_cls WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW nosuch_cls LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls IN DATABASE nosuch_db_g7");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls IN ACCOUNT x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW nosuch_cls IN SCHEMA nosuch_s LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.NOSUCH_CLS' does not "
                    + "exist or not authorized.",
                "SHOW test_schema.nosuch_cls IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls LIKE 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_cls STARTS WITH 'a' LIMIT 1 FROM 'b'");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS' does not exist or not "
                    + "authorized.",
                "SHOW TERSE nosuch_cls");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW SNOWFLAKE.ML.FORECAST IN ACCOUNT LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH.",
                "SHOW SNOWFLAKE.ML.FORECAST LIKE 'x' IN SCHEMA nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW SNOWFLAKE.ML.FORECAST IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH.",
                "SHOW TERSE SNOWFLAKE.ML.FORECAST IN SCHEMA nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
                "SHOW SNOWFLAKE.ML.FORECAST IN SCHEMA nosuch_db_g7.s");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
