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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * What GET_DDL keeps of an object's settings: a materialized view's column comments, a task's session parameters,
 * each on its own line, and the collation a table's, a schema's DEFAULT_DDL_COLLATION gave a string column when the
 * column was created; while a table's or a schema's retention, change tracking, extension and schema evolution are
 * not written (live-verified).
 */
public class GetDdlKeptPropertiesTest extends BaseDatabaseTest {

    @Test
    public void aMaterializedViewKeepsItsColumnComments() {
        engine.execute("CREATE TABLE b (k INT, v INT)");
        assertCells(engine, new String[][] {
            {"CREATE MATERIALIZED VIEW mv (k, total COMMENT 'sum') AS SELECT k, SUM(v) FROM b GROUP BY k",
                "Materialized view MV successfully created."},
            {"SELECT GET_DDL('MATERIALIZED_VIEW', 'mv')", "create or replace materialized view MV(\n\tK,\n\tTOTAL "
                + "COMMENT 'sum'\n) as SELECT k, SUM(v) FROM b GROUP BY k;"},
            {"CREATE MATERIALIZED VIEW mv2 (k COMMENT 'key', total) COMMENT = 'mvc' AS SELECT k, SUM(v) FROM b "
                + "GROUP BY k", "Materialized view MV2 successfully created."},
            {"SELECT GET_DDL('MATERIALIZED_VIEW', 'mv2')", "create or replace materialized view MV2(\n\tK COMMENT "
                + "'key',\n\tTOTAL\n) COMMENT='mvc'\n as SELECT k, SUM(v) FROM b GROUP BY k;"},
        });
    }

