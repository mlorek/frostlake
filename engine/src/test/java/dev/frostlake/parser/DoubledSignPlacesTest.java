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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Two signs in a row are refused as an invalid function wherever a statement writes them (live-verified): a WHERE, a
 * HAVING, a select item over a missing table, a column DEFAULT, a view's query, a procedure's body as it is created -
 * its branch that never runs and a body written without a block too - and a SQL function's body, as its compilation
 * failing. A task's statement is not judged as the task is created or altered, a caret or a bar there neither, while
 * its WHEN condition is. Beside a caret or a bar, the operator a walk of the expression meets first, each operator
 * ahead of its operands, is the one refused.
 */
public class DoubledSignPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void signsOutsideSortAndGroupingKeys() {
        engine.execute("CREATE OR REPLACE TABLE t925 (a INT)");
        engine.execute("INSERT INTO t925 VALUES (1), (-1)");
        assertCells(new String[][] {
            {"SELECT a FROM t925 WHERE a = - -1", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT a FROM t925 GROUP BY a HAVING a = - -1", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT - -1 FROM no_such_table925", "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE TABLE dd925 (a INT DEFAULT - -1)",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE VIEW dv925 AS SELECT - -1 AS x",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE PROCEDURE dp925() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN - -1; END'",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE TASK dt925 SCHEDULE = '60 MINUTE' AS SELECT - -1", "Task DT925 successfully created."},
            {"SELECT 2 ^ 3 + - -1", "REFUSED Unsupported feature '^'."},
            {"SELECT - -1 + 2 ^ 3", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT a FROM t925 WHERE a = + +1", "REFUSED SQL compilation error: | invalid function '+'"},
            {"SELECT a FROM t925 WHERE a = -(-1)", "1"},
            {"SELECT a FROM t925 ORDER BY - -a", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT a FROM t925 WHERE a = - - 1", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT a FROM t925 WHERE a = -+1", "REFUSED SQL compilation error: | invalid function '-'"},
            {"UPDATE t925 SET a = - -1 WHERE FALSE", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT IFF(a = - -1, 'y', 'n') FROM no_such_table925",
                "REFUSED SQL compilation error: | invalid function '-'"},
        });
    }

    @Test
    public void tasksRoutinesAndOperatorOrder() {
        engine.execute("CREATE OR REPLACE TABLE t929 (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK dt929 SCHEDULE = '60 MINUTE' AS SELECT 2 ^ 3", "Task DT929 successfully created."},
            {"CREATE OR REPLACE TASK dt929 SCHEDULE = '60 MINUTE' AS SELECT 1 | 2", "Task DT929 successfully created."},
            {"ALTER TASK dt929 MODIFY AS SELECT - -1", "Statement executed successfully."},
            {"CREATE OR REPLACE TASK dt929 SCHEDULE = '60 MINUTE' WHEN - -1 = 1 AS SELECT 1",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT 1 | 2 + - -1", "REFUSED Unsupported feature '|'."},
            {"SELECT - -1 + (1 | 2)", "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT - -1 ^ 2", "REFUSED Unsupported feature '^'."},
            {"CREATE OR REPLACE FUNCTION df929() RETURNS INT AS '- -1'",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE PROCEDURE dp929() RETURNS INT LANGUAGE SQL AS 'BEGIN IF (FALSE) THEN RETURN - "
                + "-1; END IF; RETURN 1; END'",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE PROCEDURE dp929() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN - -1 + 2 ^ 3; END'",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE PROCEDURE dp929() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 2 ^ 3 + - -1; END'",
                "REFUSED Unsupported feature '^'."},
            {"CREATE OR REPLACE PROCEDURE dp929() RETURNS INT LANGUAGE SQL AS 'SELECT - -1'",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"CREATE OR REPLACE PROCEDURE dp929() RETURNS INT LANGUAGE JAVASCRIPT AS 'return - -1;'",
                "REFUSED Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type."},
            {"CREATE OR REPLACE VIEW dv929 AS SELECT 2 ^ 3 + - -1 AS x", "REFUSED Unsupported feature '^'."},
        });
    }
}
