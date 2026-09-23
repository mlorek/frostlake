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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A date shift or a date difference over a value its planned function does not read is refused in that
 * function's name: DATEADD, TIMEADD and TIMESTAMPADD are {@code DATE_ADD<UNITS>TOTIMESTAMP} over the amount and
 * the value, whichever alias spells the unit, and DATEDIFF, TIMEDIFF and TIMESTAMPDIFF are
 * {@code DATE_DIFF<DATE|TIMESTAMP>IN<UNITS>} over the two values. A BINARY, BOOLEAN, ARRAY, OBJECT, NUMBER or
 * FLOAT value is refused, where a VARIANT, a text, a DATE, a TIMESTAMP or a NULL is taken. Every cell is
 * live-verified.
 */
public class DateShiftInternalNameTest extends BaseDatabaseTest {

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertRefused(final String[][] cells) {
        for (final String[] cell : cells) {
            final String sql = cell[0];
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                sql + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    @Test
    public void everyUnitNamesItsOwnShift() {
        assertRefused(new String[][] {
            {"SELECT DATEADD(year, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(year, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(quarter, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDQUARTERSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(quarter, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDQUARTERSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(month, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(month, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(week, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDWEEKSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(week, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDWEEKSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(day, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(day, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(hour, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(hour, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(minute, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(minute, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(second, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(second, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(millisecond, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMILLISTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(millisecond, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMILLISTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(microsecond, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMICROSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(microsecond, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMICROSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(nanosecond, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDNANOSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(nanosecond, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDNANOSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
        });
    }

    @Test
    public void everyAliasNamesTheShiftOfItsUnit() {
        assertRefused(new String[][] {
            {"SELECT DATEADD(y, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(yy, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(yyyy, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(yr, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(years, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDYEARSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(q, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDQUARTERSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(qtr, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDQUARTERSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(mm, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(mon, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(months, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(w, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDWEEKSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(wk, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDWEEKSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(woy, 1, X'00')", "SQL compilation error: ['WOY'] is not a valid date/time component for function DATEADD."},
            {"SELECT DATEADD(d, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(dd, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(days, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(dayofmonth, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(h, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(hh, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(hr, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(hours, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(m, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(mi, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(min, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(minutes, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMINUTESTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(s, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(sec, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(seconds, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(ms, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMILLISTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(msec, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMILLISTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(us, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMICROSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(usec, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMICROSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(ns, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDNANOSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(nsec, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDNANOSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
        });
    }

    @Test
    public void onlyATemporalATextOrAVariantIsShifted() {
        assertRefused(new String[][] {
            {"SELECT DATEADD(day, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(day, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(day, 1, ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), ARRAY)"},
            {"SELECT DATEADD(day, 1, OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), OBJECT)"},
            {"SELECT DATEADD(day, 1, 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT DATEADD(day, 1, 1.5::FLOAT)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), FLOAT)"},
            {"SELECT DATEADD(day, 1, TO_TIME('10:00:00'))", "SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9)."},
            {"SELECT DATEADD(hour, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(hour, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(hour, 1, ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), ARRAY)"},
            {"SELECT DATEADD(hour, 1, OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), OBJECT)"},
            {"SELECT DATEADD(hour, 1, 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT DATEADD(hour, 1, 1.5::FLOAT)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), FLOAT)"},
            {"SELECT DATEADD(month, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(month, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEADD(month, 1, ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), ARRAY)"},
            {"SELECT DATEADD(month, 1, OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), OBJECT)"},
            {"SELECT DATEADD(month, 1, 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT DATEADD(month, 1, 1.5::FLOAT)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDMONTHSTOTIMESTAMP': (NUMBER(1,0), FLOAT)"},
            {"SELECT DATEADD(month, 1, TO_TIME('10:00:00'))", "SQL compilation error: ['MONTH'] is not a valid date/time component for function DATEADD and type TIME(9)."},
        });
        for (final String[] cell : new String[][] {
            {"SELECT SYSTEM$TYPEOF(DATEADD(year, 1, TO_DATE('2020-01-01')))", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(quarter, 1, TO_DATE('2020-01-01')))", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(month, 1, TO_DATE('2020-01-01')))", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(week, 1, TO_DATE('2020-01-01')))", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(day, 1, TO_DATE('2020-01-01')))", "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(hour, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(minute, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(second, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(millisecond, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(microsecond, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(nanosecond, 1, TO_DATE('2020-01-01')))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT DATEADD(day, 1, TO_DATE('2020-01-01'))", "2020-01-02"},
            {"SELECT DATEADD(day, 1, NULL)", "null"},
            {"SELECT DATEADD(hour, 1, NULL)", "null"},
            {"SELECT DATEADD(month, 1, TO_DATE('2020-01-01'))", "2020-02-01"},
            {"SELECT DATEADD(month, 1, NULL)", "null"},
        }) {
            assertEquals(cell[1].toLowerCase(), answer(cell[0]).toLowerCase(), cell[0]);
        }
    }

    @Test
    public void theOtherShiftsAndTheDifferencesShareTheNaming() {
        assertRefused(new String[][] {
            {"SELECT TIMEADD(day, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT TIMESTAMPADD(day, 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT TIMEADD(hour, 1, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDHOURSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)"},
            {"SELECT DATEDIFF(day, X'00', X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINDAYS': (BINARY(1), BINARY(1))"},
            {"SELECT DATEDIFF(day, TRUE, TO_DATE('2020-01-01'))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFDATEINDAYS': (BOOLEAN, DATE)"},
            {"SELECT DATEDIFF(day, TO_DATE('2020-01-01'), ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFDATEINDAYS': (DATE, ARRAY)"},
            {"SELECT TIMEDIFF(hour, X'00', X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINHOURS': (BINARY(1), BINARY(1))"},
            {"SELECT TIMESTAMPDIFF(day, TRUE, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINDAYS': (BOOLEAN, BOOLEAN)"},
            {"SELECT DATEDIFF(month, X'00', TO_DATE('2020-01-01'))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFDATEINMONTHS': (BINARY(1), DATE)"},
            {"SELECT DATEDIFF(hour, X'00', TO_DATE('2020-01-01'))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFDATEINHOURS': (BINARY(1), DATE)"},
            {"SELECT DATEADD(day, 1, X'00') FROM (SELECT 1) WHERE FALSE", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD('day', 1, X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATEADD(day, 1, OBJECT_CONSTRUCT('a', 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), OBJECT)"},
            {"SELECT DATEADD(dow, 1, X'00')", "SQL compilation error: ['DOW'] is not a valid date/time component for function DATEADD."},
            {"SELECT DATEADD(epoch_second, 1, X'00')", "SQL compilation error: ['EPOCH_SECOND'] is not a valid date/time component for function DATEADD."},
            {"SELECT DATEDIFF(week, TRUE, TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINWEEKS': (BOOLEAN, BOOLEAN)"},
            {"SELECT DATEDIFF(nanosecond, X'00', X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINNANOSECONDS': (BINARY(1), BINARY(1))"},
            {"SELECT TIMESTAMPDIFF(minute, X'00', X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_DIFFTIMESTAMPINMINUTES': (BINARY(1), BINARY(1))"},
            {"SELECT DATEADD(day, 1, TO_BINARY('00', 'HEX'))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(67108864))"},
        });
    }
}
