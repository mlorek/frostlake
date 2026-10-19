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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A JavaScript handler reads the session's TIMEZONE as it does on the account: {@code Intl} tells the zone by the
 * id the account's engine keeps for it, a date names the zone in full, standard or daylight, the locale is US
 * English, and a date written without a zone is read in the session's. Every cell is live-verified.
 */
public class JavaScriptZoneNamesTest extends BaseDatabaseTest {

    private static final String TRY = "function t(f){try{return String(f())}catch(e){return 'THREW '+(e&&e.name)+': '"
        + "+(e&&e.message)+' / '+(typeof e)}}\n";

    /** What a handler tells about its zone and locale. */
    private static final String ZONE_BODY = TRY + """
        return [t(function(){ return Intl.DateTimeFormat().resolvedOptions().timeZone; }),
         t(function(){ return new Date(0).toString(); }),
         t(function(){ return new Date(1690000000000).toString(); }),
         t(function(){ return new Date(1700000000000).toString(); }),
         t(function(){ return new Date(0).toTimeString(); }),
         t(function(){ return new Date(1690000000000).toLocaleString(); }),
         t(function(){ return new Date(0).getTimezoneOffset(); }),
         t(function(){ return Intl.DateTimeFormat().resolvedOptions().locale; }),
         t(function(){ return new Intl.NumberFormat().resolvedOptions().locale; }),
         t(function(){ return (1234.5).toLocaleString(); }),
         t(function(){ return new Date(1690000000000).toLocaleDateString(); }),
         t(function(){ return new Date(1690000000000).toLocaleTimeString(); }),
         t(function(){ return new Intl.DateTimeFormat('en-US', {timeZoneName: 'long'}).format(new Date(0)); }),
         t(function(){ return new Intl.DateTimeFormat('en-US', {timeZoneName: 'short'}).format(new Date(0)); }),
         t(function(){ return new Date(2023, 6, 22, 12).getTime(); }),
         t(function(){ return String(new Date('2023-07-22T12:00:00')); })].join(' ;; ');""";

    /** How a handler reads a date from a text. */
    private static final String DATE_BODY = TRY + """
        return [t(function(){ return new Date('Jul 22 2023 12:00:00').getTime(); }),
         t(function(){ return new Date('2023/07/22 12:00:00').getTime(); }),
         t(function(){ return new Date('2023-07-22 12:00:00').getTime(); }),
         t(function(){ return new Date('2023-07-22').getTime(); }),
         t(function(){ return new Date('2023-07-22T12:00:00').getTime(); }),
         t(function(){ return new Date('22 Jul 2023').getTime(); }),
         t(function(){ return Date.parse('July 22, 2023 12:00:00'); }),
         t(function(){ return new Date('2023-07-22T12:00').getTime(); }),
         t(function(){ return new Date('Sat Jul 22 2023 12:00:00 GMT+0900').getTime(); }),
         t(function(){ return new Date('2023-07-22 12:00:00.123').getTime(); }),
         t(function(){ return new Date('7/22/2023').getTime(); }),
         t(function(){ return new Date('2023-7-22').getTime(); }),
         t(function(){ return new Date('2023-07-22T12:00:00Z').getTime(); }),
         t(function(){ return new Date('20230722').getTime(); }),
         t(function(){ return new Date('Jul 22 2023').toString(); }),
         t(function(){ return new Date(2023, 6, 22).getTime(); }),
         t(function(){ return Date(); }).length > 20,
         t(function(){ return typeof Date(); })].join(' ;; ');""";

