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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript Date inside a procedure's VARIANT, ARRAY or OBJECT answer, and as a whole VARIANT answer, is the text of
 * a TIMESTAMP_LTZ as the session reads one (live-verified): in the session's zone, in its TIMESTAMP_LTZ_OUTPUT_FORMAT,
 * or its TIMESTAMP_OUTPUT_FORMAT when that is unset - neither the NTZ nor the TZ format applies - and with a zero
 * offset spelled Z. A function's answer keeps the Date a TIMESTAMP_LTZ, which TO_JSON writes the same way.
 */
public class JavaScriptProcedureDateTextTest extends MeasuredCellsTestSupport {

    /** A procedure's and a function's Dates under the output formats and three zones. */
    @Test
    public void dateMembersFollowTheSessionsZoneAndFormats() {
        engine.execute("CREATE OR REPLACE PROCEDURE jdv797() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$\nreturn {t: new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6)), a: [new Date(0)]};\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jdf797() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$\nreturn {t: new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))};\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jtz797() RETURNS TIMESTAMP_TZ LANGUAGE JAVASCRIPT AS $$\nreturn "
            + "new Date(Date.UTC(2024, 6, 2, 3, 4, 5, 6));\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jtzf797() RETURNS TIMESTAMP_TZ LANGUAGE JAVASCRIPT AS $$\nreturn "
            + "new Date(Date.UTC(2024, 6, 2, 3, 4, 5, 6));\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"1969/12/31 16:00:00.000 /0800\"],\"t\":\"2024/01/01 19:04:05.006 /0800\"}"},
            {"SELECT REPLACE((TO_JSON(jdf797()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/01 19:04:05.006 /0800\"}"},
            {"SELECT REPLACE((jdf797():t)::VARCHAR, '-', '/')",
                "2024/01/01 19:04:05.006 /0800"},
            {"SELECT REPLACE((SYSTEM$TYPEOF(jdf797():t))::VARCHAR, '-', '/')",
                "VARIANT[LOB]"},
            {"ALTER SESSION SET TIMESTAMP_OUTPUT_FORMAT = 'YYYY-MM-DD\"T\"HH24:MI:SS'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"1969/12/31T16:00:00\"],\"t\":\"2024/01/01T19:04:05\"}"},
            {"SELECT REPLACE((TO_JSON(jdf797()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/01T19:04:05\"}"},
            {"ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'DD.MM.YYYY HH24:MI'",
                "Statement executed successfully."},
            {"CALL jdv797()",
                "{\"a\":[\"31.12.1969 16:00\"],\"t\":\"01.01.2024 19:04\"}"},
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"31.12.1969 16:00\"],\"t\":\"01.01.2024 19:04\"}"},
            {"SELECT REPLACE((TO_JSON(jdf797()))::VARCHAR, '-', '/')",
                "{\"t\":\"01.01.2024 19:04\"}"},
            {"ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY'",
                "Statement executed successfully."},
            {"CALL jdv797()",
                "{\"a\":[\"31.12.1969 16:00\"],\"t\":\"01.01.2024 19:04\"}"},
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"31.12.1969 16:00\"],\"t\":\"01.01.2024 19:04\"}"},
            {"ALTER SESSION UNSET TIMESTAMP_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jtz797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/07/02 12:04:05.006 +0900"},
            {"SELECT REPLACE((jtzf797())::VARCHAR, '-', '/')",
                "2024/07/02 12:04:05.006 +0900"},
            {"SELECT REPLACE((SYSTEM$TYPEOF(jtzf797()))::VARCHAR, '-', '/')",
                "TIMESTAMP_TZ(9)[SB16]"},
        });
        engine.execute("CALL jdv797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"1970/01/01 09:00:00.000 +0900\"],\"t\":\"2024/01/02 12:04:05.006 +0900\"}"},
            {"ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jtz797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/07/01 20:04:05.006 /0700"},
            {"SELECT REPLACE((jtzf797())::VARCHAR, '-', '/')",
                "2024/07/01 20:04:05.006 /0700"},
            {"SELECT REPLACE((SYSTEM$TYPEOF(jtzf797()))::VARCHAR, '-', '/')",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv797()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"a\":[\"1970/01/01 00:00:00.000 Z\"],\"t\":\"2024/01/02 03:04:05.006 Z\"}"},
            {"SELECT REPLACE((TO_JSON(jdf797()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/02 03:04:05.006 Z\"}"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }

    /** A Date as the whole VARIANT answer, in an ARRAY and in an OBJECT, under other formats and zones. */
    @Test
    public void wholeAnswersAndContainers() {
        engine.execute("CREATE OR REPLACE PROCEDURE jdv799() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$\nreturn {t: new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))};\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jdb799() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$\nreturn new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6));\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jda799() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$\nreturn [new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))];\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE jdo799() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$\nreturn {t: new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))};\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION jdf799() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$\nreturn {t: new "
            + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))};\n$$");
        assertCells(new String[][] {
            {"ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
        });
        engine.execute("CALL jdb799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/01/02 04:04:05.006 +0100"},
        });
        engine.execute("CALL jda799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "[\"2024/01/02 04:04:05.006 +0100\"]"},
        });
        engine.execute("CALL jdo799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"SELECT REPLACE((TO_JSON(jdf799()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006000000 +01:00\"}"},
        });
        engine.execute("CALL jdb799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/01/02 04:04:05.006000000 +01:00"},
        });
        engine.execute("CALL jda799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "[\"2024/01/02 04:04:05.006000000 +01:00\"]"},
        });
        engine.execute("CALL jdo799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006000000 +01:00\"}"},
            {"SELECT REPLACE((TO_JSON(jdf799()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/02 04:04:05.006000000 +01:00\"}"},
            {"ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION SET TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdv799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
        });
        engine.execute("CALL jdb799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/01/02 04:04:05.006 +0100"},
        });
        engine.execute("CALL jda799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "[\"2024/01/02 04:04:05.006 +0100\"]"},
        });
        engine.execute("CALL jdo799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"SELECT REPLACE((TO_JSON(jdf799()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"ALTER SESSION UNSET TIMESTAMP_TZ_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION SET TIMESTAMP_OUTPUT_FORMAT = 'AUTO'",
                "REFUSED SQL compilation error: | invalid value [AUTO] for parameter 'TIMESTAMP_OUTPUT_FORMAT'"},
        });
        engine.execute("CALL jdv799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
        });
        engine.execute("CALL jdb799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/01/02 04:04:05.006 +0100"},
        });
        engine.execute("CALL jda799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "[\"2024/01/02 04:04:05.006 +0100\"]"},
        });
        engine.execute("CALL jdo799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"SELECT REPLACE((TO_JSON(jdf799()))::VARCHAR, '-', '/')",
                "{\"t\":\"2024/01/02 04:04:05.006 +0100\"}"},
            {"ALTER SESSION UNSET TIMESTAMP_OUTPUT_FORMAT",
                "Statement executed successfully."},
            {"ALTER SESSION SET TIMEZONE = 'UTC'",
                "Statement executed successfully."},
        });
        engine.execute("CALL jdb799()");
        assertCells(new String[][] {
            {"SELECT REPLACE($1::VARCHAR, '-', '/') FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "2024/01/02 03:04:05.006 Z"},
            {"ALTER SESSION UNSET TIMEZONE",
                "Statement executed successfully."},
        });
    }
}
