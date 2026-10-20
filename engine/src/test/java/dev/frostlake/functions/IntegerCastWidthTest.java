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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * INT, INTEGER and BIGINT are NUMBER(38,0), and so is a bare NUMBER: a cast to any of them keeps every one of
 * thirty-eight digits and refuses past them, from every source, in the source's own sentence. The INT spelling
 * used to keep only the low 64 bits — {@code 99999999999999999999::INT} was 7766279631452241919 — and a bare NUMBER
 * range-checked only a text, so a FLOAT, a VARIANT or a DECFLOAT of 10^38 became a NUMBER(38,0) of thirty-nine
 * digits.
 */
public class IntegerCastWidthTest extends MeasuredCellsTestSupport {

    private static final String PAST_38 = "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value ";

    @Test
    public void anIntegerCastKeepsEveryDigitPastSixtyFourBits() {
        assertAnswer("99999999999999999999", "SELECT 99999999999999999999::INT");
        assertAnswer("99999999999999999999", "SELECT '99999999999999999999'::INTEGER");
        assertAnswer("100000000000000000000", "SELECT CAST(99999999999999999999.5 AS BIGINT)");
        assertAnswer("99999999999999999999", "SELECT PARSE_JSON('99999999999999999999')::INT");
        assertAnswer("99999999999999999999", "SELECT TRY_CAST('99999999999999999999' AS INT)");
        assertAnswer("99999999999999999999", "SELECT TO_NUMBER('99999999999999999999')::BIGINT");
        assertAnswer("9223372036854775808|-9223372036854775809|9223372036854775807",
            "SELECT 9223372036854775808::INT, -9223372036854775809::INT, 9223372036854775807::INT");
        assertAnswer("true", "SELECT 99999999999999999999::INT = 99999999999999999999");
        assertAnswer("NUMBER(38,0)[SB16]", "SELECT SYSTEM$TYPEOF(99999999999999999999::INT)");
        // A value that fits 64 bits still rounds half away from zero, as it always did.
        assertAnswer("3|-3|4", "SELECT 2.5::INT, -2.5::INT, 3.9::INT");
    }

    @Test
    public void anIntegerCastIsStoredWithEveryDigit() {
        engine.execute("CREATE TABLE icw_t (a INT)");
        engine.execute("INSERT INTO icw_t SELECT '99999999999999999999'::INT");
        engine.execute("INSERT INTO icw_t SELECT 99999999999999999999.4::BIGINT");
        assertAnswer("99999999999999999999 ;; 99999999999999999999", "SELECT a FROM icw_t");
        engine.execute("CREATE TABLE icw_c AS SELECT 123456789012345678901234567890::INT a");
        assertAnswer("123456789012345678901234567890", "SELECT a FROM icw_c");
    }

    @Test
    public void anIntegerCastPastThirtyEightDigitsIsRefusedAsTheNumberCastIs() {
        assertAnswer(PAST_38 + "100000000000000000000000000000000000000",
            "SELECT CAST('1e38' AS DECFLOAT)::INT");
        assertAnswer(PAST_38 + "999999999999999939709166371603178586112", "SELECT 1e39::FLOAT::INT");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value "
            + "100000000000000000000000000000000000000", "SELECT PARSE_JSON('1e38')::INT");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT '1e38'::INT");
        assertAnswer("null", "SELECT TRY_CAST('1e38' AS INT)");
    }

    @Test
    public void aBareNumberCastIsRangeCheckedFromEverySource() {
        assertAnswer(PAST_38 + "999999999999999939709166371603178586112", "SELECT 1e39::FLOAT::NUMBER");
        assertAnswer("REFUSED Number out of representable range: type FIXED, value "
            + "1000000000000000000000000000000000000000", "SELECT PARSE_JSON('1e39')::NUMBER");
        assertAnswer(PAST_38 + "100000000000000000000000000000000000000",
            "SELECT CAST('1e38' AS DECFLOAT)::NUMBER");
        assertAnswer(PAST_38 + "100000000000000000000000000000000000000",
            "SELECT CAST('1e38' AS DECFLOAT)::NUMBER + 1");
        // The bare spelling and the explicit one agree.
        assertAnswer(PAST_38 + "100000000000000000000000000000000000000",
            "SELECT CAST(CAST('1e38' AS DECFLOAT) AS NUMBER(38,0))");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.ICW_W failed on column X with error: " + PAST_38.substring(8)
            + "100000000000000000000000000000000000000",
            "CREATE TABLE icw_w AS SELECT CAST('1e38' AS DECFLOAT)::NUMBER x");
        // Thirty-eight nines still fit.
        assertAnswer("99999999999999999999999999999999999999",
            "SELECT CAST('99999999999999999999999999999999999999' AS DECFLOAT)::NUMBER");
    }
}
