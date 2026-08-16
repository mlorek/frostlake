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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The TRY_TO_* non-string refusal, spelled the way a real account spells it (live-verified cell by
 * cell).
 *
 * <p>★ THE TARGET IS THE DECLARED PAIR. {@code TRY_TO_NUMBER(1000, 2, 0)} names NUMBER(2,0) — the
 * width the call itself declares — not the family's nominal NUMBER(38,0), which only a bare call
 * echoes. The whole sentence rides under the compilation prefix.
 *
 * <p>★ A BOOLEAN SOURCE IS ITS OWN CELL: it echoes NUMBER(2,0) whatever the call declares — even
 * against an explicit (5,1).
 *
 * <p>★ A PAIR THE PLAIN CAST CANNOT CARRY changes family entirely: it is "invalid type [&lt;the call,
 * replayed&gt;] for parameter '&lt;the base conversion&gt;'" — the call echoed with its arguments,
 * format string and all, the base conversion the function's OWN (TO_DECIMAL, TO_NUMERIC,
 * TO_TIMESTAMP_NTZ for TRY_TO_TIMESTAMP, TO_TIMESTAMP_LTZ, TO_TIMESTAMP_TZ). Which pairs those are
 * is the plain cast's matrix, measured whole: a NUMBER converts into NUMBER, FLOAT, BOOLEAN and the
 * TIMESTAMPs but not into TIME, DATE or BINARY; a FLOAT only into NUMBER; a BOOLEAN only into NUMBER;
 * a DATE only into the TIMESTAMPs; a TIME only into TIME; a TIMESTAMP into DATE, TIME and the
 * TIMESTAMPs; a BINARY into nothing; a VARIANT and an untyped NULL into everything; an OBJECT or an
 * ARRAY into nothing at all — the conversion sentence, not TRY_CAST's.
 *
 * <p>★ FOUR IDENTITY PAIRS PASS — a DATE, a FLOAT, a BINARY and a BOOLEAN into their own type answer
 * the value — while a NUMBER, a TIME and a TIMESTAMP into their own type are refused like any other
 * castable pair ("TIME(9) and TIME(9)").
 *
 * <p>★ THE TIMESTAMP NOMINAL'S PRECISION IS THE NUMBER SOURCE'S SCALE plus the declared scale
 * argument: {@code TRY_TO_TIMESTAMP(123)} echoes TIMESTAMP_NTZ(0), over a NUMBER(5,2) it echoes (2),
 * {@code (123, 3)} echoes (3) and {@code (1.5, 3)} echoes (4); every other source echoes (9).
 *
 * <p>★ A NUMBER SOURCE WITH A FORMAT STRING is a third sentence: "argument needs to be a string:
 * 'TT.N'". And a FLOAT cast inside the echoed call is spelled TO_DOUBLE, as the plan spells it.
 */
public class TryToRefusalShapeTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void theDeclaredWidthIsEchoed() {
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(4,0) and NUMBER(2,0)",
            refusal("SELECT TRY_TO_NUMBER(1000, 2, 0)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(4,0) and NUMBER(2,0)",
            refusal("SELECT TRY_TO_DECIMAL(1000, 2, 0)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(4,0) and NUMBER(2,0)",
            refusal("SELECT TRY_TO_NUMERIC(1000, 2, 0)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(4,0) and NUMBER(10,2)",
            refusal("SELECT TRY_TO_NUMBER(1000, 10, 2)"));
    }

    @Test
    public void aBareCallEchoesTheNominalPair() {
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(4,0) and NUMBER(38,0)",
            refusal("SELECT TRY_TO_NUMBER(1000)"));
    }

