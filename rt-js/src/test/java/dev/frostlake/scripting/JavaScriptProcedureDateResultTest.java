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
 * A {@code Date} a JavaScript procedure returns for a DATE is the date of its instant in the session's time zone,
 * and so is a number of milliseconds; a timestamp without a zone is the session's wall clock at that instant; and
 * inside a VARIANT, an ARRAY or an OBJECT a {@code Date} is the text of its instant in that zone, with the
 * zone's offset. The zone is set here, since the answers turn on it (live-verified under America/Los_Angeles). A
 * JavaScript function is refused an overload that differs from another by optional arguments alone, as a SQL one
 * is.
 */
public class JavaScriptProcedureDateResultTest extends MeasuredCellsTestSupport {

    private void procedure(final String name, final String returns, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS " + returns + " LANGUAGE JAVASCRIPT AS $$ "
            + body + " $$");
    }

    @Test
    public void aDateIsTheDayOfItsInstantInTheSessionsZone() {
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            procedure("jd", "DATE", "return new Date(Date.UTC(2024, 0, 2));");
            assertAnswer("2024-01-01", "CALL jd()");
            procedure("jm", "DATE", "return 5;");
            assertAnswer("1969-12-31", "CALL jm()");
            procedure("jn", "TIMESTAMP_NTZ", "return new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 678));");
            answer("CALL jn()");
            assertAnswer("2024-01-01 19:04:05.678", "SELECT TO_VARCHAR($1, 'YYYY-MM-DD HH24:MI:SS.FF3') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aDateInsideASemiStructuredAnswerIsTheTextOfItsInstantInTheSessionsZone() {
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            procedure("jv", "VARIANT", "return new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 678));");
            answer("CALL jv()");
            assertAnswer("VARCHAR / 2024-01-01 19:04:05.678 -0800", "SELECT TYPEOF($1) || ' / ' || $1::VARCHAR FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            // Summer time has its own offset, and a Date that holds no instant reads as the epoch.
            procedure("ja", "ARRAY", "return [new Date(Date.UTC(2024, 6, 2, 3, 4, 5)), new Date(NaN)];");
            answer("CALL ja()");
            assertAnswer("[\"2024-07-01 20:04:05.000 -0700\",\"1969-12-31 16:00:00.000 -0800\"]",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            procedure("jq", "OBJECT", "return {d: new Date(Date.UTC(2024, 0, 2, 3, 4, 5)), n: null};");
            answer("CALL jq()");
            assertAnswer("{\"d\":\"2024-01-01 19:04:05.000 -0800\",\"n\":null}",
                "SELECT $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            answer("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aJavaScriptFunctionIsRefusedAnOverloadOfOptionalArgumentsAlone() {
        engine.execute("CREATE FUNCTION jo(a FLOAT) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 'one'; $$");
        assertAnswer("REFUSED SQL compilation error: Cannot overload FUNCTION 'JO' as it would cause ambiguous "
            + "FUNCTION overloading.", "CREATE FUNCTION jo(a FLOAT, b FLOAT DEFAULT 0) RETURNS VARCHAR LANGUAGE "
            + "JAVASCRIPT AS $$ return 'two'; $$");
        assertAnswer("one", "SELECT jo(1)");
    }
}