    /** A session zone, and what a handler tells in it. */
    private static final String[][] ZONES = {
            {"UTC",
                "UTC ;; Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time) ;; Sat Jul 22 2023 04:26:40 GMT+0000 (Coordinated Universal Time) ;; Tue Nov 14 2023 22:13:20 GMT+0000 (Coordinated Universal Time) ;; 00:00:00 GMT+0000 (Coordinated Universal Time) ;; 7/22/2023, 4:26:40 AM ;; 0 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 4:26:40 AM ;; 1/1/1970, Coordinated Universal Time ;; 1/1/1970, UTC ;; 1690027200000 ;; Sat Jul 22 2023 12:00:00 GMT+0000 (Coordinated Universal Time)"},
            {"Etc/UTC",
                "UTC ;; Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time) ;; Sat Jul 22 2023 04:26:40 GMT+0000 (Coordinated Universal Time) ;; Tue Nov 14 2023 22:13:20 GMT+0000 (Coordinated Universal Time) ;; 00:00:00 GMT+0000 (Coordinated Universal Time) ;; 7/22/2023, 4:26:40 AM ;; 0 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 4:26:40 AM ;; 1/1/1970, Coordinated Universal Time ;; 1/1/1970, UTC ;; 1690027200000 ;; Sat Jul 22 2023 12:00:00 GMT+0000 (Coordinated Universal Time)"},
            {"Europe/London",
                "Europe/London ;; Thu Jan 01 1970 01:00:00 GMT+0100 (Greenwich Mean Time) ;; Sat Jul 22 2023 05:26:40 GMT+0100 (British Summer Time) ;; Tue Nov 14 2023 22:13:20 GMT+0000 (Greenwich Mean Time) ;; 01:00:00 GMT+0100 (Greenwich Mean Time) ;; 7/22/2023, 5:26:40 AM ;; -60 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 5:26:40 AM ;; 1/1/1970, GMT+01:00 ;; 1/1/1970, GMT+1 ;; 1690023600000 ;; Sat Jul 22 2023 12:00:00 GMT+0100 (British Summer Time)"},
            {"America/New_York",
                "America/New_York ;; Wed Dec 31 1969 19:00:00 GMT-0500 (Eastern Standard Time) ;; Sat Jul 22 2023 00:26:40 GMT-0400 (Eastern Daylight Time) ;; Tue Nov 14 2023 17:13:20 GMT-0500 (Eastern Standard Time) ;; 19:00:00 GMT-0500 (Eastern Standard Time) ;; 7/22/2023, 12:26:40 AM ;; 300 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 12:26:40 AM ;; 12/31/1969, Eastern Standard Time ;; 12/31/1969, EST ;; 1690041600000 ;; Sat Jul 22 2023 12:00:00 GMT-0400 (Eastern Daylight Time)"},
            {"Asia/Tokyo",
                "Asia/Tokyo ;; Thu Jan 01 1970 09:00:00 GMT+0900 (Japan Standard Time) ;; Sat Jul 22 2023 13:26:40 GMT+0900 (Japan Standard Time) ;; Wed Nov 15 2023 07:13:20 GMT+0900 (Japan Standard Time) ;; 09:00:00 GMT+0900 (Japan Standard Time) ;; 7/22/2023, 1:26:40 PM ;; -540 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 1:26:40 PM ;; 1/1/1970, Japan Standard Time ;; 1/1/1970, GMT+9 ;; 1689994800000 ;; Sat Jul 22 2023 12:00:00 GMT+0900 (Japan Standard Time)"},
            {"America/Los_Angeles",
                "America/Los_Angeles ;; Wed Dec 31 1969 16:00:00 GMT-0800 (Pacific Standard Time) ;; Fri Jul 21 2023 21:26:40 GMT-0700 (Pacific Daylight Time) ;; Tue Nov 14 2023 14:13:20 GMT-0800 (Pacific Standard Time) ;; 16:00:00 GMT-0800 (Pacific Standard Time) ;; 7/21/2023, 9:26:40 PM ;; 480 ;; en-US ;; en-US ;; 1,234.5 ;; 7/21/2023 ;; 9:26:40 PM ;; 12/31/1969, Pacific Standard Time ;; 12/31/1969, PST ;; 1690052400000 ;; Sat Jul 22 2023 12:00:00 GMT-0700 (Pacific Daylight Time)"},
            {"Europe/Warsaw",
                "Europe/Warsaw ;; Thu Jan 01 1970 01:00:00 GMT+0100 (Central European Standard Time) ;; Sat Jul 22 2023 06:26:40 GMT+0200 (Central European Summer Time) ;; Tue Nov 14 2023 23:13:20 GMT+0100 (Central European Standard Time) ;; 01:00:00 GMT+0100 (Central European Standard Time) ;; 7/22/2023, 6:26:40 AM ;; -60 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 6:26:40 AM ;; 1/1/1970, Central European Standard Time ;; 1/1/1970, GMT+1 ;; 1690020000000 ;; Sat Jul 22 2023 12:00:00 GMT+0200 (Central European Summer Time)"},
            {"Australia/Sydney",
                "Australia/Sydney ;; Thu Jan 01 1970 10:00:00 GMT+1000 (Australian Eastern Standard Time) ;; Sat Jul 22 2023 14:26:40 GMT+1000 (Australian Eastern Standard Time) ;; Wed Nov 15 2023 09:13:20 GMT+1100 (Australian Eastern Daylight Time) ;; 10:00:00 GMT+1000 (Australian Eastern Standard Time) ;; 7/22/2023, 2:26:40 PM ;; -600 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 2:26:40 PM ;; 1/1/1970, Australian Eastern Standard Time ;; 1/1/1970, GMT+10 ;; 1689991200000 ;; Sat Jul 22 2023 12:00:00 GMT+1000 (Australian Eastern Standard Time)"},
            {"Asia/Kolkata",
                "Asia/Calcutta ;; Thu Jan 01 1970 05:30:00 GMT+0530 (India Standard Time) ;; Sat Jul 22 2023 09:56:40 GMT+0530 (India Standard Time) ;; Wed Nov 15 2023 03:43:20 GMT+0530 (India Standard Time) ;; 05:30:00 GMT+0530 (India Standard Time) ;; 7/22/2023, 9:56:40 AM ;; -330 ;; en-US ;; en-US ;; 1,234.5 ;; 7/22/2023 ;; 9:56:40 AM ;; 1/1/1970, India Standard Time ;; 1/1/1970, GMT+5:30 ;; 1690007400000 ;; Sat Jul 22 2023 12:00:00 GMT+0530 (India Standard Time)"},
            {"Etc/GMT+5",
                "Etc/GMT+5 ;; Wed Dec 31 1969 19:00:00 GMT-0500 (GMT-05:00) ;; Fri Jul 21 2023 23:26:40 GMT-0500 (GMT-05:00) ;; Tue Nov 14 2023 17:13:20 GMT-0500 (GMT-05:00) ;; 19:00:00 GMT-0500 (GMT-05:00) ;; 7/21/2023, 11:26:40 PM ;; 300 ;; en-US ;; en-US ;; 1,234.5 ;; 7/21/2023 ;; 11:26:40 PM ;; 12/31/1969, GMT-05:00 ;; 12/31/1969, GMT-5 ;; 1690045200000 ;; Sat Jul 22 2023 12:00:00 GMT-0500 (GMT-05:00)"},
    };

