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
 * A virtual column's rules beside an ALTER, a CREATE OR ALTER and a text's width (live-verified): CREATE OR ALTER with
 * a virtual column is an unsupported feature, a virtual column takes no NOT NULL, a rename of a column it reads is
 * followed in its expression, a dropped column it reads is refused, and a text column written with no length takes a
 * text expression of any width, where one written with a length is judged by it.
 */
public class VirtualColumnAlterAndWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void virtualColumnsBesideAlterAndCreateOrAlter() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE vc912 (a INT, b INT AS (1 / a))",
                "REFUSED SQL compilation error: error line 1 at position 38 | Data type of virtual column does not "
                + "match the data type of its expression for column 'B'. Expected data type 'NUMBER(38,0)', found "
                + "'NUMBER(7,6)'"},
            {"INSERT INTO vc912 (a) VALUES (0)",
                "REFUSED SQL compilation error: | Table 'VC912' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VC912."},
            {"SELECT a FROM vc912",
                "REFUSED SQL compilation error: | Object 'VC912' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VC912."},
            {"SELECT b FROM vc912",
                "REFUSED SQL compilation error: | Object 'VC912' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VC912."},
            {"SELECT COUNT(*) FROM vc912",
                "REFUSED SQL compilation error: | Object 'VC912' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VC912."},
            {"SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'VC912' ORDER BY 1", ""},
            {"SELECT \"KIND\", \"EXPRESSION\" FROM information_schema.columns WHERE table_name = 'VC912' ORDER BY 1",
                ""},
            {"CREATE OR ALTER TABLE vc912 (a INT, b INT AS (1 / a))",
                "REFUSED Unsupported feature 'CREATE OR ALTER with columns defined using 'AS <expr>''."},
            {"CREATE OR REPLACE TABLE vc912b (i INT, j INT AS (i + 1))", "Table VC912B successfully created."},
            {"ALTER TABLE vc912b MODIFY COLUMN i SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column I from type NUMBER(38,0) to VARCHAR(16777216) | "},
            {"ALTER TABLE vc912b ALTER COLUMN j SET DEFAULT 5",
                "REFUSED Unsupported feature 'Alter Column Set Default'."},
            {"ALTER TABLE vc912b ALTER COLUMN j SET NOT NULL",
                "REFUSED SQL compilation error: Cannot set NOT NULL constraint on virtual column J."},
            {"ALTER TABLE vc912b RENAME COLUMN i TO i2", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'vc912b')",
                "create or replace TABLE VC912B ( | \tI2 NUMBER(38,0), | \tJ NUMBER(38,0) AS (I2 + 1) | );"},
            {"ALTER TABLE vc912b DROP COLUMN i2",
                "REFUSED SQL compilation error: | Cannot drop column 'I2' because virtual column 'J' depends on it"},
            {"CREATE OR REPLACE TABLE vc912c (a INT, b INT AS (SUM(a) OVER ()))",
                "REFUSED SQL compilation error: | Window function [SUM(VC912C.A) OVER ()] appears outside of "
                + "SELECT, QUALIFY, and ORDER BY clauses."},
            {"CREATE OR REPLACE TABLE vc912c (a INT, b INT AS (sum(a)))",
                "REFUSED SQL compilation error: error line 1 at position 49 |  Invalid usage of sum aggregate "
                + "function in B virtual column definition."},
            {"CREATE OR REPLACE TABLE vc912c (a INT, b INT AS ((SELECT 1)))",
                "REFUSED SQL compilation error: error line 1 at position 39 |  Invalid usage of subquery in B "
                + "virtual column definition."},
            {"CREATE OR REPLACE TABLE vc912c (a INT DEFAULT 1, b INT AS (a + 1))",
                "Table VC912C successfully created."},
            {"CREATE OR REPLACE TABLE vc912c (s VARCHAR(10), j VARCHAR(20) AS (UPPER(s)))",
                "REFUSED SQL compilation error: error line 1 at position 47 | Data type of virtual column does not "
                + "match the data type of its expression for column 'J'. Expected data type 'VARCHAR(20)', found "
                + "'VARCHAR(30)'"},
            {"CREATE OR REPLACE TABLE vc912c (s VARCHAR(10), j VARCHAR(30) AS (UPPER(s)))",
                "Table VC912C successfully created."},
            {"CREATE OR REPLACE TABLE vc912c (a VARCHAR, b VARCHAR, c VARCHAR AS (a || b))",
                "Table VC912C successfully created."},
            {"CREATE OR REPLACE TABLE vc912c (x FLOAT, f NUMBER(10,2) AS (x))",
                "REFUSED SQL compilation error: error line 1 at position 41 | Data type of virtual column does not "
                + "match the data type of its expression for column 'F'. Expected data type 'NUMBER(10,2)', found "
                + "'FLOAT'"},
            {"CREATE OR REPLACE TABLE vc912c (d DATE, t TIMESTAMP_NTZ AS (d))",
                "REFUSED SQL compilation error: error line 1 at position 40 | Data type of virtual column does not "
                + "match the data type of its expression for column 'T'. Expected data type 'TIMESTAMP_NTZ(9)', "
                + "found 'DATE'"},
            {"CREATE OR REPLACE TABLE vc912c (a INT, u VARCHAR AS (CURRENT_USER()))",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Invalid usage of CURRENT_USER non "
                + "deterministic function in U virtual column definition."},
            {"CREATE OR REPLACE TEMPORARY TABLE vc912t (a INT, b INT AS (a + 1))",
                "Table VC912T successfully created."},
            {"CREATE OR REPLACE TRANSIENT TABLE vc912r (a INT, b INT AS (a + 1))",
                "Table VC912R successfully created."},
            {"INSERT INTO vc912r (a) VALUES (1)", "1"},
            {"CREATE OR REPLACE TABLE vc912k CLONE vc912r",
                "REFUSED SQL compilation error: Transient object cannot be cloned to a permanent object."},
            {"SELECT a, b FROM vc912k",
                "REFUSED SQL compilation error: | Object 'VC912K' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VC912K."},
        });
    }

    @Test
    public void aTextWithoutALengthTakesAnyWidth() {
        engine.execute("CREATE OR REPLACE TABLE w913 (a VARCHAR, b VARCHAR, s VARCHAR(10), m VARCHAR(16777216))");
        engine.execute("INSERT INTO w913 VALUES ('x', 'y', 'z', 'q')");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a || b), SYSTEM$TYPEOF(UPPER(a)), SYSTEM$TYPEOF(a || s), SYSTEM$TYPEOF(m || "
                + "m) FROM w913",
                "VARCHAR(33554432)[LOB]|VARCHAR(50331648)[LOB]|VARCHAR(16777226)[LOB]|VARCHAR(33554432)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(s || s), SYSTEM$TYPEOF(UPPER(s)), SYSTEM$TYPEOF(CONCAT(a, b)) FROM w913",
                "VARCHAR(20)[LOB]|VARCHAR(30)[LOB]|VARCHAR(33554432)[LOB]"},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR, c VARCHAR AS (UPPER(a)))", "Table W913B successfully created."},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR, c VARCHAR(100) AS (a || 'x'))",
                "REFUSED SQL compilation error: error line 1 at position 42 | Data type of virtual column does not "
                + "match the data type of its expression for column 'C'. Expected data type 'VARCHAR(100)', found "
                + "'VARCHAR(16777217)'"},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR(10), b VARCHAR(10), c VARCHAR(19) AS (a || b))",
                "REFUSED SQL compilation error: error line 1 at position 61 | Data type of virtual column does not "
                + "match the data type of its expression for column 'C'. Expected data type 'VARCHAR(19)', found "
                + "'VARCHAR(20)'"},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR(10), b VARCHAR(10), c VARCHAR(20) AS (a || b))",
                "Table W913B successfully created."},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR, b VARCHAR, c VARCHAR(16777216) AS (a || b))",
                "REFUSED SQL compilation error: error line 1 at position 53 | Data type of virtual column does not "
                + "match the data type of its expression for column 'C'. Expected data type 'VARCHAR(16777216)', "
                + "found 'VARCHAR(33554432)'"},
            {"CREATE OR REPLACE TABLE w913b (a VARCHAR, b VARCHAR, c VARCHAR(134217728) AS (a || b))",
                "Table W913B successfully created."},
            {"SELECT SYSTEM$TYPEOF(['a']::ARRAY(VARCHAR)), SYSTEM$TYPEOF({'a':1}::MAP(VARCHAR, INT))",
                "ARRAY(VARCHAR)[LOB]|MAP(VARCHAR, NUMBER(38,0))[LOB]"},
        });
    }
}
