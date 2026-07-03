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
 * Base class for all Frostlake engine tests providing common setup/teardown
 */
public abstract class BaseDatabaseTest {

    protected DatabaseEngine engine;

    @BeforeEach
    public void baseSetup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        // Allow subclasses to add their own setup
        setupTest();
    }

    @AfterEach
    public void baseTeardown() {
        // Allow subclasses to add their own teardown
        teardownTest();

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
