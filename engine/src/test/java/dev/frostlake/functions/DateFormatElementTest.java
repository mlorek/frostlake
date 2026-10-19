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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The newer date and time format elements, which TO_CHAR writes and TO_DATE, TO_TIMESTAMP and TO_TIME read
 * (live-verified): H24, H12, MO, ME and S are the hour, the month, the minute and the second unpadded, P the
 * meridiem's letter, UUUU the year, FF0 no fraction digits, and Y the year's last two digits unpadded — so YYY is YY
 * then Y.
 */
public class DateFormatElementTest extends MeasuredCellsTestSupport {

    private static final String TS = "'2005-04-05 17:08:09.123456789'::TIMESTAMP_NTZ";

    @Test
    public void toCharWritesTheNewerElements() {
        assertCells(new String[][] {
            {"SELECT TO_CHAR(" + TS + ", 'H24:ME:S') AS a, TO_CHAR(" + TS + ", 'H12 P') AS b, "
                + "TO_CHAR('2005-04-05'::DATE, 'Y/MO/D') AS c, TO_CHAR('2005-04-05'::DATE, 'UUUU') AS d, "
                + "TO_CHAR(" + TS + ", 'SS.FF0') AS e", "17:8:9|5 P|5/4/5|2005|09."},
            {"SELECT TO_CHAR(" + TS + ", 'FF0'), TO_CHAR(" + TS + ", 'FF3')", "|123"},
            {"SELECT TO_CHAR('2005-04-05'::DATE, 'Y'), TO_CHAR('2005-04-05'::DATE, 'YY'), "
                + "TO_CHAR('2005-04-05'::DATE, 'YYY')", "5|05|055"},
            {"SELECT TO_CHAR('2005-04-05 17:08:09'::TIMESTAMP_NTZ, 'H12:MI AM'), "
                + "TO_CHAR('2005-04-05 05:08:09'::TIMESTAMP_NTZ, 'H12 P')", "5:08 PM|5 A"},
            {"SELECT TO_CHAR('2005-04-05 17:08:09'::TIMESTAMP_NTZ, 'HH24:MI:SS') AS ok1, "
                + "TO_CHAR('2005-04-05'::DATE, 'D/MM/YYYY') AS ok2, "
                + "TO_CHAR('2005-04-05 07:08:09'::TIMESTAMP_NTZ, 'H') AS ok3", "17:08:09|5/04/2005|7"},
        });
    }

    @Test
    public void theConversionsReadTheNewerElements() {
        assertCells(new String[][] {
            {"SELECT TO_CHAR(TRY_TO_TIMESTAMP('2005-4-5 7:8:9', 'YYYY-MO-D H24:ME:S'), 'YYYY-MM-DD HH24:MI:SS'), "
                + "TRY_TO_DATE('2005-04-05', 'UUUU-MM-DD'), "
                + "TO_CHAR(TRY_TO_TIMESTAMP('5:08 P', 'H12:MI P'), 'HH24:MI')", "2005-04-05 07:08:09|2005-04-05|17:08"},
            {"SELECT TRY_TO_DATE('2005-4-5', 'YYYY-MO-D'), TRY_TO_TIME('17:08:09', 'H24:ME:S')", "2005-04-05|17:08:09"},
        });
    }
}
