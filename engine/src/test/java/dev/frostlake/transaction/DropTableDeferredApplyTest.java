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
 * DROP TABLE / CREATE OR REPLACE TABLE wired through the deferred-apply write set
 * ({@code transaction.deferredApply=true}). When a table is dropped (or replaced) after the SAME
 * transaction has buffered INSERT/UPDATE/DELETEs against it, those buffered writes must be discarded:
 *
 * <ul>
 *   <li>otherwise COMMIT tries to flush them to storage that no longer exists and fails with
 *       "Failed to commit transaction" (cause: "Table storage does not exist"); and</li>
 *   <li>a drop-then-recreate of the same name in one transaction would otherwise resurrect the
 *       pre-drop rows in the new table.</li>
 * </ul>
 *
 * The trigger in practice is a stored procedure — the whole CALL is one deferred transaction — that
 * seeds a scratch/temporary table and DROPs it before returning (a very common test-proc cleanup shape).
 */
public class DropTableDeferredApplyTest {

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
        engine.execute("CREATE TABLE keep (id INTEGER)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long countKeep() {
        return engine.executeQuery("SELECT id FROM keep").getRowCount();
    }

    @Test
    public void insertThenDropInSameTransactionCommitsCleanly() {
        engine.setAutoCommit(false);
        engine.execute("CREATE TEMPORARY TABLE scratch (a INTEGER)");
        engine.execute("INSERT INTO scratch VALUES (1), (2), (3)");
        engine.execute("DROP TABLE scratch");
        engine.execute("INSERT INTO keep VALUES (10)");
        // The buffered scratch inserts must not be flushed to the now-dropped table's (missing) storage.
        engine.execute("COMMIT");
        assertEquals(1, countKeep(), "the transaction's other work still commits");
    }

    @Test
    public void dropThenRecreateSameNameDoesNotResurrectRows() {
        engine.setAutoCommit(false);
        engine.execute("CREATE TEMPORARY TABLE scratch (a INTEGER)");
        engine.execute("INSERT INTO scratch VALUES (1), (2)");
        engine.execute("DROP TABLE scratch");
        engine.execute("CREATE TEMPORARY TABLE scratch (a INTEGER)");
        engine.execute("INSERT INTO scratch VALUES (99)");
        engine.execute("COMMIT");
        assertEquals(1, engine.executeQuery("SELECT a FROM scratch").getRowCount(),
            "only the post-recreate row survives");
        assertEquals(99L, ((Number) engine.executeQuery("SELECT a FROM scratch")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void createOrReplaceAfterBufferedInsertsStartsEmpty() {
        engine.setAutoCommit(false);
        engine.execute("CREATE TEMPORARY TABLE scratch (a INTEGER)");
        engine.execute("INSERT INTO scratch VALUES (1), (2), (3)");
        // CREATE OR REPLACE drops the buffered rows with the old table.
        engine.execute("CREATE OR REPLACE TEMPORARY TABLE scratch (a INTEGER)");
        assertEquals(0, engine.executeQuery("SELECT a FROM scratch").getRowCount(),
            "the replaced table is empty of pre-replace buffered rows");
        engine.execute("INSERT INTO scratch VALUES (7)");
        engine.execute("COMMIT");
        assertEquals(1, engine.executeQuery("SELECT a FROM scratch").getRowCount());
    }

    @Test
    public void procSeedsThenDropsTempTable() {
        // The staged-CALL shape: one CALL == one deferred transaction; the proc creates a scratch table,
        // seeds it, uses it, then DROPs it on the way out. This crashed at commit before the fix.
        engine.execute("""
            CREATE PROCEDURE publish() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE n INTEGER;
            BEGIN
              CREATE OR REPLACE TEMPORARY TABLE s.expected (a INTEGER);
              INSERT INTO s.expected VALUES (1), (2), (3);
              n := (SELECT COUNT(*) FROM s.expected);
              INSERT INTO keep VALUES (:n);
              DROP TABLE IF EXISTS s.expected;
              RETURN 'ok';
            END $$
            """);
        final Object result = engine.executeQuery("CALL publish()").getRows().get(0).getValue(0);
        assertEquals("ok", String.valueOf(result));
        assertEquals(1, countKeep(), "the proc's durable write committed");
        assertEquals(3L, ((Number) engine.executeQuery("SELECT id FROM keep").getRows().get(0).getValue(0)).longValue(),
            "and it observed all three scratch rows before the drop");
    }

    @Test
    public void procDropRecreateSameNameKeepsOnlyNewRows() {
        engine.execute("""
            CREATE PROCEDURE churn() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              CREATE OR REPLACE TEMPORARY TABLE s.tmp (a INTEGER);
              INSERT INTO s.tmp VALUES (10), (20);
              DROP TABLE IF EXISTS s.tmp;
              CREATE OR REPLACE TEMPORARY TABLE s.tmp (a INTEGER);
              INSERT INTO s.tmp VALUES (99);
              RETURN 'ok';
            END $$
            """);
        engine.executeQuery("CALL churn()");
        assertEquals(1, engine.executeQuery("SELECT a FROM s.tmp").getRowCount(),
            "drop-then-recreate inside the proc keeps only the post-recreate row");
    }
}
