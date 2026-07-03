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

package dev.frostlake.executor.procedural;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for user-defined exceptions in procedural code
 */
public class UserDefinedExceptionsTest {
    private static final Logger logger = LoggerFactory.getLogger(UserDefinedExceptionsTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for user-defined exception tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testDeclareAndRaiseException() {
        logger.info("Testing DECLARE and RAISE exception");

        String script = """
            DECLARE
                my_exception EXCEPTION (-20002, 'my exception');
            BEGIN
                IF (EXISTS(SELECT 1 AS c)) THEN
                    RAISE my_exception;
                END IF;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20002, exception.getErrorCode());
        assertEquals("my exception", exception.getMessage());

        logger.info("User-defined exception raised correctly");
    }

    @Test
    public void testDeclareExceptionButDontRaise() {
        logger.info("Testing DECLARE exception without raising it");

        String script = """
            DECLARE
                my_exception EXCEPTION (-20001, 'error message');
            BEGIN
                IF (1 = 0) THEN
                    RAISE my_exception;
                END IF;
                RETURN 42;
            END;
            """;

        ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(42L, result.getRows().get(0).getValue(0));

        logger.info("Exception declared but not raised - worked correctly");
    }

    @Test
    public void testMultipleExceptionDeclarations() {
        logger.info("Testing multiple exception declarations");

        String script = """
            DECLARE
                exception1 EXCEPTION (-20001, 'first exception');
                exception2 EXCEPTION (-20002, 'second exception');
            BEGIN
                RAISE exception2;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20002, exception.getErrorCode());
        assertEquals("second exception", exception.getMessage());

        logger.info("Multiple exceptions declared and correct one raised");
    }

    @Test
    public void testRaiseExceptionInElseBranch() {
        logger.info("Testing RAISE exception in ELSE branch");

        String script = """
            DECLARE
                validation_error EXCEPTION (-20100, 'validation failed');
            BEGIN
                IF (EXISTS(SELECT * FROM VALUES(1) WHERE $1 > 10)) THEN
                    RETURN 1;
                ELSE
                    RAISE validation_error;
                END IF;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20100, exception.getErrorCode());
        assertEquals("validation failed", exception.getMessage());

        logger.info("Exception raised in ELSE branch correctly");
    }

    @Test
    public void testRaiseUndefinedException() {
        logger.info("Testing RAISE with undefined exception");

        String script = """
            BEGIN
                RAISE undefined_exception;
            END;
            """;

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery(script);
        });

        assertTrue(exception.getMessage().contains("Undefined exception"));

        logger.info("Undefined exception correctly rejected");
    }

    @Test
    public void testExceptionInNestedIf() {
        logger.info("Testing exception in nested IF");

        String script = """
            DECLARE
                nested_error EXCEPTION (-20200, 'nested error');
            BEGIN
                IF (1 = 1) THEN
                    IF (EXISTS(SELECT 1)) THEN
                        RAISE nested_error;
                    END IF;
                END IF;
                RETURN 0;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20200, exception.getErrorCode());
        assertEquals("nested error", exception.getMessage());

        logger.info("Exception in nested IF raised correctly");
    }

    @Test
    public void testExceptionWithNegativeErrorCode() {
        logger.info("Testing exception with negative error code");

        String script = """
            DECLARE
                my_error EXCEPTION (-30000, 'custom error');
            BEGIN
                RAISE my_error;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-30000, exception.getErrorCode());
        assertEquals("custom error", exception.getMessage());

        logger.info("Negative error code handled correctly");
    }

    @Test
    public void testConditionalExceptionRaise() {
        logger.info("Testing conditional exception raise based on table data");

        engine.execute("CREATE TABLE test_table (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'invalid')");

        String script = """
            DECLARE
                invalid_status EXCEPTION (-20300, 'Invalid status found');
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE status = 'invalid')) THEN
                    RAISE invalid_status;
                END IF;
                RETURN 1;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20300, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid status"));

        logger.info("Conditional exception based on table data works correctly");
    }

    @Test
    public void testExceptionWithSpecialCharactersInMessage() {
        logger.info("Testing exception with special characters in message");

        String script = """
            DECLARE
                special_error EXCEPTION (-20400, 'Error: "quoted", escaped\\\\, and new\\nline');
            BEGIN
                RAISE special_error;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20400, exception.getErrorCode());

        logger.info("Exception with special characters raised correctly");
    }

    @Test
    public void testRaiseExceptionAfterSuccessfulQuery() {
        logger.info("Testing RAISE exception after successful query execution");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1), (2), (3)");

        String script = """
            DECLARE
                post_query_error EXCEPTION (-20500, 'error after query');
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE id > 0)) THEN
                    RAISE post_query_error;
                END IF;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20500, exception.getErrorCode());
        assertEquals("error after query", exception.getMessage());

        logger.info("Exception after query raised correctly");
    }

    @Test
    public void testMultipleIfBranchesWithDifferentExceptions() {
        logger.info("Testing multiple IF branches with different exceptions");

        String script = """
            DECLARE
                error1 EXCEPTION (-20001, 'first error');
                error2 EXCEPTION (-20002, 'second error');
            BEGIN
                IF (1 = 0) THEN
                    RAISE error1;
                ELSEIF (1 = 1) THEN
                    RAISE error2;
                END IF;
                RETURN 0;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20002, exception.getErrorCode());
        assertEquals("second error", exception.getMessage());

        logger.info("Correct exception raised from multiple branches");
    }

    @Test
    public void testExceptionNameCaseInsensitive() {
        logger.info("Testing exception name is case insensitive");

        String script = """
            DECLARE
                My_Exception EXCEPTION (-20600, 'case test');
            BEGIN
                RAISE my_exception;
            END;
            """;

        ProceduralException exception = assertThrows(ProceduralException.class, () -> {
            engine.executeQuery(script);
        });

        assertEquals(-20600, exception.getErrorCode());
        assertEquals("case test", exception.getMessage());

        logger.info("Case insensitive exception name works correctly");
    }
}
