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
 * The rules of a virtual column: its expression reads the columns that are not virtual and the virtual ones before it,
 * holds no value but the row's - no function whose answer moves, refused where the call stands; no query, no session
 * variable and no column by its place, refused at the column's name; no bind, refused as one not set; no window,
 * refused as one outside the clauses that take it - and has the column's type, an exact number of the same scale and no
 * more digits. A column a virtual column reads stays while it does, the refusal naming every virtual column that reads
 * it. A CTAS's query and ALTER TABLE … ADD COLUMN fill the virtual column from the rows, and a MERGE writes around it
 * as an INSERT and an UPDATE do (all live-verified).
 */
public class VirtualColumnRulesTest extends MeasuredCellsTestSupport {

    private static final String AT = "REFUSED SQL compilation error: error line 1 at position ";

    private static final String AT_44 = AT + "44 |  Invalid usage of ";

    private static final String AT_34 = AT + "34 |  Invalid usage of ";

    private static final String DROP = "REFUSED SQL compilation error: | Cannot drop column ";

    @Test
    public void anExpressionReadsTheVirtualColumnsBeforeIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t (c NUMBER, c1 NUMBER AS (c + 1), c2 NUMBER AS (c1 + 1))",
                "Table T successfully created."},
            {"INSERT INTO t VALUES (1)", "1"},
            {"SELECT * FROM t", "1|2|3"},
            {"CREATE OR REPLACE TABLE t (c NUMBER, c1 NUMBER AS (c2 + 1), c2 NUMBER AS (c + 1))",
                "REFUSED SQL compilation error: error line 1 at position 51 | invalid identifier 'C2'"},
        });
    }

    @Test
    public void theColumnTakesTheTypeOfItsExpression() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t (i NUMBER(10,2), j NUMBER(5,0) AS (i))", "REFUSED SQL compilation error:"
                + " error line 1 at position 43 | Data type of virtual column does not match the data type of its"
                + " expression for column 'J'. Expected data type 'NUMBER(5,0)', found 'NUMBER(10,2)'"},
            {"CREATE OR REPLACE TABLE x (i INT, j INT AS (i * i))", "Table X successfully created."},
            {"INSERT INTO x VALUES (2)", "1"},
            {"SELECT * FROM x", "2|4"},
        });
    }

    @Test
    public void anExpressionReadsNoValueButItsRows() {
        assertCells(new String[][] {
            {"SET myvar = 1", "Statement executed successfully."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (RANDOM()))",
                AT_44 + "RANDOM non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS ((SELECT 1)))",
                AT_34 + "subquery in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (:1))",
                "REFUSED SQL compilation error: error line 1 at position 44 | Bind variable :1 not set."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS ($myvar))",
                AT_34 + "session variable in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS ($1))",
                AT_34 + "column positional reference in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (\n  i INT,\n  j INT AS ($1))",
                "REFUSED SQL compilation error: error line 3 at position 2 |  Invalid usage of column positional "
                    + "reference in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (ROW_NUMBER() OVER (ORDER BY i)))",
                "REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (ORDER BY T.I ASC NULLS LAST)] "
                    + "appears outside of SELECT, QUALIFY, and ORDER BY clauses."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (SUM(i) OVER ()))",
                "REFUSED SQL compilation error: | Window function [SUM(T.I) OVER ()] appears outside of SELECT, "
                    + "QUALIFY, and ORDER BY clauses."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2) CHECK (j > 0))",
                "REFUSED SQL compilation error: Cannot set CHECK constraint on virtual column J."},
        });
    }

    @Test
    public void aCallWhoseAnswerMovesIsRefusedWhereItStands() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t (i INT, j VARCHAR AS (UUID_STRING()))",
                AT + "48 |  Invalid usage of UUID_STRING non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j TIMESTAMP_LTZ AS (CURRENT_TIMESTAMP()))",
                AT + "54 |  Invalid usage of CURRENT_TIMESTAMP non deterministic function in J virtual column "
                    + "definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (SEQ4()))",
                AT_44 + "SEQ4 non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (UNIFORM(1, 10, RANDOM())))",
                AT + "59 |  Invalid usage of RANDOM non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i + RANDOM()))",
                AT + "48 |  Invalid usage of RANDOM non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j VARCHAR AS (CURRENT_USER()))",
                AT + "48 |  Invalid usage of CURRENT_USER non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j DATE AS (CURRENT_DATE))",
                AT + "45 |  Invalid usage of CURRENT_DATE non deterministic function in J virtual column definition."},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i), k INT AS (RANDOM()))",
                AT + "58 |  Invalid usage of RANDOM non deterministic function in K virtual column definition."},
        });
    }

    @Test
    public void aColumnAVirtualColumnReadsStays() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2))", "Table T successfully created."},
            {"ALTER TABLE t DROP COLUMN i", DROP + "'I' because virtual column 'J' depends on it"},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2), k INT AS (i + 1))", "Table T successfully created."},
            {"ALTER TABLE t DROP COLUMN i", DROP + "'I' because virtual columns 'J', 'K' depend on it"},
            {"CREATE OR REPLACE TABLE t (i INT, h INT, j INT AS (i * h))", "Table T successfully created."},
            {"ALTER TABLE t DROP COLUMN h", DROP + "'H' because virtual column 'J' depends on it"},
            {"ALTER TABLE t DROP COLUMN i, h", DROP + "'I' because virtual column 'J' depends on it"},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2), k INT AS (j + 1))", "Table T successfully created."},
            {"ALTER TABLE t DROP COLUMN j", DROP + "'J' because virtual column 'K' depends on it"},
            {"ALTER TABLE t DROP COLUMN i", DROP + "'I' because virtual column 'J' depends on it"},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2))", "Table T successfully created."},
            {"ALTER TABLE t DROP COLUMN j", "Statement executed successfully."},
            {"ALTER TABLE t DROP COLUMN i", "REFUSED SQL compilation error: | cannot drop all the columns of a table"},
            {"CREATE OR REPLACE TABLE t (i INT, j INT AS (i * 2))", "Table T successfully created."},
            {"ALTER TABLE t RENAME COLUMN i TO i2", "Statement executed successfully."},
        });
    }

    @Test
    public void aQueryAndAnAddedColumnFillTheVirtualColumn() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE today_sales (vendor_name VARCHAR(255), cost NUMBER(16,3), tax NUMBER(16,3)"
                + " AS ((cost * 0.075)::NUMBER(16,3))) AS SELECT 'Initech', 46",
                "Table TODAY_SALES successfully created."},
            {"SELECT * FROM today_sales", "Initech|46.000|3.450"},
            {"CREATE OR REPLACE TABLE x (i INT)", "Table X successfully created."},
            {"INSERT INTO x VALUES (2), (3)", "2"},
            {"ALTER TABLE x ADD COLUMN j INT AS (i * i)", "Statement executed successfully."},
            {"SELECT * FROM x ORDER BY i", "2|4 ;; 3|9"},
        });
    }

    @Test
    public void aMergeWritesAroundTheVirtualColumn() {
        final String notATarget = "REFUSED SQL compilation error: | Virtual column 'B' is invalid target.";
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE m (a INT, b INT AS (a * 3))", "Table M successfully created."},
            {"MERGE INTO m USING (SELECT 1 AS k) s ON m.a = s.k WHEN NOT MATCHED THEN INSERT VALUES (s.k)", "1"},
            {"MERGE INTO m USING (SELECT 1 AS k) s ON m.a = s.k WHEN MATCHED THEN UPDATE SET a = 7", "1"},
            {"SELECT * FROM m", "7|21"},
            {"MERGE INTO m USING (SELECT 7 AS k) s ON m.a = s.k WHEN MATCHED THEN UPDATE SET b = 1", notATarget},
            {"MERGE INTO m USING (SELECT 8 AS k) s ON m.a = s.k WHEN NOT MATCHED THEN INSERT (a, b) VALUES (s.k, 1)",
                notATarget},
        });
    }
}
