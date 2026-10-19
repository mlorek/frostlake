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

import org.junit.jupiter.api.Test;

/**
 * The value a date function reads, judged while the statement compiles. EXTRACT, DATE_PART and the functions
 * named after one part refuse a NUMBER, a FLOAT, a BOOLEAN, an ARRAY, an OBJECT, a BINARY, a text and a
 * VARIANT in one sentence that names EXTRACT and the value's type, ahead of the part's own name; DATE_TRUNC,
 * LAST_DAY and TIME_SLICE say the same in their own names. DAYNAME, MONTHNAME, NEXT_DAY and PREVIOUS_DAY
 * list their argument types, MONTHS_BETWEEN and ADD_MONTHS those of the difference or the shift they are
 * planned as. A part the value's kind lacks — a year of a TIME, an hour of a DATE — is refused in the
 * part's name, the function naming itself. The refusals hold over no rows, in a branch never reached and in
 * a view's body. HOUR, MINUTE and SECOND read a TIME, a weekday is named by its first two letters, and
 * TRUNC with a date part truncates a zoned timestamp as DATE_TRUNC does. Every answer is live-verified.
 */
public class DatePartOperandTest extends MeasuredCellsTestSupport {

    @Test
    public void aValueThatIsNoDateIsRefusedByItsType() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT EXTRACT(year FROM 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DATE_PART(year, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DATE_PART('year', 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAR(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT HOUR(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DAYOFWEEK(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT EXTRACT(epoch_second FROM 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT EXTRACT(day FROM a) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT DATE_PART(day, a) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT DAY(a) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(2,1) argument type",
            "SELECT EXTRACT(day FROM 2.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT EXTRACT(day FROM TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT EXTRACT(day FROM f) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT (EXTRACT(b FROM 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT EXTRACT(xyz FROM 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DATE_PART(xyz, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT EXTRACT(xyz FROM a) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [b] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(b FROM d) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(1) argument type",
            "SELECT EXTRACT(b FROM 'x') FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT MONTH(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT QUARTER(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT WEEK(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT MINUTE(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT SECOND(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DAYOFYEAR(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT DAYOFMONTH(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT WEEKOFYEAR(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAROFWEEK(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (NUMBER(1,0))",
            "SELECT DAYNAME(2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (NUMBER(1,0))",
            "SELECT MONTHNAME(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(1,0) argument type",
            "SELECT LAST_DAY(2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(1,0) argument type",
            "SELECT DATE_TRUNC('day', 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(38,0) argument type",
            "SELECT DATE_TRUNC(day, a) FROM n");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFTIMESTAMPINMONTHS': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT MONTHS_BETWEEN(2, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT DATEADD(day, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFTIMESTAMPINDAYS': (NUMBER(1,0), NUMBER(1,0))",
            "SELECT DATEDIFF(day, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (NUMBER(1,0), VARCHAR(2))",
            "SELECT NEXT_DAY(2, 'mo')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (NUMBER(1,0), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(2, 'mo')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support NUMBER(1,0) argument type",
            "SELECT TIME_SLICE(2, 1, 'HOUR')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(2)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(2)");
        assertAnswer(
            "null",
            "SELECT EXTRACT(year FROM NULL)");
        assertAnswer(
            "null",
            "SELECT YEAR(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type",
            "SELECT EXTRACT(year FROM '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(10) argument type",
            "SELECT YEAR('2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT EXTRACT(year FROM PARSE_JSON('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(PARSE_JSON('\"2024-01-02\"'))");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM d) FROM t");
        assertAnswer(
            "2024",
            "SELECT YEAR(d) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [hour] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(hour FROM d) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "SELECT HOUR(d) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [year] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(year FROM '03:04:05'::TIME)");
        assertAnswer(
            "3",
            "SELECT HOUR('03:04:05'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT EXTRACT(year FROM ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(67108864) argument type",
            "SELECT EXTRACT(year FROM TO_BINARY('ab'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT YEAR(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT YEAR(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(2,1) argument type",
            "SELECT DATE_PART(year, 2.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT EXTRACT(year FROM 2::VARIANT)");
    }

    @Test
    public void thePartReadersTruncationsAndSlicesRefuseTheSameFamilies() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, dc NUMBER(10,2), f FLOAT, bo BOOLEAN, s VARCHAR(5), o OBJECT, ar "
            + "ARRAY, bi BINARY, v VARIANT, tm TIME, d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, lz "
            + "TIMESTAMP_LTZ, sv VARCHAR, b8 BINARY(8), t9 TIME(3), dbl DOUBLE, n1 NUMBER(5,0))");
        engine.execute("INSERT INTO tt SELECT 86400, 1.25, 1.5, TRUE, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), "
            + "ARRAY_CONSTRUCT('2024-01-02'), TO_BINARY('ab', 'UTF-8'), PARSE_JSON('{\"x\": \"2024-01-02\"}'), "
            + "'03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, '2024-01-02 03:04:05 "
            + "+02:00'::TIMESTAMP_TZ, '2024-01-02 03:04:05'::TIMESTAMP_LTZ, '2024-01-02', TO_BINARY('ab', "
            + "'UTF-8'), '03:04:05.123'::TIME(3), 2.5, 7");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT EXTRACT(year FROM a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT EXTRACT(year FROM dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT EXTRACT(year FROM f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT EXTRACT(year FROM bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT EXTRACT(year FROM s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT EXTRACT(year FROM o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT EXTRACT(year FROM ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT EXTRACT(year FROM bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT EXTRACT(year FROM v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [year] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(year FROM tm) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM d) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM ts) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM tz) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT EXTRACT(year FROM sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT EXTRACT(year FROM b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [year] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(year FROM t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT EXTRACT(year FROM dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT EXTRACT(year FROM n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT EXTRACT(hour FROM a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT EXTRACT(hour FROM dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT EXTRACT(hour FROM f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT EXTRACT(hour FROM bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT EXTRACT(hour FROM s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT EXTRACT(hour FROM o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT EXTRACT(hour FROM ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT EXTRACT(hour FROM bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT EXTRACT(hour FROM v) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [hour] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(hour FROM d) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM ts) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM tz) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT EXTRACT(hour FROM sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT EXTRACT(hour FROM b8) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT EXTRACT(hour FROM dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT EXTRACT(hour FROM n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT YEAR(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT YEAR(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT YEAR(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT YEAR(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT YEAR(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT YEAR(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT YEAR(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'YEAR date/time part'",
            "SELECT YEAR(tm) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(d) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(ts) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(tz) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT YEAR(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT YEAR(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'YEAR date/time part'",
            "SELECT YEAR(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT YEAR(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT YEAR(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT HOUR(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT HOUR(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT HOUR(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT HOUR(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT HOUR(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT HOUR(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT HOUR(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT HOUR(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT HOUR(v) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "SELECT HOUR(d) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(ts) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(tz) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT HOUR(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT HOUR(b8) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT HOUR(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT HOUR(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT DATE_PART(epoch_second, a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT DATE_PART(epoch_second, dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT DATE_PART(epoch_second, f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT DATE_PART(epoch_second, bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT DATE_PART(epoch_second, s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT DATE_PART(epoch_second, o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT DATE_PART(epoch_second, ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT DATE_PART(epoch_second, bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT DATE_PART(epoch_second, v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [EPOCH_SECOND] for parameter 'DATE_PART date/time "
                + "part'",
            "SELECT DATE_PART(epoch_second, tm) FROM tt");
        assertAnswer(
            "1704153600",
            "SELECT DATE_PART(epoch_second, d) FROM tt");
        assertAnswer(
            "1704164645",
            "SELECT DATE_PART(epoch_second, ts) FROM tt");
        assertAnswer(
            "1704157445",
            "SELECT DATE_PART(epoch_second, tz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT DATE_PART(epoch_second, sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT DATE_PART(epoch_second, b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [EPOCH_SECOND] for parameter 'DATE_PART date/time "
                + "part'",
            "SELECT DATE_PART(epoch_second, t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT DATE_PART(epoch_second, dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT DATE_PART(epoch_second, n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(38,0) argument type",
            "SELECT DATE_TRUNC('day', a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(10,2) argument type",
            "SELECT DATE_TRUNC('day', dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support FLOAT argument type",
            "SELECT DATE_TRUNC('day', f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BOOLEAN argument type",
            "SELECT DATE_TRUNC('day', bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(5) argument type",
            "SELECT DATE_TRUNC('day', s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support OBJECT argument type",
            "SELECT DATE_TRUNC('day', o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support ARRAY argument type",
            "SELECT DATE_TRUNC('day', ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BINARY(8388608) argument type",
            "SELECT DATE_TRUNC('day', bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARIANT argument type",
            "SELECT DATE_TRUNC('day', v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC "
                + "and type TIME.",
            "SELECT DATE_TRUNC('day', tm) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT DATE_TRUNC('day', d) FROM tt");
        assertAnswer(
            "2024-01-02T00:00",
            "SELECT DATE_TRUNC('day', ts) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(16777216) argument type",
            "SELECT DATE_TRUNC('day', sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BINARY(8) argument type",
            "SELECT DATE_TRUNC('day', b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [DAY] is not a valid date/time component for function DATE_TRUNC "
                + "and type TIME.",
            "SELECT DATE_TRUNC('day', t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support FLOAT argument type",
            "SELECT DATE_TRUNC('day', dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(5,0) argument type",
            "SELECT DATE_TRUNC('day', n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(38,0) argument type",
            "SELECT DATE_TRUNC('hour', a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(10,2) argument type",
            "SELECT DATE_TRUNC('hour', dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support FLOAT argument type",
            "SELECT DATE_TRUNC('hour', f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BOOLEAN argument type",
            "SELECT DATE_TRUNC('hour', bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(5) argument type",
            "SELECT DATE_TRUNC('hour', s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support OBJECT argument type",
            "SELECT DATE_TRUNC('hour', o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support ARRAY argument type",
            "SELECT DATE_TRUNC('hour', ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BINARY(8388608) argument type",
            "SELECT DATE_TRUNC('hour', bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARIANT argument type",
            "SELECT DATE_TRUNC('hour', v) FROM tt");
        assertAnswer(
            "03:00",
            "SELECT DATE_TRUNC('hour', tm) FROM tt");
        assertAnswer(
            "2024-01-02T03:00",
            "SELECT DATE_TRUNC('hour', ts) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support VARCHAR(16777216) argument type",
            "SELECT DATE_TRUNC('hour', sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support BINARY(8) argument type",
            "SELECT DATE_TRUNC('hour', b8) FROM tt");
        assertAnswer(
            "03:00",
            "SELECT DATE_TRUNC('hour', t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support FLOAT argument type",
            "SELECT DATE_TRUNC('hour', dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function DATE_TRUNC does not support NUMBER(5,0) argument type",
            "SELECT DATE_TRUNC('hour', n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(38,0) argument type",
            "SELECT LAST_DAY(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(10,2) argument type",
            "SELECT LAST_DAY(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support FLOAT argument type",
            "SELECT LAST_DAY(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support BOOLEAN argument type",
            "SELECT LAST_DAY(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support VARCHAR(5) argument type",
            "SELECT LAST_DAY(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support OBJECT argument type",
            "SELECT LAST_DAY(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support ARRAY argument type",
            "SELECT LAST_DAY(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support BINARY(8388608) argument type",
            "SELECT LAST_DAY(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support VARIANT argument type",
            "SELECT LAST_DAY(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support TIME(9) argument type",
            "SELECT LAST_DAY(tm) FROM tt");
        assertAnswer(
            "2024-01-31",
            "SELECT LAST_DAY(d) FROM tt");
        assertAnswer(
            "2024-01-31",
            "SELECT LAST_DAY(ts) FROM tt");
        assertAnswer(
            "2024-01-31",
            "SELECT LAST_DAY(tz) FROM tt");
        assertAnswer(
            "2024-01-31",
            "SELECT LAST_DAY(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support VARCHAR(16777216) argument type",
            "SELECT LAST_DAY(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support BINARY(8) argument type",
            "SELECT LAST_DAY(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support TIME(3) argument type",
            "SELECT LAST_DAY(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support FLOAT argument type",
            "SELECT LAST_DAY(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(5,0) argument type",
            "SELECT LAST_DAY(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support NUMBER(38,0) argument type",
            "SELECT TIME_SLICE(a, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support NUMBER(10,2) argument type",
            "SELECT TIME_SLICE(dc, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support FLOAT argument type",
            "SELECT TIME_SLICE(f, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support BOOLEAN argument type",
            "SELECT TIME_SLICE(bo, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support VARCHAR(5) argument type",
            "SELECT TIME_SLICE(s, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support OBJECT argument type",
            "SELECT TIME_SLICE(o, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support ARRAY argument type",
            "SELECT TIME_SLICE(ar, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support BINARY(8388608) argument type",
            "SELECT TIME_SLICE(bi, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support VARIANT argument type",
            "SELECT TIME_SLICE(v, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIME(9) argument type",
            "SELECT TIME_SLICE(tm, 1, 'HOUR') FROM tt");
        assertAnswer(
            "2024-01-02T03:00",
            "SELECT TIME_SLICE(ts, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_TZ(9) argument type",
            "SELECT TIME_SLICE(tz, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIMESTAMP_LTZ(9) argument type",
            "SELECT TIME_SLICE(lz, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support VARCHAR(16777216) argument type",
            "SELECT TIME_SLICE(sv, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support BINARY(8) argument type",
            "SELECT TIME_SLICE(b8, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support TIME(3) argument type",
            "SELECT TIME_SLICE(t9, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support FLOAT argument type",
            "SELECT TIME_SLICE(dbl, 1, 'HOUR') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support NUMBER(5,0) argument type",
            "SELECT TIME_SLICE(n1, 1, 'HOUR') FROM tt");
    }

    @Test
    public void nameAndDifferenceFunctionsListTheirArgumentTypes() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, dc NUMBER(10,2), f FLOAT, bo BOOLEAN, s VARCHAR(5), o OBJECT, ar "
            + "ARRAY, bi BINARY, v VARIANT, tm TIME, d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, lz "
            + "TIMESTAMP_LTZ, sv VARCHAR, b8 BINARY(8), t9 TIME(3), dbl DOUBLE, n1 NUMBER(5,0))");
        engine.execute("INSERT INTO tt SELECT 86400, 1.25, 1.5, TRUE, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), "
            + "ARRAY_CONSTRUCT('2024-01-02'), TO_BINARY('ab', 'UTF-8'), PARSE_JSON('{\"x\": \"2024-01-02\"}'), "
            + "'03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, '2024-01-02 03:04:05 "
            + "+02:00'::TIMESTAMP_TZ, '2024-01-02 03:04:05'::TIMESTAMP_LTZ, '2024-01-02', TO_BINARY('ab', "
            + "'UTF-8'), '03:04:05.123'::TIME(3), 2.5, 7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (NUMBER(38,0))",
            "SELECT DAYNAME(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (NUMBER(10,2))",
            "SELECT DAYNAME(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (FLOAT)",
            "SELECT DAYNAME(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (BOOLEAN)",
            "SELECT DAYNAME(bo) FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT DAYNAME(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (OBJECT)",
            "SELECT DAYNAME(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (ARRAY)",
            "SELECT DAYNAME(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (BINARY(8388608))",
            "SELECT DAYNAME(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (TIME(9))",
            "SELECT DAYNAME(tm) FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME(d) FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME(ts) FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME(tz) FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME(lz) FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (BINARY(8))",
            "SELECT DAYNAME(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (TIME(3))",
            "SELECT DAYNAME(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (FLOAT)",
            "SELECT DAYNAME(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DAYNAME': (NUMBER(5,0))",
            "SELECT DAYNAME(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (NUMBER(38,0))",
            "SELECT MONTHNAME(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (NUMBER(10,2))",
            "SELECT MONTHNAME(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (FLOAT)",
            "SELECT MONTHNAME(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (BOOLEAN)",
            "SELECT MONTHNAME(bo) FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT MONTHNAME(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (OBJECT)",
            "SELECT MONTHNAME(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (ARRAY)",
            "SELECT MONTHNAME(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (BINARY(8388608))",
            "SELECT MONTHNAME(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (TIME(9))",
            "SELECT MONTHNAME(tm) FROM tt");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME(d) FROM tt");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME(ts) FROM tt");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME(tz) FROM tt");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME(lz) FROM tt");
        assertAnswer(
            "Jan",
            "SELECT MONTHNAME(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (BINARY(8))",
            "SELECT MONTHNAME(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (TIME(3))",
            "SELECT MONTHNAME(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (FLOAT)",
            "SELECT MONTHNAME(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MONTHNAME': (NUMBER(5,0))",
            "SELECT MONTHNAME(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (NUMBER(38,0), VARCHAR(2))",
            "SELECT NEXT_DAY(a, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (NUMBER(10,2), VARCHAR(2))",
            "SELECT NEXT_DAY(dc, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (FLOAT, VARCHAR(2))",
            "SELECT NEXT_DAY(f, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (BOOLEAN, VARCHAR(2))",
            "SELECT NEXT_DAY(bo, 'mo') FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT NEXT_DAY(s, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (OBJECT, VARCHAR(2))",
            "SELECT NEXT_DAY(o, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (ARRAY, VARCHAR(2))",
            "SELECT NEXT_DAY(ar, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (BINARY(8388608), VARCHAR(2))",
            "SELECT NEXT_DAY(bi, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (TIME(9), VARCHAR(2))",
            "SELECT NEXT_DAY(tm, 'mo') FROM tt");
        assertAnswer(
            "2024-01-08",
            "SELECT NEXT_DAY(d, 'mo') FROM tt");
        assertAnswer(
            "2024-01-08",
            "SELECT NEXT_DAY(ts, 'mo') FROM tt");
        assertAnswer(
            "2024-01-08",
            "SELECT NEXT_DAY(tz, 'mo') FROM tt");
        assertAnswer(
            "2024-01-08",
            "SELECT NEXT_DAY(lz, 'mo') FROM tt");
        assertAnswer(
            "2024-01-08",
            "SELECT NEXT_DAY(sv, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (BINARY(8), VARCHAR(2))",
            "SELECT NEXT_DAY(b8, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (TIME(3), VARCHAR(2))",
            "SELECT NEXT_DAY(t9, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (FLOAT, VARCHAR(2))",
            "SELECT NEXT_DAY(dbl, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEXT_DAY': (NUMBER(5,0), VARCHAR(2))",
            "SELECT NEXT_DAY(n1, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (NUMBER(38,0), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(a, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (NUMBER(10,2), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(dc, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (FLOAT, VARCHAR(2))",
            "SELECT PREVIOUS_DAY(f, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (BOOLEAN, VARCHAR(2))",
            "SELECT PREVIOUS_DAY(bo, 'mo') FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT PREVIOUS_DAY(s, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (OBJECT, VARCHAR(2))",
            "SELECT PREVIOUS_DAY(o, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (ARRAY, VARCHAR(2))",
            "SELECT PREVIOUS_DAY(ar, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (BINARY(8388608), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(bi, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (TIME(9), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(tm, 'mo') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT PREVIOUS_DAY(d, 'mo') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT PREVIOUS_DAY(ts, 'mo') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT PREVIOUS_DAY(tz, 'mo') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT PREVIOUS_DAY(lz, 'mo') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT PREVIOUS_DAY(sv, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (BINARY(8), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(b8, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (TIME(3), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(t9, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (FLOAT, VARCHAR(2))",
            "SELECT PREVIOUS_DAY(dbl, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PREVIOUS_DAY': (NUMBER(5,0), VARCHAR(2))",
            "SELECT PREVIOUS_DAY(n1, 'mo') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, NUMBER(38,0))",
            "SELECT MONTHS_BETWEEN(a, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, NUMBER(10,2))",
            "SELECT MONTHS_BETWEEN(dc, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, FLOAT)",
            "SELECT MONTHS_BETWEEN(f, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, BOOLEAN)",
            "SELECT MONTHS_BETWEEN(bo, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT MONTHS_BETWEEN(s, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, OBJECT)",
            "SELECT MONTHS_BETWEEN(o, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, ARRAY)",
            "SELECT MONTHS_BETWEEN(ar, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, BINARY(8388608))",
            "SELECT MONTHS_BETWEEN(bi, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT MONTHS_BETWEEN(v, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(5), DATE, TIME(9))",
            "SELECT MONTHS_BETWEEN(tm, d) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(d, d) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(ts, d) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(tz, d) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(lz, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT MONTHS_BETWEEN(sv, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, BINARY(8))",
            "SELECT MONTHS_BETWEEN(b8, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(5), DATE, TIME(3))",
            "SELECT MONTHS_BETWEEN(t9, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, FLOAT)",
            "SELECT MONTHS_BETWEEN(dbl, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (DATE, NUMBER(5,0))",
            "SELECT MONTHS_BETWEEN(n1, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (NUMBER(38,0), DATE)",
            "SELECT MONTHS_BETWEEN(d, a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (NUMBER(10,2), DATE)",
            "SELECT MONTHS_BETWEEN(d, dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (FLOAT, DATE)",
            "SELECT MONTHS_BETWEEN(d, f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (BOOLEAN, DATE)",
            "SELECT MONTHS_BETWEEN(d, bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT MONTHS_BETWEEN(d, s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (OBJECT, DATE)",
            "SELECT MONTHS_BETWEEN(d, o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (ARRAY, DATE)",
            "SELECT MONTHS_BETWEEN(d, ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (BINARY(8388608), DATE)",
            "SELECT MONTHS_BETWEEN(d, bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT MONTHS_BETWEEN(d, v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(5), TIME(9), DATE)",
            "SELECT MONTHS_BETWEEN(d, tm) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(d, d) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(d, ts) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(d, tz) FROM tt");
        assertAnswer(
            "0.000000",
            "SELECT MONTHS_BETWEEN(d, lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT MONTHS_BETWEEN(d, sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (BINARY(8), DATE)",
            "SELECT MONTHS_BETWEEN(d, b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(5), TIME(3), DATE)",
            "SELECT MONTHS_BETWEEN(d, t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (FLOAT, DATE)",
            "SELECT MONTHS_BETWEEN(d, dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINMONTHS': (NUMBER(5,0), DATE)",
            "SELECT MONTHS_BETWEEN(d, n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), NUMBER(38,0))",
            "SELECT ADD_MONTHS(a, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), NUMBER(10,2))",
            "SELECT ADD_MONTHS(dc, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), FLOAT)",
            "SELECT ADD_MONTHS(f, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)",
            "SELECT ADD_MONTHS(bo, 1) FROM tt");
        assertAnswer(
            "REFUSED Timestamp 'abc' is not recognized",
            "SELECT ADD_MONTHS(s, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), OBJECT)",
            "SELECT ADD_MONTHS(o, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), ARRAY)",
            "SELECT ADD_MONTHS(ar, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), BINARY(8388608))",
            "SELECT ADD_MONTHS(bi, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]",
            "SELECT ADD_MONTHS(tm, 1) FROM tt");
        assertAnswer(
            "2024-02-02",
            "SELECT ADD_MONTHS(d, 1) FROM tt");
        assertAnswer(
            "2024-02-02T00:00",
            "SELECT ADD_MONTHS(sv, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), BINARY(8))",
            "SELECT ADD_MONTHS(b8, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [TIME(3)] and [TIMESTAMP_NTZ(9)]",
            "SELECT ADD_MONTHS(t9, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), FLOAT)",
            "SELECT ADD_MONTHS(dbl, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDMONTHSEOMTOTIMESTAMP': (NUMBER(1,0), NUMBER(5,0))",
            "SELECT ADD_MONTHS(n1, 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(38,0))",
            "SELECT DATEADD(day, 1, a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(10,2))",
            "SELECT DATEADD(day, 1, dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), FLOAT)",
            "SELECT DATEADD(day, 1, f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BOOLEAN)",
            "SELECT DATEADD(day, 1, bo) FROM tt");
        assertAnswer(
            "REFUSED Timestamp 'abc' is not recognized",
            "SELECT DATEADD(day, 1, s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), OBJECT)",
            "SELECT DATEADD(day, 1, o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), ARRAY)",
            "SELECT DATEADD(day, 1, ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(8388608))",
            "SELECT DATEADD(day, 1, bi) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to TIMESTAMP_NTZ",
            "SELECT DATEADD(day, 1, v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD "
                + "and type TIME(9).",
            "SELECT DATEADD(day, 1, tm) FROM tt");
        assertAnswer(
            "2024-01-03",
            "SELECT DATEADD(day, 1, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(8))",
            "SELECT DATEADD(day, 1, b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function DATEADD "
                + "and type TIME(3).",
            "SELECT DATEADD(day, 1, t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), FLOAT)",
            "SELECT DATEADD(day, 1, dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(5,0))",
            "SELECT DATEADD(day, 1, n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (NUMBER(38,0), DATE)",
            "SELECT DATEDIFF(day, a, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (NUMBER(10,2), DATE)",
            "SELECT DATEDIFF(day, dc, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (FLOAT, DATE)",
            "SELECT DATEDIFF(day, f, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (BOOLEAN, DATE)",
            "SELECT DATEDIFF(day, bo, d) FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT DATEDIFF(day, s, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (OBJECT, DATE)",
            "SELECT DATEDIFF(day, o, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (ARRAY, DATE)",
            "SELECT DATEDIFF(day, ar, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (BINARY(8388608), DATE)",
            "SELECT DATEDIFF(day, bi, d) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to DATE",
            "SELECT DATEDIFF(day, v, d) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(day, d, d) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(day, ts, d) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(day, tz, d) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(day, lz, d) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(day, sv, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (BINARY(8), DATE)",
            "SELECT DATEDIFF(day, b8, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (FLOAT, DATE)",
            "SELECT DATEDIFF(day, dbl, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINDAYS': (NUMBER(5,0), DATE)",
            "SELECT DATEDIFF(day, n1, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, NUMBER(38,0))",
            "SELECT DATEDIFF(hour, d, a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, NUMBER(10,2))",
            "SELECT DATEDIFF(hour, d, dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, FLOAT)",
            "SELECT DATEDIFF(hour, d, f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, BOOLEAN)",
            "SELECT DATEDIFF(hour, d, bo) FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT DATEDIFF(hour, d, s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, OBJECT)",
            "SELECT DATEDIFF(hour, d, o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, ARRAY)",
            "SELECT DATEDIFF(hour, d, ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, BINARY(8388608))",
            "SELECT DATEDIFF(hour, d, bi) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to DATE",
            "SELECT DATEDIFF(hour, d, v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(4), DATE, TIME(9))",
            "SELECT DATEDIFF(hour, d, tm) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(hour, d, d) FROM tt");
        assertAnswer(
            "3",
            "SELECT DATEDIFF(hour, d, ts) FROM tt");
        assertAnswer(
            "3",
            "SELECT DATEDIFF(hour, d, lz) FROM tt");
        assertAnswer(
            "0",
            "SELECT DATEDIFF(hour, d, sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, BINARY(8))",
            "SELECT DATEDIFF(hour, d, b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATEDIFF': (VARCHAR(4), DATE, TIME(3))",
            "SELECT DATEDIFF(hour, d, t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, FLOAT)",
            "SELECT DATEDIFF(hour, d, dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_DIFFDATEINHOURS': (DATE, NUMBER(5,0))",
            "SELECT DATEDIFF(hour, d, n1) FROM tt");
    }

    @Test
    public void theOtherDateFunctionsOverEachFamily() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, dc NUMBER(10,2), f FLOAT, bo BOOLEAN, s VARCHAR(5), o OBJECT, ar "
            + "ARRAY, bi BINARY, v VARIANT, tm TIME, d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, lz "
            + "TIMESTAMP_LTZ, sv VARCHAR, b8 BINARY(8), t9 TIME(3), dbl DOUBLE, n1 NUMBER(5,0))");
        engine.execute("INSERT INTO tt SELECT 86400, 1.25, 1.5, TRUE, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), "
            + "ARRAY_CONSTRUCT('2024-01-02'), TO_BINARY('ab', 'UTF-8'), PARSE_JSON('{\"x\": \"2024-01-02\"}'), "
            + "'03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, '2024-01-02 03:04:05 "
            + "+02:00'::TIMESTAMP_TZ, '2024-01-02 03:04:05'::TIMESTAMP_LTZ, '2024-01-02', TO_BINARY('ab', "
            + "'UTF-8'), '03:04:05.123'::TIME(3), 2.5, 7");
        assertAnswer(
            "REFUSED Numeric value 'month' is not recognized",
            "SELECT TRUNC(a, 'month') FROM tt");
        assertAnswer(
            "REFUSED Numeric value 'month' is not recognized",
            "SELECT TRUNC(dc, 'month') FROM tt");
        assertAnswer(
            "REFUSED Numeric value 'month' is not recognized",
            "SELECT TRUNC(f, 'month') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [MONTH] is not a valid date/time component for function TRUNC and "
                + "type TIME.",
            "SELECT TRUNC(tm, 'month') FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT TRUNC(d, 'month') FROM tt");
        assertAnswer(
            "2024-01-01T00:00",
            "SELECT TRUNC(ts, 'month') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [MONTH] is not a valid date/time component for function TRUNC and "
                + "type TIME.",
            "SELECT TRUNC(t9, 'month') FROM tt");
        assertAnswer(
            "REFUSED Numeric value 'month' is not recognized",
            "SELECT TRUNC(dbl, 'month') FROM tt");
        assertAnswer(
            "REFUSED Numeric value 'month' is not recognized",
            "SELECT TRUNC(n1, 'month') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT WEEKISO(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT WEEKISO(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT WEEKISO(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT WEEKISO(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT WEEKISO(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT WEEKISO(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT WEEKISO(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT WEEKISO(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT WEEKISO(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [WEEKISO] for parameter 'WEEKISO date/time part'",
            "SELECT WEEKISO(tm) FROM tt");
        assertAnswer(
            "1",
            "SELECT WEEKISO(d) FROM tt");
        assertAnswer(
            "1",
            "SELECT WEEKISO(ts) FROM tt");
        assertAnswer(
            "1",
            "SELECT WEEKISO(tz) FROM tt");
        assertAnswer(
            "1",
            "SELECT WEEKISO(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT WEEKISO(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT WEEKISO(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [WEEKISO] for parameter 'WEEKISO date/time part'",
            "SELECT WEEKISO(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT WEEKISO(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT WEEKISO(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(ts) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(tz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NANOSECOND.",
            "SELECT NANOSECOND(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT DAYOFWEEKISO(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT DAYOFWEEKISO(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT DAYOFWEEKISO(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT DAYOFWEEKISO(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT DAYOFWEEKISO(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT DAYOFWEEKISO(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT DAYOFWEEKISO(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT DAYOFWEEKISO(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT DAYOFWEEKISO(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAYOFWEEKISO] for parameter 'DAYOFWEEKISO "
                + "date/time part'",
            "SELECT DAYOFWEEKISO(tm) FROM tt");
        assertAnswer(
            "2",
            "SELECT DAYOFWEEKISO(d) FROM tt");
        assertAnswer(
            "2",
            "SELECT DAYOFWEEKISO(ts) FROM tt");
        assertAnswer(
            "2",
            "SELECT DAYOFWEEKISO(tz) FROM tt");
        assertAnswer(
            "2",
            "SELECT DAYOFWEEKISO(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT DAYOFWEEKISO(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT DAYOFWEEKISO(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAYOFWEEKISO] for parameter 'DAYOFWEEKISO "
                + "date/time part'",
            "SELECT DAYOFWEEKISO(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT DAYOFWEEKISO(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT DAYOFWEEKISO(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAROFWEEKISO(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT YEAROFWEEKISO(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT YEAROFWEEKISO(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BOOLEAN argument type",
            "SELECT YEAROFWEEKISO(bo) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT YEAROFWEEKISO(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT YEAROFWEEKISO(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT YEAROFWEEKISO(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8388608) argument type",
            "SELECT YEAROFWEEKISO(bi) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAROFWEEKISO(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAROFWEEKISO] for parameter 'YEAROFWEEKISO "
                + "date/time part'",
            "SELECT YEAROFWEEKISO(tm) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAROFWEEKISO(d) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAROFWEEKISO(ts) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAROFWEEKISO(tz) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAROFWEEKISO(lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(16777216) argument type",
            "SELECT YEAROFWEEKISO(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(8) argument type",
            "SELECT YEAROFWEEKISO(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAROFWEEKISO] for parameter 'YEAROFWEEKISO "
                + "date/time part'",
            "SELECT YEAROFWEEKISO(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support FLOAT argument type",
            "SELECT YEAROFWEEKISO(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(5,0) argument type",
            "SELECT YEAROFWEEKISO(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.A)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.DC)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.F)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.BO)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(bo) FROM tt");
        assertAnswer(
            "REFUSED Date 'abc' is not recognized",
            "SELECT TO_DATE(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.O)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.AR)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.BI)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(bi) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to DATE",
            "SELECT TO_DATE(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.TM)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(tm) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT TO_DATE(d) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT TO_DATE(ts) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT TO_DATE(tz) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT TO_DATE(lz) FROM tt");
        assertAnswer(
            "2024-01-02",
            "SELECT TO_DATE(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.B8)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.T9)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.DBL)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(TT.N1)] for parameter 'TO_DATE'",
            "SELECT TO_DATE(n1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.A)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.DC)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(dc) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.F)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.BO)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(bo) FROM tt");
        assertAnswer(
            "REFUSED Time 'abc' is not recognized",
            "SELECT TO_TIME(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.O)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.AR)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.BI)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(bi) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to TIME",
            "SELECT TO_TIME(v) FROM tt");
        assertAnswer(
            "03:04:05",
            "SELECT TO_TIME(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.D)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(d) FROM tt");
        assertAnswer(
            "03:04:05",
            "SELECT TO_TIME(ts) FROM tt");
        assertAnswer(
            "REFUSED Time '2024-01-02' is not recognized",
            "SELECT TO_TIME(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.B8)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.DBL)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(dbl) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(TT.N1)] for parameter 'TO_TIME'",
            "SELECT TO_TIME(n1) FROM tt");
        assertAnswer(
            "1970-01-02T00:00",
            "SELECT TO_TIMESTAMP(a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.F)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(f) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.BO)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(bo) FROM tt");
        assertAnswer(
            "REFUSED Timestamp 'abc' is not recognized",
            "SELECT TO_TIMESTAMP(s) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.O)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(o) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.AR)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(ar) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.BI)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(bi) FROM tt");
        assertAnswer(
            "REFUSED Failed to cast variant value {\"x\":\"2024-01-02\"} to TIMESTAMP_NTZ",
            "SELECT TO_TIMESTAMP(v) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.TM)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(tm) FROM tt");
        assertAnswer(
            "2024-01-02T00:00",
            "SELECT TO_TIMESTAMP(d) FROM tt");
        assertAnswer(
            "2024-01-02T00:00",
            "SELECT TO_TIMESTAMP(sv) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.B8)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(b8) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.T9)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TT.DBL)] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP(dbl) FROM tt");
    }

    @Test
    public void aPartTheValueLacksAndWhereTheRefusalsHold() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, dc NUMBER(10,2), f FLOAT, bo BOOLEAN, s VARCHAR(5), o OBJECT, ar "
            + "ARRAY, bi BINARY, v VARIANT, tm TIME, d DATE, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, lz "
            + "TIMESTAMP_LTZ, sv VARCHAR, b8 BINARY(8), t9 TIME(3), dbl DOUBLE, n1 NUMBER(5,0))");
        engine.execute("INSERT INTO tt SELECT 86400, 1.25, 1.5, TRUE, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), "
            + "ARRAY_CONSTRUCT('2024-01-02'), TO_BINARY('ab', 'UTF-8'), PARSE_JSON('{\"x\": \"2024-01-02\"}'), "
            + "'03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, '2024-01-02 03:04:05 "
            + "+02:00'::TIMESTAMP_TZ, '2024-01-02 03:04:05'::TIMESTAMP_LTZ, '2024-01-02', TO_BINARY('ab', "
            + "'UTF-8'), '03:04:05.123'::TIME(3), 2.5, 7");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [minute] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(minute FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [second] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(second FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [nanosecond] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(nanosecond FROM d) FROM tt");
        assertAnswer(
            "1704153600",
            "SELECT EXTRACT(epoch_second FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [timezone_hour] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(timezone_hour FROM d) FROM tt");
        assertAnswer(
            "0",
            "SELECT EXTRACT(timezone_hour FROM ts) FROM tt");
        assertAnswer(
            "2",
            "SELECT EXTRACT(timezone_hour FROM tz) FROM tt");
        assertAnswer(
            "0",
            "SELECT EXTRACT(timezone_minute FROM lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [MINUTE] for parameter 'MINUTE date/time part'",
            "SELECT MINUTE(d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [SECOND] for parameter 'SECOND date/time part'",
            "SELECT SECOND(d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(hour, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [hour] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART('hour', d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "SELECT HOUR(CURRENT_DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [HOUR] for parameter 'HOUR date/time part'",
            "SELECT HOUR('2024-01-02'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: [YEAR] is not a valid date/time component for function DATE_TRUNC "
                + "and type TIME.",
            "SELECT DATE_TRUNC(year, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [MONTH] is not a valid date/time component for function "
                + "DATE_TRUNC and type TIME.",
            "SELECT DATE_TRUNC(month, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: [WEEK] is not a valid date/time component for function DATE_TRUNC "
                + "and type TIME.",
            "SELECT DATE_TRUNC(week, tm) FROM tt");
        assertAnswer(
            "03:04",
            "SELECT DATE_TRUNC(minute, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [month] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(month FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [day] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(day FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [dayofweek] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(dayofweek FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [dayofyear] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(dayofyear FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [week] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(week FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [quarter] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(quarter FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [epoch_second] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(epoch_second FROM tm) FROM tt");
        assertAnswer(
            "4",
            "SELECT EXTRACT(minute FROM tm) FROM tt");
        assertAnswer(
            "5",
            "SELECT EXTRACT(second FROM tm) FROM tt");
        assertAnswer(
            "0",
            "SELECT EXTRACT(nanosecond FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [MONTH] for parameter 'MONTH date/time part'",
            "SELECT MONTH(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAY] for parameter 'DAY date/time part'",
            "SELECT DAY(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAYOFWEEK] for parameter 'DAYOFWEEK date/time part'",
            "SELECT DAYOFWEEK(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [QUARTER] for parameter 'QUARTER date/time part'",
            "SELECT QUARTER(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [WEEK] for parameter 'WEEK date/time part'",
            "SELECT WEEK(tm) FROM tt");
        assertAnswer(
            "4",
            "SELECT MINUTE(tm) FROM tt");
        assertAnswer(
            "5",
            "SELECT SECOND(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAYOFYEAR] for parameter 'DAYOFYEAR date/time part'",
            "SELECT DAYOFYEAR(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DAYOFMONTH] for parameter 'DAYOFMONTH date/time part'",
            "SELECT DAYOFMONTH(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [WEEKOFYEAR] for parameter 'WEEKOFYEAR date/time part'",
            "SELECT WEEKOFYEAR(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAROFWEEK] for parameter 'YEAROFWEEK date/time part'",
            "SELECT YEAROFWEEK(tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(year, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [year] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART('year', tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [XYZ] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(xyz, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [xyz] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(xyz FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [xyz] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(xyz FROM ts) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['hour'] is not a valid date/time component for function LAST_DAY.",
            "SELECT LAST_DAY(d, 'hour') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['HOUR'] is not a valid date/time component for function LAST_DAY.",
            "SELECT LAST_DAY(d, hour) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(38,0) argument type",
            "SELECT LAST_DAY(a, 'month') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function LAST_DAY does not support NUMBER(38,0) argument type",
            "SELECT LAST_DAY(a, xyz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['XYZ'] is not a valid date/time component for function DATE_TRUNC.",
            "SELECT DATE_TRUNC(xyz, a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['xyz'] is not a valid date/time component for function DATE_TRUNC.",
            "SELECT DATE_TRUNC('xyz', a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function TIME_SLICE does not support NUMBER(38,0) argument type",
            "SELECT TIME_SLICE(a, 1, 'XYZ') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['XYZ'] is not a valid date/time component for function TIME_SLICE.",
            "SELECT TIME_SLICE(d, 1, 'XYZ') FROM tt");
        assertAnswer(
            "REFUSED Weekday '2' is not recognized",
            "SELECT NEXT_DAY(d, 2) FROM tt");
        assertAnswer(
            "REFUSED Weekday 'zz' is not recognized",
            "SELECT NEXT_DAY(d, 'zz') FROM tt");
        assertAnswer(
            "Tue",
            "SELECT DAYNAME('2024-01-02')");
        assertAnswer(
            "REFUSED Date 'x' is not recognized",
            "SELECT DAYNAME('x')");
        assertAnswer(
            "null",
            "SELECT MONTHNAME(NULL)");
        assertAnswer(
            "null",
            "SELECT DAYNAME(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(NULL::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(TT.A AS DATE)] for parameter 'TO_DATE'",
            "SELECT YEAR(a::DATE) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(TO_VARIANT(d)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(v:x) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(v:x::DATE) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(o:x) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARIANT argument type",
            "SELECT YEAR(ar[0]) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(2,0) argument type",
            "SELECT YEAR(1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(a + 1) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(-a) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(COALESCE(a, 1)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(IFF(bo, a, 1)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(6) argument type",
            "SELECT YEAR(s || 'x') FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(15) argument type",
            "SELECT YEAR(UPPER(s)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAR((SELECT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR((SELECT a FROM tt)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAR(q.c) FROM (SELECT 2 c) q");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(q.c) FROM (SELECT a c FROM tt) q");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(c) FROM (SELECT a + 1 c FROM tt)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(5) argument type",
            "SELECT YEAR(c) FROM (SELECT s c FROM tt)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(18,0) argument type",
            "SELECT YEAR(COUNT(*)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(SUM(a)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(MAX(a)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,6) argument type",
            "SELECT YEAR(AVG(a)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(18,0) argument type",
            "SELECT YEAR(ROW_NUMBER() OVER (ORDER BY a)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(18,0) argument type",
            "SELECT YEAR(LENGTH(s)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(12,0) argument type",
            "SELECT YEAR(123456789012)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(3,0) argument type",
            "SELECT YEAR(1e2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(2,1) argument type",
            "SELECT YEAR(0.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAR(-2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(10,2) argument type",
            "SELECT YEAR(1::NUMBER(10,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(TRY_TO_NUMBER(s)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT YEAR(a) FROM tt WHERE 1 = 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT 1 FROM tt WHERE YEAR(a) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT 1 FROM tt WHERE 1 = 0 AND YEAR(a) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT 1 FROM tt ORDER BY YEAR(a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "SELECT 1 FROM tt GROUP BY YEAR(a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT 1 FROM tt HAVING YEAR(1) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT IFF(TRUE, 1, YEAR(2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT CASE WHEN FALSE THEN YEAR(2) END");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT COALESCE(1, YEAR(2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(1,0) argument type",
            "SELECT YEAR(2) FROM tt LIMIT 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support NUMBER(38,0) argument type",
            "CREATE OR REPLACE VIEW vdp AS SELECT YEAR(a) y FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT YEAR(TO_OBJECT(o)) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support OBJECT argument type",
            "SELECT YEAR(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support ARRAY argument type",
            "SELECT YEAR(TO_ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support BINARY(67108864) argument type",
            "SELECT YEAR('x'::BINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function EXTRACT does not support VARCHAR(8) argument type",
            "SELECT YEAR(HEX_ENCODE('a'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'YEAR date/time part'",
            "SELECT YEAR(CURRENT_TIME)");
        assertAnswer(
            "true",
            "SELECT YEAR(CURRENT_TIMESTAMP) > 2000");
        assertAnswer(
            "true",
            "SELECT YEAR(SYSDATE()) > 2000");
        assertAnswer(
            "2024",
            "SELECT YEAR(tz) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(tz) FROM tt");
        assertAnswer(
            "true",
            "SELECT HOUR(lz) IS NOT NULL FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(ts) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(t9) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [YEAR] for parameter 'YEAR date/time part'",
            "SELECT YEAR(t9) FROM tt");
    }

    @Test
    public void aDateATimeAndATimestampAreRead() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, s VARCHAR(5), o OBJECT, v VARIANT, tm TIME, d DATE, ts "
            + "TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bo BOOLEAN)");
        engine.execute("INSERT INTO tt SELECT 86400, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), PARSE_JSON('{\"x\": "
            + "\"2024-01-02\"}'), '03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, TRUE");
        assertAnswer(
            "2024",
            "SELECT YEAR(d + 1) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(d - 1) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(ts + INTERVAL '1 day') FROM tt");
        assertAnswer(
            "2025",
            "SELECT YEAR(d + INTERVAL '1 year') FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(DATEADD(day, 1, d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(GREATEST(d, d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(LEAST(d, d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(COALESCE(d, CURRENT_DATE)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(MAX(d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(MIN(ts)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(ANY_VALUE(d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(TO_DATE('2024-01-02'))");
        assertAnswer(
            "2024",
            "SELECT YEAR(d::TIMESTAMP) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(IFF(TRUE, d, NULL)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(NVL(d, d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(LAST_DAY(d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(DATE_TRUNC('month', d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(TRUNC(d, 'month')) FROM tt");
        assertAnswer(
            "1970",
            "SELECT YEAR(TO_TIMESTAMP(86400))");
        assertAnswer(
            "2024",
            "SELECT YEAR(DATE_FROM_PARTS(2024, 1, 2))");
        assertAnswer(
            "2024",
            "SELECT YEAR(ADD_MONTHS(d, 1)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(NEXT_DAY(d, 'mo')) FROM tt");
        assertAnswer(
            "null",
            "SELECT YEAR(NULLIF(d, d)) FROM tt");
        assertAnswer(
            "null",
            "SELECT YEAR(LAG(d) OVER (ORDER BY d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(FIRST_VALUE(d) OVER (ORDER BY d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(CASE WHEN bo THEN d ELSE d END) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(DECODE(1, 1, d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(v:x::DATE) FROM tt");
        assertAnswer(
            "null",
            "SELECT YEAR(TRY_TO_DATE(s)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(TRY_TO_DATE('2024-01-02'))");
        assertAnswer(
            "2024",
            "SELECT YEAR(TO_TIMESTAMP_NTZ(d)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(TIMESTAMP_FROM_PARTS(2024, 1, 2, 3, 4, 5))");
        assertAnswer(
            "2024",
            "SELECT YEAR(CONVERT_TIMEZONE('UTC', ts)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(d + a - a) FROM tt");
        assertAnswer(
            "4",
            "SELECT HOUR(ts + INTERVAL '1 hour') FROM tt");
        assertAnswer(
            "4",
            "SELECT HOUR(DATEADD(hour, 1, tm)) FROM tt");
        assertAnswer(
            "3",
            "SELECT HOUR(TIME_FROM_PARTS(3, 4, 5))");
        assertAnswer(
            "3",
            "SELECT HOUR(TO_TIME('03:04:05'))");
        assertAnswer(
            "3",
            "SELECT HOUR(tm::TIME) FROM tt");
        assertAnswer(
            "4",
            "SELECT MINUTE(tz) FROM tt");
        assertAnswer(
            "6",
            "SELECT SECOND(TIMEADD(second, 1, tm)) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(year FROM d + 1) FROM tt");
        assertAnswer(
            "2024",
            "SELECT DATE_PART(year, DATEADD(month, 1, ts)) FROM tt");
        assertAnswer(
            "Wed",
            "SELECT DAYNAME(d + 1) FROM tt");
        assertAnswer(
            "Feb",
            "SELECT MONTHNAME(DATEADD(month, 1, d)) FROM tt");
        assertAnswer(
            "2024-01-31",
            "SELECT LAST_DAY(d + 1) FROM tt");
        assertAnswer(
            "2024-01-01",
            "SELECT DATE_TRUNC('month', d + 1) FROM tt");
        assertAnswer(
            "2024-01-05",
            "SELECT NEXT_DAY(d + 1, 'Fri') FROM tt");
        assertAnswer(
            "2024-01-05",
            "SELECT NEXT_DAY(d, '  friday') FROM tt");
        assertAnswer(
            "2024-01-06",
            "SELECT NEXT_DAY(d, 'Sat x') FROM tt");
        assertAnswer(
            "2023-12-26",
            "SELECT PREVIOUS_DAY(d, 'TU') FROM tt");
        assertAnswer(
            "REFUSED Weekday 's' is not recognized",
            "SELECT NEXT_DAY(d, 's') FROM tt");
        assertAnswer(
            "REFUSED Weekday '' is not recognized",
            "SELECT NEXT_DAY(d, '') FROM tt");
        assertAnswer(
            "0.032258",
            "SELECT MONTHS_BETWEEN(d + 1, d) FROM tt");
        assertAnswer(
            "-1.000000",
            "SELECT MONTHS_BETWEEN(ts, DATEADD(month, 1, ts)) FROM tt");
        assertAnswer(
            "2024-02-03",
            "SELECT ADD_MONTHS(d + 1, 1) FROM tt");
        assertAnswer(
            "2024-01-02T03:00",
            "SELECT TIME_SLICE(ts, 1, 'HOUR') FROM tt");
        assertAnswer(
            "2024-01-02T04:00",
            "SELECT TIME_SLICE(DATEADD(hour, 1, ts), 1, 'HOUR') FROM tt");
        assertAnswer(
            "2024",
            "SELECT YEAR(q.c) FROM (SELECT d c FROM tt) q");
        assertAnswer(
            "2024",
            "SELECT YEAR(c) FROM (SELECT d + 1 c FROM tt)");
        assertAnswer(
            "2024",
            "SELECT YEAR(c) FROM (SELECT MAX(d) c FROM tt)");
        assertAnswer(
            "2024",
            "SELECT YEAR((SELECT d FROM tt))");
        assertAnswer(
            "2024",
            "SELECT YEAR($1) FROM (SELECT d FROM tt)");
        assertAnswer(
            "3",
            "SELECT HOUR(c) FROM (SELECT tm c FROM tt)");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hour FROM c) FROM (SELECT tm c FROM tt)");
        assertAnswer(
            "true",
            "SELECT YEAR(CURRENT_DATE) > 2000");
        assertAnswer(
            "true",
            "SELECT YEAR(CURRENT_DATE()) > 2000");
        assertAnswer(
            "true",
            "SELECT YEAR(GETDATE()) > 2000");
        assertAnswer(
            "true",
            "SELECT HOUR(CURRENT_TIME) >= 0");
        assertAnswer(
            "true",
            "SELECT HOUR(LOCALTIME) >= 0");
        assertAnswer(
            "true",
            "SELECT YEAR(LOCALTIMESTAMP) > 2000");
        assertAnswer(
            "2",
            "SELECT DAYOFWEEK(d) FROM tt");
        assertAnswer(
            "1",
            "SELECT WEEKOFYEAR(d) FROM tt");
        assertAnswer(
            "1",
            "SELECT QUARTER(ts) FROM tt");
        assertAnswer(
            "2",
            "SELECT EXTRACT(dow FROM d) FROM tt");
        assertAnswer(
            "2024",
            "SELECT EXTRACT(yy FROM d) FROM tt");
        assertAnswer(
            "3",
            "SELECT EXTRACT(hh FROM tm) FROM tt");
        assertAnswer(
            "4",
            "SELECT EXTRACT(mi FROM tm) FROM tt");
        assertAnswer(
            "0",
            "SELECT EXTRACT(ns FROM tm) FROM tt");
        assertAnswer(
            "1704153600",
            "SELECT EXTRACT(epoch FROM d) FROM tt");
        assertAnswer(
            "2",
            "SELECT DATE_PART(tzh, tz) FROM tt");
        assertAnswer(
            "1704164645000",
            "SELECT DATE_PART(epoch_millisecond, ts) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [yy] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(yy FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [hh] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(hh FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [MI] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(mi, d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [DD] for parameter 'DATE_PART date/time part'",
            "SELECT DATE_PART(dd, tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [tzh] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(tzh FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [tzm] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(tzm FROM d) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [epoch_nanosecond] for parameter 'EXTRACT "
                + "date/time part'",
            "SELECT EXTRACT(epoch_nanosecond FROM tm) FROM tt");
        assertAnswer(
            "0",
            "SELECT EXTRACT(nsecond FROM tm) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [nsecond] for parameter 'EXTRACT date/time part'",
            "SELECT EXTRACT(nsecond FROM d) FROM tt");
    }

    @Test
    public void truncTruncatesAZonedTimestampByADatePart() {
        engine.execute("CREATE OR REPLACE TABLE tt (tz TIMESTAMP_TZ, lz TIMESTAMP_LTZ, d DATE)");
        engine.execute("INSERT INTO tt SELECT '2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, '2024-01-02 "
            + "03:04:05'::TIMESTAMP_LTZ, '2024-01-02'::DATE");
        assertAnswer(
            "true",
            "SELECT TRUNC(tz, 'month') = DATE_TRUNC('month', tz) FROM tt");
        assertAnswer(
            "true",
            "SELECT TRUNC(lz, 'month') = DATE_TRUNC('month', lz) FROM tt");
        assertAnswer(
            "true",
            "SELECT TRUNC(tz, 'hour') = DATE_TRUNC('hour', tz) FROM tt");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TRUNC(tz, 'month')) FROM tt");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(TRUNC(lz, 'day')) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TRUNC': (TIMESTAMP_TZ(9))",
            "SELECT TRUNC(tz) FROM tt");
        assertAnswer(
            "true",
            "SELECT TRUNC(lz, 'year') = DATE_TRUNC('year', lz) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: ['xyz'] is not a valid date/time component for function TRUNC.",
            "SELECT TRUNC(tz, 'xyz') FROM tt");
        assertAnswer(
            "true",
            "SELECT TRUNC('2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, 'month') = '2024-01-01 00:00:00 "
                + "+02:00'::TIMESTAMP_TZ");
        assertAnswer(
            "2024-01-01",
            "SELECT TO_VARCHAR(TRUNC(d, 'month')) FROM tt");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TRUNC': (TIMESTAMP_LTZ(9))",
            "SELECT TRUNC(lz) FROM tt");
    }
}
