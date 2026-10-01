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

/**
 * Functions and aggregates over an interval: SUM, AVG and ABS keep the interval, EXTRACT and the one-part
 * functions read its signed components, MEDIAN and the ordered percentiles read the number its cast gives,
 * and the functions that would need it as text, as a VARIANT or as a timestamp refuse it while the statement
 * compiles. Live-verified cell by cell.
 */
public class IntervalFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ifn (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ, g NUMBER)");
        engine.execute("INSERT INTO ifn VALUES ('2024-01-02 01:00:00', '2024-01-01 00:00:00', 1),"
            + " ('2024-01-01 00:00:00.6', '2024-01-01 00:00:00', 1), ('2024-01-01 00:00:00', '2024-01-03 12:30:00', 2)");
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

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
        return "answered";
    }

    /** SUM and AVG add intervals and answer one; grouped, and over no row, NULL. */
    @Test
    public void sumAndAverageKeepTheInterval() {
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|-1 11:29:59.400000000", rows("SELECT SYSTEM$TYPEOF(SUM(ts - ts2)),"
            + " SUM(ts - ts2)::VARCHAR FROM ifn"));
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|-0 11:49:59.800000000", rows("SELECT SYSTEM$TYPEOF(AVG(ts - ts2)),"
            + " AVG(ts - ts2)::VARCHAR FROM ifn"));
        assertEquals("1|+1 01:00:00.600000000|+0 12:30:00.300000000;2|-2 12:30:00.000000000|-2 12:30:00.000000000",
            rows("SELECT g, SUM(ts - ts2)::VARCHAR, AVG(ts - ts2)::VARCHAR FROM ifn GROUP BY g ORDER BY g"));
        assertEquals("null|null", rows("SELECT SUM(ts - ts2)::VARCHAR, AVG(ts - ts2)::VARCHAR FROM ifn WHERE g = 3"));
    }

    /** SUM and AVG answer the widest day-time interval, whatever digits their argument declares. */
    @Test
    public void sumAndAverageWidenTheDigits() {
        engine.execute("CREATE OR REPLACE TABLE ifd (a INTERVAL DAY(3) TO SECOND(3), k NUMBER)");
        engine.execute("INSERT INTO ifd VALUES ('1 01:00:00.5', 1), ('0 00:00:00.25', 2), ('-2 00:00:00', 3)");
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|-0 22:59:59.250000000|INTERVAL DAY(9) TO SECOND(9)[SB16]"
            + "|-0 07:39:59.750000000", rows("SELECT SYSTEM$TYPEOF(SUM(a)), SUM(a)::VARCHAR, SYSTEM$TYPEOF(AVG(a)),"
            + " AVG(a)::VARCHAR FROM ifd"));
        assertEquals("1|+1 01:00:00.500000000;2|+0 00:00:00.250000000;3|-2 00:00:00.000000000",
            rows("SELECT k, SUM(a)::VARCHAR FROM ifd GROUP BY k ORDER BY k"));
        engine.execute("CREATE OR REPLACE TABLE ifd_sums AS SELECT SUM(a) AS s, AVG(a) AS av FROM ifd");
        assertEquals("S|INTERVAL DAY(9) TO SECOND(9);AV|INTERVAL DAY(9) TO SECOND(9)", firstTwo("DESCRIBE TABLE ifd_sums"));
    }

    /** AVG and a division truncate toward zero at the nanosecond, where a product rounds half away from zero. */
    @Test
    public void quotientsTruncateTowardZero() {
        engine.execute("CREATE OR REPLACE TABLE ifq (ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ, g NUMBER)");
        engine.execute("INSERT INTO ifq VALUES ('2024-01-01 00:00:02', '2024-01-01 00:00:00', 1),"
            + " ('2024-01-01 00:00:00', '2024-01-01 00:00:00', 1), ('2024-01-01 00:00:00', '2024-01-01 00:00:00', 1),"
            + " ('2024-01-01 00:00:00.000000001', '2024-01-01 00:00:00', 2), ('2024-01-01 00:00:00', '2024-01-01 00:00:00', 2),"
            + " ('2024-01-01 00:00:05', '2024-01-01 00:00:00', 3), ('2024-01-01 00:00:00', '2024-01-01 00:00:00', 3),"
            + " ('2024-01-01 00:00:00', '2024-01-01 00:00:00', 3)");
        assertEquals("1|+0 00:00:00.666666666|-0 00:00:00.666666666;2|+0 00:00:00.000000000|+0 00:00:00.000000000"
            + ";3|+0 00:00:01.666666666|-0 00:00:01.666666666",
            rows("SELECT g, AVG(ts - ts2)::VARCHAR, AVG(ts2 - ts)::VARCHAR FROM ifq GROUP BY g ORDER BY g"));
        assertEquals("+0 00:00:00.666666666|-0 00:00:00.666666666|+0 00:00:01.333333333|+0 00:00:00.285714285"
            + "|-0 00:00:00.666666666|+0 00:00:00.666666667|+0 00:00:02.857142857|-0 00:00:00.666666667",
            rows("SELECT ((ts - ts2) / 3)::VARCHAR, ((ts2 - ts) / 3)::VARCHAR, ((ts - ts2) / 1.5)::VARCHAR,"
                + " ((ts - ts2) / 7)::VARCHAR, ((ts - ts2) / -3)::VARCHAR, ((ts - ts2) * 0.3333333333)::VARCHAR,"
                + " ((ts - ts2) / 0.7)::VARCHAR, ((ts2 - ts) * 0.3333333333)::VARCHAR FROM ifq WHERE g = 1 AND ts > ts2"));
        assertEquals("+0 00:00:00.000000000|+0 00:00:00.000000000|+0 00:00:00.000000001|-0 00:00:00.000000001"
            + "|+0 00:00:00.000000002", rows("SELECT ((ts - ts2) / 2)::VARCHAR, ((ts2 - ts) / 2)::VARCHAR,"
            + " ((ts - ts2) * 0.5)::VARCHAR, ((ts2 - ts) * 0.5)::VARCHAR, ((ts - ts2) * 1.5)::VARCHAR FROM ifq"
            + " WHERE g = 2 AND ts > ts2"));
    }

    /** A component of an interval that is always NULL is tagged as a NULL is; one of a constant keeps its width. */
    @Test
    public void componentOfNullIsNarrow() {
        assertEquals("NUMBER(9,0)[SB1]|NUMBER(9,0)[SB1]|NUMBER(9,0)[SB1]|NUMBER(9,0)[SB1]|NUMBER(9,0)[SB1]"
            + "|NUMBER(9,0)[SB4]|NUMBER(9,0)[SB4]", rows("SELECT SYSTEM$TYPEOF(EXTRACT(HOUR FROM NULL::INTERVAL DAY"
            + " TO SECOND)), SYSTEM$TYPEOF(EXTRACT(DAY FROM NULL::INTERVAL DAY(2))), SYSTEM$TYPEOF(EXTRACT(YEAR FROM"
            + " NULL::INTERVAL YEAR TO MONTH)), SYSTEM$TYPEOF(HOUR(NULL::INTERVAL HOUR)), SYSTEM$TYPEOF(DATE_PART(MINUTE,"
            + " NULL::INTERVAL DAY TO SECOND)), SYSTEM$TYPEOF(EXTRACT(HOUR FROM '1 02'::INTERVAL DAY TO HOUR)),"
            + " SYSTEM$TYPEOF(EXTRACT(HOUR FROM '25'::INTERVAL HOUR))"));
    }

    /** The text functions, LIKE, OBJECT_AGG and the truncations refuse an interval while compiling. */
    @Test
    public void textAndTruncationRefuseTheInterval() {
        engine.execute("CREATE OR REPLACE TABLE ifo (s VARCHAR, ts TIMESTAMP_NTZ, ts2 TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO ifo VALUES ('+1 01:00:00', '2024-01-02 01:00:00', '2024-01-01 00:00:00')");
        final String incompatible = "SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and"
            + " [VARCHAR(134217728)]";
        assertEquals(incompatible, refusal("SELECT CONCAT_WS(',', ts - ts2, 'a') FROM ifo"));
        assertEquals(incompatible, refusal("SELECT CONCAT_WS(ts - ts2, 'a', 'b') FROM ifo"));
        assertEquals(incompatible, refusal("SELECT TRIM(ts - ts2) FROM ifo"));
        assertEquals(incompatible, refusal("SELECT LTRIM(ts - ts2) FROM ifo"));
        assertEquals(incompatible, refusal("SELECT RTRIM(ts - ts2, 'x') FROM ifo"));
        assertEquals(incompatible, refusal("SELECT ts - ts2 LIKE '%1%' FROM ifo"));
        assertEquals(incompatible, refusal("SELECT 'x' LIKE ts - ts2 FROM ifo"));
        assertEquals(incompatible, refusal("SELECT ts - ts2 ILIKE '%1%' FROM ifo"));
        assertEquals(incompatible, refusal("SELECT ts - ts2 NOT LIKE '%1%' FROM ifo"));
        assertEquals(incompatible, refusal("SELECT ts - ts2 NOT ILIKE '%1%' FROM ifo"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OBJECT_AGG':"
            + " (VARCHAR(16777216), INTERVAL DAY(9) TO SECOND(9))", refusal("SELECT OBJECT_AGG(s, ts - ts2) FROM ifo"));
        assertEquals("SQL compilation error:\nFunction DATE_TRUNC does not support INTERVAL DAY(9) TO SECOND(9) argument"
            + " type", refusal("SELECT DATE_TRUNC('HOUR', ts - ts2) FROM ifo"));
        assertEquals("SQL compilation error:\nFunction DATE_TRUNC does not support INTERVAL DAY(9) argument type",
            refusal("SELECT DATE_TRUNC('HOUR', INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error:\nFunction TRUNC does not support INTERVAL DAY(9) TO SECOND(9) argument type",
            refusal("SELECT TRUNC(ts - ts2, 'HOUR') FROM ifo"));
        assertEquals("y|y|+1 01:00:00,a|+1 01:00:00", rows("SELECT IFF(s LIKE '%1%', 'y', 'n'), IFF(s NOT LIKE 'x', 'y',"
            + " 'n'), CONCAT_WS(',', s, 'a'), TRIM(s) FROM ifo"));
    }

    /** The first two cells of every row, joined by {@code |}, rows by {@code ;}. */
    private String firstTwo(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(row.getValue(0)).append('|').append(row.getValue(1));
        }
        return out.toString();
    }

    /** ABS keeps the interval and its type. */
    @Test
    public void absoluteValueKeepsTheInterval() {
        assertEquals("INTERVAL DAY(9) TO SECOND(9)[SB16]|+0 00:00:00.600000000;INTERVAL DAY(9) TO SECOND(9)[SB16]"
            + "|+1 01:00:00.000000000;INTERVAL DAY(9) TO SECOND(9)[SB16]|+2 12:30:00.000000000",
            rows("SELECT SYSTEM$TYPEOF(ABS(ts - ts2)), ABS(ts - ts2)::VARCHAR FROM ifn ORDER BY 2"));
        assertEquals("+1|INTERVAL YEAR(9)[SB8]|+3|INTERVAL HOUR(9)[SB16]", rows("SELECT ABS(INTERVAL '-1' YEAR)::VARCHAR,"
            + " SYSTEM$TYPEOF(ABS(INTERVAL '-1' YEAR)), ABS(INTERVAL '-3' HOUR)::VARCHAR, SYSTEM$TYPEOF(ABS(INTERVAL '-3' HOUR))"));
    }

    /** EXTRACT, DATE_PART and the one-part functions read the signed components, NUMBER(9,0). */
    @Test
    public void componentsAreSigned() {
        assertEquals("0|0|0|0|600000000;1|0|1|0|0;-12|0|-2|-30|0", rows("SELECT EXTRACT(HOUR FROM ts - ts2),"
            + " DATE_PART(SECOND, ts - ts2), EXTRACT(DAY FROM ts - ts2), EXTRACT(MINUTE FROM ts - ts2),"
            + " EXTRACT(NANOSECOND FROM ts - ts2) FROM ifn ORDER BY g, 1"));
        assertEquals("NUMBER(9,0)[SB4]|NUMBER(9,0)[SB4]", rows("SELECT SYSTEM$TYPEOF(EXTRACT(HOUR FROM ts - ts2)),"
            + " SYSTEM$TYPEOF(EXTRACT(NANOSECOND FROM ts - ts2)) FROM ifn LIMIT 1"));
        assertEquals("0|0|0|0;1|1|0|0;-12|-2|-30|0", rows("SELECT HOUR(ts - ts2), DAY(ts - ts2), MINUTE(ts - ts2),"
            + " SECOND(ts - ts2) FROM ifn ORDER BY g, ts"));
        assertEquals("1|2|1|2|1|1", rows("SELECT EXTRACT(YEAR FROM INTERVAL '14' MONTH), EXTRACT(MONTH FROM INTERVAL"
            + " '14' MONTH), YEAR(INTERVAL '14' MONTH), MONTH(INTERVAL '14' MONTH), EXTRACT(HOUR FROM INTERVAL '25' HOUR),"
            + " EXTRACT(DAY FROM INTERVAL '25' HOUR)"));
    }

    /** A part the interval does not have is refused while compiling, in the function's own name. */
    @Test
    public void missingPartsAreRefused() {
        assertEquals("SQL compilation error:\ninvalid value [EPOCH_SECOND] for parameter 'EXTRACT date/time part'",
            refusal("SELECT EXTRACT(EPOCH_SECOND FROM ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid value [YEAR] for parameter 'EXTRACT date/time part'",
            refusal("SELECT EXTRACT(YEAR FROM ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid value [epoch_second] for parameter 'DATE_PART date/time part'",
            refusal("SELECT DATE_PART('epoch_second', ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid value [YEAR] for parameter 'YEAR date/time part'",
            refusal("SELECT YEAR(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid value [DAYOFWEEK] for parameter 'DAYOFWEEK date/time part'",
            refusal("SELECT DAYOFWEEK(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid value [DAY] for parameter 'EXTRACT date/time part'",
            refusal("SELECT EXTRACT(DAY FROM INTERVAL '14' MONTH)"));
    }

    /** MEDIAN and the ordered percentiles read an interval as the whole number of its cast. */
    @Test
    public void medianReadsTheNumber() {
        assertEquals("1.000|NUMBER(12,3)[SB8]", rows("SELECT MEDIAN(ts - ts2), SYSTEM$TYPEOF(MEDIAN(ts - ts2)) FROM ifn"));
        assertEquals("1.000", rows("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ts - ts2) FROM ifn"));
        assertEquals("-2 12:30:00.000000000|+1 01:00:00.000000000|3", rows("SELECT MIN(ts - ts2)::VARCHAR,"
            + " MAX(ts - ts2)::VARCHAR, COUNT(DISTINCT ts - ts2) FROM ifn"));
    }

    /** The functions that would read the interval as text, a VARIANT or a JSON value refuse it while compiling. */
    @Test
    public void conversionsAreRefused() {
        assertEquals("SQL compilation error:\nFunction ARRAY_CONSTRUCT does not support INTERVAL DAY(9) TO SECOND(9)"
            + " argument type", refusal("SELECT ARRAY_CONSTRUCT(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\nFunction OBJECT_CONSTRUCT does not support INTERVAL YEAR(9) argument type",
            refusal("SELECT OBJECT_CONSTRUCT('k', INTERVAL '1' YEAR)"));
        assertEquals("SQL compilation error:\nFunction ARRAY_AGG does not support INTERVAL DAY(9) TO SECOND(9) argument"
            + " type", refusal("SELECT ARRAY_AGG(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid type [TO_VARIANT(DATE_DIFFTIMESTAMPTOINTERVAL(IFN.TS2, IFN.TS))]"
            + " for parameter 'TO_VARIANT'", refusal("SELECT TO_VARIANT(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\ninvalid type [TO_VARIANT(TO_INTERVAL_DAY_TIME('1'))] for parameter"
            + " 'TO_VARIANT'", refusal("SELECT TO_VARIANT(INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(134217728)]",
            refusal("SELECT (ts - ts2) || 'x' FROM ifn"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL YEAR(9)] and [VARCHAR(134217728)]",
            refusal("SELECT '' || INTERVAL '1' YEAR"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(134217728)]",
            refusal("SELECT UPPER(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(134217728)]",
            refusal("SELECT LENGTH(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error:\nincompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(134217728)]",
            refusal("SELECT LISTAGG(ts - ts2, ',') FROM ifn"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'TO_JSON':"
            + " (INTERVAL DAY(9) TO SECOND(9))", refusal("SELECT TO_JSON(ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function '*':"
            + " (INTERVAL DAY(9) TO SECOND(9), INTERVAL DAY(9) TO SECOND(9))", refusal("SELECT VARIANCE(ts - ts2) FROM ifn"));
    }

    /** The date functions take no interval, and a plus sign refuses one where a minus negates it. */
    @Test
    public void dateFunctionsAndSignsAreRefused() {
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function"
            + " 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), INTERVAL DAY(9) TO SECOND(9))",
            refusal("SELECT DATEADD(second, 1, ts - ts2) FROM ifn"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function"
            + " 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), INTERVAL DAY(9))", refusal("SELECT DATEADD(second, 1, INTERVAL '1' DAY)"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function"
            + " 'DATE_DIFFTIMESTAMPINDAYS': (INTERVAL DAY(9) TO SECOND(9), TIMESTAMP_NTZ(9))",
            refusal("SELECT DATEDIFF(day, ts - ts2, ts) FROM ifn"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UNARY PLUS':"
            + " (INTERVAL DAY(9) TO SECOND(9))", refusal("SELECT +(ts - ts2) FROM ifn"));
        assertEquals("-1 01:00:00.000000000", rows("SELECT (-(ts - ts2))::VARCHAR FROM ifn WHERE g = 1 AND ts > ts2"
            + " ORDER BY ts DESC LIMIT 1"));
    }

    /** Arithmetic leaving the nine digits of days is refused. */
    @Test
    public void arithmeticPastTheRangeIsRefused() {
        assertEquals("+997011834 00:00:00.000000000",
            rows("SELECT ((TIMESTAMP '9999-12-31' - TIMESTAMP '0001-01-01') * 273)::VARCHAR"));
        assertEquals("Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB16](153,3){not null}",
            refusal("SELECT ((TIMESTAMP '9999-12-31' - TIMESTAMP '0001-01-01') * 274)::VARCHAR"));
        assertEquals("Interval out of representable range after plus, type: INTERVAL_DAY_TIME[SB16](153,3){not null}",
            refusal("SELECT ((TIMESTAMP '9999-12-31' - TIMESTAMP '0001-01-01') * 273 + (TIMESTAMP '9999-12-31'"
                + " - TIMESTAMP '0001-01-01') * 10)::VARCHAR"));
    }

    /** The range refusal names the result's nullability: a cast of a literal is not null, a TRY_CAST may be. */
    @Test
    public void rangeRefusalNamesTheOperandsNullability() {
        final String past = "Interval out of representable range after multiply, type: INTERVAL_DAY_TIME[SB16](153,3)";
        assertEquals(past + "{not null}", refusal("SELECT (('2024-01-02'::TIMESTAMP_NTZ - '2024-01-01'::TIMESTAMP_NTZ)"
            + " * 1000000000000)::VARCHAR"));
        assertEquals(past + "{nullable}", refusal("SELECT ((TRY_CAST('2024-01-02' AS TIMESTAMP_NTZ)"
            + " - TIMESTAMP '2024-01-01') * 1000000000000)::VARCHAR"));
        assertEquals(past + "{nullable}", refusal("SELECT ((ts - ts2) * 1000000000000)::VARCHAR FROM ifn"));
    }
}
