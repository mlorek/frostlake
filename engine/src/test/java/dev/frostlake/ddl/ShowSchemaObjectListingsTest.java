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
 * The listings of a schema's things that no object is modelled of - SNAPSHOTS, GIT REPOSITORIES, SEMANTIC VIEWS,
 * the SESSION, AUTHENTICATION, PACKAGES and PRIVACY POLICIES, EXTERNAL and DATA METRIC FUNCTIONS, INDEXES and
 * CHANNELS - answer the account's columns over the scopes a schema-level listing takes. A schema or a database
 * that does not exist is named, where INDEXES answers that no object exists; a table is a scope of INDEXES and
 * CHANNELS alone, CHANNELS reading a bare name after IN as a table's and asking for a schema's full path, as the
 * key listings do, and refusing a view by a sentence that names none. The two function listings answer an
 * application, a class and a service as FUNCTIONS does. HISTORY is no word of CHANNELS, SESSION POLICIES and
 * AUTHENTICATION POLICIES; CHANNELS reads no STARTS WITH, which stacks a second line straight after the
 * listing's own words only, and the three read no WITH PRIVILEGES, which the others read and do not support,
 * each under the name the account gives it. TERSE narrows INDEXES and SEMANTIC VIEWS to five columns. Every
 * statement reads only; every answer is live-verified.
 */
public class ShowSchemaObjectListingsTest extends MeasuredCellsTestSupport {

