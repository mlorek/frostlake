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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A JavaScript UDF declared to RETURN a semi-structured type hands back a REAL semi-structured
 * value, not its JSON text — so a container embeds it structurally:
 * {@code OBJECT_CONSTRUCT('k', js_array_fn(...))} nests the array instead of quoting its text,
 * and a scalar string under a VARIANT declaration becomes a VARIANT STRING.
 */
public class JavaScriptSemiStructuredReturnTest extends BaseDatabaseTest {

    @Test
    public void testDeclaredArrayReturnEmbedsStructurally() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION js_matches()
            RETURNS ARRAY
            LANGUAGE JAVASCRIPT
            AS $$ return ['hello', 'world']; $$""");

        assertEquals("{\"k\":[\"hello\",\"world\"]}",
            String.valueOf(engine.executeQuery(
                "SELECT OBJECT_CONSTRUCT('k', js_matches())").getRows().get(0).getValue(0)));
    }

    @Test
    public void testDeclaredVariantScalarStringBecomesVariantString() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION js_word()
            RETURNS VARIANT
            LANGUAGE JAVASCRIPT
            AS $$ return 'plain'; $$""");

        assertEquals("\"plain\"",
            String.valueOf(engine.executeQuery(
                "SELECT js_word()").getRows().get(0).getValue(0)));
    }
}
