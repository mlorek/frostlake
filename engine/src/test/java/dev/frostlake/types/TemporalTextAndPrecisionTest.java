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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A date, time or timestamp becomes text in the account's own form wherever it does — TO_VARCHAR, a cast to
 * VARCHAR, a concatenation, TO_JSON, a VARIANT and an ARRAY or OBJECT around it: a space between date and time and
 * three fractional digits, a zoned value with its offset. A cast of a text, temporal or VARIANT value to a TIME or
 * TIMESTAMP with a declared precision truncates the fraction to that many digits, as a column of that type does; a
 * NUMBER keeps every digit it carries. Every cell is live-verified.
 */
public class TemporalTextAndPrecisionTest extends BaseDatabaseTest {

    private static final String NINE = "'YYYY-MM-DD HH24:MI:SS.FF9'";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer("SELECT " + cell[0]), cell[0]);
        }
    }

    @Test
    public void aTemporalValueBecomesTextInTheAccountsForm() {
        final String ntz = "'2024-01-01 00:00:00'::TIMESTAMP_NTZ";
        final String fraction = "'2024-01-01 12:34:56.123456789'::TIMESTAMP_NTZ";
        assertCells(new String[][] {
            {"TO_VARCHAR(" + ntz + ")", "2024-01-01 00:00:00.000"},
            {"TO_JSON(TO_VARIANT(" + ntz + "))", "\"2024-01-01 00:00:00.000\""},
            {"TO_VARIANT(" + ntz + ")::VARCHAR", "2024-01-01 00:00:00.000"},
            {"CAST(" + ntz + " AS VARCHAR)", "2024-01-01 00:00:00.000"},
            {ntz + " || ''", "2024-01-01 00:00:00.000"},
            {"ARRAY_CONSTRUCT(" + ntz + ")::VARCHAR", "[\"2024-01-01 00:00:00.000\"]"},
            {"OBJECT_CONSTRUCT('a', " + ntz + ")::VARCHAR", "{\"a\":\"2024-01-01 00:00:00.000\"}"},
            {"TO_VARCHAR(" + fraction + ")", "2024-01-01 12:34:56.123"},
            {"TO_JSON(TO_VARIANT(" + fraction + "))", "\"2024-01-01 12:34:56.123\""},
            {"TO_VARCHAR('2024-01-01 12:34:56.123456789'::TIMESTAMP_LTZ(3))", "2024-01-01 12:34:56.123 Z"},
            {"TO_JSON(TO_VARIANT('2024-01-01 12:34:56.123456789'::TIMESTAMP_TZ(3)))", "\"2024-01-01 12:34:56.123 Z\""},
            {"TO_VARCHAR('2024-01-01'::DATE)", "2024-01-01"},
            {"TO_JSON(TO_VARIANT('2024-01-01'::DATE))", "\"2024-01-01\""},
            {"TO_VARCHAR('12:34:56.123456789'::TIME)", "12:34:56"},
            {"TO_JSON(TO_VARIANT('12:34:56.123456789'::TIME(3)))", "\"12:34:56\""},
        });
    }

    @Test
    public void aCastToADeclaredPrecisionTruncatesTheFraction() {
        final String value = "'2024-01-01 12:34:56.123456789'";
        assertCells(new String[][] {
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(0), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(1), " + NINE + ")", "2024-01-01 12:34:56.100000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(2), " + NINE + ")", "2024-01-01 12:34:56.120000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(3), " + NINE + ")", "2024-01-01 12:34:56.123000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(6), " + NINE + ")", "2024-01-01 12:34:56.123456000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(9), " + NINE + ")", "2024-01-01 12:34:56.123456789"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_LTZ(0), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_LTZ(3), " + NINE + ")", "2024-01-01 12:34:56.123000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_TZ(0), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_TZ(6), " + NINE + ")", "2024-01-01 12:34:56.123456000"},
            {"TO_VARCHAR('12:34:56.123456789'::TIME(0), 'HH24:MI:SS.FF9')", "12:34:56.000000000"},
            {"TO_VARCHAR('12:34:56.123456789'::TIME(3), 'HH24:MI:SS.FF9')", "12:34:56.123000000"},
            {"TO_VARCHAR('12:34:56.123456789'::TIME(6), 'HH24:MI:SS.FF9')", "12:34:56.123456000"},
            {"TO_VARCHAR(CAST('2024-01-01 12:34:56.999999999' AS TIMESTAMP_NTZ(0)), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(CAST('2024-01-01 12:34:56.987654321'::TIMESTAMP_NTZ(9) AS TIMESTAMP_NTZ(2)), " + NINE + ")",
                "2024-01-01 12:34:56.980000000"},
            {"TO_VARCHAR(TO_TIMESTAMP_NTZ('2024-01-01 12:34:56.987654321')::TIMESTAMP_NTZ(1), " + NINE + ")",
                "2024-01-01 12:34:56.900000000"},
            {"'2024-01-01 12:34:56.9'::TIMESTAMP_NTZ(0) = '2024-01-01 12:34:56'::TIMESTAMP_NTZ", "true"},
            {"TO_VARCHAR(TO_TIMESTAMP_NTZ(2.5)::TIMESTAMP_NTZ(0), " + NINE + ")", "1970-01-01 00:00:02.000000000"},
            {"TO_VARCHAR(PARSE_JSON('\"2024-01-01 12:34:56.789\"')::TIMESTAMP_NTZ(0), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(TRY_CAST('2024-01-01 12:34:56.789' AS TIMESTAMP_NTZ(0)), " + NINE + ")", "2024-01-01 12:34:56.000000000"},
            {"TO_VARCHAR(2.5::TIMESTAMP_NTZ(0), " + NINE + ")", "1970-01-01 00:00:02.500000000"},
            {"TO_VARCHAR(2.123456::TIMESTAMP_NTZ(3), " + NINE + ")", "1970-01-01 00:00:02.123456000"},
            {"TO_VARCHAR(2.5::TIMESTAMP_LTZ(0), " + NINE + ")", "1970-01-01 00:00:02.500000000"},
            {"2.5::TIMESTAMP_NTZ(0) = 2::TIMESTAMP_NTZ(0)", "false"},
            {"TO_VARCHAR(" + value + "::TIMESTAMP_NTZ(0))", "2024-01-01 12:34:56.000"},
            {"SYSTEM$TYPEOF('2024-01-01 12:34:56.9'::TIMESTAMP_NTZ(0))", "TIMESTAMP_NTZ(0)[SB8]"},
        });
    }

    @Test
    public void aColumnOfADeclaredPrecisionKeepsThatManyDigits() {
        engine.execute("CREATE TABLE tt (a TIMESTAMP_NTZ(0), b TIMESTAMP_NTZ(3), c TIME(0), d TIMESTAMP_LTZ(0))");
        engine.execute("INSERT INTO tt VALUES ('2024-01-01 12:34:56.123456789', '2024-01-01 12:34:56.123456789',"
            + " '12:34:56.987', '2024-01-01 12:34:56.123456789')");
        assertEquals("2024-01-01 12:34:56.000000000 2024-01-01 12:34:56.123000000 12:34:56.000000000 2024-01-01 12:34:56.000000000",
            answer("SELECT TO_VARCHAR(a, " + NINE + ") || ' ' || TO_VARCHAR(b, " + NINE + ") || ' ' || TO_VARCHAR(c, 'HH24:MI:SS.FF9')"
                + " || ' ' || TO_VARCHAR(d, " + NINE + ") FROM tt"));
    }
}
