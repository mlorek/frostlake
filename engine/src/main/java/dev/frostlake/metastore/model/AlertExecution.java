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

import java.time.Instant;

/** One evaluation of an alert, as ALERT_HISTORY reports it. */
public final class AlertExecution {

    private final Instant scheduledTime;
    private final Instant completedTime;
    private final String state;
    private final String errorMessage;
    private final String scheduledFrom;

    /**
     * @param scheduledTime when the evaluation was due
     * @param completedTime when it finished
     * @param state its outcome: {@code TRIGGERED}, {@code CONDITION_FALSE}, {@code CONDITION_FAILED} or
     *              {@code ACTION_FAILED}
     * @param errorMessage the failure's message, or null
     * @param scheduledFrom what started it: {@code SCHEDULE} or {@code EXECUTE ALERT}
     */
    public AlertExecution(final Instant scheduledTime, final Instant completedTime, final String state,
                          final String errorMessage, final String scheduledFrom) {
        this.scheduledTime = scheduledTime;
        this.completedTime = completedTime;
        this.state = state;
        this.errorMessage = errorMessage;
        this.scheduledFrom = scheduledFrom;
    }

    /** When the evaluation was due. */
    public Instant getScheduledTime() {
        return scheduledTime;
    }

    /** When the evaluation finished. */
    public Instant getCompletedTime() {
        return completedTime;
    }

    /** The outcome. */
    public String getState() {
        return state;
    }

    /** The failure's message, or null. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** What started the evaluation. */
    public String getScheduledFrom() {
        return scheduledFrom;
    }

    /** Whether the evaluation failed, in its condition or its action. */
    public boolean failed() {
        return "CONDITION_FAILED".equals(state) || "ACTION_FAILED".equals(state);
    }
}
