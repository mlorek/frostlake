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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a date shift and a date difference take is judged while the statement COMPILES, so an empty table
 * refuses as a full one does, and a fraction of a day ROUNDS. A DATE moves by an exact number only; the
 * interval functions refuse in the name of the function the call is planned as, a TIME is measured
 * against a TIME alone, and an untyped NULL makes the call NULL. Live-verified.
 */
public class DateShiftArgumentTypesTest extends BaseDatabaseTest {

    /** One row of every family the rules tell apart, and an empty twin. */
    private void createFamilies() {
        final String columns = "(d DATE, ts TIMESTAMP_NTZ, tm TIME, g VARCHAR(10), f FLOAT, n NUMBER(5,2), i INT,"
            + " b BOOLEAN, v VARIANT)";
        engine.execute("CREATE OR REPLACE TABLE fam " + columns);
        engine.execute("CREATE OR REPLACE TABLE fam0 " + columns);
        engine.execute("INSERT INTO fam SELECT '2024-01-15', '2024-01-15 10:00:00', '10:00:00', '2024-01-01',"
            + " 1.5, 2.25, 3, TRUE, PARSE_JSON('2')");
    }

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
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

    /** A compile-time argument-type refusal anchored on line 1. */
    private static String at(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    /** A DATE is shifted by an exact number only: a FLOAT beside it is refused in either order. */
    @Test
    public void aDateIsShiftedByAnExactNumberOnly() {
        createFamilies();
        assertEquals(at(9, "+", "DATE, FLOAT"), refusal("SELECT d + f FROM fam"));
        assertEquals(at(9, "+", "FLOAT, DATE"), refusal("SELECT f + d FROM fam"));
        assertEquals(at(9, "-", "DATE, FLOAT"), refusal("SELECT d - f FROM fam0"));
        assertEquals(at(9, "+", "DATE, FLOAT"), refusal("SELECT d + SQRT(4) FROM fam"));
        assertEquals(at(12, "+", "DATE, FLOAT"), refusal("SELECT 1, d + f FROM fam"));
        assertEquals("2024-01-16", scalar("SELECT d + 1e0 FROM fam"));
    }

    /** A fraction of a day rounds half away from zero, through the operator and DATEADD alike. */
    @Test
    public void aFractionOfADayRoundsHalfAwayFromZero() {
        createFamilies();
        assertEquals("2024-01-17", scalar("SELECT d + 1.5 FROM fam"));
        assertEquals("2024-01-16", scalar("SELECT d + 1.4 FROM fam"));
        assertEquals("2024-01-12", scalar("SELECT d - 2.5 FROM fam"));
        assertEquals("2024-01-13", scalar("SELECT d + -1.5 FROM fam"));
        assertEquals("2024-01-20", scalar("SELECT d + n * 2 FROM fam"));
        assertEquals("2024-01-03", scalar("SELECT '2024-01-01'::DATE + 1.5"));
        assertEquals("2024-01-17", scalar("SELECT DATEADD(day, 1.5, d) FROM fam"));
        assertEquals("2024-01-14", scalar("SELECT DATEADD(day, -0.5, d) FROM fam"));
        assertEquals("2024-01-18", scalar("SELECT DATEADD(day, 2.5::FLOAT, d) FROM fam"));
        assertEquals("2024-03-15", scalar("SELECT DATEADD(month, 1.5, d) FROM fam"));
        assertEquals("2024-01-15 12:00:00.000", scalar("SELECT TO_VARCHAR(DATEADD(hour, 1.5, ts)) FROM fam"));
    }

    /** A text or VARIANT amount is read as the number it spells, and one that spells none fails its row. */
    @Test
    public void aTextOrVariantAmountIsReadAsANumber() {
        createFamilies();
        assertEquals("2024-01-17", scalar("SELECT DATEADD(day, '1.5', d) FROM fam"));
        assertEquals("2024-01-17", scalar("SELECT DATEADD(day, ' 2 ', d) FROM fam"));
        assertEquals("2024-01-17", scalar("SELECT DATEADD(day, v, d) FROM fam"));
        assertEquals("2024-01-18", scalar("SELECT DATEADD(day, PARSE_JSON('\"3\"'), d) FROM fam"));
        final String unreadable = refusal("SELECT DATEADD(day, 'x', d) FROM fam");
        assertTrue(unreadable.contains("Numeric value 'x' is not recognized"), unreadable);
    }

    /** A shift is refused in the name of the planned function, its two value arguments listed. */
    @Test
    public void aShiftNamesThePlannedFunction() {
        createFamilies();
        assertEquals(at(7, "DATE_ADDDAYSTODATE", "BOOLEAN, DATE"), refusal("SELECT DATEADD(day, TRUE, d) FROM fam"));
        assertEquals(at(7, "DATE_ADDMONTHSTODATE", "BOOLEAN, DATE"),
            refusal("SELECT DATEADD(month, TRUE, d) FROM fam0"));
        assertEquals(at(7, "DATE_ADDHOURSTOTIMESTAMP", "BOOLEAN, TIMESTAMP_NTZ(9)"),
            refusal("SELECT DATEADD(hour, TRUE, d) FROM fam"));
        assertEquals(at(7, "DATE_ADDSECONDSTOTIME", "BOOLEAN, TIME(9)"),
            refusal("SELECT DATEADD(second, TRUE, tm) FROM fam"));
        assertEquals(at(7, "DATE_ADDDAYSTOTIMESTAMP", "BOOLEAN, VARCHAR(10)"),
            refusal("SELECT DATEADD(day, TRUE, g) FROM fam"));
        assertEquals(at(7, "DATE_ADDDAYSTOTIMESTAMP", "NUMBER(1,0), NUMBER(38,0)"),
            refusal("SELECT DATEADD(day, 1, i) FROM fam"));
        assertEquals(at(7, "DATE_ADDDAYSTOTIMESTAMP", "NUMBER(1,0), FLOAT"),
            refusal("SELECT DATEADD(day, 1, f) FROM fam0"));
        assertEquals(at(7, "DATE_ADDDAYSTODATE", "TIMESTAMP_NTZ(9), DATE"),
            refusal("SELECT DATEADD(day, ts, d) FROM fam"));
        assertEquals(at(7, "DATE_ADDMICROSTOTIMESTAMP", "BOOLEAN, TIMESTAMP_NTZ(9)"),
            refusal("SELECT DATEADD(microsecond, TRUE, ts) FROM fam"));
        assertEquals(at(7, "DATE_ADDHOURSTOTIME", "BOOLEAN, TIME(9)"), refusal("SELECT TIMEADD(hour, TRUE, tm) FROM fam"));
        assertEquals(at(7, "DATE_ADDDAYSTODATE", "BOOLEAN, DATE"),
            refusal("SELECT TIMESTAMPADD(day, TRUE, d) FROM fam"));
    }

    /** An untyped NULL in either value position makes the shift NULL, whatever stands beside it. */
    @Test
    public void anUntypedNullMakesTheShiftNull() {
        createFamilies();
        assertEquals("NULL", scalar("SELECT DATEADD(day, TRUE, NULL) FROM fam"));
        assertEquals("NULL", scalar("SELECT DATEADD(day, NULL, d) FROM fam"));
        assertEquals("NULL", scalar("SELECT DATEADD(day, NULL, '2024-01-01'::DATE)"));
    }

    /** A difference is refused in the name of the planned function, DATE or TIMESTAMP by its arguments. */
    @Test
    public void aDifferenceNamesThePlannedFunction() {
        createFamilies();
        assertEquals(at(7, "DATE_DIFFDATEINDAYS", "NUMBER(38,0), DATE"), refusal("SELECT DATEDIFF(day, i, d) FROM fam"));
        assertEquals(at(7, "DATE_DIFFTIMESTAMPINDAYS", "TIMESTAMP_NTZ(9), NUMBER(38,0)"),
            refusal("SELECT DATEDIFF(day, ts, i) FROM fam"));
        assertEquals(at(7, "DATE_DIFFDATEINMONTHS", "NUMBER(38,0), DATE"),
            refusal("SELECT DATEDIFF(month, i, d) FROM fam0"));
        assertEquals(at(7, "DATE_DIFFDATEINHOURS", "NUMBER(38,0), DATE"), refusal("SELECT DATEDIFF(hour, i, d) FROM fam"));
        assertEquals(at(7, "DATE_DIFFDATEINMICROSECONDS", "NUMBER(38,0), DATE"),
            refusal("SELECT DATEDIFF(microsecond, i, d) FROM fam"));
        assertEquals(at(7, "DATE_DIFFDATEINYEARS", "BOOLEAN, DATE"), refusal("SELECT DATEDIFF(year, b, d) FROM fam"));
        assertEquals(at(7, "DATE_DIFFTIMESTAMPINDAYS", "NUMBER(1,0), NUMBER(1,0)"),
            refusal("SELECT DATEDIFF(day, 1, 2) FROM fam"));
        assertEquals(at(7, "DATE_DIFFDATEINDAYS", "NUMBER(1,0), DATE"),
            refusal("SELECT DATEDIFF(day, 1, '2024-01-01'::DATE)"));
        assertEquals("14", scalar("SELECT DATEDIFF(day, g, d) FROM fam"));
        assertEquals("NULL", scalar("SELECT DATEDIFF(day, i, NULL) FROM fam"));
    }

    /** A TIME is measured against a TIME only, and is refused beside anything else under the written name. */
    @Test
    public void aTimeIsMeasuredAgainstATimeOnly() {
        createFamilies();
        assertEquals(at(7, "DATEDIFF", "VARCHAR(4), VARCHAR(10), TIME(9)"), refusal("SELECT DATEDIFF(hour, g, tm) FROM fam"));
        assertEquals(at(7, "DATEDIFF", "VARCHAR(5), VARCHAR(8), TIME(9)"),
            refusal("SELECT DATEDIFF(hours, '09:00:00', tm) FROM fam"));
        assertEquals(at(7, "DATEDIFF", "VARCHAR(1), VARCHAR(10), TIME(9)"), refusal("SELECT DATEDIFF(h, g, tm) FROM fam0"));
        assertEquals(at(7, "DATEDIFF", "VARCHAR(4), TIMESTAMP_NTZ(9), TIME(9)"),
            refusal("SELECT DATEDIFF(hour, ts, tm) FROM fam"));
        assertEquals(at(7, "DATEDIFF", "VARCHAR(6), TIME(9), NUMBER(1,0)"),
            refusal("SELECT DATEDIFF(second, tm, 5) FROM fam"));
        assertEquals(at(7, "TIMESTAMPDIFF", "VARCHAR(4), VARCHAR(10), TIME(9)"),
            refusal("SELECT TIMESTAMPDIFF(hour, g, tm) FROM fam"));
        assertEquals(at(7, "TIMEDIFF", "VARCHAR(4), VARCHAR(10), TIME(9)"), refusal("SELECT TIMEDIFF(hour, g, tm) FROM fam"));
        assertEquals("1", scalar("SELECT DATEDIFF(hour, '09:00:00'::TIME, tm) FROM fam"));
        assertEquals("NULL", scalar("SELECT DATEDIFF(hour, tm, NULL) FROM fam"));
    }

    /** The refusal lands where the call stands, and an unknown name is reported ahead of it. */
    @Test
    public void theRefusalLandsWhereTheCallStands() {
        createFamilies();
        assertEquals(at(10, "DATE_ADDDAYSTODATE", "BOOLEAN, DATE"), refusal("SELECT 1, DATEADD(day, TRUE, d) FROM fam"));
        assertEquals(at(24, "DATE_ADDDAYSTODATE", "BOOLEAN, DATE"),
            refusal("SELECT 1 FROM fam WHERE DATEADD(day, TRUE, d) IS NULL"));
        assertEquals(at(13, "DATE_ADDDAYSTODATE", "BOOLEAN, DATE"),
            refusal("SELECT UPPER(DATEADD(day, TRUE, d)) FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 30\ninvalid identifier 'MISSING'",
            refusal("SELECT DATEADD(day, TRUE, d), missing FROM fam"));
    }
}
