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

/**
 * What started a task run, as TASK_HISTORY's {@code SCHEDULED_FROM} reports it. A DAG child inherits
 * its root's trigger: a child run of a manually executed root reads EXECUTE TASK too (live-verified).
 */
public enum TaskTrigger {

    /** The task's own schedule armed the run — also what a pending, not-yet-run fire reports. */
    SCHEDULE("SCHEDULE"),

    /** An EXECUTE TASK statement started the run, or the root of the graph it belongs to. */
    EXECUTE_TASK("EXECUTE TASK"),

    /** A run EXECUTE TASK … RETRY LAST started, re-running the failed part of the last graph run. */
    MANUAL_RETRY("MANUAL RETRY");

    private final String reported;

    TaskTrigger(final String reported) {
        this.reported = reported;
    }

    /**
     * The value TASK_HISTORY prints for it.
     *
     * @return the reported text
     */
    public String reported() {
        return reported;
    }
}
