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
 * ALTER SCHEMA, ALTER DATABASE and ALTER TABLE take the properties of their own level alone (live-verified). A name
 * that is no parameter at all is an invalid property of the kind, judged first and in the account's own order; a
 * parameter of another level, set or unset, is refused for want of access, as
 * {@code Insufficient privileges to operate on schema 'TEST_SCHEMA'.}; CREATE SCHEMA and CREATE TABLE refuse one as
 * {@code cannot set parameter 'TIMEZONE' at 'SCHEMA'}. A container's classification profile is taken: unset, and
 * set to a profile that must exist, {@code invalid value 'P' for property 'bundle instance name'}; a catalog's
 * namespace properties are no schema's, and no database's that is not linked to a catalog.
 */
public class ParameterLevelTest extends MeasuredCellsTestSupport {

    @Test
    public void eachLevelTakesItsOwnParameters() {
        engine.execute("CREATE OR REPLACE TABLE nf_p (a INT)");
        assertCells(new String[][] {
            {"ALTER SCHEMA test_db.test_schema SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
            {"ALTER SCHEMA test_db.test_schema UNSET TIMEZONE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
            {"ALTER DATABASE test_db SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'."},
            {"ALTER DATABASE test_db UNSET TIMEZONE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'."},
            {"ALTER TABLE nf_p SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'NF_P'."},
            {"ALTER TABLE nf_p UNSET TIMEZONE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'NF_P'."},
            {"ALTER SCHEMA test_db.test_schema SET AUTOCOMMIT = TRUE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
            {"ALTER SCHEMA test_db.test_schema SET NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema UNSET NOSUCH_PARAMETER",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 7",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
            {"CREATE SCHEMA test_db.nf_sx ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9",
                "REFUSED SQL compilation error: | cannot set parameter 'ICEBERG_TIMESTAMP_DEFAULT_PRECISION' at "
                + "'SCHEMA'"},
            {"CREATE SCHEMA test_db.nf_sy TIMEZONE = 'UTC'",
                "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'SCHEMA'"},
            {"ALTER TABLE nf_p SET ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'NF_P'."},
            {"ALTER TABLE nf_p SET AUTOCOMMIT = TRUE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'NF_P'."},
            {"ALTER SCHEMA test_db.test_schema SET TIMEZONE = 'Nowhere/Zone'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
            {"ALTER SCHEMA test_db.test_schema SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET DATA_RETENTION_TIME_IN_DAYS", "Statement executed successfully."},
            {"ALTER DATABASE test_db SET NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'DATABASE'"},
            {"ALTER DATABASE test_db UNSET NOSUCH_PARAMETER",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'DATABASE'"},
            {"ALTER SCHEMA test_db.test_schema SET ICEBERG_VERSION_DEFAULT = 2", "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET ICEBERG_VERSION_DEFAULT", "Statement executed successfully."},
            {"CREATE SCHEMA test_db.nf_sz AUTOCOMMIT = TRUE",
                "REFUSED SQL compilation error: | cannot set parameter 'AUTOCOMMIT' at 'SCHEMA'"},
            {"CREATE SCHEMA test_db.nf_sw NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"CREATE SCHEMA test_db.nf_sv TIMEZONE = 'UTC' NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema SET COMMENT = 'k' TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'."},
        });
        engine.execute("SHOW SCHEMAS LIKE 'TEST_SCHEMA' IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"name\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "TEST_SCHEMA|"},
            {"ALTER SCHEMA test_db.test_schema SET TIMEZONE = 'UTC' NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema SET NOSUCH_PARAMETER = 1 TIMEZONE = 'UTC'",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER TABLE nf_p SET TIMEZONE = 'UTC' NOSUCH_PARAMETER = 1",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'TABLE'"},
            {"ALTER TABLE nf_p UNSET NOSUCH_PARAMETER",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'TABLE'"},
            {"ALTER DATABASE test_db SET AUTOCOMMIT = TRUE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on database 'TEST_DB'."},
            {"CREATE OR REPLACE TABLE nf_q (a INT) TIMEZONE = 'UTC'",
                "REFUSED SQL compilation error: | cannot set parameter 'TIMEZONE' at 'TABLE'"},
            {"USE SCHEMA test_db.test_schema", "Statement executed successfully."},
        });
    }

    @Test
    public void theContainersOwnPropertiesAreJudgedToo() {
        assertCells(new String[][] {
            {"ALTER SCHEMA test_db.test_schema UNSET CLASSIFICATION_PROFILE", "Statement executed successfully."},
            {"ALTER DATABASE test_db UNSET CATALOG_SYNC_NAMESPACE_MODE",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'DATABASE'"},
            {"ALTER DATABASE test_db UNSET CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER' for "
                + "'DATABASE'"},
            {"ALTER DATABASE test_db UNSET CLASSIFICATION_PROFILE", "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET CATALOG_SYNC_NAMESPACE_MODE",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema UNSET CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER' for "
                + "'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema SET CLASSIFICATION_PROFILE = 'nosuch_profile'",
                "REFUSED SQL compilation error: | invalid value 'nosuch_profile' for property 'bundle instance name'"},
            {"ALTER DATABASE test_db SET CLASSIFICATION_PROFILE = 'nosuch_profile'",
                "REFUSED SQL compilation error: | invalid value 'nosuch_profile' for property 'bundle instance name'"},
            {"ALTER DATABASE test_db SET CATALOG_SYNC_NAMESPACE_MODE = NEST",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'DATABASE'"},
            {"ALTER DATABASE test_db SET CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER = '-'",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER' for "
                + "'DATABASE'"},
            {"ALTER SCHEMA test_db.test_schema SET CATALOG_SYNC_NAMESPACE_MODE = NEST",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema UNSET CLASSIFICATION_PROFILE, NOSUCH_PARAMETER",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER SCHEMA test_db.test_schema UNSET NOSUCH_PARAMETER, CLASSIFICATION_PROFILE",
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'"},
            {"ALTER DATABASE test_db UNSET CATALOG_SYNC_NAMESPACE_MODE, TIMEZONE",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'DATABASE'"},
            {"ALTER SCHEMA test_db.test_schema UNSET SERVERLESS_TASK_MIN_STATEMENT_SIZE",
                "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET ICEBERG_MERGE_ON_READ_BEHAVIOR",
                "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET ENABLE_DATA_COMPACTION", "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET CATALOG_SYNC", "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET EXTERNAL_VOLUME", "Statement executed successfully."},
            {"ALTER SCHEMA test_db.test_schema UNSET CATALOG", "Statement executed successfully."},
            {"ALTER DATABASE test_db UNSET ENABLE_DATA_COMPACTION", "Statement executed successfully."},
            {"CREATE SCHEMA test_db.nf_cp CLASSIFICATION_PROFILE = 'nosuch_profile'",
                "REFUSED SQL compilation error: | invalid value 'nosuch_profile' for property 'bundle instance name'"},
            {"CREATE SCHEMA test_db.nf_cq CATALOG_SYNC_NAMESPACE_MODE = NEST",
                "REFUSED SQL compilation error: | invalid property 'CATALOG_SYNC_NAMESPACE_MODE' for 'SCHEMA'"},
        });
    }
}
