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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TIME synonym, which the account resolves among one-argument overloads rather than as TO_TIME:
 * a text reads as TO_TIME reads it, a DATE is its midnight, a timestamp its wall clock, a NUMBER or a
 * VARIANT that holds no time fails as a VARIANT cast, a TIME is refused on its way to TIMESTAMP_LTZ, a
 * BINARY matches nothing, and no format is taken. Every cell is live-verified.
 */
public class TimeFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ct (d DATE, bn BINARY, o OBJECT, b BOOLEAN, f FLOAT, n NUMBER(10,2), "
            + "v VARCHAR, ts TIMESTAMP_NTZ, tm TIME, va VARIANT, a ARRAY)");
        engine.execute("INSERT INTO ct SELECT '2020-01-02', TO_BINARY('61'), OBJECT_CONSTRUCT('a',1), TRUE, 1.5, 2.25, "
            + "'10:11:12', '2020-01-02 10:11:12', '10:11:12', PARSE_JSON('1.5'), ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE ct3 (t3 TIME(3), t0 TIME(0), ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
        engine.execute("INSERT INTO ct3 SELECT '10:11:12.345', '10:11:12', '2020-01-02 10:11:12', "
            + "'2020-01-02 10:11:12 +01:00'");
    }

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aTextReadsAsToTimeReadsIt() {
        assertEquals("10:00:00", text("SELECT TIME('10:00:00')::VARCHAR"));
        assertEquals("TIME(9)[SB8]", text("SELECT SYSTEM$TYPEOF(TIME('10:00:00'))"));
        assertEquals("10:11:12", text("SELECT TIME(v)::VARCHAR FROM ct"));
        assertRefused("SELECT TIME('abc')", "Time 'abc' is not recognized");
        assertRefused("SELECT TIME(TRUE)", "Time 'true' is not recognized");
        assertRefused("SELECT TIME(b) FROM ct", "Time 'true' is not recognized");
        assertNull(engine.executeQuery("SELECT TIME(NULL)").getRows().get(0).getValue(0));
    }

    @Test
    public void aDateIsItsMidnightAndATimestampItsWallClock() {
        assertEquals("00:00:00", text("SELECT TIME(d)::VARCHAR FROM ct"));
        assertEquals("TIME(9)[SB8]", text("SELECT SYSTEM$TYPEOF(TIME(d)) FROM ct"));
        assertEquals("10:11:12", text("SELECT TIME(ts)::VARCHAR FROM ct"));
        assertEquals("TIME(9)[SB8]", text("SELECT SYSTEM$TYPEOF(TIME(ts)) FROM ct"));
        assertEquals("10:11:12", text("SELECT TIME(ltz)::VARCHAR FROM ct3"));
        assertEquals("10:11:12", text("SELECT TIME(tz)::VARCHAR FROM ct3"));
    }

    @Test
    public void aVariantIsReadForItsTimeOrFailsTheCast() {
        assertEquals("10:00:00", text("SELECT TIME(PARSE_JSON('\"10:00\"'))::VARCHAR"));
        assertEquals("10:00:00", text("SELECT TIME(TO_VARIANT(TO_TIME('10:00:00')))::VARCHAR"));
        assertNull(engine.executeQuery("SELECT TIME(PARSE_JSON('null'))").getRows().get(0).getValue(0));
        assertRefused("SELECT TIME(PARSE_JSON('\"abc\"'))", "Failed to cast variant value \"abc\" to TIME");
        assertRefused("SELECT TIME(PARSE_JSON('{\"a\":1}'))", "Failed to cast variant value {\"a\":1} to TIME");
        assertRefused("SELECT TIME(ARRAY_CONSTRUCT(1,2))", "Failed to cast variant value [1,2] to TIME");
        assertRefused("SELECT TIME(PARSE_JSON('1.5'))", "Failed to cast variant value 1.5 to TIME");
        assertRefused("SELECT TIME(TO_VARIANT(TRUE))", "Failed to cast variant value true to TIME");
        assertRefused("SELECT TIME(TO_VARIANT(1.5::FLOAT))",
            "Failed to cast variant value 1.500000000000000e+00 to TIME");
        assertRefused("SELECT TIME(o) FROM ct", "Failed to cast variant value {\"a\":1} to TIME");
        assertRefused("SELECT TIME(a) FROM ct", "Failed to cast variant value [1] to TIME");
        assertRefused("SELECT TIME(va) FROM ct", "Failed to cast variant value 1.5 to TIME");
    }

    @Test
    public void aNumberGoesThroughAVariant() {
        assertRefused("SELECT TIME(123)", "Failed to cast variant value 123 to TIME");
        assertRefused("SELECT TIME(-1)", "Failed to cast variant value -1 to TIME");
        assertRefused("SELECT TIME(1.5)", "Failed to cast variant value 1.5 to TIME");
        assertRefused("SELECT TIME(1.5::FLOAT)", "Failed to cast variant value 1.500000000000000e+00 to TIME");
        assertRefused("SELECT TIME(n) FROM ct", "Failed to cast variant value 2.25 to TIME");
        assertRefused("SELECT TIME(f) FROM ct", "Failed to cast variant value 1.500000000000000e+00 to TIME");
    }

    @Test
    public void aTimeIsRefusedOnItsWayToTimestampLtz() {
        final String refusal = "SQL compilation error:\nincompatible types: ";
        assertRefused("SELECT TIME(tm) FROM ct", refusal + "[TIME(9)] and [TIMESTAMP_LTZ(9)]");
        assertRefused("SELECT TIME(t3) FROM ct3", refusal + "[TIME(3)] and [TIMESTAMP_LTZ(9)]");
        assertRefused("SELECT TIME(t0) FROM ct3", refusal + "[TIME(0)] and [TIMESTAMP_LTZ(9)]");
        assertRefused("SELECT TIME(TO_TIME('10:00:00'))", refusal + "[TIME(9)] and [TIMESTAMP_LTZ(9)]");
        assertRefused("SELECT TIME(NULL::TIME)", refusal + "[TIME(9)] and [TIMESTAMP_LTZ(9)]");
    }

    @Test
    public void aBinaryMatchesNoOverloadAndNoFormatIsTaken() {
        assertRefused("SELECT TIME(bn) FROM ct",
            "error line 1 at position 7\nInvalid argument types for function 'TIME': (BINARY(8388608))");
        assertRefused("SELECT TIME(X'41')",
            "error line 1 at position 7\nInvalid argument types for function 'TIME': (BINARY(1))");
        assertRefused("SELECT TIME()", "error line 1 at position 7\nInvalid argument types for function 'TIME': ()");
        assertRefused("SELECT TIME(1, 2, 3)",
            "error line 1 at position 7\ntoo many arguments for function [TIME(1, 2, 3)] expected 1, got 3");
        assertRefused("SELECT  TIME('10:00:00', 'HH24:MI:SS')", "error line 1 at position 8\n"
            + "too many arguments for function [TIME('10:00:00', 'HH24:MI:SS')] expected 1, got 2");
    }
}
