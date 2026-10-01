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

package dev.frostlake.testkit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The corpus runner's teardown on a live account: it drops the run's {@code test_db} only while the run holds the
 * account's claim, since a run that lost its claim would drop the {@code test_db} of the run holding the account
 * now. An in-process engine stands in for the live backend; the statement is the same.
 */
public class JsonSuiteLiveTeardownTest {

    private EngineBackend backend;

    @BeforeEach
    public void openBackend() throws Exception {
        backend = new EngineBackend();
        backend.resetContext();
    }

    @AfterEach
    public void closeBackend() {
        backend.close();
    }

    private int testDatabases() {
        return backend.execute("SHOW DATABASES LIKE 'TEST_DB'").getRows().size();
    }

    @Test
    public void aRunHoldingTheClaimDropsItsTestDatabase() throws Exception {
        assertEquals(1, testDatabases());
        JsonSuiteTest.dropLiveTestDatabase(backend, true);
        assertEquals(0, testDatabases());
    }

    @Test
    public void aRunThatLostTheClaimLeavesTestDatabaseToTheHolder() throws Exception {
        JsonSuiteTest.dropLiveTestDatabase(backend, false);
        assertEquals(1, testDatabases());
    }
}
