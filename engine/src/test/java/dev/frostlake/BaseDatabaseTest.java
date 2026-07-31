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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for all Frostlake engine tests providing common setup/teardown.
 *
 * <p>With {@code SF_LIVE=1} in the environment (see {@link LiveSnowflake}) the SQL these tests
 * submit runs against a real Snowflake account instead of the embedded engine — the same suite,
 * flipped by a switch, to confirm Frostlake and Snowflake agree. In live mode {@code test_db} is
 * recreated on the account before each test for isolation.
 */
public abstract class BaseDatabaseTest {

    protected DatabaseEngine engine;

    @BeforeEach
    public void baseSetup() {
        if (LiveSnowflake.enabled()) {
            engine = new LiveSnowflakeEngine();
            // A previous test may have left the shared session in an open transaction or with
            // autocommit/role/warehouse moved — restore the baseline before touching test_db.
            LiveSnowflake.resetSharedIfDirty();
            // Record which account-level objects pre-date the run, before the suite creates any.
            LiveAccountObjects.captureBaseline(LiveSnowflake.shared());
            engine.execute("CREATE OR REPLACE DATABASE test_db");
            // Everything the TEST creates from here on is the test's to clean up, not the harness's.
            LiveAccountObjects.beginTest();
        } else {
            engine = new DatabaseEngine();
            engine.execute("CREATE DATABASE test_db");
        }
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        // Allow subclasses to add their own setup
        setupTest();
    }

    /** Whether this run targets live Snowflake — for tests that must skip engine-internal checks there. */
    protected static boolean isLiveSnowflake() {
        return LiveSnowflake.enabled();
    }

    @AfterEach
    public void baseTeardown() {
        // Allow subclasses to add their own teardown
        teardownTest();

        // Recreating test_db isolates everything INSIDE a database; roles, users, warehouses and
        // sibling databases outlive it and would collide with the next test that uses the same name.
        if (LiveSnowflake.enabled() && LiveAccountObjects.sawAccountObjectStatement()) {
            // Restore the session's role/warehouse first: dropping the role a test switched TO would
            // otherwise leave the shared session without a usable one.
            LiveSnowflake.resetSharedIfDirty();
            LiveAccountObjects.dropNewAccountObjects(LiveSnowflake.shared());
        }

        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * Override this method to add test-specific setup
     */
    protected void setupTest() {
        // Default: no additional setup
    }

    /**
     * Override this method to add test-specific teardown
     */
    protected void teardownTest() {
        // Default: no additional teardown
    }
}
