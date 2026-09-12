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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dotted key continues a semi-structured PATH and nothing else: it is taken after a colon path, a bracket
 * access or another dotted key, and refused as a syntax error at the dot after a call, a parenthesised
 * expression or a literal. A bare {@code v.a} is a qualified column, not a path. Every cell is live-verified.
 */
public class DottedPathKeyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vt (v VARIANT, n NUMBER)");
        engine.execute("""
            INSERT INTO vt SELECT PARSE_JSON('{"a":{"b":1},"c":[{"b":2}]}'), 1
            UNION ALL SELECT PARSE_JSON('{"a":{"b":3},"c":[{"b":4}]}'), 2""");
    }

    private String column(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(row.getValue(0));
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aDottedKeyContinuesAPath() {
        assertEquals("1 | 3", column("SELECT v:a.b FROM vt ORDER BY n"));
        assertEquals("2 | 4", column("SELECT v:c[0].b FROM vt ORDER BY n"));
        assertEquals("1 | 3", column("SELECT v['a'].b FROM vt ORDER BY n"));
        assertEquals("1 | 3", column("SELECT vt.v:a.b FROM vt ORDER BY n"));
        assertEquals("null | null", column("SELECT v:a.b.c FROM vt ORDER BY n"));
        assertEquals("1 | 3", column("SELECT v:a['b'] FROM vt ORDER BY n"));
        assertEquals("1 | 3", column("SELECT v:a.\"b\" FROM vt ORDER BY n"));
        assertEquals("1", column("SELECT ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 1))[0].a"));
    }

    @Test
    public void elsewhereTheDotIsASyntaxError() {
        assertRefused("SELECT (v:a).b FROM vt ORDER BY n", "syntax error line 1 at position 12 unexpected '.'.");
        assertRefused("SELECT GET(v, 'a').b FROM vt ORDER BY n", "syntax error line 1 at position 18 unexpected '.'.");
        assertRefused("SELECT OBJECT_CONSTRUCT('a', 1).a", "syntax error line 1 at position 31 unexpected '.'.");
        assertRefused("SELECT PARSE_JSON('{\"AS\":2}').AS AS v", "syntax error line 1 at position 29 unexpected '.'.");
        assertRefused("SELECT (v).a FROM vt ORDER BY n", "syntax error line 1 at position 10 unexpected '.'.");
        assertRefused("SELECT PARSE_JSON('{\"a\":1}').a", "syntax error line 1 at position 28 unexpected '.'.");
        assertRefused("SELECT v.a FROM vt ORDER BY n", "invalid identifier 'V.A'");
    }
}