    private static final String CHANNELS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), table_name:VARCHAR(16777216), client_sequencer:NUMBER(38,0), "
            + "row_sequencer:NUMBER(38,0), offset_token:VARCHAR(16777216), parent_name:VARCHAR(16777216), "
            + "parent_domain:VARCHAR(16777216)";

    private static final String EXTERNAL_FUNCTIONS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
            + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
            + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
            + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), is_table_function:VARCHAR(16777216), "
            + "valid_for_clustering:VARCHAR(16777216), is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
            + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
            + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
            + "is_ai_function:VARCHAR(16777216)";

    private static final String GIT_REPOSITORIES_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), origin:VARCHAR(16777216), api_integration:VARCHAR(16777216), "
            + "git_credentials:VARCHAR(16777216), owner:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), "
            + "comment:VARCHAR(16777216), last_fetched_at:TIMESTAMP_LTZ, use_private_link:VARCHAR(16777216), "
            + "repository_size:VARCHAR(16777216)";

    private static final String INDEXES_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), is_unique:VARCHAR(16777216), table:VARCHAR(16777216), "
            + "columns:VARCHAR(16777216), included_columns:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), owner:VARCHAR(16777216), status:VARCHAR(16777216), "
            + "status_info:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)";

    private static final String SEMANTIC_VIEWS_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), comment:VARCHAR(16777216), owner:VARCHAR(16777216), "
            + "owner_role_type:VARCHAR(16777216), extension:VARCHAR(16777216), max_staleness:VARCHAR(16777216)";

    private static final String SESSION_POLICIES_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
            + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)";

    private static final String SNAPSHOTS_COLUMNS =
        "name:VARCHAR(16777216), state:VARCHAR(16777216), database_name:VARCHAR(16777216), "
            + "schema_name:VARCHAR(16777216), service_name:VARCHAR(16777216), volume_name:VARCHAR(16777216), "
            + "instance:VARCHAR(16777216), size:NUMBER(38,0), comment:VARCHAR(16777216), owner:VARCHAR(16777216), "
            + "owner_role_type:VARCHAR(16777216), created_on:TIMESTAMP_LTZ, updated_on:TIMESTAMP_LTZ, "
            + "encryption:VARCHAR(16777216)";

    private static final String TERSE_COLUMNS =
        "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), kind:VARCHAR(16777216), "
            + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216)";

    @Test
    public void indexes() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES",
            "SHOW INDEXES LIKE 'zz%'",
            "SHOW INDEXES IN ACCOUNT",
            "SHOW INDEXES IN DATABASE",
            "SHOW INDEXES IN SCHEMA",
            "SHOW INDEXES IN DATABASE test_db",
            "SHOW INDEXES IN SCHEMA test_db.test_schema",
            "SHOW INDEXES IN test_db.test_schema",
            "SHOW INDEXES IN TABLE ab",
            "SHOW INDEXES STARTS WITH 'zz'",
            "SHOW INDEXES LIMIT 1",
            "SHOW INDEXES LIMIT 1 FROM 'zz'");
        assertShapes(TERSE_COLUMNS,
            "SHOW TERSE INDEXES");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES HISTORY",
            "SHOW INDEXES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'nosuchword'.",
            "SHOW INDEXES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW INDEXES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW INDEXES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '5'.",
            "SHOW INDEXES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '<EOF>'.",
            "SHOW INDEXES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "SHOW INDEXES LIKE");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES IN TABLE ab");
        assertShape(
            "REFUSED Unsupported feature 'SHOW KEY_VALUE_TABLE_INDEXS ... WITH PRIVILEGES <>'.",
            "SHOW INDEXES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type KEY VALUE "
                + "TABLE INDEX in VIEW'.",
            "SHOW INDEXES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type KEY VALUE "
                + "TABLE INDEX in SERVICE'.",
            "SHOW INDEXES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type KEY VALUE "
                + "TABLE INDEX in CLASS'.",
            "SHOW INDEXES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type KEY VALUE "
                + "TABLE INDEX in INSTANCE'.",
            "SHOW INDEXES IN nosuch_class_x9 inst_x9");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Table 'NOSUCH_TABLE_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE_X9.",
            "SHOW INDEXES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW INDEXES IN ACCOUNT LIMIT 0");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type KEY VALUE "
                + "TABLE INDEX in VIEW'.",
            "SHOW INDEXES IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW INDEXES IN nosuch_db_x9.nosuch_s");
        assertShapes(INDEXES_COLUMNS,
            "SHOW INDEXES IN TABLE abv");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW INDEXES IN TABLE test_db.nosuch_s.t");
    }

    @Test
    public void channels() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS",
            "SHOW CHANNELS LIKE 'zz%'",
            "SHOW CHANNELS IN ACCOUNT",
            "SHOW CHANNELS IN DATABASE",
            "SHOW CHANNELS IN SCHEMA",
            "SHOW CHANNELS IN DATABASE test_db",
            "SHOW CHANNELS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB.",
            "SHOW CHANNELS IN test_db.test_schema");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 26 unexpected ''zz''.",
            "SHOW CHANNELS STARTS WITH 'zz'");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS LIMIT 1",
            "SHOW CHANNELS LIMIT 1 FROM 'zz'",
            "SHOW TERSE CHANNELS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'HISTORY'.",
            "SHOW CHANNELS HISTORY");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'STARTS'.",
            "SHOW CHANNELS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'nosuchword'.",
            "SHOW CHANNELS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_SCHEMA_X9",
            "SHOW CHANNELS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW CHANNELS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW CHANNELS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW CHANNELS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'.",
            "SHOW CHANNELS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "SHOW CHANNELS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
            "SHOW CHANNELS LIKE");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS IN TABLE ab");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'PRIVILEGES'.",
            "SHOW CHANNELS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for ABV",
            "SHOW CHANNELS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CHANNEL in "
                + "SERVICE'.",
            "SHOW CHANNELS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW CHANNELS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CHANNEL in "
                + "CLASS'.",
            "SHOW CHANNELS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CHANNEL in "
                + "INSTANCE'.",
            "SHOW CHANNELS IN nosuch_class_x9 inst_x9");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Table 'NOSUCH_TABLE_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE_X9.",
            "SHOW CHANNELS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Table 'NOSUCH_X9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_X9.",
            "SHOW CHANNELS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW CHANNELS IN ACCOUNT LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'HISTORY'.",
            "SHOW CHANNELS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW CHANNELS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'STARTS'. | syntax "
                + "error line 1 at position 26 unexpected ''zz''.",
            "SHOW CHANNELS STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'STARTS'.",
            "SHOW CHANNELS IN ACCOUNT STARTS WITH 'zz'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'STARTS'.",
            "SHOW CHANNELS LIKE 'zz%' STARTS WITH 'zz'");
        assertShapes(CHANNELS_COLUMNS,
            "SHOW CHANNELS IN VIEW",
            "SHOW CHANNELS IN SCHEMA",
            "SHOW CHANNELS IN DATABASE");
        assertShape(
            "REFUSED SQL compilation error: | View 'VIEW' does not exist or not authorized.",
            "SHOW CHANNELS IN TABLE abv");
        assertShape(
            "REFUSED SQL compilation error: | View 'VIEW' does not exist or not authorized.",
            "SHOW CHANNELS IN abv");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW CHANNELS IN nosuch_db_x9.s.t");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'STARTS'.",
            "SHOW CHANNELS IN SCHEMA STARTS WITH 'b'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'STARTS'.",
            "SHOW CHANNELS IN TABLE ab STARTS WITH 'b'");
    }

    @Test
    public void snapshots() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SNAPSHOTS_COLUMNS,
            "SHOW SNAPSHOTS",
            "SHOW SNAPSHOTS LIKE 'zz%'",
            "SHOW SNAPSHOTS IN ACCOUNT",
            "SHOW SNAPSHOTS IN DATABASE",
            "SHOW SNAPSHOTS IN SCHEMA",
            "SHOW SNAPSHOTS IN DATABASE test_db",
            "SHOW SNAPSHOTS IN SCHEMA test_db.test_schema",
            "SHOW SNAPSHOTS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "TABLE'.",
            "SHOW SNAPSHOTS IN TABLE ab");
        assertShapes(SNAPSHOTS_COLUMNS,
            "SHOW SNAPSHOTS STARTS WITH 'zz'",
            "SHOW SNAPSHOTS LIMIT 1",
            "SHOW SNAPSHOTS LIMIT 1 FROM 'zz'",
            "SHOW TERSE SNAPSHOTS",
            "SHOW SNAPSHOTS HISTORY",
            "SHOW SNAPSHOTS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'nosuchword'.",
            "SHOW SNAPSHOTS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SNAPSHOTS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW SNAPSHOTS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW SNAPSHOTS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SNAPSHOTS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'.",
            "SHOW SNAPSHOTS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "SHOW SNAPSHOTS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "SHOW SNAPSHOTS LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW SERVICE_SNAPSHOTS ... WITH PRIVILEGES <>'.",
            "SHOW SNAPSHOTS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "VIEW'.",
            "SHOW SNAPSHOTS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "SERVICE'.",
            "SHOW SNAPSHOTS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW SNAPSHOTS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "CLASS'.",
            "SHOW SNAPSHOTS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "INSTANCE'.",
            "SHOW SNAPSHOTS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "TABLE'.",
            "SHOW SNAPSHOTS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SNAPSHOT in "
                + "TABLE'.",
            "SHOW SNAPSHOTS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW SNAPSHOTS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SNAPSHOTS IN ACCOUNT LIMIT 0");
        assertShapes(SNAPSHOTS_COLUMNS,
            "SHOW SNAPSHOTS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SNAPSHOTS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_B",
            "SHOW SNAPSHOTS IN DATABASE nosuch_a.nosuch_b");
    }

    @Test
    public void gitRepositories() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(GIT_REPOSITORIES_COLUMNS,
            "SHOW GIT REPOSITORIES",
            "SHOW GIT REPOSITORIES LIKE 'zz%'",
            "SHOW GIT REPOSITORIES IN ACCOUNT",
            "SHOW GIT REPOSITORIES IN DATABASE",
            "SHOW GIT REPOSITORIES IN SCHEMA",
            "SHOW GIT REPOSITORIES IN DATABASE test_db",
            "SHOW GIT REPOSITORIES IN SCHEMA test_db.test_schema",
            "SHOW GIT REPOSITORIES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in TABLE'.",
            "SHOW GIT REPOSITORIES IN TABLE ab");
        assertShapes(GIT_REPOSITORIES_COLUMNS,
            "SHOW GIT REPOSITORIES STARTS WITH 'zz'",
            "SHOW GIT REPOSITORIES LIMIT 1",
            "SHOW GIT REPOSITORIES LIMIT 1 FROM 'zz'",
            "SHOW TERSE GIT REPOSITORIES",
            "SHOW GIT REPOSITORIES HISTORY",
            "SHOW GIT REPOSITORIES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'nosuchword'.",
            "SHOW GIT REPOSITORIES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW GIT REPOSITORIES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW GIT REPOSITORIES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW GIT REPOSITORIES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW GIT REPOSITORIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'.",
            "SHOW GIT REPOSITORIES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "SHOW GIT REPOSITORIES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SHOW GIT REPOSITORIES LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW GIT_REPOSITORYS ... WITH PRIVILEGES <>'.",
            "SHOW GIT REPOSITORIES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in VIEW'.",
            "SHOW GIT REPOSITORIES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in SERVICE'.",
            "SHOW GIT REPOSITORIES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW GIT REPOSITORIES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in CLASS'.",
            "SHOW GIT REPOSITORIES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in INSTANCE'.",
            "SHOW GIT REPOSITORIES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in TABLE'.",
            "SHOW GIT REPOSITORIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type GIT "
                + "REPOSITORY in TABLE'.",
            "SHOW GIT REPOSITORIES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW GIT REPOSITORIES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW GIT REPOSITORIES IN ACCOUNT LIMIT 0");
        assertShapes(GIT_REPOSITORIES_COLUMNS,
            "SHOW GIT REPOSITORIES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW GIT REPOSITORIES IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW GIT REPOSITORIES IN nosuch_a.nosuch_b.nosuch_c");
    }

    @Test
    public void semanticViews() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SEMANTIC_VIEWS_COLUMNS,
            "SHOW SEMANTIC VIEWS",
            "SHOW SEMANTIC VIEWS LIKE 'zz%'",
            "SHOW SEMANTIC VIEWS IN ACCOUNT",
            "SHOW SEMANTIC VIEWS IN DATABASE",
            "SHOW SEMANTIC VIEWS IN SCHEMA",
            "SHOW SEMANTIC VIEWS IN DATABASE test_db",
            "SHOW SEMANTIC VIEWS IN SCHEMA test_db.test_schema",
            "SHOW SEMANTIC VIEWS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in TABLE'.",
            "SHOW SEMANTIC VIEWS IN TABLE ab");
        assertShapes(SEMANTIC_VIEWS_COLUMNS,
            "SHOW SEMANTIC VIEWS STARTS WITH 'zz'",
            "SHOW SEMANTIC VIEWS LIMIT 1",
            "SHOW SEMANTIC VIEWS LIMIT 1 FROM 'zz'");
        assertShapes(TERSE_COLUMNS,
            "SHOW TERSE SEMANTIC VIEWS");
        assertShapes(SEMANTIC_VIEWS_COLUMNS,
            "SHOW SEMANTIC VIEWS HISTORY",
            "SHOW SEMANTIC VIEWS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'nosuchword'.",
            "SHOW SEMANTIC VIEWS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SEMANTIC VIEWS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW SEMANTIC VIEWS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW SEMANTIC VIEWS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SEMANTIC VIEWS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '5'.",
            "SHOW SEMANTIC VIEWS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SHOW SEMANTIC VIEWS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "SHOW SEMANTIC VIEWS LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW SEMANTIC_VIEWS ... WITH PRIVILEGES <>'.",
            "SHOW SEMANTIC VIEWS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in VIEW'.",
            "SHOW SEMANTIC VIEWS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in SERVICE'.",
            "SHOW SEMANTIC VIEWS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW SEMANTIC VIEWS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in CLASS'.",
            "SHOW SEMANTIC VIEWS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in INSTANCE'.",
            "SHOW SEMANTIC VIEWS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in TABLE'.",
            "SHOW SEMANTIC VIEWS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SEMANTIC "
                + "VIEW in TABLE'.",
            "SHOW SEMANTIC VIEWS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW SEMANTIC VIEWS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SEMANTIC VIEWS IN ACCOUNT LIMIT 0");
        assertShapes(SEMANTIC_VIEWS_COLUMNS,
            "SHOW SEMANTIC VIEWS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SEMANTIC VIEWS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_B",
            "SHOW SEMANTIC VIEWS IN ACCOUNT nosuch_a.nosuch_b");
    }

    @Test
    public void sessionPolicies() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW SESSION POLICIES",
            "SHOW SESSION POLICIES LIKE 'zz%'",
            "SHOW SESSION POLICIES IN ACCOUNT",
            "SHOW SESSION POLICIES IN DATABASE",
            "SHOW SESSION POLICIES IN SCHEMA",
            "SHOW SESSION POLICIES IN DATABASE test_db",
            "SHOW SESSION POLICIES IN SCHEMA test_db.test_schema",
            "SHOW SESSION POLICIES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in TABLE'.",
            "SHOW SESSION POLICIES IN TABLE ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW SESSION POLICIES STARTS WITH 'zz'",
            "SHOW SESSION POLICIES LIMIT 1",
            "SHOW SESSION POLICIES LIMIT 1 FROM 'zz'",
            "SHOW TERSE SESSION POLICIES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'HISTORY'.",
            "SHOW SESSION POLICIES HISTORY");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW SESSION POLICIES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'nosuchword'.",
            "SHOW SESSION POLICIES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SESSION POLICIES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW SESSION POLICIES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW SESSION POLICIES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SESSION POLICIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'.",
            "SHOW SESSION POLICIES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "SHOW SESSION POLICIES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SHOW SESSION POLICIES LIKE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'PRIVILEGES'.",
            "SHOW SESSION POLICIES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in VIEW'.",
            "SHOW SESSION POLICIES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in SERVICE'.",
            "SHOW SESSION POLICIES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW SESSION POLICIES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in CLASS'.",
            "SHOW SESSION POLICIES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in INSTANCE'.",
            "SHOW SESSION POLICIES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in TABLE'.",
            "SHOW SESSION POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in TABLE'.",
            "SHOW SESSION POLICIES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW SESSION POLICIES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW SESSION POLICIES IN ACCOUNT LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'HISTORY'.",
            "SHOW SESSION POLICIES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW SESSION POLICIES IN SCHEMA test_db.nosuch_schema_x9");
    }

    @Test
    public void authenticationPolicies() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW AUTHENTICATION POLICIES",
            "SHOW AUTHENTICATION POLICIES LIKE 'zz%'",
            "SHOW AUTHENTICATION POLICIES IN ACCOUNT",
            "SHOW AUTHENTICATION POLICIES IN DATABASE",
            "SHOW AUTHENTICATION POLICIES IN SCHEMA",
            "SHOW AUTHENTICATION POLICIES IN DATABASE test_db",
            "SHOW AUTHENTICATION POLICIES IN SCHEMA test_db.test_schema",
            "SHOW AUTHENTICATION POLICIES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in TABLE'.",
            "SHOW AUTHENTICATION POLICIES IN TABLE ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW AUTHENTICATION POLICIES STARTS WITH 'zz'",
            "SHOW AUTHENTICATION POLICIES LIMIT 1",
            "SHOW AUTHENTICATION POLICIES LIMIT 1 FROM 'zz'",
            "SHOW TERSE AUTHENTICATION POLICIES");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'HISTORY'.",
            "SHOW AUTHENTICATION POLICIES HISTORY");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW AUTHENTICATION POLICIES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'nosuchword'.",
            "SHOW AUTHENTICATION POLICIES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW AUTHENTICATION POLICIES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW AUTHENTICATION POLICIES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW AUTHENTICATION POLICIES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW AUTHENTICATION POLICIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '5'.",
            "SHOW AUTHENTICATION POLICIES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "SHOW AUTHENTICATION POLICIES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "SHOW AUTHENTICATION POLICIES LIKE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'PRIVILEGES'.",
            "SHOW AUTHENTICATION POLICIES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in VIEW'.",
            "SHOW AUTHENTICATION POLICIES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in SERVICE'.",
            "SHOW AUTHENTICATION POLICIES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW AUTHENTICATION POLICIES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in CLASS'.",
            "SHOW AUTHENTICATION POLICIES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in INSTANCE'.",
            "SHOW AUTHENTICATION POLICIES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in TABLE'.",
            "SHOW AUTHENTICATION POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                + "AUTHENTICATION POLICY in TABLE'.",
            "SHOW AUTHENTICATION POLICIES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW AUTHENTICATION POLICIES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW AUTHENTICATION POLICIES IN ACCOUNT LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'HISTORY'.",
            "SHOW AUTHENTICATION POLICIES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW AUTHENTICATION POLICIES IN SCHEMA test_db.nosuch_schema_x9");
    }

    @Test
    public void packagesPolicies() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PACKAGES POLICIES",
            "SHOW PACKAGES POLICIES LIKE 'zz%'",
            "SHOW PACKAGES POLICIES IN ACCOUNT",
            "SHOW PACKAGES POLICIES IN DATABASE",
            "SHOW PACKAGES POLICIES IN SCHEMA",
            "SHOW PACKAGES POLICIES IN DATABASE test_db",
            "SHOW PACKAGES POLICIES IN SCHEMA test_db.test_schema",
            "SHOW PACKAGES POLICIES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in TABLE'.",
            "SHOW PACKAGES POLICIES IN TABLE ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PACKAGES POLICIES STARTS WITH 'zz'",
            "SHOW PACKAGES POLICIES LIMIT 1",
            "SHOW PACKAGES POLICIES LIMIT 1 FROM 'zz'",
            "SHOW TERSE PACKAGES POLICIES",
            "SHOW PACKAGES POLICIES HISTORY",
            "SHOW PACKAGES POLICIES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'nosuchword'.",
            "SHOW PACKAGES POLICIES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW PACKAGES POLICIES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW PACKAGES POLICIES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW PACKAGES POLICIES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW PACKAGES POLICIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '5'.",
            "SHOW PACKAGES POLICIES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SHOW PACKAGES POLICIES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SHOW PACKAGES POLICIES LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW PACKAGES_POLICYS ... WITH PRIVILEGES <>'.",
            "SHOW PACKAGES POLICIES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in VIEW'.",
            "SHOW PACKAGES POLICIES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in SERVICE'.",
            "SHOW PACKAGES POLICIES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW PACKAGES POLICIES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in CLASS'.",
            "SHOW PACKAGES POLICIES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in INSTANCE'.",
            "SHOW PACKAGES POLICIES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in TABLE'.",
            "SHOW PACKAGES POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PACKAGES "
                + "POLICY in TABLE'.",
            "SHOW PACKAGES POLICIES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW PACKAGES POLICIES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW PACKAGES POLICIES IN ACCOUNT LIMIT 0");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PACKAGES POLICIES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW PACKAGES POLICIES IN SCHEMA test_db.nosuch_schema_x9");
    }

    @Test
    public void privacyPolicies() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PRIVACY POLICIES",
            "SHOW PRIVACY POLICIES LIKE 'zz%'",
            "SHOW PRIVACY POLICIES IN ACCOUNT",
            "SHOW PRIVACY POLICIES IN DATABASE",
            "SHOW PRIVACY POLICIES IN SCHEMA",
            "SHOW PRIVACY POLICIES IN DATABASE test_db",
            "SHOW PRIVACY POLICIES IN SCHEMA test_db.test_schema",
            "SHOW PRIVACY POLICIES IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in TABLE'.",
            "SHOW PRIVACY POLICIES IN TABLE ab");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PRIVACY POLICIES STARTS WITH 'zz'",
            "SHOW PRIVACY POLICIES LIMIT 1",
            "SHOW PRIVACY POLICIES LIMIT 1 FROM 'zz'",
            "SHOW TERSE PRIVACY POLICIES",
            "SHOW PRIVACY POLICIES HISTORY",
            "SHOW PRIVACY POLICIES LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'nosuchword'.",
            "SHOW PRIVACY POLICIES nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW PRIVACY POLICIES IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW PRIVACY POLICIES IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW PRIVACY POLICIES IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW PRIVACY POLICIES LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'.",
            "SHOW PRIVACY POLICIES LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
            "SHOW PRIVACY POLICIES IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SHOW PRIVACY POLICIES LIKE");
        assertShape(
            "REFUSED Unsupported feature 'SHOW PRIVACY_POLICYS ... WITH PRIVILEGES <>'.",
            "SHOW PRIVACY POLICIES WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in VIEW'.",
            "SHOW PRIVACY POLICIES IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in SERVICE'.",
            "SHOW PRIVACY POLICIES IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW PRIVACY POLICIES IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in CLASS'.",
            "SHOW PRIVACY POLICIES IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in INSTANCE'.",
            "SHOW PRIVACY POLICIES IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in TABLE'.",
            "SHOW PRIVACY POLICIES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type PRIVACY "
                + "POLICY in TABLE'.",
            "SHOW PRIVACY POLICIES IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW PRIVACY POLICIES IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW PRIVACY POLICIES IN ACCOUNT LIMIT 0");
        assertShapes(SESSION_POLICIES_COLUMNS,
            "SHOW PRIVACY POLICIES HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW PRIVACY POLICIES IN SCHEMA test_db.nosuch_schema_x9");
    }

    @Test
    public void externalFunctions() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW EXTERNAL FUNCTIONS",
            "SHOW EXTERNAL FUNCTIONS LIKE 'zz%'",
            "SHOW EXTERNAL FUNCTIONS IN ACCOUNT",
            "SHOW EXTERNAL FUNCTIONS IN DATABASE",
            "SHOW EXTERNAL FUNCTIONS IN SCHEMA",
            "SHOW EXTERNAL FUNCTIONS IN DATABASE test_db",
            "SHOW EXTERNAL FUNCTIONS IN SCHEMA test_db.test_schema",
            "SHOW EXTERNAL FUNCTIONS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW EXTERNAL FUNCTIONS IN TABLE ab");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW EXTERNAL FUNCTIONS STARTS WITH 'zz'",
            "SHOW EXTERNAL FUNCTIONS LIMIT 1",
            "SHOW EXTERNAL FUNCTIONS LIMIT 1 FROM 'zz'",
            "SHOW TERSE EXTERNAL FUNCTIONS",
            "SHOW EXTERNAL FUNCTIONS HISTORY",
            "SHOW EXTERNAL FUNCTIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'nosuchword'.",
            "SHOW EXTERNAL FUNCTIONS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW EXTERNAL FUNCTIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW EXTERNAL FUNCTIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW EXTERNAL FUNCTIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW EXTERNAL FUNCTIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '5'.",
            "SHOW EXTERNAL FUNCTIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SHOW EXTERNAL FUNCTIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SHOW EXTERNAL FUNCTIONS LIKE");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW EXTERNAL FUNCTIONS IN SCHEMA test_db.test_schema");
        assertShape(
            "REFUSED Unsupported feature 'SHOW FUNCTIONS ... WITH PRIVILEGES <>'.",
            "SHOW EXTERNAL FUNCTIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "VIEW'.",
            "SHOW EXTERNAL FUNCTIONS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH_X9' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X9.",
            "SHOW EXTERNAL FUNCTIONS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW EXTERNAL FUNCTIONS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_X9' does not exist or not authorized.",
            "SHOW EXTERNAL FUNCTIONS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLASS_X9' does not exist or not "
                + "authorized.",
            "SHOW EXTERNAL FUNCTIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW EXTERNAL FUNCTIONS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW EXTERNAL FUNCTIONS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW EXTERNAL FUNCTIONS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW EXTERNAL FUNCTIONS IN ACCOUNT LIMIT 0");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW EXTERNAL FUNCTIONS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW EXTERNAL FUNCTIONS IN SCHEMA test_db.nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'nosuch_x9'.",
            "SHOW EXTERNAL FUNCTIONS IN MODEL nosuch_x9");
    }

    @Test
    public void dataMetricFunctions() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ab2 (b INT REFERENCES ab (a))");
        engine.execute(
            "CREATE OR REPLACE VIEW abv AS SELECT a FROM ab");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW DATA METRIC FUNCTIONS",
            "SHOW DATA METRIC FUNCTIONS LIKE 'zz%'",
            "SHOW DATA METRIC FUNCTIONS IN ACCOUNT",
            "SHOW DATA METRIC FUNCTIONS IN DATABASE",
            "SHOW DATA METRIC FUNCTIONS IN SCHEMA",
            "SHOW DATA METRIC FUNCTIONS IN DATABASE test_db",
            "SHOW DATA METRIC FUNCTIONS IN SCHEMA test_db.test_schema",
            "SHOW DATA METRIC FUNCTIONS IN test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW DATA METRIC FUNCTIONS IN TABLE ab");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW DATA METRIC FUNCTIONS STARTS WITH 'zz'",
            "SHOW DATA METRIC FUNCTIONS LIMIT 1",
            "SHOW DATA METRIC FUNCTIONS LIMIT 1 FROM 'zz'",
            "SHOW TERSE DATA METRIC FUNCTIONS",
            "SHOW DATA METRIC FUNCTIONS HISTORY",
            "SHOW DATA METRIC FUNCTIONS LIKE 'zz%' IN ACCOUNT STARTS WITH 'zz' LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'nosuchword'.",
            "SHOW DATA METRIC FUNCTIONS nosuchword");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW DATA METRIC FUNCTIONS IN SCHEMA nosuch_schema_x9");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_X9.",
            "SHOW DATA METRIC FUNCTIONS IN DATABASE nosuch_db_x9");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_X9",
            "SHOW DATA METRIC FUNCTIONS IN ACCOUNT nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW DATA METRIC FUNCTIONS LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '5'.",
            "SHOW DATA METRIC FUNCTIONS LIKE 5");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
            "SHOW DATA METRIC FUNCTIONS IN");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "SHOW DATA METRIC FUNCTIONS LIKE");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW DATA METRIC FUNCTIONS IN ACCOUNT");
        assertShape(
            "REFUSED Unsupported feature 'SHOW FUNCTIONS ... WITH PRIVILEGES <>'.",
            "SHOW DATA METRIC FUNCTIONS WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "VIEW'.",
            "SHOW DATA METRIC FUNCTIONS IN VIEW abv");
        assertShape(
            "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH_X9' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X9.",
            "SHOW DATA METRIC FUNCTIONS IN SERVICE nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_X9.",
            "SHOW DATA METRIC FUNCTIONS IN APPLICATION nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_X9' does not exist or not authorized.",
            "SHOW DATA METRIC FUNCTIONS IN CLASS nosuch_x9");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLASS_X9' does not exist or not "
                + "authorized.",
            "SHOW DATA METRIC FUNCTIONS IN nosuch_class_x9 inst_x9");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW DATA METRIC FUNCTIONS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type FUNCTION in "
                + "TABLE'.",
            "SHOW DATA METRIC FUNCTIONS IN TABLE nosuch_table_x9");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X9' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X9.",
            "SHOW DATA METRIC FUNCTIONS IN nosuch_x9");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW DATA METRIC FUNCTIONS IN ACCOUNT LIMIT 0");
        assertShapes(EXTERNAL_FUNCTIONS_COLUMNS,
            "SHOW DATA METRIC FUNCTIONS HISTORY LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_X9.",
            "SHOW DATA METRIC FUNCTIONS IN SCHEMA test_db.nosuch_schema_x9");
    }
}
