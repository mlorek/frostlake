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

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for ALTER SESSION statement with session parameters
 */
public class AlterSessionTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterSessionTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        logger.info("DatabaseEngine initialized for ALTER SESSION tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterSessionSetMultiStatementCountZero() {
        logger.info("Testing ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");

        // Verify default value
        Object defaultValue = engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        assertEquals(1, defaultValue);

        // Set to 0
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");

        // Verify the value was set
        Object value = engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        assertNotNull(value);
        assertEquals(0L, value);

        logger.info("MULTI_STATEMENT_COUNT set to: {}", value);
    }

    @Test
    public void testAlterSessionSetMultiStatementCountOne() {
        logger.info("Testing ALTER SESSION SET MULTI_STATEMENT_COUNT = 1");

        // Set to 0 first
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");

        // Set back to 1
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 1");

        // Verify the value was set
        Object value = engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        assertNotNull(value);
        assertEquals(1L, value);

        logger.info("MULTI_STATEMENT_COUNT set to: {}", value);
    }

    @Test
    public void testAlterSessionSetMultiStatementCountMultiple() {
        logger.info("Testing ALTER SESSION SET MULTI_STATEMENT_COUNT with multiple values");

        // Set to 5
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 5");
        assertEquals(5L, engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT"));

        // Set to 10
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 10");
        assertEquals(10L, engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT"));

        // Set back to 0
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        assertEquals(0L, engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT"));

        logger.info("Multiple MULTI_STATEMENT_COUNT values set successfully");
    }

    @Test
    public void testAlterSessionSetCustomParameter() {
        logger.info("Testing ALTER SESSION SET with custom parameter");

        // Set a custom parameter
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test_query'");

        // Verify the value was set
        Object value = engine.getSessionContext().getSessionParameter("QUERY_TAG");
        assertNotNull(value);
        assertEquals("test_query", value);

        logger.info("Custom parameter QUERY_TAG set to: {}", value);
    }

    @Test
    public void testAlterSessionSetNumericParameter() {
        logger.info("Testing ALTER SESSION SET with numeric parameter");

        // Set a numeric parameter
        engine.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 3600");

        // Verify the value was set
        Object value = engine.getSessionContext().getSessionParameter("STATEMENT_TIMEOUT_IN_SECONDS");
        assertNotNull(value);
        assertEquals(3600L, value);

        logger.info("Numeric parameter set to: {}", value);
    }

    @Test
    public void testAlterSessionSetBooleanParameter() {
        logger.info("Testing ALTER SESSION SET with boolean parameter");

        // Set a boolean parameter
        engine.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");

        // Verify the value was set
        Object value = engine.getSessionContext().getSessionParameter("AUTOCOMMIT");
        assertNotNull(value);
        assertEquals(true, value);

        logger.info("Boolean parameter set to: {}", value);
    }

    @Test
    public void testSessionParametersPersistAcrossStatements() {
        logger.info("Testing session parameters persist across statements");

        // Set parameter
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 7");

        // Execute other statements
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE TABLE test_table (id INTEGER)");

        // Verify parameter still set
        Object value = engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        assertEquals(7L, value);

        logger.info("Session parameter persisted across statements");
    }

    @Test
    public void testGetAllSessionParameters() {
        logger.info("Testing getAllSessionParameters");

        // Set multiple parameters
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 3");
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test'");
        engine.execute("ALTER SESSION SET AUTOCOMMIT = FALSE");

        // Get all parameters
        var params = engine.getSessionContext().getAllSessionParameters();
        assertNotNull(params);
        assertEquals(3L, params.get("MULTI_STATEMENT_COUNT"));
        assertEquals("test", params.get("QUERY_TAG"));
        assertEquals(false, params.get("AUTOCOMMIT"));

        logger.info("Retrieved all session parameters: {}", params);
    }

    @Test
    public void testSessionParametersResetAfterReset() {
        logger.info("Testing session parameters reset after session reset");

        // Set parameters
        engine.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 5");
        engine.execute("ALTER SESSION SET QUERY_TAG = 'test'");

        // Reset session
        engine.getSessionContext().reset();

        // Verify default value restored
        Object value = engine.getSessionContext().getSessionParameter("MULTI_STATEMENT_COUNT");
        assertEquals(1, value);

        // Verify custom parameter cleared
        Object customParam = engine.getSessionContext().getSessionParameter("QUERY_TAG");
        assertEquals(null, customParam);

        logger.info("Session parameters reset successfully");
    }
}
