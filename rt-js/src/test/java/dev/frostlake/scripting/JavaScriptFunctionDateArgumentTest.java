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
 * What a JavaScript function's handler receives for a DATE and a timestamp parameter: a JavaScript Date. A DATE
 * is the midnight that begins it in the session's time zone, a TIMESTAMP_NTZ its wall clock read in that zone,
 * a TIMESTAMP_LTZ and a TIMESTAMP_TZ their own instants - cut to the millisecond - and {@code getHours()}
 * answers in the session's zone. A text and a timestamp become the parameter's type first, so a text that is
 * no date is the call's refusal. A SQL NULL is {@code undefined}. A Date the handler hands back is the DATE or
 * the timestamp it names in the session's zone. A TIME parameter is refused when the function is created. The
 * session's TIMEZONE is set by the tests and unset whatever the assertions make of the statements between.
 * Every answer is live-verified.
 */
public class JavaScriptFunctionDateArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aDateParameterIsADate() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function JD successfully created.",
                "CREATE OR REPLACE FUNCTION jd(d DATE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof D "
                    + "+ '|' + (D instanceof Date) + '|' + (D === undefined ? 'undef' : D.toISOString()) + '|' + (D "
                    + "=== undefined ? '' : D.getFullYear() + '-' + D.getHours()); $$");
            assertAnswer(
                "undefined|false|undef|",
                "SELECT jd(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'JD': (NUMBER(1,0))",
                "SELECT jd(5)");
            assertAnswer(
                "REFUSED Date 'nope' is not recognized",
                "SELECT jd('nope')");
            assertAnswer(
                "Function JN successfully created.",
                "CREATE OR REPLACE FUNCTION jn(d TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "typeof D + '|' + (D instanceof Date) + '|' + (D === undefined ? 'undef' : D.toISOString()) + "
                    + "'|' + (D === undefined ? '' : D.getFullYear() + '-' + D.getHours()); $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'JN': (TIMESTAMP_TZ(9))",
                "SELECT jn('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "Function JL successfully created.",
                "CREATE OR REPLACE FUNCTION jl(d TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "typeof D + '|' + (D instanceof Date) + '|' + (D === undefined ? 'undef' : D.toISOString()) + "
                    + "'|' + (D === undefined ? '' : D.getFullYear() + '-' + D.getHours()); $$");
            assertAnswer(
                "Function JZ successfully created.",
                "CREATE OR REPLACE FUNCTION jz(d TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "typeof D + '|' + (D instanceof Date) + '|' + (D === undefined ? 'undef' : D.toISOString()) + "
                    + "'|' + (D === undefined ? '' : D.getFullYear() + '-' + D.getHours()); $$");
            assertAnswer(
                "REFUSED Language JAVASCRIPT does not support type 'TIME(9)' for argument or return type.",
                "CREATE OR REPLACE FUNCTION jt(d TIME) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return typeof D; "
                    + "$$");
            assertAnswer(
                "Function JTS successfully created.",
                "CREATE OR REPLACE FUNCTION jts(d TIMESTAMP) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "typeof D + '|' + (D instanceof Date) + '|' + (D === undefined ? 'undef' : D.toISOString()) + "
                    + "'|' + (D === undefined ? '' : D.getFullYear() + '-' + D.getHours()); $$");
            assertAnswer(
                "Function RD successfully created.",
                "CREATE OR REPLACE FUNCTION rd(d DATE) RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return D; $$");
            assertAnswer(
                "2024-03-05",
                "SELECT rd('2024-03-05'::DATE)::VARCHAR");
            assertAnswer(
                "Function RN successfully created.",
                "CREATE OR REPLACE FUNCTION rn(d TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ "
                    + "return new Date(D.getTime() + 86400000); $$");
            assertAnswer(
                "2024/03/06 10.11.12.345",
                "SELECT TO_VARCHAR(rn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), 'YYYY/MM/DD HH24.MI.SS.FF3')");
            assertAnswer(
                "Function GT successfully created.",
                "CREATE OR REPLACE FUNCTION gt(d DATE) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return "
                    + "D.getTime(); $$");
            assertAnswer(
                "1709596800000",
                "SELECT gt('2024-03-05'::DATE)::VARCHAR");
            assertAnswer(
                "Function GN successfully created.",
                "CREATE OR REPLACE FUNCTION gn(d TIMESTAMP_NTZ) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return "
                    + "D.getTime(); $$");
            assertAnswer(
                "1709633472345",
                "SELECT gn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)::VARCHAR");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "1709625600000",
                "SELECT gt('2024-03-05'::DATE)::VARCHAR");
            assertAnswer(
                "1709662272345",
                "SELECT gn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ)::VARCHAR");
            assertAnswer(
                "2024-03-05",
                "SELECT rd('2024-03-05'::DATE)::VARCHAR");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void theInstantADateHolds() {
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "Function JD successfully created.",
                "CREATE OR REPLACE FUNCTION jd(d DATE) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return (D "
                    + "instanceof Date) + '|' + D.toISOString() + '|' + D.getTime() + '|' + D.getHours(); $$");
            assertAnswer(
                "Function JN successfully created.",
                "CREATE OR REPLACE FUNCTION jn(d TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "(D instanceof Date) + '|' + D.toISOString() + '|' + D.getTime() + '|' + D.getHours(); $$");
            assertAnswer(
                "Function JL successfully created.",
                "CREATE OR REPLACE FUNCTION jl(d TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "(D instanceof Date) + '|' + D.toISOString() + '|' + D.getTime() + '|' + D.getHours(); $$");
            assertAnswer(
                "Function JZ successfully created.",
                "CREATE OR REPLACE FUNCTION jz(d TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return "
                    + "(D instanceof Date) + '|' + D.toISOString() + '|' + D.getTime() + '|' + D.getHours(); $$");
            assertAnswer(
                "true|2024_03_05T00;00;00.000Z|1709596800000|0",
                "SELECT REPLACE(REPLACE(jd('2024-03-05'::DATE), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T00;00;00.000Z|1709596800000|0",
                "SELECT REPLACE(REPLACE(jd('2024-03-05'), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T00;00;00.000Z|1709596800000|0",
                "SELECT REPLACE(REPLACE(jd('2024-03-05 10:11:12'::TIMESTAMP_NTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T10;11;12.345Z|1709633472345|10",
                "SELECT REPLACE(REPLACE(jn('2024-03-05 10:11:12.345678912'::TIMESTAMP_NTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T00;00;00.000Z|1709596800000|0",
                "SELECT REPLACE(REPLACE(jn('2024-03-05'::DATE), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T10;11;12.000Z|1709633472000|10",
                "SELECT REPLACE(REPLACE(jn('2024-03-05 10:11:12'), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T10;11;12.345Z|1709633472345|10",
                "SELECT REPLACE(REPLACE(jl('2024-03-05 10:11:12.345 +00:00'::TIMESTAMP_LTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T10;11;12.345Z|1709633472345|10",
                "SELECT REPLACE(REPLACE(jl('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T05;11;12.345Z|1709615472345|5",
                "SELECT REPLACE(REPLACE(jz('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ), '-', '_'), ':', ';')");
            assertAnswer(
                "Function RN successfully created.",
                "CREATE OR REPLACE FUNCTION rn(d TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ "
                    + "return new Date(D.getTime() + 86400000); $$");
            assertAnswer(
                "2024/03/06 10.11.12.345",
                "SELECT TO_VARCHAR(rn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), 'YYYY/MM/DD HH24.MI.SS.FF3')");
            assertAnswer(
                "Function RD successfully created.",
                "CREATE OR REPLACE FUNCTION rd(d DATE) RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return D; $$");
            assertAnswer(
                "2024/03/05",
                "SELECT TO_VARCHAR(rd('2024-03-05'::DATE), 'YYYY/MM/DD')");
            assertAnswer(
                "Function RD2 successfully created.",
                "CREATE OR REPLACE FUNCTION rd2(d DATE) RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return new "
                    + "Date(D.getTime() + 36 * 3600000); $$");
            assertAnswer(
                "2024/03/06",
                "SELECT TO_VARCHAR(rd2('2024-03-05'::DATE), 'YYYY/MM/DD')");
            assertAnswer(
                "Function RL successfully created.",
                "CREATE OR REPLACE FUNCTION rl(d TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$ "
                    + "return D; $$");
            assertAnswer(
                "2024/03/05 10.11.12.345 Z",
                "SELECT TO_VARCHAR(rl('2024-03-05 10:11:12.345 +00:00'::TIMESTAMP_LTZ), 'YYYY/MM/DD "
                    + "HH24.MI.SS.FF3 TZHTZM')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "true|2024_03_05T08;00;00.000Z|1709625600000|0",
                "SELECT REPLACE(REPLACE(jd('2024-03-05'::DATE), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T18;11;12.345Z|1709662272345|10",
                "SELECT REPLACE(REPLACE(jn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T10;11;12.345Z|1709633472345|2",
                "SELECT REPLACE(REPLACE(jl('2024-03-05 10:11:12.345 +00:00'::TIMESTAMP_LTZ), '-', '_'), ':', ';')");
            assertAnswer(
                "true|2024_03_05T05;11;12.345Z|1709615472345|21",
                "SELECT REPLACE(REPLACE(jz('2024-03-05 10:11:12.345 +05:00'::TIMESTAMP_TZ), '-', '_'), ':', ';')");
            assertAnswer(
                "2024/03/06 10.11.12.345",
                "SELECT TO_VARCHAR(rn('2024-03-05 10:11:12.345'::TIMESTAMP_NTZ), 'YYYY/MM/DD HH24.MI.SS.FF3')");
            assertAnswer(
                "2024/03/05",
                "SELECT TO_VARCHAR(rd('2024-03-05'::DATE), 'YYYY/MM/DD')");
            assertAnswer(
                "2024/03/06",
                "SELECT TO_VARCHAR(rd2('2024-03-05'::DATE), 'YYYY/MM/DD')");
            assertAnswer(
                "2024/03/05 02.11.12.345 -0800",
                "SELECT TO_VARCHAR(rl('2024-03-05 10:11:12.345 +00:00'::TIMESTAMP_LTZ), 'YYYY/MM/DD "
                    + "HH24.MI.SS.FF3 TZHTZM')");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
