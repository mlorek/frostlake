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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VARIANT holding a NUMBER is an epoch in SECONDS - never unit-detected, a fraction kept - and becomes
 * a timestamp as that instant in the session's zone: an NTZ is the session zone's wall clock, an LTZ and
 * a TZ carry the session's offset, through the TO_TIMESTAMP functions and the casts alike. A plain
 * NUMBER keeps the UTC wall clock and a VARIANT string is read as text. A VARIANT number is no DATE or
 * TIME, and a VARIANT source takes no scale or format argument. Every cell is live-verified.
 */
public class VariantEpochTimestampTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** Under Los Angeles, a VARIANT number is the session zone's wall clock or offset, in seconds. */
    @Test
    public void aVariantNumberIsAnInstantInTheSessionZone() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT TO_TIMESTAMP(PARSE_JSON('1579046400'))::VARCHAR"));
            assertEquals("52007-12-27 16:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400000'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000 -0800",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_LTZ::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000 -0800",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_TZ::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.500",
                rows("SELECT PARSE_JSON('1579046400.5')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("1969-12-30 16:00:00.000",
                rows("SELECT PARSE_JSON('-86400')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.500",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400.5'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_NTZ(3)::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000 -0800",
                rows("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('1579046400'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000 -0800",
                rows("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('1579046400'))::VARCHAR"));
            assertEquals("true",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_NTZ = TO_TIMESTAMP_NTZ('2020-01-14 16:00:00')"));
            assertEquals("2001-09-08 18:46:40.000",
                rows("SELECT PARSE_JSON('1e9')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("1970-12-31 16:00:00.000, 2969-05-02 16:59:59.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('31536000'))::VARCHAR, TO_TIMESTAMP_NTZ(PARSE_JSON('31535999999'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_NTZ(0)::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(TO_VARIANT(1579046400))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.123",
                rows("SELECT TO_TIMESTAMP_NTZ(TO_VARIANT(1579046400.123))::VARCHAR"));
            assertEquals("2017-07-13 19:40:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1.5e9'))::VARCHAR"));
            assertEquals("2020-01-14 16:00:00.000 -0800",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_LTZ(3)::VARCHAR"));
            assertEquals("null",
                rows("SELECT PARSE_JSON('null')::TIMESTAMP_NTZ"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** A VARIANT number is no DATE or TIME, a boolean or a container no timestamp, and TRY_CAST refuses a VARIANT. */
    @Test
    public void aVariantNumberIsNoDateOrTime() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertRefused("SELECT PARSE_JSON('1579046400')::DATE",
                "Failed to cast variant value 1579046400 to DATE");
            assertRefused("SELECT PARSE_JSON('1579046400')::TIME",
                "Failed to cast variant value 1579046400 to TIME");
            assertRefused("SELECT TO_DATE(PARSE_JSON('1579046400'))",
                "Failed to cast variant value 1579046400 to DATE");
            assertRefused("SELECT TO_TIME(PARSE_JSON('1579046400'))",
                "Failed to cast variant value 1579046400 to TIME");
            assertRefused("SELECT PARSE_JSON('1579046400')::TIME(3)",
                "Failed to cast variant value 1579046400 to TIME");
            assertRefused("SELECT TIME(PARSE_JSON('1579046400'))",
                "Failed to cast variant value 1579046400 to TIME");
            assertRefused("SELECT PARSE_JSON('true')::TIMESTAMP_NTZ",
                "Failed to cast variant value true to TIMESTAMP_NTZ");
            assertRefused("SELECT PARSE_JSON('[1]')::TIMESTAMP_NTZ",
                "Failed to cast variant value [1] to TIMESTAMP_NTZ");
            assertRefused("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('{\"a\":1}'))",
                "Failed to cast variant value {\"a\":1} to TIMESTAMP_NTZ");
            assertRefused("SELECT PARSE_JSON('true')::TIMESTAMP_LTZ",
                "Failed to cast variant value true to TIMESTAMP_LTZ");
            assertRefused("SELECT TRY_CAST(PARSE_JSON('1579046400') AS TIMESTAMP_NTZ)",
                "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types VARIANT and TIMESTAMP_NTZ(9)");
            assertRefused("SELECT TRY_TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'))",
                "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types VARIANT and TIMESTAMP_NTZ(9)");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** A VARIANT source takes no second argument, a scale or a format alike. */
    @Test
    public void aVariantSourceTakesNoSecondArgument() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertRefused("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'), 3)::VARCHAR",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'), 3)] expected 1, got 2");
            assertRefused("SELECT TO_TIMESTAMP_LTZ(PARSE_JSON('1579046400'), 3)",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP_LTZ(PARSE_JSON('1579046400'), 3)] expected 1, got 2");
            assertRefused("SELECT TO_TIMESTAMP_TZ(PARSE_JSON('1579046400'), 3)",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP_TZ(PARSE_JSON('1579046400'), 3)] expected 1, got 2");
            assertRefused("SELECT TO_TIMESTAMP(PARSE_JSON('1579046400'), 3)",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP(PARSE_JSON('1579046400'), 3)] expected 1, got 2");
            assertRefused("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('\"2020-01-15 10:00:00\"'), 'YYYY-MM-DD HH24:MI:SS')::VARCHAR",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP_NTZ(PARSE_JSON('\"2020-01-15 10:00:00\"'), 'YYYY-MM-DD HH24:MI:SS')] expected 1, got 2");
            assertRefused("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('\"1579046400\"'), 3)::VARCHAR",
                "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_TIMESTAMP_NTZ(PARSE_JSON('\"1579046400\"'), 3)] expected 1, got 2");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** A VARIANT string, a plain NUMBER and the DATE alias read as ever: text, and the UTC wall clock. */
    @Test
    public void textAndPlainNumbersKeepTheirReading() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        try {
            assertEquals("2020-01-15 00:00:00.000",
                rows("SELECT PARSE_JSON('\"1579046400\"')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("2020-01-15 00:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(1579046400)::VARCHAR"));
            assertEquals("2020-01-15",
                rows("SELECT DATE(PARSE_JSON('1579046400'))"));
            assertEquals("2020-01-15",
                rows("SELECT TO_DATE(PARSE_JSON('\"2020-01-15\"'))"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** The same VARIANT number under UTC and under Tokyo follows each zone. */
    @Test
    public void theWallClockFollowsTheSessionZone() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        try {
            assertEquals("2020-01-15 00:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'))::VARCHAR"));
            assertEquals("2020-01-15 00:00:00.000",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_NTZ::VARCHAR"));
            assertEquals("2020-01-15 00:00:00.000 Z",
                rows("SELECT PARSE_JSON('1579046400')::TIMESTAMP_LTZ::VARCHAR"));
            assertRefused("SELECT PARSE_JSON('1579046400')::DATE",
                "Failed to cast variant value 1579046400 to DATE");
            assertRefused("SELECT PARSE_JSON('1579046400')::TIME",
                "Failed to cast variant value 1579046400 to TIME");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        try {
            assertEquals("2020-01-15 09:00:00.000",
                rows("SELECT TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400'))::VARCHAR"));
            assertRefused("SELECT PARSE_JSON('1579046400')::DATE",
                "Failed to cast variant value 1579046400 to DATE");
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
