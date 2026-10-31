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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A MAP keys by a text or by a NUMBER of no scale and by nothing else: a FLOAT, a NUMBER with a scale, a DECFLOAT, a
 * BOOLEAN, a temporal, a BINARY, a VARIANT, a VECTOR, a UUID, a semi-structured or structured type and a MAP are
 * refused wherever the type is written - a cast, a column, a routine's parameter or result, a nested MAP or ARRAY,
 * EXPLAIN - as "Unsupported data type 'MAP with key type FLOAT'.", the key named as its type is canonically
 * spelled, a GEOGRAPHY as UNKNOWN (all live-verified).
 */
public class MapKeyTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void keyTypesWhereverWritten() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT {'1.5':'x'}::MAP(FLOAT,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BOOLEAN'.",
            "SELECT {'1':'x'}::MAP(BOOLEAN,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DATE'.",
            "SELECT {'1':'x'}::MAP(DATE,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type NUMBER(10,2)'.",
            "SELECT {'1':'x'}::MAP(NUMBER(10,2),VARCHAR) m");
        assertAnswer("{\"1\":\"x\"}", "SELECT {'1':'x'}::MAP(NUMBER(10,0),VARCHAR) m");
        assertAnswer("{\"1\":\"x\"}", "SELECT {'1':'x'}::MAP(INT,VARCHAR) m");
        assertAnswer("{\"a\":\"x\"}", "SELECT {'a':'x'}::MAP(VARCHAR(5),VARCHAR) m");
        assertAnswer("{\"a\":\"x\"}", "SELECT {'a':'x'}::MAP(TEXT,VARCHAR) m");
        assertAnswer("{\"a\":\"x\"}", "SELECT {'a':'x'}::MAP(CHAR,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIMESTAMP_NTZ(9)'.",
            "SELECT {'1':'x'}::MAP(TIMESTAMP_NTZ,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BINARY(67108864)'.",
            "SELECT {'1':'x'}::MAP(BINARY,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type VARIANT'.",
            "SELECT {'1':'x'}::MAP(VARIANT,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DECFLOAT(38)'.",
            "SELECT {'1':'x'}::MAP(DECFLOAT,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT {'1':'x'}::MAP(DOUBLE,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIME(9)'.",
            "SELECT {'1':'x'}::MAP(TIME,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type ARRAY(NUMBER(38,0))'.",
            "SELECT {'1':'x'}::MAP(ARRAY(INT),VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type OBJECT(a NUMBER(38,"
                + "0))'.",
            "SELECT {'1':'x'}::MAP(OBJECT(a INT),VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT CAST(NULL AS MAP(FLOAT, INT)) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT [{'1':'x'}]::ARRAY(MAP(FLOAT,VARCHAR)) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DATE'.",
            "SELECT {'1':'x'}::MAP(VARCHAR, MAP(DATE, INT)) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type NUMBER(10,2)'.",
            "CREATE OR REPLACE TABLE tm42 (m MAP(NUMBER(10,2), VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "CREATE OR REPLACE TABLE tm42 (m MAP(FLOAT, VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DATE'.",
            "CREATE OR REPLACE TABLE tm42 (m MAP(DATE, VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BOOLEAN'.",
            "CREATE OR REPLACE TABLE tm42 (m MAP(BOOLEAN, VARCHAR))");
        assertAnswer("Table TM42 successfully created.", "CREATE OR REPLACE TABLE tm42 (m MAP(VARCHAR, VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "ALTER TABLE tm42 ADD COLUMN m2 MAP(FLOAT, INT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "CREATE OR REPLACE FUNCTION f42(m MAP(FLOAT, INT)) RETURNS INT AS '1'");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DATE'.",
            "CREATE OR REPLACE FUNCTION f42(m INT) RETURNS MAP(DATE, INT) AS 'NULL'");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "EXPLAIN SELECT {'1.5':'x'}::MAP(FLOAT,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT {'1':'x'}::MAP(NUMBER(38,0),VARCHAR)::MAP(FLOAT,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT {'1':'x'}::MAP(FLOAT ,VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT TRY_CAST({'1':'x'} AS MAP(FLOAT,VARCHAR)) m");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void keyTypeNames() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type UUID'.",
            "SELECT {'a':'x'}::MAP(UUID, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type UNKNOWN'.",
            "SELECT {'a':'x'}::MAP(GEOGRAPHY, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type VECTOR(INT, 2)'.",
            "SELECT {'a':'x'}::MAP(VECTOR(INT, 2), VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type ARRAY'.",
            "SELECT {'a':'x'}::MAP(ARRAY, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type OBJECT'.",
            "SELECT {'a':'x'}::MAP(OBJECT, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIMESTAMP_TZ(9)'.",
            "SELECT {'a':'x'}::MAP(TIMESTAMP_TZ, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIMESTAMP_LTZ(3)'.",
            "SELECT {'a':'x'}::MAP(TIMESTAMP_LTZ(3), VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BINARY(5)'.",
            "SELECT {'a':'x'}::MAP(BINARY(5), VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BINARY(67108864)'.",
            "SELECT {'a':'x'}::MAP(VARBINARY, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type FLOAT'.",
            "SELECT {'a':'x'}::MAP(REAL, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIME(3)'.",
            "SELECT {'a':'x'}::MAP(TIME(3), VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIMESTAMP_NTZ(9)'.",
            "SELECT {'a':'x'}::MAP(DATETIME, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type "
                + "MAP(VARCHAR(134217728), NUMBER(38,0))'.",
            "SELECT {'a':'x'}::MAP(MAP(VARCHAR, INT), VARCHAR) m");
        assertAnswer("REFUSED Numeric value 'a' is not recognized", "SELECT {'a':'x'}::MAP(NUMBER(5,0), VARCHAR) m");
        assertAnswer("{\"a\":\"x\"}", "SELECT {'a':'x'}::MAP(STRING, VARCHAR) m");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type BINARY(8388608)'.",
            "CREATE OR REPLACE TABLE tm46 (m MAP(BINARY, VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type TIME(9)'.",
            "CREATE OR REPLACE TABLE tm46 (m MAP(VARCHAR, VARCHAR), n MAP(TIME, INT))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type "
                + "ARRAY(VARCHAR(16777216))'.",
            "CREATE OR REPLACE TABLE tm46 (m MAP(ARRAY(VARCHAR), INT))");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
