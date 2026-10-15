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
 * The ISO-week spellings, as the account reads them (live-verified): DATE_PART and EXTRACT read {@code week_iso},
 * {@code yearofweekiso} and {@code yearofweek_iso} and refuse {@code weekofyeariso} and {@code weekofyear_iso}, and
 * DATE_TRUNC, TRUNC, LAST_DAY, DATEADD, DATEDIFF and TIME_SLICE read none of them — though the documented table of
 * date and time parts lists them for DATE_TRUNC and LAST_DAY, the account refuses each.
 */
public class IsoWeekPartSpellingTest extends MeasuredCellsTestSupport {

    private static final String D = "'2024-02-29'::DATE";

    private static String invalidPart(final String word, final String function) {
        return "REFUSED SQL compilation error: | invalid value [" + word + "] for parameter '" + function
            + " date/time part'";
    }

    private static String notAComponent(final String word, final String function) {
        return "REFUSED SQL compilation error: ['" + word + "'] is not a valid date/time component for function "
            + function + ".";
    }

    @Test
    public void datePartReadsTheIsoWeekAndItsYear() {
        assertCells(new String[][] {
            {"SELECT DATE_PART(week_iso, " + D + "), EXTRACT(week_iso FROM " + D + ")", "9|9"},
            {"SELECT DATE_PART(yearofweekiso, " + D + "), DATE_PART(yearofweek_iso, " + D + ")", "2024|2024"},
            {"SELECT DATE_PART(weekofyeariso, " + D + ")", invalidPart("WEEKOFYEARISO", "DATE_PART")},
            {"SELECT DATE_PART(weekofyear_iso, " + D + ")", invalidPart("WEEKOFYEAR_ISO", "DATE_PART")},
            {"SELECT EXTRACT(weekofyeariso FROM " + D + ")", invalidPart("weekofyeariso", "EXTRACT")},
        });
    }

    @Test
    public void theTruncatingAndIntervalFunctionsReadNoIsoWeek() {
        assertCells(new String[][] {
            {"SELECT DATE_TRUNC('week_iso', " + D + ")", notAComponent("week_iso", "DATE_TRUNC")},
            {"SELECT DATE_TRUNC('yearofweekiso', " + D + ")", notAComponent("yearofweekiso", "DATE_TRUNC")},
            {"SELECT DATE_TRUNC('weekiso', " + D + ")", notAComponent("weekiso", "DATE_TRUNC")},
            {"SELECT DATE_TRUNC(week_iso, " + D + ")", notAComponent("WEEK_ISO", "DATE_TRUNC")},
            {"SELECT TRUNC(" + D + ", 'week_iso')", notAComponent("week_iso", "TRUNC")},
            {"SELECT LAST_DAY(" + D + ", 'week_iso')", notAComponent("week_iso", "LAST_DAY")},
            {"SELECT LAST_DAY(" + D + ", 'yearofweekiso')", notAComponent("yearofweekiso", "LAST_DAY")},
            {"SELECT DATEADD('week_iso', 1, " + D + ")", notAComponent("week_iso", "DATEADD")},
            {"SELECT DATEDIFF('week_iso', " + D + ", " + D + ")", notAComponent("week_iso", "DATEDIFF")},
            {"SELECT TIME_SLICE(" + D + ", 1, 'week_iso')", notAComponent("week_iso", "TIME_SLICE")},
        });
    }
}
