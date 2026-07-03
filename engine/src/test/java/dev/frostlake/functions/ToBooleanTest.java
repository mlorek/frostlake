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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TO_BOOLEAN accepts the full Snowflake input set: a numeric value (0 = false, non-zero = true) and
 * the text tokens true/t/yes/y/on/1 and false/f/no/n/off/0 (case-insensitive). A numeric STRING like
 * '2' is still an error (only numeric 2 is truthy). Previously only true/1/yes/on and false/0/no/off
 * were accepted and any numeric argument such as 2 threw.
 */
public class ToBooleanTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void numericZeroFalseNonZeroTrue() {
        assertEquals(false, q("SELECT TO_BOOLEAN(0)"));
        assertEquals(true, q("SELECT TO_BOOLEAN(2)"));
        assertEquals(true, q("SELECT TO_BOOLEAN(-5)"));
    }

    @Test
    public void truthyTextTokens() {
        for (final String token : new String[] {"'true'", "'t'", "'yes'", "'y'", "'on'", "'1'", "'TRUE'"}) {
            assertEquals(true, q("SELECT TO_BOOLEAN(" + token + ")"), token);
        }
    }

    @Test
    public void falsyTextTokens() {
        for (final String token : new String[] {"'false'", "'f'", "'no'", "'n'", "'off'", "'0'", "'FALSE'"}) {
            assertEquals(false, q("SELECT TO_BOOLEAN(" + token + ")"), token);
        }
    }

    @Test
    public void numericStringIsNotAValidToken() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_BOOLEAN('2')");
            }
        });
    }

    @Test
    public void nullPassesThrough() {
        assertNull(q("SELECT TO_BOOLEAN(NULL)"));
    }
}
