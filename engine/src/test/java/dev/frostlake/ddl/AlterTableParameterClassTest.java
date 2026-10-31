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
 * What ALTER TABLE … SET and UNSET answer for each kind of name (live-verified, every parameter the account lists
 * measured): a table's own properties and its thirteen parameters are set; LOG_LEVEL, TRACE_LEVEL,
 * ROW_TIMESTAMP_DEFAULT and the other task, alert, logging and pipe parameters are refused at the table's level,
 * "cannot set parameter
 * 'LOG_LEVEL' at 'TABLE'"; any other parameter for want of access; BASE_LOCATION as an unsupported feature and a name
 * no parameter has as an invalid property, the name as written, both before the table is looked for; and an Iceberg
 * table's parameters over a plain table in CREATE's words.
 */
public class AlterTableParameterClassTest extends MeasuredCellsTestSupport {

    /** Each class over a plain table, and over a table that is not there. */
    @Test
    public void eachNameIsJudgedByItsClass() {
        engine.execute("CREATE OR REPLACE TABLE pt_t (a INT)");
        assertCells(new String[][] {
            {"ALTER TABLE pt_t SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "Statement executed successfully."},
            {"ALTER TABLE pt_t SET QUOTED_IDENTIFIERS_IGNORE_CASE = TRUE",
                "Statement executed successfully."},
            {"ALTER TABLE pt_t SET LOG_LEVEL = 'INFO'",
                "REFUSED SQL compilation error: | cannot set parameter 'LOG_LEVEL' at 'TABLE'"},
            {"ALTER TABLE pt_t SET TRACE_LEVEL = 'ALWAYS'",
                "REFUSED SQL compilation error: | cannot set parameter 'TRACE_LEVEL' at 'TABLE'"},
            {"ALTER TABLE pt_t SET ROW_TIMESTAMP_DEFAULT = TRUE",
                "REFUSED SQL compilation error: | cannot set parameter 'ROW_TIMESTAMP_DEFAULT' at 'TABLE'"},
            {"ALTER TABLE pt_t SET TIMEZONE = 'UTC'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'PT_T'."},
            {"ALTER TABLE pt_t SET STATEMENT_TIMEOUT_IN_SECONDS = 10",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'PT_T'."},
            {"ALTER TABLE pt_t SET BASE_LOCATION = 'x'",
                "REFUSED Unsupported feature 'BASE_LOCATION'."},
            {"ALTER TABLE pt_t SET foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"ALTER TABLE nosuch_pt SET foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"ALTER TABLE nosuch_pt SET TIMEZONE = 'UTC'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PT."},
            {"ALTER TABLE nosuch_pt SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_PT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PT."},
        });
    }

    /** The names as written, several in one SET, UNSET, and the Iceberg parameters. */
    @Test
    public void theNamesAndTheIcebergParameters() {
        engine.execute("CREATE OR REPLACE TABLE sp_t (a INT)");
        assertCells(new String[][] {
            {"ALTER SCHEMA test_db.test_schema SET foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'SCHEMA'"},
            {"CREATE OR REPLACE VIEW sp_v foo = 1 AS SELECT 1 AS c",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"CREATE OR REPLACE SEQUENCE sp_s foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'SEQUENCE'"},
            {"CREATE OR REPLACE TABLE sp_t2 (a INT) foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"ALTER TABLE sp_t UNSET foo",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"ALTER TABLE sp_t SET Foo = 1",
                "REFUSED SQL compilation error: | invalid property 'Foo' for 'TABLE'"},
            {"ALTER TABLE sp_t SET \"foo\" = 1",
                "REFUSED SQL compilation error: | invalid property '\"foo\"' for 'TABLE'"},
            {"ALTER TABLE sp_t SET foo = 1, bar = 2",
                "REFUSED SQL compilation error: | invalid property 'bar' for 'TABLE'"},
            {"ALTER TABLE sp_t SET LOG_LEVEL = 'INFO', foo = 1",
                "REFUSED SQL compilation error: | invalid property 'foo' for 'TABLE'"},
            {"ALTER TABLE sp_t SET TIMEZONE = 'UTC', LOG_LEVEL = 'INFO'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'SP_T'."},
            {"ALTER TABLE sp_t SET log_level = 'INFO'",
                "REFUSED SQL compilation error: | cannot set parameter 'LOG_LEVEL' at 'TABLE'"},
            {"ALTER TABLE nosuch_sp SET LOG_LEVEL = 'INFO'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP."},
            {"ALTER TABLE nosuch_sp SET BASE_LOCATION = 'x'",
                "REFUSED Unsupported feature 'BASE_LOCATION'."},
            {"ALTER TABLE sp_t UNSET LOG_LEVEL",
                "REFUSED SQL compilation error: | cannot set parameter 'LOG_LEVEL' at 'TABLE'"},
            {"ALTER TABLE sp_t UNSET TIMEZONE",
                "REFUSED SQL access control error: | Insufficient privileges to operate on table 'SP_T'."},
            {"ALTER TABLE sp_t UNSET BASE_LOCATION",
                "REFUSED SQL compilation error: | invalid property 'BASE_LOCATION' for 'TABLE'"},
            {"ALTER TABLE sp_t SET CATALOG = 'cat_x'",
                "REFUSED Catalog integration cannot be set for legacy iceberg tables"},
            {"ALTER TABLE sp_t SET EXTERNAL_VOLUME = 'ev_x'",
                "REFUSED EXTERNAL_VOLUME is not supported on table SP_T."},
            {"ALTER TABLE sp_t SET TARGET_FILE_SIZE = '16MB'",
                "REFUSED SQL Compilation error: | Setting target file size is only supported for Iceberg tables."},
            {"ALTER TABLE sp_t SET STORAGE_SERIALIZATION_POLICY = 'COMPATIBLE'",
                "REFUSED SQL Compilation error: | Setting storage serialization policy is only supported for "
                + "Iceberg tables using Snowflake catalog."},
            {"ALTER TABLE sp_t SET PURGE_ON_DROP_TABLE = TRUE",
                "REFUSED SQL compilation error: | invalid parameter 'PURGE_ON_DROP_TABLE can only be set on tables "
                + "in a catalog-linked database'"},
            {"ALTER TABLE sp_t SET REPLACE_INVALID_CHARACTERS = TRUE",
                "REFUSED SQL compilation error: | invalid type of property 'REPLACE_INVALID_CHARACTERS' for 'TABLE'"},
            {"ALTER TABLE sp_t SET VENDED_CREDENTIAL_STORAGE_REGION = 'x'",
                "REFUSED SQL compilation error: | invalid value [Table must be an unmanaged table with credential "
                + "vending enabled.] for parameter '{1}'"},
            {"ALTER TABLE sp_t SET CATALOG_SYNC = 'cs_x'",
                "REFUSED Catalog integration cs_x is not valid. Please ensure required properties are valid."},
        });
    }
}
