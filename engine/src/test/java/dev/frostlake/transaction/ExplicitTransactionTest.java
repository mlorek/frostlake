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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake autocommit semantics (Phase 2): an explicit {@code BEGIN} suspends autocommit until
 * {@code COMMIT}/{@code ROLLBACK}, even though autocommit is ON by default — you do NOT have to
 * {@code SET AUTOCOMMIT=FALSE}. Previously {@code BEGIN} was auto-committed at statement end, so explicit
 * multi-statement transactions silently didn't group. Uses the default engine (autocommit + deferred apply
 * both on). See docs/acid-snowflake-plan.md.
 */
public class ExplicitTransactionTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
    }

    private long rowCount() {
        return engine.executeQuery("SELECT id FROM t").getRowCount();
    }

    @Test
    public void explicitBeginRollbackDiscardsAcrossStatements() {
        engine.execute("BEGIN");                          // autocommit suspended
        engine.execute("INSERT INTO t VALUES (1, 'a')");  // not committed
        assertEquals(1, rowCount(), "own write is visible inside the explicit transaction");
        engine.execute("ROLLBACK");
        assertEquals(0, rowCount(), "ROLLBACK must discard the explicit transaction's writes");
    }

    @Test
    public void explicitBeginCommitPersists() {
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("COMMIT");
        assertEquals(2, rowCount());
    }

    @Test
    public void explicitBeginRollbackIsAtomicOverMultipleStatements() {
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("ROLLBACK");
        assertEquals(0, rowCount(), "every statement in the explicit transaction rolls back together");
    }

    @Test
    public void autocommitStillCommitsEachStatementWithoutBegin() {
        engine.execute("INSERT INTO t VALUES (1, 'a')");   // no BEGIN → autocommit
        assertEquals(1, rowCount());
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        assertEquals(2, rowCount());
    }

    @Test
    public void singleScriptBeginInsertCommitPersists() {
        engine.execute("""
            BEGIN;
            INSERT INTO t VALUES (1, 'a');
            COMMIT;
            """);
        assertEquals(1, rowCount());
    }
}
