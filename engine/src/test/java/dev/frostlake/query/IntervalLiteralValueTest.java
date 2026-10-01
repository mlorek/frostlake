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
 * A unit-suffixed interval literal is a VALUE: {@code INTERVAL '1' DAY} and {@code INTERVAL '24' HOUR} are
 * the same span, so they compare equal, deduplicate to one row and sort together, and {@code INTERVAL '1'
 * YEAR} is {@code INTERVAL '12' MONTH}. Its text follows the fields of the type it is read AS — its own field
 * for a bare literal ({@code +1}, {@code +1.000000000} for a SECOND, {@code +14} for a MONTH), and the
 * column's for a value in a column of another field ({@code INTERVAL '25' HOUR} in a DAY(9) column prints
 * {@code +1}). Live-verified cell by cell.
 */
public class IntervalLiteralValueTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ilv (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ, half TIMESTAMP_NTZ,"
            + " n INT, s VARCHAR)");
        engine.execute("INSERT INTO ilv VALUES ('2024-01-02 01:00:00', '2024-01-01 00:00:00',"
            + " '2024-01-02 01:00:00.5', 5, '1')");
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

    /** Two literals of one family compare by span, whatever unit each is written in. */
    @Test
    public void literalsCompareBySpan() {
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' DAY = INTERVAL '24' HOUR,"
            + " INTERVAL '1' DAY > INTERVAL '23' HOUR, INTERVAL '1' DAY < INTERVAL '25' HOUR"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT INTERVAL '60' MINUTE = INTERVAL '1' HOUR,"
            + " INTERVAL '3600' SECOND = INTERVAL '1' HOUR, INTERVAL '-1' DAY < INTERVAL '0' DAY"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' YEAR = INTERVAL '12' MONTH,"
            + " INTERVAL '1' YEAR > INTERVAL '11' MONTH, INTERVAL '-1' YEAR < INTERVAL '1' MONTH"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT (ts - ts2) = INTERVAL '25' HOUR, (ts - ts2) > INTERVAL '1' DAY,"
            + " INTERVAL '1' DAY < (ts - ts2) FROM ilv"));
        assertEquals("FALSE|TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' DAY IS DISTINCT FROM INTERVAL '24' HOUR,"
            + " INTERVAL '1' DAY IN (INTERVAL '24' HOUR, INTERVAL '2' DAY), INTERVAL '1' DAY NOT IN (INTERVAL '48'"
            + " HOUR), INTERVAL '1' DAY BETWEEN INTERVAL '23' HOUR AND INTERVAL '25' HOUR"));
        assertEquals("1", rows("SELECT COUNT(*) FROM ilv WHERE ts - ts2 > INTERVAL '1' DAY"));
    }

    /** One span is one row to UNION, DISTINCT, INTERSECT, EXCEPT, COUNT(DISTINCT) and GROUP BY. */
    @Test
    public void oneSpanIsOneKey() {
        assertEquals("1", rows("SELECT COUNT(*) FROM (SELECT INTERVAL '1' DAY UNION SELECT INTERVAL '24' HOUR)"));
        assertEquals("1", rows("SELECT COUNT(DISTINCT x) FROM (SELECT INTERVAL '1' DAY AS x UNION ALL"
            + " SELECT INTERVAL '24' HOUR)"));
        assertEquals("1", rows("SELECT COUNT(*) FROM (SELECT INTERVAL '1' DAY INTERSECT SELECT INTERVAL '24' HOUR)"));
        assertEquals("0", rows("SELECT COUNT(*) FROM (SELECT INTERVAL '1' DAY EXCEPT SELECT INTERVAL '24' HOUR)"));
        assertEquals("1", rows("SELECT COUNT(*) FROM (SELECT DISTINCT x FROM (SELECT INTERVAL '1' DAY AS x"
            + " UNION ALL SELECT INTERVAL '24' HOUR UNION ALL SELECT INTERVAL '1440' MINUTE))"));
        assertEquals("+1|2;+2|1", rows("SELECT TO_VARCHAR(x), COUNT(*) FROM (SELECT INTERVAL '1' DAY AS x"
            + " UNION ALL SELECT INTERVAL '24' HOUR UNION ALL SELECT INTERVAL '2' DAY) GROUP BY x ORDER BY x"));
        assertEquals("+24", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '24' HOUR AS x UNION"
            + " SELECT INTERVAL '1' DAY)"));
    }

    /** Intervals sort, and MIN / MAX / GREATEST / LEAST pick, by span — and print in the column's field. */
    @Test
    public void spansOrderAndPrintInTheColumnsField() {
        assertEquals("-0;+1;+1;+2", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '2' DAY AS x UNION ALL"
            + " SELECT INTERVAL '25' HOUR UNION ALL SELECT INTERVAL '1' DAY UNION ALL SELECT INTERVAL '-1' MINUTE)"
            + " ORDER BY x"));
        assertEquals("-0|+1", rows("SELECT TO_VARCHAR(MIN(x)), TO_VARCHAR(MAX(x)) FROM (SELECT INTERVAL '1' DAY"
            + " AS x UNION ALL SELECT INTERVAL '25' HOUR UNION ALL SELECT INTERVAL '-3' HOUR)"));
        assertEquals("+60;+90", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '90' MINUTE AS x UNION ALL"
            + " SELECT INTERVAL '1' HOUR) ORDER BY x"));
        assertEquals("+1.000000000;+86400.000000000", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '1' SECOND"
            + " AS x UNION ALL SELECT INTERVAL '1' DAY) ORDER BY x"));
        assertEquals("-12;+12;+14", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '14' MONTH AS x UNION ALL"
            + " SELECT INTERVAL '-1' YEAR UNION ALL SELECT INTERVAL '1' YEAR) ORDER BY x"));
        assertEquals("-1;+1;+1", rows("SELECT TO_VARCHAR(x) FROM (SELECT INTERVAL '1' YEAR AS x UNION ALL"
            + " SELECT INTERVAL '14' MONTH UNION ALL SELECT INTERVAL '-13' MONTH) ORDER BY x"));
        assertEquals("-0|+1", rows("SELECT TO_VARCHAR(MIN(x)), TO_VARCHAR(MAX(x)) FROM (SELECT INTERVAL '1' YEAR"
            + " AS x UNION ALL SELECT INTERVAL '3' MONTH UNION ALL SELECT INTERVAL '-5' MONTH)"));
        assertEquals("+1|+0", rows("SELECT TO_VARCHAR(GREATEST(INTERVAL '1' DAY, INTERVAL '23' HOUR)),"
            + " TO_VARCHAR(LEAST(INTERVAL '1' DAY, INTERVAL '23' HOUR))"));
        assertEquals("+24|+1|+1", rows("SELECT TO_VARCHAR(COALESCE(INTERVAL '24' HOUR, INTERVAL '1' DAY)),"
            + " TO_VARCHAR(IFF(TRUE, INTERVAL '1' DAY, INTERVAL '25' HOUR)),"
            + " TO_VARCHAR(IFF(FALSE, INTERVAL '1' DAY, INTERVAL '25' HOUR))"));
    }

    /** A literal's text is its own field's, its sign always spelled. */
    @Test
    public void aLiteralPrintsInItsOwnField() {
        assertEquals("+1|-1|+0|+100", rows("SELECT TO_VARCHAR(INTERVAL '1' DAY), TO_VARCHAR(INTERVAL '-1' DAY),"
            + " TO_VARCHAR(INTERVAL '0' DAY), TO_VARCHAR(INTERVAL '100' DAY)"));
        assertEquals("+1|+25|-1|+90", rows("SELECT TO_VARCHAR(INTERVAL '1' HOUR), TO_VARCHAR(INTERVAL '25' HOUR),"
            + " TO_VARCHAR(INTERVAL '-1' HOUR), TO_VARCHAR(INTERVAL '90' MINUTE)"));
        assertEquals("+1.000000000|+0.000000000|-5.000000000|+90061.000000000", rows("SELECT"
            + " TO_VARCHAR(INTERVAL '1' SECOND), TO_VARCHAR(INTERVAL '0' SECOND), TO_VARCHAR(INTERVAL '-5' SECOND),"
            + " TO_VARCHAR(INTERVAL '90061' SECOND)"));
        assertEquals("+1|-2|+14|-14|+0", rows("SELECT TO_VARCHAR(INTERVAL '1' YEAR), TO_VARCHAR(INTERVAL '-2' YEAR),"
            + " TO_VARCHAR(INTERVAL '14' MONTH), TO_VARCHAR(INTERVAL '-14' MONTH), TO_VARCHAR(INTERVAL '0' MONTH)"));
        assertEquals("+1|+25|+1 01:00:00.500000000", rows("SELECT INTERVAL '1' DAY::VARCHAR,"
            + " CAST(INTERVAL '25' HOUR AS VARCHAR), (half - ts2)::VARCHAR FROM ilv"));
        assertEquals("+1 01:00:00.500000000|-1 01:00:00.500000000", rows("SELECT TO_VARCHAR(half - ts2),"
            + " TO_VARCHAR(ts2 - half) FROM ilv"));
    }

    /** A cast to a number reads the span in the type's trailing field: a DAY in days, a difference in seconds. */
    @Test
    public void aNumberIsTheSpanInTheTrailingField() {
        assertEquals("1|1|90000.5", rows("SELECT INTERVAL '1' DAY::NUMBER, CAST(INTERVAL '1' HOUR AS INT),"
            + " (half - ts2)::NUMBER(10,1) FROM ilv"));
    }

    /** A text on the interval's right is read in the interval's fields, and refused when it does not fit. */
    @Test
    public void aTextIsReadInTheIntervalsFields() {
        assertEquals("TRUE|FALSE|TRUE|TRUE", rows("SELECT INTERVAL '1' DAY = '1', INTERVAL '24' HOUR = '1',"
            + " INTERVAL '1' YEAR = '+1', INTERVAL '1' DAY = s FROM ilv"));
        assertEquals("TRUE|TRUE|TRUE", rows("SELECT INTERVAL '1' SECOND = '1.0', (half - ts2) = '1 01:00:00.5',"
            + " (half - ts2) > '1 01:00:00' FROM ilv"));
        assertTrue(refusal("SELECT INTERVAL '1' DAY = '1.5'").startsWith("Day-Time Interval '1.5' is invalid,"
            + " expected format is '<sign>D(p) HH24:MM:SS.F(fsp)' for subtype DAY TO SECOND"),
            refusal("SELECT INTERVAL '1' DAY = '1.5'"));
        assertEquals("Year-Month Interval 'x' is invalid, expected format is '<sign>Y(p)-MM' for subtype"
            + " YEAR TO MONTH, '<sign>Y(p)' for subtype YEAR, or '<sign>M(p)' for subtype MONTH",
            refusal("SELECT INTERVAL '1' YEAR = 'x'"));
        assertEquals("Day-Time Interval is '1.0000000001' invalid, value of leading or fractional second field is"
            + " greater than specified precision/fsp", refusal("SELECT INTERVAL '1' SECOND = '1.0000000001'"));
    }

    /** Every other pairing is refused while the statement compiles. */
    @Test
    public void otherPairingsAreRefusedWhileCompiling() {
        assertEquals("SQL compilation error:\nCan not convert parameter 'CAST('1' AS INTERVAL MONTH(9))' of type"
            + " [INTERVAL MONTH(9)] into expected type [INTERVAL DAY(9)]",
            refusal("SELECT INTERVAL '1' DAY = INTERVAL '1' MONTH"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'CAST('1' AS INTERVAL DAY(9))' of type"
            + " [INTERVAL DAY(9)] into expected type [INTERVAL MONTH(9)]",
            refusal("SELECT INTERVAL '1' MONTH > INTERVAL '1' DAY"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'ILV.N' of type [NUMBER(38,0)] into"
            + " expected type [INTERVAL DAY(9)]", refusal("SELECT INTERVAL '1' DAY = n FROM ilv"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'TRUE' of type [BOOLEAN] into expected"
            + " type [INTERVAL DAY(9)]", refusal("SELECT INTERVAL '1' DAY = TRUE"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9)] and [VARCHAR(1)]",
            refusal("SELECT '1' = INTERVAL '1' DAY"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [TO_VARCHAR(TO_INTERVAL_DAY_TIME('1'), 'YYYY')] expected 1, got 2",
            refusal("SELECT TO_VARCHAR(INTERVAL '1' DAY, 'YYYY')"));
    }

    /** A day-time literal holds nine digits in its leading field; past them it is refused when read. */
    @Test
    public void aLiteralHoldsNineLeadingDigits() {
        assertEquals("Day-Time Interval is '1000000000' invalid, value of leading or fractional second field is"
            + " greater than specified precision/fsp", refusal("SELECT TO_VARCHAR(INTERVAL '1000000000' SECOND)"));
        assertEquals("+999999999", rows("SELECT TO_VARCHAR(INTERVAL '999999999' DAY)"));
    }
}
