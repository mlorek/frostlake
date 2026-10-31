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
 * Type parameter lists in three more places: a policy's RETURNS type whose list cannot begin reads on past its '('
 * to the first word, taken for the type, and the token after it; a dynamic table's column list names its first
 * column at any type list it does not take; an external table's column list is read as a table's
 * (all live-verified).
 */
public class TypeListTailLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void typeListTails() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 70 unexpected '->'. |"
                + " syntax error line 1 at position 74 unexpected '<EOF>'.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER) RETURNS NUMBER() -> v");
        assertAnswer("Masking policy TP_M2 successfully created.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v VARCHAR) RETURNS VARCHAR() -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ','. | syntax error line 1 at position 69 unexpected ')'. | "
                + "syntax error line 1 at position 71 unexpected '->'. | syntax error line 1 at position 75 "
                + "unexpected '<EOF>'.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER) RETURNS NUMBER(,) -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 70 unexpected '->'. |"
                + " syntax error line 1 at position 75 unexpected '+'.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER) RETURNS NUMBER() -> v + 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected '('. | syntax "
                + "error line 1 at position 72 unexpected ')'. | syntax error line 1 at position 74 unexpected '->'. |"
                + " syntax error line 1 at position 81 unexpected '<EOF>'.",
            "CREATE OR REPLACE ROW ACCESS POLICY tp_r2 AS (v NUMBER) RETURNS BOOLEAN() -> TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ','. | syntax "
                + "error line 1 at position 51 unexpected '2'. | syntax error line 1 at position 54 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a VARCHAR(5,2) AS (value:a::VARCHAR)) LOCATION = @nosuch "
                + "FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ','. | syntax "
                + "error line 1 at position 52 unexpected '1'. | syntax error line 1 at position 55 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a NUMBER(5,2,1) AS (value:a::NUMBER)) LOCATION = @nosuch "
                + "FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a VARCHAR(5,2)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        assertAnswer("REFUSED Warehouse 'NOSUCH' does not exist.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a VARCHAR(5)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        assertAnswer("REFUSED Warehouse 'NOSUCH' does not exist.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (b VARCHAR(5)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        assertAnswer("REFUSED Warehouse 'NOSUCH' does not exist.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS SELECT 1");
        assertAnswer("REFUSED Warehouse 'NOSUCH' does not exist.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a COMMENT 'x') TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        assertAnswer("REFUSED Warehouse 'NOSUCH' does not exist.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a, b) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS SELECT 1, 2");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void typeListTailShapes() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT)");
        engine.execute("INSERT INTO rt VALUES (1), (2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a NUMBER()) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a INT, b VARCHAR(5,2)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch "
                + "AS SELECT 1, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a VARCHAR(5,2), b INT) TARGET_LAG = '1 minute' WAREHOUSE = nosuch "
                + "AS SELECT 1, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a VARCHAR(5,)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'a'.",
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a ARRAY(5)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 70 unexpected '->'. |"
                + " syntax error line 1 at position 78 unexpected 'WHEN'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER() -> CASE WHEN TRUE THEN v END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 69 unexpected '->'. |"
                + " syntax error line 1 at position 72 unexpected '<EOF>'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER()->v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected ','.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER(5,2,1) -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected ')'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER(5,) -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 70 unexpected '->'. |"
                + " syntax error line 1 at position 75 unexpected 'COMMENT'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER() -> v COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected '('. | syntax "
                + "error line 1 at position 70 unexpected ')'. | syntax error line 1 at position 72 unexpected '->'. |"
                + " syntax error line 1 at position 76 unexpected '<EOF>'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER ( ) -> v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '('. | syntax "
                + "error line 1 at position 68 unexpected ')'. | syntax error line 1 at position 70 unexpected '->'.",
            "CREATE OR REPLACE MASKING POLICY tp_m3 AS (v NUMBER) RETURNS NUMBER() -> 1 + v");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ','. | syntax "
                + "error line 1 at position 51 unexpected '2'. | syntax error line 1 at position 54 unexpected 'AS'.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a VARCHAR(5,2) AS (value:a::VARCHAR), b INT AS (value:b::INT)) "
                + "LOCATION = @nosuch FILE_FORMAT = (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('.",
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a NUMBER() AS (value:a::NUMBER)) LOCATION = @nosuch FILE_FORMAT "
                + "= (TYPE = CSV)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','.",
            "CREATE EXTERNAL TABLE tp_e (a VARCHAR(5,) AS (value:a::VARCHAR)) LOCATION = @nosuch FILE_FORMAT = (TYPE "
                + "= CSV)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
