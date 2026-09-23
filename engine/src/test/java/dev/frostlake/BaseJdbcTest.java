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

package dev.frostlake;

import dev.frostlake.jdbc.DirectConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for JDBC-based tests with a shared concurrent DatabaseEngine.
 * This provides better performance for test execution as the engine is shared across all tests.
 */
public abstract class BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(BaseJdbcTest.class);

    // Shared concurrent engine for all tests
    protected static DatabaseEngine sharedEngine;

    // Thread-local connection for each test
    protected Connection connection;
    protected Statement statement;

    @BeforeAll
    public static void setupSharedEngine() {
        sharedEngine = new DatabaseEngine();
        logger.info("Shared DatabaseEngine initialized");
    }

    @AfterAll
    public static void teardownSharedEngine() {
        if (sharedEngine != null) {
            sharedEngine.shutdown();
            logger.info("Shared DatabaseEngine shutdown");
        }
    }

    @BeforeEach
    public void setupConnection() throws SQLException {
        // A direct JDBC connection to the shared engine — or, with SF_LIVE=1, a fresh session on a
        // real Snowflake account, so the same java.sql-level tests validate both backends.
        connection = LiveSnowflake.enabled() ? LiveSnowflake.open() : new DirectConnection(sharedEngine);
        statement = connection.createStatement();

        if (LiveSnowflake.enabled()) {
            // Record which account-level objects pre-date the run, before the suite creates any.
            LiveAccountObjects.captureBaseline(connection);
        }

        // Setup test database and schema
        // Drop first to ensure clean state
        statement.execute("DROP DATABASE IF EXISTS test_db");
        statement.execute("CREATE DATABASE test_db");
        statement.execute("USE DATABASE test_db");
        statement.execute("USE SCHEMA PUBLIC");

        // Allow subclasses to add their own setup
        setupTest();
    }

    @AfterEach
    public void teardownConnection() throws SQLException {
        // Allow subclasses to add their own teardown
        teardownTest();

        // Clean up test database — on a live account only while this run holds its claim: after a lost
        // claim, test_db belongs to the run that holds the account now.
        if (!LiveSnowflake.enabled() || LiveSnowflake.holdsAccountClaim()) {
            try {
                statement.execute("DROP DATABASE IF EXISTS test_db");
            } catch (final Exception e) {
                // Ignore errors during cleanup
            }
        }

        // Roles, users, warehouses and sibling databases live OUTSIDE test_db, so dropping it leaves
        // them on the account to collide with the next test that creates the same name. Nothing here
        // observes the raw java.sql statements these tests run, so the diff is unconditional.
        if (LiveSnowflake.enabled()) {
            LiveAccountObjects.dropNewAccountObjects(connection);
        }

        // Close resources
        if (statement != null) {
            try {
                statement.close();
            } catch (final SQLException e) {
                // Ignore
            }
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (final SQLException e) {
                // Ignore
            }
        }
    }

    /** Whether this run targets live Snowflake — for tests that must skip driver-internal checks there. */
    protected static boolean isLiveSnowflake() {
        return LiveSnowflake.enabled();
    }

    /**
     * Override this method to add test-specific setup after connection is established
     */
    protected void setupTest() throws SQLException {
        // Default: no additional setup
    }

    /**
     * Override this method to add test-specific teardown before connection is closed
     */
    protected void teardownTest() throws SQLException {
        // Default: no additional teardown
    }
}
