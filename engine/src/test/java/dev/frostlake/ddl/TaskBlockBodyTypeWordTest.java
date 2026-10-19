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
 * A task whose body is a block holding a word that names no type is created, and GET_DDL tells the body as
 * written (live-verified): in a column's place, after ADD COLUMN, in a CAST and as a LET's variable's type.
 */
public class TaskBlockBodyTypeWordTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockBodyIsStoredAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK tk5 SCHEDULE = '60 MINUTE' AS BEGIN CREATE TABLE t9 (x NOSUCHTYPE); END",
                "Task TK5 successfully created."},
            {"SELECT GET_DDL('TASK', 'tk5')",
                "create or replace task TK5 | 	schedule='60 MINUTE' | 	as BEGIN CREATE TABLE t9 (x NOSUCHTYPE); END;"},
            {"CREATE OR REPLACE TASK tk12 SCHEDULE = '60 MINUTE' AS BEGIN CREATE TABLE t9 (x NOSUCHTYPE(1, 2)); END",
                "Task TK12 successfully created."},
            {"CREATE OR REPLACE TASK tk13 SCHEDULE = '60 MINUTE' AS BEGIN ALTER TABLE t ADD COLUMN z NOSUCHTYPE; END",
                "Task TK13 successfully created."},
            {"CREATE OR REPLACE TASK tk14 SCHEDULE = '60 MINUTE' AS BEGIN SELECT CAST(1 AS NOSUCHTYPE); END",
                "Task TK14 successfully created."},
            {"CREATE OR REPLACE TASK tk15 SCHEDULE = '60 MINUTE' AS BEGIN LET v NOSUCHTYPE := 1; END",
                "Task TK15 successfully created."},
            {"CREATE OR REPLACE TASK tk16 SCHEDULE = '60 MINUTE' AS BEGIN CREATE TABLE t9 (x NOSUCHTYPE); RETURN 1; "
                + "END",
                "Task TK16 successfully created."},
            {"CREATE OR REPLACE TASK tk8 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected '='."},
            {"CREATE OR REPLACE TASK tk10 SCHEDULE = '60 MINUTE' AS SELECT a FROM t x WHERE a = 1 COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 84 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE TASK tk11 SCHEDULE = '60 MINUTE' AS SELECT 1 x COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'COMMENT'."},
            {"CREATE OR REPLACE TASK tk17 SCHEDULE = '60 MINUTE' AS SELECT 1 FROM t x y COMMENT = 'c'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'y'."},
            {"CREATE OR REPLACE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (TABLE t)) THEN SELECT 1",
                "Alert AL successfully created."},
            {"DROP ALERT IF EXISTS al", "AL successfully dropped."},
            {"BEGIN CREATE TABLE t9 (x INT) y; SELECT 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'."},
        });
    }
}
