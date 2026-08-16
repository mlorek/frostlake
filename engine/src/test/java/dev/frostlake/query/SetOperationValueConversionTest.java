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
 * A set operation's VALUES take the folded column's type, not the type the arm that produced them had.
 * The declared type was already the fold; the rows still came back as each arm made them, so a column
 * declared NUMBER(38,2) handed back a value with scale 0 and one declared TIMESTAMP_NTZ handed back a
 * date:
 *
 * <pre>
 *   i UNION ALL n    NUMBER(38,2)     was [1, 2.50]                    is [1.00, 2.50]
 *   n UNION ALL m    NUMBER(12,4)     was [2.50, 0.5000]               is [2.5000, 0.5000]
 *   n UNION ALL f    FLOAT            was [2.50, 3.5]                  is [2.5, 3.5]
 *   d UNION ALL ts   TIMESTAMP_NTZ    was [2020-01-01, …T10:00]        is [2020-01-01T00:00, …T10:00]
 * </pre>
 *
 * <p>The conversion already existed but only ran when SOME arm was a string, so a purely numeric or
 * temporal union was skipped entirely.
 *
 * <p><b>A scale-0 target is left alone deliberately.</b> Re-wrapping an integral Long as a BigDecimal
 * changes what the driver hands back without changing a single rendered digit, and four existing tests
 * assert the Java type they receive. The conversion runs where it alters the value and nowhere else.
 *
 * <p>What made this safe to land where the conditional twin of it was reverted twice: set-operation
 * dedup already compares by VALUE and not by scale. {@code SELECT 1 UNION SELECT 1.00} is one row on
 * both engines, and so are the EXCEPT and INTERSECT forms — so converting the values cannot change
 * which rows survive. Those cells are asserted below as the guard they are.
 */
public class SetOperationValueConversionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sv (i INT, n NUMBER(10,2), m NUMBER(12,4),"
            + " f FLOAT, d DATE, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO sv VALUES (1, 2.50, 0.5000, 3.5, '2020-01-01',"
            + " '2020-01-01 10:00:00')");
    }

    /** Every row's first column, joined. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            all.append(String.valueOf(rs.getValue(0)));
        }
        return all.toString();
    }

    /** Two arms of the named columns. */
    private String twoArms(final String left, final String op, final String right) {
        return rows("SELECT " + left + " FROM sv " + op + " SELECT " + right + " FROM sv");
    }

    /** Every arm's value takes the folded SCALE. */
    @Test
    public void everyArmTakesTheFoldedScale() {
        assertEquals("1.00,2.50", twoArms("i", "UNION ALL", "n"));
        assertEquals("2.5000,0.5000", twoArms("n", "UNION ALL", "m"));
        assertEquals("0.5000,2.5000", twoArms("m", "UNION ALL", "n"),
            "and the same whichever arm leads");
    }

    /** An exact arm beside a FLOAT one becomes a float, losing the trailing zero. */
    @Test
    public void anExactArmBesideAFloatBecomesAFloat() {
        assertEquals("2.5,3.5", twoArms("n", "UNION ALL", "f"));
        assertEquals("3.5,2.5", twoArms("f", "UNION ALL", "n"));
    }

    /** A DATE arm beside a TIMESTAMP one becomes a timestamp at midnight. */
    @Test
    public void aDateArmBesideATimestampBecomesATimestamp() {
        assertEquals("2020-01-01T00:00,2020-01-01T10:00", twoArms("d", "UNION ALL", "ts"));
        assertEquals("2020-01-01T10:00,2020-01-01T00:00", twoArms("ts", "UNION ALL", "d"));
    }

    /** A string arm still converts, which is the case that already worked. */
    @Test
    public void aStringArmIsUnchanged() {
        assertEquals("1,7", twoArms("i", "UNION ALL", "'7'"));
    }

    /**
     * The guard the whole change rests on: dedup compares by VALUE, so converting the values cannot
     * change which rows survive a UNION, an EXCEPT or an INTERSECT.
     */
    @Test
    public void dedupComparesByValueAndNotByScale() {
        assertEquals("1", twoArms("1", "UNION", "1.00"));
        assertEquals("", twoArms("1", "EXCEPT", "1.00"));
        assertEquals("1", twoArms("1", "INTERSECT", "1.00"));
        assertEquals("", rows("SELECT '2020-01-01'::DATE FROM sv"
            + " EXCEPT SELECT '2020-01-01 00:00:00'::TIMESTAMP_NTZ FROM sv"));
    }

    /** A scale-0 fold changes no digit, and must leave the value as it was. */
    @Test
    public void aScaleZeroFoldIsLeftAlone() {
        assertEquals("1,1", twoArms("i", "UNION ALL", "i"));
        assertEquals("1,7", twoArms("i", "UNION ALL", "7"));
    }
}
