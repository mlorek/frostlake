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
 * The zone a JavaScript handler is told. {@code Intl} names the session's zone as the account's engine knows it:
 * a renamed zone under its older name, every other name of UTC - {@code Etc/GMT}, {@code Greenwich},
 * {@code Zulu}, {@code Universal}, {@code GMT+0} - as {@code UTC}, and a session whose TIMEZONE is {@code GMT}
 * by its offset, {@code +00:00}. A formatter given a zone tells it the same way - {@code Asia/Kolkata} is
 * {@code Asia/Calcutta}, {@code Japan} is {@code Asia/Tokyo}, {@code GMT} is {@code UTC} - whatever case it was
 * written in. Every answer is live-verified.
 */
public class JavaScriptFormatterZoneTest extends MeasuredCellsTestSupport {

    @Test
    public void theSessionsZoneAndAFormatters() {
        try {
            answer(
                "CREATE OR REPLACE FUNCTION nf_z() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return "
                    + "Intl.DateTimeFormat().resolvedOptions().timeZone;'");
            answer(
                "CREATE OR REPLACE FUNCTION nf_zo(z VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'try { return "
                    + "new Intl.DateTimeFormat(\"en-US\", {timeZone: Z}).resolvedOptions().timeZone; } catch (e) { "
                    + "return \"ERR \" + e.message; }'");
            answer(
                "CREATE OR REPLACE FUNCTION nf_zd() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return new "
                    + "Date(0).toString();'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'GMT'");
            assertAnswer(
                "+00:00|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/UTC'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/GMT'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Greenwich'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'GMT0'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Zulu'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UCT'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/Zulu'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/GMT+0'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/GMT-0'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/GMT+5'");
            assertAnswer(
                "Etc/GMT+5|Wed Dec 31 1969 19:00:00 GMT-0500 (GMT-05:00)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/GMT-3'");
            assertAnswer(
                "Etc/GMT-3|Thu Jan 01 1970 03:00:00 GMT+0300 (GMT+03:00)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Europe/London'");
            assertAnswer(
                "Europe/London|Thu Jan 01 1970 01:00:00 GMT+0100 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
            assertAnswer(
                "Asia/Calcutta|Thu Jan 01 1970 05:30:00 GMT+0530 (India Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Asia/Calcutta'");
            assertAnswer(
                "Asia/Calcutta|Thu Jan 01 1970 05:30:00 GMT+0530 (India Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertAnswer(
                "America/Los_Angeles|Wed Dec 31 1969 16:00:00 GMT-0800 (Pacific Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Europe/Kyiv'");
            assertAnswer(
                "Europe/Kiev|Thu Jan 01 1970 03:00:00 GMT+0300 (Eastern European Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Universal'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/Universal'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'GMT+0'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'GMT-0'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/UCT'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Etc/Greenwich'");
            assertAnswer(
                "UTC|Thu Jan 01 1970 00:00:00 GMT+0000 (Greenwich Mean Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
            assertAnswer(
                "Asia/Tokyo|Thu Jan 01 1970 09:00:00 GMT+0900 (Japan Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Japan'");
            assertAnswer(
                "Asia/Tokyo|Thu Jan 01 1970 09:00:00 GMT+0900 (Japan Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'EST'");
            assertAnswer(
                "Etc/GMT+5|Wed Dec 31 1969 19:00:00 GMT-0500 (GMT-05:00)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'EST5EDT'");
            assertAnswer(
                "EST5EDT|Wed Dec 31 1969 19:00:00 GMT-0500 (Eastern Standard Time)",
                "SELECT nf_z(), nf_zd()");
            assertAnswer(
                "Asia/Calcutta",
                "SELECT nf_zo('Asia/Kolkata')");
            assertAnswer(
                "Asia/Calcutta",
                "SELECT nf_zo('Asia/Calcutta')");
            assertAnswer(
                "Asia/Katmandu",
                "SELECT nf_zo('Asia/Kathmandu')");
            assertAnswer(
                "Europe/Kiev",
                "SELECT nf_zo('Europe/Kyiv')");
            assertAnswer(
                "Europe/Kiev",
                "SELECT nf_zo('Europe/Kiev')");
            assertAnswer(
                "Asia/Saigon",
                "SELECT nf_zo('Asia/Ho_Chi_Minh')");
            assertAnswer(
                "America/Buenos_Aires",
                "SELECT nf_zo('America/Argentina/Buenos_Aires')");
            assertAnswer(
                "America/Los_Angeles",
                "SELECT nf_zo('US/Pacific')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('UTC')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('GMT')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('Etc/UTC')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('Etc/GMT')");
            assertAnswer(
                "Etc/GMT+5",
                "SELECT nf_zo('Etc/GMT+5')");
            assertAnswer(
                "Etc/GMT+5",
                "SELECT nf_zo('EST')");
            assertAnswer(
                "Asia/Tokyo",
                "SELECT nf_zo('Japan')");
            assertAnswer(
                "+05:30",
                "SELECT nf_zo('+05:30')");
            assertAnswer(
                "+00:00",
                "SELECT nf_zo('+00:00')");
            assertAnswer(
                "America/Godthab",
                "SELECT nf_zo('America/Nuuk')");
            assertAnswer(
                "Asia/Rangoon",
                "SELECT nf_zo('Asia/Yangon')");
            assertAnswer(
                "Pacific/Enderbury",
                "SELECT nf_zo('Pacific/Kanton')");
            assertAnswer(
                "Europe/London",
                "SELECT nf_zo('Europe/London')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('utc')");
            assertAnswer(
                "Asia/Calcutta",
                "SELECT nf_zo('asia/kolkata')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('Zulu')");
            assertAnswer(
                "UTC",
                "SELECT nf_zo('Greenwich')");
            assertAnswer(
                "Asia/Saigon",
                "SELECT nf_zo('Asia/Saigon')");
            assertAnswer(
                "America/Godthab",
                "SELECT nf_zo('America/Godthab')");
            assertAnswer(
                "Europe/London",
                "SELECT nf_zo('Europe/Belfast')");
            assertAnswer(
                "Asia/Shanghai",
                "SELECT nf_zo('PRC')");
            assertAnswer(
                "Europe/Istanbul",
                "SELECT nf_zo('Turkey')");
            assertAnswer(
                "Etc/GMT+7",
                "SELECT nf_zo('MST')");
            assertAnswer(
                "Etc/GMT+10",
                "SELECT nf_zo('HST')");
            assertAnswer(
                "CET",
                "SELECT nf_zo('CET')");
            assertAnswer(
                "America/Indianapolis",
                "SELECT nf_zo('America/Indiana/Indianapolis')");
        } finally {
            answer(
                "ALTER SESSION UNSET TIMEZONE");
        }
    }
}
