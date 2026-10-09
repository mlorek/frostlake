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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.security.SessionContext;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.StringType;

import java.util.List;

public class CurrentUser extends BuiltInFunction {
    private final SessionContext sessionContext;

    public CurrentUser(final SessionContext sessionContext) {
        super("CURRENT_USER", StringType.VARCHAR);
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A task's run is the system's, or the user's the task is declared to execute as (live-verified).
        final Task running = TaskScheduler.currentTask();
        if (running != null) {
            return running.getExecuteAsUser() != null ? running.getExecuteAsUser() : "SYSTEM";
        }
        return sessionContext.getDisplayUser();
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
