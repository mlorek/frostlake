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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The GRAPH_RUN_GROUP_ID of a task graph run: stable for one root task's run, so the task graph functions and the
 * refusals that name a run agree on it.
 */
public final class GraphRunGroup {

    private GraphRunGroup() {
    }

    /**
     * The id of one graph run.
     *
     * @param rootTaskId the root task's id
     * @param runId the run's RUN_ID, the epoch milliseconds of its scheduled time, or null
     * @return the group id
     */
    public static String id(final String rootTaskId, final Long runId) {
        return UUID.nameUUIDFromBytes((rootTaskId + ":" + runId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
