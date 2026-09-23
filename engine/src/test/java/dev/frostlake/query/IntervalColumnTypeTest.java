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
 * An INTERVAL type spelled in a column definition or a cast target: every single field and every pair from a
 * leading field to a later one of its family, with leading and fractional digits; the metadata surfaces that
 * print it; the conversions into it from text, from another interval, from a number; and the refusals of every
 * other spelling and source. Live-verified cell by cell.
 */
public class IntervalColumnTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ict_src (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO ict_src VALUES ('2024-01-02 01:00:00.123456789', '2024-01-01 00:00:00')");
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
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** One column of every row, joined by {@code ;}. */
    private String column(final String sql, final int index) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(row.getValue(index));
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
        return "answered";
    }

    /** A column declared with every qualifier is created and DESCRIBE, SHOW COLUMNS and INFORMATION_SCHEMA name it. */
    @Test
    public void everyQualifierDeclaresAColumn() {
        engine.execute("CREATE OR REPLACE TABLE ict_all (a INTERVAL DAY(3) TO SECOND(3), b INTERVAL YEAR TO MONTH,"
            + " c INTERVAL HOUR, d INTERVAL DAY, e INTERVAL SECOND, f INTERVAL MONTH, g INTERVAL YEAR,"
            + " h INTERVAL MINUTE TO SECOND, i INTERVAL DAY TO HOUR, j INTERVAL DAY TO MINUTE,"
            + " k INTERVAL HOUR TO MINUTE, l INTERVAL HOUR TO SECOND, m interval minute)");
        assertEquals("INTERVAL DAY(3) TO SECOND(3);INTERVAL YEAR(9) TO MONTH;INTERVAL HOUR(9);INTERVAL DAY(9);"
            + "INTERVAL SECOND(9,9);INTERVAL MONTH(9);INTERVAL YEAR(9);INTERVAL MINUTE(9) TO SECOND(9);"
            + "INTERVAL DAY(9) TO HOUR;INTERVAL DAY(9) TO MINUTE;INTERVAL HOUR(9) TO MINUTE;"
            + "INTERVAL HOUR(9) TO SECOND(9);INTERVAL MINUTE(9)", column("DESCRIBE TABLE ict_all", 1));
        assertEquals("{\"type\":\"INTERVAL DAY TO SECOND\",\"precision\":3,\"scale\":3,\"nullable\":true};"
            + "{\"type\":\"INTERVAL YEAR TO MONTH\",\"precision\":9,\"nullable\":true};"
            + "{\"type\":\"INTERVAL HOUR\",\"precision\":9,\"nullable\":true}",
            column("SHOW COLUMNS IN TABLE ict_all", 3).substring(0, column("SHOW COLUMNS IN TABLE ict_all", 3)
                .indexOf(";{\"type\":\"INTERVAL DAY\"")));
        assertEquals("A|INTERVAL DAY TO SECOND;B|INTERVAL YEAR TO MONTH;I|INTERVAL DAY TO HOUR;M|INTERVAL MINUTE",
            rows("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'ICT_ALL'"
                + " AND column_name IN ('A', 'B', 'I', 'M') ORDER BY column_name"));
        assertTrue(column("SELECT GET_DDL('TABLE', 'ICT_ALL')", 0).contains("\tA INTERVAL DAY(3) TO SECOND(3),\n"
            + "\tB INTERVAL YEAR(9) TO MONTH,"), column("SELECT GET_DDL('TABLE', 'ICT_ALL')", 0));
    }

    /**
     * Two intervals of one family meet in the fields of the one written first with the larger digits of the two,
     * leading and fractional alike, in a set operation and in a conditional, and the values print in that type.
     */
    @Test
    public void setOperationsAndConditionalsWidenTheDigits() {
        engine.execute("CREATE OR REPLACE TABLE ict_w (iv INTERVAL DAY TO SECOND, a INTERVAL DAY(3) TO SECOND(3),"
            + " h INTERVAL HOUR, k NUMBER)");
        engine.execute("INSERT INTO ict_w VALUES ('1 01:00:00', '1 01:00:00.5', '25', 1)");
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|+1 01:00:00.000000000;INTERVAL DAY(9) TO SECOND(9)[SB16]"
            + "|+1 01:00:00.500000000", rows("SELECT SYSTEM$TYPEOF(x), x::VARCHAR FROM (SELECT a AS x FROM ict_w"
            + " UNION ALL SELECT iv FROM ict_w) ORDER BY 2"));
        assertEquals("INTERVAL DAY(5)[SB8]|+1;INTERVAL DAY(5)[SB8]|+4", rows("SELECT SYSTEM$TYPEOF(x), x::VARCHAR FROM"
            + " (SELECT '1'::INTERVAL DAY(2) AS x UNION ALL SELECT '100'::INTERVAL HOUR(5)) ORDER BY 2"));
        assertEquals("INTERVAL SECOND(4,3)[SB8]|+1.500;INTERVAL SECOND(4,3)[SB8]|+120.000", rows("SELECT"
            + " SYSTEM$TYPEOF(x), x::VARCHAR FROM (SELECT '1.5'::INTERVAL SECOND(2,3) AS x UNION ALL"
            + " SELECT '2'::INTERVAL MINUTE(4)) ORDER BY 2"));
        assertEquals("INTERVAL DAY(3) TO HOUR[SB8]|+0 00;INTERVAL DAY(3) TO HOUR[SB8]|+1 02", rows("SELECT"
            + " SYSTEM$TYPEOF(x), x::VARCHAR FROM (SELECT '1 02'::INTERVAL DAY(2) TO HOUR AS x UNION ALL"
            + " SELECT '1:30.5'::INTERVAL MINUTE(3) TO SECOND(4)) ORDER BY 2"));
        assertEquals("INTERVAL YEAR(5)[SB4]|+1;INTERVAL YEAR(5)[SB4]|+8", rows("SELECT SYSTEM$TYPEOF(x), x::VARCHAR FROM"
            + " (SELECT '1'::INTERVAL YEAR(2) AS x UNION ALL SELECT '100'::INTERVAL MONTH(5)) ORDER BY 2"));
        assertEquals("INTERVAL MONTH(5)[SB4]|+100;INTERVAL MONTH(5)[SB4]|+12", rows("SELECT SYSTEM$TYPEOF(x), x::VARCHAR"
            + " FROM (SELECT '100'::INTERVAL MONTH(5) AS x UNION ALL SELECT '1'::INTERVAL YEAR(2)) ORDER BY 2"));
        assertEquals("INTERVAL DAY(7)[SB16]|+1", rows("SELECT SYSTEM$TYPEOF(x), x::VARCHAR FROM (SELECT '1'::INTERVAL"
            + " DAY(2) AS x UNION ALL SELECT '1'::INTERVAL DAY(5) UNION ALL SELECT '1'::INTERVAL DAY(7)) ORDER BY 2"
            + " LIMIT 1"));
        assertEquals("INTERVAL DAY(5)[SB8]|INTERVAL SECOND(5,3)[SB8]|INTERVAL HOUR(5)[SB8]|INTERVAL DAY(5)[SB8]",
            rows("SELECT SYSTEM$TYPEOF(IFF(TRUE, '1'::INTERVAL DAY(2), '1'::INTERVAL DAY(5))),"
                + " SYSTEM$TYPEOF(IFF(TRUE, '1.5'::INTERVAL SECOND(2,3), '1'::INTERVAL SECOND(5,1))),"
                + " SYSTEM$TYPEOF(IFF(TRUE, '1'::INTERVAL HOUR(2), '1'::INTERVAL DAY(5))),"
                + " SYSTEM$TYPEOF(IFF(TRUE, '1'::INTERVAL DAY(5), '1'::INTERVAL HOUR(2)))"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|INTERVAL DAY(9) TO SECOND(9)[SB16]|INTERVAL DAY(9) TO SECOND(9)"
            + "[SB16]|INTERVAL DAY(9) TO SECOND(9)[SB16]|INTERVAL DAY(9) TO SECOND(3)[SB16]|INTERVAL HOUR(9)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(COALESCE(a, iv)), SYSTEM$TYPEOF(NVL2(a, a, iv)), SYSTEM$TYPEOF(CASE WHEN k = 1"
                + " THEN a ELSE iv END), SYSTEM$TYPEOF(GREATEST(a, iv)), SYSTEM$TYPEOF(IFF(k = 1, a, h)),"
                + " SYSTEM$TYPEOF(COALESCE(h, a)) FROM ict_w"));
        assertEquals("+1 01:00:00.500000000|+1 01:00:00.500|+25", rows("SELECT COALESCE(a, iv)::VARCHAR,"
            + " IFF(k = 1, a, h)::VARCHAR, COALESCE(h, a)::VARCHAR FROM ict_w"));
        engine.execute("CREATE OR REPLACE TABLE ict_u AS SELECT a AS x FROM ict_w UNION ALL SELECT iv FROM ict_w");
        assertEquals("INTERVAL DAY(9) TO SECOND(9)", column("DESCRIBE TABLE ict_u", 1));
    }

    /** A cast names the type as a column does, and SYSTEM$TYPEOF tags it by the widest span its digits allow. */
    @Test
    public void castTargetsAreTypedWithTheirDigits() {
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|INTERVAL YEAR(9) TO MONTH[SB8]|INTERVAL DAY(3) TO SECOND(3)[SB8]"
            + "|INTERVAL SECOND(3,9)[SB8]|INTERVAL DAY(2)[SB8]", rows("SELECT SYSTEM$TYPEOF(CAST(NULL AS INTERVAL DAY"
            + " TO SECOND)), SYSTEM$TYPEOF(CAST(NULL AS INTERVAL YEAR TO MONTH)), SYSTEM$TYPEOF(NULL::INTERVAL DAY(3)"
            + " TO SECOND(3)), SYSTEM$TYPEOF(NULL::INTERVAL SECOND(3)), SYSTEM$TYPEOF(TRY_CAST(NULL AS INTERVAL DAY(2)))"));
        assertEquals("INTERVAL DAY(5)[SB8]|INTERVAL DAY(6)[SB16]|INTERVAL MINUTE(8)[SB8]|INTERVAL MONTH(2)[SB2]"
            + "|INTERVAL MONTH(5)[SB4]|INTERVAL YEAR(2) TO MONTH[SB2]", rows("SELECT SYSTEM$TYPEOF(NULL::INTERVAL DAY(5)),"
            + " SYSTEM$TYPEOF(NULL::INTERVAL DAY(6)), SYSTEM$TYPEOF(NULL::INTERVAL MINUTE(8)),"
            + " SYSTEM$TYPEOF(NULL::INTERVAL MONTH(2)), SYSTEM$TYPEOF(NULL::INTERVAL MONTH(5)),"
            + " SYSTEM$TYPEOF(NULL::INTERVAL YEAR(2) TO MONTH)"));
    }

    /** A pair outside a family, a digit count out of range and a trailing precision on no SECOND are refused. */
    @Test
    public void invalidSpecificationsAreRefused() {
        assertEquals("Invalid specification for type INTERVAL: INTERVAL MONTH TO DAY",
            refusal("SELECT NULL::INTERVAL MONTH TO DAY"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO DAY", refusal("SELECT NULL::INTERVAL DAY TO DAY"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY", refusal("SELECT NULL::INTERVAL DAY(10)"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL SECOND", refusal("SELECT NULL::INTERVAL SECOND(0,0)"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY TO SECOND",
            refusal("SELECT NULL::INTERVAL DAY TO SECOND(10)"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL YEAR TO MONTH",
            refusal("SELECT NULL::INTERVAL YEAR TO MONTH(2)"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY", refusal("SELECT NULL::INTERVAL DAY(2,2)"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            refusal("CREATE OR REPLACE TABLE ict_bad (c INTERVAL DAY(10))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected 'WEEK'.",
            refusal("SELECT NULL::INTERVAL WEEK"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 37 unexpected ','.\n"
            + "syntax error line 1 at position 39 unexpected ')'.", refusal("SELECT NULL::INTERVAL DAY TO SECOND(3,3)"));
        assertEquals("SQL compilation error:\nUnsupported data type 'Array with element type INTERVAL DAY(9)'.",
            refusal("CREATE OR REPLACE TABLE ict_bad (c ARRAY(INTERVAL DAY))"));
        assertEquals("SQL compilation error:\nUnsupported data type 'Object with field type INTERVAL DAY(9)'.",
            refusal("CREATE OR REPLACE TABLE ict_bad (c OBJECT(x INTERVAL DAY))"));
    }

    /** A text is read in the type's fields and printed back in them, fractional digits included. */
    @Test
    public void textConvertsInTheTypesFields() {
        assertEquals("+1 02:03:04.500000000|+1 02:03|+1 02|+2:03|+2:03:04.500000000|+3:04.500000000|+4.500000000",
            rows("SELECT '1 02:03:04.5'::INTERVAL DAY TO SECOND::VARCHAR, '1 02:03'::INTERVAL DAY TO MINUTE::VARCHAR,"
                + " '1 02'::INTERVAL DAY TO HOUR::VARCHAR, '02:03'::INTERVAL HOUR TO MINUTE::VARCHAR,"
                + " '02:03:04.5'::INTERVAL HOUR TO SECOND::VARCHAR, '03:04.5'::INTERVAL MINUTE TO SECOND::VARCHAR,"
                + " '4.5'::INTERVAL SECOND::VARCHAR"));
        assertEquals("-1-02|+0-00|+14|-3|+1 02:03:04|+5|+1.000|-2:03:04.0", rows("SELECT"
            + " '-1-2'::INTERVAL YEAR TO MONTH::VARCHAR, '0-0'::INTERVAL YEAR TO MONTH::VARCHAR,"
            + " '14'::INTERVAL MONTH::VARCHAR, '-3'::INTERVAL YEAR::VARCHAR, '1 02:03:04'::INTERVAL DAY TO SECOND(0)::VARCHAR,"
            + " '5'::INTERVAL SECOND(9,0)::VARCHAR, '1'::INTERVAL SECOND(2,3)::VARCHAR,"
            + " '-02:03:04'::INTERVAL HOUR TO SECOND(1)::VARCHAR"));
        assertTrue(refusal("SELECT '1'::INTERVAL DAY TO SECOND").startsWith("Day-Time Interval '1' is invalid, expected"
            + " format is '<sign>D(p) HH24:MM:SS.F(fsp)' for subtype DAY TO SECOND"), refusal("SELECT '1'::INTERVAL DAY TO SECOND"));
        assertEquals("Day-Time Interval '1 25:00:00' is invalid, required that 0 <= HOUR <= 23, 0 <= MINUTE <= 59,"
            + " 0 <= SECOND <= 59", refusal("SELECT '1 25:00:00'::INTERVAL DAY TO SECOND"));
        assertEquals("Day-Time Interval is '100' invalid, value of leading or fractional second field is greater than"
            + " specified precision/fsp", refusal("SELECT '100'::INTERVAL DAY(2)"));
        assertEquals("Year-Month Interval '1-13' is invalid, required that 0 <= MONTH <= 11",
            refusal("SELECT '1-13'::INTERVAL YEAR TO MONTH"));
        assertEquals("Year-Month Interval '100' is invalid, value of leading field is greater than specified precision",
            refusal("SELECT '100'::INTERVAL YEAR(2)"));
        assertEquals("null|+1", rows("SELECT TRY_CAST('x' AS INTERVAL DAY), TRY_CAST('1' AS INTERVAL DAY)::VARCHAR"));
    }

    /** An interval of the same family is cut toward zero to the type's fields; past the leading digits it is refused. */
    @Test
    public void intervalsConvertByTruncation() {
        assertEquals("+1 00:00:00.999|-1 00:00:00.999|+1|-1 23|+47:59|+0:00|+25", rows("SELECT"
            + " ('1 00:00:00.9999'::INTERVAL DAY TO SECOND)::INTERVAL DAY TO SECOND(3)::VARCHAR,"
            + " ('-1 00:00:00.9999'::INTERVAL DAY TO SECOND)::INTERVAL DAY TO SECOND(3)::VARCHAR,"
            + " ('1 23:59:59'::INTERVAL DAY TO SECOND)::INTERVAL DAY::VARCHAR,"
            + " ('-1 23:59:59'::INTERVAL DAY TO SECOND)::INTERVAL DAY TO HOUR::VARCHAR,"
            + " ('1 23:59:59.9'::INTERVAL DAY TO SECOND)::INTERVAL HOUR TO MINUTE::VARCHAR,"
            + " ('-0 00:00:00.5'::INTERVAL DAY TO SECOND)::INTERVAL HOUR TO MINUTE::VARCHAR,"
            + " (ts - ts2)::INTERVAL HOUR::VARCHAR FROM ict_src"));
        assertEquals("+1|-1|+2-01", rows("SELECT ('1-11'::INTERVAL YEAR TO MONTH)::INTERVAL YEAR::VARCHAR,"
            + " ('-1-11'::INTERVAL YEAR TO MONTH)::INTERVAL YEAR::VARCHAR, ('25'::INTERVAL MONTH)::INTERVAL YEAR TO MONTH::VARCHAR"));
        assertEquals("Interval out of representable range, type: INTERVAL_DAY_TIME[SB8](2,6){not null} value: +100"
            + " 00:00:00.000000000", refusal("SELECT ('100 00:00:00'::INTERVAL DAY TO SECOND)::INTERVAL DAY(2)::VARCHAR"));
        assertEquals("Interval out of representable range, type: INTERVAL_YEAR_MONTH[SB2](1,2){not null} value: +2-11",
            refusal("SELECT ('2-11'::INTERVAL YEAR TO MONTH)::INTERVAL MONTH(1)::VARCHAR"));
    }

    /** An exact number is a count of a one-field type's field; several fields take none; every other family is refused. */
    @Test
    public void numbersAndOtherSources() {
        assertEquals("+1|+2|+1.500000000|+90|+1|-2|+2|-2", rows("SELECT CAST(1 AS INTERVAL DAY)::VARCHAR,"
            + " CAST(1.5 AS INTERVAL HOUR)::VARCHAR, CAST(1.5 AS INTERVAL SECOND)::VARCHAR, CAST(90 AS INTERVAL MINUTE)::VARCHAR,"
            + " CAST(1 AS INTERVAL YEAR)::VARCHAR, CAST(-2 AS INTERVAL MONTH)::VARCHAR, CAST(1.5 AS INTERVAL YEAR)::VARCHAR,"
            + " CAST(-1.5 AS INTERVAL DAY)::VARCHAR"));
        assertEquals("Numeric value 1 cannot be cast to Interval type INTERVAL_DAY_TIME[SB16](9,5){not null}",
            refusal("SELECT CAST(1 AS INTERVAL DAY TO HOUR)::VARCHAR"));
        assertEquals("Numeric value 1 cannot be cast to Interval type INTERVAL_DAY_TIME[SB16](153,7){not null}",
            refusal("SELECT CAST(1 AS INTERVAL HOUR TO SECOND)::VARCHAR"));
        assertEquals("Interval out of representable range, type: INTERVAL_DAY_TIME[SB16](9,6){not null} value: 1000000000",
            refusal("SELECT CAST(1000000000 AS INTERVAL DAY)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TO_INTERVAL_YEAR_MONTH('1') AS INTERVAL DAY(9))] for"
            + " parameter 'TO_INTERVAL_DAY_TIME'", refusal("SELECT CAST(INTERVAL '1' YEAR AS INTERVAL DAY)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TO_INTERVAL_DAY_TIME('1 02:03:04') AS INTERVAL YEAR(9)"
            + " TO MONTH)] for parameter 'TO_INTERVAL_YEAR_MONTH'",
            refusal("SELECT CAST('1 02:03:04'::INTERVAL DAY TO SECOND AS INTERVAL YEAR TO MONTH)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TO_DOUBLE(1.5) AS INTERVAL HOUR(9))] for parameter"
            + " 'TO_INTERVAL_DAY_TIME'", refusal("SELECT CAST(1.5::FLOAT AS INTERVAL HOUR)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TO_VARIANT('1') AS INTERVAL DAY(9))] for parameter"
            + " 'TO_INTERVAL_DAY_TIME'", refusal("SELECT CAST(TO_VARIANT('1') AS INTERVAL DAY)"));
        assertEquals("SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types NUMBER(1,0) and"
            + " INTERVAL DAY(9) TO HOUR", refusal("SELECT TRY_CAST(1 AS INTERVAL DAY TO HOUR)"));
        assertEquals("93785|26|14|3|123", rows("SELECT ('1 02:03:04.5'::INTERVAL DAY TO SECOND)::NUMBER,"
            + " ('1 02'::INTERVAL DAY TO HOUR)::NUMBER, ('1-2'::INTERVAL YEAR TO MONTH)::NUMBER, ('3'::INTERVAL YEAR)::NUMBER,"
            + " ('02:03'::INTERVAL HOUR TO MINUTE)::NUMBER"));
    }

    /** A column write converts as the cast does, refuses other families while compiling and a bad text per row. */
    @Test
    public void columnsTakeWritesInTheirFields() {
        engine.execute("CREATE OR REPLACE TABLE ict_w (iv INTERVAL DAY TO SECOND, a INTERVAL DAY(3) TO SECOND(3),"
            + " b INTERVAL YEAR TO MONTH, c INTERVAL HOUR, e INTERVAL SECOND, m INTERVAL MONTH)");
        engine.execute("INSERT INTO ict_w (iv, a) SELECT ts - ts2, ts - ts2 FROM ict_src");
        engine.execute("INSERT INTO ict_w (iv, a, b, c, e, m) VALUES ('1 02:03:04.5', '1 02:03:04.5', '1-2', '25',"
            + " '1.5', '14')");
        engine.execute("INSERT INTO ict_w (c) SELECT ts - ts2 FROM ict_src");
        assertEquals("+1 01:00:00.123456789|+1 01:00:00.123|null|null|null|null;"
            + "+1 02:03:04.500000000|+1 02:03:04.500|+1-02|+25|+1.500000000|+14;null|null|null|+25|null|null",
            rows("SELECT iv::VARCHAR, a::VARCHAR, b::VARCHAR, c::VARCHAR, e::VARCHAR, m::VARCHAR FROM ict_w"
                + " ORDER BY iv NULLS LAST"));
        assertEquals("INTERVAL DAY(3) TO SECOND(3)[SB8]|INTERVAL HOUR(9)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(c) FROM ict_w LIMIT 1"));
        assertEquals("+25", rows("SELECT c::VARCHAR FROM ict_w WHERE c = '25' LIMIT 1"));
        assertEquals("SQL compilation error:\nExpression type does not match column data type, expecting INTERVAL"
            + " DAY(9) TO SECOND(9) but got NUMBER(1,0) for column IV", refusal("INSERT INTO ict_w (iv) VALUES (1)"));
        assertEquals("SQL compilation error:\nExpression type does not match column data type, expecting INTERVAL"
            + " YEAR(9) TO MONTH but got INTERVAL DAY(9) TO SECOND(9) for column B",
            refusal("INSERT INTO ict_w (b) SELECT ts - ts2 FROM ict_src"));
        assertEquals("SQL compilation error:\nExpression type does not match column data type, expecting INTERVAL"
            + " DAY(9) TO SECOND(9) but got VARIANT for column IV", refusal("INSERT INTO ict_w (iv) SELECT TO_VARIANT('1')"));
        engine.execute("CREATE OR REPLACE TABLE ict_s (s VARCHAR)");
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(16777216)]",
            refusal("INSERT INTO ict_s (s) SELECT ts - ts2 FROM ict_src"));
        assertTrue(refusal("INSERT INTO ict_w (iv) VALUES ('x')").startsWith("DML operation to table ICT_W failed on"
            + " column IV with error: Day-Time Interval 'x' is invalid"), refusal("INSERT INTO ict_w (iv) VALUES ('x')"));
        assertEquals("DML operation to table ICT_W failed on column A with error: Day-Time Interval is '1000 00:00:00'"
            + " invalid, value of leading or fractional second field is greater than specified precision/fsp",
            refusal("INSERT INTO ict_w (a) VALUES ('1000 00:00:00')"));
        assertEquals("SQL compilation error:\nDefault value data type does not match data type for column C",
            refusal("CREATE OR REPLACE TABLE ict_d (c INTERVAL DAY DEFAULT '1')"));
        assertEquals("SQL compilation error: cannot change column IV from type INTERVAL DAY(9) TO SECOND(9) to"
            + " INTERVAL DAY(9)\n", refusal("ALTER TABLE ict_w ALTER COLUMN iv SET DATA TYPE INTERVAL DAY"));
    }

    /** A VALUES slot hands the column the interval's own number, read back in the value's fields. */
    @Test
    public void valuesSlotsReadTheNumberBack() {
        engine.execute("CREATE OR REPLACE TABLE ict_v (m INTERVAL MONTH, y INTERVAL YEAR, h INTERVAL HOUR)");
        engine.execute("INSERT INTO ict_v (m, y) VALUES (INTERVAL '1' YEAR, INTERVAL '3' YEAR)");
        engine.execute("INSERT INTO ict_v (m) SELECT INTERVAL '1' YEAR");
        assertEquals("+12|null;+144|+36", rows("SELECT m::VARCHAR, y::VARCHAR FROM ict_v ORDER BY m"));
        assertEquals("DML operation to table ICT_V failed on column H with error: Day-Time Interval is '7200000000000'"
            + " invalid, value of leading or fractional second field is greater than specified precision/fsp",
            refusal("INSERT INTO ict_v (h) VALUES (INTERVAL '2' HOUR)"));
    }

    /** RESULT_SCAN over a result with an interval column answers only the bare star. */
    @Test
    public void resultScanRefusesAnIntervalColumn() {
        engine.executeQuery("SELECT ts - ts2 AS iv, 1 AS n FROM ict_src");
        final String refused = refusal("SELECT n FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertTrue(refused.startsWith("SQL compilation error:\ninvalid type [CAST(STRIP_NULL_VALUE(GET(\"RESULT_SCAN_"),
            refused);
        assertTrue(refused.endsWith("_RESULT_SCAN\".$1, 'IV')) AS INTERVAL DAY(9) TO SECOND(9))] for parameter"
            + " 'TO_INTERVAL_DAY_TIME'"), refused);
        engine.executeQuery("SELECT ts - ts2 AS iv, 1 AS n FROM ict_src");
        assertEquals("1", column("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r", 1));
        engine.executeQuery("SELECT ts - ts2 AS iv, 1 AS n FROM ict_src");
        final String aliased = refusal("SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r");
        assertTrue(aliased.endsWith("_R\".$1, 'IV')) AS INTERVAL DAY(9) TO SECOND(9))] for parameter"
            + " 'TO_INTERVAL_DAY_TIME'"), aliased);
    }

    /**
     * A unit-suffixed literal meets a declared column as an interval of its family: a sum spans both, a product
     * widens the leading digits, a result past them is refused naming the column's nullability, and a write cuts
     * the literal to the column's fields.
     */
    @Test
    public void unitSuffixedLiteralsMeetDeclaredColumns() {
        engine.execute("CREATE OR REPLACE TABLE ict_lit (iv INTERVAL DAY TO SECOND, d2 INTERVAL DAY(2), h INTERVAL HOUR,"
            + " ym INTERVAL YEAR TO MONTH, k NUMBER)");
        engine.execute("INSERT INTO ict_lit VALUES ('1 01:00:00', '5', '25', '1-2', 1), ('0 00:00:01', '99', '2', '0-11', 2)");
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+5 01", rows("SELECT SYSTEM$TYPEOF(d2 + INTERVAL '1' HOUR),"
            + " (d2 + INTERVAL '1' HOUR)::VARCHAR FROM ict_lit WHERE k = 1"));
        assertEquals("INTERVAL DAY(9) TO HOUR[SB16]|+3 04", rows("SELECT SYSTEM$TYPEOF(h * 2 + INTERVAL '1 02' DAY TO"
            + " HOUR), (h * 2 + INTERVAL '1 02' DAY TO HOUR)::VARCHAR FROM ict_lit WHERE k = 1"));
        assertEquals("INTERVAL YEAR(9) TO MONTH[SB8]|+1-03", rows("SELECT SYSTEM$TYPEOF(ym + INTERVAL '1' MONTH(2)),"
            + " (ym + INTERVAL '1' MONTH(2))::VARCHAR FROM ict_lit WHERE k = 1"));
        assertEquals("INTERVAL DAY(4)[SB8]|+1980", rows("SELECT SYSTEM$TYPEOF(d2 * 20), (d2 * 20)::VARCHAR FROM ict_lit"
            + " WHERE k = 2"));
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB16](9,6){nullable}",
            refusal("SELECT (d2 * 200000000)::VARCHAR FROM ict_lit WHERE k = 2"));
        assertEquals("Interval out of representable range after plus, type: INTERVAL_DAY_TIME[SB16](153,3){nullable}",
            refusal("SELECT (iv + INTERVAL '999999999' DAY)::VARCHAR FROM ict_lit WHERE k = 1"));
        engine.execute("INSERT INTO ict_lit (d2, k) SELECT INTERVAL '1 02' DAY TO HOUR, 8");
        assertEquals("+1", rows("SELECT d2::VARCHAR FROM ict_lit WHERE k = 8"));
    }
}
