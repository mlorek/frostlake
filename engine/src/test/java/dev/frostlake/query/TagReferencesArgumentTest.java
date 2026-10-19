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
 * The two arguments of TAG_REFERENCES and TAG_REFERENCES_ALL_COLUMNS. Each is a text, refused by its
 * parameter's name otherwise. The domain is matched whatever its case, an underscore standing for the blank
 * between its words, and as written otherwise - a blank beside it makes it unknown; a view, a materialized
 * view and an external table are refused in favour of TABLE, while a dynamic, an event and an Iceberg table
 * are domains of their own. A name of more parts than its kind has names nothing, and a missing object is
 * named as its kind names it: a column, a schema, a stage, a pipe and a password policy by the full path, a
 * table, a stream, a task and an alert as written, a function and a procedure by the argument's own text.
 * Every answer is live-verified.
 */
public class TagReferencesArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theNameAMissingObjectIsRefusedBy() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.T.NOSUCH_C' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t.nosuch_c', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.NOSUCH_C' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_c', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s', 'schema'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_ST' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ST.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_st', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type "
                + "TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_v', 'view'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_wh_w7', 'warehouse'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7', 'database'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_role_w7', 'role'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t.a', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.NOSUCH_T.A' does not exist or not "
                + "authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t.a', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.t', 'table'))");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'nosuch_domain'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_P' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_P.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_p', 'pipe'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'NOSUCH_SM' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on STREAM NOSUCH_SM.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_sm', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Task 'NOSUCH_TK' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TASK NOSUCH_TK.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_tk', 'task'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'nosuch_f' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_f', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'NOSUCH_AL' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ALERT NOSUCH_AL.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_al', 'alert'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value MATERIALIZED_VIEW for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_mv', 'materialized view'))");
        assertAnswer(
            "REFUSED Unknown domain: TABLE .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'TABLE '))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('', 'table'))");
        assertAnswer(
            "REFUSED Unknown domain: .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', ''))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument "
                + "[OBJECT_NAME] for function [TAG_REFERENCES]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(NULL, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument [DOMAIN] for "
                + "function [TAG_REFERENCES]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'T' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on TAG T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'nosuch_pr' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_pr', 'procedure'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PP.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_pp', 'password policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Integration 'NOSUCH_NI' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_NI.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_ni', 'integration'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'NOSUCH_NP' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on NETWORK POLICY NOSUCH_NP.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_np', 'network policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_dr', 'database role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.TEST_SCHEMA.NOSUCH_C' does not exist or "
                + "not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_c', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7.s.t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(1, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'DOMAIN'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('nosuch_t', 'table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type "
                + "TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'view'))");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'nosuch_domain'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('test_schema.nosuch_t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid identifier TAG_REFERENCES",
            "SELECT * FROM TABLE(TAG_REFERENCES('t', 'table'))");
    }

    @Test
    public void theDomainAndTheTypesOfTheArguments() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('tg', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOSUCH_TG' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TG.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_tg', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TG' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG NOSUCH_TG.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_tg', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary role "
                + "<ROLE> must have USAGE or any other privilege granted on DATABASE A.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d.e', 'column'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c', 'schema'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'database'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'warehouse'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value VIEW for argument OBJECT_TYPE. Please use object type "
                + "TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'view'))");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'nosuch_domain'))");
        assertAnswer(
            "REFUSED Unknown domain:  table.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', ' table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'Table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value MATERIALIZED_VIEW for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'materialized_view'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value MATERIALIZED_VIEW for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'Materialized View'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value EXTERNAL_TABLE for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'external table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'dynamic table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'event table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'iceberg table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('vw', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(1.5, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(TRUE, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'DOMAIN'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(CURRENT_DATE, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument "
                + "[OBJECT_NAME] for function [TAG_REFERENCES]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(NULL::VARCHAR, 'table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'ta' || 'ble'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(1, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(1, 'nosuch_domain'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument [DOMAIN] for "
                + "function [TAG_REFERENCES]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | too many arguments for function "
                + "[INFORMATION_SCHEMA] expected 2, got 3",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'table', 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument "
                + "[OBJECT_NAME] for function [TAG_REFERENCES]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES())");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d.e', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: role identifier 'A.B.C.D' has too many qualifiers",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'a.b.c.d' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'database role'))");
        assertAnswer(
            "REFUSED Unknown domain: TABLE .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'TABLE '))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'OBJECT_NAME'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS(1, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'DOMAIN'",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('a.b.c.d', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value MATERIALIZED_VIEW for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'materialized view'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'column'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'tag'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'schema'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('vw', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Table '\"\"' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE \"\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('', 'table'))");
        assertAnswer(
            "REFUSED Unknown domain: .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', ''))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument "
                + "[OBJECT_NAME] for function [TAG_REFERENCES_ALL_COLUMNS]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS(NULL, 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument [DOMAIN] for "
                + "function [TAG_REFERENCES_ALL_COLUMNS]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('nosuch_s.t', 'table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('nosuch_db_w7.s.t', 'table'))");
    }

    @Test
    public void theNameEachDomainSpells() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c', 'role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c', 'database role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'user'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'integration'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'network policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b', 'compute pool'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'task'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'pipe'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'alert'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'a.b.c.d' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'procedure'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'password policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value EXTERNAL_TABLE for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'external_table'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'dynamic_table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'DATABASE_ROLE'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'password_policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Network policy 'T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on NETWORK POLICY T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'network_policy'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('vw', 'dynamic table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t', 'dynamic table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t', 'event table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_t', 'iceberg table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_t', 'dynamic table'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_ST' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ST.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_st', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.x', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.NOSUCH_SM' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SM.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_sm', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.x', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_TK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TK.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_tk', 'task'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_AL.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_al', 'alert'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_P' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_P.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_p', 'pipe'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'test_schema.nosuch_f' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_f', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'nosuch_s.nosuch_f' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.nosuch_f', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'NoSuch_F' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('NoSuch_F', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function '\"NoSuch_F\"' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"NoSuch_F\"', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'nosuch_f(int)' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_f(int)', 'function'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'test_schema.nosuch_pr' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_pr', 'procedure'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7.nosuch_dr', 'database role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH_DR' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ROLE TEST_DB.NOSUCH_DR.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.nosuch_dr', 'database role'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stream '\"x y\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on STREAM \"x y\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"x y\"', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.\"x y\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.\"x y\".",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"x y\"', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Compute pool 'NOSUCH_CP' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on COMPUTE POOL NOSUCH_CP.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_cp', 'compute pool'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PP.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.nosuch_pp', 'password policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.x', 'password policy'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_s.x', 'tag'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7.s.x', 'stage'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W7.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7.s.x', 'stream'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'nosuch_db_w7.s.x' does not exist or not authorized.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db_w7.s.x', 'function'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema', 'schema'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db', 'database'))");
        assertAnswer(
            "REFUSED Unknown domain: COLUMN .",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t.a', 'COLUMN '))");
        assertAnswer(
            "REFUSED Unknown domain: TABLES.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t', 'TABLES'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'dynamic table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value EXTERNAL_TABLE for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'external table'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'database'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'warehouse'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'role'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'event table'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'iceberg table'))");
        assertAnswer(
            "REFUSED SQL compilation error: Invalid value MATERIALIZED_VIEW for argument OBJECT_TYPE. Please use "
                + "object type TABLE for all kinds of table-like objects.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'materialized_view'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('nosuch_t', 'dynamic table'))");
        assertAnswer(
            "REFUSED Unsupported feature 'Only table-like objects are currently supported'.",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'stage'))");
        assertAnswer(
            "",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'Table'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | too many arguments for function "
                + "[INFORMATION_SCHEMA] expected 2, got 3",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t', 'table', 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument [DOMAIN] for "
                + "function [TAG_REFERENCES_ALL_COLUMNS]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('t'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 419 | missing required argument "
                + "[OBJECT_NAME] for function [TAG_REFERENCES_ALL_COLUMNS]",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS())");
    }
}
