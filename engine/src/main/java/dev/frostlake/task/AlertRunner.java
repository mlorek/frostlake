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
package dev.frostlake.task;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertExecution;
import dev.frostlake.metastore.model.AlertState;
import dev.frostlake.storage.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Evaluates an alert once: its condition runs in the alert's home database and schema, and when it returns at
 * least one row the action runs too. Every evaluation is recorded for ALERT_HISTORY, and an alert whose
 * {@code SUSPEND_ALERT_AFTER_NUM_FAILURES} is reached by consecutive failures is suspended.
 */
public final class AlertRunner {

    private static final Logger logger = LoggerFactory.getLogger(AlertRunner.class);

    private final Catalog catalog;
    private final TaskExecutor executor;

    /**
     * @param catalog the catalog whose session scope the evaluation runs in
     * @param executor what runs the condition and the action
     */
    public AlertRunner(final Catalog catalog, final TaskExecutor executor) {
        this.catalog = catalog;
        this.executor = executor;
    }

    /**
     * Evaluates the alert.
     *
     * @param database the alert's database
     * @param schema the alert's schema
     * @param alert the alert
     * @param scheduledFrom what started the evaluation: {@code SCHEDULE} or {@code EXECUTE ALERT}
     * @return the recorded evaluation
     */
    public AlertExecution run(final String database, final String schema, final Alert alert,
                              final String scheduledFrom) {
        final Instant scheduled = Instant.now();
        final String[] prior = catalog.currentSessionScope();
        catalog.beginSessionScope(database, schema);
        final AlertExecution execution;
        try {
            execution = evaluate(alert, scheduled, scheduledFrom);
        } finally {
            catalog.restoreSessionScope(prior);
        }
        alert.recordExecution(execution);
        final Integer limit = alert.getSuspendAfterNumFailures();
        if (execution.failed() && limit != null && limit.intValue() > 0
                && alert.getConsecutiveFailures() >= limit.intValue() && alert.getState() == AlertState.STARTED) {
            logger.warn("Alert {}.{}.{} failed {} times in a row: suspending", database, schema, alert.getName(),
                alert.getConsecutiveFailures());
            alert.setState(AlertState.SUSPENDED);
            alert.setWasAutoSuspended(Boolean.TRUE);
            alert.setNextScheduledTime(null);
        }
        return execution;
    }

    private AlertExecution evaluate(final Alert alert, final Instant scheduled, final String scheduledFrom) {
        final List<ResultSet> answer;
        try {
            answer = executor.executeQuery(alert.getCondition());
        } catch (final RuntimeException failure) {
            return new AlertExecution(scheduled, Instant.now(), "CONDITION_FAILED", message(failure), scheduledFrom);
        }
        if (!returnsRows(answer)) {
            return new AlertExecution(scheduled, Instant.now(), "CONDITION_FALSE", null, scheduledFrom);
        }
        try {
            executor.execute(alert.getAction());
        } catch (final RuntimeException failure) {
            return new AlertExecution(scheduled, Instant.now(), "ACTION_FAILED", message(failure), scheduledFrom);
        }
        return new AlertExecution(scheduled, Instant.now(), "TRIGGERED", null, scheduledFrom);
    }

    /** The interval of an alert's schedule in seconds (see {@link IntervalSchedule}); an hour when it is none. */
    public static long seconds(final String schedule) {
        final long interval = IntervalSchedule.seconds(schedule);
        return interval > 0L && interval <= IntervalSchedule.MAXIMUM_SECONDS ? interval : 3600L;
    }

    private static boolean returnsRows(final List<ResultSet> answer) {
        if (answer == null) {
            return false;
        }
        for (final ResultSet set : answer) {
            if (set != null && !set.getRows().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String message(final RuntimeException failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.toString();
    }
}
