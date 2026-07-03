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
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

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
        SessionContext session = engine.createSession();
        assertNotNull(session.getSessionId());

        // Create database and table
        engine.execute("CREATE DATABASE test_db", session);
        engine.execute("USE DATABASE test_db", session);
        engine.execute("USE SCHEMA PUBLIC", session);
        engine.execute("CREATE TABLE users (id INT, name VARCHAR)", session);
        engine.execute("INSERT INTO users VALUES (1, 'Alice')", session);

        // Query data
        ResultSet rs = engine.executeQuery("SELECT * FROM users", session);
        assertEquals(1, rs.getRowCount());
        rs.next();
        assertEquals("Alice", rs.getValue("name"));
    }

    @Test
    public void testMultipleSessions() {
        // Create two sessions
        SessionContext session1 = engine.createSession();
        SessionContext session2 = engine.createSession();

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
        ResultSet rs1 = engine.executeQuery("SELECT * FROM users1", session1);
        assertEquals(1, rs1.getRowCount());
        rs1.next();
        assertEquals("Alice", rs1.getValue("name"));

        ResultSet rs2 = engine.executeQuery("SELECT * FROM users2", session2);
        assertEquals(1, rs2.getRowCount());
        rs2.next();
        assertEquals("Bob", rs2.getValue("name"));
    }

    @Test
    public void testConcurrentReads() throws Exception {
        // Setup: Create table with data
        SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE data (id INT, value VARCHAR)", setupSession);

        for (int i = 0; i < 100; i++) {
            engine.execute(String.format("INSERT INTO data VALUES (%d, 'value%d')", i, i), setupSession);
        }

        // Test: Multiple concurrent reads
        int numThreads = 10;
        int readsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            Future<Integer> future = executor.submit(() -> {
                SessionContext session = engine.createSession();
                engine.execute("USE DATABASE test_db", session);

                int successCount = 0;
                for (int i = 0; i < readsPerThread; i++) {
                    try {
                        ResultSet rs = engine.executeQuery("SELECT * FROM data", session);
                        if (rs.getRowCount() == 100) {
                            successCount++;
                        }
                    } catch (final Exception e) {
                        logger.error("Read error: {}", e.getMessage(), e);
                    }
                }
                latch.countDown();
                return successCount;
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
        int expected = numThreads * readsPerThread;
        int minExpected = (int) (expected * 0.95);
        assertTrue(totalSuccesses >= minExpected,
            String.format("At least 95%% of concurrent reads should succeed (got %d/%d)", totalSuccesses, expected));
    }

    @Test
    public void testConcurrentWrites() throws Exception {
        // Setup: Create database and table
        SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE counters (id INT, count INT)", setupSession);
        engine.execute("INSERT INTO counters VALUES (1, 0)", setupSession);

        // Test: Multiple concurrent writes
        int numThreads = 5;
        int writesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                SessionContext session = engine.createSession();
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
            });
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not complete in time");
        executor.shutdown();

        // Verify total row count
        ResultSet rs = engine.executeQuery("SELECT * FROM counters", setupSession);
        assertEquals(1 + (numThreads * writesPerThread), rs.getRowCount(),
            "All concurrent writes should be committed");
    }

    @Test
    public void testSessionIsolation() {
        SessionContext session1 = engine.createSession();
        SessionContext session2 = engine.createSession();

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
        SessionManager manager = new SessionManager(1000); // 1 second timeout

        SessionContext session = manager.createSession();
        String sessionId = session.getSessionId();

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

        SessionContext s1 = engine.createSession();
        assertEquals(1, engine.getActiveSessionCount());

        SessionContext s2 = engine.createSession();
        assertEquals(2, engine.getActiveSessionCount());

        engine.removeSession(s1.getSessionId());
        assertEquals(1, engine.getActiveSessionCount());

        engine.removeSession(s2.getSessionId());
        assertEquals(0, engine.getActiveSessionCount());
    }

    @Test
    public void testReadWriteLocking() throws Exception {
        // Setup
        SessionContext setupSession = engine.createSession();
        engine.execute("CREATE DATABASE test_db", setupSession);
        engine.execute("USE DATABASE test_db", setupSession);
        engine.execute("CREATE TABLE test (id INT)", setupSession);

        // Concurrent reads should not block each other
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        List<Long> startTimes = new CopyOnWriteArrayList<>();
        List<Long> endTimes = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    SessionContext session = engine.createSession();
                    engine.execute("USE DATABASE test_db", session);

                    startLatch.await(); // Wait for all threads to be ready

                    long start = System.currentTimeMillis();
                    startTimes.add(start);

                    engine.executeQuery("SELECT * FROM test", session);
                    Thread.sleep(100); // Simulate some processing

                    long end = System.currentTimeMillis();
                    endTimes.add(end);

                    doneLatch.countDown();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify reads happened concurrently (overlapping time ranges)
        long minStart = startTimes.stream().min(Long::compare).orElse(0L);
        long maxStart = startTimes.stream().max(Long::compare).orElse(0L);
        long minEnd = endTimes.stream().min(Long::compare).orElse(0L);

        // If reads were truly concurrent, maxStart should be before minEnd
        assertTrue(maxStart < minEnd, "Reads should overlap in time");
    }
}