    /** A session zone, and the dates a handler reads in it. */
    private static final String[][] DATES = {
            {"Asia/Tokyo",
                "1689994800000 ;; 1689994800000 ;; 1689994800000 ;; 1689984000000 ;; 1689994800000 ;; 1689951600000 ;; 1689994800000 ;; 1689994800000 ;; 1689994800000 ;; 1689994800123 ;; 1689951600000 ;; 1689951600000 ;; 1690027200000 ;; NaN ;; Sat Jul 22 2023 00:00:00 GMT+0900 (Japan Standard Time) ;; 1689951600000 ;; true ;; string"},
            {"America/New_York",
                "1690041600000 ;; 1690041600000 ;; 1690041600000 ;; 1689984000000 ;; 1690041600000 ;; 1689998400000 ;; 1690041600000 ;; 1690041600000 ;; 1689994800000 ;; 1690041600123 ;; 1689998400000 ;; 1689998400000 ;; 1690027200000 ;; NaN ;; Sat Jul 22 2023 00:00:00 GMT-0400 (Eastern Daylight Time) ;; 1689998400000 ;; true ;; string"},
    };

    /** A zone id, and the id the account's engine keeps for it. */
    private static final String[][] IDS = {
            {"Asia/Calcutta", "Asia/Calcutta"},
            {"Asia/Katmandu", "Asia/Katmandu"},
            {"Europe/Kiev", "Europe/Kiev"},
            {"Asia/Saigon", "Asia/Saigon"},
            {"America/Buenos_Aires", "America/Buenos_Aires"},
            {"UTC", "UTC"},
            {"Asia/Rangoon", "Asia/Rangoon"},
            {"Atlantic/Faeroe", "Atlantic/Faeroe"},
            {"Africa/Asmera", "Africa/Asmera"},
            {"Pacific/Truk", "Pacific/Truk"},
            {"Pacific/Ponape", "Pacific/Ponape"},
            {"America/Indianapolis", "America/Indianapolis"},
            {"America/Louisville", "America/Louisville"},
            {"Asia/Thimphu", "Asia/Thimphu"},
            {"Asia/Ulaanbaatar", "Asia/Ulaanbaatar"},
            {"Asia/Macau", "Asia/Macau"},
            {"Asia/Dhaka", "Asia/Dhaka"},
            {"Europe/Uzhgorod", "Europe/Uzhgorod"},
            {"America/Godthab", "America/Godthab"},
            {"Pacific/Enderbury", "Pacific/Enderbury"},
            {"Australia/Sydney", "Australia/Sydney"},
            {"Asia/Tokyo", "Asia/Tokyo"},
            {"Asia/Shanghai", "Asia/Shanghai"},
            {"Asia/Singapore", "Asia/Singapore"},
            {"America/Toronto", "America/Toronto"},
            {"Europe/Istanbul", "Europe/Istanbul"},
            {"EST5EDT", "EST5EDT"},
            {"CET", "CET"},
            {"MET", "MET"},
            {"EET", "EET"},
            {"WET", "WET"},
            {"PST8PDT", "PST8PDT"},
            {"Etc/GMT+5", "Etc/GMT+5"},
            {"Etc/GMT-14", "Etc/GMT-14"},
            {"America/Ciudad_Juarez", "America/Ciudad_Juarez"},
            {"Europe/Amsterdam", "Europe/Amsterdam"},
            {"Europe/Brussels", "Europe/Brussels"},
            {"Europe/Copenhagen", "Europe/Copenhagen"},
            {"Europe/Berlin", "Europe/Berlin"},
            {"Europe/Oslo", "Europe/Oslo"},
            {"Europe/Stockholm", "Europe/Stockholm"},
            {"Arctic/Longyearbyen", "Arctic/Longyearbyen"},
            {"Atlantic/Reykjavik", "Atlantic/Reykjavik"},
            {"Africa/Abidjan", "Africa/Abidjan"},
            {"Asia/Bahrain", "Asia/Bahrain"},
            {"Asia/Qatar", "Asia/Qatar"},
            {"Asia/Kuwait", "Asia/Kuwait"},
            {"Asia/Riyadh", "Asia/Riyadh"},
            {"America/Montevideo", "America/Montevideo"},
            {"America/Panama", "America/Panama"},
            {"Antarctica/McMurdo", "Antarctica/McMurdo"},
            {"Pacific/Auckland", "Pacific/Auckland"},
    };

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void assertInEachZone(final String function, final String[][] zones) {
        try {
            for (final String[] zone : zones) {
                engine.execute("ALTER SESSION SET TIMEZONE = '" + zone[0] + "'");
                final String[] expected = zone[1].split(" ;; ", -1);
                final String[] cells = cell("SELECT " + function + "()").split(" ;; ", -1);
                assertEquals(expected.length, cells.length, zone[0]);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], cells[i], zone[0] + " cell " + i);
                }
            }
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void aHandlerTellsTheSessionZoneAsTheAccountDoes() {
        engine.execute("CREATE OR REPLACE FUNCTION zn() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + ZONE_BODY + "$$");
        assertInEachZone("zn", ZONES);
    }

    @Test
    public void aDateWrittenWithoutAZoneIsReadInTheSessions() {
        engine.execute("CREATE OR REPLACE FUNCTION zd() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + DATE_BODY + "$$");
        assertInEachZone("zd", DATES);
    }

    @Test
    public void aZoneIsToldByTheIdTheAccountsEngineKeeps() {
        final StringBuilder list = new StringBuilder();
        for (final String[] id : IDS) {
            list.append(list.length() > 0 ? "," : "").append('\'').append(id[0]).append('\'');
        }
        engine.execute("CREATE OR REPLACE FUNCTION zi() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$return [" + list
            + "].map(function (z) { return new Intl.DateTimeFormat('en-US', {timeZone: z}).resolvedOptions().timeZone; }).join();$$");
        final String[] cells = cell("SELECT zi()").split(",", -1);
        assertEquals(IDS.length, cells.length);
        for (int i = 0; i < IDS.length; i++) {
            assertEquals(IDS[i][1], cells[i], IDS[i][0]);
        }
    }
}
