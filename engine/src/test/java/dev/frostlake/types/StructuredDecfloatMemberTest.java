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
 * A structured ARRAY, OBJECT or MAP of DECFLOAT takes only text and DECFLOAT members, each read back a DECFLOAT;
 * a DECFLOAT handed to TYPEOF, TO_JSON or an IS_ function arrives as its VARIANT, a DECFLOAT is no MAP key, and a
 * semi-structured value written to a structured column is "incompatible types" (all live-verified).
 */
public class StructuredDecfloatMemberTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatMemberIsTextOrDecfloat() {
        assertAnswer("[\"1.5\"]|[\"1.5\"]|[undefined]",
            "SELECT ['1.5']::ARRAY(DECFLOAT), [DECFLOAT '1.5']::ARRAY(DECFLOAT), [NULL]::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT [1.5]::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT [1]::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT ['abc']::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT [TRUE]::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT PARSE_JSON('[1.5]')::ARRAY(DECFLOAT)");
        assertAnswer("[\"1.5\"]", "SELECT PARSE_JSON('[\"1.5\"]')::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT ARRAY_CONSTRUCT(1.5)::ARRAY(DECFLOAT)");
        assertAnswer("[\"1.5\",\"2\"]", "SELECT ARRAY_CONSTRUCT('1.5', DECFLOAT '2')::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT ARRAY_CONSTRUCT('1.5', 2)::ARRAY(DECFLOAT)");
        assertAnswer("[\"1e400\"]|1e400", "SELECT ['1e400']::ARRAY(DECFLOAT), ['1e400']::ARRAY(DECFLOAT)[0]::VARCHAR");
        assertAnswer("1.2345678901234567890123456789012345679",
            "SELECT ['1.2345678901234567890123456789012345678901']::ARRAY(DECFLOAT)[0]::VARCHAR");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT {'a': 1.5}::OBJECT(a DECFLOAT)");
        assertAnswer("{\"a\":\"1.5\"}", "SELECT {'a': '1.5'}::OBJECT(a DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT {'a': 1.5}::MAP(VARCHAR, DECFLOAT)");
        assertAnswer("{\"a\":\"1.5\"}", "SELECT {'a': '1.5'}::MAP(VARCHAR, DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT {'a': 1.5::FLOAT}::OBJECT(a DECFLOAT)");
        assertAnswer("{\"a\":\"1.5\",\"b\":2}", "SELECT {'a': '1.5', 'b': 2}::OBJECT(a DECFLOAT, b INT)");
        assertAnswer("DECFLOAT|DECFLOAT|DECFLOAT|DECIMAL",
            "SELECT TYPEOF(DECFLOAT '1.5'), TYPEOF(1.5::DECFLOAT), TYPEOF(DECFLOAT '1'), TYPEOF(1.5::NUMBER(10,2))");
        assertAnswer("DECFLOAT|DECFLOAT(38)[DECFLOAT18]",
            "SELECT TYPEOF(['1.5']::ARRAY(DECFLOAT)[0]), SYSTEM$TYPEOF(['1.5']::ARRAY(DECFLOAT)[0])");
        assertAnswer("DECFLOAT|DECFLOAT",
            "SELECT TYPEOF({'a':'1.5'}::OBJECT(a DECFLOAT):a), TYPEOF({'a':'1.5'}::OBJECT(a DECFLOAT)['a'])");
        assertAnswer("DECFLOAT|DECFLOAT(38)[DECFLOAT18]",
            "SELECT TYPEOF({'a':'1.5'}::MAP(VARCHAR,DECFLOAT)['a']), SYSTEM$TYPEOF({'a':'1.5'}::MAP(VARCHAR,"
                + "DECFLOAT)['a'])");
        assertAnswer("DECIMAL|DECFLOAT",
            "SELECT TYPEOF(['1.5']::ARRAY(NUMBER(10,2))[0]), TYPEOF(GET(['1.5']::ARRAY(DECFLOAT), 0))");
        assertAnswer("2.5|DECFLOAT(38)[DECFLOAT18]",
            "SELECT (['1.5']::ARRAY(DECFLOAT)[0] + 1)::VARCHAR, SYSTEM$TYPEOF(['1.5']::ARRAY(DECFLOAT)[0] + 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(DECFLOAT(38)), NUMBER(2,1))",
            "SELECT ARRAY_APPEND(['1.5']::ARRAY(DECFLOAT), 2.5)");
        assertAnswer("[\"1.5\",\"2.5\"]", "SELECT ARRAY_APPEND(['1.5']::ARRAY(DECFLOAT), DECFLOAT '2.5')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(DECFLOAT(38)), VARCHAR(3))",
            "SELECT ARRAY_APPEND(['1.5']::ARRAY(DECFLOAT), '2.5')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_APPEND': (ARRAY(DECFLOAT(38)), FLOAT)",
            "SELECT ARRAY_APPEND(['1.5']::ARRAY(DECFLOAT), 2.5::FLOAT)");
        assertAnswer("{\"a\":\"2.25\"}", "SELECT OBJECT_INSERT({'a':'1.5'}::OBJECT(a DECFLOAT), 'a', 2.25, TRUE)");
        assertAnswer("{\"a\":\"2.25\"}", "SELECT OBJECT_INSERT({'a':'1.5'}::OBJECT(a DECFLOAT), 'a', '2.25', TRUE)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT ['1.5']::ARRAY(DECFLOAT)::ARRAY(NUMBER(10,2)), ['1.5']::ARRAY(DECFLOAT)::ARRAY(VARCHAR)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT [1.5]::ARRAY(NUMBER(10,2))::ARRAY(DECFLOAT)");
        assertAnswer("Table ST3 successfully created.", "CREATE TABLE st3 (ar ARRAY(DECFLOAT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(DECFLOAT(38))]",
            "INSERT INTO st3 SELECT ARRAY_CONSTRUCT(DECFLOAT '1e-50')");
        answer("INSERT INTO st3 VALUES (ARRAY_CONSTRUCT(DECFLOAT '1'))");
        assertAnswer("1", "INSERT INTO st3 SELECT ['1e-50']::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED DML operation to table ST3 failed on column AR with error: Typed object schema mismatch "
                + "in conversion",
            "INSERT INTO st3 SELECT ['1']::ARRAY(NUMBER)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(DECFLOAT(38))]",
            "UPDATE st3 SET ar = ARRAY_CONSTRUCT(1)");
        assertAnswer("[\"1e-50\"]|ARRAY(DECFLOAT(38))[LOB]|DECFLOAT",
            "SELECT ar, SYSTEM$TYPEOF(ar), TYPEOF(ar[0]) FROM st3");
        assertAnswer("Table ST4 successfully created.", "CREATE TABLE st4 (ar ARRAY(INT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(38,0))]",
            "INSERT INTO st4 SELECT ARRAY_CONSTRUCT(1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(1)] in VALUES clause",
            "INSERT INTO st4 VALUES (ARRAY_CONSTRUCT(1))");
        assertAnswer("Table ST5 successfully created.", "CREATE TABLE st5 (o OBJECT(a DECFLOAT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [OBJECT] and [OBJECT(a DECFLOAT(38))]",
            "INSERT INTO st5 SELECT OBJECT_CONSTRUCT('a', DECFLOAT '1')");
        assertAnswer("Table ST6 successfully created.", "CREATE TABLE st6 (m MAP(VARCHAR, DECFLOAT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [OBJECT] and [MAP(VARCHAR(16777216), "
                + "DECFLOAT(38))]",
            "INSERT INTO st6 SELECT OBJECT_CONSTRUCT('a', DECFLOAT '1')");
        assertAnswer("Table ST7 successfully created.", "CREATE TABLE st7 (o OBJECT(a INT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [OBJECT] and [OBJECT(a NUMBER(38,0))]",
            "INSERT INTO st7 SELECT OBJECT_CONSTRUCT('a', 1)");
        assertAnswer("REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                + "input branches, expected ARRAY(DECFLOAT(38)), got ARRAY",
            "SELECT ARRAY_CONSTRUCT(1) UNION ALL SELECT ['1']::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT('1') AS "
                + "ARRAY(DECFLOAT(38)))' of type [ARRAY(DECFLOAT(38))] into expected type [ARRAY]",
            "SELECT ARRAY_CONSTRUCT(1) = ['1']::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT('1') AS "
                + "ARRAY(DECFLOAT(38)))' of type [ARRAY(DECFLOAT(38))] into expected type [ARRAY]",
            "SELECT IFF(TRUE, ARRAY_CONSTRUCT(1), ['1']::ARRAY(DECFLOAT))");
    }

    @Test
    public void aStructuredWriteAndAVariantReadAreAsTheAccountMakesThem() {
        assertAnswer("Table ST4 successfully created.", "CREATE TABLE st4 (ar ARRAY(INT))");
        assertAnswer("Table ST7 successfully created.", "CREATE TABLE st7 (o OBJECT(a INT))");
        assertAnswer("Table ST3 successfully created.", "CREATE TABLE st3 (ar ARRAY(DECFLOAT))");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY(NUMBER(38,0)) but got NUMBER(1,0) for column AR",
            "INSERT INTO st4 SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY(NUMBER(38,0)) but got VARCHAR(1) for column AR",
            "INSERT INTO st4 SELECT 'x'");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [VARIANT] and [ARRAY(NUMBER(38,0))]",
            "INSERT INTO st4 SELECT PARSE_JSON('[1]')");
        answer("INSERT INTO st4 SELECT ['a']::ARRAY(VARCHAR)");
        assertAnswer("1", "INSERT INTO st4 SELECT [1]::ARRAY(INT)");
        assertAnswer("1", "INSERT INTO st4 SELECT NULL");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [OBJECT(a NUMBER(38,0))]",
            "INSERT INTO st7 SELECT ARRAY_CONSTRUCT(1)");
        answer("INSERT INTO st7 SELECT {'a':1}::MAP(VARCHAR, INT)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [VARIANT] and [OBJECT(a NUMBER(38,0))]",
            "INSERT INTO st7 SELECT TO_VARIANT(1)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [VARIANT] and [ARRAY(NUMBER(38,0))]",
            "UPDATE st4 SET ar = PARSE_JSON('[1]')");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(38,0))]",
            "MERGE INTO st4 USING (SELECT ARRAY_CONSTRUCT(1) a) s ON FALSE WHEN NOT MATCHED THEN INSERT (ar) VALUES "
                + "(s.a)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(38,0))]",
            "CREATE TABLE st8 (ar ARRAY(INT)) AS SELECT ARRAY_CONSTRUCT(1)");
        assertAnswer("Table SA successfully created.", "CREATE TABLE sa (a ARRAY)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY but got ARRAY(NUMBER(38,0)) for column A",
            "INSERT INTO sa SELECT [1]::ARRAY(INT)");
        assertAnswer("", "SELECT a, SYSTEM$TYPEOF(a) FROM sa");
        answer("INSERT INTO st3 SELECT ['1']::ARRAY(VARCHAR)");
        answer("SELECT ar FROM st3");
        assertAnswer("[\"1.5\"]", "SELECT ['1.5']::ARRAY(DECFLOAT)::ARRAY(VARCHAR)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT ['1.5']::ARRAY(DECFLOAT)::ARRAY(NUMBER(10,2))");
        assertAnswer("[\"1\"]", "SELECT ['1']::ARRAY(VARCHAR)::ARRAY(DECFLOAT)");
        assertAnswer("{\"a\":\"1\"}", "SELECT OBJECT_CONSTRUCT('a', DECFLOAT '1')::OBJECT(a DECFLOAT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion",
            "SELECT {'a': NULL}::OBJECT(a DECFLOAT), ARRAY_CONSTRUCT(NULL, '2')::ARRAY(DECFLOAT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type DECFLOAT(38)'.",
            "SELECT {'1.5':'a'}::MAP(DECFLOAT, VARCHAR)");
        assertAnswer("[\"1.5\",undefined]", "SELECT PARSE_JSON('[\"1.5\", null]')::ARRAY(DECFLOAT)");
        assertAnswer("DECFLOAT", "SELECT TYPEOF(x) FROM (SELECT DECFLOAT '2' x)");
        assertAnswer("DECFLOAT", "SELECT TYPEOF(SUM(x)) FROM (SELECT DECFLOAT '2' x)");
        assertAnswer("DECFLOAT|DECFLOAT|DECFLOAT",
            "SELECT TYPEOF(IFF(TRUE, DECFLOAT '1', NULL)), TYPEOF(TO_DECFLOAT('3')), TYPEOF(DECFLOAT '1' + 1)");
        assertAnswer("false|false|false|false",
            "SELECT IS_DECIMAL(DECFLOAT '1.5'), IS_INTEGER(DECFLOAT '1'), IS_DOUBLE(DECFLOAT '1.5'), "
                + "IS_REAL(DECFLOAT '1')");
        assertAnswer("DECFLOAT|DECFLOAT",
            "SELECT TYPEOF(GET(['1.5']::ARRAY(DECFLOAT), 0)), TYPEOF(['1.5']::ARRAY(DECFLOAT)[0]::VARIANT)");
        assertAnswer("1|2", "SELECT ARRAY_SIZE(['1.5']::ARRAY(DECFLOAT)), ['1.5', '2']::ARRAY(DECFLOAT)[1]::VARCHAR");
        assertAnswer("\"1.5\"|1.5", "SELECT TO_JSON(DECFLOAT '1.5'), TO_VARCHAR(TO_VARIANT(DECFLOAT '1.5'))");
    }
}
