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

/**
 * A SYSTEM$ call takes any argument a select item could be, the boolean operators included:
 * {@code SYSTEM$TYPEOF(NOT TRUE)}, {@code (TRUE AND FALSE)} and {@code (TRUE OR FALSE)} are BOOLEAN,
 * exactly as the parenthesised forms always were. Every cell is live-verified.
 */
public class SystemTypeofOperatorArgumentTest extends BaseDatabaseTest {

    private String typeOf(final String argument) {
        return String.valueOf(engine.executeQuery("SELECT SYSTEM$TYPEOF(" + argument + ")")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void aBooleanOperatorIsAnArgumentLikeAnyOther() {
        assertEquals("BOOLEAN[SB1]", typeOf("NOT TRUE"));
        assertEquals("BOOLEAN[SB1]", typeOf("TRUE AND FALSE"));
        assertEquals("BOOLEAN[SB1]", typeOf("TRUE OR FALSE"));
        assertEquals("BOOLEAN[SB1]", typeOf("NULL AND TRUE"));
        assertEquals("BOOLEAN[SB1]", typeOf("(NOT TRUE)"));
        assertEquals("NUMBER(1,0)[SB1]", typeOf("IFF(NOT TRUE, 1, 2)"));
    }
}
