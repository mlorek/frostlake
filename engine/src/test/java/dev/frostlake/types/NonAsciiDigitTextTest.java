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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text converts to a number by its ASCII digits alone, on every path: a forty-eight written in Arabic-Indic or
 * fullwidth digits is refused as TO_NUMBER refuses it - by a cast, a comparison, an arithmetic operator, a numeric
 * function's argument and a write to a NUMBER column alike - and reads as no hexadecimal digits for a BINARY.
 * Snowflake's CAST documentation makes a cast to a number its TO_ conversion, and its binary input is {@code 0-9} and
 * {@code A-F}.
 */
public class NonAsciiDigitTextTest {

    /** Forty-eight in Arabic-Indic digits. */
    private static final String ARABIC = "٤٨";

    /** Forty-eight in fullwidth digits. */
    private static final String FULLWIDTH = "４８";

    /** Two in Arabic-Indic digits. */
    private static final String TWO = "٢";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (n NUMBER, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (48, '" + TWO + "')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** What a statement answers: its rows, or its refusal. */
    private String answer(final String sql) {
        final ResultSet rs;
        try {
            rs = engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private static String unrecognized(final String text) {
        return "REFUSED Numeric value '" + text + "' is not recognized";
    }

    @Test
    public void aCastRefusesAsTheConversionDoes() {
        assertEquals(unrecognized(ARABIC), answer("SELECT TO_NUMBER('" + ARABIC + "')"));
        assertEquals(unrecognized(ARABIC), answer("SELECT '" + ARABIC + "'::NUMBER"));
        assertEquals(unrecognized(ARABIC), answer("SELECT '" + ARABIC + "'::NUMBER(10,2)"));
        assertEquals(unrecognized(ARABIC), answer("SELECT CAST('" + ARABIC + "' AS INT)"));
        assertEquals(unrecognized(ARABIC), answer("SELECT '" + ARABIC + "'::FLOAT"));
        assertEquals(unrecognized(ARABIC), answer("SELECT '" + ARABIC + "'::DECFLOAT"));
        assertEquals(unrecognized(FULLWIDTH), answer("SELECT '" + FULLWIDTH + "'::NUMBER"));
        assertEquals("null|null", answer("SELECT TRY_TO_NUMBER('" + ARABIC + "'), TRY_CAST('" + ARABIC
            + "' AS NUMBER)"));
    }

    @Test
    public void aComparisonRefuses() {
        assertEquals(unrecognized(ARABIC), answer("SELECT n = '" + ARABIC + "' FROM t"));
        assertEquals(unrecognized(ARABIC), answer("SELECT n IN ('" + ARABIC + "') FROM t"));
        assertEquals(unrecognized(FULLWIDTH), answer("SELECT * FROM t WHERE n = '" + FULLWIDTH + "'"));
        assertEquals(unrecognized(ARABIC), answer("SELECT n BETWEEN '" + ARABIC + "' AND 50 FROM t"));
    }

    @Test
    public void anOperatorRefuses() {
        assertEquals(unrecognized(ARABIC), answer("SELECT n + '" + ARABIC + "' FROM t"));
        assertEquals(unrecognized(ARABIC), answer("SELECT n * '" + ARABIC + "' FROM t"));
        assertEquals(unrecognized(ARABIC), answer("SELECT '" + ARABIC + "' + 1"));
        assertEquals(unrecognized(TWO), answer("SELECT '" + TWO + "' % 2"));
        assertEquals(unrecognized(TWO), answer("SELECT n * s FROM t"));
    }

    @Test
    public void aNumericArgumentRefuses() {
        assertEquals(unrecognized(TWO), answer("SELECT ABS('" + TWO + "')"));
        assertEquals(unrecognized(TWO), answer("SELECT ROUND(s) FROM t"));
        assertEquals(unrecognized(TWO), answer("SELECT DIV0('" + TWO + "', 1)"));
        assertEquals(unrecognized(TWO), answer("SELECT LEFT('abc', '" + TWO + "')"));
        assertEquals(unrecognized(TWO), answer("SELECT SUM(s) FROM t"));
    }

    @Test
    public void aNumberColumnRefusesTheText() {
        assertEquals("REFUSED DML operation to table T failed on column N with error: Numeric value '" + ARABIC
            + "' is not recognized", answer("INSERT INTO t (n) VALUES ('" + ARABIC + "')"));
        assertEquals("REFUSED DML operation to table T failed on column N with error: Numeric value '" + ARABIC
            + "' is not recognized", answer("UPDATE t SET n = '" + ARABIC + "'"));
        assertEquals("48", answer("SELECT n FROM t"));
    }

    @Test
    public void aHexadecimalInputRefuses() {
        final String refusal = "REFUSED The following string is not a legal hex-encoded value: '";
        assertEquals(refusal + ARABIC + "'", answer("SELECT TO_BINARY('" + ARABIC + "')"));
        assertEquals(refusal + FULLWIDTH + "'", answer("SELECT '" + FULLWIDTH + "'::BINARY"));
        assertEquals(refusal + ARABIC + "'", answer("SELECT HEX_DECODE_BINARY('" + ARABIC + "')"));
        assertEquals("null", answer("SELECT TRY_TO_BINARY('" + ARABIC + "')"));
        final String literal = answer("SELECT X'" + ARABIC + "'");
        assertTrue(literal.startsWith("REFUSED SQL compilation error:")
            && literal.contains("Invalid binary literal"), literal);
    }

    @Test
    public void asciiDigitsStillConvert() {
        assertEquals("48|96|49|0|2.0|4A", answer("SELECT '48'::NUMBER, n + '48', '48' + 1, '2' % 2, ABS('2'), "
            + "HEX_ENCODE(TO_BINARY('4a')) FROM t"));
    }
}
