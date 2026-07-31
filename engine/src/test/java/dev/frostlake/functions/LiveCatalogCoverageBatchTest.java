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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live-catalog coverage batch: functions found in Snowflake's SHOW BUILTIN FUNCTIONS
 * but absent from the engine — bit operations, binary digests, variant AS_/IS_ families, array
 * builders, string utilities, TRY_ wrappers, ISO week parts, TIME_SLICE, context functions, and the
 * Snowflake-name aliases over existing implementations.
 */
public class LiveCatalogCoverageBatchTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString();
    }

    @Test
    public void bitOperationsAndAliases() {
        assertEquals(3L, ((Number) scalar("SELECT BITCOUNT(11)")).longValue());
        assertEquals(8L, ((Number) scalar("SELECT BIT_AND(12, 10)")).longValue());
        assertEquals(14L, ((Number) scalar("SELECT BIT_OR(12, 10)")).longValue());
        assertEquals(6L, ((Number) scalar("SELECT BIT_XOR(12, 10)")).longValue());
        assertEquals(8L, ((Number) scalar("SELECT BIT_SHIFTLEFT(2, 2)")).longValue());
    }

    @Test
    public void numericAdditions() {
        assertEquals(-5L, ((Number) scalar("SELECT NEGATE(5)")).longValue());
        // DIV is listed in SHOW BUILTIN FUNCTIONS but not invocable in Snowflake ("Unsupported
        // feature 'DIV'", live-verified) — it is deliberately not registered here either.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT DIV(10, 4)");
            }
        });
        assertEquals(7L, ((Number) scalar("SELECT REGR_VALX(1, 7)")).longValue());
        assertNull(scalar("SELECT REGR_VALX(NULL, 7)"));
        assertEquals(1L, ((Number) scalar("SELECT REGR_VALY(1, 7)")).longValue());
    }

    @Test
    public void stringAdditions() {
        assertEquals(2L, ((Number) scalar("SELECT RTRIMMED_LENGTH('ab   ')")).longValue());
        assertEquals(5, text("SELECT RANDSTR(5, 42)").length());
        assertEquals(text("SELECT RANDSTR(8, 7)"), text("SELECT RANDSTR(8, 7)"),
            "the same generator seed yields the same string");
        // NORMALIZE is min-max normalization in Snowflake — (x-lo)/(hi-lo), unclamped
        // (live-verified; it is NOT Unicode normalization).
        assertEquals(0.5, ((Number) scalar("SELECT NORMALIZE(5, 0, 10)")).doubleValue(), 0.0001);
        assertEquals(1.5, ((Number) scalar("SELECT NORMALIZE(15, 0, 10)")).doubleValue(), 0.0001);
        assertEquals(0.5, ((Number) scalar(
            "SELECT NORMALIZE('2024-01-16'::DATE, '2024-01-01'::DATE, '2024-01-31'::DATE)")).doubleValue(), 0.0001);
        assertEquals(55.0 / 384.0, ((Number) scalar("SELECT NORMALIZE('7', 0, 10)")).doubleValue(), 0.000001,
            "a VARCHAR maps to its bytes as a base-256 fraction over 384, range-independent (live-verified)");
        assertEquals("abc", text("SELECT TRY_VALIDATE_UTF8('abc')"));
        // REGEXP exists only as the infix operator (the call form is a syntax error, as in
        // Snowflake); the function spelling is RLIKE.
        assertEquals(Boolean.TRUE, scalar("SELECT 'abc' REGEXP 'a.c'"));
        assertEquals(Boolean.TRUE, scalar("SELECT RLIKE('abc', 'a.c')"));
    }

    @Test
    public void uuidAndBinaryReinterpretation() {
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
            text("SELECT TO_UUID('123E4567-E89B-12D3-A456-426614174000')"));
        // Live-verified: only the hyphenated 8-4-4-4-12 form is a UUID — hyphenless input errors.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_UUID('123E4567E89B12D3A456426614174000')");
            }
        });
        assertNull(scalar("SELECT TRY_TO_UUID('123E4567E89B12D3A456426614174000')"));
        assertNull(scalar("SELECT TRY_TO_UUID('not-a-uuid')"));
        assertEquals("hi", text("SELECT BINARY_AS_STRING(STRING_AS_BINARY('hi'))"));
        assertEquals(Boolean.TRUE,
            scalar("SELECT MD5_BINARY('a') = TO_BINARY(MD5('a'), 'HEX')"));
        assertEquals(Boolean.TRUE,
            scalar("SELECT SHA2_BINARY('a', 256) = TO_BINARY(SHA2('a', 256), 'HEX')"));
    }

    @Test
    public void tryWrappers() {
        assertNull(scalar("SELECT TRY_DECRYPT(ENCRYPT('secret', 'k1'), 'wrong-key')"));
        assertNotNull(scalar("SELECT TRY_DECRYPT(ENCRYPT('secret', 'k1'), 'k1')"));
        assertNull(scalar("SELECT TRY_PARSE_IP('not-an-ip', 'INET')"));
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP_NTZ('garbage')"));
    }

    @Test
    public void isoWeekPartsAndTimeSlice() {
        assertEquals(1L, ((Number) scalar("SELECT DAYOFWEEKISO('2026-01-05'::DATE)")).longValue(),
            "2026-01-05 is a Monday");
        assertEquals(2L, ((Number) scalar("SELECT WEEKISO('2026-01-05'::DATE)")).longValue());
        assertEquals(2026L, ((Number) scalar("SELECT YEAROFWEEKISO('2026-01-05'::DATE)")).longValue());
        assertTrue(text("SELECT TIME_SLICE('2026-07-31 14:23:00'::TIMESTAMP, 15, 'MINUTE')")
            .startsWith("2026-07-31T14:15"));
        assertTrue(text("SELECT TIME_SLICE('2026-07-31 14:23:00'::TIMESTAMP, 15, 'MINUTE', 'END')")
            .startsWith("2026-07-31T14:30"));
        assertEquals("2026-07-01", text("SELECT TIME_SLICE('2026-07-31'::DATE, 1, 'MONTH')"));
    }

    @Test
    public void variantAsAndIsFamilies() {
        assertEquals(Boolean.TRUE, scalar("SELECT IS_DECIMAL(PARSE_JSON('1'))"));
        assertEquals(Boolean.TRUE, scalar("SELECT IS_DOUBLE(PARSE_JSON('1.5'))"));
        assertEquals(Boolean.TRUE, scalar("SELECT IS_CHAR(PARSE_JSON('\"x\"'))"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_DATE(PARSE_JSON('\"2026-01-01\"'))"),
            "variant members carry dates as text in this engine's model");
        // AS_NUMBER defaults to NUMBER(38,0), so 2.5 ROUNDS to 3; an explicit scale keeps it
        // (both live-verified). AS_INTEGER of a fractional value is NULL, never rounded.
        assertEquals("3", text("SELECT AS_NUMBER(PARSE_JSON('2.5'))"));
        assertEquals("2.50", text("SELECT AS_NUMBER(PARSE_JSON('2.5'), 10, 2)"));
        assertNull(scalar("SELECT AS_INTEGER(PARSE_JSON('2.5'))"));
        assertEquals("x", text("SELECT AS_CHAR(PARSE_JSON('\"x\"'))"));
        assertNull(scalar("SELECT AS_DATE(PARSE_JSON('\"2026-01-01\"'))"));
    }

    @Test
    public void arrayBuildersAndJsonPathText() {
        assertEquals("[{\"$1\":1,\"$2\":\"a\"},{\"$1\":2,\"$2\":\"b\"}]",
            text("SELECT ARRAYS_ZIP(PARSE_JSON('[1,2]'), PARSE_JSON('[\"a\",\"b\"]'))"));
        assertEquals("{\"a\":1,\"b\":2}",
            text("SELECT ARRAYS_TO_OBJECT(PARSE_JSON('[\"a\",\"b\"]'), PARSE_JSON('[1,2]'))"));
        assertEquals("[7,7,7]", text("SELECT ARRAY_REPEAT(7, 3)"));
        assertEquals("2", text("SELECT JSON_EXTRACT_PATH_TEXT('{\"a\":{\"b\":[1,2]}}', 'a.b[1]')"));
        assertEquals("x", text("SELECT JSON_EXTRACT_PATH_TEXT('{\"k\":\"x\"}', 'k')"),
            "string results are unquoted");
    }

    @Test
    public void aggregateAliases() {
        engine.execute("CREATE TABLE lcb_t (n INTEGER)");
        engine.execute("INSERT INTO lcb_t VALUES (12), (10)");
        assertEquals("[10,12]", text("SELECT ARRAYAGG(n) WITHIN GROUP (ORDER BY n) FROM lcb_t"));
        assertEquals(2L, ((Number) scalar("SELECT HLL(n) FROM lcb_t")).longValue());
        assertEquals(8L, ((Number) scalar("SELECT BITANDAGG(n) FROM lcb_t")).longValue());
        assertEquals(8L, ((Number) scalar("SELECT BIT_AND_AGG(n) FROM lcb_t")).longValue());
    }

    @Test
    public void fromPartsAliases() {
        assertEquals("2026-03-15", text("SELECT DATEFROMPARTS(2026, 3, 15)"));
        assertTrue(text("SELECT TIMESTAMPNTZFROMPARTS(2026, 3, 15, 10, 30, 0)")
            .startsWith("2026-03-15T10:30"));
        assertTrue(text("SELECT TIMESTAMP_NTZ_FROM_PARTS(2026, 3, 15, 10, 30, 0)")
            .startsWith("2026-03-15T10:30"));
    }

    @Test
    public void contextAdditions() {
        assertEquals("ROLE", text("SELECT CURRENT_ROLE_TYPE()"));
        assertNotNull(scalar("SELECT CURRENT_SCHEMAS()"));
        assertNotNull(scalar("SELECT INVOKER_ROLE()"));
        assertEquals(Boolean.FALSE, scalar("SELECT IS_ROLE_IN_SESSION('NO_SUCH_ROLE')"));
        assertNull(scalar("SELECT CURRENT_TRANSACTION()"));
        // Embedded, the client address is loopback; on a live account it is environment-dependent.
        Assumptions.assumeFalse(isLiveSnowflake(), "environment-dependent");
        assertEquals("127.0.0.1", text("SELECT CURRENT_IP_ADDRESS()"));
    }
}
