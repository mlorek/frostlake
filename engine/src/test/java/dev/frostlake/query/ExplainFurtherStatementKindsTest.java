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
 * EXPLAIN takes no PUT or GET; EXPLAIN CREATE TABLE over a view's name is refused as the plain CREATE is, and
 * EXPLAIN ALTER TABLE over one finds an object of another type; EXPLAIN DROP and DESCRIBE of a missing pipe, stream,
 * task, procedure, masking policy or tag are refused as the plain statements are, where an existing one, or a DROP
 * IF EXISTS of a missing one, is explained (all live-verified).
 */
public class ExplainFurtherStatementKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void furtherKindsAreJudgedAsThePlainStatement() {
        engine.execute("CREATE OR REPLACE STAGE es");
        engine.execute("CREATE OR REPLACE TABLE et (c INT)");
        engine.execute("CREATE OR REPLACE VIEW ev AS SELECT 1 AS c");
        engine.execute("CREATE OR REPLACE STREAM es_s ON TABLE et");
        engine.execute("CREATE OR REPLACE PROCEDURE ep() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE MASKING POLICY emp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE TAG etg");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'PUT'.",
            "EXPLAIN PUT file:///tmp/fl_nosuch_file.csv @es");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'PUT'.",
            "EXPLAIN PUT file:///tmp/fl_nosuch_file.csv @nosuch_es");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'GET'.",
            "EXPLAIN GET @es file:///tmp/");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'GET'.",
            "EXPLAIN GET @nosuch_es file:///tmp/");
        assertAnswer("REFUSED SQL compilation error: | Object 'EV' already exists as VIEW",
            "EXPLAIN CREATE TABLE ev (c INT)");
        assertAnswer("REFUSED SQL compilation error: | Object 'EV' already exists as VIEW",
            "EXPLAIN CREATE TABLE ev AS SELECT 1 AS c");
        assertAnswer("REFUSED SQL compilation error: | Object 'EV' already exists as VIEW",
            "EXPLAIN CREATE OR REPLACE TABLE ev (c INT)");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "EXPLAIN ALTER TABLE ev ADD COLUMN c INT");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_PIPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PIPE.",
            "EXPLAIN DROP PIPE nosuch_pipe");
        assertAnswer("REFUSED SQL compilation error: | Stream 'TEST_DB.TEST_SCHEMA.NOSUCH_STREAM' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STREAM "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_STREAM.",
            "EXPLAIN DROP STREAM nosuch_stream");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP STREAM es_s");
        assertAnswer("REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_TASK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TASK.",
            "EXPLAIN DROP TASK nosuch_task");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH_PROC' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PROC.",
            "EXPLAIN DROP PROCEDURE nosuch_proc()");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP PROCEDURE ep()");
        assertAnswer("REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "EXPLAIN DROP MASKING POLICY nosuch_mp");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP MASKING POLICY emp");
        assertAnswer("REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOSUCH_TAG' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TAG.",
            "EXPLAIN DROP TAG nosuch_tag");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP TAG etg");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NOSUCH_PIPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PIPE.",
            "EXPLAIN DESCRIBE PIPE nosuch_pipe");
        assertAnswer("REFUSED SQL compilation error: | Stream 'NOSUCH_STREAM' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on STREAM NOSUCH_STREAM.",
            "EXPLAIN DESCRIBE STREAM nosuch_stream");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE STREAM es_s");
        assertAnswer("REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH_TASK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TASK.",
            "EXPLAIN DESCRIBE TASK nosuch_task");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.NOSUCH_PROC' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PROC.",
            "EXPLAIN DESCRIBE PROCEDURE nosuch_proc()");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE PROCEDURE ep()");
        assertAnswer("REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "EXPLAIN DESCRIBE MASKING POLICY nosuch_mp");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DESCRIBE MASKING POLICY emp");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP STREAM IF EXISTS nosuch_stream");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN DROP TAG IF EXISTS nosuch_tag");
    }
}
