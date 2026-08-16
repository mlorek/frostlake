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

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Five real JDBC clients against ONE embedded engine ({@code jdbc:frostlake:direct:<name>} shares
 * the engine per name), hammering a single table with concurrent DML — the multi-client safety
 * scenario, with and without explicit transactions.
 *
 * <p>What holds, and why: every statement of a direct connection is SERIALIZED on the shared
 * engine's monitor ({@code DirectConnection.executeScoped}), with the connection's own
 * database/schema/autocommit context bound to the executing thread for the statement's duration —
 * so interleaved autocommit DML from many clients cannot lose writes or tear rows, and a
 * single-row increment behaves atomically per statement. Transactions are scoped to the executing
 * THREAD ({@code TransactionManager}'s ThreadLocal), so each client here runs on its own dedicated
 * thread — the JDBC norm — and BEGIN/COMMIT/ROLLBACK stay per-client; uncommitted writes ride the
 * transaction's write-set and are invisible to the other clients until COMMIT (READ COMMITTED).
 *
 * <p>The one measured divergence from a real account sits in
 * {@link #overlappingTransactionsLoseTheFirstIncrement}: Frostlake has no lock enforcement, so two
 * overlapping transactions incrementing the same row both read the same committed value and the
 * first commit's increment is overwritten — a real account's row lock would block the second
 * UPDATE until the first commit and land both. Pinned as-is; row locking is the known remaining
 * ACID work.
 */
public class ConcurrentClientsDmlTest {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentClientsDmlTest.class);

    private static final int CLIENTS = 5;
    private static int engineSequence = 0;

    private String engineName;
    private Connection setup;
    private final List<Connection> clients = new ArrayList<>();

    @BeforeEach
    public void openClients() throws SQLException {
        engineSequence++;
        engineName = "concurrent_dml_" + engineSequence;
        setup = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        try (final Statement s = setup.createStatement()) {
            s.execute("CREATE DATABASE cdb");
            s.execute("USE DATABASE cdb");
            s.execute("CREATE SCHEMA cs");
        }
        for (int i = 0; i < CLIENTS; i++) {
            clients.add(DriverManager.getConnection(
                "jdbc:frostlake:direct:" + engineName + "?database=cdb&schema=cs"));
        }
    }

    @AfterEach
    public void closeClients() throws SQLException {
        for (final Connection client : clients) {
            client.close();
        }
        clients.clear();
        setup.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void createTable(final String ddl) throws SQLException {
        try (final Statement s = setup.createStatement()) {
            s.execute("USE SCHEMA cdb.cs");
            s.execute(ddl);
        }
    }

    private long scalarLong(final String sql) throws SQLException {
        try (final Statement s = setup.createStatement()) {
            s.execute("USE SCHEMA cdb.cs");
            try (final ResultSet rs = s.executeQuery(sql)) {
                assertTrue(rs.next(), "expected one row from: " + sql);
                return rs.getLong(1);
            }
        }
    }

    /**
     * Run one body per client on its own dedicated thread, all released together, and rethrow the
     * first client failure. The barrier maximizes interleaving; the join bounds liveness only.
     */
    private void runClientsConcurrently(final ClientBody body) throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(CLIENTS);
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        for (int i = 0; i < CLIENTS; i++) {
            final int clientId = i;
            final Connection connection = clients.get(i);
            final Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        body.run(clientId, connection);
                    } catch (final Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                }
            }, "dml-client-" + i);
            worker.start();
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "clients did not finish in time");
        assertTrue(failures.isEmpty(), "client failures: " + failures);
    }

    /** No lost INSERTs: five autocommit clients writing disjoint keys all land, none duplicated. */
    @Test
    public void concurrentAutocommitInsertsAllLand() throws Exception {
        createTable("CREATE TABLE t (id INTEGER, client INTEGER)");
        final int rowsPerClient = 40;

        runClientsConcurrently(new ClientBody() {
            @Override
            public void run(final int clientId, final Connection connection) throws Exception {
                try (final Statement s = connection.createStatement()) {
                    for (int i = 0; i < rowsPerClient; i++) {
                        s.execute("INSERT INTO t VALUES (" + (clientId * 1000 + i) + ", " + clientId + ")");
                    }
                }
            }
        });

        assertEquals(CLIENTS * rowsPerClient, scalarLong("SELECT COUNT(*) FROM t"));
        assertEquals(CLIENTS * rowsPerClient, scalarLong("SELECT COUNT(DISTINCT id) FROM t"));
        for (int c = 0; c < CLIENTS; c++) {
            assertEquals(rowsPerClient, scalarLong("SELECT COUNT(*) FROM t WHERE client = " + c),
                "client " + c + " lost writes");
        }
    }

    /** Disjoint-row UPDATEs from five clients never clobber one another's rows. */
    @Test
    public void concurrentDisjointUpdatesDoNotClobber() throws Exception {
        createTable("CREATE TABLE t (id INTEGER, client INTEGER, val INTEGER)");
        try (final Statement s = setup.createStatement()) {
            s.execute("USE SCHEMA cdb.cs");
            for (int c = 0; c < CLIENTS; c++) {
                for (int i = 0; i < 10; i++) {
                    s.execute("INSERT INTO t VALUES (" + (c * 100 + i) + ", " + c + ", 0)");
                }
            }
        }

        runClientsConcurrently(new ClientBody() {
            @Override
            public void run(final int clientId, final Connection connection) throws Exception {
                try (final Statement s = connection.createStatement()) {
                    // Three passes over the client's own rows, ending at round 3.
                    for (int round = 1; round <= 3; round++) {
                        s.execute("UPDATE t SET val = " + round + " WHERE client = " + clientId);
                    }
                }
            }
        });

        assertEquals(CLIENTS * 10L, scalarLong("SELECT COUNT(*) FROM t"));
        assertEquals(CLIENTS * 10L, scalarLong("SELECT COUNT(*) FROM t WHERE val = 3"),
            "some rows missed their owner's final update");
    }

    /** Autocommit single-row increments serialize statement-by-statement: none are lost. */
    @Test
    public void concurrentAutocommitIncrementsSerialize() throws Exception {
        createTable("CREATE TABLE t (id INTEGER, v INTEGER)");
        try (final Statement s = setup.createStatement()) {
            s.execute("USE SCHEMA cdb.cs");
            s.execute("INSERT INTO t VALUES (1, 0)");
        }
        final int incrementsPerClient = 20;

        runClientsConcurrently(new ClientBody() {
            @Override
            public void run(final int clientId, final Connection connection) throws Exception {
                try (final Statement s = connection.createStatement()) {
                    for (int i = 0; i < incrementsPerClient; i++) {
                        s.execute("UPDATE t SET v = v + 1 WHERE id = 1");
                    }
                }
            }
        });

        assertEquals(CLIENTS * incrementsPerClient, scalarLong("SELECT v FROM t WHERE id = 1"),
            "an autocommit increment was lost");
    }

    /** Explicit transactions commit atomically; a ROLLBACK contributes nothing. */
    @Test
    public void transactionsCommitAtomicallyAndRollbackLeavesNothing() throws Exception {
        createTable("CREATE TABLE t (id INTEGER, client INTEGER)");
        final int rowsPerClient = 10;

        runClientsConcurrently(new ClientBody() {
            @Override
            public void run(final int clientId, final Connection connection) throws Exception {
                try (final Statement s = connection.createStatement()) {
                    s.execute("BEGIN");
                    for (int i = 0; i < rowsPerClient; i++) {
                        s.execute("INSERT INTO t VALUES (" + (clientId * 1000 + i) + ", " + clientId + ")");
                    }
                    // Clients 0-2 keep their rows; clients 3-4 take everything back.
                    s.execute(clientId < 3 ? "COMMIT" : "ROLLBACK");
                }
            }
        });

        assertEquals(3L * rowsPerClient, scalarLong("SELECT COUNT(*) FROM t"));
        for (int c = 0; c < CLIENTS; c++) {
            assertEquals(c < 3 ? rowsPerClient : 0L,
                scalarLong("SELECT COUNT(*) FROM t WHERE client = " + c),
                "client " + c + " has the wrong committed row count");
        }
    }

    /** READ COMMITTED across clients: uncommitted rows are invisible until the writer commits. */
    @Test
    public void uncommittedWritesAreInvisibleToOtherClients() throws Exception {
        createTable("CREATE TABLE t (id INTEGER)");
        final CountDownLatch inserted = new CountDownLatch(1);
        final CountDownLatch observed = new CountDownLatch(1);
        final CountDownLatch committed = new CountDownLatch(1);
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());

        final Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try (final Statement s = clients.get(0).createStatement()) {
                    s.execute("BEGIN");
                    for (int i = 0; i < 5; i++) {
                        s.execute("INSERT INTO t VALUES (" + i + ")");
                    }
                    inserted.countDown();
                    // Hold the transaction open until every other client has looked.
                    assertTrue(observed.await(30, TimeUnit.SECONDS), "readers never arrived");
                    s.execute("COMMIT");
                    committed.countDown();
                } catch (final Throwable t) {
                    failures.add(t);
                    inserted.countDown();
                    committed.countDown();
                }
            }
        }, "dml-writer");
        writer.start();

        assertTrue(inserted.await(30, TimeUnit.SECONDS), "writer never inserted");
        assertTrue(failures.isEmpty(), "writer failed: " + failures);
        // Every OTHER client reads while the writer's transaction is still open.
        for (int c = 1; c < CLIENTS; c++) {
            try (final Statement s = clients.get(c).createStatement();
                 final ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM t")) {
                assertTrue(rs.next());
                assertEquals(0L, rs.getLong(1), "client " + c + " saw uncommitted rows");
            }
        }
        observed.countDown();
        assertTrue(committed.await(30, TimeUnit.SECONDS), "writer never committed");
        assertTrue(failures.isEmpty(), "writer failed: " + failures);
        for (int c = 1; c < CLIENTS; c++) {
            try (final Statement s = clients.get(c).createStatement();
                 final ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM t")) {
                assertTrue(rs.next());
                assertEquals(5L, rs.getLong(1), "client " + c + " missed committed rows");
            }
        }
        writer.join(TimeUnit.SECONDS.toMillis(30));
    }

    /**
     * The measured DIVERGENCE: two overlapping transactions increment the same row, and the first
     * commit's increment is lost — both read the same committed value, so the final value is +1,
     * not +2. A real account's row lock blocks the second UPDATE until the first COMMIT and lands
     * both. Frostlake has no lock enforcement (the remaining ACID work); this pins today's
     * behavior so a future locking implementation shows up as this test CHANGING, deliberately.
     */
    @Test
    public void overlappingTransactionsLoseTheFirstIncrement() throws Exception {
        createTable("CREATE TABLE t (id INTEGER, v INTEGER)");
        try (final Statement s = setup.createStatement()) {
            s.execute("USE SCHEMA cdb.cs");
            s.execute("INSERT INTO t VALUES (1, 0)");
        }
        final CountDownLatch firstUpdated = new CountDownLatch(1);
        final CountDownLatch secondUpdated = new CountDownLatch(1);
        final CountDownLatch firstCommitted = new CountDownLatch(1);
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());

        final Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                try (final Statement s = clients.get(0).createStatement()) {
                    s.execute("BEGIN");
                    s.execute("UPDATE t SET v = v + 1 WHERE id = 1");
                    firstUpdated.countDown();
                    // The second transaction updates the SAME row before this one commits.
                    assertTrue(secondUpdated.await(30, TimeUnit.SECONDS), "second txn never updated");
                    s.execute("COMMIT");
                    firstCommitted.countDown();
                } catch (final Throwable t) {
                    failures.add(t);
                    firstUpdated.countDown();
                    firstCommitted.countDown();
                }
            }
        }, "txn-first");
        final Thread second = new Thread(new Runnable() {
            @Override
            public void run() {
                try (final Statement s = clients.get(1).createStatement()) {
                    assertTrue(firstUpdated.await(30, TimeUnit.SECONDS), "first txn never updated");
                    s.execute("BEGIN");
                    // Reads COMMITTED state (v = 0): the first transaction's +1 is still pending.
                    s.execute("UPDATE t SET v = v + 1 WHERE id = 1");
                    secondUpdated.countDown();
                    assertTrue(firstCommitted.await(30, TimeUnit.SECONDS), "first txn never committed");
                    s.execute("COMMIT");
                } catch (final Throwable t) {
                    failures.add(t);
                    secondUpdated.countDown();
                }
            }
        }, "txn-second");
        first.start();
        second.start();
        first.join(TimeUnit.SECONDS.toMillis(60));
        second.join(TimeUnit.SECONDS.toMillis(60));
        assertTrue(failures.isEmpty(), "transaction clients failed: " + failures);

        final long finalValue = scalarLong("SELECT v FROM t WHERE id = 1");
        logger.info("overlapping increments settled at v = {}", finalValue);
        assertEquals(1L, finalValue,
            "both transactions read committed v=0, so the second commit overwrites the first"
                + " increment; a locking implementation would land 2 and should update this pin");
    }
}
