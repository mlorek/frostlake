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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** TRY_TO_TIME(expr [, format]) — non-throwing TO_TIME: NULL instead of an error on unparseable input. */
public class TryToTimeTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void parsesAValidTime() {
        final Object v = scalar("SELECT TRY_TO_TIME('12:30:00')");
        assertNotNull(v);
        assertEquals("12:30", v.toString());
    }

    @Test
    public void invalidTimeYieldsNullNotError() {
        assertNull(scalar("SELECT TRY_TO_TIME('not-a-time')"));
    }

    @Test
    public void nullInputYieldsNull() {
        assertNull(scalar("SELECT TRY_TO_TIME(NULL)"));
    }
}
