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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The internal names DATEADD and DATEDIFF are planned as, called directly: {@code DATE_ADD<UNITS>TO<KIND>(amount,
 * value)} and {@code DATE_DIFF<KIND>IN<UNITS>(from, to)} — their exact name set, the value each moves its
 * arguments to (a VARIANT as a cast to the kind moves it), their result types, their refusals, and that SHOW
 * FUNCTIONS lists none of them. Also DATEDIFF's own width, which follows the same planned kind, and its reading of
 * a text beside a DATE as a DATE.
 */
public class PlannedIntervalFunctionsTest extends BaseDatabaseTest {

    private static final String DATE = "TO_DATE('2020-01-31')";
    private static final String TIMESTAMP = "TO_TIMESTAMP_NTZ('2020-01-31 10:20:30.123456789')";
    private static final String TIME = "TO_TIME('10:20:30.123456789')";
    private static final String TS_FORMAT = "'YYYY-MM-DD HH24:MI:SS.FF9'";
    private static final String TIME_FORMAT = "'HH24:MI:SS.FF9'";

    @Test
    public void testShiftsIntoADate() {
        final String[][] cells = {
            {"YEARS", "2023-01-31"}, {"QUARTERS", "2020-10-31"}, {"MONTHS", "2020-04-30"}, {"WEEKS", "2020-02-21"},
            {"DAYS", "2020-02-03"}, {"HOURS", "2020-01-31"}, {"MINUTES", "2020-01-31"}, {"SECONDS", "2020-01-31"},
        };
        for (final String[] cell : cells) {
            final String call = "DATE_ADD" + cell[0] + "TODATE(3, " + DATE + ")";
            assertEquals(Arrays.asList(cell[1] + " | DATE[SB4]"),
                lines("SELECT TO_VARCHAR(" + call + "), SYSTEM$TYPEOF(" + call + ")"), call);
        }
    }

    @Test
    public void testShiftsIntoATimestamp() {
        final String[][] cells = {
            {"YEARS", "2023-01-31 10:20:30.123456789"}, {"QUARTERS", "2020-10-31 10:20:30.123456789"},
            {"MONTHS", "2020-04-30 10:20:30.123456789"}, {"WEEKS", "2020-02-21 10:20:30.123456789"},
            {"DAYS", "2020-02-03 10:20:30.123456789"}, {"HOURS", "2020-01-31 13:20:30.123456789"},
            {"MINUTES", "2020-01-31 10:23:30.123456789"}, {"SECONDS", "2020-01-31 10:20:33.123456789"},
            {"MILLIS", "2020-01-31 10:20:30.126456789"}, {"MICROS", "2020-01-31 10:20:30.123459789"},
            {"NANOS", "2020-01-31 10:20:30.123456792"},
        };
        for (final String[] cell : cells) {
            final String call = "DATE_ADD" + cell[0] + "TOTIMESTAMP(3, " + TIMESTAMP + ")";
            assertEquals(Arrays.asList(cell[1] + " | TIMESTAMP_NTZ(9)[SB16]"),
                lines("SELECT TO_VARCHAR(" + call + ", " + TS_FORMAT + "), SYSTEM$TYPEOF(" + call + ")"), call);
        }
    }

    @Test
    public void testShiftsIntoATime() {
        final String[][] cells = {
            {"HOURS", "13:20:30.123456789"}, {"MINUTES", "10:23:30.123456789"}, {"SECONDS", "10:20:33.123456789"},
            {"MILLIS", "10:20:30.126456789"}, {"MICROS", "10:20:30.123459789"}, {"NANOS", "10:20:30.123456792"},
        };
        for (final String[] cell : cells) {
            final String call = "DATE_ADD" + cell[0] + "TOTIME(3, " + TIME + ")";
            assertEquals(Arrays.asList(cell[1] + " | TIME(9)[SB8]"),
                lines("SELECT TO_VARCHAR(" + call + ", " + TIME_FORMAT + "), SYSTEM$TYPEOF(" + call + ")"), call);
        }
    }

