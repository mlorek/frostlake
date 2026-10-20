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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A member of a structured ARRAY, OBJECT or MAP holds the type the structured value declares for it, however it got
 * there: a whole element of a NUMBER(p,s) reads as that NUMBER and computes in fixed point, a value OBJECT_INSERT or
 * ARRAY_APPEND puts in is held to the member's precision or length as the cast to the structured type holds it, a
 * NUMBER-keyed MAP's keys are the numbers they convert to, and a DECFLOAT member is a DECFLOAT with all its digits.
 */
public class StructuredMemberConversionTest extends MeasuredCellsTestSupport {

    /** FL-476: a whole element under a scaled NUMBER reads as the NUMBER, as its neighbour with a fraction does. */
    @Test
    public void aWholeElementOfAScaledNumberReadsAsThatNumber() {
        assertCells(new String[][] {
            {"SELECT a[0] + 0 x0, a[1] + 0 x1, a[1] / 3 d1, a[1]::VARCHAR t1 "
                + "FROM (SELECT [1.5,2]::ARRAY(NUMBER(10,2)) a)", "1.50|2.00|0.66666667|2.00"},
            {"SELECT a[1] + 1 x1 FROM (SELECT [1.5, 12345678901234567]::ARRAY(NUMBER(38,2)) a)",
                "12345678901234568.00"},
            {"SELECT m['a'] + 0 x, m['a'] / 3 d, o:a + 0 y, o:a / 3 e "
                + "FROM (SELECT {'a':2}::MAP(VARCHAR,NUMBER(10,2)) m, {'a':2}::OBJECT(a NUMBER(10,2)) o)",
                "2.00|0.66666667|2.00|0.66666667"},
            {"SELECT GET(a, 1) + 0, GET_PATH(o, 'b[1]') / 3 "
                + "FROM (SELECT [1.5,2]::ARRAY(NUMBER(10,2)) a, {'b':[1.5,2]}::OBJECT(b ARRAY(NUMBER(10,2))) o)",
                "2.00|0.66666667"},
            {"SELECT f.value + 0 FROM (SELECT [1.5,2]::ARRAY(NUMBER(10,2)) a), TABLE(FLATTEN(a)) f", "1.50 ;; 2.00"},
            {"CREATE OR REPLACE TABLE smc_ta (a ARRAY(NUMBER(10,2)))", "Table SMC_TA successfully created."},
            {"INSERT INTO smc_ta SELECT [1.5, 2, 3]::ARRAY(NUMBER(10,2))", "1"},
            {"CREATE OR REPLACE TABLE smc_tb AS SELECT a[1] e FROM smc_ta", "Table SMC_TB successfully created."},
            {"SELECT a[1]::VARCHAR, a[2] || 'x', a[1] / 3 FROM smc_ta", "2.00|3.00x|0.66666667"},
            {"SELECT e::VARCHAR FROM smc_tb", "2.00"},
        });
        assertShape("X1:NUMBER(11,2)", "SELECT a[1] + 0 x1 FROM (SELECT [1.5,2]::ARRAY(NUMBER(10,2)) a)");
    }

    /**
     * FL-477: OBJECT_INSERT's update casts to the field's declared type with its precision and length, and
     * ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT hold a text to the element's length, so neither puts in a value
     * the cast to the structured type refuses and a CTAS no longer stores one.
     */
    @Test
    public void anInsertedValueIsHeldToTheMembersPrecisionAndLength() {
        assertCells(new String[][] {
            {"SELECT OBJECT_INSERT({'a':1}::OBJECT(a NUMBER(3,0)), 'a', 12345, TRUE) x",
                "REFUSED Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345"},
            {"SELECT OBJECT_INSERT({'a':'x'}::OBJECT(a VARCHAR(2)), 'a', 'toolong', TRUE) x",
                "REFUSED String 'toolong' is too long and would be truncated"},
            {"SELECT OBJECT_INSERT({'a':1}::OBJECT(a INT), 'a', 1e40, TRUE) x",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, "
                + "value 10000000000000000303786028427003666890752"},
            {"SELECT OBJECT_INSERT({'a':1}::OBJECT(a NUMBER(3,0)), 'a', 123, TRUE) x", "{\"a\":123}"},
            {"SELECT OBJECT_INSERT({'a':'x', 'b':2}::OBJECT(a VARCHAR(10), b NUMBER(5,2)), 'b', 1.239, TRUE) x",
                "{\"a\":\"x\",\"b\":1.24}"},
            {"SELECT OBJECT_INSERT({'a':'x'}::OBJECT(a VARCHAR(2)), 'a', 'ok', TRUE) x", "{\"a\":\"ok\"}"},
            {"SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'toolong') x",
                "REFUSED String 'toolong' is too long and would be truncated"},
            {"SELECT ARRAY_PREPEND(['a']::ARRAY(VARCHAR(2)), 'toolong') x",
                "REFUSED String 'toolong' is too long and would be truncated"},
            {"SELECT ARRAY_INSERT(['a']::ARRAY(VARCHAR(2)), 0, 'toolong') x",
                "REFUSED String 'toolong' is too long and would be truncated"},
            {"SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'ok') x", "[\"a\",\"ok\"]"},
            {"SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 12345) x",
                "REFUSED String '12345' is too long and would be truncated"},
            {"CREATE OR REPLACE TABLE smc_ct AS SELECT OBJECT_INSERT({'a':'x'}::OBJECT(a VARCHAR(2)), 'a', 'toolong', "
                + "TRUE) y",
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.SMC_CT failed on column Y with error: "
                + "String 'toolong' is too long and would be truncated"},
            {"CREATE OR REPLACE TABLE smc_ct AS SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'toolong') y",
                "REFUSED String 'toolong' is too long and would be truncated"},
        });
    }

