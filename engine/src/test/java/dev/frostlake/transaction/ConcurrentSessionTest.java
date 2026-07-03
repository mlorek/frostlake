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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two sessions sharing ONE engine in the same JVM must not interfere when run concurrently on separate
 * threads: each session's writes land correctly (no lost / extra / cross rows, no exceptions), and each
 * session's context (current database) stays its own. Complements {@code MultiSessionIsolationTest} (which
 * checks READ COMMITTED visibility between sessions, sequentially). The concurrency is the stress; the
 * assertions are on deterministic final state, so the test is not timing-sensitive.
 */
public class ConcurrentSessionTest {

    private static final int N = 200;

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        final SessionContext setup = engine.createSession();
        engine.execute("CREATE DATABASE db", setup);
        engine.execute("USE DATABASE db", setup);
        engine.execute("CREATE SCHEMA s", setup);
        engine.execute("USE SCHEMA s", setup);
        engine.execute("CREATE TABLE db.s.t1 (id INTEGER)", setup);
        engine.execute("CREATE TABLE db.s.t2 (id INTEGER)", setup);
        engine.execute("CREATE TABLE db.s.shared (id INTEGER, who VARCHAR)", setup);
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void concurrentSessionsIntoSeparateTablesDoNotInterfere() throws InterruptedException {
        final SessionContext s1 = engine.createSession();
        final SessionContext s2 = engine.createSession();
        final List<Throwable> errors = runBoth(
            insertLoop(s1, "INSERT INTO db.s.t1 VALUES (", ")"),
            insertLoop(s2, "INSERT INTO db.s.t2 VALUES (", ")"));

        assertTrue(errors.isEmpty(), "concurrent sessions threw: " + errors);
        assertEquals(N, count("db.s.t1"), "session 1's rows");
        assertEquals(N, count("db.s.t2"), "session 2's rows");
    }

    @Test
    public void concurrentSessionsIntoSharedTablePersistAllWrites() throws InterruptedException {
        final SessionContext s1 = engine.createSession();
        final SessionContext s2 = engine.createSession();
        final List<Throwable> errors = runBoth(
            insertLoop(s1, "INSERT INTO db.s.shared VALUES (", ", 'a')"),
            insertLoop(s2, "INSERT INTO db.s.shared VALUES (", ", 'b')"));

        assertTrue(errors.isEmpty(), "concurrent sessions threw: " + errors);
        assertEquals(2L * N, count("db.s.shared"), "no writes lost across concurrent sessions");
        assertEquals(N, count("db.s.shared WHERE who = 'a'"));
        assertEquals(N, count("db.s.shared WHERE who = 'b'"));
    }

    @Test
    public void sessionContextsAreIndependent() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        engine.execute("CREATE DATABASE da", a);
        engine.execute("CREATE DATABASE db2", a);   // shared catalog, both databases now exist
        engine.execute("USE DATABASE da", a);
        engine.execute("USE DATABASE db2", b);
        assertTrue("DA".equalsIgnoreCase(a.getCurrentDatabase()),
            "session a kept its own current database, got: " + a.getCurrentDatabase());
        assertTrue("DB2".equalsIgnoreCase(b.getCurrentDatabase()),
            "session b kept its own current database, got: " + b.getCurrentDatabase());
    }

    /** A workload that inserts ids 0..N-1 via {@code session}, each statement {@code prefix + i + suffix}. */
    private Runnable insertLoop(final SessionContext session, final String prefix, final String suffix) {
        return new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < N; i++) {
                    engine.execute(prefix + i + suffix, session);
                }
            }
        };
    }

    /** Start both workloads on separate threads, released together, and return any throwables they raised. */
    private List<Throwable> runBoth(final Runnable a, final Runnable b) throws InterruptedException {
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch start = new CountDownLatch(1);
        final Thread ta = startGuarded(a, start, errors);
        final Thread tb = startGuarded(b, start, errors);
        start.countDown();   // release both threads at the same time
        ta.join();
        tb.join();
        return errors;
    }

    private Thread startGuarded(final Runnable body, final CountDownLatch start, final List<Throwable> errors) {
        final Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    body.run();
                } catch (final Throwable t) {
                    errors.add(t);
                }
            }
        });
        th.start();
        return th;
    }

    private long count(final String tableAndPredicate) {
        return engine.executeQuery("SELECT * FROM " + tableAndPredicate, engine.createSession()).getRowCount();
    }
}
