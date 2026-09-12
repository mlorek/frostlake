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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LAG and LEAD's DEFAULT is one more branch of a conditional: the call declares the FOLD of its
 * argument and its default, and both the value it hands back and the default are converted to that
 * fold — exactly as COALESCE would convert them. Live-verified:
 *
 * <ul>
 *   <li>{@code LAG(a, 1, 0)} over a NUMBER(10,2) declares NUMBER(10,2) and the default reads 0.00;
 *       {@code LAG(a, 1, 1.555)} declares NUMBER(11,3) and the COLUMN reads 1.000; a NUMBER(10,0)
 *       beside 1.5 declares NUMBER(11,1) and reads 1.0;</li>
 *   <li>a text default that spells a number joins as that number ('7.5' reads 7.50); one that does not
 *       is refused at the row ("Numeric value 'x' is not recognized"), and a VARCHAR column given a
 *       numeric default is refused the same way — the fold is numeric either way;</li>
 *   <li>a DATE beside a TIMESTAMP is TIMESTAMP_NTZ and the date reads as midnight; a DATE beside a
 *       number is refused at compile time in the conditional's own words; a number beside TRUE is
 *       BOOLEAN and 1.00 reads true; a NUMBER beside a FLOAT is FLOAT;</li>
 *   <li>a default may be a COLUMN, read from the current row — {@code LAG(a, 1, b)} on the first row
 *       is that row's b — and a NUMBER(10,0) column beside a NUMBER(10,2) one folds to NUMBER(12,2).</li>
 * </ul>
 *
 * <p>Frostlake used to hand the default back as written (0, not 0.00), keep the argument's own type
 * whatever the default, and refuse a column default as an invalid identifier.
 *
 * <p>NOT COVERED: {@code SYSTEM$TYPEOF} over a call whose fold refuses a value at the row — live types
 * it without computing, Frostlake computes and refuses.
 */
