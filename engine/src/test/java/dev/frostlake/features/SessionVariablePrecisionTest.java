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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A time or a timestamp variable keeps the precision of the type it was set from: SYSTEM$TYPEOF and a column
 * built from it read it, a variable copied from it keeps it, and a parameter refuses it in its name; SHOW
 * VARIABLES lists each value's family and its value to the nanosecond (all live-verified).
 */
public class SessionVariablePrecisionTest extends MeasuredCellsTestSupport {

    @Test
    public void parameterRefusalsNameThePrecision() {
        engine.execute("CREATE OR REPLACE TABLE tt691 (c TIME(3), d TIMESTAMP_NTZ(2))");
        engine.execute("INSERT INTO tt691 VALUES ('10:00:00.123', '2024-01-02 03:04:05.12')");
        assertAnswer("Statement executed successfully.", "SET t3 = '10:00:00'::TIME(3)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(3) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t3");
        assertAnswer("Statement executed successfully.", "SET t0 = '10:00:00'::TIME(0)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(0) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t0");
        assertAnswer("Statement executed successfully.", "SET n3 = '2024-01-02 03:04:05'::TIMESTAMP_NTZ(3)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_NTZ(3) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $n3");
        assertAnswer("Statement executed successfully.", "SET l0 = '2024-01-02 03:04:05'::TIMESTAMP_LTZ(0)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_LTZ(0) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $l0");
        assertAnswer("Statement executed successfully.", "SET z6 = '2024-01-02 03:04:05 +01:00'::TIMESTAMP_TZ(6)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_TZ(6) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $z6");
        assertAnswer("Statement executed successfully.", "SET tc = (SELECT c FROM tt691)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(3) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $tc");
        assertAnswer("Statement executed successfully.", "SET td = (SELECT d FROM tt691)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_NTZ(2) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv691 (a INT) DATA_RETENTION_TIME_IN_DAYS = $td");
        assertAnswer("TIME(3)[SB4]|TIMESTAMP_NTZ(3)[SB8]|TIME(3)[SB4]",
            "SELECT SYSTEM$TYPEOF($t3), SYSTEM$TYPEOF($n3), SYSTEM$TYPEOF($tc)");
        assertShape("A:TIME, B:TIMESTAMP_NTZ, C:TIME, D:TIMESTAMP_NTZ",
            "SELECT $t3 AS a, $n3 AS b, $tc AS c, $td AS d");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(3) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "ALTER TABLE tt691 SET DATA_RETENTION_TIME_IN_DAYS = $t3");
    }

    @Test
    public void precisionAcrossTypesAndCopies() {
        engine.execute("CREATE OR REPLACE TABLE tt692 (c TIME, d TIMESTAMP_LTZ(4), e TIMESTAMP_TZ(0))");
        engine.execute("INSERT INTO tt692 VALUES ('10:00:00.123456789', '2024-01-02 03:04:05.1234', '2024-01-02 "
            + "03:04:05 +01:00')");
        assertAnswer("Statement executed successfully.",
            "SET (t0, t3, t6, t9, tt) = ('10:00:00.123456789'::TIME(0), '10:00:00.123456789'::TIME(3), "
                + "'10:00:00.123456789'::TIME(6), '10:00:00.123456789'::TIME(9), '10:00:00.123456789'::TIME)");
        assertAnswer("TIME(0)[SB4]|TIME(3)[SB4]|TIME(6)[SB8]|TIME(9)[SB8]|TIME(9)[SB8]",
            "SELECT SYSTEM$TYPEOF($t0), SYSTEM$TYPEOF($t3), SYSTEM$TYPEOF($t6), SYSTEM$TYPEOF($t9), "
                + "SYSTEM$TYPEOF($tt)");
        assertAnswer("TIME(0)[SB4]|TIME(3)[SB4]|TIME(6)[SB8]|TIME(9)[SB8]",
            "SELECT SYSTEM$TYPEOF('10:00:00'::TIME(0)), SYSTEM$TYPEOF('10:00:00'::TIME(3)), "
                + "SYSTEM$TYPEOF('10:00:00'::TIME(6)), SYSTEM$TYPEOF('10:00:00'::TIME(9))");
        assertAnswer("10:00:00|10:00:00|10:00:00|10:00:00",
            "SELECT $t0::VARCHAR, $t3::VARCHAR, $t6::VARCHAR, $t9::VARCHAR");
        assertAnswer("Statement executed successfully.",
            "SET (n0, n3, n6, n9) = ('2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(0), '2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_NTZ(3), '2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(6), "
                + "'2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(9))");
        assertAnswer("TIMESTAMP_NTZ(0)[SB8]|TIMESTAMP_NTZ(3)[SB8]|TIMESTAMP_NTZ(6)[SB8]|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF($n0), SYSTEM$TYPEOF($n3), SYSTEM$TYPEOF($n6), SYSTEM$TYPEOF($n9)");
        assertAnswer("TIMESTAMP_NTZ(0)[SB8]|TIMESTAMP_NTZ(3)[SB8]|TIMESTAMP_NTZ(6)[SB8]|TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF('2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(0)), SYSTEM$TYPEOF('2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_NTZ(3)), SYSTEM$TYPEOF('2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_NTZ(6)), SYSTEM$TYPEOF('2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_NTZ(9))");
        assertAnswer("Statement executed successfully.",
            "SET (l0, l3, l9, z0, z3, z9) = ('2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(0), '2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_LTZ(3), '2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(9), "
                + "'2024-01-02 03:04:05.123456789 +01:00'::TIMESTAMP_TZ(0), '2024-01-02 03:04:05.123456789 "
                + "+01:00'::TIMESTAMP_TZ(3), '2024-01-02 03:04:05.123456789 +01:00'::TIMESTAMP_TZ(9))");
        assertAnswer("TIMESTAMP_LTZ(0)[SB8]|TIMESTAMP_LTZ(3)[SB8]|TIMESTAMP_LTZ(9)[SB16]|TIMESTAMP_TZ(0)[SB8]|"
                + "TIMESTAMP_TZ(3)[SB8]|TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF($l0), SYSTEM$TYPEOF($l3), SYSTEM$TYPEOF($l9), SYSTEM$TYPEOF($z0), "
                + "SYSTEM$TYPEOF($z3), SYSTEM$TYPEOF($z9)");
        assertAnswer("TIMESTAMP_LTZ(0)[SB8]|TIMESTAMP_LTZ(3)[SB8]|TIMESTAMP_TZ(0)[SB8]|TIMESTAMP_TZ(3)[SB8]",
            "SELECT SYSTEM$TYPEOF('2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(0)), SYSTEM$TYPEOF('2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_LTZ(3)), SYSTEM$TYPEOF('2024-01-02 03:04:05.123456789 "
                + "+01:00'::TIMESTAMP_TZ(0)), SYSTEM$TYPEOF('2024-01-02 03:04:05.123456789 +01:00'::TIMESTAMP_TZ(3))");
        assertAnswer("Statement executed successfully.",
            "SET (vc, vd, ve) = ((SELECT c FROM tt692), (SELECT d FROM tt692), (SELECT e FROM tt692))");
        assertAnswer("TIME(9)[SB8]|TIMESTAMP_LTZ(4)[SB8]|TIMESTAMP_TZ(0)[SB8]",
            "SELECT SYSTEM$TYPEOF($vc), SYSTEM$TYPEOF($vd), SYSTEM$TYPEOF($ve)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(6) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t6");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t9");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $tt");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_NTZ(0) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $n0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_LTZ(3) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $l3");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_TZ(0) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $z0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_LTZ(4) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $vd");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIMESTAMP_TZ(0) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $ve");
        assertAnswer("Statement executed successfully.", "SET t3b = $t3");
        assertAnswer("TIME(3)[SB4]", "SELECT SYSTEM$TYPEOF($t3b)");
        assertAnswer("Statement executed successfully.", "SET t3c = DATEADD(second, 1, $t3)");
        assertAnswer("TIME(9)[SB8]|10:00:01", "SELECT SYSTEM$TYPEOF($t3c), $t3c::VARCHAR");
        assertAnswer("Statement executed successfully.", "SET t3d = (SELECT $t3)");
        assertAnswer("TIME(3)[SB4]", "SELECT SYSTEM$TYPEOF($t3d)");
        assertAnswer("Statement executed successfully.", "SET t3e = (SELECT '10:00:00.5'::TIME(1))");
        assertAnswer("TIME(1)[SB4]", "SELECT SYSTEM$TYPEOF($t3e)");
        assertAnswer("Table CV692 successfully created.",
            "CREATE OR REPLACE TABLE cv692 AS SELECT $t3 AS a, $n0 AS b, $l3 AS c, $z0 AS d, $t9 AS e, $vd AS f");
        engine.execute("DESCRIBE TABLE cv692");
        assertAnswer("A|TIME(3) ;; B|TIMESTAMP_NTZ(0) ;; C|TIMESTAMP_LTZ(3) ;; D|TIMESTAMP_TZ(0) ;; E|TIME(9) ;; F|"
                + "TIMESTAMP_LTZ(4)",
            "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("SHOW VARIABLES LIKE 'T3'");
        assertAnswer("T3|time|10:00:00.123000000",
            "SELECT \"name\", \"type\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        engine.execute("SHOW VARIABLES LIKE 'N0'");
        answer("SELECT \"name\", \"type\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("TIME(0)[SB4]",
            "EXECUTE IMMEDIATE $$BEGIN LET r VARCHAR := (SELECT SYSTEM$TYPEOF($t0)); RETURN r; END;$$");
        assertAnswer("TIME(3)[SB4]|TIMESTAMP_NTZ(3)[SB8]",
            "SELECT SYSTEM$TYPEOF(IFF(TRUE, $t3, $t3)), SYSTEM$TYPEOF(COALESCE($n3, $n3))");
        assertAnswer("Statement executed successfully.", "SET t3 = '11:00:00'::TIME");
        assertAnswer("TIME(9)[SB8]", "SELECT SYSTEM$TYPEOF($t3)");
        assertAnswer("Statement executed successfully.", "SET t0 = 5");
        assertAnswer("NUMBER(1,0)[SB1]", "SELECT SYSTEM$TYPEOF($t0)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported type TIME(9) for parameter "
                + "DATA_RETENTION_TIME_IN_DAYS specified with session variable",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t3");
        assertAnswer("Table PV692 successfully created.",
            "CREATE OR REPLACE TABLE pv692 (a INT) DATA_RETENTION_TIME_IN_DAYS = $t0");
    }

    @Test
    public void listedFamiliesAndValues() {
        assertAnswer("Statement executed successfully.",
            "SET (va_d, va_t0, va_t3, va_t9, va_n0, va_n9, va_l3, va_z0) = ('2024-01-02'::DATE, "
                + "'10:00:00.123456789'::TIME(0), '10:00:00.123456789'::TIME(3), '10:00:00.123456789'::TIME(9), "
                + "'2024-01-02 03:04:05.123456789'::TIMESTAMP_NTZ(0), '2024-01-02 "
                + "03:04:05.123456789'::TIMESTAMP_NTZ(9), '2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(3), "
                + "'2024-01-02 03:04:05.123456789 +01:00'::TIMESTAMP_TZ(0))");
        assertAnswer("Statement executed successfully.",
            "SET (vb_bin, vb_s, vb_f, vb_df, vb_i, vb_b, vb_dec) = (TO_BINARY('AB', 'HEX'), 'abc', 1.5::FLOAT, "
                + "1.5::DECFLOAT, 7, TRUE, 1.50)");
        assertAnswer("Statement executed successfully.", "SET vb_nul = NULL");
        engine.execute("SHOW VARIABLES LIKE 'VA%'");
        engine.execute("SHOW VARIABLES LIKE 'VB%'");
        assertAnswer("VB_B|boolean|TRUE ;; VB_BIN|binary|AB ;; VB_DEC|fixed|1.5 ;; VB_DF|decfloat|1.5 ;; VB_F|real|"
                + "1.5 ;; VB_I|fixed|7 ;; VB_NUL|text|null ;; VB_S|text|abc",
            "SELECT \"name\", \"type\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY \"name\"");
        assertAnswer("Statement executed successfully.", "SET va_t6 = '10:00:00'::TIME(6)");
        engine.execute("SHOW VARIABLES LIKE 'VA_T6'");
        assertAnswer("VA_T6|time|10:00:00.000000000",
            "SELECT \"name\", \"type\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    /** A TIMESTAMP_LTZ variable is listed as its instant in UTC, whatever the session's time zone. */
    @Test
    public void aLocalTimestampListsItsInstantInUtc() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            engine.execute("SET va_l3 = '2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(3)");
            engine.execute("SHOW VARIABLES LIKE 'VA_L3'");
            assertAnswer("VA_L3|timestamp_ltz|2024-01-02 11:04:05.123000000Z",
                "SELECT \"name\", \"type\", \"value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