    @Test
    public void testDifferences() {
        final String dates = "TO_DATE('2019-11-15'), TO_DATE('2020-01-31')";
        final String stamps = "TO_TIMESTAMP_NTZ('2019-11-15 23:59:59.999999999'), " + TIMESTAMP;
        final String times = "TO_TIME('01:02:03.5'), " + TIME;
        final String[][] cells = {
            {"DATEINYEARS", dates, "1 | NUMBER(9,0)[SB4]"}, {"DATEINQUARTERS", dates, "1 | NUMBER(9,0)[SB4]"},
            {"DATEINMONTHS", dates, "2 | NUMBER(9,0)[SB4]"}, {"DATEINWEEKS", dates, "11 | NUMBER(9,0)[SB4]"},
            {"DATEINDAYS", dates, "77 | NUMBER(9,0)[SB4]"}, {"DATEINHOURS", dates, "1848 | NUMBER(18,0)[SB8]"},
            {"DATEINMINUTES", dates, "110880 | NUMBER(18,0)[SB8]"},
            {"DATEINSECONDS", dates, "6652800 | NUMBER(18,0)[SB8]"},
            {"DATEINMILLISECONDS", dates, "6652800000 | NUMBER(18,0)[SB8]"},
            {"DATEINMICROSECONDS", dates, "6652800000000 | NUMBER(38,0)[SB16]"},
            {"DATEINNANOSECONDS", dates, "6652800000000000 | NUMBER(38,0)[SB16]"},
            {"TIMESTAMPINYEARS", stamps, "1 | NUMBER(9,0)[SB4]"}, {"TIMESTAMPINQUARTERS", stamps, "1 | NUMBER(9,0)[SB4]"},
            {"TIMESTAMPINMONTHS", stamps, "2 | NUMBER(9,0)[SB4]"}, {"TIMESTAMPINWEEKS", stamps, "11 | NUMBER(9,0)[SB4]"},
            {"TIMESTAMPINDAYS", stamps, "77 | NUMBER(9,0)[SB4]"}, {"TIMESTAMPINHOURS", stamps, "1835 | NUMBER(9,0)[SB4]"},
            {"TIMESTAMPINMINUTES", stamps, "110061 | NUMBER(18,0)[SB8]"},
            {"TIMESTAMPINSECONDS", stamps, "6603631 | NUMBER(18,0)[SB8]"},
            {"TIMESTAMPINMILLISECONDS", stamps, "6603630124 | NUMBER(38,0)[SB16]"},
            {"TIMESTAMPINMICROSECONDS", stamps, "6603630123457 | NUMBER(38,0)[SB16]"},
            {"TIMESTAMPINNANOSECONDS", stamps, "6603630123456790 | NUMBER(38,0)[SB16]"},
            {"TIMEINHOURS", times, "9 | NUMBER(9,0)[SB4]"}, {"TIMEINMINUTES", times, "558 | NUMBER(9,0)[SB4]"},
            {"TIMEINSECONDS", times, "33507 | NUMBER(9,0)[SB4]"},
            {"TIMEINMILLISECONDS", times, "33506623 | NUMBER(9,0)[SB4]"},
            {"TIMEINMICROSECONDS", times, "33506623456 | NUMBER(18,0)[SB8]"},
            {"TIMEINNANOSECONDS", times, "33506623456789 | NUMBER(18,0)[SB8]"},
        };
        for (final String[] cell : cells) {
            final String call = "DATE_DIFF" + cell[0] + "(" + cell[1] + ")";
            assertEquals(Arrays.asList(cell[2]), lines("SELECT " + call + ", SYSTEM$TYPEOF(" + call + ")"), call);
        }
        // The same argument order as DATEDIFF: from the first to the second.
        assertEquals(Arrays.asList("-1 | -1"), lines("SELECT DATE_DIFFDATEINDAYS(TO_DATE('2020-01-02'),"
            + " TO_DATE('2020-01-01')), DATEDIFF(day, TO_DATE('2020-01-02'), TO_DATE('2020-01-01'))"));
    }

