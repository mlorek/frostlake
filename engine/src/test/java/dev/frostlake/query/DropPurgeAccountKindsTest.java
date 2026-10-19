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
 * DROP ... PURGE for the kinds that were left: a warehouse, a compute pool, a user, a role and a database role
 * read the word and refuse it as the kinds of a schema do, a network policy takes it; another kind under the name
 * a DROP ICEBERG TABLE gives is refused first; HYBRID names no kind after DROP; an aggregation and a join policy
 * are created in a sentence of their own (live-verified). The statements that name a kind of the account name
 * objects that cannot exist, so none of them drops anything whatever the answer.
 */
public class DropPurgeAccountKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void theAccountsKindsReadTheWord() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP WAREHOUSE IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on WAREHOUSE "
                + "NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP WAREHOUSE nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP COMPUTE POOL IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Compute pool 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on COMPUTE POOL "
                + "NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP COMPUTE POOL nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP USER IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | User 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
            "DROP USER nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP ROLE IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP ROLE nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP NETWORK POLICY IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NETWORK POLICY "
                + "NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP NETWORK POLICY nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP DATABASE ROLE IF EXISTS nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_PURGE_ZQ_7F3A91' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP DATABASE ROLE nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'.",
            "DROP WAREHOUSE IF EXISTS nosuch_purge_zq_7f3a91 PURGE x");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP WAREHOUSE IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP ROLE IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP USER IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP COMPUTE POOL IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP NETWORK POLICY IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TEST_DB.NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP DATABASE ROLE IF EXISTS test_db.nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_PURGE_ZQ_7F3A91' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                + "TEST_DB.NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP DATABASE ROLE test_db.nosuch_purge_zq_7f3a91 PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_DB_ZQ_7F3A91.",
            "DROP DATABASE ROLE nosuch_db_zq_7f3a91.r PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ_7F3A91' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_DB_ZQ_7F3A91.",
            "DROP DATABASE ROLE IF EXISTS nosuch_db_zq_7f3a91.r PURGE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP DATABASE ROLE IF EXISTS nosuch_purge_zq_7f3a91 CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP DATABASE ROLE IF EXISTS nosuch_purge_zq_7f3a91");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: '\"nosuch purge zq 7f3a91\"'.",
            "DROP WAREHOUSE IF EXISTS \"nosuch purge zq 7f3a91\" PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_PURGE_ZQ_7F3A91'.",
            "DROP ROLE IF EXISTS IDENTIFIER('nosuch_purge_zq_7f3a91') PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'CASCADE'.",
            "DROP WAREHOUSE IF EXISTS nosuch_purge_zq_7f3a91 PURGE CASCADE");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP WAREHOUSE IF EXISTS nosuch_purge_zq_7f3a91 RESTRICT");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PURGE_ZQ_7F3A91 already dropped).",
            "DROP NETWORK POLICY IF EXISTS nosuch_purge_zq_7f3a91 RESTRICT");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'NOSUCH_PURGE_ZQ_7F3A91' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NETWORK POLICY "
                + "NOSUCH_PURGE_ZQ_7F3A91.",
            "DROP NETWORK POLICY nosuch_purge_zq_7f3a91 CASCADE");
    }

    @Test
    public void anotherKindUnderAnIcebergTablesName() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP ICEBERG TABLE nosuch_ic_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP ICEBERG TABLE nosuch_ic_zq");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_IC_ZQ'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_ic_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP ICEBERG TABLE nosuch_ic_zq CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP ICEBERG TABLE test_schema.nosuch_ic_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP ICEBERG TABLE test_db.test_schema.nosuch_ic_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.\"nosuch ic\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.\"nosuch ic\".",
            "DROP ICEBERG TABLE \"nosuch ic\" PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ.",
            "DROP ICEBERG TABLE nosuch_db_zq.s.t PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_ZQ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_ZQ.",
            "DROP ICEBERG TABLE test_db.nosuch_schema_zq.t PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_IC_ZQ.",
            "DROP TABLE nosuch_ic_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_ET_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ET_ZQ.",
            "DROP EXTERNAL TABLE nosuch_et_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_EV_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_EV_ZQ.",
            "DROP EVENT TABLE nosuch_ev_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID TABLE nosuch_hy_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_DT_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DT_ZQ.",
            "DROP DYNAMIC TABLE nosuch_dt_zq PURGE");
        assertAnswer(
            "Table TPL successfully created.",
            "CREATE OR REPLACE TABLE tpl (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE tpl PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS tpl PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE tpl");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM tpl");
        assertAnswer(
            "View VPL successfully created.",
            "CREATE OR REPLACE VIEW vpl AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE vpl PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE vpl");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM vpl");
        assertAnswer(
            "Table TPL successfully created.",
            "CREATE OR REPLACE TABLE tpl (x INT)");
        assertAnswer(
            "View VPL successfully created.",
            "CREATE OR REPLACE VIEW vpl AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS vpl PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS vpl");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS tpl");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS tpl CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE test_schema.tpl PURGE");
        assertAnswer(
            "Sequence SQP_ZQ successfully created.",
            "CREATE OR REPLACE SEQUENCE sqp_zq");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.SQP_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.SQP_ZQ.",
            "DROP ICEBERG TABLE sqp_zq");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.SQP_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.SQP_ZQ.",
            "DROP ICEBERG TABLE sqp_zq PURGE");
        assertAnswer(
            "Table EVP_ZQ successfully created.",
            "CREATE OR REPLACE EVENT TABLE evp_zq");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'EVENT_TABLE', not specified type "
                + "'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE evp_zq");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'EVENT_TABLE', not specified type "
                + "'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE evp_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'EVENT_TABLE', not specified type "
                + "'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE IF EXISTS evp_zq PURGE");
        assertAnswer(
            "Stream STP_ZQ successfully created.",
            "CREATE OR REPLACE STREAM stp_zq ON TABLE tpl");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE stp_zq");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE stp_zq PURGE");
    }

    @Test
    public void anExistingObjectIsRefusedAndKept() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "Alert AL_ZQ successfully created.",
            "CREATE OR REPLACE ALERT al_zq SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'AL_ZQ'.",
            "DROP ALERT al_zq PURGE");
        assertAnswer(
            "AL_ZQ successfully dropped.",
            "DROP ALERT al_zq");
        assertAnswer(
            "Password policy 'PP_ZQ' created successfully",
            "CREATE OR REPLACE PASSWORD POLICY pp_zq PASSWORD_MIN_LENGTH = 20");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PP_ZQ'.",
            "DROP PASSWORD POLICY pp_zq PURGE");
        assertAnswer(
            "PP_ZQ successfully dropped.",
            "DROP PASSWORD POLICY pp_zq");
        assertAnswer(
            "Aggregation policy 'AG_ZQ' is successfully created.",
            "CREATE OR REPLACE AGGREGATION POLICY ag_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'AG_ZQ'.",
            "DROP AGGREGATION POLICY ag_zq PURGE");
        assertAnswer(
            "AG_ZQ successfully dropped.",
            "DROP AGGREGATION POLICY ag_zq");
        assertAnswer(
            "Projection policy 'PJ_ZQ' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY pj_zq AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PJ_ZQ'.",
            "DROP PROJECTION POLICY pj_zq PURGE");
        assertAnswer(
            "PJ_ZQ successfully dropped.",
            "DROP PROJECTION POLICY pj_zq");
        assertAnswer(
            "Join policy 'JP_ZQ' is successfully created.",
            "CREATE OR REPLACE JOIN POLICY jp_zq AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED "
                + "=> FALSE)");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'JP_ZQ'.",
            "DROP JOIN POLICY jp_zq PURGE");
        assertAnswer(
            "JP_ZQ successfully dropped.",
            "DROP JOIN POLICY jp_zq");
        assertAnswer(
            "Stage area STG_ZQ successfully created.",
            "CREATE OR REPLACE STAGE stg_zq");
        assertAnswer(
            "Streamlit SL_ZQ successfully created.",
            "CREATE OR REPLACE STREAMLIT sl_zq ROOT_LOCATION = '@stg_zq/old' MAIN_FILE = 'a.py'");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SL_ZQ'.",
            "DROP STREAMLIT sl_zq PURGE");
        assertAnswer(
            "SL_ZQ successfully dropped.",
            "DROP STREAMLIT sl_zq");
        assertAnswer(
            "Notebook NB_ZQ successfully created.",
            "CREATE OR REPLACE NOTEBOOK nb_zq COMMENT = 'a'");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NB_ZQ'.",
            "DROP NOTEBOOK nb_zq PURGE");
        assertAnswer(
            "NB_ZQ successfully dropped.",
            "DROP NOTEBOOK nb_zq");
    }

    @Test
    public void policiesAreCreatedInTheirOwnSentence() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "Aggregation policy 'AG2_ZQ' is successfully created.",
            "CREATE AGGREGATION POLICY ag2_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'AG2_ZQ' already exists.",
            "CREATE AGGREGATION POLICY ag2_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "AG2_ZQ already exists, statement succeeded.",
            "CREATE AGGREGATION POLICY IF NOT EXISTS ag2_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "Aggregation policy 'AG2_ZQ' is successfully created.",
            "CREATE OR REPLACE AGGREGATION POLICY ag2_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "Aggregation policy 'ag2_zq q' is successfully created.",
            "CREATE OR REPLACE AGGREGATION POLICY \"ag2_zq q\" AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "ag2_zq q successfully dropped.",
            "DROP AGGREGATION POLICY \"ag2_zq q\"");
        assertAnswer(
            "AG2_ZQ successfully dropped.",
            "DROP AGGREGATION POLICY ag2_zq");
        assertAnswer(
            "Aggregation policy 'AG2_ZQ' is successfully created.",
            "CREATE AGGREGATION POLICY IF NOT EXISTS ag2_zq AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        assertAnswer(
            "AG2_ZQ successfully dropped.",
            "DROP AGGREGATION POLICY IF EXISTS ag2_zq");
        assertAnswer(
            "Join policy 'JP2_ZQ' is successfully created.",
            "CREATE JOIN POLICY jp2_zq AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'JP2_ZQ' already exists.",
            "CREATE JOIN POLICY jp2_zq AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer(
            "JP2_ZQ already exists, statement succeeded.",
            "CREATE JOIN POLICY IF NOT EXISTS jp2_zq AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer(
            "Join policy 'JP2_ZQ' is successfully created.",
            "CREATE OR REPLACE JOIN POLICY jp2_zq AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED "
                + "=> FALSE)");
        assertAnswer(
            "Join policy 'jp2_zq q' is successfully created.",
            "CREATE OR REPLACE JOIN POLICY \"jp2_zq q\" AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer(
            "jp2_zq q successfully dropped.",
            "DROP JOIN POLICY \"jp2_zq q\"");
        assertAnswer(
            "JP2_ZQ successfully dropped.",
            "DROP JOIN POLICY jp2_zq");
        assertAnswer(
            "Join policy 'JP2_ZQ' is successfully created.",
            "CREATE JOIN POLICY IF NOT EXISTS jp2_zq AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer(
            "JP2_ZQ successfully dropped.",
            "DROP JOIN POLICY IF EXISTS jp2_zq");
        assertAnswer(
            "Projection policy 'PJ2_ZQ' is successfully created.",
            "CREATE PROJECTION POLICY pj2_zq AS () RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW "
                + "=> TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PJ2_ZQ' already exists.",
            "CREATE PROJECTION POLICY pj2_zq AS () RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW "
                + "=> TRUE)");
        assertAnswer(
            "PJ2_ZQ already exists, statement succeeded.",
            "CREATE PROJECTION POLICY IF NOT EXISTS pj2_zq AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        assertAnswer(
            "Projection policy 'PJ2_ZQ' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY pj2_zq AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        assertAnswer(
            "Projection policy 'pj2_zq q' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY \"pj2_zq q\" AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        assertAnswer(
            "pj2_zq q successfully dropped.",
            "DROP PROJECTION POLICY \"pj2_zq q\"");
        assertAnswer(
            "PJ2_ZQ successfully dropped.",
            "DROP PROJECTION POLICY pj2_zq");
        assertAnswer(
            "Projection policy 'PJ2_ZQ' is successfully created.",
            "CREATE PROJECTION POLICY IF NOT EXISTS pj2_zq AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        assertAnswer(
            "PJ2_ZQ successfully dropped.",
            "DROP PROJECTION POLICY IF EXISTS pj2_zq");
    }

    @Test
    public void hybridIsNoKindAfterDrop() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID TABLE nosuch_hy_zq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID TABLE IF EXISTS nosuch_hy_zq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'. | syntax "
                + "error line 1 at position 12 unexpected 'nosuch_hy_zq'.",
            "DROP HYBRID nosuch_hy_zq");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'HYBRID'.",
            "DROP HYBRID");
    }
}
