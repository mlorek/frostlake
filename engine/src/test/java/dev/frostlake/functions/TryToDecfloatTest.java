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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TRY_TO_DECFLOAT is TO_DECFLOAT answering NULL where TO_DECFLOAT fails on the value — a text that spells
 * no number, a format model the text does not fit, a model that is not one — and it reads text as
 * TO_DECFLOAT does, not as TRY_TO_DOUBLE: a non-finite word, a d suffix and hex digits are NULL here.
 * Like every TRY_TO_ conversion it is a TRY_CAST, so a source that is not text is judged while the
 * statement compiles: a castable one keeps TRY_CAST's sentence, one with no conversion takes the
 * conversion sentence, and a DECFLOAT passes. Every cell is live-verified.
 */
public class TryToDecfloatTest extends BaseDatabaseTest {

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double number(final String sql) {
        return ((Number) value(sql)).doubleValue();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE dt0 (n INT, x NUMBER(10,2), b BOOLEAN, d DATE, tm TIME,"
            + " ts TIMESTAMP_NTZ, j VARIANT, z DECFLOAT)");
    }

    private static String tryCast(final String source) {
        return "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types " + source
            + " and DECFLOAT(38)";
    }

    private static String noConversion(final String echo) {
        return "SQL compilation error:\ninvalid type [" + echo + "] for parameter 'TO_DECFLOAT'";
    }

    /** A number, a BOOLEAN, a VARIANT or a NULL keeps TRY_CAST's sentence, with or without a format. */
    @Test
    public void aCastableSourceKeepsTryCastsSentence() {
        createTable();
        assertEquals(tryCast("BOOLEAN"), refusal("SELECT TRY_TO_DECFLOAT(TRUE)"));
        assertEquals(tryCast("NUMBER(2,1)"), refusal("SELECT TRY_TO_DECFLOAT(1.5)"));
        assertEquals(tryCast("NUMBER(1,0)"), refusal("SELECT TRY_TO_DECFLOAT(-5)"));
        assertEquals(tryCast("NUMBER(6,0)"), refusal("SELECT TRY_TO_DECFLOAT(1e5)"));
        assertEquals(tryCast("NULL"), refusal("SELECT TRY_TO_DECFLOAT(NULL)"));
        assertEquals(tryCast("NUMBER(38,0)"), refusal("SELECT TRY_TO_DECFLOAT(NULL::INT)"));
        assertEquals(tryCast("VARIANT"), refusal("SELECT TRY_TO_DECFLOAT(PARSE_JSON('1'))"));
        assertEquals(tryCast("NUMBER(38,0)"), refusal("SELECT TRY_TO_DECFLOAT(n) FROM dt0"));
        assertEquals(tryCast("NUMBER(38,0)"), refusal("SELECT TRY_TO_DECFLOAT(n + 1) FROM dt0"));
        assertEquals(tryCast("NUMBER(10,2)"), refusal("SELECT TRY_TO_DECFLOAT(x) FROM dt0"));
        assertEquals(tryCast("BOOLEAN"), refusal("SELECT TRY_TO_DECFLOAT(b) FROM dt0"));
        assertEquals(tryCast("VARIANT"), refusal("SELECT TRY_TO_DECFLOAT(j) FROM dt0"));
        assertEquals(tryCast("NUMBER(2,1)"), refusal("SELECT TRY_TO_DECFLOAT(1.5, '9.9')"));
        assertEquals("SQL compilation error: error line 1 at position 27\ninvalid identifier 'MISSING'",
            refusal("SELECT TRY_TO_DECFLOAT(n), missing FROM dt0"));
    }

