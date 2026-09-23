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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An interval compared with anything but an interval of its family: a quoted-unit INTERVAL literal is refused
 * while the statement compiles whatever it meets, a number, a VARIANT or a temporal is refused as a conversion
 * into the interval, and a text is read in the interval's fields as the row arrives — in an IN list only when
 * no other member matches, and in a set operation only after the interval arm. Live-verified cell by cell.
 */
public class IntervalComparisonTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE icmp (iv INTERVAL DAY TO SECOND, ts TIMESTAMP_NTZ, n NUMBER(5,0), v VARIANT)");
        engine.execute("INSERT INTO icmp SELECT '1 01:00:00', '2024-01-02 01:00:00'::TIMESTAMP_NTZ, 1, TO_VARIANT(1)");
    }

    /** Every row of a query, cells joined by {@code |}, rows by {@code ;}. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(';');
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append('|');
                }
                out.append(String.valueOf(row.getValue(i)).toUpperCase());
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
        return "answered";
    }

    /** A quoted-unit INTERVAL literal is refused in every comparison, in the comparison's name, at its place. */
    @Test
    public void quotedUnitLiteralsAreRefused() {
        assertEquals("SQL compilation error: error line 1 at position 10\nInvalid argument types for function '>':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT iv > INTERVAL '1 hour' FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 28\nInvalid argument types for function '>':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT 1 FROM icmp WHERE iv > INTERVAL '1 hour'"));
        assertEquals("SQL compilation error: error line 1 at position 25\nInvalid argument types for function '<':"
            + " (INTERVAL, INTERVAL DAY(9) TO SECOND(9))", refusal("SELECT INTERVAL '1 hour' < iv FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 25\nInvalid argument types for function '=':"
            + " (INTERVAL, INTERVAL)", refusal("SELECT INTERVAL '1 hour' = INTERVAL '2 hours'"));
        assertEquals("SQL compilation error: error line 1 at position 10\nInvalid argument types for function '>':"
            + " (TIMESTAMP_NTZ(9), INTERVAL)", refusal("SELECT ts > INTERVAL '1 hour' FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 24\nInvalid argument types for function '=':"
            + " (INTERVAL DAY(9), INTERVAL)", refusal("SELECT INTERVAL '1' DAY = INTERVAL '1 day'"));
        assertEquals("SQL compilation error: error line 1 at position 10\nInvalid argument types for function '>=':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT iv BETWEEN INTERVAL '1 hour' AND INTERVAL '2 days' FROM icmp"));
        assertEquals("SQL compilation error: error line 0 at position -1\nInvalid argument types for function '>=':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT iv NOT BETWEEN INTERVAL '1 hour' AND INTERVAL '2 days' FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 10\nInvalid argument types for function 'IN':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT iv IN (INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 0 at position -1\nInvalid argument types for function 'IN':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT iv NOT IN (INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 0 at position -1\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT iv IS DISTINCT FROM INTERVAL '1 hour' FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT EQUAL_NULL(iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'IFNULL':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT COALESCE(iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 0 at position -1\n: interval literal is not supported in this"
            + " form.", refusal("SELECT SYSTEM$TYPEOF(INTERVAL '1 day')"));
    }

    /** A number, a VARIANT and a timestamp are no conversions into an interval. */
    @Test
    public void otherFamiliesAreRefused() {
        assertEquals("SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT iv = 1 FROM icmp"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'ICMP.N' of type [NUMBER(5,0)] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT iv = n FROM icmp"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'ICMP.V' of type [VARIANT] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT iv = v FROM icmp"));
        assertEquals("SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT iv IN (1, 2) FROM icmp"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'ICMP.TS' of type [TIMESTAMP_NTZ(9)] into"
            + " expected type [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT iv IN (ts) FROM icmp"));
    }

    /** A text is read in the interval's fields; an IN list refuses an unreadable member only when nothing matched. */
    @Test
    public void textIsReadAsTheInterval() {
        assertEquals("TRUE|TRUE|NULL", rows("SELECT iv = '+1 01:00:00', iv > '1 00:00:00', iv IN (NULL, '+0 01:00:00')"
            + " FROM icmp"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT iv IN ('+1 01:00:00', 'x'), iv IN ('x', '+1 01:00:00'),"
            + " iv IN (INTERVAL '25' HOUR, INTERVAL '1' DAY) FROM icmp"));
        assertTrue(refusal("SELECT iv IN ('x') FROM icmp").startsWith("Day-Time Interval 'x' is invalid, expected format"),
            refusal("SELECT iv IN ('x') FROM icmp"));
        assertTrue(refusal("SELECT iv NOT IN ('x', '+0 01:00:00') FROM icmp").startsWith("Day-Time Interval 'x' is"
            + " invalid,"), refusal("SELECT iv NOT IN ('x', '+0 01:00:00') FROM icmp"));
        assertTrue(refusal("SELECT iv = '25:00:00' FROM icmp").startsWith("Day-Time Interval '25:00:00' is invalid,"),
            refusal("SELECT iv = '25:00:00' FROM icmp"));
    }

    /**
     * A parenthesized value before IN is that value, not a row: a list of quoted-unit literals is refused at the IN
     * whether the list is flat or of one-value rows, the NOT spelling nowhere, and a text member is read as above.
     */
    @Test
    public void parenthesizedValueBeforeInIsTheValue() {
        final String in = "Invalid argument types for function 'IN': (INTERVAL DAY(9) TO SECOND(9), INTERVAL";
        assertEquals("SQL compilation error: error line 1 at position 12\n" + in + ")",
            refusal("SELECT (iv) IN (INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 12\n" + in + ", INTERVAL)",
            refusal("SELECT (iv) IN ((INTERVAL '1 hour'), (INTERVAL '2 hours')) FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 14\n" + in + ")",
            refusal("SELECT ((iv)) IN (INTERVAL '1 hour') FROM icmp"));
        assertEquals("SQL compilation error: error line 0 at position -1\n" + in + ")",
            refusal("SELECT (iv) NOT IN ((INTERVAL '1 hour')) FROM icmp"));
        assertEquals("TRUE|TRUE|FALSE", rows("SELECT (iv) IN ('+1 01:00:00', 'x'), (iv) IN (iv), (iv) NOT IN ((iv))"
            + " FROM icmp"));
    }

    /** IS NOT DISTINCT FROM is refused at its IS, where IS DISTINCT FROM points nowhere. */
    @Test
    public void notDistinctIsPlacedAtItsIs() {
        assertEquals("SQL compilation error: error line 1 at position 10\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT iv IS NOT DISTINCT FROM INTERVAL '1 hour' FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 25\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT INTERVAL '1 hour' IS NOT DISTINCT FROM iv FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 28\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT 1 FROM icmp WHERE iv IS NOT DISTINCT FROM INTERVAL '1 hour'"));
        assertEquals("SQL compilation error: error line 2 at position 2\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT 1 FROM icmp WHERE n = 1 AND iv\n  IS NOT DISTINCT FROM INTERVAL '2 days'"));
        assertEquals("SQL compilation error: error line 0 at position -1\nInvalid argument types for function"
            + " 'EQUAL_NULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT 1 FROM icmp WHERE iv IS DISTINCT FROM INTERVAL '1 hour'"));
        assertEquals("SQL compilation error: error line 1 at position 21\nInvalid argument types for function"
            + " 'EQUAL_NULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            refusal("SELECT (SELECT 1, 2) IS NOT DISTINCT FROM 1"));
    }

    /**
     * A simple CASE value or a DECODE search beside a quoted-unit literal is refused as a conversion, the literal
     * named from the plan by its parts as written, into the subject's type or into ANY.
     */
    @Test
    public void simpleCaseAndDecodeNameTheLiteral() {
        engine.execute("CREATE OR REPLACE TABLE icase (g NUMBER, s VARCHAR)");
        engine.execute("INSERT INTO icase VALUES (1, 'a')");
        final String offered = "SQL compilation error:\nCan not convert parameter ";
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN INTERVAL '1 hour' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('HOURS', '2')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN INTERVAL '2 HOURS' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('day', '1', 'hours', '2')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN INTERVAL '1 day, 2 hours' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1', 'minutes', '2', 'seconds', '3')' of type [INTERVAL] into"
            + " expected type [INTERVAL DAY(9) TO SECOND(9)]",
            refusal("SELECT CASE iv WHEN INTERVAL '1 hour,2 minutes , 3 seconds' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('Hour', '+01')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN INTERVAL ' +01  Hour ' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('SECOND', '10')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN INTERVAL '10' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [TIMESTAMP_NTZ(9)]",
            refusal("SELECT CASE ts WHEN INTERVAL '1 hour' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [VARIANT]",
            refusal("SELECT CASE v WHEN INTERVAL '1 hour' THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [VARCHAR(16777216)]",
            refusal("SELECT CASE s WHEN INTERVAL '1 hour' THEN 1 END FROM icase"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [ANY]",
            refusal("SELECT CASE INTERVAL '1 hour' WHEN iv THEN 1 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [ANY]",
            refusal("SELECT CASE NULL WHEN INTERVAL '1 hour' THEN 1 END"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT CASE iv WHEN iv THEN 1 WHEN INTERVAL '1 hour' THEN 2 END"
            + " FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT DECODE(iv, INTERVAL '1 hour', 1, 0) FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('days', '2')' of type [INTERVAL] into expected type"
            + " [INTERVAL DAY(9) TO SECOND(9)]", refusal("SELECT DECODE(iv, iv, 1, INTERVAL '2 days', 2, 0) FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [ANY]",
            refusal("SELECT DECODE(INTERVAL '1 hour', iv, 1, 0) FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')' of type [INTERVAL] into expected type [NUMBER(38,0)]",
            refusal("SELECT DECODE(g, 1, 1, INTERVAL '1 hour', 2, 0) FROM icase"));
        assertEquals("1|1", rows("SELECT CASE iv WHEN iv THEN 1 ELSE 0 END, DECODE(iv, iv, 1, 0) FROM icmp"));
    }

    /** A VARIANT beside an interval is no conversion in either order, in a comparison or a conditional. */
    @Test
    public void variantBesideAnIntervalIsRefused() {
        final String intervalIntoVariant = "SQL compilation error:\nCan not convert parameter 'ICMP.IV' of type"
            + " [INTERVAL DAY(9) TO SECOND(9)] into expected type [VARIANT]";
        assertEquals(intervalIntoVariant, refusal("SELECT v = iv FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT v > iv FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT v IN (1, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT v BETWEEN iv AND iv FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT EQUAL_NULL(v, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT v IS DISTINCT FROM iv FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT CASE v WHEN iv THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT DECODE(v, iv, 1, 0) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT IFF(n = 1, v, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT NVL(v, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT COALESCE(v, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT GREATEST(v, iv) FROM icmp"));
        assertEquals(intervalIntoVariant, refusal("SELECT CASE WHEN n = 1 THEN v ELSE iv END FROM icmp"));
        final String variantIntoInterval = "SQL compilation error:\nCan not convert parameter 'ICMP.V' of type [VARIANT]"
            + " into expected type [INTERVAL DAY(9) TO SECOND(9)]";
        assertEquals(variantIntoInterval, refusal("SELECT COALESCE(iv, v) FROM icmp"));
        assertEquals(variantIntoInterval, refusal("SELECT GREATEST(iv, v) FROM icmp"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'CAST('1' AS INTERVAL HOUR(9))' of type"
            + " [INTERVAL HOUR(9)] into expected type [VARIANT]", refusal("SELECT TO_VARIANT(1) = '1'::INTERVAL HOUR"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'CAST('1' AS INTERVAL DAY(9))' of type"
            + " [INTERVAL DAY(9)] into expected type [VARIANT]", refusal("SELECT v = INTERVAL '1' DAY FROM icmp"));
        assertEquals("SQL compilation error: error line 1 at position 9\nInvalid argument types for function '=':"
            + " (VARIANT, INTERVAL)", refusal("SELECT v = INTERVAL '1 hour' FROM icmp"));
    }

    /** A quoted-unit literal among a conditional's values is refused at the call, as the conditional is planned. */
    @Test
    public void conditionalsRefuseQuotedUnitLiterals() {
        final String at = "SQL compilation error: error line 1 at position 7\nInvalid argument types for function ";
        assertEquals(at + "'NULLIF': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT NULLIF(iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals(at + "'NULLIF': (INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT NULLIF(INTERVAL '1 hour', iv) FROM icmp"));
        assertEquals(at + "'NVL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)", refusal("SELECT NVL(iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals(at + "'IFNULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT IFNULL(iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals(at + "'IFF': (BOOLEAN, INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT IFF(n = 1, iv, INTERVAL '1 hour') FROM icmp"));
        assertEquals(at + "'IFF': (BOOLEAN, INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT NVL2(iv, INTERVAL '1 hour', iv) FROM icmp"));
        assertEquals(at + "'IFNULL': (INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT COALESCE(INTERVAL '1 hour', iv) FROM icmp"));
        assertEquals(at + "'IFNULL': (INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT COALESCE(iv, INTERVAL '1 hour', iv) FROM icmp"));
        assertEquals(at + "'IFNULL': (INTERVAL, INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT COALESCE(INTERVAL '1 hour', iv, iv) FROM icmp"));
        assertEquals(at + "'IFNULL': (INTERVAL DAY(9) TO SECOND(9), INTERVAL)",
            refusal("SELECT COALESCE(iv, iv, iv, INTERVAL '2 days') FROM icmp"));
        assertEquals("+1 01:00:00.000000000|NULL", rows("SELECT IFF(n = 1, iv, iv)::VARCHAR, NULLIF(iv, iv) FROM icmp"));
    }

    /** A text arm after an interval arm is read as the interval; a text arm before one is refused. */
    @Test
    public void setOperationsReadTextAfterTheInterval() {
        assertEquals("+1 01:00:00.000000000", rows("SELECT x::VARCHAR FROM (SELECT iv AS x FROM icmp UNION"
            + " SELECT '+1 01:00:00')"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16];INTERVAL DAY(9) TO SECOND(9)[SB16]", rows("SELECT"
            + " SYSTEM$TYPEOF(x) FROM (SELECT iv AS x FROM icmp UNION ALL SELECT '+1 01:00:00')"));
        assertTrue(refusal("SELECT iv FROM icmp UNION ALL SELECT 'x'").startsWith("Day-Time Interval 'x' is invalid,"),
            refusal("SELECT iv FROM icmp UNION ALL SELECT 'x'"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(11)]",
            refusal("SELECT '+1 01:00:00' UNION ALL SELECT iv FROM icmp"));
        assertEquals("SQL compilation error:\ninconsistent data type for result columns for set operator input branches,"
            + " expected NUMBER(1,0), got INTERVAL DAY(9) TO SECOND(9)", refusal("SELECT iv FROM icmp UNION ALL SELECT 1"));
    }

    /**
     * A quoted-unit literal is named by its parts as the text reads them: a sign apart from its number joins it,
     * a comment is dropped, and a fraction or an exponent stays as written.
     */
    @Test
    public void quotedLiteralPartsAreNamedAsRead() {
        final String offered = "SQL compilation error:\nCan not convert parameter ";
        final String expected = "' of type [INTERVAL] into expected type [INTERVAL DAY(9) TO SECOND(9)]";
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '-1')" + expected,
            refusal("SELECT CASE iv WHEN INTERVAL '- 1 hour' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('seconds', '1e2')" + expected,
            refusal("SELECT CASE iv WHEN INTERVAL '1e2 seconds' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hour', '1')" + expected,
            refusal("SELECT CASE iv WHEN INTERVAL '1 /* c */ hour' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hours', '.5')" + expected,
            refusal("SELECT CASE iv WHEN INTERVAL '.5 hours' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hours', '2', 'minutes', '30')" + expected,
            refusal("SELECT CASE iv WHEN INTERVAL '2 hours, 30 minutes' THEN 1 ELSE 0 END FROM icmp"));
        assertEquals(offered + "'INTERVAL_LITERAL('hours', '+1.5')" + expected,
            refusal("SELECT DECODE(iv, INTERVAL '+1.5 hours', 1, 0) FROM icmp"));
    }
}
