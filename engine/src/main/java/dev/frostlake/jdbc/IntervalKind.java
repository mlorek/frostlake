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

package dev.frostlake.jdbc;

/**
 * How Snowflake's JDBC driver materializes an interval column, which follows the column's storage width
 * rather than its family alone (live-verified with the driver's default ARROW results): a day-time interval
 * stored in sixteen bytes is a fixed-point count of nanoseconds, one stored in eight bytes (an
 * {@code INTERVAL SECOND(9,9)}) is a {@link java.time.Duration}, and a year-month interval is a
 * {@link java.time.Period}. Each kind carries the type text the driver names in its conversion refusals.
 */
enum IntervalKind {

    /** A day-time interval stored in sixteen bytes: DAY, HOUR, MINUTE and every compound DAY … qualifier. */
    WIDE("FIXED(null,null)"),

    /** A day-time interval stored in eight bytes: SECOND(9,9). */
    NARROW("INTERVAL_DAY_TIME"),

    /** A year-month interval: YEAR, MONTH, YEAR TO MONTH. */
    YEAR_MONTH("INTERVAL_YEAR_MONTH");

    private final String driverTypeText;

    IntervalKind(final String driverTypeText) {
        this.driverTypeText = driverTypeText;
    }

    /** @return the type the driver names in a refused conversion, {@code FIXED(null,null)} and the like */
    String driverTypeText() {
        return driverTypeText;
    }
}
