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
 * TRUNCATE wired through the deferred-apply write set ({@code transaction.deferredApply=true}). A TRUNCATE
 * inside an open transaction must clear rows the SAME transaction has buffered (its not-yet-committed
 * INSERTs), not just committed base rows — otherwise a "TRUNCATE then reload" of a table earlier populated
 * in the same transaction leaves the earlier rows behind. It must also stay rollback-safe: a rolled-back
 * TRUNCATE restores the committed rows.
 */
public class TruncateDeferredApplyTest {

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
    public void truncateClearsBufferedInsertsInSameTransaction() {
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        assertEquals(3, rowCount(), "own inserts visible through the overlay");
        engine.execute("TRUNCATE TABLE t");
        assertEquals(0, rowCount(), "TRUNCATE must clear the transaction's own buffered inserts");
    }

    @Test
    public void truncateThenReinsertKeepsOnlyReinsertedRows() {
        // The reload pattern: populate, then later TRUNCATE + reload within one transaction.
        engine.setAutoCommit(false);
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("TRUNCATE TABLE t");
        engine.execute("INSERT INTO t VALUES (4, 'd'), (5, 'e')");
        assertEquals(2, rowCount(), "only the rows inserted after TRUNCATE remain");
        engine.execute("COMMIT");
        assertEquals(2, rowCount(), "and that survives commit");
    }

    @Test
    public void truncateClearsCommittedRowsWithinTransaction() {
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')"); // autocommit → committed
        assertEquals(2, rowCount());
        engine.setAutoCommit(false);
        engine.execute("TRUNCATE TABLE t");
        assertEquals(0, rowCount(), "committed rows are gone within the transaction");
        engine.execute("COMMIT");
        assertEquals(0, rowCount());
    }

    @Test
    public void rollbackRestoresTruncatedCommittedRows() {
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c')"); // committed
        engine.setAutoCommit(false);
        engine.execute("TRUNCATE TABLE t");
        assertEquals(0, rowCount(), "TRUNCATE visible within its transaction");
        engine.execute("ROLLBACK");
        assertEquals(3, rowCount(), "a rolled-back TRUNCATE restores the committed rows");
    }

    @Test
    public void autocommitTruncateClearsTable() {
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        engine.execute("TRUNCATE TABLE t");
        assertEquals(0, rowCount(), "a standalone autocommit TRUNCATE clears the table");
    }

    @Test
    public void truncateAfterNestedCallClearsBufferedInserts() {
        // The trigger case: a nested CALL runs between the INSERTs and the TRUNCATE.
        engine.execute("CREATE PROCEDURE noop() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        engine.execute("""
            CREATE PROCEDURE reload() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE r VARCHAR; n INTEGER;
            BEGIN
              INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c');
              r := (CALL noop());
              TRUNCATE TABLE t;
              INSERT INTO t VALUES (4, 'd'), (5, 'e');
              n := (SELECT COUNT(*) FROM t);
              RETURN 'n=' || :n;
            END $$
            """);
        final Object result = engine.executeQuery("CALL reload()").getRows().get(0).getValue(0);
        assertEquals("n=2", String.valueOf(result));
        assertEquals(2, rowCount(), "table holds only the post-TRUNCATE inserts");
    }
}
