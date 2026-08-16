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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The conditional fold's VALUE presentation for the families beyond the exact-numeric core, and the
 * fold shapes a text takes part in — live-verified cell by cell, every conditional in the family.
 *
 * <p>★ AN APPROXIMATE FOLD HANDS BACK A DOUBLE: a NUMBER(10,2) 1.50 beside a FLOAT is 1.5, an INT 1 is
 * 1.0, a NUMBER(7,6) third is 0.333333 — the chosen branch converted, not merely relabelled.
 *
 * <p>★ A TEMPORAL FOLD PRESENTS THE WIDER TYPE: a DATE beside a TIMESTAMP is the midnight timestamp,
 * an NTZ beside an LTZ is the same wall clock in the session zone, a chosen TEXT is read as the fold's
 * type — and refused as "Date 'abc' is not recognized" when it reads as none.
 *
 * <p>★ A BOOLEAN FOLD PRESENTS A BOOLEAN whichever side the boolean was written on against a number
 * (1 is TRUE, 0 and 0.0 FALSE, -1 TRUE), while against a STRING the first branch keeps the lead: a
 * boolean first reads the chosen text strictly ('yes' TRUE, 'x' refused), a string first prints the
 * boolean as 'true'.
 *
 * <p>★ A STRING COLUMN FOLDS INTO THE OTHER FAMILY: NUMBER(18,5) beside any exact number, FLOAT beside
 * a FLOAT, DATE / TIME / TIMESTAMP beside a temporal — the text converted when it is chosen and
 * refused with the family's row-time sentence when it reads as none. A string LITERAL beside a number
 * measures as the number it spells instead ('1.5' beside 1 is NUMBER(2,1)); one that spells no number
 * keeps the NUMBER(18,5) fold and fails the row. Beside a BOOLEAN or a VARIANT the string leads and
 * the fold is the unbounded VARCHAR.
 *
 * <p>★ THE CASTS BEHIND THE FOLD ARE STRICT TOO: 'yes'::BOOLEAN is TRUE, 'x'::BOOLEAN and TO_BOOLEAN('x')
 * are "Boolean value 'x' is not recognized", 'abc'::TIME is "Time 'abc' is not recognized".
 */
public class ConditionalFoldFamiliesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cf (i INT, n NUMBER(10,2), f FLOAT, d DATE, ts TIMESTAMP_NTZ,"
            + " tl TIMESTAMP_LTZ, tm TIME, b BOOLEAN, s VARCHAR(5), s2 VARCHAR(10), v VARIANT)");
        engine.execute("INSERT INTO cf SELECT 1, 1.5, 2.5, '2020-01-01', '2020-01-01 10:00:00',"
            + " '2020-01-01 10:00:00', '10:00:00', TRUE, 'abc', 'abcdefghij', PARSE_JSON('1')");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void anApproximateFoldHandsBackADouble() {
        assertEquals("1.5", cell("SELECT COALESCE(n, f) FROM cf"));
        assertEquals("2.5", cell("SELECT COALESCE(f, n) FROM cf"));
        assertEquals("1.0", cell("SELECT IFF(TRUE, i, f) FROM cf"));
        assertEquals("1.5", cell("SELECT CASE WHEN TRUE THEN n ELSE f END FROM cf"));
        assertEquals("2.5", cell("SELECT GREATEST(f, n) FROM cf"));
        assertEquals("1.5", cell("SELECT LEAST(n, f) FROM cf"));
        assertEquals("1.5", cell("SELECT DECODE(1, 1, n, f) FROM cf"));
        assertEquals("1.5", cell("SELECT COALESCE(NULL::FLOAT, n) FROM cf"));
        assertEquals("0.333333", cell("SELECT COALESCE(1/3, f) FROM cf"));
        assertEquals("1", cell("SELECT IFF(TRUE, i, f)::VARCHAR FROM cf"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(COALESCE(n, f)) FROM cf"));
    }

    @Test
    public void aTemporalFoldPresentsTheWiderType() {
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT COALESCE(d, ts)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT IFF(TRUE, d, ts)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT CASE WHEN TRUE THEN d ELSE ts END::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT LEAST(d, ts)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 10:00:00.000", cell("SELECT GREATEST(d, ts)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT COALESCE(d, NULL::TIMESTAMP)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT IFF(FALSE, ts, d)::VARCHAR FROM cf"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT DATEADD(day, 1, COALESCE(d, ts))::VARCHAR FROM cf")
            .replace("2020-01-02", "2020-01-01"));
        assertEquals("2020-01-01 00:00:00", cell("SELECT TO_VARCHAR(COALESCE(d, tl), 'YYYY-MM-DD HH24:MI:SS') FROM cf"));
        assertEquals("2020-01-01 10:00:00", cell("SELECT TO_VARCHAR(COALESCE(ts, tl), 'YYYY-MM-DD HH24:MI:SS') FROM cf"));
        assertEquals("2020-01-01 10:00:00", cell("SELECT TO_VARCHAR(IFF(FALSE, tl, ts), 'YYYY-MM-DD HH24:MI:SS') FROM cf"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(COALESCE(d, ts)) FROM cf"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(COALESCE(ts, tl)) FROM cf"));
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", cell("SELECT SYSTEM$TYPEOF(COALESCE(d, tl)) FROM cf"));
        assertEquals("TIMESTAMP_NTZ(3)[SB8]", cell("SELECT SYSTEM$TYPEOF(COALESCE(d, ts::TIMESTAMP_NTZ(3))) FROM cf"));
        // A chosen TEXT is read as the fold's type.
        assertEquals("2020-01-01", cell("SELECT COALESCE(d, '2021-01-01')::VARCHAR FROM cf"));
        assertEquals("2021-01-01", cell("SELECT IFF(FALSE, d, '2021-01-01')::VARCHAR FROM cf"));
        assertEquals("2020-01-02", cell("SELECT IFF(FALSE, d, '2020-01-02 10:00:00')::VARCHAR FROM cf"));
        assertEquals("2020-01-02 00:00:00.000", cell("SELECT IFF(FALSE, ts, '2020-01-02')::VARCHAR FROM cf"));
        assertEquals("11:30:00", cell("SELECT IFF(FALSE, tm, '11:30')::VARCHAR FROM cf"));
        assertEquals("DATE[SB4]", cell("SELECT SYSTEM$TYPEOF(COALESCE(d, '2021-01-01')) FROM cf"));
        // A TIMESTAMP fold is a TIMESTAMP: the DATE's day arithmetic is gone with it.
        assertEquals("SQL compilation error: error line 1 at position 23\n"
            + "Invalid argument types for function '+': (TIMESTAMP_NTZ(9), NUMBER(1,0))",
            refusal("SELECT COALESCE(d, ts) + 1 FROM cf"));
    }

    @Test
    public void aBooleanFoldPresentsABoolean() {
        assertEquals("true", cell("SELECT COALESCE(1, b) FROM cf"));
        assertEquals("true", cell("SELECT COALESCE(b, 1) FROM cf"));
        assertEquals("false", cell("SELECT IFF(FALSE, b, 0) FROM cf"));
        assertEquals("false", cell("SELECT IFF(TRUE, 0, b) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, 0, b) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, 2) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, -1) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, 1.5) FROM cf"));
        assertEquals("false", cell("SELECT IFF(FALSE, b, 0.0) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, f) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, n) FROM cf"));
        assertEquals("true", cell("SELECT COALESCE(n, b) FROM cf"));
        assertEquals("true", cell("SELECT CASE WHEN FALSE THEN b ELSE 1 END FROM cf"));
        assertEquals("false", cell("SELECT DECODE(1, 2, b, 0) FROM cf"));
        assertEquals("true", cell("SELECT GREATEST(b, 0) FROM cf"));
        assertEquals("BOOLEAN[SB1]", cell("SELECT SYSTEM$TYPEOF(COALESCE(1, b)) FROM cf"));
        assertEquals("BOOLEAN[SB1]", cell("SELECT SYSTEM$TYPEOF(COALESCE(n, b)) FROM cf"));
        // A boolean written FIRST reads the chosen text strictly …
        assertEquals("true", cell("SELECT IFF(FALSE, b, 'yes') FROM cf"));
        assertEquals("false", cell("SELECT IFF(FALSE, b, 'no') FROM cf"));
        assertEquals("false", cell("SELECT IFF(FALSE, b, '0') FROM cf"));
        assertEquals("true", cell("SELECT COALESCE(b, 'abc') FROM cf"));
        assertEquals("Boolean value 'x' is not recognized", refusal("SELECT CASE WHEN FALSE THEN b ELSE 'x' END FROM cf"));
        assertEquals("Boolean value '2' is not recognized", refusal("SELECT IFF(FALSE, b, '2') FROM cf"));
        assertEquals("Boolean value '' is not recognized", refusal("SELECT IFF(FALSE, b, '') FROM cf"));
        assertEquals("Boolean value 'abc' is not recognized", refusal("SELECT IFF(FALSE, b, 'abc') FROM cf"));
        assertEquals("Boolean value 'x' is not recognized", refusal("SELECT NVL2(NULL, b, 'x') FROM cf"));
        // … while a string written first keeps the lead, and the boolean prints as text.
        assertEquals("x", cell("SELECT COALESCE('x', b) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, 'yes', b) FROM cf"));
        assertEquals("abc", cell("SELECT COALESCE(s, b) FROM cf"));
        assertEquals("true", cell("SELECT IFF(FALSE, s, b) FROM cf"));
        assertEquals("VARCHAR(134217728)[LOB]", cell("SELECT SYSTEM$TYPEOF(COALESCE(s, b)) FROM cf"));
        assertEquals("BOOLEAN[SB1]", cell("SELECT SYSTEM$TYPEOF(COALESCE(b, s)) FROM cf"));
    }

    @Test
    public void aStringColumnFoldsIntoTheOtherFamily() {
        assertEquals("1.50000", cell("SELECT IFF(FALSE, s, 1.5) FROM cf"));
        assertEquals("1.00000", cell("SELECT COALESCE(1, s) FROM cf"));
        assertEquals("1.00000", cell("SELECT IFF(TRUE, 1, s) FROM cf"));
        assertEquals("1.50000", cell("SELECT IFF(FALSE, s, n) FROM cf"));
        assertEquals("1.50000", cell("SELECT COALESCE(n, s) FROM cf"));
        assertEquals("1.50000", cell("SELECT IFF(FALSE, s2, n) FROM cf"));
        assertEquals("NUMBER(18,5)[SB4]", cell("SELECT SYSTEM$TYPEOF(IFF(FALSE, s, 1.5)) FROM cf"));
        assertEquals("12.50000", cell("SELECT COALESCE(t, 1) FROM (SELECT '12.5'::VARCHAR(5) AS t FROM cf)"));
        assertEquals("12.50000", cell("SELECT COALESCE(t, n)::VARCHAR FROM (SELECT '12.5'::VARCHAR(5) AS t, n FROM cf)"));
        assertEquals("1.5", cell("SELECT IFF(FALSE, s, 1.5::FLOAT) FROM cf"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(IFF(FALSE, s, 1.5::FLOAT)) FROM cf"));
        assertEquals("2020-01-01", cell("SELECT IFF(FALSE, s, d)::VARCHAR FROM cf"));
        assertEquals("2020-01-01", cell("SELECT COALESCE(d, s)::VARCHAR FROM cf"));
        assertEquals("2021-03-04", cell("SELECT COALESCE(t, d)::VARCHAR FROM (SELECT '2021-03-04'::VARCHAR(10) AS t, d FROM cf)"));
        assertEquals("10:00:00", cell("SELECT IFF(FALSE, s, tm)::VARCHAR FROM cf"));
        assertEquals("11:30:00", cell("SELECT COALESCE(t, tm)::VARCHAR FROM (SELECT '11:30:00'::VARCHAR(8) AS t, tm FROM cf)"));
        assertEquals("DATE[SB4]", cell("SELECT SYSTEM$TYPEOF(IFF(FALSE, s, d)) FROM cf"));
        assertEquals("TIME(9)[SB8]", cell("SELECT SYSTEM$TYPEOF(IFF(FALSE, s, tm)) FROM cf"));
        assertEquals("yes", cell("SELECT COALESCE(t, b) FROM (SELECT 'yes'::VARCHAR(3) AS t, b FROM cf)"));
        assertEquals("true", cell("SELECT IFF(FALSE, b, t) FROM (SELECT 'yes'::VARCHAR(3) AS t, b FROM cf)"));
        assertEquals("abc", cell("SELECT COALESCE(s, v) FROM cf"));
        assertEquals("1", cell("SELECT IFF(FALSE, s, v) FROM cf"));
        assertEquals("abc", cell("SELECT COALESCE(s, s2) FROM cf"));
        assertEquals("abcdefghij", cell("SELECT IFF(FALSE, s, s2) FROM cf"));
        assertEquals("VARCHAR(10)[LOB]", cell("SELECT SYSTEM$TYPEOF(COALESCE(s, s2)) FROM cf"));
        assertEquals("VARCHAR(16)[LOB]", cell("SELECT SYSTEM$TYPEOF(COALESCE(s, 'longer than five')) FROM cf"));
        assertEquals("16", cell("SELECT LENGTH(IFF(FALSE, s, 'longer than five')) FROM cf"));
        // The chosen text that reads as none of the fold's family is the family's row-time sentence.
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT COALESCE(s, 1) FROM cf"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT IFF(TRUE, s, 1) FROM cf"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT IFF(TRUE, s, f) FROM cf"));
        assertEquals("Numeric value 'abcdefghij' is not recognized", refusal("SELECT COALESCE(s2, n) FROM cf"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT GREATEST(s, 1) FROM cf"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT LEAST(s, 1) FROM cf"));
        assertEquals("Date 'abc' is not recognized", refusal("SELECT COALESCE(s, d) FROM cf"));
        assertEquals("Date 'abc' is not recognized", refusal("SELECT IFF(TRUE, s, d) FROM cf"));
        assertEquals("Time 'abc' is not recognized", refusal("SELECT COALESCE(s, tm) FROM cf"));
    }

    @Test
    public void aTextLiteralBesideAnotherFamilyIsReadAtRowTime() {
        assertEquals("1", cell("SELECT COALESCE('1', 1)"));
        assertEquals("1.5", cell("SELECT COALESCE('1.5', 1)"));
        assertEquals("1.0", cell("SELECT COALESCE(1, '1.5')"));
        assertEquals("1.00000", cell("SELECT IFF(FALSE, 'abc', 1)"));
        assertEquals("1.00000", cell("SELECT COALESCE(1, 'abc')"));
        assertEquals("2.5", cell("SELECT IFF(FALSE, 'abc', f) FROM cf"));
        assertEquals("2020-01-01", cell("SELECT COALESCE(d, 'abc')::VARCHAR FROM cf"));
        assertEquals("2020-01-01", cell("SELECT COALESCE('2020-01-01', d)::VARCHAR FROM cf"));
        assertEquals("abc", cell("SELECT COALESCE('abc', b) FROM cf"));
        assertEquals("true", cell("SELECT COALESCE('true', b) FROM cf"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT COALESCE('abc', 1)"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT IFF(TRUE, 'abc', 1)"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT GREATEST('abc', 1)"));
        assertEquals("Numeric value 'abc' is not recognized", refusal("SELECT COALESCE('abc', f) FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT COALESCE('x', n) FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT CASE WHEN TRUE THEN 'x' ELSE n END FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT DECODE(1, 1, 'x', n) FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT NVL2(1, 'x', n) FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT GREATEST('x', n) FROM cf"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT NVL('x', n) FROM cf"));
        assertEquals("Date 'abc' is not recognized", refusal("SELECT COALESCE('abc', d) FROM cf"));
        assertEquals("Time 'abc' is not recognized", refusal("SELECT COALESCE('abc', tm) FROM cf"));
    }

    @Test
    public void theCastsBehindTheFoldAreStrict() {
        assertEquals("true", cell("SELECT 'yes'::BOOLEAN"));
        assertEquals("true", cell("SELECT ' yes '::BOOLEAN"));
        assertEquals("true", cell("SELECT 'on'::BOOLEAN"));
        assertEquals("false", cell("SELECT 'off'::BOOLEAN"));
        assertEquals("false", cell("SELECT '0'::BOOLEAN"));
        assertEquals("true", cell("SELECT 1.5::BOOLEAN"));
        assertEquals("false", cell("SELECT 0.0::BOOLEAN"));
        assertEquals("true", cell("SELECT n::BOOLEAN FROM cf"));
        assertEquals("null", cell("SELECT TRY_TO_BOOLEAN('x')"));
        assertEquals("Boolean value 'x' is not recognized", refusal("SELECT 'x'::BOOLEAN"));
        assertEquals("Boolean value 'x' is not recognized", refusal("SELECT TO_BOOLEAN('x')"));
        assertEquals("Boolean value '2' is not recognized", refusal("SELECT '2'::BOOLEAN"));
        assertEquals("Boolean value '1.0' is not recognized", refusal("SELECT '1.0'::BOOLEAN"));
        assertEquals("Boolean value '' is not recognized", refusal("SELECT ''::BOOLEAN"));
        assertEquals("Boolean value 'abc' is not recognized", refusal("SELECT s::BOOLEAN FROM cf"));
        assertEquals("Time 'abc' is not recognized", refusal("SELECT 'abc'::TIME"));
        assertEquals("Time 'abc' is not recognized", refusal("SELECT TO_TIME('abc')"));
        assertEquals("Time 'abc' is not recognized", refusal("SELECT s::TIME FROM cf"));
        assertEquals("Date 'abc' is not recognized", refusal("SELECT 'abc'::DATE"));
        assertEquals("Timestamp 'abc' is not recognized", refusal("SELECT 'abc'::TIMESTAMP"));
    }
}
