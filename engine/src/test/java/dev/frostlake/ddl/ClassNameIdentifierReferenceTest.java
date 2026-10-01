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

package dev.frostlake.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * An IDENTIFIER() reference names the class of {@code SHOW <class>} and {@code DROP <class> <instance>} as a written
 * name does: its value resolves as any object name's, and no class is found — not even for the word of a kind
 * Frostlake lists, {@code IDENTIFIER('table')}. A class named so takes no CASCADE or RESTRICT. Every cell is
 * live-verified.
 */
public class ClassNameIdentifierReferenceTest extends BaseDatabaseTest {

    private static final String NO_CLASS_X = "SQL compilation error: Object type or Class 'X' does not exist or not authorized.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aReferenceNamesAClassThatDoesNotExist() {
        assertEquals(NO_CLASS_X, answer("SHOW IDENTIFIER('x')"));
        assertEquals(NO_CLASS_X, answer("SHOW TERSE IDENTIFIER('x')"));
        assertEquals(NO_CLASS_X, answer("SHOW IDENTIFIER('x') LIKE 'a'"));
        assertEquals(NO_CLASS_X, answer("SHOW IDENTIFIER('x') IN ACCOUNT"));
        assertEquals(NO_CLASS_X, answer("DROP IDENTIFIER('x') y"));
        assertEquals(NO_CLASS_X, answer("DROP IDENTIFIER('x') IF EXISTS y"));
        assertEquals(NO_CLASS_X, answer("DROP IDENTIFIER('x') IDENTIFIER('y')"));
        assertEquals("SQL compilation error: Object type or Class 'TABLE' does not exist or not authorized.",
            answer("DROP IDENTIFIER('table') t1"));
        assertEquals("SQL compilation error: Object type or Class 'TABLES' does not exist or not authorized.",
            answer("SHOW IDENTIFIER('tables')"));
        assertEquals("SQL compilation error: Object type or Class '\"x\"' does not exist or not authorized.",
            answer("SHOW IDENTIFIER('\"x\"')"));
        engine.execute("SET cls = 'x'");
        assertEquals(NO_CLASS_X, answer("SHOW IDENTIFIER($cls)"));
        assertEquals(NO_CLASS_X, answer("DROP IDENTIFIER($cls) y"));
    }

    @Test
    public void theReferenceResolvesAsAnObjectNameDoes() {
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.A' does not exist or not authorized."),
            answer("SHOW IDENTIFIER('a.b')"));
        assertEquals(hinted("SQL compilation error:|Database 'A' does not exist or not authorized."),
            answer("DROP IDENTIFIER('a.b.c') y"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("DROP IDENTIFIER('test_db.nosch.c') y"));
        assertEquals("SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.C' does not exist or not "
            + "authorized.", answer("SHOW IDENTIFIER('test_schema.c')"));
        assertEquals("SQL compilation error: error line 1 at position 16|invalid identifier ''x y''",
            answer("SHOW IDENTIFIER('x y')"));
        assertEquals("SQL compilation error: error line 1 at position 16|Session variable '$NOSUCHVAR' does not exist",
            answer("SHOW IDENTIFIER($nosuchvar)"));
    }

    @Test
    public void aReferencedClassTakesNoCascadeAndNeedsItsInstance() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'CASCADE'.",
            answer("DROP IDENTIFIER('x') y CASCADE"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'RESTRICT'.",
            answer("DROP IDENTIFIER('x') y RESTRICT"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 20 unexpected '<EOF>'.",
            answer("DROP IDENTIFIER('x')"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 21 unexpected 'y'.",
            answer("SHOW IDENTIFIER('x') y"));
    }
}
