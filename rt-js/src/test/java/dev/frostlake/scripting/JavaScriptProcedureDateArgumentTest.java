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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DATE and a TIMESTAMP argument of a JavaScript procedure reach the handler as a {@code Date}: a date at its
 * midnight and a timestamp without a zone at its wall clock, both in the session's time zone, a zoned timestamp at
 * its own instant, each to the millisecond; a NULL is {@code undefined}. The zone is set here, since the instant
 * a handler reads turns on it (live-verified under America/Los_Angeles).
 */
public class JavaScriptProcedureDateArgumentTest extends MeasuredCellsTestSupport {

    private void procedure(final String name, final String type, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "(a " + type + ") RETURNS VARCHAR LANGUAGE JAVASCRIPT "
            + "AS $$ " + body + " $$");
    }

    @Test
    public void aDateAndATimestampAreDatesInTheSessionsZone() {
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            procedure("jd", "DATE",
                "return (A instanceof Date) + '|' + (A === undefined ? 'undefined' : A.getTime());");
            assertAnswer("true|1706688000000", "CALL jd('2024-01-31')");
            assertAnswer("true|1706688000000", "CALL jd('2024-01-31'::DATE)");
            assertAnswer("false|undefined", "CALL jd(NULL)");
            procedure("jt", "TIMESTAMP_NTZ", "return (A instanceof Date) + '|' + A.getTime() + '|' + A.toISOString();");
            assertAnswer("true|1706724672000|2024-01-31T18:11:12.000Z", "CALL jt('2024-01-31 10:11:12')");
            assertAnswer("true|1706724672123|2024-01-31T18:11:12.123Z", "CALL jt('2024-01-31 10:11:12.123456789')");
            assertAnswer("true|1706688000000|2024-01-31T08:00:00.000Z", "CALL jt('2024-01-31'::DATE)");
            procedure("jz", "TIMESTAMP_LTZ", "return (A instanceof Date) + '|' + A.getTime();");
            assertAnswer("true|1706695872000", "CALL jz('2024-01-31 10:11:12 +00:00')");
        } finally {
            answer("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
