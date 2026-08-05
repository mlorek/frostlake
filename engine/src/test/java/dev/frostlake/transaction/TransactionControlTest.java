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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 2 transaction control (Snowflake semantics) on the default engine (autocommit + deferred apply on):
 * <ul>
 *   <li>DDL implicitly commits the open transaction;</li>
 *   <li>a failed statement inside an explicit transaction rolls back ONLY itself (including its partial
 *       multi-row writes) and leaves the transaction open.</li>
 * </ul>
 * See docs/acid-snowflake-plan.md.
 */
public class TransactionControlTest extends BaseDatabaseTest {

    private long rowCount(final String table) {
        return engine.executeQuery("SELECT * FROM " + table).getRowCount();
    }

    @Test
    public void ddlImplicitlyCommitsOpenTransaction() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE TABLE other (id INTEGER)");   // DDL commits the open transaction
        engine.execute("ROLLBACK");                           // no-op: the INSERT was already committed
        assertEquals(1, rowCount("t"), "DDL must implicitly commit the open transaction's INSERT");
    }

    @Test
    public void failedStatementKeepsExplicitTransactionOpen() {
        Assumptions.assumeFalse(isLiveSnowflake(), "PK enforcement is informational on live Snowflake");
        engine.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)");
        engine.getStorageEngine().setEnforcePrimaryKey(true);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1)");
        try {
            engine.execute("INSERT INTO t VALUES (1)");   // duplicate PK → fails THIS statement
            fail("expected a duplicate primary key error");
        } catch (final RuntimeException expected) {
            // expected — must roll back only this statement, not the whole transaction
        }
        engine.execute("INSERT INTO t VALUES (2)");        // transaction still open
        engine.execute("COMMIT");
        assertEquals(2, rowCount("t"), "failed statement rolls back itself but keeps the transaction open");
    }

    @Test
    public void failedMultiRowStatementUndoesOnlyItsOwnWrites() {
        Assumptions.assumeFalse(isLiveSnowflake(), "PK enforcement is informational on live Snowflake");
        engine.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)");
        engine.getStorageEngine().setEnforcePrimaryKey(true);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (1)");
        try {
            engine.execute("INSERT INTO t VALUES (3), (1)");   // (3) records, then (1) dup → statement fails
            fail("expected a duplicate primary key error");
        } catch (final RuntimeException expected) {
            // expected — the partial insert of (3) must be undone (statement-level rollback)
        }
        engine.execute("COMMIT");
        assertEquals(1, rowCount("t"));
        assertEquals(0, engine.executeQuery("SELECT * FROM t WHERE id = 3").getRowCount(),
            "the failed statement's partial insert of 3 must be rolled back");
    }
}
