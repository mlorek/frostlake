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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A TaskExecutor that only implements {@code execute} must not inherit a silent {@code executeQuery}:
 * an empty result there would make a task's WHEN condition evaluate as "run the task". The default now
 * throws, forcing implementers to provide a real query path (the engine's executor does).
 */
public class TaskExecutorDefaultTest {

    @Test
    public void defaultExecuteQueryThrowsInsteadOfReturningEmpty() {
        final TaskExecutor executor = new TaskExecutor() {
            @Override
            public int execute(final String sql) {
                return 0;
            }
        };
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                executor.executeQuery("SELECT 1");
            }
        });
    }
}
