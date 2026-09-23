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
package dev.frostlake.metastore.model;

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.SqlObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Snowflake ALERT: a condition (a query, a SHOW listing or a CALL) evaluated on a schedule, and an action run
 * whenever the condition returns rows. A new alert is suspended; ALTER ALERT … RESUME starts its schedule, and
 * EXECUTE ALERT evaluates it once whatever its state.
 */
public class Alert extends SqlObject {

    /** How many evaluations the alert remembers for ALERT_HISTORY. */
    private static final int HISTORY_LIMIT = 10000;

    private String warehouse;
    private String schedule;
    private String config;
    private String runbook;
    private Integer suspendAfterNumFailures;
    private String condition;
    private String action;
    private AlertState state = AlertState.SUSPENDED;
    private Boolean wasAutoSuspended;
    private int consecutiveFailures;
    private Instant nextScheduledTime;
    private final List<AlertExecution> history = new ArrayList<>();

    /**
     * @param name the alert's name
     * @param condition the condition statement's text
     * @param action the action statement's text
     */
    public Alert(final String name, final String condition, final String action) {
        this(name, condition, action, StatementClock.instant());
    }

    /**
     * An alert created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the alert's name
     * @param condition the condition statement's text
     * @param action the action statement's text
     * @param createdTime when it was created
     */
    public Alert(final String name, final String condition, final String action, final Instant createdTime) {
        super(name, createdTime);
        this.condition = condition;
        this.action = action;
    }

    @Override
    public String getObjectType() {
        return "ALERT";
    }

    /** A copy under another name, as CREATE ALERT … CLONE makes it: suspended, with no history. */
    public Alert copy(final String newName) {
        final Alert copy = new Alert(newName, condition, action);
        copy.warehouse = warehouse;
        copy.schedule = schedule;
        copy.config = config;
        copy.runbook = runbook;
        copy.suspendAfterNumFailures = suspendAfterNumFailures;
        copy.setComment(getComment());
        for (final Map.Entry<String, String> tag : getTagValues().entrySet()) {
            copy.setTag(tag.getKey(), tag.getValue());
        }
        return copy;
    }

    /** The warehouse that runs the alert, or null for a serverless alert. */
    public String getWarehouse() {
        return warehouse;
    }

    /** Sets the warehouse; null unsets it. */
    public void setWarehouse(final String warehouse) {
        this.warehouse = warehouse;
    }

    /** The schedule as written ({@code 5 MINUTE}, {@code USING CRON …}), or null for an alert on new data. */
    public String getSchedule() {
        return schedule;
    }

    /** Sets the schedule. */
    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    /** The configuration string, or null. */
    public String getConfig() {
        return config;
    }

    /** Sets the configuration string; null unsets it. */
    public void setConfig(final String config) {
        this.config = config;
    }

    /** The runbook reference, or null. */
    public String getRunbook() {
        return runbook;
    }

    /** Sets the runbook reference; null unsets it. */
    public void setRunbook(final String runbook) {
        this.runbook = runbook;
    }

    /** How many consecutive failures suspend the alert, or null when the alert names no limit. */
    public Integer getSuspendAfterNumFailures() {
        return suspendAfterNumFailures;
    }

    /** Sets the failure limit; null unsets it. */
    public void setSuspendAfterNumFailures(final Integer suspendAfterNumFailures) {
        this.suspendAfterNumFailures = suspendAfterNumFailures;
    }

    /** The condition statement's text. */
    public String getCondition() {
        return condition;
    }

    /** Replaces the condition. */
    public void setCondition(final String condition) {
        this.condition = condition;
    }

    /** The action statement's text. */
    public String getAction() {
        return action;
    }

    /** Replaces the action. */
    public void setAction(final String action) {
        this.action = action;
    }

    /** Whether the alert is started or suspended. */
    public AlertState getState() {
        return state;
    }

    /** Sets the state. */
    public void setState(final AlertState state) {
        this.state = state;
    }

    /** Whether the alert is suspended by its failure limit; null when it never was. */
    public Boolean getWasAutoSuspended() {
        return wasAutoSuspended;
    }

    /** Records whether the alert is suspended by its failure limit. */
    public void setWasAutoSuspended(final Boolean wasAutoSuspended) {
        this.wasAutoSuspended = wasAutoSuspended;
    }

    /** The next scheduled evaluation, or null when none is armed. */
    public Instant getNextScheduledTime() {
        return nextScheduledTime;
    }

    /** Sets the next scheduled evaluation. */
    public void setNextScheduledTime(final Instant nextScheduledTime) {
        this.nextScheduledTime = nextScheduledTime;
    }

    /** How many evaluations in a row have failed. */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /** Records an evaluation, counting consecutive failures. */
    public synchronized void recordExecution(final AlertExecution execution) {
        history.add(execution);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
        consecutiveFailures = execution.failed() ? consecutiveFailures + 1 : 0;
    }

    /** The evaluations recorded, oldest first. */
    public synchronized List<AlertExecution> getHistory() {
        return new ArrayList<>(history);
    }
}
