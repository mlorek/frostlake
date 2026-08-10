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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The declared PRECISION of an integer-returning function, which is not 38 and is not one number
 * either. Frostlake declared every one of them NUMBER(38,0) — the width of a NUMBER column — where the
 * account declares only as many digits as the result can occupy, in five distinct widths.
 *
 * <p>The widths track what the value can BE, but they cannot be derived from that story: DAYOFYEAR is
 * four where DAYOFWEEK is two, BIT_LENGTH is nineteen where OCTET_LENGTH is eighteen, and DATE_PART
 * changes width with the PART it is asked for.
 */
public class IntegerResultWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE iw (s VARCHAR(10), b BINARY(4), a ARRAY, n NUMBER)");
        engine.execute("INSERT INTO iw SELECT 'abc', TO_BINARY('4142'), ARRAY_CONSTRUCT(1, 2), 5");
    }

    private void assertPrecision(final int expected, final String expression) {
        engine.execute("CREATE OR REPLACE VIEW iw_v AS SELECT " + expression + " AS c FROM iw");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.iw_v");
        rs.next();
        assertEquals("{\"type\":\"FIXED\",\"precision\":" + expected + ",\"scale\":0,\"nullable\":true}",
            String.valueOf(rs.getValue("data_type")), expression);
    }

    /** A length or a count is eighteen digits — whatever family the argument is. */
    @Test
    public void aLengthOrCountIsEighteen() {
        assertPrecision(18, "LENGTH(s)");
        assertPrecision(18, "LENGTH(b)");
        assertPrecision(18, "LEN(s)");
        assertPrecision(18, "OCTET_LENGTH(s)");
        assertPrecision(18, "REGEXP_INSTR(s, 'b')");
        assertPrecision(18, "REGEXP_COUNT(s, 'b')");
        assertPrecision(18, "COUNT(*)");
        assertPrecision(18, "COUNT(s)");
    }

    /** A bit length and a hash need the extra digit a signed 64-bit value reaches. */
    @Test
    public void aBitLengthOrHashIsNineteen() {
        assertPrecision(19, "BIT_LENGTH(s)");
        assertPrecision(19, "BIT_LENGTH(b)");
        assertPrecision(19, "HASH(s)");
    }

    /** A position, a collection size and a temporal difference are nine. */
    @Test
    public void aPositionOrSizeIsNine() {
        assertPrecision(9, "CHARINDEX('b', s)");
        assertPrecision(9, "CHARINDEX('b', s, 1)");
        assertPrecision(9, "ARRAY_SIZE(a)");
        assertPrecision(9, "ARRAY_POSITION(1::VARIANT, a)");
        assertPrecision(9, "DATEDIFF('day', CURRENT_DATE(), CURRENT_DATE())");
        assertPrecision(9, "TIMESTAMPDIFF('day', CURRENT_DATE(), CURRENT_DATE())");
    }

    /** A date part is two digits, or four for the ones that count years or days of the year. */
    @Test
    public void aDatePartIsTwoOrFour() {
        assertPrecision(2, "MONTH(CURRENT_DATE())");
        assertPrecision(2, "DAY(CURRENT_DATE())");
        assertPrecision(2, "DAYOFMONTH(CURRENT_DATE())");
        assertPrecision(2, "DAYOFWEEK(CURRENT_DATE())");
        assertPrecision(2, "DAYOFWEEKISO(CURRENT_DATE())");
        assertPrecision(2, "WEEK(CURRENT_DATE())");
        assertPrecision(2, "WEEKISO(CURRENT_DATE())");
        assertPrecision(2, "QUARTER(CURRENT_DATE())");
        assertPrecision(2, "HOUR(CURRENT_TIMESTAMP())");
        assertPrecision(2, "MINUTE(CURRENT_TIMESTAMP())");
        assertPrecision(2, "SECOND(CURRENT_TIMESTAMP())");
        assertPrecision(4, "YEAR(CURRENT_DATE())");
        assertPrecision(4, "DAYOFYEAR(CURRENT_DATE())");
        assertPrecision(4, "YEAROFWEEK(CURRENT_DATE())");
    }

    /** DATE_PART and EXTRACT take the width of the PART they are asked for, not a fixed one. */
    @Test
    public void datePartFollowsItsPart() {
        assertPrecision(4, "DATE_PART('year', CURRENT_DATE())");
        assertPrecision(2, "DATE_PART('month', CURRENT_DATE())");
        assertPrecision(2, "DATE_PART('second', CURRENT_TIMESTAMP())");
        assertPrecision(4, "EXTRACT(year FROM CURRENT_DATE())");
        assertPrecision(2, "EXTRACT(month FROM CURRENT_DATE())");
    }

    /** The row counters declare a width too, though they are window functions rather than calls. */
    @Test
    public void theRowCountersAreEighteen() {
        assertPrecision(18, "ROW_NUMBER() OVER (ORDER BY n)");
        assertPrecision(18, "RANK() OVER (ORDER BY n)");
        assertPrecision(18, "DENSE_RANK() OVER (ORDER BY n)");
        assertPrecision(18, "NTILE(2) OVER (ORDER BY n)");
    }

    /** And an ordinary NUMBER is still thirty-eight — the narrowing is per function, not global. */
    @Test
    public void anOrdinaryNumberIsStillThirtyEight() {
        assertPrecision(38, "n");
        assertPrecision(38, "ABS(n)");
    }
}
