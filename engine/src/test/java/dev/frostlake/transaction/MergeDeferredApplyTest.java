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
 * MERGE wired through the deferred-apply write set ({@code transaction.deferredApply=true}; see
 * docs/acid-snowflake-plan.md). MERGE was previously fully non-transactional — it logged nothing, so a
 * ROLLBACK silently failed to undo it. Now WHEN MATCHED UPDATE/DELETE and WHEN NOT MATCHED INSERT buffer
 * into the write set (matched target rows by stable id), are visible to the transaction via the read
 * overlay, discarded by ROLLBACK and applied by COMMIT. The flag defaults off.
 */
public class MergeDeferredApplyTest {

    private static final String MERGE_UPSERT = """
        MERGE INTO t USING src ON t.id = src.id
        WHEN MATCHED THEN UPDATE SET v = src.v
        WHEN NOT MATCHED THEN INSERT (id, v) VALUES (src.id, src.v)
        """;

    private static final String MERGE_DELETE = """
        MERGE INTO t USING src ON t.id = src.id
        WHEN MATCHED THEN DELETE
        """;

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
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (2, 'B2'), (3, 'c3')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long count() {
        return engine.executeQuery("SELECT id FROM t").getRowCount();
    }

    private String vWhereId(final int id) {
        final ResultSet rs = engine.executeQuery("SELECT v FROM t WHERE id = " + id);
        return rs.getRowCount() == 0 ? null : rs.getRows().get(0).getValue(0).toString();
    }

    @Test
    public void autocommitMergeApplies() {
        engine.execute(MERGE_UPSERT);   // autocommit → applied at statement end
        assertEquals(3, count());
        assertEquals("B2", vWhereId(2));   // matched → updated
        assertEquals("c3", vWhereId(3));   // not matched → inserted
    }

    @Test
    public void mergeUpsertRollbackDiscards() {
        engine.setAutoCommit(false);
        engine.execute(MERGE_UPSERT);
        assertEquals("B2", vWhereId(2), "own merge must be visible to its transaction");
        assertEquals(3, count());
        engine.execute("ROLLBACK");
        assertEquals("b", vWhereId(2), "rolled-back merge update must not reach the base store");
        assertEquals(2, count());
        assertNull(vWhereId(3), "rolled-back merge insert must not reach the base store");
    }

    @Test
    public void mergeUpsertCommitApplies() {
        engine.setAutoCommit(false);
        engine.execute(MERGE_UPSERT);
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(3, count());
        assertEquals("B2", vWhereId(2));
        assertEquals("c3", vWhereId(3));
    }

    @Test
    public void mergeDeleteRollbackRestores() {
        engine.setAutoCommit(false);
        engine.execute(MERGE_DELETE);
        assertEquals(1, count(), "own merge-delete must be visible to its transaction");
        engine.execute("ROLLBACK");
        assertEquals(2, count(), "rolled-back merge-delete must restore the row");
    }

    @Test
    public void mergeDeleteCommitApplies() {
        engine.setAutoCommit(false);
        engine.execute(MERGE_DELETE);
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(1, count());
        assertNull(vWhereId(2));
    }
}
