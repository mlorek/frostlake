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
 * A task's body is stored as written and judged when the task runs: a word in it that names no type — a column's, a
 * cast's, an added column's — refuses nothing at CREATE TASK, and GET_DDL tells the body as it was written
 * (live-verified).
 */
public class TaskBodyTypeWordTest extends MeasuredCellsTestSupport {

    @Test
    public void aTaskBodyIsStoredAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x NOSUCHTYPE)",
                "Task TK successfully created."},
            {"SELECT GET_DDL('TASK', 'tk')",
                "create or replace task TK | \tschedule='60 MINUTE' | \tas CREATE TABLE t9 (x NOSUCHTYPE);"},
            {"CREATE OR REPLACE TASK tk2 SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x NOSUCHTYPE(1, 2))",
                "Task TK2 successfully created."},
            {"CREATE OR REPLACE TASK tk3 SCHEDULE = '60 MINUTE' AS ALTER TABLE t ADD COLUMN z NOSUCHTYPE",
                "Task TK3 successfully created."},
            {"CREATE OR REPLACE TASK tk4 SCHEDULE = '60 MINUTE' AS SELECT CAST(1 AS NOSUCHTYPE)",
                "Task TK4 successfully created."},
            {"SELECT GET_DDL('TASK', 'tk4')",
                "create or replace task TK4 | \tschedule='60 MINUTE' | \tas SELECT CAST(1 AS NOSUCHTYPE);"},
            {"CREATE OR REPLACE TASK tk6 SCHEDULE = '60 MINUTE' AS TABLE t", "Task TK6 successfully created."},
            {"CREATE OR REPLACE TASK tk8 SCHEDULE = '60 MINUTE' AS BEGIN SELECT 1; END COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 81 unexpected '='."},
            {"CREATE OR REPLACE TASK tk9 SCHEDULE = '60 MINUTE' AS SELECT 1::NOSUCHTYPE",
                "Task TK9 successfully created."},
            {"CREATE OR REPLACE TASK tk10 SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x INT, y NOSUCH TYPE2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 87 unexpected 'TYPE2'."},
            {"CREATE OR REPLACE TASK tk11 SCHEDULE = '60 MINUTE' AS SELECT TRY_CAST('1' AS NOSUCHTYPE)",
                "Task TK11 successfully created."},
            {"CREATE OR REPLACE TASK tk12 SCHEDULE = '60 MINUTE' AS CREATE TABLE t9 (x VARCHAR(0))",
                "Task TK12 successfully created."},
        });
    }
}
