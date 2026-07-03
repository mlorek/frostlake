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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for NULL statement in Snowflake SQL scripting
 */
public class NullStatementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSimpleNullStatement() {
        // NULL statement should execute without error
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("NULL;");
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
        engine.execute("NULL");
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("NULL");
        engine.execute("INSERT INTO test VALUES (2)");
        engine.execute("NULL");

        ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
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
        engine.execute("NULL;");

        // NULL with other statements
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("NULL");
        engine.execute("INSERT INTO test VALUES (2)");

        ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
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
                        NULL
                    END;
                    """);
            }
        });
    }

    @Test
    public void testNullStatementStandalone() {
        // NULL statement as standalone statement (not in block)
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("NULL");
            }
        });
    }

    @Test
    public void testNullStatementInSequence() {
        // NULL statement in a sequence of statements
        engine.execute("CREATE TABLE test (id INTEGER)");

        // Execute a sequence with NULL statements interspersed
        engine.execute("NULL");
        engine.execute("INSERT INTO test VALUES (1)");
        engine.execute("NULL");
        engine.execute("NULL");
        engine.execute("INSERT INTO test VALUES (2)");
        engine.execute("NULL");

        ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
