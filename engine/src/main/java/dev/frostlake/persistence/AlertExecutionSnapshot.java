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
package dev.frostlake.persistence;

import java.io.Serializable;
import java.time.Instant;

/** One recorded evaluation of an alert, as ALERT_HISTORY reports it, as a snapshot holds it. */
public class AlertExecutionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** When the evaluation was scheduled. */
    public Instant scheduledTime;

    /** When it completed, or null. */
    public Instant completedTime;

    /** Its state, e.g. TRIGGERED or CONDITION_FALSE. */
    public String state;

    /** The error message of a failed evaluation, or null. */
    public String errorMessage;

    /** What scheduled it: its schedule or an EXECUTE ALERT. */
    public String scheduledFrom;
}