    @Test
    public void testValuesMoveToTheKind() {
        assertEquals(Arrays.asList("2020-02-01 | 2020-01-30 | 2020-01-31 | 2020-02-01 | 2020-01-30"),
            lines("SELECT TO_VARCHAR(DATE_ADDHOURSTODATE(30, " + DATE + ")), TO_VARCHAR(DATE_ADDHOURSTODATE(-1, " + DATE
                + ")), TO_VARCHAR(DATE_ADDSECONDSTODATE(86399, " + DATE + ")), TO_VARCHAR(DATE_ADDSECONDSTODATE(86400, "
                + DATE + ")), TO_VARCHAR(DATE_ADDMINUTESTODATE(-1, " + DATE + "))"));
        assertEquals(Arrays.asList("2020-02-01 | 2020-02-01 | 2020-02-01 | 2021-02-28"),
            lines("SELECT TO_VARCHAR(DATE_ADDDAYSTODATE(1, TO_TIMESTAMP_NTZ('2020-01-31 10:00:00'))),"
                + " TO_VARCHAR(DATE_ADDDAYSTODATE(1, '2020-01-31')), TO_VARCHAR(DATE_ADDDAYSTODATE(1,"
                + " PARSE_JSON('\"2020-01-31\"'))), TO_VARCHAR(DATE_ADDYEARSTODATE(1,"
                + " TO_TIMESTAMP_TZ('2020-02-29 23:00:00 +05:00')))"));
        assertEquals(Arrays.asList("2020-02-01 00:00:00.000000000 | TIMESTAMP_NTZ(9)[SB16]"),
            lines("SELECT TO_VARCHAR(DATE_ADDDAYSTOTIMESTAMP(1, '2020-01-31'), " + TS_FORMAT + "),"
                + " SYSTEM$TYPEOF(DATE_ADDDAYSTOTIMESTAMP(1, TO_DATE('2020-01-31')))"));
        assertEquals(Arrays.asList("2020-02-01 10:00:00.000 +0500 | TIMESTAMP_TZ(9)[SB16]"),
            lines("SELECT TO_VARCHAR(DATE_ADDDAYSTOTIMESTAMP(1, TO_TIMESTAMP_TZ('2020-01-31 10:00:00 +05:00'))),"
                + " SYSTEM$TYPEOF(DATE_ADDDAYSTOTIMESTAMP(1, TO_TIMESTAMP_TZ('2020-01-31 10:00:00 +05:00')))"));
        assertEquals(Arrays.asList("11:00:00 | 11:00:00 | 00:00:00 | 11:00:00 | 23:29:00"),
            lines("SELECT TO_VARCHAR(DATE_ADDHOURSTOTIME(1, '10:00:00')), TO_VARCHAR(DATE_ADDHOURSTOTIME(1,"
                + " TO_TIMESTAMP_NTZ('2020-01-31 10:00:00'))), TO_VARCHAR(DATE_ADDNANOSTOTIME(1,"
                + " TO_TIME('23:59:59.999999999'))), TO_VARCHAR(DATE_ADDHOURSTOTIME(25, TO_TIME('10:00:00'))),"
                + " TO_VARCHAR(DATE_ADDMINUTESTOTIME(-61, TO_TIME('00:30:00')))"));
        // A fractional amount rounds half away from zero, a text amount is read as a number.
        assertEquals(Arrays.asList("2020-02-02 | 2020-01-29 | 2020-02-02 | 2020-02-02 | 2020-02-02"),
            lines("SELECT TO_VARCHAR(DATE_ADDDAYSTODATE(1.7, " + DATE + ")), TO_VARCHAR(DATE_ADDDAYSTODATE(-1.7, "
                + DATE + ")), TO_VARCHAR(DATE_ADDDAYSTODATE(1.5, " + DATE + ")), TO_VARCHAR(DATE_ADDDAYSTODATE('2', "
                + DATE + ")), TO_VARCHAR(DATE_ADDDAYSTODATE(1.5::FLOAT, " + DATE + "))"));
        assertEquals(Arrays.asList("2020-02-07 | 2021-02-28 | 2019-12-31"),
            lines("SELECT TO_VARCHAR(DATE_ADDWEEKSTODATE(1, " + DATE + ")), TO_VARCHAR(DATE_ADDQUARTERSTODATE(1,"
                + " TO_DATE('2020-11-30'))), TO_VARCHAR(DATE_ADDSECONDSTODATE(-1, TO_DATE('2020-01-01')))"));
        assertEquals(Arrays.asList("1 | 24 | 25 | 2 | 2 | 4 | 4 | 2"),
            lines("SELECT DATE_DIFFDATEINDAYS(TO_TIMESTAMP_NTZ('2020-01-01 23:00:00'), TO_TIMESTAMP_NTZ('2020-01-02"
                + " 01:00:00')), DATE_DIFFDATEINHOURS(TO_TIMESTAMP_NTZ('2020-01-01 23:00:00'),"
                + " TO_TIMESTAMP_NTZ('2020-01-02 01:00:00')), DATE_DIFFTIMESTAMPINHOURS(TO_DATE('2020-01-01'),"
                + " TO_TIMESTAMP_NTZ('2020-01-02 01:00:00')), DATE_DIFFTIMESTAMPINDAYS('2020-01-01', '2020-01-03'),"
                + " DATE_DIFFDATEINDAYS('2020-01-01', '2020-01-03'), DATE_DIFFTIMEINHOURS('01:00:00', '05:00:00'),"
                + " DATE_DIFFTIMEINHOURS(TO_TIMESTAMP_NTZ('2020-01-01 01:00:00'), TO_TIMESTAMP_NTZ('2020-01-02"
                + " 05:00:00')), DATE_DIFFDATEINDAYS(PARSE_JSON('\"2020-01-01\"'), TO_DATE('2020-01-03'))"));
        assertEquals(Arrays.asList("1 | 1 | 1 | 1"),
            lines("SELECT DATE_DIFFTIMESTAMPINDAYS(TO_TIMESTAMP_TZ('2020-01-01 23:00:00 +00:00'),"
                + " TO_TIMESTAMP_TZ('2020-01-02 01:00:00 +05:00')), DATE_DIFFDATEINWEEKS(TO_DATE('2020-01-05'),"
                + " TO_DATE('2020-01-06')), DATE_DIFFTIMESTAMPINMONTHS(TO_TIMESTAMP_NTZ('2020-01-31 23:59:59'),"
                + " TO_TIMESTAMP_NTZ('2020-02-01 00:00:00')), DATE_DIFFTIMEINSECONDS(TO_TIME('10:00:00.999'),"
                + " TO_TIME('10:00:01'))"));
    }

