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
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpus runner's skip rule. A case whose {@code skip} names the backend is replayed on {@code engine},
 * {@code jdbc} and {@code http}, on a backend of its own: once it passes it fails as an unexpected pass naming the
 * skip, and while it fails it is reported as skipped with the skip's reason. On {@code snowflake} it is not run.
 */
public class JsonSuiteSkipReplayTest {

    private static final String PASSING = "{\"sql\": \"SELECT 1\", \"expect\": {\"value\": \"1\"}}";
    private static final String FAILING = "{\"sql\": \"SELECT 1\", \"expect\": {\"value\": \"2\"}}";

    private EngineBackend engine;

    @BeforeEach
    public void openBackend() {
        engine = new EngineBackend();
        engine.resetContext();
    }

    @AfterEach
    public void closeBackend() {
        engine.close();
    }

    /** A case skipped on {@code backends} (a JSON list body) for the reason "why", with {@code steps}. */
    private static Map<String, Object> skippedCase(final String backends, final String... steps) {
        return Json.obj(Json.parse("{\"name\": \"c\", \"skip\": {\"backends\": [" + backends
            + "], \"reason\": \"why\"}, \"steps\": [" + String.join(", ", steps) + "]}"));
    }

    private Executable running(final String kind, final Backend target, final Map<String, Object> test) {
        return new Executable() {
            @Override
            public void execute() throws Throwable {
                JsonSuiteTest.runCase(kind, target, "suite/c", test);
            }
        };
    }

    @Test
    public void aSkippedCaseThatPassesFailsAsAnUnexpectedPass() {
        final AssertionFailedError failure = assertThrows(AssertionFailedError.class,
            running("engine", engine, skippedCase("\"engine\"", PASSING)));
        assertEquals("unexpected pass on engine: remove the skip (\"why\")", failure.getMessage());
    }

    @Test
    public void aSkippedCaseThatStillFailsIsReportedAsSkipped() {
        final TestAbortedException skipped = assertThrows(TestAbortedException.class,
            running("engine", engine, skippedCase("\"http\", \"engine\"", PASSING, FAILING)));
        assertEquals("why", skipped.getMessage());
    }

    @Test
    public void aSkippedCaseIsReplayedThroughTheJdbcDriver() {
        final AssertionFailedError failure = assertThrows(AssertionFailedError.class,
            running("jdbc", engine, skippedCase("\"jdbc\"", PASSING)));
        assertEquals("unexpected pass on jdbc: remove the skip (\"why\")", failure.getMessage());
    }

    @Test
    public void aSkippedCaseIsReplayedOnAServerOfItsOwn() {
        final AssertionFailedError failure = assertThrows(AssertionFailedError.class,
            running("http", engine, skippedCase("\"http\"", PASSING)));
        assertEquals("unexpected pass on http: remove the skip (\"why\")", failure.getMessage());
        final TestAbortedException skipped = assertThrows(TestAbortedException.class,
            running("http", engine, skippedCase("\"http\"", FAILING)));
        assertEquals("why", skipped.getMessage());
    }

    @Test
    public void theReplayLeavesNothingInTheRunsBackend() {
        assertThrows(TestAbortedException.class, running("engine", engine, skippedCase("\"engine\"",
            "{\"sql\": \"CREATE DATABASE replay_leftover\"}",
            "{\"sql\": \"CREATE TABLE test_db.test_schema.replay_table (a INT)\"}",
            "{\"sql\": \"SET replay_var = 1\"}",
            FAILING)));
        assertEquals("0", engine.execute("SELECT COUNT(*) FROM test_db.information_schema.tables"
            + " WHERE table_name = 'REPLAY_TABLE'").getRows().get(0).get(0));
        assertEquals(0, engine.execute("SHOW DATABASES LIKE 'REPLAY_LEFTOVER'").getRows().size());
        assertTrue(engine.execute("SELECT $replay_var").failed());
    }

    @Test
    public void aLiveSkippedCaseIsNotRun() {
        // No backend at all: a replay would fail on it, so an abort with the reason shows nothing ran.
        final TestAbortedException skipped = assertThrows(TestAbortedException.class,
            running("snowflake", null, skippedCase("\"snowflake\"", PASSING)));
        assertEquals("why", skipped.getMessage());
    }

    @Test
    public void aCaseSkippedOnAnotherBackendRunsAsUsual() {
        assertDoesNotThrow(running("engine", engine, skippedCase("\"http\", \"snowflake\"", PASSING)));
        final AssertionFailedError failure = assertThrows(AssertionFailedError.class,
            running("engine", engine, skippedCase("\"http\"", FAILING)));
        assertTrue(failure.getMessage().startsWith("suite/c step 1: "), failure.getMessage());
    }
}
