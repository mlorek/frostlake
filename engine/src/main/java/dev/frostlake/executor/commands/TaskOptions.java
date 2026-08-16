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

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.task.CronSchedule;

import java.time.ZonedDateTime;

/**
 * The serverless-task option rules, shared by CREATE TASK and ALTER TASK … SET.
 *
 * <p>A task is SERVERLESS exactly when it has no WAREHOUSE; Snowflake then manages the compute.
 * Three of the task options are meaningful only in that mode and are refused on a task that names
 * a warehouse — measured on a real account, with two distinct sentences:
 * {@code USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE} and {@code SERVERLESS_TASK_MAX_STATEMENT_SIZE}
 * both give "Cannot set &lt;OPTION&gt; on a non-serverless task.", while
 * {@code TARGET_COMPLETION_INTERVAL} names the task instead: "TARGET_COMPLETION_INTERVAL is only
 * allowed for serverless Tasks. &lt;DB&gt;.&lt;SCHEMA&gt;.&lt;TASK&gt; is not a serverless task."
 * {@code USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS} is deliberately NOT in the set — a real
 * account accepts it on a warehouse task.
 *
 * <p>The check is one-directional, as live's is: it fires when a serverless-only option is SET on
 * a task that has a warehouse. Attaching a warehouse to a task that already carries one of these
 * options is accepted on a real account, so this never rejects a WAREHOUSE assignment.
 */
final class TaskOptions {

    private TaskOptions() {
    }

    static void rejectServerlessOptionsOnWarehouseTask(final String warehouse,
                                                       final String managedWarehouseSize,
                                                       final String serverlessMaxStatementSize,
                                                       final String targetCompletionInterval,
                                                       final Schema schema,
                                                       final String taskName) {
        if (warehouse == null || warehouse.isEmpty()) {
            return;
        }
        if (managedWarehouseSize != null) {
            throw new RuntimeException(
                "Cannot set USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE on a non-serverless task.");
        }
        if (serverlessMaxStatementSize != null) {
            throw new RuntimeException(
                "Cannot set SERVERLESS_TASK_MAX_STATEMENT_SIZE on a non-serverless task.");
        }
        if (targetCompletionInterval != null) {
            throw new RuntimeException("TARGET_COMPLETION_INTERVAL is only allowed for serverless"
                + " Tasks. " + qualifiedTaskName(schema, taskName) + " is not a serverless task.");
        }
    }

    /**
     * A task SCHEDULE is either {@code '<n> MINUTE[S]'} with a positive count, or
     * {@code 'USING CRON <minute> <hour> <day-of-month> <month> <day-of-week> <time zone>'}.
     * Anything else — a zero interval included — refuses with the account's one sentence for
     * every malformed shape, live-verified. A CRON expression is held to the account's full field
     * grammar (see {@link CronSchedule}), and one that is well formed but names no instant at all —
     * February 31st — is refused with its own sentence.
     */
    static void requireValidSchedule(final String schedule) {
        final String trimmed = schedule == null ? "" : schedule.trim();
        final String upper = trimmed.toUpperCase();
        if (upper.contains("CRON")) {
            final CronSchedule cron = CronSchedule.parse(trimmed);
            if (cron.nextFireAfter(ZonedDateTime.now(cron.zone())) == null) {
                throw new RuntimeException(CronSchedule.NEVER_FIRES_MESSAGE);
            }
            return;
        }
        if (!upper.matches("[0-9]+\\s+MINUTES?") || Long.parseLong(upper.split("\\s+")[0]) < 1L) {
            throw new RuntimeException(invalidScheduleMessage());
        }
    }

    private static String invalidScheduleMessage() {
        return CronSchedule.INVALID_SCHEDULE_MESSAGE;
    }

    /** The task's fully qualified upper-case name, the form live names it by in the
     *  TARGET_COMPLETION_INTERVAL rejection. */
    private static String qualifiedTaskName(final Schema schema, final String taskName) {
        final StringBuilder qualified = new StringBuilder();
        if (schema != null) {
            if (schema.getDatabaseName() != null) {
                qualified.append(schema.getDatabaseName().toUpperCase()).append('.');
            }
            qualified.append(schema.getName().toUpperCase()).append('.');
        }
        return qualified.append(taskName.toUpperCase()).toString();
    }
}
