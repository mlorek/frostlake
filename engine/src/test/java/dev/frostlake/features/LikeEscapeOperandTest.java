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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What may follow LIKE's ESCAPE. Only a bare literal, NULL or a session variable stands there: a
 * parenthesis, a cast, a number, a keyword, a column name, a sign, a binary literal, a second literal
 * and a second ESCAPE are each a syntax error at the token itself. The value is then one character —
 * the empty string and any longer run are refused by value — and a NULL escape makes the single-pattern
 * predicate UNKNOWN while the multi-pattern form answers as the default escape would. Live-verified.
 */
public class LikeEscapeOperandTest extends BaseDatabaseTest {

    /** The single cell of a single-row query, as upper-cased text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString().toUpperCase();
    }

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** A bare literal, a dollar-quoted one and a session variable all stand after ESCAPE. */
    @Test
    public void escapeTakesALiteralOrAVariable() {
        assertEquals("TRUE", answer("SELECT 'a' LIKE 'a' ESCAPE '!'"));
        assertEquals("TRUE", answer("SELECT 'a' LIKE 'a' ESCAPE $$!$$"));
        assertEquals("TRUE", answer("SELECT 'a' ILIKE 'a' ESCAPE '!'"));
        assertEquals("FALSE", answer("SELECT 'a' NOT LIKE 'a' ESCAPE '!'"));
        assertEquals("TRUE", answer("SELECT 'a' LIKE ANY ('a') ESCAPE '!'"));
        assertEquals("TRUE", answer("SELECT 'a' LIKE ALL ('a') ESCAPE '!'"));
        assertEquals("TRUE", answer("SELECT 'a' ILIKE ANY ('a') ESCAPE '!'"));
        assertEquals("TRUE", answer("SELECT 'a' LIKE 'a' ESCAPE '!' AND 1 = 1"));
        engine.execute("SET like_escape_var = '!'");
        try {
            assertEquals("TRUE", answer("SELECT 'a' LIKE 'a' ESCAPE $like_escape_var"));
        } finally {
            engine.execute("UNSET like_escape_var");
        }
    }

    /** Anything built from a literal is a syntax error at the token that starts it. */
    @Test
    public void escapeRefusesAnythingBuiltFromALiteral() {
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE ('!')", "unexpected '('");
        assertRefused("SELECT 'a' ILIKE 'a' ESCAPE ('!')", "unexpected '('");
        assertRefused("SELECT 'a' NOT LIKE 'a' ESCAPE ('!')", "unexpected '('");
        assertRefused("SELECT 'a' LIKE ANY ('a') ESCAPE ('!')", "unexpected '('");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE ('!' COLLATE 'de')", "unexpected '('");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE 1", "unexpected '1'");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE TRUE", "unexpected 'TRUE'");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE -'!'", "unexpected '-'");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE X'21'", "unexpected 'X'21''");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE 'a' 'b'", "unexpected ''b''");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE '!' ESCAPE '?'", "unexpected ''?''");
        // A COLLATE after the escape has nothing to attach to, while one on the PATTERN is ordinary.
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE '!' COLLATE 'de'", "unexpected ''de''");
        assertEquals("TRUE", answer("SELECT 'a' LIKE 'a' COLLATE 'de' ESCAPE '!'"));
    }

    /** A column name is no escape either, even where one is in scope. */
    @Test
    public void escapeRefusesAColumn() {
        engine.execute("CREATE OR REPLACE TABLE like_escape_source (c VARCHAR)");
        engine.execute("INSERT INTO like_escape_source VALUES ('!')");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE c FROM like_escape_source", "unexpected 'c'");
    }

    /** The escape is ONE character; the empty string and any longer run are refused by value. */
    @Test
    public void escapeIsExactlyOneCharacter() {
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE ''", "invalid value [''] for parameter 'escape'");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE '!!'", "invalid value ['!!'] for parameter 'escape'");
        assertRefused("SELECT 'a' LIKE 'a' ESCAPE '  '", "invalid value ['  '] for parameter 'escape'");
    }

    /** A NULL escape makes the single-pattern predicate UNKNOWN; the multi-pattern form answers on. */
    @Test
    public void escapeNullMakesTheSinglePatternPredicateUnknown() {
        assertEquals("NULL", answer("SELECT 'a' LIKE 'a' ESCAPE NULL"));
        assertEquals("NULL", answer("SELECT 'a' LIKE 'ab' ESCAPE NULL"));
        assertEquals("NULL", answer("SELECT 'a' NOT LIKE 'a' ESCAPE NULL"));
        assertEquals("TRUE", answer("SELECT 'a' LIKE ANY ('a') ESCAPE NULL"));
    }
}
