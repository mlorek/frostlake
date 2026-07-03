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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UPDATE and DELETE wired through the deferred-apply write set ({@code transaction.deferredApply=true};
 * see docs/acid-snowflake-plan.md). Inside an explicit (autocommit-off) transaction, changes are visible
 * to the transaction itself through the read overlay, ROLLBACK discards them (the base store is never
 * touched), and COMMIT applies them — including modifying or deleting rows the same transaction just
 * inserted. The flag defaults off, so the ~2,700 other tests keep exercising the immediate-apply path.
 */
public class UpdateDeleteDeferredApplyTest {

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
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");   // autocommit → committed base rows
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long count(final String tail) {
        return engine.executeQuery("SELECT id FROM t " + tail).getRowCount();
    }

    private String vWhereId(final int id) {
        final ResultSet rs = engine.executeQuery("SELECT v FROM t WHERE id = " + id);
        return rs.getRowCount() == 0 ? null : rs.getRows().get(0).getValue(0).toString();
    }

    @Test
    public void updateRollbackRestoresOriginal() {
        engine.setAutoCommit(false);
        engine.execute("UPDATE t SET v = 'X' WHERE id = 1");
        assertEquals("X", vWhereId(1), "own update must be visible to its own transaction");
        engine.execute("ROLLBACK");
        assertEquals("a", vWhereId(1), "rolled-back update must never reach the base store");
    }

    @Test
    public void updateCommitApplies() {
        engine.setAutoCommit(false);
        engine.execute("UPDATE t SET v = 'X' WHERE id = 1");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals("X", vWhereId(1));
    }

    @Test
    public void deleteRollbackRestores() {
        engine.setAutoCommit(false);
        engine.execute("DELETE FROM t WHERE id = 1");
        assertEquals(1, count(""), "own delete must be visible to its own transaction");
        engine.execute("ROLLBACK");
        assertEquals(2, count(""), "rolled-back delete must restore the row");
    }

    @Test
    public void deleteCommitApplies() {
        engine.setAutoCommit(false);
        engine.execute("DELETE FROM t WHERE id = 1");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(1, count(""));
        assertNull(vWhereId(1));
    }

    @Test
    public void insertThenUpdateSameTransaction() {
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (5, 'e')");
        engine.execute("UPDATE t SET v = 'E' WHERE id = 5");
        assertEquals("E", vWhereId(5), "update must apply to a row inserted in the same transaction");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals("E", vWhereId(5));
    }

    @Test
    public void insertThenDeleteSameTransaction() {
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (6, 'f')");
        engine.execute("DELETE FROM t WHERE id = 6");
        assertEquals(0, count("WHERE id = 6"), "delete must drop a row inserted in the same transaction");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(0, count("WHERE id = 6"));
        assertEquals(2, count(""));   // back to the two seed rows
    }
}
