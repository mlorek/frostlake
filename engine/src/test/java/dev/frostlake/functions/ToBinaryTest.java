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

/** TO_BINARY(string [, format]) — decodes to bytes (HEX default / BASE64 / UTF-8), rendered as uppercase hex. */
public class ToBinaryTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void hexIsTheDefaultAndIsNormalizedToUppercase() {
        assertEquals("AB12", scalar("SELECT TO_BINARY('ab12')"));
        assertEquals("534E4F57", scalar("SELECT TO_BINARY('534e4f57', 'HEX')"));
    }

    @Test
    public void utf8EncodesTheStringBytes() {
        assertEquals("534E4F57", scalar("SELECT TO_BINARY('SNOW', 'UTF-8')"));
    }

    @Test
    public void base64Decodes() {
        assertEquals("534E4F57", scalar("SELECT TO_BINARY('U05PVw==', 'BASE64')"));
    }

    @Test
    public void nullYieldsNull() {
        assertNull(scalar("SELECT TO_BINARY(NULL)"));
    }

    @Test
    public void invalidHexLengthThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_BINARY('abc')");
            }
        });
    }

    @Test
    public void unsupportedFormatThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_BINARY('abc', 'OCTAL')");
            }
        });
    }
}
