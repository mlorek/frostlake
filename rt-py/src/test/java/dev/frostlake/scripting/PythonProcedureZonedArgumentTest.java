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
 * A TIMESTAMP_LTZ argument of a Python procedure reaches the handler as a {@code datetime} without a zone, at the
 * session's wall clock for that instant, and a TIMESTAMP_TZ one with its own offset. The zone is set here, since
 * what the handler reads turns on it (live-verified under America/Los_Angeles).
 */
public class PythonProcedureZonedArgumentTest extends MeasuredCellsTestSupport {

    private void procedure(final String name, final String type) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "(a " + type + ") RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\n"
            + "def run(session, a):\n    return type(a).__name__ + ':' + repr(a)\n$$");
    }

    @Test
    public void aZonedTimestampIsADatetimeOfTheSessionsWallClockOrOfItsOwnOffset() {
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            procedure("pz", "TIMESTAMP_LTZ");
            assertAnswer("datetime:datetime.datetime(2024, 1, 31, 2, 11, 12)",
                "CALL pz('2024-01-31 10:11:12 +00:00')");
            procedure("pw", "TIMESTAMP_TZ");
            assertAnswer("datetime:datetime.datetime(2024, 1, 31, 10, 11, 12, "
                + "tzinfo=datetime.timezone(datetime.timedelta(seconds=7200)))",
                "CALL pw('2024-01-31 10:11:12 +02:00')");
        } finally {
            answer("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
