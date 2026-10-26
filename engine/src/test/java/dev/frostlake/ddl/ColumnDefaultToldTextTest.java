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
 * A column DEFAULT told back as the account prints it, from the plan it compiled to: DESCRIBE's default cell,
 * GET_DDL and INFORMATION_SCHEMA.COLUMNS (all live-verified).
 */
public class ColumnDefaultToldTextTest extends MeasuredCellsTestSupport {

    @Test
    public void toldDefaults() {
        assertAnswer("Table DD01 successfully created.",
            "CREATE OR REPLACE TABLE dd01 (c TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP()::TIMESTAMP_NTZ)");
        assertAnswer("C|TIMESTAMP_NTZ(9)|COLUMN|Y|CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_NTZ(9))|N|N|null|null|null|"
                + "null|null|null",
            "DESCRIBE TABLE dd01");
        assertAnswer("Table DD02 successfully created.", "CREATE OR REPLACE TABLE dd02 (c INT DEFAULT 1 + 2)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|1 + 2|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd02");
        assertAnswer("Table DD03 successfully created.", "CREATE OR REPLACE TABLE dd03 (c VARCHAR DEFAULT UPPER('a'))");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|UPPER('a')|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd03");
        assertAnswer("Table DD04 successfully created.", "CREATE OR REPLACE TABLE dd04 (c VARCHAR DEFAULT 'a' || 'b')");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|'a' || 'b'|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd04");
        assertAnswer("Table DD05 successfully created.",
            "CREATE OR REPLACE TABLE dd05 (c INT DEFAULT CASE WHEN TRUE THEN 1 ELSE 2 END)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 1, 2)|N|N|null|null|null|null|"
                + "null|null",
            "DESCRIBE TABLE dd05");
        assertAnswer("Table DD06 successfully created.",
            "CREATE OR REPLACE TABLE dd06 (c DATE DEFAULT '2020-01-01'::DATE)");
        assertAnswer("C|DATE|COLUMN|Y|CAST('2020-01-01' AS DATE)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd06");
        assertAnswer("Table DD07 successfully created.", "CREATE OR REPLACE TABLE dd07 (c NUMBER(10,2) DEFAULT 1.5)");
        assertAnswer("C|NUMBER(10,2)|COLUMN|Y|1.5|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd07");
        assertAnswer("Table DD08 successfully created.",
            "CREATE OR REPLACE TABLE dd08 (c VARCHAR DEFAULT CAST(1 AS VARCHAR))");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|CAST(1 AS VARCHAR)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd08");
        assertAnswer("Table DD09 successfully created.", "CREATE OR REPLACE TABLE dd09 (c INT DEFAULT -1)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|-1|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd09");
        assertAnswer("Table DD11 successfully created.",
            "CREATE OR REPLACE TABLE dd11 (c VARCHAR DEFAULT CONCAT('a', 'b'))");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|CONCAT('a', 'b')|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd11");
        assertAnswer("Table DD12 successfully created.", "CREATE OR REPLACE TABLE dd12 (c INT DEFAULT ABS(-1))");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|ABS(-1)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd12");
        assertAnswer("Table DD13 successfully created.",
            "CREATE OR REPLACE TABLE dd13 (c TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP::TIMESTAMP_LTZ)");
        assertAnswer("C|TIMESTAMP_LTZ(9)|COLUMN|Y|CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_LTZ(9))|N|N|null|null|null|"
                + "null|null|null",
            "DESCRIBE TABLE dd13");
        assertAnswer("Table DD14 successfully created.", "CREATE OR REPLACE TABLE dd14 (c INT DEFAULT 10 / 4)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|(CAST(10 AS NUMBER(8,6))) / 4|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd14");
        assertAnswer("Table DD15 successfully created.",
            "CREATE OR REPLACE TABLE dd15 (c VARCHAR(5) DEFAULT 'x'::VARCHAR(5))");
        assertAnswer("C|VARCHAR(5)|COLUMN|Y|'x'|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd15");
        assertAnswer("Table DD16 successfully created.",
            "CREATE OR REPLACE TABLE dd16 (c TIMESTAMP_NTZ(3) DEFAULT CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_NTZ(3)))");
        assertAnswer("C|TIMESTAMP_NTZ(3)|COLUMN|Y|CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_NTZ(3))|N|N|null|null|null|"
                + "null|null|null",
            "DESCRIBE TABLE dd16");
        assertAnswer("Table DD17 successfully created.", "CREATE OR REPLACE TABLE dd17 (c VARCHAR DEFAULT 'it''s')");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|'it''s'|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd17");
        assertAnswer("Table DD18 successfully created.", "CREATE OR REPLACE TABLE dd18 (c INT DEFAULT 1 + 2 * 3)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|1 + (2 * 3)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd18");
        assertAnswer("Table DD19 successfully created.",
            "CREATE OR REPLACE TABLE dd19 (c DATE DEFAULT DATEADD(day, 1, CURRENT_DATE()))");
        assertAnswer("C|DATE|COLUMN|Y|DATE_ADDDAYSTODATE(1, CURRENT_DATE())|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd19");
        assertAnswer("Table DD20 successfully created.",
            "CREATE OR REPLACE TABLE dd20 (c VARCHAR DEFAULT TO_VARCHAR(1))");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|CAST(1 AS VARCHAR)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd20");
        assertAnswer("Table DD21 successfully created.", "CREATE OR REPLACE TABLE dd21 (c NUMBER(10,2) DEFAULT 1)");
        assertAnswer("C|NUMBER(10,2)|COLUMN|Y|1|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd21");
        assertAnswer("REFUSED SQL compilation error: | Default value data type does not match data type for column C",
            "CREATE OR REPLACE TABLE dd22 (c VARCHAR DEFAULT 1)");
        assertAnswer("REFUSED SQL compilation error: | Table 'DD22' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE DD22.",
            "DESCRIBE TABLE dd22");
        assertAnswer("Table DD23 successfully created.",
            "CREATE OR REPLACE TABLE dd23 (c TIMESTAMP_TZ DEFAULT CURRENT_TIMESTAMP()::TIMESTAMP_TZ)");
        assertAnswer("C|TIMESTAMP_TZ(9)|COLUMN|Y|CAST(CURRENT_TIMESTAMP() AS TIMESTAMP_TZ(9))|N|N|null|null|null|null|"
                + "null|null",
            "DESCRIBE TABLE dd23");
        assertAnswer("Table DD24 successfully created.", "CREATE OR REPLACE TABLE dd24 (c INT DEFAULT (1 + 2))");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|1 + 2|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd24");
        assertAnswer("create or replace TABLE DD01 ( | 	C TIMESTAMP_NTZ(9) DEFAULT CAST(CURRENT_TIMESTAMP() AS "
                + "TIMESTAMP_NTZ(9)) | );",
            "SELECT GET_DDL('TABLE', 'dd01')");
        assertAnswer("create or replace TABLE DD02 ( | 	C NUMBER(38,0) DEFAULT 1 + 2 | );",
            "SELECT GET_DDL('TABLE', 'dd02')");
        assertAnswer("create or replace TABLE DD05 ( | 	C NUMBER(38,0) DEFAULT CASE_FLATTENED(CAST(TRUE AS BOOLEAN), "
                + "1, 2) | );",
            "SELECT GET_DDL('TABLE', 'dd05')");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void parenthesizedAndLiteralDefaults() {
        assertAnswer("Table DX01 successfully created.",
            "CREATE OR REPLACE TABLE dx01 (id INTEGER, status VARCHAR DEFAULT 'active')");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; STATUS|VARCHAR(16777216)|"
                + "COLUMN|Y|'active'|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx01");
        assertAnswer("Table DX02 successfully created.",
            "CREATE OR REPLACE TABLE dx02 (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; CREATED_AT|TIMESTAMP_NTZ(9)|"
                + "COLUMN|Y|CURRENT_TIMESTAMP()|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx02");
        assertAnswer("Table DX03 successfully created.",
            "CREATE OR REPLACE TABLE dx03 (id INTEGER, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP())");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; UPDATED_AT|TIMESTAMP_NTZ(9)|"
                + "COLUMN|Y|CURRENT_TIMESTAMP()|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx03");
        assertAnswer("Table DX04 successfully created.",
            "CREATE OR REPLACE TABLE dx04 (id INTEGER, date_col DATE DEFAULT CURRENT_DATE)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; DATE_COL|DATE|COLUMN|Y|"
                + "CURRENT_DATE()|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx04");
        assertAnswer("Table DX05 successfully created.",
            "CREATE OR REPLACE TABLE dx05 (id INTEGER, quantity INTEGER DEFAULT 10 + 5)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUANTITY|NUMBER(38,0)|COLUMN|"
                + "Y|10 + 5|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx05");
        assertAnswer("Table DX06 successfully created.",
            "CREATE OR REPLACE TABLE dx06 (id INTEGER, upper_name VARCHAR DEFAULT UPPER('test'))");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; UPPER_NAME|VARCHAR(16777216)|"
                + "COLUMN|Y|UPPER('test')|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx06");
        assertAnswer("Table DX07 successfully created.",
            "CREATE OR REPLACE TABLE dx07 (id INTEGER, full_name VARCHAR DEFAULT 'Mr. ' || 'Unknown')");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; FULL_NAME|VARCHAR(16777216)|"
                + "COLUMN|Y|'Mr. ' || 'Unknown'|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx07");
        assertAnswer("Table DX08 successfully created.",
            "CREATE OR REPLACE TABLE dx08 (id INTEGER, priority VARCHAR DEFAULT CASE WHEN 1 = 1 THEN 'high' ELSE "
                + "'low' END)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; PRIORITY|VARCHAR(16777216)|"
                + "COLUMN|Y|CASE_FLATTENED(1 = 1, 'high', 'low')|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx08");
        assertAnswer("Table DX09 successfully created.",
            "CREATE OR REPLACE TABLE dx09 (id INTEGER, balance INTEGER DEFAULT -100)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; BALANCE|NUMBER(38,0)|COLUMN|"
                + "Y|-100|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx09");
        assertAnswer("Table DX10 successfully created.",
            "CREATE OR REPLACE TABLE dx10 (id INTEGER, computed INTEGER DEFAULT (10 * 5) + (20 - 5))");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; COMPUTED|NUMBER(38,0)|COLUMN|"
                + "Y|(10 * 5) + (20 - 5)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx10");
        assertAnswer("Table DX11 successfully created.",
            "CREATE OR REPLACE TABLE dx11 (id INTEGER, value INTEGER DEFAULT (100))");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; VALUE|NUMBER(38,0)|COLUMN|Y|"
                + "100|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx11");
        assertAnswer("Table DX12 successfully created.",
            "CREATE OR REPLACE TABLE dx12 (id INTEGER DEFAULT 0, name VARCHAR DEFAULT 'unnamed', created_at "
                + "TIMESTAMP DEFAULT CURRENT_TIMESTAMP, count INTEGER DEFAULT 5 * 2)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|0|N|N|null|null|null|null|null|null ;; NAME|VARCHAR(16777216)|COLUMN|Y|"
                + "'unnamed'|N|N|null|null|null|null|null|null ;; CREATED_AT|TIMESTAMP_NTZ(9)|COLUMN|Y|"
                + "CURRENT_TIMESTAMP()|N|N|null|null|null|null|null|null ;; COUNT|NUMBER(38,0)|COLUMN|Y|5 * 2|N|N|"
                + "null|null|null|null|null|null",
            "DESCRIBE TABLE dx12");
        assertAnswer("Table DX13 successfully created.",
            "CREATE OR REPLACE TABLE dx13 (id INTEGER, nullable_col VARCHAR DEFAULT NULL)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; NULLABLE_COL|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx13");
        assertAnswer("Table DX14 successfully created.",
            "CREATE OR REPLACE TABLE dx14 (id INTEGER, is_active BOOLEAN DEFAULT TRUE)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; IS_ACTIVE|BOOLEAN|COLUMN|Y|"
                + "TRUE|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx14");
        assertAnswer("Table DX15 successfully created.",
            "CREATE OR REPLACE TABLE dx15 (id INTEGER, rate DECIMAL DEFAULT 3.14 * 2)");
        assertAnswer("ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; RATE|NUMBER(38,0)|COLUMN|Y|"
                + "3.14 * 2|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx15");
        assertAnswer("Table DX16 successfully created.",
            "CREATE OR REPLACE TABLE dx16 (c INTEGER DEFAULT 1 * (2 + 3))");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|1 * (2 + 3)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dx16");
        assertAnswer("Table DX17 successfully created.",
            "CREATE OR REPLACE TABLE dx17 (c INTEGER DEFAULT (1 + 2) * 3)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|(1 + 2) * 3|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dx17");
        assertAnswer("Table DX18 successfully created.",
            "CREATE OR REPLACE TABLE dx18 (c NUMBER(10,2) DEFAULT (1.5 + 2) * 3)");
        assertAnswer("C|NUMBER(10,2)|COLUMN|Y|(1.5 + (CAST(2 AS NUMBER(3,1)))) * 3|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx18");
        assertAnswer("Table DX19 successfully created.",
            "CREATE OR REPLACE TABLE dx19 (c INTEGER DEFAULT 2 * (3 * 4))");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|2 * (3 * 4)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dx19");
        assertAnswer("Table DX20 successfully created.", "CREATE OR REPLACE TABLE dx20 (c INTEGER DEFAULT (10 + 5))");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|10 + 5|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dx20");
        assertAnswer("Table DX21 successfully created.",
            "CREATE OR REPLACE TABLE dx21 (c INTEGER DEFAULT (10 * 5) - 1)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|(10 * 5) - 1|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dx21");
        assertAnswer("Table DX22 successfully created.",
            "CREATE OR REPLACE TABLE dx22 (c NUMBER(10,2) DEFAULT 1 + 0.5)");
        assertAnswer("C|NUMBER(10,2)|COLUMN|Y|(CAST(1 AS NUMBER(3,1))) + 0.5|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dx22");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
