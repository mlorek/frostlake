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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TRY_TO_TIMESTAMP / _LTZ / _TZ — non-throwing TO_TIMESTAMP family (all behave as NTZ in this engine). */
public class TryToTimestampVariantsTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void tryToTimestampParsesValid() {
        assertNotNull(scalar("SELECT TRY_TO_TIMESTAMP('2024-06-15 10:30:00')"));
    }

    @Test
    public void tryToTimestampInvalidYieldsNull() {
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP('not-a-timestamp')"));
    }

    @Test
    public void tryToTimestampLtzParsesValid() {
        assertNotNull(scalar("SELECT TRY_TO_TIMESTAMP_LTZ('2024-06-15 10:30:00')"));
    }

    @Test
    public void tryToTimestampTzInvalidYieldsNull() {
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP_TZ('nope')"));
    }

    @Test
    public void untypedNullIsRejectedAndCastNullYieldsNull() {
        // TRY_TO_* is TRY_CAST under the hood and needs a VARCHAR source: an UNTYPED NULL errors
        // "Function TRY_CAST cannot be used with arguments of types NULL and TIMESTAMP_NTZ(9)"
        // (live-verified), while NULL::VARCHAR converts to NULL.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar("SELECT TRY_TO_TIMESTAMP(NULL)");
            }
        });
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP(NULL::VARCHAR)"));
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP_LTZ(NULL::VARCHAR)"));
        assertNull(scalar("SELECT TRY_TO_TIMESTAMP_TZ(NULL::VARCHAR)"));
    }
}