public class LagDefaultConversionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a NUMBER(10,2), i NUMBER(10,0), d DATE, s VARCHAR(5),"
            + " f FLOAT, b NUMBER(10,2), ts TIMESTAMP_NTZ, bo BOOLEAN)");
        engine.execute("INSERT INTO gw SELECT 1.00, 1, '2020-01-01', 'x', 1.5, 9.99, '2020-01-01 10:00:00', TRUE");
        engine.execute("INSERT INTO gw SELECT 2.00, 2, '2020-01-02', 'y', 2.5, 8.88, '2020-01-02 10:00:00', FALSE");
    }

    /** Every row's text, in order, or the refusal with its line breaks shown as bars. */
    private String values(final String call) {
        final String expression = call.contains(" OVER ") ? call : call + " OVER (ORDER BY a)";
        try {
            final ResultSet rs = engine.executeQuery("SELECT TO_VARCHAR(" + expression + ") FROM gw ORDER BY a");
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                out.append(String.valueOf(rs.getValue(0)));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String typeOf(final String call) {
        final String expression = call.contains(" OVER ") ? call : call + " OVER (ORDER BY a)";
        try {
            final ResultSet rs = engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expression + ") FROM gw LIMIT 1");
            rs.next();
            return String.valueOf(rs.getValue(0)).replaceAll("\\[SB[0-9]+\\]|\\[LOB\\]|\\[DOUBLE\\]", "");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The default is converted to the fold, and so is the column. */
    @Test
    public void theDefaultAndTheColumnReadAtTheFold() {
        assertEquals("0.00 | 1.00", values("LAG(a, 1, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a, 1, 0)"));
        assertEquals("2.00 | 0.00", values("LEAD(a, 1, 0)"));
        assertEquals("NUMBER(10,2)", typeOf("LEAD(a, 1, 0)"));
        assertEquals("5.00 | 1.00", values("LAG(a, 1, 5)"));
        assertEquals("1.50 | 1.00", values("LAG(a, 1, 1.5)"));
        assertEquals("1.555 | 1.000", values("LAG(a, 1, 1.555)"), "the column widens to the default's scale");
        assertEquals("NUMBER(11,3)", typeOf("LAG(a, 1, 1.555)"));
        assertEquals("1.5555 | 1.0000", values("LAG(a, 1, 1.5555)"));
        assertEquals("NUMBER(12,4)", typeOf("LAG(a, 1, 1.5555)"));
        assertEquals("1.5 | 1.0", values("LAG(i, 1, 1.5)"));
        assertEquals("NUMBER(11,1)", typeOf("LAG(i, 1, 1.5)"));
        assertEquals("0 | 1", values("LAG(i, 1, 0)"));
        assertEquals("NUMBER(10,0)", typeOf("LAG(i, 1, 0)"));
        assertEquals("2.5 | 1.0", values("LAG(i, 1, 2.5)"));
        assertEquals("0.00 | 0.00", values("LAG(a, 2, 0)"), "every row takes the default");
        assertEquals("null | 1.00", values("LAG(a)"));
        assertEquals("null | 1.00", values("LAG(a, 1, NULL)"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a, 1, NULL)"));
        assertEquals("2.00 | 1.00", values("LAG(a, 1, 1 + 1)"), "an expression default");
        assertEquals("3.00 | 1.00", values("LAG(a, 1, 3::NUMBER(3,1))"));
        assertEquals("0.000 | 1.000", values("LAG(a, 1, 0::NUMBER(5,3))"));
        assertEquals("NUMBER(11,3)", typeOf("LAG(a, 1, 0::NUMBER(5,3))"));
        assertEquals("0.00 | 1.00", values("LAG(a, 1, 0::NUMBER(20,1))"));
        assertEquals("NUMBER(21,2)", typeOf("LAG(a, 1, 0::NUMBER(20,1))"));
        assertEquals("0.5 | 1.0", values("LAG(i, 1, 0.5::NUMBER(5,1))"));
        assertEquals("NUMBER(11,1)", typeOf("LAG(i, 1, 0.5::NUMBER(5,1))"));
    }

    /** ★ Text joins as the number it spells, or is refused at the row. */
    @Test
    public void textJoinsAsANumberOrIsRefused() {
        assertEquals("7.00 | 1.00", values("LAG(a, 1, '7')"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a, 1, '7')"));
        assertEquals("7.50 | 1.00", values("LAG(a, 1, '7.5')"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a, 1, '7.5')"));
        assertEquals("Numeric value 'x' is not recognized", values("LAG(a, 1, 'x')"));
        assertEquals("Numeric value 'x' is not recognized", values("LAG(s, 1, 0)"),
            "a VARCHAR column beside a numeric default folds numeric, and the column's text fails");
        assertEquals("zz | x", values("LAG(s, 1, 'zz')"));
        assertEquals("VARCHAR(5)", typeOf("LAG(s, 1, 'zz')"));
        assertEquals("abcdefghij | x", values("LAG(s, 1, 'abcdefghij')"));
        assertEquals("VARCHAR(10)", typeOf("LAG(s, 1, 'abcdefghij')"), "the wider string wins");
        assertEquals("0 | 1.5", values("LAG(f, 1, 0)"));
        assertEquals("FLOAT", typeOf("LAG(f, 1, 0)"));
        assertEquals("7 | 1.5", values("LAG(f, 1, '7')"));
    }

    /** ★ Across families: temporal folds convert, a number beside TRUE is BOOLEAN, a DATE beside a number is refused. */
    @Test
    public void foldsAcrossFamiliesConvertOrRefuse() {
        assertEquals("2000-01-01 | 2020-01-01", values("LAG(d, 1, '2000-01-01')"));
        assertEquals("DATE", typeOf("LAG(d, 1, '2000-01-01')"));
        assertEquals("2000-01-01 | 2020-01-01", values("LAG(d, 1, DATE '2000-01-01')"));
        assertEquals("2020-01-01 10:00:00.000 | 2020-01-01 00:00:00.000", values("LAG(d, 1, ts)"),
            "the date reads as midnight at the timestamp fold");
        assertEquals("TIMESTAMP_NTZ(9)", typeOf("LAG(d, 1, ts)"));
        assertEquals("2020-01-01 00:00:00.000 | 2020-01-01 10:00:00.000", values("LAG(ts, 1, d)"));
        assertEquals("2000-01-01 00:00:00.000 | 2020-01-01 10:00:00.000", values("LAG(ts, 1, '2000-01-01')"));
        assertEquals("TIMESTAMP_NTZ(9)", typeOf("LAG(ts, 1, '2000-01-01')"));
        assertEquals("SQL compilation error:|Can not convert parameter '0' of type [NUMBER(1,0)]"
            + " into expected type [DATE]", values("LAG(d, 1, 0)"));
        assertEquals("SQL compilation error:|Can not convert parameter '0' of type [NUMBER(1,0)]"
            + " into expected type [DATE]", typeOf("LAG(d, 1, 0)"), "refused at compile time");
        assertEquals("true | true", values("LAG(a, 1, TRUE)"), "1.00 reads true at a BOOLEAN fold");
        assertEquals("BOOLEAN", typeOf("LAG(a, 1, TRUE)"));
        assertEquals("false | true", values("LAG(bo, 1, 0)"));
        assertEquals("BOOLEAN", typeOf("LAG(bo, 1, 0)"));
        assertEquals("true | true", values("LAG(bo, 1, 'true')"));
        assertEquals("1.5 | 1", values("LAG(a, 1, 1.5::FLOAT)"), "a NUMBER beside a FLOAT is FLOAT");
        assertEquals("FLOAT", typeOf("LAG(a, 1, 1.5::FLOAT)"));
    }

    /** ★ A column default is read from the current row, and folds like any branch. */
    @Test
    public void aColumnDefaultReadsTheCurrentRow() {
        assertEquals("9.99 | 1.00", values("LAG(a, 1, b)"));
        assertEquals("NUMBER(10,2)", typeOf("LAG(a, 1, b)"));
        assertEquals("1.00 | 1.00", values("LAG(a, 1, i)"));
        assertEquals("NUMBER(12,2)", typeOf("LAG(a, 1, i)"), "ten integer digits and two decimals");
        assertEquals("1.00 | 1.00", values("LAG(i, 1, a)"));
        assertEquals("NUMBER(12,2)", typeOf("LAG(i, 1, a)"));
        assertEquals("1.5 | 1", values("LAG(a, 1, f)"));
        assertEquals("FLOAT", typeOf("LAG(a, 1, f)"));
    }

    /** The conditionals the fold comes from agree with it. */
    @Test
    public void theConditionalsAgree() {
        assertEquals("0.00 | 1.00", values("NVL(LAG(a) OVER (ORDER BY a), 0)"));
        assertEquals("0.00 | 1.00", values("IFNULL(LAG(a) OVER (ORDER BY a), 0)"));
        assertEquals("1.5 | 1.0", values("COALESCE(LAG(i) OVER (ORDER BY a), 1.5)"));
        assertEquals("NUMBER(11,1)", typeOf("COALESCE(LAG(i) OVER (ORDER BY a), 1.5)"));
        assertEquals("1.00 | 1.00", values("FIRST_VALUE(a) OVER (ORDER BY a)"));
        assertEquals("null | null", values("NTH_VALUE(a, 3) OVER (ORDER BY a)"));
    }
}
