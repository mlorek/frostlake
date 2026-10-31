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

package dev.frostlake.executor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The date and time functions a block variable holding NULL makes an untyped NULL of when it is one of their
 * arguments, whatever it is declared as: {@code YEAR(:s)} over a DATE, {@code DATEADD(day, :n, d)} over a NUMBER
 * (live-verified, one by one). A statement the block runs as text binds the NULL bare there ({@link NullBindSlot});
 * a query the block evaluates itself types the call as it types the call over the word NULL.
 */
public final class NullFoldingDateFunctions {

    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
        "ADD_MONTHS", "DATEADD", "DATEDIFF", "DATE_PART", "DATE_TRUNC", "DAY", "DAYOFMONTH", "DAYOFWEEK",
        "DAYOFWEEKISO", "DAYOFYEAR", "EXTRACT", "HOUR", "LAST_DAY", "MINUTE", "MONTH", "MONTHS_BETWEEN", "QUARTER",
        "SECOND", "TIMEADD", "TIMEDIFF", "TIMESTAMPADD", "TIMESTAMPDIFF", "TIME_SLICE", "TRUNC", "TRUNCATE", "WEEK",
        "WEEKISO", "WEEKOFYEAR", "YEAR", "YEAROFWEEK", "YEAROFWEEKISO"));

    private NullFoldingDateFunctions() {
    }

    /**
     * @param name a function's name
     * @return whether it is one of these functions
     */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase(Locale.ROOT));
    }
}
