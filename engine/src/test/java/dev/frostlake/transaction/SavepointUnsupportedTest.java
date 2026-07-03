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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Savepoints are unsupported (Snowflake has no SAVEPOINT, and supportsSavepoints() is false). Rather
 * than silently no-op — which they did under the default deferred-apply mode, where the undo log a
 * savepoint rollback replays is never populated — the API now rejects them.
 */
public class SavepointUnsupportedTest extends BaseDatabaseTest {

    @Test
    public void setSavepointIsRejected() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                engine.setSavepoint("sp");
            }
        });
    }

    @Test
    public void rollbackToSavepointIsRejected() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                engine.rollbackToSavepoint("sp");
            }
        });
    }
}
