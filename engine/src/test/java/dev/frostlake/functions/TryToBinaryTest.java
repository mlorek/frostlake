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

/** TRY_TO_BINARY(expr [, format]) — non-throwing TO_BINARY: NULL instead of an error on undecodable input. */
public class TryToBinaryTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void decodesHexByDefault() {
        assertEquals("AB12", scalar("SELECT TRY_TO_BINARY('ab12')"));
    }

    @Test
    public void decodesBase64() {
        assertEquals("534E4F57", scalar("SELECT TRY_TO_BINARY('U05PVw==', 'BASE64')"));
    }

    @Test
    public void invalidHexYieldsNullNotError() {
        assertNull(scalar("SELECT TRY_TO_BINARY('xyz')"));
    }

    @Test
    public void nullInputYieldsNull() {
        assertNull(scalar("SELECT TRY_TO_BINARY(NULL)"));
    }
}
