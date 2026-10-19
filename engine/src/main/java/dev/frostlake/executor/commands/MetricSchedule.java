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

package dev.frostlake.executor.commands;

import dev.frostlake.task.CronSchedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The schedule the data metric functions of a table, a view or a materialized view run on — the text of its
 * {@code DATA_METRIC_SCHEDULE}. It is one of
 *
 * <pre>
 *   '5 MINUTE'                      an interval of 5, 15, 30, 60, 720 or 1440 minutes, the unit in any case
 *   'USING CRON 0 0 * * * UTC'      a CRON expression and its time zone
 *   'TRIGGER_ON_CHANGES'            with every change — a table's alone
 *   ''                              none: the functions are suspended
 * </pre>
 *
 * and anything else — another count of minutes, another unit, a CRON text short of its six parts — is refused
 * with the one sentence a malformed task schedule earns (live-verified).
 */
final class MetricSchedule {

    private static final Set<String> MINUTES = new HashSet<String>(Arrays.asList("5", "15", "30", "60", "720", "1440"));

    private MetricSchedule() {
    }

    /**
     * Refuses a text that is no metric schedule.
     *
     * @param schedule  the schedule as its text reads
     * @param onChanges whether the object's kind may run its metrics with every change
     */
    static void require(final String schedule, final boolean onChanges) {
        final List<String> words = new ArrayList<String>();
        for (final String word : schedule.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word.toUpperCase(Locale.ROOT));
            }
        }
        if (words.isEmpty()) {
            return;
        }
        if ("USING".equals(words.get(0))) {
            if (words.size() != 8) {
                throw new RuntimeException(CronSchedule.INVALID_SCHEDULE_MESSAGE);
            }
            CronSchedule.parse(schedule);
            return;
        }
        if (words.size() == 1 && "TRIGGER_ON_CHANGES".equals(words.get(0)) && onChanges) {
            return;
        }
        if (words.size() == 2 && MINUTES.contains(words.get(0))
                && ("MINUTE".equals(words.get(1)) || "M".equals(words.get(1)))) {
            return;
        }
        throw new RuntimeException(CronSchedule.INVALID_SCHEDULE_MESSAGE);
    }
}
