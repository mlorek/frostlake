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
 * A column DEFAULT takes a condition, NOT, AND and OR, and tells it back as the plan prints it; DEFAULT NOT NULL
 * is the NOT of a NULL, and ADD COLUMN refuses a condition as a computed default (all live-verified).
 */
public class ColumnDefaultConditionTest extends MeasuredCellsTestSupport {

    @Test
    public void conditions() {
        assertAnswer("Table DD10 successfully created.", "CREATE OR REPLACE TABLE dd10 (c BOOLEAN DEFAULT NOT FALSE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd10");
        assertAnswer("1", "INSERT INTO dd10 (c) VALUES (DEFAULT)");
        assertAnswer("true", "SELECT c FROM dd10");
        assertAnswer("Table DD11 successfully created.", "CREATE OR REPLACE TABLE dd11 (c BOOLEAN DEFAULT NOT NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(CAST(null AS BOOLEAN))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE dd11");
        assertAnswer("Table DD12 successfully created.",
            "CREATE OR REPLACE TABLE dd12 (c BOOLEAN DEFAULT TRUE AND FALSE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|TRUE AND FALSE|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd12");
        assertAnswer("Table DD13 successfully created.", "CREATE OR REPLACE TABLE dd13 (c BOOLEAN DEFAULT 1 = 1)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|1 = 1|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd13");
        assertAnswer("Table DD14 successfully created.", "CREATE OR REPLACE TABLE dd14 (c BOOLEAN DEFAULT NOT 1 = 2)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(1 = 2)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd14");
        assertAnswer("Table DD15 successfully created.",
            "CREATE OR REPLACE TABLE dd15 (c BOOLEAN DEFAULT TRUE OR FALSE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|TRUE OR FALSE|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd15");
        assertAnswer("Table DD16 successfully created.", "CREATE OR REPLACE TABLE dd16 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [NOT(FALSE)]",
            "ALTER TABLE dd16 ADD COLUMN c BOOLEAN DEFAULT NOT FALSE");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd16");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'C'",
            "ALTER TABLE dd16 ALTER COLUMN c SET DEFAULT NOT TRUE");
        assertAnswer("Table DD17 successfully created.",
            "CREATE OR REPLACE TABLE dd17 (c BOOLEAN DEFAULT NOT FALSE NOT NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|N|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd17");
        assertAnswer("Table DD18 successfully created.",
            "CREATE OR REPLACE TABLE dd18 (c BOOLEAN NOT NULL DEFAULT NOT FALSE)");
        assertAnswer("C|BOOLEAN|COLUMN|N|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd18");
        assertAnswer("Table DD19 successfully created.", "CREATE OR REPLACE TABLE dd19 (c BOOLEAN DEFAULT 1 < 2)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|1 < 2|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd19");
        assertAnswer("Table DD20 successfully created.", "CREATE OR REPLACE TABLE dd20 (c BOOLEAN DEFAULT 1 IS NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|1 IS NULL|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd20");
        assertAnswer("Table DD21 successfully created.",
            "CREATE OR REPLACE TABLE dd21 (c BOOLEAN DEFAULT (NOT FALSE))");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd21");
        assertAnswer("Table DD22 successfully created.",
            "CREATE OR REPLACE TABLE dd22 (c BOOLEAN DEFAULT NOT NOT TRUE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(NOT(TRUE))|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd22");
        assertAnswer("Table DD23 successfully created.",
            "CREATE OR REPLACE TABLE dd23 (c BOOLEAN DEFAULT 1 IN (1, 2))");
        assertAnswer("C|BOOLEAN|COLUMN|Y|1 IN (1, 2)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd23");
        assertAnswer("Table DD24 successfully created.",
            "CREATE OR REPLACE TABLE dd24 (c BOOLEAN DEFAULT 'a' LIKE 'a%')");
        assertAnswer("C|BOOLEAN|COLUMN|Y|'a' LIKE 'a%'|N|N|null|null|null|null|null|null", "DESCRIBE TABLE dd24");
        assertAnswer("Table DD25 successfully created.",
            "CREATE OR REPLACE TABLE dd25 (c BOOLEAN DEFAULT NOT FALSE COMMENT 'x')");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(FALSE)|N|N|null|null|x|null|null|null", "DESCRIBE TABLE dd25");
    }

    @Test
    public void moreConditions() {
        assertAnswer("Table DE1 successfully created.",
            "CREATE OR REPLACE TABLE de1 (c BOOLEAN DEFAULT NOT FALSE AND TRUE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|(NOT(FALSE)) AND TRUE|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de1");
        assertAnswer("1", "INSERT INTO de1 (c) VALUES (DEFAULT)");
        assertAnswer("true", "SELECT c FROM de1");
        assertAnswer("Table DE2 successfully created.",
            "CREATE OR REPLACE TABLE de2 (c BOOLEAN DEFAULT FALSE OR NOT TRUE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|FALSE OR (NOT(TRUE))|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de2");
        assertAnswer("1", "INSERT INTO de2 (c) VALUES (DEFAULT)");
        assertAnswer("false", "SELECT c FROM de2");
        assertAnswer("Table DE3 successfully created.",
            "CREATE OR REPLACE TABLE de3 (c BOOLEAN DEFAULT TRUE AND NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|TRUE AND (SYSTEM$NULL_TO_BOOLEAN(null))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de3");
        assertAnswer("1", "INSERT INTO de3 (c) VALUES (DEFAULT)");
        assertAnswer("null", "SELECT c FROM de3");
        assertAnswer("Table DE4 successfully created.",
            "CREATE OR REPLACE TABLE de4 (c BOOLEAN DEFAULT NOT TRUE OR FALSE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|(NOT(TRUE)) OR FALSE|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de4");
        assertAnswer("1", "INSERT INTO de4 (c) VALUES (DEFAULT)");
        assertAnswer("false", "SELECT c FROM de4");
        assertAnswer("Table DE5 successfully created.",
            "CREATE OR REPLACE TABLE de5 (c BOOLEAN DEFAULT NOT (TRUE OR FALSE))");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(TRUE OR FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de5");
        assertAnswer("1", "INSERT INTO de5 (c) VALUES (DEFAULT)");
        assertAnswer("false", "SELECT c FROM de5");
        assertAnswer("Table DE6 successfully created.",
            "CREATE OR REPLACE TABLE de6 (c BOOLEAN DEFAULT TRUE AND FALSE OR TRUE)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|(TRUE AND FALSE) OR TRUE|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de6");
        assertAnswer("1", "INSERT INTO de6 (c) VALUES (DEFAULT)");
        assertAnswer("true", "SELECT c FROM de6");
        assertAnswer("Table DE7 successfully created.",
            "CREATE OR REPLACE TABLE de7 (c BOOLEAN DEFAULT 1 NOT IN (1, 2))");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(1 IN (1, 2))|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de7");
        assertAnswer("Table DE8 successfully created.",
            "CREATE OR REPLACE TABLE de8 (c BOOLEAN DEFAULT 'a' NOT LIKE 'b')");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT('a' LIKE 'b')|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de8");
        assertAnswer("Table DE9 successfully created.",
            "CREATE OR REPLACE TABLE de9 (c BOOLEAN DEFAULT 1 IS NOT NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|1 IS NOT NULL|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de9");
        assertAnswer("Table DE10 successfully created.",
            "CREATE OR REPLACE TABLE de10 (c BOOLEAN DEFAULT NOT NULL NOT NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|N|NOT(CAST(null AS BOOLEAN))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de10");
        assertAnswer("REFUSED DML operation to table DE10 failed on column C with error: NULL result in a "
                + "non-nullable column",
            "INSERT INTO de10 (c) VALUES (DEFAULT)");
        assertAnswer("", "SELECT c FROM de10");
        assertAnswer("Table DE11 successfully created.", "CREATE OR REPLACE TABLE de11 (c BOOLEAN DEFAULT NOT 5)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(CAST(5 AS BOOLEAN))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de11");
        assertAnswer("1", "INSERT INTO de11 (c) VALUES (DEFAULT)");
        assertAnswer("false", "SELECT c FROM de11");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"!\".",
            "CREATE OR REPLACE TABLE de12 (c BOOLEAN DEFAULT !TRUE)");
        assertAnswer("REFUSED SQL compilation error: | Table 'DE12' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE DE12.",
            "DESCRIBE TABLE de12");
        assertAnswer("REFUSED SQL compilation error: | Table 'DE12' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE DE12.",
            "INSERT INTO de12 (c) VALUES (DEFAULT)");
        assertAnswer("REFUSED SQL compilation error: | Object 'DE12' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE DE12.",
            "SELECT c FROM de12");
        assertAnswer("Table DE13 successfully created.",
            "CREATE OR REPLACE TABLE de13 (c BOOLEAN DEFAULT NOT 1 BETWEEN 0 AND 2)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT((1 >= 0) AND (1 <= 2))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de13");
        assertAnswer("Table DE14 successfully created.",
            "CREATE OR REPLACE TABLE de14 (c BOOLEAN DEFAULT TRUE AND (FALSE OR TRUE))");
        assertAnswer("C|BOOLEAN|COLUMN|Y|TRUE AND (FALSE OR TRUE)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de14");
        assertAnswer("1", "INSERT INTO de14 (c) VALUES (DEFAULT)");
        assertAnswer("true", "SELECT c FROM de14");
        assertAnswer("Table DE15 successfully created.",
            "CREATE OR REPLACE TABLE de15 (c BOOLEAN DEFAULT NOT TRUE::BOOLEAN)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(TRUE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de15");
        assertAnswer("1", "INSERT INTO de15 (c) VALUES (DEFAULT)");
        assertAnswer("false", "SELECT c FROM de15");
        assertAnswer("Table DE16 successfully created.",
            "CREATE OR REPLACE TABLE de16 (c BOOLEAN NOT NULL DEFAULT NOT NULL)");
        assertAnswer("C|BOOLEAN|COLUMN|N|NOT(CAST(null AS BOOLEAN))|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de16");
        assertAnswer("Table DE17 successfully created.", "CREATE OR REPLACE TABLE de17 (c INT DEFAULT NOT FALSE)");
        assertAnswer("C|NUMBER(38,0)|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de17");
        assertAnswer("Table DE18 successfully created.", "CREATE OR REPLACE TABLE de18 (c VARCHAR DEFAULT NOT FALSE)");
        assertAnswer("C|VARCHAR(16777216)|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE de18");
        assertAnswer("1", "INSERT INTO de18 (c) VALUES (DEFAULT)");
        assertAnswer("true", "SELECT c FROM de18");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 52 | sub-queries are not supported as "
                + "part of the specification of a default value clause.",
            "CREATE OR REPLACE TABLE de19 (c BOOLEAN DEFAULT NOT EXISTS (SELECT 1))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'C2'",
            "CREATE OR REPLACE TABLE de20 (c BOOLEAN DEFAULT NOT c2, c2 BOOLEAN)");
        assertAnswer("Table DE21 successfully created.", "CREATE OR REPLACE TABLE de21 (c VARIANT DEFAULT NOT FALSE)");
        assertAnswer("C|VARIANT|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null", "DESCRIBE TABLE de21");
        assertAnswer("Table DE22 successfully created.",
            "CREATE OR REPLACE TABLE de22 (c BOOLEAN DEFAULT NOT FALSE, d INT DEFAULT 1)");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null ;; D|NUMBER(38,0)|COLUMN|Y|1|N|"
                + "N|null|null|null|null|null|null",
            "DESCRIBE TABLE de22");
        assertAnswer("create or replace TABLE DE22 ( | 	C BOOLEAN DEFAULT NOT(FALSE), | 	D NUMBER(38,0) DEFAULT 1 | );",
            "SELECT GET_DDL('table', 'de22')");
        assertAnswer("C|NOT(FALSE) ;; D|1",
            "SELECT column_name, column_default FROM information_schema.columns WHERE table_name = 'DE22' ORDER BY 1");
        assertAnswer("DE22|TEST_SCHEMA|C|{\"type\":\"BOOLEAN\",\"nullable\":true}|true|NOT(FALSE)|COLUMN|||TEST_DB||"
                + "null|null ;; DE22|TEST_SCHEMA|D|{\"type\":\"FIXED\",\"precision\":38,\"scale\":0,"
                + "\"nullable\":true}|true|1|COLUMN|||TEST_DB||null|null",
            "SHOW COLUMNS IN TABLE de22");
        assertAnswer("Table DE23 successfully created.", "CREATE OR REPLACE TABLE de23 LIKE de22");
        assertAnswer("C|BOOLEAN|COLUMN|Y|NOT(FALSE)|N|N|null|null|null|null|null|null ;; D|NUMBER(38,0)|COLUMN|Y|1|N|"
                + "N|null|null|null|null|null|null",
            "DESCRIBE TABLE de23");
        assertAnswer("Table DE24 successfully created.", "CREATE OR REPLACE TABLE de24 (a INT, c BOOLEAN)");
        assertAnswer("REFUSED Unsupported feature 'Alter Column Set Default'.",
            "ALTER TABLE de24 ALTER COLUMN c SET DEFAULT NOT TRUE");
        assertAnswer("REFUSED Unsupported feature 'Alter Column Set Default'.",
            "ALTER TABLE de24 MODIFY COLUMN c SET DEFAULT NOT TRUE");
        assertAnswer("REFUSED Unsupported feature 'Alter Column Set Default'.",
            "ALTER TABLE de24 ALTER COLUMN c SET DEFAULT TRUE");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [TRUE AND FALSE]",
            "ALTER TABLE de24 ADD COLUMN d BOOLEAN DEFAULT TRUE AND FALSE");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [NOT(FALSE)]",
            "ALTER TABLE de24 ADD COLUMN e BOOLEAN DEFAULT (NOT FALSE)");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [NOT(CAST(null AS BOOLEAN))]",
            "ALTER TABLE de24 ADD COLUMN f BOOLEAN DEFAULT NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [1 = 1]",
            "ALTER TABLE de24 ADD COLUMN g BOOLEAN DEFAULT 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [NOT(CAST(null AS BOOLEAN))]",
            "ALTER TABLE de24 ADD COLUMN h BOOLEAN DEFAULT NOT NULL NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid column default expression [NOT(FALSE)]",
            "ALTER TABLE de24 ADD COLUMN i INT DEFAULT NOT FALSE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'X'",
            "ALTER TABLE de24 ADD COLUMN j BOOLEAN DEFAULT TRUE OR x");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; C|BOOLEAN|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null",
            "DESCRIBE TABLE de24");
        assertAnswer("REFUSED SQL compilation error:  | Multiple DEFAULT or AUTOINCREMENT expressions declared for "
                + "column C.",
            "CREATE OR REPLACE TABLE de25 (c BOOLEAN DEFAULT NOT FALSE DEFAULT TRUE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ')'.",
            "CREATE OR REPLACE TABLE de26 (c BOOLEAN DEFAULT TRUE AND)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ')'.",
            "CREATE OR REPLACE TABLE de27 (c BOOLEAN DEFAULT NOT)");
        assertAnswer("Table DE28 successfully created.",
            "CREATE OR REPLACE TABLE de28 (c BOOLEAN DEFAULT NOT FALSE PRIMARY KEY)");
        assertAnswer("C|BOOLEAN|COLUMN|N|NOT(FALSE)|Y|N|null|null|null|null|null|null", "DESCRIBE TABLE de28");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE de29 (c BOOLEAN DEFAULT TRUE OR FALSE COMMENT 'x' NOT NULL)");
        assertAnswer("REFUSED SQL compilation error: | Table 'DE29' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE DE29.",
            "DESCRIBE TABLE de29");
    }
}
