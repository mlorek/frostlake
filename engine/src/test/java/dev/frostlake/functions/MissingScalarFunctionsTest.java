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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MissingScalarFunctionsTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    // ── String extras ────────────────────────────────────────────────────────

    @Test
    public void testInstr() {
        assertEquals(4L, q("SELECT CHARINDEX('lo', 'hello world')"));
    }
    @Test
    public void testInstrNotFound() {
        assertEquals(0L, q("SELECT CHARINDEX('xyz', 'hello')"));
    }
    @Test
    public void testInstrWithStart() {
        assertEquals(7L, q("SELECT CHARINDEX('lo', 'hello lo world', 5)"));
    }
    @Test
    public void testRegexpInstr() {
        assertEquals(4L, q("SELECT REGEXP_INSTR('hello world', 'lo')"));
    }
    @Test
    public void testRegexpInstrNotFound() {
        assertEquals(0L, q("SELECT REGEXP_INSTR('hello', '[0-9]+')"));
    }
    @Test
    public void testTryBase64DecodeValid() {
        assertEquals("hello", q("SELECT TRY_BASE64_DECODE_STRING('aGVsbG8=')"));
    }
    @Test
    public void testTryBase64DecodeInvalid() {
        assertNull(q("SELECT TRY_BASE64_DECODE_STRING('not!!valid@@base64%%')"));
    }
    @Test
    public void testTryHexDecodeValid() {
        assertEquals("hi", q("SELECT TRY_HEX_DECODE_STRING('6869')"));
    }
    @Test
    public void testTryHexDecodeInvalid() {
        assertNull(q("SELECT TRY_HEX_DECODE_STRING('ZZ')"));
    }
    @Test
    public void testSha1() {
        final String hash = q("SELECT SHA1('hello')").toString();
        assertEquals(40, hash.length());
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", hash);
    }

    // ── Numeric extras ───────────────────────────────────────────────────────

    @Test
    public void testGreatestIgnoreNulls() {
        assertEquals(3L, ((Number) q("SELECT GREATEST_IGNORE_NULLS(1, NULL, 3, NULL, 2)")).longValue());
    }
    @Test
    public void testGreatestIgnoreNullsAllNull() {
        assertNull(q("SELECT GREATEST_IGNORE_NULLS(NULL, NULL)"));
    }
    @Test
    public void testLeastIgnoreNulls() {
        assertEquals(1L, ((Number) q("SELECT LEAST_IGNORE_NULLS(NULL, 1, 3, NULL)")).longValue());
    }

    // ── Date/time extras ─────────────────────────────────────────────────────

    @Test
    public void testGetdate() {
        assertNotNull(q("SELECT GETDATE()"));
    }
    @Test
    public void testDayname() {
        // 2024-01-01 is a Monday
        assertEquals("Mon", q("SELECT DAYNAME('2024-01-01')"));
    }
    @Test
    public void testMonthname() {
        assertEquals("Jan", q("SELECT MONTHNAME('2024-01-15')"));
        assertEquals("Dec", q("SELECT MONTHNAME('2024-12-25')"));
    }
    @Test
    public void testConvertTimezone() {
        // UTC noon → US/Eastern (UTC-4 in June) = 08:00
        final Object r = q("SELECT CONVERT_TIMEZONE('UTC', 'America/New_York', '2024-06-15T12:00:00')");
        assertNotNull(r);
        assertTrue(r.toString().contains("08:00"), "Expected 08:00, got: " + r);
    }

    // ── TRY_ conversions ─────────────────────────────────────────────────────

    @Test
    public void testTryToDateValid() {
        assertNotNull(q("SELECT TRY_TO_DATE('2024-06-15')"));
    }
    @Test
    public void testTryToDateInvalid() {
        assertNull(q("SELECT TRY_TO_DATE('not-a-date')"));
    }
    @Test
    public void testTryToTimestampValid() {
        assertNotNull(q("SELECT TRY_TO_TIMESTAMP_NTZ('2024-06-15T10:30:00')"));
    }
    @Test
    public void testTryToTimestampInvalid() {
        assertNull(q("SELECT TRY_TO_TIMESTAMP_NTZ('not-a-ts')"));
    }
    @Test
    public void testTryToNumberValid() {
        // TRY_TO_NUMBER carries TO_NUMBER's NUMBER(38,0) default — no scale rounds to a whole
        // number; an explicit (precision, scale) keeps the fraction.
        assertEquals(123.0, ((Number) q("SELECT TRY_TO_NUMBER('123.45')")).doubleValue(), 0.001);
        assertEquals(123.45, ((Number) q("SELECT TRY_TO_NUMBER('123.45', 10, 2)")).doubleValue(), 0.001);
    }
    @Test
    public void testTryToNumberInvalid() {
        assertNull(q("SELECT TRY_TO_NUMBER('abc')"));
    }
    @Test
    public void testTryToDoubleValid() {
        assertEquals(3.14, ((Number) q("SELECT TRY_TO_DOUBLE('3.14')")).doubleValue(), 0.001);
    }
    @Test
    public void testTryToDoubleInvalid() {
        assertNull(q("SELECT TRY_TO_DOUBLE('abc')"));
    }
    @Test
    public void testTryToBooleanTrue() {
        assertEquals(true, q("SELECT TRY_TO_BOOLEAN('true')"));
    }
    @Test
    public void testTryToBooleanFalse() {
        assertEquals(false, q("SELECT TRY_TO_BOOLEAN('0')"));
    }
    @Test
    public void testTryToBooleanInvalid() {
        assertNull(q("SELECT TRY_TO_BOOLEAN('maybe')"));
    }
    @Test
    public void testToBoolean() {
        assertEquals(true, q("SELECT TO_BOOLEAN('yes')"));
        assertEquals(false, q("SELECT TO_BOOLEAN('off')"));
    }
    @Test
    public void testToDouble() {
        assertEquals(3.14, ((Number) q("SELECT TO_DOUBLE('3.14')")).doubleValue(), 0.001);
    }
    @Test
    public void testToInteger() {
        assertEquals(42L, ((Number) q("SELECT TO_NUMBER('42')")).longValue());
    }

    // ── Semi-structured / JSON ────────────────────────────────────────────────

    @Test
    public void testParseJson() {
        final Object r = q("SELECT PARSE_JSON('{\"a\":1}')");
        assertNotNull(r);
    }
    @Test
    public void testTryParseJsonValid() {
        assertNotNull(q("SELECT TRY_PARSE_JSON('{\"a\":1}')"));
    }
    @Test
    public void testTryParseJsonInvalid() {
        assertNull(q("SELECT TRY_PARSE_JSON('not json')"));
    }
    @Test
    public void testToVariant() {
        assertNotNull(q("SELECT TO_VARIANT(42)"));
    }
    @Test
    public void testToJson() {
        assertNotNull(q("SELECT TO_JSON(42)"));
    }
    @Test
    public void testTypeOfInteger() {
        assertEquals("INTEGER", q("SELECT TYPEOF(42)"));
    }
    @Test
    public void testTypeOfString() {
        // Snowflake reports strings as VARCHAR (never "TEXT").
        assertEquals("VARCHAR", q("SELECT TYPEOF(TO_VARIANT('hello'))"));
    }
    @Test
    public void testIsObject() {
        assertEquals(true, q("SELECT IS_OBJECT(PARSE_JSON('{\"a\":1}'))"));
        assertEquals(false, q("SELECT IS_OBJECT(PARSE_JSON('[1,2]'))"));
    }
    @Test
    public void testIsArray() {
        assertEquals(true, q("SELECT IS_ARRAY(PARSE_JSON('[1,2,3]'))"));
        assertEquals(false, q("SELECT IS_ARRAY(PARSE_JSON('{\"a\":1}'))"));
    }
    @Test
    public void testIsNullValue() {
        assertEquals(true, q("SELECT IS_NULL_VALUE(PARSE_JSON('null'))"));
        assertEquals(false, q("SELECT IS_NULL_VALUE(PARSE_JSON('1'))"));
    }
    @Test
    public void testIsInteger() {
        assertEquals(true, q("SELECT IS_INTEGER(PARSE_JSON('42'))"));
        assertEquals(false, q("SELECT IS_INTEGER(PARSE_JSON('3.14'))"));
    }
    @Test
    public void testIsVarchar() {
        assertEquals(true, q("SELECT IS_VARCHAR(PARSE_JSON('\"hello\"'))"));
        assertEquals(false, q("SELECT IS_VARCHAR(PARSE_JSON('42'))"));
    }
    @Test
    public void testIsBoolean() {
        assertEquals(true, q("SELECT IS_BOOLEAN(PARSE_JSON('true'))"));
        assertEquals(false, q("SELECT IS_BOOLEAN(PARSE_JSON('42'))"));
    }
    @Test
    public void testStripNullValue() {
        assertNull(q("SELECT STRIP_NULL_VALUE(PARSE_JSON('null'))"));
        assertNotNull(q("SELECT STRIP_NULL_VALUE(PARSE_JSON('42'))"));
    }

    // ── Context functions ─────────────────────────────────────────────────────

    @Test
    public void testCurrentSchema() {
        assertNotNull(q("SELECT CURRENT_SCHEMA()"));
    }
    @Test
    public void testCurrentWarehouse() {
        // may be null if no warehouse set, just ensure it doesn't throw
        engine.executeQuery("SELECT CURRENT_WAREHOUSE()");
    }
    @Test
    public void testCurrentRegion() {
        assertNotNull(q("SELECT CURRENT_REGION()"));
    }
}
