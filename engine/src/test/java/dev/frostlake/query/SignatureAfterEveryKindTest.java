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
 * A signature after the name of a DROP's or a DESCRIBE's object is read for every kind the account was asked
 * about - a sequence, a stream, a task, a pipe, a tag, a file format, a policy, an alert, a secret, a network
 * rule, a notebook, a database and the table kinds - and for none after a stage, where the parenthesis is the
 * statement's one line. DESCRIBE ICEBERG TABLE refuses a table that is no Iceberg table. Every answer is
 * live-verified.
 */
public class SignatureAfterEveryKindTest extends MeasuredCellsTestSupport {

    @Test
    public void theKindsThatReadOne() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                + "line 1 at position 33 unexpected ')'. | syntax error line 1 at position 37 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW mv ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'. | syntax error line 1 at position 33 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE dt ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'b'. | syntax error "
                + "line 1 at position 37 unexpected '<EOF>'.",
            "DESCRIBE MATERIALIZED VIEW mv (a b) x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TYPE'. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "DESCRIBE DYNAMIC TABLE dt (TYPE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW mv (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid parameter 'x'",
            "DESCRIBE DYNAMIC TABLE dt (a) x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'MV' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE MV.",
            "DESCRIBE MATERIALIZED VIEW mv (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.DT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.DT.",
            "DESCRIBE DYNAMIC TABLE dt (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "DESCRIBE MATERIALIZED VIEW mv (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "DESCRIBE DYNAMIC TABLE dt (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "DROP MATERIALIZED VIEW mv ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('.",
            "DROP DYNAMIC TABLE dt ((a) b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('.",
            "DROP ICEBERG TABLE it ((a) b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE EVENT TABLE t1 (a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE EVENT TABLE t1 (a, b)");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1 (a)");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1 (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE t1 (a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE EVENT TABLE t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax error "
                + "line 1 at position 27 unexpected ')'. | syntax error line 1 at position 31 unexpected ')'.",
            "DESCRIBE EVENT TABLE t1 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 29 unexpected ')'.",
            "DESCRIBE EVENT TABLE t1 (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TYPE'. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "DESCRIBE ICEBERG TABLE t1 (TYPE)");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'ICEBERG_TABLE'.",
            "DROP ICEBERG TABLE t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '('.",
            "DROP STAGE s ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('.",
            "DESCRIBE STAGE s ('x'); SELECT 1 x y");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SEC already dropped).",
            "DROP SECRET IF EXISTS nosuch_sec (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SEC already dropped).",
            "DROP SECRET IF EXISTS nosuch_sec ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'b'.",
            "DROP SECRET IF EXISTS nosuch_sec (a b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE EVENT TABLE t1 (INT)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_ICE already dropped).",
            "DROP ICEBERG TABLE IF EXISTS nosuch_ice (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'b'.",
            "DROP ICEBERG TABLE IF EXISTS nosuch_ice (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '('.",
            "DROP STAGE IF EXISTS nosuch_stage (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '('.",
            "DROP STAGE IF EXISTS nosuch_stage ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "DESCRIBE STAGE nosuch_stage (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_ET already dropped).",
            "DROP EXTERNAL TABLE IF EXISTS nosuch_et (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_DT already dropped).",
            "DROP DYNAMIC TABLE IF EXISTS nosuch_dt (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_MV already dropped).",
            "DROP MATERIALIZED VIEW IF EXISTS nosuch_mv (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SCH already dropped).",
            "DROP SCHEMA IF EXISTS nosuch_sch (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_DB_ZZQ_PROBE already dropped).",
            "DROP DATABASE IF EXISTS nosuch_db_zzq_probe (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SEQ already dropped).",
            "DROP SEQUENCE IF EXISTS nosuch_seq (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_STR already dropped).",
            "DROP STREAM IF EXISTS nosuch_str (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_TASK already dropped).",
            "DROP TASK IF EXISTS nosuch_task (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PIPE already dropped).",
            "DROP PIPE IF EXISTS nosuch_pipe (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_TAG already dropped).",
            "DROP TAG IF EXISTS nosuch_tag (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_FF already dropped).",
            "DROP FILE FORMAT IF EXISTS nosuch_ff (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_AL already dropped).",
            "DROP ALERT IF EXISTS nosuch_al (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_MP already dropped).",
            "DROP MASKING POLICY IF EXISTS nosuch_mp (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_RP already dropped).",
            "DROP ROW ACCESS POLICY IF EXISTS nosuch_rp (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_NR already dropped).",
            "DROP NETWORK RULE IF EXISTS nosuch_nr (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_PP already dropped).",
            "DROP PASSWORD POLICY IF EXISTS nosuch_pp (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_NB already dropped).",
            "DROP NOTEBOOK IF EXISTS nosuch_nb (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SL already dropped).",
            "DROP STREAMLIT IF EXISTS nosuch_sl (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_CS already dropped).",
            "DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_cs (a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_V already dropped).",
            "DROP VIEW IF EXISTS nosuch_v (a)");
        assertAnswer(
            "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vv (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Sequence 'TEST_DB.TEST_SCHEMA.NOSUCH_SEQ' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SEQUENCE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEQ.",
            "DESCRIBE SEQUENCE nosuch_seq (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'NOSUCH_STR' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on STREAM NOSUCH_STR.",
            "DESCRIBE STREAM nosuch_str (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_TASK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TASK.",
            "DESCRIBE TASK nosuch_task (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_PIPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PIPE.",
            "DESCRIBE PIPE nosuch_pipe (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_ZQ' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_ZQ.",
            "DESCRIBE WAREHOUSE nosuch_wh_zq (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_AL.",
            "DESCRIBE ALERT nosuch_al (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Secret 'TEST_DB.TEST_SCHEMA.NOSUCH_SEC' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on SECRET "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SEC.",
            "DESCRIBE SECRET nosuch_sec (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PP.",
            "DESCRIBE PASSWORD POLICY nosuch_pp (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Notebook 'TEST_DB.TEST_SCHEMA.NOSUCH_NB' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on NOTEBOOK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_NB.",
            "DESCRIBE NOTEBOOK nosuch_nb (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Streamlit 'TEST_DB.TEST_SCHEMA.NOSUCH_SL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAMLIT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SL.",
            "DESCRIBE STREAMLIT nosuch_sl (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CS' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.NOSUCH_CS.",
            "DESCRIBE CORTEX SEARCH SERVICE nosuch_cs (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'A'.",
            "DESCRIBE PROCEDURE nosuch_p (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'A'.",
            "DESCRIBE FUNCTION nosuch_f (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'A'.",
            "DROP FUNCTION IF EXISTS nosuch_f (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'b'.",
            "DROP PROCEDURE IF EXISTS nosuch_p (a b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'VIEW'.",
            "DROP SECRET IF EXISTS nosuch_sec (VIEW)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_SEC already dropped).",
            "DROP SECRET IF EXISTS nosuch_sec (a) CASCADE");
        assertAnswer(
            "REFUSED SQL Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use "
                + "DESCRIBE ICEBERG TABLE command.",
            "DESCRIBE ICEBERG TABLE t1 TYPE = COLUMNS");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE EVENT TABLE t1 (a) TYPE = COLUMNS");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1");
    }
}
