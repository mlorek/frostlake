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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Write-ahead log durability (Phase 5; {@code durability.walEnabled}, default off). Committed (autocommit)
 * statements are appended to the WAL with fsync, and a fresh engine on the same WAL file replays them to
 * recover the data; uncommitted / rolled-back work is not recovered. See docs/acid-snowflake-plan.md.
 */
public class WalDurabilityTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_test_");
        walFile = dir.resolve("wal.log");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(walFile);
        Files.deleteIfExists(dir);
    }

    private DatabaseEngine newWalEngine() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        return new DatabaseEngine(cfg);
    }

    @Test
    public void committedWritesAreRecoveredByAFreshEngine() {
        final DatabaseEngine e1 = newWalEngine();
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
        e1.execute("INSERT INTO t VALUES (1, 'a')");
        e1.execute("INSERT INTO t VALUES (2, 'b')");
        e1.shutdown();

        // A brand-new engine on the same WAL file replays the committed statements on startup.
        final DatabaseEngine e2 = newWalEngine();
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(2, rs.getRowCount(), "committed writes must be recovered from the WAL");
        e2.shutdown();
    }

    @Test
    public void rolledBackWorkIsNotRecovered() {
        final DatabaseEngine e1 = newWalEngine();
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER)");
        e1.execute("INSERT INTO t VALUES (1)");   // autocommit → committed → logged
        e1.execute("BEGIN");
        e1.execute("INSERT INTO t VALUES (2)");   // uncommitted explicit txn → not logged
        e1.execute("ROLLBACK");
        e1.shutdown();

        final DatabaseEngine e2 = newWalEngine();
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(1, rs.getRowCount(), "only committed work is recovered; rolled-back work is not");
        e2.shutdown();
    }

    @Test
    public void explicitTransactionCommitIsRecoveredAsAUnit() {
        final DatabaseEngine e1 = newWalEngine();
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER)");
        e1.execute("BEGIN");
        e1.execute("INSERT INTO t VALUES (1)");
        e1.execute("INSERT INTO t VALUES (2)");
        e1.execute("INSERT INTO t VALUES (3)");
        e1.execute("COMMIT");   // the whole explicit transaction is logged here, atomically
        e1.shutdown();

        final DatabaseEngine e2 = newWalEngine();
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(3, rs.getRowCount(), "an explicit BEGIN…COMMIT block must be recovered as a unit");
        e2.shutdown();
    }

    @Test
    public void theStatementsARequestCommittedBeforeItFailedAreRecovered() {
        final DatabaseEngine e1 = newWalEngine();
        e1.execute("CREATE DATABASE db");
        e1.execute("USE DATABASE db");
        e1.execute("CREATE SCHEMA s");
        e1.execute("USE SCHEMA s");
        e1.execute("CREATE TABLE t (id INTEGER)");
        e1.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        e1.execute("INSERT INTO t VALUES (1); INSERT INTO t VALUES (2)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                e1.execute("INSERT INTO t VALUES (3); INSERT INTO t VALUES (4); SELECT * FROM nowhere_xyz");
            }
        });
        e1.shutdown();

        // Each statement of a request commits on its own, so each is logged on its own: the two that
        // completed before the failure are recovered, and nothing is replayed twice.
        final DatabaseEngine e2 = newWalEngine();
        final ResultSet rs = e2.executeQuery("SELECT * FROM db.s.t");
        assertEquals(4, rs.getRowCount(), "every statement a request committed must be recovered, once");
        e2.shutdown();
    }
}