    /** FL-478: a cast to a NUMBER-keyed MAP converts each key to its number, and refuses one that is no number. */
    @Test
    public void aNumberKeyedMapCastConvertsItsKeys() {
        assertCells(new String[][] {
            {"SELECT m, m[1] a, MAP_CONTAINS_KEY(1, m) c, MAP_KEYS(m) k, GET(m, 1) g "
                + "FROM (SELECT {'01':'x'}::MAP(INT,VARCHAR) m)", "{\"1\":\"x\"}|x|true|[1]|x"},
            {"SELECT m, m[1] a FROM (SELECT {'1.0':'x'}::MAP(INT,VARCHAR) m)", "{\"1\":\"x\"}|x"},
            {"SELECT m, m[1] a FROM (SELECT {' 1':'x', '+2':'y'}::MAP(INT,VARCHAR) m)", "{\"1\":\"x\",\"2\":\"y\"}|x"},
            {"SELECT m, m[1] FROM (SELECT PARSE_JSON('{\"01\":\"x\"}')::MAP(INT,VARCHAR) m)", "{\"1\":\"x\"}|x"},
            {"SELECT m FROM (SELECT {'abc':'x'}::MAP(INT,VARCHAR) m)",
                "REFUSED Typed object schema mismatch in conversion"},
            {"SELECT m FROM (SELECT {'1000':'x'}::MAP(NUMBER(3,0),VARCHAR) m)",
                "REFUSED Typed object schema mismatch in conversion"},
            {"SELECT {'01':'x', '1':'y'}::MAP(INT,VARCHAR) m", "REFUSED Duplicate field key '1'"},
            {"SELECT {'01':'x'}::MAP(VARCHAR,VARCHAR) m", "{\"01\":\"x\"}"},
            {"CREATE OR REPLACE TABLE smc_mt (m MAP(INT, VARCHAR))", "Table SMC_MT successfully created."},
            {"INSERT INTO smc_mt SELECT {'01':'x'}::MAP(INT,VARCHAR)", "1"},
            {"SELECT m, m[1], MAP_SIZE(m) FROM smc_mt", "{\"1\":\"x\"}|x|1"},
            {"SELECT f.key, f.value FROM smc_mt, TABLE(FLATTEN(m)) f", "1|x"},
        });
    }

    /**
     * FL-486: a DECFLOAT member is a DECFLOAT, not a FLOAT whose name test it passed: it keeps its digits, its range
     * and its type, a structured value already holding DECFLOATs casts, and a column of the type takes one.
     */
    @Test
    public void aDecfloatMemberKeepsItsDigitsAndItsType() {
        assertCells(new String[][] {
            {"SELECT [1.5]::ARRAY(DECFLOAT), {'a': 1.5}::OBJECT(a DECFLOAT), {'a': 1.5}::MAP(VARCHAR, DECFLOAT)",
                "[\"1.5\"]|{\"a\":\"1.5\"}|{\"a\":\"1.5\"}"},
            {"SELECT ['1.2345678901234567890123']::ARRAY(DECFLOAT)[0]::VARCHAR, "
                + "SYSTEM$TYPEOF(['1.5']::ARRAY(DECFLOAT)[0]), TYPEOF(['1.5']::ARRAY(DECFLOAT)[0]::VARIANT)",
                "1.2345678901234567890123|DECFLOAT(38)[DECFLOAT18]|DECFLOAT"},
            {"SELECT ['1e400']::ARRAY(DECFLOAT), ['1e400']::ARRAY(DECFLOAT)[0]::VARCHAR", "[\"1e400\"]|1e400"},
            {"SELECT ARRAY_CONSTRUCT(DECFLOAT '2')::ARRAY(DECFLOAT) a, a[0] + 1", "[\"2\"]|3"},
            {"SELECT {'a': '1.2345678901234567890123'}::MAP(VARCHAR, DECFLOAT)['a'] + 1",
                "2.2345678901234567890123"},
            {"SELECT ARRAY_APPEND([1.5]::ARRAY(DECFLOAT), DECFLOAT '2.5') a, a[1] + 0", "[\"1.5\",\"2.5\"]|2.5"},
            {"SELECT OBJECT_INSERT({'a':1.5}::OBJECT(a DECFLOAT), 'a', 2.25, TRUE) o, o:a + 0",
                "{\"a\":\"2.25\"}|2.25"},
            {"CREATE OR REPLACE TABLE smc_st3 (ar ARRAY(DECFLOAT))", "Table SMC_ST3 successfully created."},
            {"INSERT INTO smc_st3 SELECT ARRAY_CONSTRUCT(DECFLOAT '1e-50')::ARRAY(DECFLOAT)", "1"},
            {"INSERT INTO smc_st3 SELECT ['1.2345678901234567890123']::ARRAY(DECFLOAT)", "1"},
            {"SELECT ar, ar[0]::VARCHAR, SYSTEM$TYPEOF(ar[0]) FROM smc_st3 ORDER BY ar[0]",
                "[\"1e-50\"]|1e-50|DECFLOAT(38)[DECFLOAT18] ;; "
                + "[\"1.2345678901234567890123\"]|1.2345678901234567890123|DECFLOAT(38)[DECFLOAT18]"},
        });
        assertAnswer("ARRAY(DECFLOAT(38))[LOB]|OBJECT(a DECFLOAT(38))[LOB]|MAP(VARCHAR, DECFLOAT(38))[LOB]",
            "SELECT SYSTEM$TYPEOF([1.5]::ARRAY(DECFLOAT)), SYSTEM$TYPEOF({'a': 1.5}::OBJECT(a DECFLOAT)), "
                + "SYSTEM$TYPEOF({'a': 1.5}::MAP(VARCHAR, DECFLOAT))");
    }
}
