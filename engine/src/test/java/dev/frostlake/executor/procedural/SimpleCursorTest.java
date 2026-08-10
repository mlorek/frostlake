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

package dev.frostlake.executor.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleCursorTest extends BaseDatabaseTest {

    @Test
    public void testSimpleCursor() {
        final String script = "DECLARE c1 CURSOR FOR SELECT 1 AS x; BEGIN RETURN 42; END;";

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals("42", String.valueOf(result.getRows().get(0).getValue(0)));
    }
}
