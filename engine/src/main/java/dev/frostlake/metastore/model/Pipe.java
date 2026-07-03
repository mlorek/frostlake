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

import dev.frostlake.metastore.SqlObject;

/**
 * Represents a Snowflake PIPE for continuous data loading
 */
public class Pipe extends SqlObject {
    private final String copyStatement;
    private final boolean autoIngest;
    private final String notificationChannel;
    private boolean paused;
    private String errorIntegration;
    private String awsSnsTopicArn;
    private String integration;
    private int lastLoadedFileCount;
    private String lastLoadedTime;

    public Pipe(final String name, final String copyStatement, final boolean autoIngest) {
        super(name);
        this.copyStatement = copyStatement;
        this.autoIngest = autoIngest;
        this.notificationChannel = null;
        this.paused = false;
        this.lastLoadedFileCount = 0;
    }

    public Pipe(final String name, final String copyStatement, final boolean autoIngest, final String notificationChannel) {
        super(name);
        this.copyStatement = copyStatement;
        this.autoIngest = autoIngest;
        this.notificationChannel = notificationChannel;
        this.paused = false;
        this.lastLoadedFileCount = 0;
    }

    public String getCopyStatement() {
        return copyStatement;
    }

    public boolean isAutoIngest() {
        return autoIngest;
    }

    public String getNotificationChannel() {
        return notificationChannel;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(final boolean paused) {
        this.paused = paused;
    }

    public String getErrorIntegration() {
        return errorIntegration;
    }

    public void setErrorIntegration(final String errorIntegration) {
        this.errorIntegration = errorIntegration;
    }

    public String getAwsSnsTopicArn() {
        return awsSnsTopicArn;
    }

    public void setAwsSnsTopicArn(final String awsSnsTopicArn) {
        this.awsSnsTopicArn = awsSnsTopicArn;
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(final String integration) {
        this.integration = integration;
    }

    public int getLastLoadedFileCount() {
        return lastLoadedFileCount;
    }

    public void setLastLoadedFileCount(final int lastLoadedFileCount) {
        this.lastLoadedFileCount = lastLoadedFileCount;
    }

    public String getLastLoadedTime() {
        return lastLoadedTime;
    }

    public void setLastLoadedTime(final String lastLoadedTime) {
        this.lastLoadedTime = lastLoadedTime;
    }

    public String getStatus() {
        if (paused) {
            return "PAUSED";
        }
        return "RUNNING";
    }

    /**
     * The JSON returned by {@code SYSTEM$PIPE_STATUS} — reflects the pipe's actual execution state
     * (RUNNING vs PAUSED) and notification channel, matching Snowflake's shape.
     */
    public String getStatusJson() {
        final String channel = notificationChannel != null
            ? "\"" + notificationChannel + "\""
            : "null";
        // pendingFileCount is 0 — ingestion is synchronous (ALTER PIPE … REFRESH), so nothing is queued.
        return "{\"executionState\": \"" + getStatus() + "\", \"pendingFileCount\": 0"
            + ", \"notificationChannelName\": " + channel + "}";
    }

    @Override
    public String getObjectType() {
        return "PIPE";
    }

    @Override
    public String toString() {
        return "Pipe{" +
                "name='" + getName() + '\'' +
                ", autoIngest=" + autoIngest +
                ", status=" + getStatus() +
                ", copyStatement='" + copyStatement + '\'' +
                '}';
    }
}
