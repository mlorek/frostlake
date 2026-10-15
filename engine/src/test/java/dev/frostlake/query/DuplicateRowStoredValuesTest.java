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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The duplicate-row refusal prints the target row as it is stored: a time and a timestamp in the units of
 * their scale, a zoned timestamp with its offset folded in, a binary as hexadecimal digits, a FLOAT in six
 * significant digits (live-verified). The semi-structured kinds are run for the record: the account prints
 * their stored bytes.
 */
public class DuplicateRowStoredValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void timesTimestampsBinariesAndNumbers() {
        answer(
            "CREATE OR REPLACE TABLE us (id INT, n INT)");
        answer(
            "INSERT INTO us VALUES (1, 10), (1, 20)");
        answer(
            "CREATE OR REPLACE TABLE dw1 (id INT, v TIME)");
        answer(
            "INSERT INTO dw1 SELECT 1, '03:04:05'::TIME");
        answer(
            "CREATE OR REPLACE TABLE dw2 (id INT, v TIME(9))");
        answer(
            "INSERT INTO dw2 SELECT 1, '03:04:05.123456789'::TIME(9)");
        answer(
            "CREATE OR REPLACE TABLE dw3 (id INT, v TIME(3))");
        answer(
            "INSERT INTO dw3 SELECT 1, '03:04:05.123'::TIME(3)");
        answer(
            "CREATE OR REPLACE TABLE dw4 (id INT, v TIME(0))");
        answer(
            "INSERT INTO dw4 SELECT 1, '23:59:59'::TIME(0)");
        answer(
            "CREATE OR REPLACE TABLE dw5 (id INT, v TIMESTAMP_NTZ(0))");
        answer(
            "INSERT INTO dw5 SELECT 1, '2024-01-02 03:04:05'::TIMESTAMP_NTZ(0)");
        answer(
            "CREATE OR REPLACE TABLE dw6 (id INT, v TIMESTAMP_NTZ(3))");
        answer(
            "INSERT INTO dw6 SELECT 1, '2024-01-02 03:04:05.123'::TIMESTAMP_NTZ(3)");
        answer(
            "CREATE OR REPLACE TABLE dw7 (id INT, v TIMESTAMP_NTZ(6))");
        answer(
            "INSERT INTO dw7 SELECT 1, '2024-01-02 03:04:05.123456'::TIMESTAMP_NTZ(6)");
        answer(
            "CREATE OR REPLACE TABLE dw8 (id INT, v TIMESTAMP_NTZ(9))");
        answer(
            "INSERT INTO dw8 SELECT 1, '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9)");
        answer(
            "CREATE OR REPLACE TABLE dw9 (id INT, v TIMESTAMP_NTZ)");
        answer(
            "INSERT INTO dw9 SELECT 1, '1969-12-31 23:59:59'::TIMESTAMP_NTZ");
        answer(
            "CREATE OR REPLACE TABLE dw10 (id INT, v TIMESTAMP_LTZ)");
        answer(
            "INSERT INTO dw10 SELECT 1, '2024-01-02 03:04:05 +00:00'::TIMESTAMP_LTZ");
        answer(
            "CREATE OR REPLACE TABLE dw11 (id INT, v TIMESTAMP_LTZ(3))");
        answer(
            "INSERT INTO dw11 SELECT 1, '2024-01-02 03:04:05.123 +02:00'::TIMESTAMP_LTZ(3)");
        answer(
            "CREATE OR REPLACE TABLE dw12 (id INT, v TIMESTAMP_TZ)");
        answer(
            "INSERT INTO dw12 SELECT 1, '2024-01-02 03:04:05 +00:00'::TIMESTAMP_TZ");
        answer(
            "CREATE OR REPLACE TABLE dw13 (id INT, v TIMESTAMP_TZ)");
        answer(
            "INSERT INTO dw13 SELECT 1, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ");
        answer(
            "CREATE OR REPLACE TABLE dw14 (id INT, v TIMESTAMP_TZ)");
        answer(
            "INSERT INTO dw14 SELECT 1, '2024-01-02 03:04:05 -08:00'::TIMESTAMP_TZ");
        answer(
            "CREATE OR REPLACE TABLE dw15 (id INT, v TIMESTAMP_TZ(3))");
        answer(
            "INSERT INTO dw15 SELECT 1, '2024-01-02 03:04:05.123 +05:30'::TIMESTAMP_TZ(3)");
        answer(
            "CREATE OR REPLACE TABLE dw16 (id INT, v DATE)");
        answer(
            "INSERT INTO dw16 SELECT 1, '1969-12-31'::DATE");
        answer(
            "CREATE OR REPLACE TABLE dw17 (id INT, v BINARY)");
        answer(
            "INSERT INTO dw17 SELECT 1, TO_BINARY('414243', 'HEX')");
        answer(
            "CREATE OR REPLACE TABLE dw18 (id INT, v BINARY)");
        answer(
            "INSERT INTO dw18 SELECT 1, TO_BINARY('', 'HEX')");
        answer(
            "CREATE OR REPLACE TABLE dw19 (id INT, v BINARY(3))");
        answer(
            "INSERT INTO dw19 SELECT 1, TO_BINARY('00FF7F', 'HEX')");
        answer(
            "CREATE OR REPLACE TABLE dw20 (id INT, v NUMBER(10,2))");
        answer(
            "INSERT INTO dw20 SELECT 1, -2.50");
        answer(
            "CREATE OR REPLACE TABLE dw21 (id INT, v NUMBER(38,0))");
        answer(
            "INSERT INTO dw21 SELECT 1, 99999999999999999999999999999999999999");
        answer(
            "CREATE OR REPLACE TABLE dw22 (id INT, v NUMBER(38,10))");
        answer(
            "INSERT INTO dw22 SELECT 1, 1.5");
        answer(
            "CREATE OR REPLACE TABLE dw23 (id INT, v NUMBER(5,0))");
        answer(
            "INSERT INTO dw23 SELECT 1, 0");
        answer(
            "CREATE OR REPLACE TABLE dw24 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw24 SELECT 1, 2::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw25 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw25 SELECT 1, 'NaN'::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw26 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw26 SELECT 1, 'inf'::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw27 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw27 SELECT 1, '-inf'::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw28 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw28 SELECT 1, 1e30::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw29 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw29 SELECT 1, 0.1::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw30 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw30 SELECT 1, -0.0::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw31 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dw31 SELECT 1, 123456789012::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dw32 (id INT, v VARCHAR)");
        answer(
            "INSERT INTO dw32 SELECT 1, ''");
        answer(
            "CREATE OR REPLACE TABLE dw33 (id INT, v VARCHAR)");
        answer(
            "INSERT INTO dw33 SELECT 1, 'a\\nb'");
        answer(
            "CREATE OR REPLACE TABLE dw34 (id INT, v VARCHAR)");
        answer(
            "INSERT INTO dw34 SELECT 1, 'tab\\there'");
        answer(
            "CREATE OR REPLACE TABLE dw35 (id INT, v VARCHAR(5))");
        answer(
            "INSERT INTO dw35 SELECT 1, 'x, y'");
        answer(
            "CREATE OR REPLACE TABLE dw36 (id INT, v CHAR(3))");
        answer(
            "INSERT INTO dw36 SELECT 1, 'ab'");
        answer(
            "CREATE OR REPLACE TABLE dw51 (id INT, v BOOLEAN)");
        answer(
            "INSERT INTO dw51 SELECT 1, FALSE");
        answer(
            "CREATE OR REPLACE TABLE dwm (id INT, t TIME, z TIMESTAMP_TZ, l TIMESTAMP_LTZ, b BINARY, n VARCHAR)");
        answer(
            "INSERT INTO dwm SELECT 1, '03:04:05'::TIME, '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, '2024-01-02 "
                + "03:04:05 +00:00'::TIMESTAMP_LTZ, TO_BINARY('414243', 'HEX'), NULL");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 11045000000000]",
            "MERGE INTO dw1 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 11045123456789]",
            "MERGE INTO dw2 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 11045123]",
            "MERGE INTO dw3 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 86399]",
            "MERGE INTO dw4 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1704164645]",
            "MERGE INTO dw5 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1704164645123]",
            "MERGE INTO dw6 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1704164645123456]",
            "MERGE INTO dw7 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertShapedAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, <ID19>]",
            "MERGE INTO dw8 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -1000000000]",
            "MERGE INTO dw9 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertShapedAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, <ID19>]",
            "MERGE INTO dw10 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1704157445123]",
            "MERGE INTO dw11 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 27921033543680000001440]",
            "MERGE INTO dw12 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 27920915578880000001560]",
            "MERGE INTO dw13 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 27921505402880000000960]",
            "MERGE INTO dw14 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 27920709142497002]",
            "MERGE INTO dw15 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -1]",
            "MERGE INTO dw16 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0x414243]",
            "MERGE INTO dw17 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, NO_VALUE]",
            "MERGE INTO dw18 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0x00ff7f]",
            "MERGE INTO dw19 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -250]",
            "MERGE INTO dw20 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, "
                + "99999999999999999999999999999999999999]",
            "MERGE INTO dw21 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 15000000000]",
            "MERGE INTO dw22 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0]",
            "MERGE INTO dw23 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 2]",
            "MERGE INTO dw24 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, nan]",
            "MERGE INTO dw25 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, inf]",
            "MERGE INTO dw26 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -inf]",
            "MERGE INTO dw27 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e+30]",
            "MERGE INTO dw28 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.1]",
            "MERGE INTO dw29 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -0]",
            "MERGE INTO dw30 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1.23457e+11]",
            "MERGE INTO dw31 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, \"\"]",
            "MERGE INTO dw32 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, \"a | b\"]",
            "MERGE INTO dw33 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, \"tab\there\"]",
            "MERGE INTO dw34 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x, y\"]",
            "MERGE INTO dw35 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, \"ab\"]",
            "MERGE INTO dw36 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0]",
            "MERGE INTO dw51 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertShapedAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 11045000000000, "
                + "27920915578880000001560, <ID19>, 0x414243, NULL]",
            "MERGE INTO dwm t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM dwm WHERE id = 1");
    }

    @Test
    public void semiStructuredValues() {
        answer(
            "CREATE OR REPLACE TABLE us (id INT, n INT)");
        answer(
            "INSERT INTO us VALUES (1, 10), (1, 20)");
        answer(
            "CREATE OR REPLACE TABLE dw37 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw37 SELECT 1, 1::VARIANT");
        answer(
            "CREATE OR REPLACE TABLE dw38 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw38 SELECT 1, 'x'::VARIANT");
        answer(
            "CREATE OR REPLACE TABLE dw39 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw39 SELECT 1, TRUE::VARIANT");
        answer(
            "CREATE OR REPLACE TABLE dw40 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw40 SELECT 1, 1.5::VARIANT");
        answer(
            "CREATE OR REPLACE TABLE dw41 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw41 SELECT 1, PARSE_JSON('null')");
        answer(
            "CREATE OR REPLACE TABLE dw42 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw42 SELECT 1, PARSE_JSON('[]')");
        answer(
            "CREATE OR REPLACE TABLE dw43 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw43 SELECT 1, PARSE_JSON('{}')");
        answer(
            "CREATE OR REPLACE TABLE dw44 (id INT, v VARIANT)");
        answer(
            "INSERT INTO dw44 SELECT 1, '2024-01-02'::DATE::VARIANT");
        answer(
            "CREATE OR REPLACE TABLE dw45 (id INT, v OBJECT)");
        answer(
            "INSERT INTO dw45 SELECT 1, OBJECT_CONSTRUCT('k', 1)");
        answer(
            "CREATE OR REPLACE TABLE dw46 (id INT, v OBJECT)");
        answer(
            "INSERT INTO dw46 SELECT 1, OBJECT_CONSTRUCT()");
        answer(
            "CREATE OR REPLACE TABLE dw47 (id INT, v ARRAY)");
        answer(
            "INSERT INTO dw47 SELECT 1, ARRAY_CONSTRUCT()");
        answer(
            "CREATE OR REPLACE TABLE dw48 (id INT, v ARRAY)");
        answer(
            "INSERT INTO dw48 SELECT 1, ARRAY_CONSTRUCT(1, 2)");
        answer(
            "MERGE INTO dw37 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw38 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw39 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw40 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw41 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw42 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw43 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw44 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw45 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw46 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw47 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        answer(
            "MERGE INTO dw48 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
    }

    @Test
    public void floatsAndTheirDigits() {
        answer(
            "CREATE OR REPLACE TABLE us (id INT, n INT)");
        answer(
            "INSERT INTO us VALUES (1, 10), (1, 20)");
        answer(
            "CREATE OR REPLACE TABLE dv1 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv1 SELECT 1, 100000::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv2 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv2 SELECT 1, 1000000::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv3 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv3 SELECT 1, 0.0001::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv4 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv4 SELECT 1, 0.00001::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv5 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv5 SELECT 1, 123456.7::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv6 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv6 SELECT 1, 1234567.0::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv7 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv7 SELECT 1, 1.5e-7::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv8 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv8 SELECT 1, 0.1::FLOAT + 0.2::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv9 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv9 SELECT 1, 1::FLOAT / 3");
        answer(
            "CREATE OR REPLACE TABLE dv10 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv10 SELECT 1, 100::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv11 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv11 SELECT 1, 1e15::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv12 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv12 SELECT 1, 0.5::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv13 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv13 SELECT 1, -1.5::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv14 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv14 SELECT 1, 12345.678::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv15 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv15 SELECT 1, 999999.5::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv16 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv16 SELECT 1, 0.000123456789::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv17 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv17 SELECT 1, 1e-5::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv18 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv18 SELECT 1, 1e100::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv19 (id INT, v FLOAT)");
        answer(
            "INSERT INTO dv19 SELECT 1, -123456789::FLOAT");
        answer(
            "CREATE OR REPLACE TABLE dv20 (id INT, v REAL)");
        answer(
            "INSERT INTO dv20 SELECT 1, 2.5");
        answer(
            "CREATE OR REPLACE TABLE dv21 (id INT, v DOUBLE)");
        answer(
            "INSERT INTO dv21 SELECT 1, 1e5");
        answer(
            "CREATE OR REPLACE TABLE dv22 (id INT, v TIMESTAMP_NTZ(9))");
        answer(
            "INSERT INTO dv22 SELECT 1, '1970-01-02 00:00:00.123456789'::TIMESTAMP_NTZ(9)");
        answer(
            "CREATE OR REPLACE TABLE dv23 (id INT, v TIMESTAMP_LTZ)");
        answer(
            "INSERT INTO dv23 SELECT 1, '1970-01-02 00:00:00.5 +00:00'::TIMESTAMP_LTZ");
        answer(
            "CREATE OR REPLACE TABLE dv24 (id INT, v TIMESTAMP_TZ)");
        answer(
            "INSERT INTO dv24 SELECT 1, '1970-01-01 00:00:01 +01:00'::TIMESTAMP_TZ");
        answer(
            "CREATE OR REPLACE TABLE dv25 (id INT, v TIMESTAMP_TZ(0))");
        answer(
            "INSERT INTO dv25 SELECT 1, '1970-01-02 00:00:00 +00:00'::TIMESTAMP_TZ(0)");
        answer(
            "CREATE OR REPLACE TABLE dv26 (id INT, v TIMESTAMP_TZ)");
        answer(
            "INSERT INTO dv26 SELECT 1, '1969-12-31 23:59:59 +00:00'::TIMESTAMP_TZ");
        answer(
            "CREATE OR REPLACE TABLE dv27 (id INT, v TIMESTAMP_TZ(6))");
        answer(
            "INSERT INTO dv27 SELECT 1, '1970-01-02 00:00:00.000001 -00:30'::TIMESTAMP_TZ(6)");
        answer(
            "CREATE OR REPLACE TABLE dv28 (id INT, v TIME(6))");
        answer(
            "INSERT INTO dv28 SELECT 1, '00:00:01.5'::TIME(6)");
        answer(
            "CREATE OR REPLACE TABLE dv29 (id INT, v DATETIME)");
        answer(
            "INSERT INTO dv29 SELECT 1, '1970-01-02 00:00:00'::DATETIME");
        answer(
            "CREATE OR REPLACE TABLE dv30 (id INT, v TIMESTAMP)");
        answer(
            "INSERT INTO dv30 SELECT 1, '1970-01-02 00:00:00'::TIMESTAMP");
        answer(
            "CREATE OR REPLACE TABLE dv31 (id INT, v TIMESTAMP_NTZ(2))");
        answer(
            "INSERT INTO dv31 SELECT 1, '1970-01-01 00:00:01.999'::TIMESTAMP_NTZ(2)");
        answer(
            "CREATE OR REPLACE TABLE dv32 (id INT, v BINARY)");
        answer(
            "INSERT INTO dv32 SELECT 1, TO_BINARY('ABCDEF0123456789', 'HEX')");
        answer(
            "CREATE OR REPLACE TABLE dv33 (id INT, v BINARY)");
        answer(
            "INSERT INTO dv33 SELECT 1, TO_BINARY('SGVsbG8=', 'BASE64')");
        answer(
            "CREATE OR REPLACE TABLE dv34 (id INT, v VARBINARY)");
        answer(
            "INSERT INTO dv34 SELECT 1, TO_BINARY('0a', 'HEX')");
        answer(
            "CREATE OR REPLACE TABLE dv36 (id INT, v NUMBER(38,37))");
        answer(
            "INSERT INTO dv36 SELECT 1, 1.5");
        answer(
            "CREATE OR REPLACE TABLE dv37 (id INT, v NUMBER(10,2))");
        answer(
            "INSERT INTO dv37 SELECT 1, 0.05");
        answer(
            "CREATE OR REPLACE TABLE dv38 (id INT, v BOOLEAN)");
        answer(
            "INSERT INTO dv38 SELECT 1, NULL");
        answer(
            "CREATE OR REPLACE TABLE dv39 (id INT, v VARCHAR)");
        answer(
            "INSERT INTO dv39 SELECT 1, NULL");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 100000]",
            "MERGE INTO dv1 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e+06]",
            "MERGE INTO dv2 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.0001]",
            "MERGE INTO dv3 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e-05]",
            "MERGE INTO dv4 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 123457]",
            "MERGE INTO dv5 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1.23457e+06]",
            "MERGE INTO dv6 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1.5e-07]",
            "MERGE INTO dv7 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.3]",
            "MERGE INTO dv8 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.333333]",
            "MERGE INTO dv9 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 100]",
            "MERGE INTO dv10 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e+15]",
            "MERGE INTO dv11 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.5]",
            "MERGE INTO dv12 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -1.5]",
            "MERGE INTO dv13 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 12345.7]",
            "MERGE INTO dv14 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e+06]",
            "MERGE INTO dv15 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0.000123457]",
            "MERGE INTO dv16 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e-05]",
            "MERGE INTO dv17 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1e+100]",
            "MERGE INTO dv18 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -1.23457e+08]",
            "MERGE INTO dv19 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 2.5]",
            "MERGE INTO dv20 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 100000]",
            "MERGE INTO dv21 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 86400123456789]",
            "MERGE INTO dv22 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 86400500000000]",
            "MERGE INTO dv23 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -58966015999998500]",
            "MERGE INTO dv24 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1415579040]",
            "MERGE INTO dv25 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, -16383999998560]",
            "MERGE INTO dv26 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1445068800017794]",
            "MERGE INTO dv27 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 1500000]",
            "MERGE INTO dv28 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 86400000000000]",
            "MERGE INTO dv29 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 86400000000000]",
            "MERGE INTO dv30 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 199]",
            "MERGE INTO dv31 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0xabcdef0123456789]",
            "MERGE INTO dv32 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0x48656c6c6f]",
            "MERGE INTO dv33 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 0x0a]",
            "MERGE INTO dv34 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, "
                + "15000000000000000000000000000000000000]",
            "MERGE INTO dv36 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, 5]",
            "MERGE INTO dv37 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, NULL]",
            "MERGE INTO dv38 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
        assertAnswer(
            "REFUSED Duplicate row detected during DML action | Row Values: [1, NULL]",
            "MERGE INTO dv39 t USING us s ON t.id = s.id WHEN MATCHED THEN UPDATE SET id = s.n");
    }
}
