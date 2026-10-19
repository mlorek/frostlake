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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    // An auto-ingest pipe made by cloning its schema, which does not run until it is resumed (documented).
    private boolean stoppedCloned;

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
        if (!paused) {
            // Resuming is what ends a cloned pipe's stop, as it ends a pause.
            stoppedCloned = false;
        }
    }

    /** Whether this is a cloned auto-ingest pipe that has not been resumed since. */
    public boolean isStoppedCloned() {
        return stoppedCloned;
    }

    public void setStoppedCloned(final boolean stoppedCloned) {
        this.stoppedCloned = stoppedCloned;
    }

    /**
     * The stage this pipe's COPY loads from, as the parts of the name written after its '@'. A user stage
     * ({@code @~}) or a table stage ({@code @%t}) answers an empty list. A statement in which no stage can be
     * read answers null. A quoted part keeps its case; any other part is upper-cased, as it resolves.
     *
     * @return the stage name's parts, an empty list, or null
     */
    public List<String> stageParts() {
        if (copyStatement == null) {
            return null;
        }
        boolean inLiteral = false;
        for (int i = 0; i < copyStatement.length(); i++) {
            final char c = copyStatement.charAt(i);
            if (c == '\'') {
                inLiteral = !inLiteral;
            } else if (c == '@' && !inLiteral) {
                return stageNameAt(i + 1);
            }
        }
        return null;
    }

    /** The parts of the stage name starting at {@code start}, just past its '@'; see {@link #stageParts}. */
    private List<String> stageNameAt(final int start) {
        final List<String> parts = new ArrayList<>();
        int i = start;
        while (i < copyStatement.length()) {
            final char c = copyStatement.charAt(i);
            if (c == '~' || c == '%') {
                return new ArrayList<>();
            }
            if (c == '.') {
                i++;
            } else if (c == '"') {
                final StringBuilder part = new StringBuilder();
                i++;
                while (i < copyStatement.length()) {
                    if (copyStatement.charAt(i) == '"') {
                        if (i + 1 < copyStatement.length() && copyStatement.charAt(i + 1) == '"') {
                            part.append('"');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    part.append(copyStatement.charAt(i));
                    i++;
                }
                parts.add(part.toString());
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                final int begin = i;
                while (i < copyStatement.length() && (Character.isLetterOrDigit(copyStatement.charAt(i))
                        || copyStatement.charAt(i) == '_' || copyStatement.charAt(i) == '$')) {
                    i++;
                }
                parts.add(copyStatement.substring(begin, i).toUpperCase(Locale.ROOT));
            } else {
                break;
            }
        }
        return parts.isEmpty() ? null : parts;
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
        if (stoppedCloned) {
            return "STOPPED_CLONED";
        }
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