    @Test
    public void aBooleanSourceAlwaysEchoesNumberTwoZero() {
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types BOOLEAN and NUMBER(2,0)",
            refusal("SELECT TRY_TO_NUMBER(TRUE)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types BOOLEAN and NUMBER(2,0)",
            refusal("SELECT TRY_TO_NUMBER(TRUE, 5, 1)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types BOOLEAN and NUMBER(2,0)",
            refusal("SELECT TRY_TO_DECIMAL(TRUE)"));
    }

    @Test
    public void anUncastablePairTakesTheConversionSentence() {
        assertEquals("SQL compilation error:\ninvalid type [TRY_TO_NUMBER(CURRENT_DATE())]"
            + " for parameter 'TO_NUMBER'",
            refusal("SELECT TRY_TO_NUMBER(CURRENT_DATE)"));
        assertEquals("SQL compilation error:\ninvalid type [TRY_TO_DATE(123)] for parameter 'TO_DATE'",
            refusal("SELECT TRY_TO_DATE(123)"));
        assertEquals("SQL compilation error:\ninvalid type [TRY_TO_DATE(123, 'YYYY')]"
            + " for parameter 'TO_DATE'",
            refusal("SELECT TRY_TO_DATE(123, 'YYYY')"));
    }

    private void seedPairs() {
        engine.execute("CREATE OR REPLACE TABLE tt (d DATE, t TIME, ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ,"
            + " tz TIMESTAMP_TZ, n NUMBER(5,2), b BOOLEAN, v VARIANT, f FLOAT, bi BINARY(4), o OBJECT, a ARRAY)");
        engine.execute("INSERT INTO tt SELECT '2020-01-01', '10:00:00', '2020-01-01 10:00:00',"
            + " '2020-01-01 10:00:00', '2020-01-01 10:00:00 +0000', 1.5, TRUE, PARSE_JSON('1'), 1.5,"
            + " TO_BINARY('0A0B'), OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1)");
    }

    private static String tryCast(final String source, final String target) {
        return "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types "
            + source + " and " + target;
    }

    private static String conversion(final String echo, final String parameter) {
        return "SQL compilation error:\ninvalid type [" + echo + "] for parameter '" + parameter + "'";
    }

    @Test
    public void theCastablePairsKeepTheTryCastSentence() {
        seedPairs();
        assertEquals(tryCast("NUMBER(3,0)", "TIMESTAMP_LTZ(0)"), refusal("SELECT TRY_TO_TIMESTAMP_LTZ(123)"));
        assertEquals(tryCast("NUMBER(3,0)", "TIMESTAMP_TZ(0)"), refusal("SELECT TRY_TO_TIMESTAMP_TZ(123)"));
        assertEquals(tryCast("NUMBER(5,2)", "TIMESTAMP_LTZ(2)"), refusal("SELECT TRY_TO_TIMESTAMP_LTZ(n) FROM tt"));
        assertEquals(tryCast("NUMBER(3,0)", "TIMESTAMP_NTZ(3)"), refusal("SELECT TRY_TO_TIMESTAMP_NTZ(123, 3)"));
        assertEquals(tryCast("NUMBER(2,1)", "TIMESTAMP_NTZ(4)"), refusal("SELECT TRY_TO_TIMESTAMP(1.5, 3)"));
        assertEquals(tryCast("DATE", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP(d) FROM tt"));
        assertEquals(tryCast("DATE", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP(d, 'YYYY') FROM tt"));
        assertEquals(tryCast("DATE", "TIMESTAMP_LTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP_LTZ(d) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_NTZ(9)", "TIME(9)"), refusal("SELECT TRY_TO_TIME(ts) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_NTZ(9)", "DATE"), refusal("SELECT TRY_TO_DATE(ts) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_NTZ(9)", "TIMESTAMP_TZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP_TZ(ts) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_NTZ(9)", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP_NTZ(ts) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_LTZ(9)", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP_NTZ(tl) FROM tt"));
        assertEquals(tryCast("TIMESTAMP_TZ(9)", "DATE"), refusal("SELECT TRY_TO_DATE(tz) FROM tt"));
        assertEquals(tryCast("TIME(9)", "TIME(9)"), refusal("SELECT TRY_TO_TIME(t) FROM tt"));
        assertEquals(tryCast("FLOAT", "NUMBER(38,0)"), refusal("SELECT TRY_TO_NUMBER(f) FROM tt"));
        assertEquals(tryCast("NUMBER(5,2)", "NUMBER(38,0)"), refusal("SELECT TRY_TO_NUMBER(n) FROM tt"));
        assertEquals(tryCast("NUMBER(3,0)", "FLOAT"), refusal("SELECT TRY_TO_DOUBLE(123)"));
        assertEquals(tryCast("NUMBER(5,2)", "BOOLEAN"), refusal("SELECT TRY_TO_BOOLEAN(n) FROM tt"));
        assertEquals(tryCast("BOOLEAN", "NUMBER(2,0)"), refusal("SELECT TRY_TO_DECIMAL(TRUE)"));
        assertEquals(tryCast("VARIANT", "TIMESTAMP_LTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP_LTZ(v) FROM tt"));
        assertEquals(tryCast("VARIANT", "TIME(9)"), refusal("SELECT TRY_TO_TIME(v) FROM tt"));
        assertEquals(tryCast("VARIANT", "BINARY(67108864)"), refusal("SELECT TRY_TO_BINARY(v) FROM tt"));
        assertEquals(tryCast("NULL", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_TO_TIMESTAMP(NULL)"));
        assertEquals(tryCast("NULL", "TIME(9)"), refusal("SELECT TRY_TO_TIME(NULL)"));
    }

    @Test
    public void theUncastablePairsTakeTheConversionSentence() {
        seedPairs();
        assertEquals(conversion("TRY_TO_TIME(123)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(123)"));
        assertEquals(conversion("TRY_TO_TIME(TT.N)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(n) FROM tt"));
        assertEquals(conversion("TRY_TO_TIME(1.5)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(1.5)"));
        assertEquals(conversion("TRY_TO_DATE(1.5)", "TO_DATE"), refusal("SELECT TRY_TO_DATE(1.5)"));
        assertEquals(conversion("TRY_TO_BINARY(123)", "TO_BINARY"), refusal("SELECT TRY_TO_BINARY(123)"));
        assertEquals(conversion("TRY_TO_DECIMAL(TT.T)", "TO_DECIMAL"), refusal("SELECT TRY_TO_DECIMAL(t) FROM tt"));
        assertEquals(conversion("TRY_TO_NUMERIC(TT.T)", "TO_NUMERIC"), refusal("SELECT TRY_TO_NUMERIC(t) FROM tt"));
        assertEquals(conversion("TRY_TO_DOUBLE(TT.TS)", "TO_DOUBLE"), refusal("SELECT TRY_TO_DOUBLE(ts) FROM tt"));
        assertEquals(conversion("TRY_TO_DOUBLE(TT.D)", "TO_DOUBLE"), refusal("SELECT TRY_TO_DOUBLE(d) FROM tt"));
        assertEquals(conversion("TRY_TO_NUMBER(CURRENT_TIME())", "TO_NUMBER"),
            refusal("SELECT TRY_TO_NUMBER(CURRENT_TIME)"));
        assertEquals(conversion("TRY_TO_NUMBER(CURRENT_TIMESTAMP())", "TO_NUMBER"),
            refusal("SELECT TRY_TO_NUMBER(CURRENT_TIMESTAMP)"));
        assertEquals(conversion("TRY_TO_DOUBLE(TRUE)", "TO_DOUBLE"), refusal("SELECT TRY_TO_DOUBLE(TRUE)"));
        assertEquals(conversion("TRY_TO_TIME(TRUE)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(TRUE)"));
        assertEquals(conversion("TRY_TO_TIME(CURRENT_DATE())", "TO_TIME"), refusal("SELECT TRY_TO_TIME(CURRENT_DATE)"));
        assertEquals(conversion("TRY_TO_DATE(TT.T)", "TO_DATE"), refusal("SELECT TRY_TO_DATE(t) FROM tt"));
        assertEquals(conversion("TRY_TO_TIMESTAMP(TT.T)", "TO_TIMESTAMP_NTZ"), refusal("SELECT TRY_TO_TIMESTAMP(t) FROM tt"));
        assertEquals(conversion("TRY_TO_TIMESTAMP_LTZ(TT.T)", "TO_TIMESTAMP_LTZ"),
            refusal("SELECT TRY_TO_TIMESTAMP_LTZ(t) FROM tt"));
        assertEquals(conversion("TRY_TO_TIMESTAMP_TZ(TT.F)", "TO_TIMESTAMP_TZ"),
            refusal("SELECT TRY_TO_TIMESTAMP_TZ(f) FROM tt"));
        assertEquals(conversion("TRY_TO_TIMESTAMP(TRUE)", "TO_TIMESTAMP_NTZ"), refusal("SELECT TRY_TO_TIMESTAMP(TRUE)"));
        assertEquals(conversion("TRY_TO_BOOLEAN(TT.F)", "TO_BOOLEAN"), refusal("SELECT TRY_TO_BOOLEAN(f) FROM tt"));
        assertEquals(conversion("TRY_TO_DATE(TT.F)", "TO_DATE"), refusal("SELECT TRY_TO_DATE(f) FROM tt"));
        assertEquals(conversion("TRY_TO_BINARY(TT.F)", "TO_BINARY"), refusal("SELECT TRY_TO_BINARY(f) FROM tt"));
        assertEquals(conversion("TRY_TO_NUMBER(TT.BI)", "TO_NUMBER"), refusal("SELECT TRY_TO_NUMBER(bi) FROM tt"));
        assertEquals(conversion("TRY_TO_TIME(TT.BI)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(bi) FROM tt"));
        assertEquals(conversion("TRY_TO_BOOLEAN(TT.BI)", "TO_BOOLEAN"), refusal("SELECT TRY_TO_BOOLEAN(bi) FROM tt"));
        assertEquals(conversion("TRY_TO_BINARY(TT.B)", "TO_BINARY"), refusal("SELECT TRY_TO_BINARY(b) FROM tt"));
        assertEquals(conversion("TRY_TO_BINARY(TT.D)", "TO_BINARY"), refusal("SELECT TRY_TO_BINARY(d) FROM tt"));
        assertEquals(conversion("TRY_TO_BOOLEAN(TT.D)", "TO_BOOLEAN"), refusal("SELECT TRY_TO_BOOLEAN(d) FROM tt"));
        assertEquals(conversion("TRY_TO_NUMBER(TT.O)", "TO_NUMBER"), refusal("SELECT TRY_TO_NUMBER(o) FROM tt"));
        assertEquals(conversion("TRY_TO_TIME(TT.A)", "TO_TIME"), refusal("SELECT TRY_TO_TIME(a) FROM tt"));
        assertEquals(conversion("TRY_TO_BOOLEAN(TT.O)", "TO_BOOLEAN"), refusal("SELECT TRY_TO_BOOLEAN(o) FROM tt"));
        assertEquals(conversion("TRY_TO_TIME(TT.N, 'HH24')", "TO_TIME"), refusal("SELECT TRY_TO_TIME(n, 'HH24') FROM tt"));
        assertEquals(conversion("TRY_TO_DOUBLE(TT.D, '999')", "TO_DOUBLE"), refusal("SELECT TRY_TO_DOUBLE(d, '999') FROM tt"));
        assertEquals(conversion("TRY_TO_TIMESTAMP_LTZ(TO_DOUBLE(1.5))", "TO_TIMESTAMP_LTZ"),
            refusal("SELECT TRY_TO_TIMESTAMP_LTZ(1.5::FLOAT)"));
        // And the third sentence, for a NUMBER source with a format string.
        assertEquals("SQL compilation error:\nargument needs to be a string: 'TT.N'",
            refusal("SELECT TRY_TO_NUMBER(n, '999') FROM tt"));
    }

    @Test
    public void theIdentityPairsThatPass() {
        seedPairs();
        assertEquals("2020-01-01", firstText("SELECT TO_VARCHAR(TRY_TO_DATE(d)) FROM tt"));
        assertEquals("1.5", firstText("SELECT TO_VARCHAR(TRY_TO_DOUBLE(f)) FROM tt"));
        assertEquals("0A0B", firstText("SELECT TO_VARCHAR(TRY_TO_BINARY(bi)) FROM tt"));
        assertEquals("true", firstText("SELECT TO_VARCHAR(TRY_TO_BOOLEAN(TRUE))"));
    }

    private String firstText(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void tryToTimestampEchoesPrecisionZero() {
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments"
            + " of types NUMBER(3,0) and TIMESTAMP_NTZ(0)",
            refusal("SELECT TRY_TO_TIMESTAMP(123)"));
    }

    @Test
    public void theStringFormStaysNull() {
        assertEquals("NULL",
            String.valueOf(engine.executeQuery("SELECT TRY_TO_NUMBER('1000', 2, 0)")
                .getRows().get(0).getValue(0)).toUpperCase());
    }

    @Test
    public void theNonTryTwinRefusesAtRowTimeInstead() {
        assertEquals("Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000",
            refusal("SELECT TO_NUMBER(1000, 2, 0)"));
    }
}
