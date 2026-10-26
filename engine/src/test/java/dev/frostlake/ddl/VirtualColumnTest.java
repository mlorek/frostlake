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
 * A column defined {@code AS (expr)} is virtual (live-verified): its value is computed from its row, a positional
 * INSERT fills the columns around it, and no statement names it as a target. DESCRIBE TABLE, SHOW COLUMNS and GET_DDL
 * tell its expression back as the plan prints it, and CREATE TABLE judges the definition as it compiles: its type
 * against the column's, no aggregate, no unknown name, no NOT NULL.
 */
public class VirtualColumnTest extends MeasuredCellsTestSupport {

    private static final String CREATED = "Table TP_V3 successfully created.";
    private static final String NOT_A_TARGET = "REFUSED SQL compilation error: | Virtual column 'B' is invalid target.";
    private static final String ERROR_AT = "REFUSED SQL compilation error: error line 1 at position ";

    @Test
    public void aVirtualColumnIsComputedFromItsRow() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tp_v3 (a INT, b INT AS (a + 1))", CREATED},
            {"INSERT INTO tp_v3 (a) VALUES (1)", "1"},
            {"SELECT a, b FROM tp_v3", "1|2"},
            {"INSERT INTO tp_v3 VALUES (5)", "1"},
            {"SELECT * FROM tp_v3 ORDER BY a", "1|2 ;; 5|6"},
            {"CREATE OR REPLACE TABLE tp_v6 (a INT, b INT GENERATED ALWAYS AS (a * 2) VIRTUAL)",
                "Table TP_V6 successfully created."},
            {"CREATE OR REPLACE TABLE tp_t2 (a INT AS (1))", "Table TP_T2 successfully created."},
        });
    }

    @Test
    public void aVirtualColumnIsNoTarget() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tp_v3 (a INT, b INT AS (a + 1))", CREATED},
            {"INSERT INTO tp_v3 (a, b) VALUES (1, 2)", NOT_A_TARGET},
            {"UPDATE tp_v3 SET b = 3", NOT_A_TARGET},
        });
    }

    @Test
    public void theExpressionIsToldBackAsThePlanPrintsIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tp_v3 (a INT, b INT AS (a + 1))", CREATED},
            {"DESCRIBE TABLE tp_v3", "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null"
                + " ;; B|NUMBER(38,0)|VIRTUAL|Y|null|N|N|null|A + 1|null|null|null|null"},
            {"SELECT GET_DDL('TABLE', 'tp_v3')",
                "create or replace TABLE TP_V3 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0) AS (A + 1) | );"},
            {"SHOW COLUMNS IN TABLE tp_v3", "TP_V3|TEST_SCHEMA|A|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,"
                + "\"nullable\":true}|true||COLUMN|||TEST_DB||null|null ;; TP_V3|TEST_SCHEMA|B|{\"type\":\"FIXED\","
                + "\"precision\":38,\"scale\":0,\"nullable\":true}|true||VIRTUAL_COLUMN|A + 1||TEST_DB||null|null"},
        });
    }

    @Test
    public void theDefinitionIsJudgedAsTheTableIsCreated() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tp_v4 (a INT, b INT AS (a + 1) NOT NULL)",
                "REFUSED SQL compilation error: Cannot set NOT NULL constraint on virtual column B."},
            {"CREATE OR REPLACE TABLE tp_v5 (a INT, b INT AS (a + 1) DEFAULT 1)", "Table TP_V5 successfully created."},
            {"CREATE OR REPLACE TABLE tp_v7 (a INT, b VARCHAR AS (a))", ERROR_AT + "38 | Data type of virtual column"
                + " does not match the data type of its expression for column 'B'. Expected data type"
                + " 'VARCHAR(16777216)', found 'NUMBER(38,0)'"},
            {"CREATE OR REPLACE TABLE tp_v8 (a INT, b INT AS (SUM(a)))",
                ERROR_AT + "48 |  Invalid usage of SUM aggregate function in B virtual column definition."},
            {"CREATE OR REPLACE TABLE tp_v9 (a INT, b INT AS (c + 1))", ERROR_AT + "48 | invalid identifier 'C'"},
            {"CREATE OR REPLACE TABLE tp_t (a INT AS (1), b VARCHAR(5,2))", "REFUSED SQL compilation error: | syntax"
                + " error line 1 at position 55 unexpected ','. | syntax error line 1 at position 56 unexpected '2'."
                + " | syntax error line 1 at position 58 unexpected ')'."},
        });
    }

    @Test
    public void aUserFunctionIsNoPartOfTheDefinition() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS '1'", "Function F1 successfully created."},
            {"CREATE OR REPLACE TABLE td18 (b NUMBER, a NUMBER AS (f1()))",
                ERROR_AT + "53 |  Invalid usage of f1 user defined function in A virtual column definition."},
        });
    }
}
