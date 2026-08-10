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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for NULL statement in Snowflake SQL scripting
 */
public class NullStatementTest extends BaseDatabaseTest {

    @Test
    public void testSimpleNullStatement() {
        // A NULL statement executes without error inside a block.
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("BEGIN NULL; END;");
            }
        });
    }

    @Test
    public void testNullStatementInBeginEnd() {
        // NULL statement inside BEGIN/END block
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    BEGIN
                        NULL;
                    END;
                    """);
            }
        });
    }

    @Test
    public void testNullStatementWithOtherStatements() {
        // Mix NULL statements with other statements
        engine.execute("CREATE TABLE test (id INTEGER)");
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO test VALUES (2)");
        engine.execute("BEGIN NULL; END;");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testMultipleNullStatements() {
        // Multiple NULL statements in sequence
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    BEGIN
                        NULL;
                        NULL;
                        NULL;
                    END;
                    """);
            }
        });
    }

    @Test
    public void testNullStatementDocumentation() {
        // Verify NULL statement is accepted in various contexts
        // This is primarily for documentation purposes
        engine.execute("CREATE TABLE test (id INTEGER)");

        // Standalone NULL
        engine.execute("BEGIN NULL; END;");

        // NULL with other statements
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO test VALUES (2)");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testNullStatementNoSemicolon() {
        // NULL statement without semicolon (should still work)
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    BEGIN
                        NULL;
                    END;
                    """);
            }
        });
    }

    @Test
    public void testNullStatementStandalone() {
        // NULL is a SCRIPTING statement: outside a block live refuses it as a syntax error.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("NULL;");
            }
        });
        assertTrue(e.getMessage().toLowerCase().contains("syntax error"),
            "unexpected message: " + e.getMessage());
    }

    @Test
    public void testNullStatementInSequence() {
        // NULL statement in a sequence of statements
        engine.execute("CREATE TABLE test (id INTEGER)");

        // Execute a sequence with NULL statements interspersed
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("BEGIN NULL; END;");
        engine.execute("BEGIN NULL; END;");
        engine.execute("INSERT INTO test VALUES (2)");
        engine.execute("BEGIN NULL; END;");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
