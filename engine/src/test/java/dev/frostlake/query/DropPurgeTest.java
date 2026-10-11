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
 * PURGE where CASCADE or RESTRICT stand after a DROP: the account reads the word after a DROP of any kind and
 * refuses it, once the name has resolved, in one sentence that speaks of a table whatever is dropped. A missing
 * database or schema is refused first, and so are a missing object that no IF EXISTS forgives and a relation of
 * another kind; with IF EXISTS the sentence stands in place of the skipped drop. A file format is dropped as if
 * the word were not there, and no second behaviour word follows. Every answer is live-verified.
 */
public class DropPurgeTest extends MeasuredCellsTestSupport {

    @Test
    public void thePurgeOfEachKind() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tq (x INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vp AS SELECT 1 AS c");
        engine.execute(
            "CREATE OR REPLACE VIEW vq AS SELECT 1 AS c");
        engine.execute(
            "CREATE SCHEMA IF NOT EXISTS sp");
        engine.execute(
            "CREATE OR REPLACE SEQUENCE sq");
        engine.execute(
            "CREATE OR REPLACE STAGE sg");
        engine.execute(
            "USE SCHEMA test_schema");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE tp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TQ'.",
            "DROP TABLE tq (a) PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'VP'.",
            "DROP VIEW vp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'VQ'.",
            "DROP VIEW vq (a) PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SP'.",
            "DROP SCHEMA sp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.SQ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.SQ.",
            "DROP SEQUENCE sq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.SG' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on STAGE TEST_DB.TEST_SCHEMA.SG.",
            "DROP STAGE sg PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TZ'.",
            "DROP TABLE IF EXISTS tz PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.TZ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.TZ.",
            "DROP TABLE tz PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'CASCADE'.",
            "DROP TABLE t1 PURGE CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'PURGE'.",
            "DROP TABLE t1 PURGE PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PURGE'.",
            "DROP TABLE t1 CASCADE PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'RESTRICT'.",
            "DROP TABLE t1 CASCADE RESTRICT");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'FZZ'.",
            "DROP FUNCTION IF EXISTS fzz() PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PZZ'.",
            "DROP PROCEDURE IF EXISTS pzz() PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SZZ'.",
            "DROP STREAM IF EXISTS szz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'KZZ'.",
            "DROP TASK IF EXISTS kzz PURGE");
        assertAnswer(
            "Drop statement executed successfully (FFZ already dropped).",
            "DROP FILE FORMAT IF EXISTS ffz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TGZ'.",
            "DROP TAG IF EXISTS tgz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'MVZ'.",
            "DROP MATERIALIZED VIEW IF EXISTS mvz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'DTZ'.",
            "DROP DYNAMIC TABLE IF EXISTS dtz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'ETZ'.",
            "DROP EXTERNAL TABLE IF EXISTS etz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'MPZ'.",
            "DROP MASKING POLICY IF EXISTS mpz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'RPZ'.",
            "DROP ROW ACCESS POLICY IF EXISTS rpz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PPZ'.",
            "DROP PIPE IF EXISTS ppz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SQZ'.",
            "DROP SEQUENCE IF EXISTS sqz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'VWZ'.",
            "DROP VIEW IF EXISTS vwz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SCZ'.",
            "DROP SCHEMA IF EXISTS scz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SGZ'.",
            "DROP STAGE IF EXISTS sgz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'ICZ'.",
            "DROP ICEBERG TABLE IF EXISTS icz PURGE");
        assertAnswer(
            "Drop statement executed successfully (TZ already dropped).",
            "DROP TABLE IF EXISTS tz RESTRICT");
        assertAnswer(
            "Drop statement executed successfully (VWZ already dropped).",
            "DROP VIEW IF EXISTS vwz CASCADE");
        assertAnswer(
            "Drop statement executed successfully (SQZ already dropped).",
            "DROP SEQUENCE IF EXISTS sqz CASCADE");
        assertAnswer(
            "Drop statement executed successfully (FZZ already dropped).",
            "DROP FUNCTION IF EXISTS fzz() CASCADE");
        assertAnswer(
            "",
            "SHOW SEQUENCES LIKE 'SQ'");
        assertAnswer(
            "",
            "SHOW STAGES LIKE 'SG'");
    }

    @Test
    public void theNameTheSentenceShows() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE TABLE tp (x INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vp AS SELECT 1 AS c");
        engine.execute(
            "CREATE OR REPLACE SEQUENCE sq");
        engine.execute(
            "CREATE OR REPLACE STAGE sg");
        engine.execute(
            "CREATE OR REPLACE FILE FORMAT ffp TYPE = CSV");
        engine.execute(
            "CREATE OR REPLACE TABLE \"tp q\" (x INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fp() RETURNS INT AS '1'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pp() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute(
            "CREATE OR REPLACE STREAM stp ON TABLE tp");
        assertAnswer(
            "TEST_SCHEMA",
            "SELECT CURRENT_SCHEMA()");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SQ'.",
            "DROP SEQUENCE sq PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SG'.",
            "DROP STAGE sg PURGE");
        assertAnswer(
            "FFP successfully dropped.",
            "DROP FILE FORMAT ffp PURGE");
        assertAnswer(
            "Drop statement executed successfully (FFP already dropped).",
            "DROP FILE FORMAT IF EXISTS ffp PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.FFP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.FFP.",
            "DROP FILE FORMAT ffp");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TEST_SCHEMA.TP'.",
            "DROP TABLE test_schema.tp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TEST_DB.TEST_SCHEMA.TP'.",
            "DROP TABLE test_db.test_schema.tp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: '\"tp q\"'.",
            "DROP TABLE \"tp q\" PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE \"TP\" PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: '\"nosuch q\"'.",
            "DROP TABLE IF EXISTS \"nosuch q\" PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_Q' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_Q.",
            "DROP TABLE IF EXISTS nosuch_db_q.s.t PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP TABLE IF EXISTS test_db.nosuch_s.t PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_Q' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_Q.",
            "DROP TABLE nosuch_db_q.s.t PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'FP'.",
            "DROP FUNCTION fp() PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PP'.",
            "DROP PROCEDURE pp() PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'STP'.",
            "DROP STREAM stp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TEST_SCHEMA.VP'.",
            "DROP VIEW test_schema.vp PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_DB_ZZQ_PROBE'.",
            "DROP DATABASE IF EXISTS nosuch_db_zzq_probe PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE tp purge");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE IDENTIFIER('tp') PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'FZZ'.",
            "DROP FUNCTION IF EXISTS fzz(INT) PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'ALZ'.",
            "DROP ALERT IF EXISTS alz PURGE");
        assertAnswer(
            "Drop statement executed successfully (SCZ already dropped).",
            "DROP SECRET IF EXISTS scz PURGE");
        assertAnswer(
            "Drop statement executed successfully (NRZ already dropped).",
            "DROP NETWORK RULE IF EXISTS nrz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PPZ'.",
            "DROP PASSWORD POLICY IF EXISTS ppz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'CSZ'.",
            "DROP CORTEX SEARCH SERVICE IF EXISTS csz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'SLZ'.",
            "DROP STREAMLIT IF EXISTS slz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NBZ'.",
            "DROP NOTEBOOK IF EXISTS nbz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'PJZ'.",
            "DROP PROJECTION POLICY IF EXISTS pjz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'AGZ'.",
            "DROP AGGREGATION POLICY IF EXISTS agz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'JPZ'.",
            "DROP JOIN POLICY IF EXISTS jpz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'MDZ'.",
            "DROP MODEL IF EXISTS mdz PURGE");
        assertAnswer(
            "Drop statement executed successfully (CTZ already dropped).",
            "DROP CONTACT IF EXISTS ctz PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FUNCTION'.",
            "DROP EXTERNAL FUNCTION IF EXISTS efz() PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "DROP TABLE IF EXISTS tz PURGE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'CASCADE'.",
            "DROP TABLE IF EXISTS tz (a) PURGE CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','.",
            "DROP TABLE tp, tz PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE tp PURGE;");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "DROP TABLE tp /* c */ PURGE");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL Compilation "
                + "Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables in Catalog-Linked "
                + "Databases. Table: 'TP'.",
            "BEGIN DROP TABLE tp PURGE; END");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TP'.",
            "EXECUTE IMMEDIATE 'DROP TABLE tp PURGE'");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE tp");
        assertAnswer(
            "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vp");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE \"tp q\"");
        assertAnswer(
            "SQ successfully dropped.",
            "DROP SEQUENCE sq");
        assertAnswer(
            "SG successfully dropped.",
            "DROP STAGE sg");
    }

    @Test
    public void aMissingObjectAndAnotherKind() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_Q' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_Q.",
            "DROP TABLE IF EXISTS nosuch_db_q.s.t");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DROP TABLE IF EXISTS test_db.nosuch_s.t");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'.",
            "DROP VIEW IF EXISTS FZ PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.FZ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.FZ.",
            "DROP SEQUENCE FZ PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'RESTRICT'.",
            "DROP TABLE FZ PURGE RESTRICT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'PURGE'.",
            "DROP TABLE FZ RESTRICT PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'TEST_DB.NOSUCH_SCHEMA_ZQ'.",
            "DROP SCHEMA IF EXISTS test_db.nosuch_schema_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_ZQ' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_SCHEMA_ZQ.",
            "DROP SCHEMA nosuch_schema_zq PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_FN_ZQ'.",
            "DROP FUNCTION IF EXISTS nosuch_fn_zq(INT, VARCHAR) PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_FN_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_FN_ZQ.",
            "DROP FUNCTION nosuch_fn_zq(INT) PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH_PR_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PR_ZQ.",
            "DROP PROCEDURE nosuch_pr_zq() PURGE");
        assertAnswer(
            "REFUSED SQL Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables "
                + "in Catalog-Linked Databases. Table: 'NOSUCH_TAG_ZQ'.",
            "DROP TAG IF EXISTS nosuch_tag_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP_ZQ.",
            "DROP MASKING POLICY nosuch_mp_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.NOSUCH_FF_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_FF_ZQ.",
            "DROP FILE FORMAT nosuch_ff_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_SEC_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEC_ZQ.",
            "DROP SECRET nosuch_sec_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP_ZQ' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PP_ZQ.",
            "DROP PASSWORD POLICY nosuch_pp_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_NB_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NOTEBOOK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_NB_ZQ.",
            "DROP NOTEBOOK nosuch_nb_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL_ZQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_AL_ZQ.",
            "DROP ALERT nosuch_al_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_DT_ZQ' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DT_ZQ.",
            "DROP DYNAMIC TABLE nosuch_dt_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV_ZQ' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV_ZQ.",
            "DROP MATERIALIZED VIEW nosuch_mv_zq PURGE");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZZQ_PROBE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_DB_ZZQ_PROBE.",
            "DROP DATABASE nosuch_db_zzq_probe PURGE");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL Compilation "
                + "Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables in Catalog-Linked "
                + "Databases. Table: 'FZ'.",
            "BEGIN DROP TABLE FZ PURGE; RETURN 1; END");
    }
}
