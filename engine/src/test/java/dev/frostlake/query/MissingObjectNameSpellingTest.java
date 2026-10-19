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
 * Which name a refusal gives an object that is not there. A stream's source table, a clone's source and the
 * table of a RENAME COLUMN are named as written when written bare; a DESCRIBE of a pipe, a file format, a
 * masking policy or a row access policy by the full path, and of a stream as written; SHOW COLUMNS IN SCHEMA
 * refuses a missing database before its schema; a DROP names the kind it was asked for - an Iceberg table, an
 * event table. Every answer is live-verified.
 */
public class MissingObjectNameSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void theNameAMissingObjectIsRefusedBy() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CREATE STREAM s ON TABLE nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_V' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_V.",
            "CREATE STREAM s ON VIEW nosuch_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "CREATE STREAM s ON TABLE test_schema.nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "CREATE STREAM s ON TABLE nosuch_s.nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_DT' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_DT.",
            "CREATE DYNAMIC TABLE dt2 CLONE nosuch_dt");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CREATE TABLE t3 CLONE nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CREATE TABLE t3 LIKE nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "ALTER TABLE nosuch_t RENAME COLUMN a TO b");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ADD COLUMN b INT");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t DROP COLUMN a");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t RENAME TO t9");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t SET COMMENT = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ALTER COLUMN a SET NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t CLUSTER BY (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "COMMENT ON TABLE nosuch_t IS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE t SWAP WITH nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE test_schema.nosuch_t RENAME COLUMN a TO b");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'NOSUCH_C' does not exist or not authorized.",
            "ALTER TABLE t RENAME COLUMN nosuch_c TO b");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'NOSUCH_X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on STREAM NOSUCH_X.",
            "DESCRIBE STREAM nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE PIPE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE FILE FORMAT nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE MASKING POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE ROW ACCESS POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE TASK nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE STAGE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE SEQUENCE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE DYNAMIC TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_X.",
            "DESCRIBE TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'NOSUCH_X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH_X.",
            "DESCRIBE VIEW nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'NOSUCH_X' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_X.",
            "DESCRIBE MATERIALIZED VIEW nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE EVENT TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE ALERT nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE SECRET nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE PASSWORD POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE AGGREGATION POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE PROJECTION POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_X' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_X.",
            "DESCRIBE SCHEMA nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.NOSUCH_F' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_F.",
            "DESCRIBE FUNCTION nosuch_f()");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH_P' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_P.",
            "DESCRIBE PROCEDURE nosuch_p()");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZZ' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZZ.",
            "DESCRIBE DATABASE nosuch_db_zz");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_ZZ' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_ZZ.",
            "DESCRIBE WAREHOUSE nosuch_wh_zz");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE STREAM test_schema.nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE PIPE test_schema.nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DESCRIBE TABLE test_schema.nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "DESCRIBE FILE FORMAT nosuch_s.nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SHOW COLUMNS IN SCHEMA nosuch_db_w7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SHOW COLUMNS IN DATABASE nosuch_db_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SHOW COLUMNS IN TABLE nosuch_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_S",
            "SHOW COLUMNS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA nosuch_db_w7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN SCHEMA nosuch_db_w7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SHOW STREAMS IN SCHEMA nosuch_db_w7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_V",
            "SHOW COLUMNS IN VIEW nosuch_v");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP ICEBERG TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP VIEW nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP STREAM nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP PIPE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP FILE FORMAT nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP MASKING POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP DYNAMIC TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | External table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP EXTERNAL TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP MATERIALIZED VIEW nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP SEQUENCE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP TASK nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP STAGE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP TAG nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP ALERT nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP ROW ACCESS POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP EVENT TABLE nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP PROJECTION POLICY nosuch_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X.",
            "DROP SECRET nosuch_x");
    }
}