    @Test
    public void testNullsAndColumns() {
        assertEquals(Arrays.asList("NULL | NULL | NULL"), lines("SELECT DATE_ADDDAYSTODATE(NULL, " + DATE + "),"
            + " DATE_ADDDAYSTODATE(1, NULL), DATE_DIFFDATEINDAYS(NULL, " + DATE + ")"));
        engine.execute("CREATE TABLE tn (d DATE, n INT)");
        engine.execute("INSERT INTO tn VALUES ('2020-01-31', 1), (NULL, 2), ('2020-02-29', NULL)");
        assertEquals(Arrays.asList("2020-02-29", "NULL", "NULL"),
            lines("SELECT TO_VARCHAR(DATE_ADDMONTHSTODATE(n, d)) FROM tn ORDER BY n"));
        engine.execute("CREATE TABLE tdd AS SELECT DATE_ADDDAYSTODATE(1, " + DATE + ") AS a,"
            + " DATE_DIFFDATEINDAYS(TO_DATE('2020-01-02'), TO_DATE('2020-01-01')) AS b,"
            + " DATE_ADDHOURSTOTIME(1, TO_TIME('10:00:00')) AS c,"
            + " DATE_DIFFTIMESTAMPINNANOSECONDS(TO_TIMESTAMP_NTZ('2020-01-02'), TO_TIMESTAMP_NTZ('2020-01-01')) AS d");
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE tdd");
        final List<String> types = new ArrayList<String>();
        for (final Row row : described.getRows()) {
            types.add(cell(described, row, "name") + " " + cell(described, row, "type"));
        }
        assertEquals(Arrays.asList("A DATE", "B NUMBER(9,0)", "C TIME(9)", "D NUMBER(38,0)"), types);
        assertEquals("DATE_ADDDAYSTODATE(1, TO_DATE('2020-01-31'))", engine.executeQuery(
            "SELECT DATE_ADDDAYSTODATE(1, TO_DATE('2020-01-31'))").getColumns().get(0).getName().toUpperCase());
        assertEquals(Arrays.asList("2020-02-01"),
            lines("SELECT TO_VARCHAR(date_adddaystodate(1, " + DATE + "))"));
    }

