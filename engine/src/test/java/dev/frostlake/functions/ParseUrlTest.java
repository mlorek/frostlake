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

/** PARSE_URL(url [, permissive]) — parses a URL into a VARIANT OBJECT with Snowflake's key set. */
public class ParseUrlTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void parsesDocumentedExample() {
        // Matches the object shown in the Snowflake PARSE_URL documentation.
        assertEquals(
            "{\"scheme\":\"http\",\"host\":\"USER:PASS@EXAMPLE.INT\",\"port\":\"4345\","
                + "\"path\":\"HELLO.PHP\",\"query\":\"USER=1\",\"parameters\":{\"USER\":\"1\"},\"fragment\":null}",
            scalar("SELECT PARSE_URL('http://USER:PASS@EXAMPLE.INT:4345/HELLO.PHP?USER=1')"));
    }

    @Test
    public void parsesQueryParametersAndFragment() {
        assertEquals(
            "{\"scheme\":\"https\",\"host\":\"example.com\",\"port\":null,\"path\":\"a/b\","
                + "\"query\":\"x=1&y=2\",\"parameters\":{\"x\":\"1\",\"y\":\"2\"},\"fragment\":\"frag\"}",
            scalar("SELECT PARSE_URL('https://example.com/a/b?x=1&y=2#frag')"));
    }

    @Test
    public void permissiveReturnsErrorObjectOnMissingScheme() {
        assertEquals("{\"error\":\"scheme not specified\"}",
            scalar("SELECT PARSE_URL('example.int/hello.php?user=12', 1)"));
    }

    @Test
    public void nonPermissiveMissingSchemeThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_URL('example.int/hello.php?user=12')");
            }
        });
    }

    @Test
    public void nullInputYieldsNull() {
        assertNull(scalar("SELECT PARSE_URL(NULL)"));
    }
}
