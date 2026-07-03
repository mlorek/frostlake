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

package dev.frostlake.transaction;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INSERT wired through the deferred-apply write set ({@code transaction.deferredApply=true}; see
 * docs/acid-snowflake-plan.md). Verifies: an autocommit INSERT is applied at statement end; inside an
 * explicit (autocommit-off) transaction the INSERT is visible to the transaction itself through the
 * read overlay, ROLLBACK discards it (never reaches the base store), and COMMIT applies it.
 *
 * <p>The flag defaults off, so the engine's other ~2,700 tests still exercise the immediate-apply path.
 */
public class InsertDeferredApplyTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_TRANSACTION_DEFERRED_APPLY, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long rowCount() {
        return engine.executeQuery("SELECT id FROM t").getRowCount();
    }

    @Test
    public void autocommitInsertIsApplied() {
        engine.execute("INSERT INTO t VALUES (1, 'a')");   // autocommit → applied at statement end
        assertEquals(1, rowCount());
    }

    @Test
    public void rollbackDiscardsBufferedInsert() {
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        assertEquals(1, rowCount(), "own write must be visible to its own transaction via the overlay");

        engine.execute("ROLLBACK");
        assertEquals(0, rowCount(), "rolled-back insert must never reach the base store");
    }

    @Test
    public void commitAppliesBufferedInserts() {
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(2, rowCount());
    }
}