    @Test
    public void aTaskKeepsItsSessionParameters() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE TASK tk2 SCHEDULE = '60 MINUTE' TIMEZONE = 'Asia/Tokyo' ERROR_ON_NONDETERMINISTIC_MERGE = "
                    + "FALSE QUERY_TAG = 'q' AS SELECT 1", "Task TK2 successfully created."},
                {"SELECT GET_DDL('TASK', 'tk2')", "create or replace task TK2\n\tschedule='60 MINUTE'\n\t"
                    + "ERROR_ON_NONDETERMINISTIC_MERGE=false\n\tQUERY_TAG='q'\n\tTIMEZONE='Asia/Tokyo'\n\t"
                    + "as SELECT 1;"},
                {"CREATE TASK tk3 SCHEDULE = '60 MINUTE' USER_TASK_TIMEOUT_MS = 60000 SUSPEND_TASK_AFTER_NUM_FAILURES "
                    + "= 3 COMMENT = 'c' TIMEZONE = 'UTC' AS SELECT 1", "Task TK3 successfully created."},
                {"SELECT GET_DDL('TASK', 'tk3')", "create or replace task TK3\n\tschedule='60 MINUTE'\n\t"
                    + "SUSPEND_TASK_AFTER_NUM_FAILURES=3\n\tUSER_TASK_TIMEOUT_MS=60000\n\tTIMEZONE='UTC'\n\t"
                    + "COMMENT='c'\n\tas SELECT 1;"},
                {"CREATE TASK tk4 SCHEDULE = '60 MINUTE' AS SELECT 1", "Task TK4 successfully created."},
                {"ALTER TASK tk4 SET TIMEZONE = 'Europe/Warsaw'", "Statement executed successfully."},
                {"ALTER TASK tk4 SET DATE_INPUT_FORMAT = 'YYYY-MM-DD' TIMESTAMP_OUTPUT_FORMAT = 'YYYY'",
                    "Statement executed successfully."},
                {"SELECT GET_DDL('TASK', 'tk4')", "create or replace task TK4\n\tschedule='60 MINUTE'\n\t"
                    + "TIMESTAMP_OUTPUT_FORMAT='YYYY'\n\tDATE_INPUT_FORMAT='YYYY-MM-DD'\n\tTIMEZONE='Europe/Warsaw'\n\t"
                    + "as SELECT 1;"},
                {"ALTER TASK tk4 UNSET TIMEZONE", "Statement executed successfully."},
                {"CREATE TASK tkd SCHEDULE = '60 MINUTE' QUERY_TAG = 'it''s' AS SELECT 1",
                    "Task TKD successfully created."},
                {"SELECT GET_DDL('TASK', 'tkd')", "create or replace task TKD\n\tschedule='60 MINUTE'\n\t"
                    + "QUERY_TAG='it's'\n\tas SELECT 1;"},
            });
        } finally {
            engine.execute("DROP TASK IF EXISTS tk2");
            engine.execute("DROP TASK IF EXISTS tk3");
            engine.execute("DROP TASK IF EXISTS tk4");
            engine.execute("DROP TASK IF EXISTS tkd");
        }
    }

    @Test
    public void aStringColumnKeepsTheDefaultCollationItWasCreatedWith() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE TABLE tl2 (a VARCHAR, b VARCHAR COLLATE 'de', c INT, d STRING, e VARCHAR COLLATE '', "
                    + "f VARIANT) DEFAULT_DDL_COLLATION = 'en-ci'", "Table TL2 successfully created."},
                {"SELECT GET_DDL('TABLE', 'tl2')", "create or replace TABLE TL2 (\n\tA VARCHAR(16777216) COLLATE "
                    + "'en-ci',\n\tB VARCHAR(16777216) COLLATE 'de',\n\tC NUMBER(38,0),\n\tD VARCHAR(16777216) "
                    + "COLLATE 'en-ci',\n\tE VARCHAR(16777216) COLLATE 'en-ci',\n\tF VARIANT\n);"},
                {"CREATE TABLE tl3 (a VARCHAR(10))", "Table TL3 successfully created."},
                {"ALTER TABLE tl3 SET DEFAULT_DDL_COLLATION = 'fr'", "Statement executed successfully."},
                {"ALTER TABLE tl3 ADD COLUMN b CHAR(3)", "Statement executed successfully."},
                {"SELECT GET_DDL('TABLE', 'tl3')", "create or replace TABLE TL3 (\n\tA VARCHAR(10),\n\tB VARCHAR(3) "
                    + "COLLATE 'fr'\n);"},
                {"CREATE TABLE tl5 DEFAULT_DDL_COLLATION = 'en-ci' AS SELECT 'x'::VARCHAR AS a",
                    "Table TL5 successfully created."},
                {"SELECT GET_DDL('TABLE', 'tl5')", "create or replace TABLE TL5 (\n\tA VARCHAR(16777216)\n);"},
                {"CREATE SCHEMA test_db.fk_cs DEFAULT_DDL_COLLATION = 'en-ci'", "Schema FK_CS successfully created."},
                {"CREATE TABLE test_db.fk_cs.t (a VARCHAR)", "Table T successfully created."},
                {"CREATE TABLE test_db.fk_cs.t2 (a VARCHAR) DEFAULT_DDL_COLLATION = 'de'",
                    "Table T2 successfully created."},
                {"CREATE TABLE test_db.fk_cs.t3 (a VARCHAR) DEFAULT_DDL_COLLATION = ''",
                    "Table T3 successfully created."},
                {"SELECT GET_DDL('TABLE', 'test_db.fk_cs.t')",
                    "create or replace TABLE T (\n\tA VARCHAR(16777216) COLLATE 'en-ci'\n);"},
                {"SELECT GET_DDL('TABLE', 'test_db.fk_cs.t2')",
                    "create or replace TABLE T2 (\n\tA VARCHAR(16777216) COLLATE 'de'\n);"},
                {"SELECT GET_DDL('TABLE', 'test_db.fk_cs.t3')",
                    "create or replace TABLE T3 (\n\tA VARCHAR(16777216)\n);"},
            });
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("DROP SCHEMA IF EXISTS test_db.fk_cs");
        }
    }

    @Test
    public void aTablesAndASchemasParametersAreNotWritten() {
        try {
            assertCells(engine, new String[][] {
                {"CREATE TABLE t (a INT) DATA_RETENTION_TIME_IN_DAYS = 3 CHANGE_TRACKING = TRUE "
                    + "MAX_DATA_EXTENSION_TIME_IN_DAYS = 7 ENABLE_SCHEMA_EVOLUTION = TRUE",
                    "Table T successfully created."},
                {"SELECT GET_DDL('TABLE', 't')", "create or replace TABLE T (\n\tA NUMBER(38,0)\n);"},
                {"CREATE SCHEMA test_db.fk_ps DATA_RETENTION_TIME_IN_DAYS = 5", "Schema FK_PS successfully created."},
                {"SELECT GET_DDL('SCHEMA', 'test_db.fk_ps')", "create or replace schema FK_PS;\n"},
            });
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("DROP SCHEMA IF EXISTS test_db.fk_ps");
        }
    }
}