    @Test
    public void testArgumentRefusals() {
        final String at = "SQL compilation error: error line 1 at position 7\n";
        final String[][] cells = {
            {"SELECT DATE_ADDDAYSTODATE(TRUE, " + DATE + ")",
                at + "Invalid argument types for function 'DATE_ADDDAYSTODATE': (BOOLEAN, DATE)"},
            {"SELECT DATE_ADDDAYSTODATE(1, 5)",
                at + "Invalid argument types for function 'DATE_ADDDAYSTODATE': (NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT DATE_ADDDAYSTODATE(TO_DATE('2020-01-01'), " + DATE + ")",
                at + "Invalid argument types for function 'DATE_ADDDAYSTODATE': (DATE, DATE)"},
            {"SELECT DATE_ADDDAYSTODATE(ARRAY_CONSTRUCT(), " + DATE + ")",
                at + "Invalid argument types for function 'DATE_ADDDAYSTODATE': (ARRAY, DATE)"},
            {"SELECT DATE_ADDDAYSTOTIMESTAMP(1, X'00')",
                at + "Invalid argument types for function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(1))"},
            {"SELECT DATE_ADDHOURSTOTIME(1, " + DATE + ")",
                at + "Invalid argument types for function 'DATE_ADDHOURSTOTIME': (NUMBER(1,0), DATE)"},
            {"SELECT DATE_ADDDAYSTODATE(1, TO_TIME('10:00:00'))",
                at + "Invalid argument types for function 'DATE_ADDDAYSTODATE': (NUMBER(1,0), TIME(9))"},
            {"SELECT DATE_DIFFDATEINDAYS(1, TO_DATE('2020-01-01'))",
                at + "Invalid argument types for function 'DATE_DIFFDATEINDAYS': (NUMBER(1,0), DATE)"},
            {"SELECT DATE_DIFFTIMEINHOURS(TO_DATE('2020-01-01'), TO_TIME('05:00:00'))",
                at + "Invalid argument types for function 'DATE_DIFFTIMEINHOURS': (DATE, TIME(9))"},
            {"SELECT DATE_DIFFDATEINDAYS(TO_TIME('01:00:00'), TO_TIME('02:00:00'))",
                at + "Invalid argument types for function 'DATE_DIFFDATEINDAYS': (TIME(9), TIME(9))"},
            {"SELECT DATE_DIFFTIMESTAMPINDAYS(X'00', TO_DATE('2020-01-01'))",
                at + "Invalid argument types for function 'DATE_DIFFTIMESTAMPINDAYS': (BINARY(1), DATE)"},
            {"SELECT DATE_ADDDAYSTOTIMESTAMP(1, TO_TIME('10:00:00'))",
                "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT DATE_DIFFTIMESTAMPINDAYS(TO_TIME('01:00:00'), TO_TIME('02:00:00'))",
                "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT DATE_ADDDAYSTODATE(1)",
                at + "not enough arguments for function [DATE_ADDDAYSTODATE(1)], expected 2, got 1"},
            {"SELECT DATE_ADDDAYSTODATE()",
                at + "not enough arguments for function [DATE_ADDDAYSTODATE()], expected 2, got 0"},
            {"SELECT DATE_ADDDAYSTODATE(1, " + DATE + ", 3)", at + "too many arguments for function"
                + " [DATE_ADDDAYSTODATE(1, CAST('2020-01-31' AS DATE), 3)] expected 2, got 3"},
            {"SELECT DATE_DIFFDATEINDAYS(TO_DATE('2020-01-01'))", at + "not enough arguments for function"
                + " [DATE_DIFFDATEINDAYS(CAST('2020-01-01' AS DATE))], expected 2, got 1"},
            {"SELECT DATE_ADDDAYSTODATE(1, " + DATE + ") OVER ()",
                "SQL compilation error:\nInvalid function type [DATE_ADDDAYSTODATE] for window function."},
            {"SELECT DATE_DIFFDATEINDAYS(a => TO_DATE('2020-01-02'), b => TO_DATE('2020-01-01'))",
                at + "function DATE_DIFFDATEINDAYS does not support named arguments"},
            {"SELECT DATE_ADDDAYSTOTIME(1, TO_TIME('10:00:00'))", "SQL compilation error:\nUnknown function DATE_ADDDAYSTOTIME."},
            {"SELECT DATE_ADDMILLISTODATE(1, " + DATE + ")", "SQL compilation error:\nUnknown function DATE_ADDMILLISTODATE."},
            {"SELECT DATE_DIFFDATEINMILLIS(" + DATE + ", " + DATE + ")",
                "SQL compilation error:\nUnknown function DATE_DIFFDATEINMILLIS."},
            {"SELECT DATE_DIFFTIMEINDAYS(" + TIME + ", " + TIME + ")",
                "SQL compilation error:\nUnknown function DATE_DIFFTIMEINDAYS."},
            {"SELECT DATE_ADDDAYSTODATE('x', " + DATE + ")", "Numeric value 'x' is not recognized"},
            {"SELECT DATE_ADDDAYSTODATE(1, 'garbage')", "Date 'garbage' is not recognized"},
            {"SELECT DATE_ADDHOURSTOTIME(1, 'garbage')", "Time 'garbage' is not recognized"},
        };
        for (final String[] cell : cells) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertEquals(cell[1], refused.getMessage(), cell[0]);
        }
    }

    @Test
    public void testVariantValuesMoveToTheKindAsACastMovesThem() {
        assertEquals(Arrays.asList("2020-02-01 | 2020-02-01 | 2020-02-01 | NULL"),
            lines("SELECT TO_VARCHAR(DATE_ADDDAYSTODATE(1, PARSE_JSON('\"2020-01-31\"'))),"
                + " TO_VARCHAR(DATE_ADDDAYSTODATE(1, PARSE_JSON('\"2020-01-31 10:00:00\"'))),"
                + " TO_VARCHAR(DATE_ADDDAYSTODATE(1, TO_VARIANT(TO_DATE('2020-01-31')))),"
                + " DATE_ADDDAYSTODATE(1, PARSE_JSON('null'))"));
        assertEquals(Arrays.asList("11:00:00 | 2020-02-01 10:00:00.000 | 1"),
            lines("SELECT TO_VARCHAR(DATE_ADDHOURSTOTIME(1, PARSE_JSON('\"10:00:00\"'))),"
                + " TO_VARCHAR(DATE_ADDDAYSTOTIMESTAMP(1, PARSE_JSON('\"2020-01-31 10:00:00\"'))),"
                + " DATE_DIFFDATEINDAYS(PARSE_JSON('\"2020-01-01 23:00:00\"'), TO_DATE('2020-01-02'))"));
        final String[][] cells = {
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('5'))", "5 to DATE"},
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('5.5'))", "5.5 to DATE"},
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('true'))", "true to DATE"},
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('\"abc\"'))", "\"abc\" to DATE"},
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('[1]'))", "[1] to DATE"},
            {"DATE_ADDDAYSTODATE(1, PARSE_JSON('{\"a\":1}'))", "{\"a\":1} to DATE"},
            {"DATE_ADDDAYSTODATE(1, TO_VARIANT(TO_TIMESTAMP_NTZ('2020-01-31 10:00:00')))",
                "\"2020-01-31 10:00:00.000\" to DATE"},
            {"DATE_ADDDAYSTODATE(1, TO_VARIANT(TO_TIME('10:00:00')))", "\"10:00:00\" to DATE"},
            {"DATE_ADDHOURSTOTIME(1, PARSE_JSON('5'))", "5 to TIME"},
            {"DATE_ADDHOURSTOTIME(1, PARSE_JSON('true'))", "true to TIME"},
            {"DATE_ADDHOURSTOTIME(1, TO_VARIANT(TO_TIMESTAMP_NTZ('2020-01-31 10:00:00')))",
                "\"2020-01-31 10:00:00.000\" to TIME"},
            {"DATE_ADDDAYSTOTIMESTAMP(1, PARSE_JSON('true'))", "true to TIMESTAMP_NTZ"},
            {"DATE_ADDDAYSTOTIMESTAMP(1, PARSE_JSON('\"abc\"'))", "\"abc\" to TIMESTAMP_NTZ"},
            {"DATE_ADDDAYSTOTIMESTAMP(1, TO_VARIANT(TO_DATE('2020-01-31')))", "\"2020-01-31\" to TIMESTAMP_NTZ"},
            {"DATE_DIFFDATEINDAYS(PARSE_JSON('5'), TO_DATE('2020-01-01'))", "5 to DATE"},
            {"DATE_DIFFDATEINDAYS(PARSE_JSON('true'), TO_DATE('2020-01-01'))", "true to DATE"},
            {"DATE_DIFFTIMEINHOURS(PARSE_JSON('5'), TO_TIME('10:00:00'))", "5 to TIME"},
        };
        for (final String[] cell : cells) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + cell[0]);
                }
            }, cell[0]);
            assertEquals("Failed to cast variant value " + cell[1], refused.getMessage(), cell[0]);
        }
    }

    @Test
    public void testDatediffReadsATextBesideADateAsADate() {
        assertEquals(Arrays.asList("24 | 24 | 0 | 1 | 24 | 24"),
            lines("SELECT DATEDIFF(hour, TO_DATE('2020-01-01'), '2020-01-02 10:00:00'),"
                + " DATEDIFF(hour, '2020-01-01 23:00:00', TO_DATE('2020-01-02')),"
                + " DATEDIFF(minute, TO_DATE('2020-01-01'), '2020-01-01 10:30:00'),"
                + " DATEDIFF(day, TO_DATE('2020-01-01'), '2020-01-02 23:00:00'),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), '2020-01-02'),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), '2020-01-02 10:00:00 +05:00')"));
        assertEquals(Arrays.asList("24 | 1440 | 24 | 24 | 24"),
            lines("SELECT TIMEDIFF(hour, TO_DATE('2020-01-01'), '2020-01-02 10:00:00'),"
                + " TIMESTAMPDIFF(minute, '2020-01-01 10:30:00', TO_DATE('2020-01-02')),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), '1577923200'),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), x),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), PARSE_JSON('\"2020-01-02 10:00:00\"'))"
                + " FROM (SELECT '2020-01-02 10:00:00' AS x)"));
        for (final String text : Arrays.asList("abc", "10:00:00")) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT DATEDIFF(hour, TO_DATE('2020-01-01'), '" + text + "')");
                }
            }, text);
            assertEquals("Date '" + text + "' is not recognized", refused.getMessage(), text);
        }
    }

    @Test
    public void testShowFunctionsListsNone() {
        assertEquals(0, engine.executeQuery("SHOW FUNCTIONS LIKE 'DATE_ADD%'").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW FUNCTIONS LIKE 'DATE_DIFF%'").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW FUNCTIONS LIKE 'DATEADD'").getRowCount());
    }

    @Test
    public void testDatediffWidthFollowsThePlannedKind() {
        assertEquals(Arrays.asList("NUMBER(18,0)[SB8] | NUMBER(18,0)[SB8] | NUMBER(38,0)[SB16]"),
            lines("SELECT SYSTEM$TYPEOF(DATEDIFF(hour, TO_DATE('2020-01-01'), TO_DATE('2020-01-02'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(millisecond, TO_DATE('2020-01-01'), TO_DATE('2020-01-02'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(nanosecond, TO_DATE('2020-01-01'), TO_DATE('2020-01-02')))"));
        assertEquals(Arrays.asList("NUMBER(9,0)[SB4] | NUMBER(9,0)[SB4] | 34"),
            lines("SELECT SYSTEM$TYPEOF(DATEDIFF(day, TO_DATE('2020-01-01'), TO_TIMESTAMP_NTZ('2020-01-02 10:00:00'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(hour, TO_DATE('2020-01-01'), TO_TIMESTAMP_NTZ('2020-01-02 10:00:00'))),"
                + " DATEDIFF(hour, TO_DATE('2020-01-01'), TO_TIMESTAMP_NTZ('2020-01-02 10:00:00'))"));
        assertEquals(Arrays.asList("NUMBER(9,0)[SB4] | NUMBER(9,0)[SB4] | NUMBER(18,0)[SB8]"),
            lines("SELECT SYSTEM$TYPEOF(DATEDIFF(hour, TO_TIME('01:00:00'), TO_TIME('02:00:00'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(millisecond, TO_TIME('01:00:00'), TO_TIME('02:00:00'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(microsecond, TO_TIME('01:00:00'), TO_TIME('02:00:00')))"));
        assertEquals(Arrays.asList("NUMBER(18,0)[SB8] | NUMBER(9,0)[SB4] | NUMBER(18,0)[SB8]"),
            lines("SELECT SYSTEM$TYPEOF(DATEDIFF(minute, TO_DATE('2020-01-01'), TO_DATE('2020-01-02'))),"
                + " SYSTEM$TYPEOF(DATEDIFF(day, '2020-01-01', '2020-01-02')),"
                + " SYSTEM$TYPEOF(DATEDIFF(hour, '2020-01-01', TO_DATE('2020-01-02')))"));
    }

    /** A query's rows, each cell as text, joined by {@code " | "}. */
    private List<String> lines(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                final Object value = row.getValue(i);
                if (value == null) {
                    line.append("NULL");
                } else if (value instanceof Number) {
                    line.append(new BigDecimal(value.toString()).stripTrailingZeros().toPlainString());
                } else {
                    line.append(value);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
