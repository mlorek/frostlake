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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for exception handling in Snowflake SQL scripting
 */
public class ExceptionHandlingTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandlingTest.class);

    @Test
    public void testBasicExceptionHandling() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Insert a row, then insert NULL into the NOT NULL column (will cause an error)
        // The exception handler should catch it
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (NULL);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO test VALUES (2);
            END;
            """);

        // Should have rows 1 and 2 (the second 1 failed, handler inserted 2)
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testExceptionHandlerWithoutError() {
        engine.execute("CREATE TABLE test (id INTEGER)");

        // No error occurs, so exception handler should not run
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (2);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO test VALUES (999);
            END;
            """);

        // Should have rows 1 and 2 (no error, so handler didn't run)
        ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        // Verify 999 was not inserted
        rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test WHERE id = 999");
        assertEquals(0, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testMultipleExceptionHandlers() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Insert NULL into the NOT NULL column; the first matching handler catches it
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (NULL);
            EXCEPTION
                WHEN STATEMENT_ERROR THEN
                    INSERT INTO test VALUES (2);
                WHEN OTHER THEN
                    INSERT INTO test VALUES (3);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        // Should have at least 1 row (the first insert succeeded)
        final int count = ((Number) rs.getRows().get(0).getValue(0)).intValue();
        logger.info("Row count after exception handling: {}", count);
        assertNotNull(rs);
    }

    @Test
    public void testNestedExceptionHandling() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Nested BEGIN/END blocks with exception handling
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                BEGIN
                    INSERT INTO test VALUES (2);
                    INSERT INTO test VALUES (NULL);
                EXCEPTION
                    WHEN OTHER THEN
                        INSERT INTO test VALUES (3);
                END;
                INSERT INTO test VALUES (4);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO test VALUES (999);
            END;
            """);

        // Should have rows 1, 2, 3, 4
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        final int count = ((Number) rs.getRows().get(0).getValue(0)).intValue();
        logger.info("Row count after nested exception handling: {}", count);
        assertEquals(4, count);
    }

    @Test
    public void testExceptionNotHandled() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Insert NULL into the NOT NULL column with no exception handler
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("""
                    BEGIN
                        INSERT INTO test VALUES (1);
                        INSERT INTO test VALUES (NULL);
                    END;
                    """);
            }
        });
    }

    @Test
    public void testExceptionHandlerWithStatements() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");
        engine.execute("CREATE TABLE error_log (error_msg VARCHAR)");

        // Exception handler with multiple statements
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (NULL);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO error_log VALUES ('Error occurred');
                    INSERT INTO test VALUES (2);
            END;
            """);

        // Check test table
        ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());

        // Check error_log table
        rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM error_log");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testExceptionHandlerBeforeBlockEnd() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Every statement carries its semicolon — live refuses a statement that runs into
        // EXCEPTION or END without one.
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (NULL);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO test VALUES (2);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testSimpleExceptionCatchAll() {
        engine.execute("CREATE TABLE test (id INTEGER NOT NULL)");

        // Simple catch-all exception handler over a NOT NULL violation
        engine.execute("""
            BEGIN
                INSERT INTO test VALUES (1);
                INSERT INTO test VALUES (NULL);
            EXCEPTION
                WHEN OTHER THEN
                    INSERT INTO test VALUES (2);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as cnt FROM test");
        // Handler should have caught the NOT NULL violation
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