    /** A temporal value, a BINARY and a container have no conversion to a DECFLOAT at all. */
    @Test
    public void aSourceWithNoConversionTakesTheConversionSentence() {
        createTable();
        assertEquals(noConversion("TRY_TO_DECFLOAT(DT0.D)"), refusal("SELECT TRY_TO_DECFLOAT(d) FROM dt0"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(DT0.TM)"), refusal("SELECT TRY_TO_DECFLOAT(tm) FROM dt0"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(DT0.TS)"), refusal("SELECT TRY_TO_DECFLOAT(ts) FROM dt0"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(CURRENT_DATE())"), refusal("SELECT TRY_TO_DECFLOAT(CURRENT_DATE())"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(X'00')"), refusal("SELECT TRY_TO_DECFLOAT(X'00')"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(ARRAY_CONSTRUCT(1))"),
            refusal("SELECT TRY_TO_DECFLOAT(ARRAY_CONSTRUCT(1))"));
        assertEquals(noConversion("TRY_TO_DECFLOAT(OBJECT_CONSTRUCT())"),
            refusal("SELECT TRY_TO_DECFLOAT(OBJECT_CONSTRUCT())"));
    }

    /** A DECFLOAT into a DECFLOAT passes. */
    @Test
    public void aDecfloatSourcePasses() {
        createTable();
        assertEquals(1.5, number("SELECT TRY_TO_DECFLOAT(TO_DECFLOAT('1.5'))"));
        assertEquals(1.0, number("SELECT TRY_TO_DECFLOAT(TRY_TO_DECFLOAT('1'))"));
        assertEquals(1.0, number("SELECT TRY_TO_DECFLOAT(CAST('1' AS DECFLOAT))"));
        assertEquals(0, engine.executeQuery("SELECT TRY_TO_DECFLOAT(z), TRY_TO_DECFLOAT(z + 1) FROM dt0").getRowCount());
    }

    /** A cast to DECFLOAT is judged by the same pairs, and names the target DECFLOAT(38). */
    @Test
    public void aCastToDecfloatIsJudgedTheSameWay() {
        createTable();
        assertEquals(tryCast("NUMBER(2,1)"), refusal("SELECT TRY_CAST(1.5 AS DECFLOAT)"));
        assertEquals(tryCast("BOOLEAN"), refusal("SELECT TRY_CAST(TRUE AS DECFLOAT)"));
        assertEquals(tryCast("VARIANT"), refusal("SELECT TRY_CAST(PARSE_JSON('1') AS DECFLOAT)"));
        assertEquals(noConversion("TRY_CAST(DT0.D)"), refusal("SELECT TRY_CAST(d AS DECFLOAT) FROM dt0"));
        assertEquals(noConversion("TRY_CAST(DT0.TM)"), refusal("SELECT TRY_CAST(tm AS DECFLOAT) FROM dt0"));
        assertEquals(noConversion("TRY_CAST(X'00')"), refusal("SELECT TRY_CAST(X'00' AS DECFLOAT)"));
        assertEquals(noConversion("TRY_CAST(ARRAY_CONSTRUCT())"), refusal("SELECT TRY_CAST(ARRAY_CONSTRUCT() AS DECFLOAT)"));
        assertEquals(noConversion("CAST(DT0.D AS DECFLOAT(38))"), refusal("SELECT CAST(d AS DECFLOAT) FROM dt0"));
        assertEquals(noConversion("CAST(DT0.TM AS DECFLOAT(38))"), refusal("SELECT CAST(tm AS DECFLOAT) FROM dt0"));
        assertEquals(noConversion("CAST(X'00' AS DECFLOAT(38))"), refusal("SELECT CAST(X'00' AS DECFLOAT)"));
        assertEquals(noConversion("CAST(ARRAY_CONSTRUCT() AS DECFLOAT(38))"),
            refusal("SELECT CAST(ARRAY_CONSTRUCT() AS DECFLOAT)"));
        assertEquals(1.0, number("SELECT CAST(TRUE AS DECFLOAT)"));
        assertEquals(1.5, number("SELECT CAST(1.5 AS DECFLOAT)"));
        assertEquals(1.5, number("SELECT TRY_CAST('1.5' AS DECFLOAT)"));
        assertNull(value("SELECT TRY_CAST(NULL AS DECFLOAT)"));
    }

    @Test
    public void aNumberInTextConverts() {
        assertEquals(1.5, number("SELECT TRY_TO_DECFLOAT('1.5')"));
        assertEquals(1.5, number("SELECT TRY_TO_DECFLOAT(' 1.5 ')"));
        assertEquals(100000.0, number("SELECT TRY_TO_DECFLOAT('1e5', '9EEEE')"));
    }

    @Test
    public void whatToDecfloatRefusesIsNull() {
        assertNull(value("SELECT TRY_TO_DECFLOAT('abc')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('123', '99')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1e5', '9e9')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1', '9+')"));
    }

    @Test
    public void itIsNotTryToDouble() {
        assertNull(value("SELECT TRY_TO_DECFLOAT('inf')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('nan')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('1d')"));
        assertNull(value("SELECT TRY_TO_DECFLOAT('0x10')"));
        assertEquals(Double.POSITIVE_INFINITY, number("SELECT TRY_TO_DOUBLE('inf')"));
        assertEquals(1.0, number("SELECT TRY_TO_DOUBLE('1d')"));
    }
}
