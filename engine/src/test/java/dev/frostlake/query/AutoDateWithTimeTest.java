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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * AUTO reads a date followed by a time by the date's own form (live-verified): after an ISO date any ISO time may
 * follow, an hour alone, its minutes, seconds and fraction, a T and a zone among them; after MM/DD/YYYY only
 * HH24:MI:SS, with a one- or two-digit hour, and no fraction, zone or T; after DD-Mon-YYYY no time at all. A text no
 * form reads is not recognized as a date, nor by TO_TIMESTAMP as a timestamp, TO_DATE with the format AUTO written
 * refuses it naming the format, and the TRY_ forms answer NULL; AM and PM are no part of any form.
 */
public class AutoDateWithTimeTest extends MeasuredCellsTestSupport {

    @Test
    public void aDateFormDecidesTheTimeAfterIt() {
        assertCells(new String[][] {
            {"SELECT TO_DATE('05/03/2024 10:00')", "REFUSED Date '05/03/2024 10:00' is not recognized"},
            {"SELECT '05/03/2024 10:00'::DATE", "REFUSED Date '05/03/2024 10:00' is not recognized"},
            {"SELECT TRY_TO_DATE('05/03/2024 10:00')", "null"},
            {"SELECT TO_DATE('03/05/2024 10:00:00')", "2024-03-05"},
            {"SELECT '03/05/2024 10:00:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('03/05/2024 10:00:00')", "2024-03-05"},
            {"SELECT TO_DATE('03/05/2024 10:00:00.123')", "REFUSED Date '03/05/2024 10:00:00.123' is not recognized"},
            {"SELECT '03/05/2024 10:00:00.123'::DATE", "REFUSED Date '03/05/2024 10:00:00.123' is not recognized"},
            {"SELECT TRY_TO_DATE('03/05/2024 10:00:00.123')", "null"},
            {"SELECT TO_DATE('03/05/2024 10')", "REFUSED Date '03/05/2024 10' is not recognized"},
            {"SELECT '03/05/2024 10'::DATE", "REFUSED Date '03/05/2024 10' is not recognized"},
            {"SELECT TRY_TO_DATE('03/05/2024 10')", "null"},
            {"SELECT TO_DATE('2024-03-05 10:00')", "2024-03-05"},
            {"SELECT '2024-03-05 10:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 10:00')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05 10:00:00')", "2024-03-05"},
            {"SELECT '2024-03-05 10:00:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 10:00:00')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05 10:00:00.5')", "2024-03-05"},
            {"SELECT '2024-03-05 10:00:00.5'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 10:00:00.5')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05T10:00')", "2024-03-05"},
            {"SELECT '2024-03-05T10:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05T10:00')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05T10:00:00')", "2024-03-05"},
            {"SELECT '2024-03-05T10:00:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05T10:00:00')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05 10')", "2024-03-05"},
            {"SELECT '2024-03-05 10'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 10')", "2024-03-05"},
            {"SELECT TO_DATE('05-Mar-2024 10:00')", "REFUSED Date '05-Mar-2024 10:00' is not recognized"},
            {"SELECT '05-Mar-2024 10:00'::DATE", "REFUSED Date '05-Mar-2024 10:00' is not recognized"},
            {"SELECT TRY_TO_DATE('05-Mar-2024 10:00')", "null"},
            {"SELECT TO_DATE('05-Mar-2024 10:00:00')", "REFUSED Date '05-Mar-2024 10:00:00' is not recognized"},
            {"SELECT '05-Mar-2024 10:00:00'::DATE", "REFUSED Date '05-Mar-2024 10:00:00' is not recognized"},
            {"SELECT TRY_TO_DATE('05-Mar-2024 10:00:00')", "null"},
            {"SELECT TO_DATE('03/05/2024 10:00:00 +0100')",
                "REFUSED Date '03/05/2024 10:00:00 +0100' is not recognized"},
            {"SELECT '03/05/2024 10:00:00 +0100'::DATE", "REFUSED Date '03/05/2024 10:00:00 +0100' is not recognized"},
            {"SELECT TRY_TO_DATE('03/05/2024 10:00:00 +0100')", "null"},
            {"SELECT TO_DATE('2024-03-05 10:00:00 +01:00')", "2024-03-05"},
            {"SELECT '2024-03-05 10:00:00 +01:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 10:00:00 +01:00')", "2024-03-05"},
            {"SELECT TO_DATE('03/05/2024T10:00:00')", "REFUSED Date '03/05/2024T10:00:00' is not recognized"},
            {"SELECT '03/05/2024T10:00:00'::DATE", "REFUSED Date '03/05/2024T10:00:00' is not recognized"},
            {"SELECT TRY_TO_DATE('03/05/2024T10:00:00')", "null"},
            {"SELECT TO_DATE('2024-03-05 10:00 PM')", "REFUSED Date '2024-03-05 10:00 PM' is not recognized"},
            {"SELECT '2024-03-05 10:00 PM'::DATE", "REFUSED Date '2024-03-05 10:00 PM' is not recognized"},
            {"SELECT TRY_TO_DATE('2024-03-05 10:00 PM')", "null"},
            {"SELECT TO_DATE('03/05/2024 1:00:00')", "2024-03-05"},
            {"SELECT '03/05/2024 1:00:00'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('03/05/2024 1:00:00')", "2024-03-05"},
            {"SELECT TO_DATE('2024-03-05 1:02:03')", "2024-03-05"},
            {"SELECT '2024-03-05 1:02:03'::DATE", "2024-03-05"},
            {"SELECT TRY_TO_DATE('2024-03-05 1:02:03')", "2024-03-05"},
            {"SELECT TO_TIMESTAMP('05/03/2024 10:00')", "REFUSED Timestamp '05/03/2024 10:00' is not recognized"},
            {"SELECT '05/03/2024 10:00'::TIMESTAMP_NTZ", "REFUSED Timestamp '05/03/2024 10:00' is not recognized"},
            {"SELECT DATE('05/03/2024 10:00')", "REFUSED Date '05/03/2024 10:00' is not recognized"},
            {"SELECT TO_DATE('05/03/2024 10:00', 'AUTO')",
                "REFUSED Can't parse '05/03/2024 10:00' as date with format 'AUTO'"},
        });
    }
}
