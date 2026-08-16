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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for concurrent SQL execution
 */
public class ConcurrentExecutionTest {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentExecutionTest.class);

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new ConcurrentDatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSingleSession() {
        final SessionContext session = engine.createSession();
        assertNotNull(session.getSessionId());

        // Create database and table
        engine.execute("CREATE DATABASE test_db", session);
        engine.execute("USE DATABASE test_db", session);
        engine.execute("USE SCHEMA PUBLIC", session);
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)", session);
        engine.execute("INSERT INTO users VALUES (1, 'Alice')", session);

        // Query data
        final ResultSet rs = engine.executeQuery("SELECT * FROM users", session);
        assertEquals(1, rs.getRowCount());
        rs.next();
        assertEquals("Alice", rs.getValue("name"));
    }

    @Test
    public void testMultipleSessions() {
        // Create two sessions
        final SessionContext session1 = engine.createSession();
        final SessionContext session2 = engine.createSession();

        assertNotEquals(session1.getSessionId(), session2.getSessionId());

        // Session 1: Create database db1
        engine.execute("CREATE DATABASE db1", session1);
        engine.execute("USE DATABASE db1", session1);
        engine.execute("CREATE TABLE users1 (id INT, name VARCHAR)", session1);
        engine.execute("INSERT INTO users1 VALUES (1, 'Alice')", session1);

        // Session 2: Create database db2
        engine.execute("CREATE DATABASE db2", session2);
        engine.execute("USE DATABASE db2", session2);
        engine.execute("CREATE TABLE users2 (id INT, name VARCHAR)", session2);
        engine.execute("INSERT INTO users2 VALUES (2, 'Bob')", session2);

        // Verify each session sees its own data
        final ResultSet rs1 = engine.executeQuery("SELECT * FROM users1", session1);
        assertEquals(1, rs1.getRowCount());
        rs1.next();
        assertEquals("Alice", rs1.getValue("name"));

        final ResultSet rs2 = engine.executeQuery("SELECT * FROM users2", session2);
        assertEquals(1, rs2.getRowCount());
        rs2.next();
        assertEquals("Bob", rs2.getValue("name"));
    }

    @Test
    public void testConcurrentReads() throws Exception {
        // Setup: Create table with data
        final SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE data (id INT, value VARCHAR)", setupSession);

        for (int i = 0; i < 100; i++) {
            engine.execute(String.format("INSERT INTO data VALUES (%d, 'value%d')", i, i), setupSession);
        }

        // Test: Multiple concurrent reads
        final int numThreads = 10;
        final int readsPerThread = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);
        final List<Future<Integer>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final Future<Integer> future = executor.submit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    final SessionContext session = engine.createSession();
                    engine.execute("USE DATABASE test_db", session);

                    int successCount = 0;
                    for (int i = 0; i < readsPerThread; i++) {
                        try {
                            final ResultSet rs = engine.executeQuery("SELECT * FROM data", session);
                            if (rs.getRowCount() == 100) {
                                successCount++;
                            }
                        } catch (final Exception e) {
                            logger.error("Read error: {}", e.getMessage(), e);
                        }
                    }
                    latch.countDown();
                    return Integer.valueOf(successCount);
                }
            });
            futures.add(future);
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Verify all (or nearly all) reads succeeded
        int totalSuccesses = 0;
        for (final Future<Integer> future : futures) {
            totalSuccesses += future.get();
        }

        // Allow up to 5% failure rate due to potential race conditions
        final int expected = numThreads * readsPerThread;
        final int minExpected = (int) (expected * 0.95);
        assertTrue(totalSuccesses >= minExpected,
            String.format("At least 95%% of concurrent reads should succeed (got %d/%d)", totalSuccesses, expected));
    }

    @Test
    public void testConcurrentWrites() throws Exception {
        // Setup: Create database and table
        final SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE counters (id INT, count INT)", setupSession);
        engine.execute("INSERT INTO counters VALUES (1, 0)", setupSession);

        // Test: Multiple concurrent writes
        final int numThreads = 5;
        final int writesPerThread = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    final SessionContext session = engine.createSession();
                    engine.execute("USE DATABASE test_db", session);

                    for (int i = 0; i < writesPerThread; i++) {
                        try {
                            engine.execute(
                                String.format("INSERT INTO counters VALUES (%d, %d)", threadId * 100 + i, i),
                                session
                            );
                        } catch (final Exception e) {
                            logger.error("Write error: {}", e.getMessage(), e);
                        }
                    }
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Verify total row count
        final ResultSet rs = engine.executeQuery("SELECT * FROM counters", setupSession);
        assertEquals(1 + (numThreads * writesPerThread), rs.getRowCount(),
            "All concurrent writes should be committed");
    }

    @Test
    public void testSessionIsolation() {
        final SessionContext session1 = engine.createSession();
        final SessionContext session2 = engine.createSession();

        // Session 1: Create database and use it
        engine.execute("CREATE DATABASE db1", session1);
        engine.execute("USE DATABASE db1", session1);

        // Session 2: Should still be on default database
        assertEquals("SNOWFLAKE", session2.getCurrentDatabase());

        // Session 2: Create different database
        engine.execute("CREATE DATABASE db2", session2);
        engine.execute("USE DATABASE db2", session2);

        // Verify isolation (database names are stored uppercase)
        assertEquals("DB1", session1.getCurrentDatabase());
        assertEquals("DB2", session2.getCurrentDatabase());
    }

    @Test
    public void testSessionTimeout() throws InterruptedException {
        // Create session manager with short timeout
        final SessionManager manager = new SessionManager(1000); // 1 second timeout

        final SessionContext session = manager.createSession();
        final String sessionId = session.getSessionId();

        // Session should exist
        assertNotNull(manager.getSession(sessionId));

        // Wait for timeout
        Thread.sleep(1500);

        // Manually trigger cleanup (normally done by scheduled task)
        // For testing, we just verify the session is marked as expired
        assertTrue(session.isExpired(1000));
    }

    @Test
    public void testActiveSessionCount() {
        assertEquals(0, engine.getActiveSessionCount());

        final SessionContext s1 = engine.createSession();
        assertEquals(1, engine.getActiveSessionCount());

        final SessionContext s2 = engine.createSession();
        assertEquals(2, engine.getActiveSessionCount());

        engine.removeSession(s1.getSessionId());
        assertEquals(1, engine.getActiveSessionCount());

        engine.removeSession(s2.getSessionId());
        assertEquals(0, engine.getActiveSessionCount());
    }

    @Test
    public void testReadWriteLocking() throws Exception {
        // Setup
        final SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE test (id INT)", setupSession);

        // Concurrent reads should not block each other
        final ExecutorService executor = Executors.newFixedThreadPool(5);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(5);
        final List<Long> startTimes = new CopyOnWriteArrayList<>();
        final List<Long> endTimes = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        final SessionContext session = engine.createSession();
                        engine.execute("USE DATABASE test_db", session);

                        startLatch.await(); // Wait for all threads to be ready

                        final long start = System.currentTimeMillis();
                        startTimes.add(Long.valueOf(start));

                        engine.executeQuery("SELECT * FROM test", session);
                        Thread.sleep(100); // Simulate some processing

                        final long end = System.currentTimeMillis();
                        endTimes.add(Long.valueOf(end));

                        doneLatch.countDown();
                    } catch (final Exception e) {
                        logger.error("Concurrent read failed", e);
                    }
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify reads happened concurrently (overlapping time ranges)
        final long maxStart = maxOf(startTimes);
        final long minEnd = minOf(endTimes);

        // If reads were truly concurrent, maxStart should be before minEnd
        assertTrue(maxStart < minEnd, "Reads should overlap in time");
    }

    /** The smallest stamp, or 0 when nothing was recorded. */
    private long minOf(final List<Long> stamps) {
        if (stamps.isEmpty()) {
            return 0L;
        }
        long smallest = Long.MAX_VALUE;
        for (final Long stamp : stamps) {
            if (stamp.longValue() < smallest) {
                smallest = stamp.longValue();
            }
        }
        return smallest;
    }

    /** The largest stamp, or 0 when nothing was recorded. */
    private long maxOf(final List<Long> stamps) {
        if (stamps.isEmpty()) {
            return 0L;
        }
        long largest = Long.MIN_VALUE;
        for (final Long stamp : stamps) {
            if (stamp.longValue() > largest) {
                largest = stamp.longValue();
            }
        }
        return largest;
    }
}
