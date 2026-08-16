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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * REPEAT answers NULL when either argument is NULL, the count included; SPACE likewise. A BINARY
 * argument is refused as the LPAD REPEAT is planned as — see BinaryRewriteRefusalTest. Live-verified.
 */
public class RepeatFunctionTest extends BaseDatabaseTest {

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void aNullArgumentIsANullAnswer() {
        assertNull(value("SELECT REPEAT('ab', NULL)"));
        assertNull(value("SELECT REPEAT(NULL, 2)"));
        assertNull(value("SELECT SPACE(NULL)"));
    }

    @Test
    public void theStringRepeats() {
        assertEquals("ababab", value("SELECT REPEAT('ab', 3)"));
        assertEquals("", value("SELECT REPEAT('ab', 0)"));
    }
}
