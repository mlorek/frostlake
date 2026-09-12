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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A NUMBER's temporal conversions, as the account draws them: a NUMBER or FLOAT has no DATE or TIME
 * conversion and is refused while the statement compiles, with the conversion sentence naming the
 * re-printed cast and the conversion the target routes through; a FLOAT has no TIMESTAMP conversion
 * either; an exact NUMBER cast to a TIMESTAMP flavour is an epoch in seconds whatever its magnitude,
 * its fraction kept, declaring the number's own scale as the precision. The cast's refusal is judged
 * inside-out, ahead of any rule about the call around it. The DATE() and TIME() aliases read a number
 * their own way: a whole number is an epoch for DATE(), a fraction fails as a date's text, and TIME()
 * fails through a VARIANT conversion whatever the number.
 */
public class NumericTemporalCastTest extends BaseDatabaseTest {

    private static final String FF9 = "'YYYY-MM-DD HH24:MI:SS.FF9'";

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE re (a NUMBER(10,2), i NUMBER(38,0), f FLOAT, s VARCHAR, n NUMBER(5,0), "
            + "d DATE, t TIME, b BOOLEAN)");
        engine.execute("INSERT INTO re SELECT 1.5, 86400, 1.5, '2020-01-01', 20200, '2020-01-01', "
            + "'10:00:00', TRUE");
        engine.execute("CREATE TABLE re0 (a NUMBER(10,2), i INT)");
    }

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private void assertRefused(final String sql, final String sentence) {
        final String message = refusal(sql);
        assertTrue(message.contains(sentence), sql + " -> " + message);
    }

    @Test
    public void aNumberHasNoDateOrTimeConversion() {
        assertRefused("SELECT a::DATE FROM re", "invalid type [CAST(RE.A AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT a::TIME FROM re", "invalid type [CAST(RE.A AS TIME(9))] for parameter 'TO_TIME'");
        assertRefused("SELECT a::TIME(3) FROM re", "invalid type [CAST(RE.A AS TIME(3))] for parameter 'TO_TIME'");
        assertRefused("SELECT i::DATE FROM re", "invalid type [CAST(RE.I AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT n::DATE FROM re", "invalid type [CAST(RE.N AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT 1.5::DATE", "invalid type [CAST(1.5 AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT 86400::DATE", "invalid type [CAST(86400 AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT 86400::TIME", "invalid type [CAST(86400 AS TIME(9))] for parameter 'TO_TIME'");
        assertRefused("SELECT TO_DATE(a) FROM re", "invalid type [TO_DATE(RE.A)] for parameter 'TO_DATE'");
        assertRefused("SELECT TO_DATE(a, 'YYYY') FROM re", "invalid type [TO_DATE(RE.A, 'YYYY')] for parameter 'TO_DATE'");
        assertRefused("SELECT TO_TIME(i) FROM re", "invalid type [TO_TIME(RE.I)] for parameter 'TO_TIME'");
        assertRefused("SELECT TRY_CAST(a AS DATE) FROM re", "invalid type [TRY_CAST(RE.A)] for parameter 'TO_DATE'");
        assertRefused("SELECT TRY_TO_DATE(i) FROM re", "invalid type [TRY_TO_DATE(RE.I)] for parameter 'TO_DATE'");
        assertRefused("SELECT TRY_TO_TIME(a) FROM re", "invalid type [TRY_TO_TIME(RE.A)] for parameter 'TO_TIME'");
        assertRefused("SELECT r.a::DATE FROM re r", "invalid type [CAST(R.A AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT a::DATE FROM re0", "invalid type [CAST(RE0.A AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT i::DATE FROM re LIMIT 0", "invalid type [CAST(RE.I AS DATE)] for parameter 'TO_DATE'");
    }

    @Test
    public void aFloatHasNoTemporalConversionAtAll() {
        assertRefused("SELECT f::TIMESTAMP_NTZ FROM re",
            "invalid type [CAST(RE.F AS TIMESTAMP_NTZ(9))] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT f::TIMESTAMP_LTZ FROM re",
            "invalid type [CAST(RE.F AS TIMESTAMP_LTZ(9))] for parameter 'TO_TIMESTAMP_LTZ'");
        assertRefused("SELECT f::TIMESTAMP_TZ FROM re",
            "invalid type [CAST(RE.F AS TIMESTAMP_TZ(9))] for parameter 'TO_TIMESTAMP_TZ'");
        assertRefused("SELECT f::TIME FROM re", "invalid type [CAST(RE.F AS TIME(9))] for parameter 'TO_TIME'");
        assertRefused("SELECT f::DATE FROM re", "invalid type [CAST(RE.F AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT TO_TIMESTAMP(f) FROM re",
            "invalid type [TO_TIMESTAMP(RE.F)] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT TO_TIMESTAMP(f, 3) FROM re",
            "invalid type [TO_TIMESTAMP(RE.F, 3)] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT TO_TIMESTAMP(f, 0) FROM re",
            "invalid type [TO_TIMESTAMP(RE.F, 0)] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT TRY_TO_TIMESTAMP(f, 3) FROM re",
            "invalid type [TRY_TO_TIMESTAMP(RE.F, 3)] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT TO_TIMESTAMP_LTZ(f, 3) FROM re",
            "invalid type [TO_TIMESTAMP_LTZ(RE.F, 3)] for parameter 'TO_TIMESTAMP_LTZ'");
        assertRefused("SELECT TO_TIMESTAMP(b, 3) FROM re",
            "invalid type [TO_TIMESTAMP(RE.B, 3)] for parameter 'TO_TIMESTAMP_NTZ'");
        assertRefused("SELECT TO_DATE(f) FROM re", "invalid type [TO_DATE(RE.F)] for parameter 'TO_DATE'");
        assertRefused("SELECT TO_TIME(f) FROM re", "invalid type [TO_TIME(RE.F)] for parameter 'TO_TIME'");
    }

    @Test
    public void theCastIsRefusedBeforeTheCallAroundIt() {
        final String cast = "invalid type [CAST(RE.A AS DATE)] for parameter 'TO_DATE'";
        assertRefused("SELECT SUM(a::DATE) FROM re", cast);
        assertRefused("SELECT AVG(a::DATE) FROM re", cast);
        assertRefused("SELECT COUNT(a::DATE) FROM re", cast);
        assertRefused("SELECT ARRAY_AGG(a::DATE) FROM re", cast);
        assertRefused("SELECT LISTAGG(a::DATE) FROM re", cast);
        assertRefused("SELECT SUM(LENGTH(a::DATE)) FROM re", cast);
        assertRefused("SELECT SUM(a::DATE) OVER () FROM re", cast);
        assertRefused("SELECT RATIO_TO_REPORT(a::DATE) OVER () FROM re", cast);
        assertRefused("SELECT SYSTEM$TYPEOF(SUM(a::DATE)) FROM re", cast);
        assertRefused("SELECT SYSTEM$TYPEOF(a::DATE) FROM re", cast);
        assertRefused("SELECT MAX(TO_DATE(a)) FROM re", "invalid type [TO_DATE(RE.A)] for parameter 'TO_DATE'");
        assertRefused("SELECT SUM(TO_DATE(f)) FROM re", "invalid type [TO_DATE(RE.F)] for parameter 'TO_DATE'");
        assertRefused("SELECT CASE WHEN a > 1 THEN a::DATE END FROM re", cast);
        assertRefused("SELECT a::DATE FROM re GROUP BY a::DATE", cast);
        assertRefused("SELECT COUNT(*) FROM re WHERE a::DATE > '2020-01-01'", cast);
        assertRefused("SELECT a::DATE FROM re ORDER BY 1", cast);
        assertRefused("SELECT a::DATE FROM re UNION ALL SELECT d FROM re", cast);
        assertRefused("CREATE VIEW vv AS SELECT a::DATE AS x FROM re", cast);
        // Select items are judged in written order, each one inside-out.
        assertRefused("SELECT SUM(f::DATE), SUM(d) FROM re",
            "invalid type [CAST(RE.F AS DATE)] for parameter 'TO_DATE'");
        assertRefused("SELECT SUM(d), SUM(f::DATE) FROM re", "Invalid argument types for function 'SUM': (DATE)");
    }

    @Test
    public void aNumberCastsToATimestampAsEpochSeconds() {
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(a::TIMESTAMP_NTZ, " + FF9 + ") FROM re"));
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(a::DATETIME, " + FF9 + ") FROM re"));
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(a::TIMESTAMP, " + FF9 + ") FROM re"));
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(1.5::TIMESTAMP_NTZ, " + FF9 + ")"));
        assertEquals("1970-01-02 00:00:00.000000000", text("SELECT TO_VARCHAR(i::TIMESTAMP_NTZ, " + FF9 + ") FROM re"));
        assertEquals("1970-01-02 00:00:00.000000000", text("SELECT TO_VARCHAR(86400::TIMESTAMP_NTZ, " + FF9 + ")"));
        // Seconds whatever the magnitude: only a STRING of digits is unit-detected.
        assertEquals("2969-05-03 00:00:00.000000000", text("SELECT TO_VARCHAR(31536000000::TIMESTAMP_NTZ, " + FF9 + ")"));
        assertEquals("2969-05-03 00:00:00.000000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(31536000000), " + FF9 + ")"));
        assertEquals("2969-05-02 23:59:59.500000000", text("SELECT TO_VARCHAR(31535999999.5::TIMESTAMP_NTZ, " + FF9 + ")"));
        // The fraction travels through the conversion functions too, the digits past the ninth truncated.
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(a), " + FF9 + ") FROM re"));
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP_NTZ(a), " + FF9 + ") FROM re"));
        assertEquals("1970-01-01 00:00:00.001500000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1.5, 3), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:00.000000001", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1.5, 9), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:01.123456789", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1.123456789), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:00.001123456", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1.123456789, 3), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:01.999999999", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1.9999999999), " + FF9 + ")"));
        assertEquals("1969-12-31 23:59:58.500000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(-1.5), " + FF9 + ")"));
        assertEquals("1969-12-31 23:59:59.000000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(-1), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:01.500000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1500, 3), " + FF9 + ")"));
        assertEquals("1970-01-18 08:40:00.000000000", text("SELECT TO_VARCHAR(TO_TIMESTAMP(1500000000000, 6), " + FF9 + ")"));
        // A cast's written precision is the TYPE's, never a scale: 1500::TIMESTAMP_NTZ(3) is 1500 seconds.
        assertEquals("1970-01-01 00:25:00.000000000", text("SELECT TO_VARCHAR(1500::TIMESTAMP_NTZ(3), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:02.500000000", text("SELECT TO_VARCHAR(2.5::TIMESTAMP_NTZ(0), " + FF9 + ")"));
        assertEquals("1970-01-01 00:00:01.123456789", text("SELECT TO_VARCHAR(1.123456789::TIMESTAMP_NTZ(3), " + FF9 + ")"));
        // The zoned flavours are the same instant, wherever the session sits.
        assertEquals("86400", text("SELECT DATE_PART(EPOCH_SECOND, i::TIMESTAMP_LTZ) FROM re"));
        assertEquals("86400", text("SELECT DATE_PART(EPOCH_SECOND, i::TIMESTAMP_TZ) FROM re"));
        assertEquals("1500", text("SELECT DATE_PART(EPOCH_MILLISECOND, a::TIMESTAMP_NTZ) FROM re"));
        assertEquals("1", text("SELECT DATE_PART(EPOCH_SECOND, a::TIMESTAMP_NTZ) FROM re"));
    }

    @Test
    public void theDeclaredPrecisionFollowsTheNumber() {
        assertEquals("TIMESTAMP_NTZ(2)[SB8]", text("SELECT SYSTEM$TYPEOF(a::TIMESTAMP_NTZ) FROM re"));
        assertEquals("TIMESTAMP_NTZ(2)[SB8]", text("SELECT SYSTEM$TYPEOF(a::DATETIME) FROM re"));
        assertEquals("TIMESTAMP_NTZ(2)[SB8]", text("SELECT SYSTEM$TYPEOF(TO_TIMESTAMP(a)) FROM re"));
        assertEquals("TIMESTAMP_NTZ(5)[SB8]", text("SELECT SYSTEM$TYPEOF(TO_TIMESTAMP(a, 3)) FROM re"));
        assertEquals("TIMESTAMP_LTZ(0)[SB8]", text("SELECT SYSTEM$TYPEOF(i::TIMESTAMP_LTZ) FROM re"));
        assertEquals("TIMESTAMP_NTZ(1)[SB8]", text("SELECT SYSTEM$TYPEOF(1.5::TIMESTAMP_NTZ(3))"));
        assertEquals("TIMESTAMP_NTZ(2)[SB8]", text("SELECT SYSTEM$TYPEOF(1.25::TIMESTAMP_NTZ(1))"));
        assertEquals("TIMESTAMP_NTZ(4)[SB8]", text("SELECT SYSTEM$TYPEOF(TO_TIMESTAMP(1.5, 3))"));
        assertEquals("TIMESTAMP_NTZ(3)[SB8]", text("SELECT SYSTEM$TYPEOF(TO_TIMESTAMP(1500, 3))"));
    }

    @Test
    public void theDateAndTimeAliasesReadANumberTheirOwnWay() {
        assertEquals("1970-01-02", text("SELECT DATE(86400)"));
        assertEquals("1970-01-02", text("SELECT DATE(i) FROM re"));
        assertEquals("1969-12-31", text("SELECT DATE(-1)"));
        assertEquals("1970-01-01", text("SELECT DATE(2.0)"));
        assertEquals("2286-11-20", text("SELECT DATE(1e10)"));
        assertRefused("SELECT DATE(1.5)", "Date '1.5' is not recognized");
        assertRefused("SELECT DATE(1.9)", "Date '1.9' is not recognized");
        assertRefused("SELECT DATE(a) FROM re", "Date '1.50' is not recognized");
        assertRefused("SELECT DATE(f) FROM re", "Date '1.5' is not recognized");
        assertRefused("SELECT TIME(86400)", "Failed to cast variant value 86400 to TIME");
        assertRefused("SELECT TIME(3661)", "Failed to cast variant value 3661 to TIME");
        assertRefused("SELECT TIME(1.5)", "Failed to cast variant value 1.5 to TIME");
        assertRefused("SELECT TIME(a) FROM re", "Failed to cast variant value 1.5 to TIME");
        assertRefused("SELECT TIME(i) FROM re", "Failed to cast variant value 86400 to TIME");
        assertRefused("SELECT TIME(f) FROM re", "Failed to cast variant value 1.500000000000000e+00 to TIME");
    }
}
