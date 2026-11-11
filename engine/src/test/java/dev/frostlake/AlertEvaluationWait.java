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

package dev.frostlake;

/**
 * Waits for an alert's evaluations to be told by INFORMATION_SCHEMA.ALERT_HISTORY. The embedded engine evaluates an
 * executed alert inside EXECUTE ALERT, so the first look finds it; an account evaluates it a moment later, and keeps
 * the history of the alerts earlier runs gave the same name, so only the evaluations since a mark are counted.
 */
public final class AlertEvaluationWait {

    private AlertEvaluationWait() {
    }

    /**
     * Marks the start of the evaluations {@link #until} counts, in the session variable {@code alert_since}: a second
     * before now, since the history tells a scheduled time to the millisecond.
     *
     * @param engine the engine under test
     */
    public static void mark(final DatabaseEngine engine) {
        engine.execute("SET alert_since = DATEADD('second', -1, CURRENT_TIMESTAMP())");
    }

    /**
     * Looks until an alert has finished a number of evaluations since the mark.
     *
     * @param engine      the engine under test
     * @param alert       the alert's name
     * @param evaluations the finished evaluations waited for
     */
    public static void until(final DatabaseEngine engine, final String alert, final int evaluations) {
        TaskRunWait.untilRows(engine, "SELECT 1 FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY("
            + "SCHEDULED_TIME_RANGE_START => $alert_since, ALERT_NAME => '" + alert + "')) "
            + "WHERE STATE NOT IN ('SCHEDULED', 'EXECUTING')", evaluations);
    }
}
