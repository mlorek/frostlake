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
import java.util.ArrayList;
import java.util.HashMap;

/** An alert, as a snapshot holds it. Its execution history is runtime state and is not kept. */
public class AlertSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The canonical name. */
    public String name;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** The IF (EXISTS (…)) condition. */
    public String condition;

    /** The THEN action. */
    public String action;

    /** WAREHOUSE, or null. */
    public String warehouse;

    /** SCHEDULE, or null. */
    public String schedule;

    /** CONFIG, or null. */
    public String config;

    /** The runbook text, or null. */
    public String runbook;

    /** SUSPEND_ALERT_AFTER_NUM_FAILURES, or null. */
    public Integer suspendAfterNumFailures;

    /** The alert's state, as the enum constant's name. */
    public String state;

    /** Whether the alert was suspended after its failures, or null. */
    public Boolean wasAutoSuspended;

    /** The tags set on the alert. */
    public HashMap<String, String> tags = new HashMap<>();

    /** When it was created. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public Instant createdOn;

    /** The evaluations recorded, oldest first. Null in a snapshot written before they were kept. */
    public ArrayList<AlertExecutionSnapshot> history;
}
