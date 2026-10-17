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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The session's WEEK_START and WEEK_OF_YEAR_POLICY make the week DAYOFWEEK, WEEK, WEEKOFYEAR, YEAROFWEEK,
 * DATE_TRUNC('WEEK'), DATE_PART, EXTRACT, LAST_DAY(…, 'WEEK') and DATEDIFF(WEEK, …) read; the ISO variants,
 * NEXT_DAY and PREVIOUS_DAY do not follow it (live-verified).
 */
public class WeekStartParameterTest extends MeasuredCellsTestSupport {

    private static final String D = "'2020-03-04'::DATE";

    /** Wednesday 2020-03-04 through every function that reads a week, in one row. */
    private static final String WEEK_OF_A_WEDNESDAY = "SELECT DAYOFWEEK(" + D + "), DAYOFWEEKISO(" + D + "), WEEK(" + D
        + "), WEEKOFYEAR(" + D + "), WEEKISO(" + D + "), YEAROFWEEK(" + D + "), YEAROFWEEKISO(" + D
        + "), DATE_TRUNC('WEEK', " + D + "), DATE_PART('dayofweek', " + D + "), EXTRACT(dayofweek FROM " + D
        + "), DATE_PART('week', " + D + "), NEXT_DAY(" + D + ", 'fr'), PREVIOUS_DAY(" + D + ", 'fr'), LAST_DAY(" + D
        + ", 'week'), DATEDIFF(week, '2020-03-01'::DATE, " + D + ")";

    /** The turn of the year 2020 and a Sunday and a Saturday. */
    private static final String YEAR_END = "SELECT DAYOFWEEK('2021-01-01'::DATE), WEEK('2021-01-01'::DATE),"
        + " YEAROFWEEK('2021-01-01'::DATE), WEEK('2020-12-31'::DATE), DATE_TRUNC('WEEK', '2021-01-01'::DATE),"
        + " DAYOFWEEK('2020-03-08'::DATE), DAYOFWEEK('2020-03-07'::DATE)";

    @AfterEach
    public void unsetTheWeek() {
        answer("ALTER SESSION UNSET WEEK_START");
        answer("ALTER SESSION UNSET WEEK_OF_YEAR_POLICY");
    }

    @Test
    public void theDefaultWeekStartsOnMondayAndCountsSundayZero() {
        assertAnswer("3|3|10|10|10|2020|2020|2020-03-02|3|3|10|2020-03-06|2020-02-28|2020-03-08|1",
            WEEK_OF_A_WEDNESDAY);
        assertAnswer("5|53|2020|53|2020-12-28|0|6", YEAR_END);
        assertAnswer("Wed|3|3", "SELECT DAYNAME(" + D + "), DATE_PART('dow', " + D + "), DATE_PART('weekday', " + D
            + ")");
    }

    @Test
    public void aWeekStartMovesTheWeek() {
        answer("ALTER SESSION SET WEEK_START = 5");
        assertAnswer("6|3|9|9|10|2020|2020|2020-02-28|6|6|9|2020-03-06|2020-02-28|2020-03-05|0", WEEK_OF_A_WEDNESDAY);
        assertAnswer("1|1|2021|52|2021-01-01|3|2", YEAR_END);
        answer("ALTER SESSION SET WEEK_START = 1");
        assertAnswer("3|3|10|10|10|2020|2020|2020-03-02|3|3|10|2020-03-06|2020-02-28|2020-03-08|1",
            WEEK_OF_A_WEDNESDAY);
        assertAnswer("5|53|2020|53|2020-12-28|7|6", YEAR_END);
        answer("ALTER SESSION SET WEEK_START = 7");
        assertAnswer("4|3|10|10|10|2020|2020|2020-03-01|4|4|10|2020-03-06|2020-02-28|2020-03-07|0",
            WEEK_OF_A_WEDNESDAY);
        assertAnswer("6|53|2020|53|2020-12-27|1|7", YEAR_END);
    }

    @Test
    public void theFirstWeekHoldsJanuaryFirstUnderPolicyOne() {
        answer("ALTER SESSION SET WEEK_START = 7");
        answer("ALTER SESSION SET WEEK_OF_YEAR_POLICY = 1");
        assertAnswer("4|3|10|10|10|2020|2020|2020-03-01|4|4|10|2020-03-06|2020-02-28|2020-03-07|0",
            WEEK_OF_A_WEDNESDAY);
        assertAnswer("6|1|2021|53|2020-12-27|1|7", YEAR_END);
        answer("ALTER SESSION SET WEEK_START = 0");
        assertAnswer("5|1|2021|53|2020-12-28|0|6", YEAR_END);
    }
}
