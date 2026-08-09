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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LAST_QUERY_ID follows the SESSION, not the thread that happens to serve a statement.
 *
 * <p>The query-ID history used to be thread-local, which is invisible in process but broken over the
 * HTTP transport: consecutive requests of one session are handed to whichever pool thread is free,
 * so the second request saw an empty history and {@code RESULT_SCAN(LAST_QUERY_ID())} failed with
 * "No previous query results available". That is the exact read-back idiom migration scripts use
 * after a SHOW.
 */
public class QueryIdSessionScopeTest {

    private ConcurrentDatabaseEngine engine;
    private ExecutorService pool;

    @BeforeEach
    public void setup() {
        engine = new ConcurrentDatabaseEngine();
        pool = Executors.newFixedThreadPool(4);
        final SessionContext admin = engine.createSession();
        engine.execute("CREATE DATABASE qid_db", admin);
    }

    @AfterEach
    public void tearDown() {
        pool.shutdownNow();
    }

    /** Point a session at the probe schema and give it a table to list. */
    private SessionContext sessionWithTable(final String sessionId, final String tableName) {
        final SessionContext session = engine.getOrCreateSession(sessionId);
        engine.execute("USE DATABASE qid_db", session);
        engine.execute("USE SCHEMA public", session);
        engine.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (k INTEGER)", session);
        return session;
    }

    /** Run one statement on a pool thread, so it is served by a different thread than its caller. */
    private <T> T onPoolThread(final Callable<T> work) throws Exception {
        return pool.submit(work).get(30, TimeUnit.SECONDS);
    }

    @Test
    public void resultScanReadsBackAcrossThreadsWithinOneSession() throws Exception {
        final SessionContext session = sessionWithTable("s1", "cross_thread");
        onPoolThread(new Callable<Object>() {
            @Override
            public Object call() {
                return engine.execute("SHOW TABLES", session);
            }
        });
        // A DIFFERENT pool thread serves the read-back, exactly as the HTTP server does.
        final ResultSet scanned = onPoolThread(new Callable<ResultSet>() {
            @Override
            public ResultSet call() {
                return engine.executeQuery(
                    "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", session);
            }
        });
        assertEquals(1L, ((Number) scanned.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void lastQueryIdIsCarriedBetweenSeparateRequestsOfOneSession() throws Exception {
        final SessionContext session = sessionWithTable("s2", "carried");
        final String first = onPoolThread(new Callable<String>() {
            @Override
            public String call() {
                engine.execute("SELECT 1", session);
                return engine.executeQuery("SELECT LAST_QUERY_ID()", session)
                    .getRows().get(0).getValue(0).toString();
            }
        });
        final String seenLater = onPoolThread(new Callable<String>() {
            @Override
            public String call() {
                return engine.executeQuery("SELECT LAST_QUERY_ID(-2)", session)
                    .getRows().get(0).getValue(0).toString();
            }
        });
        assertNotNull(first);
        // The other thread still sees the session's earlier statement in its history.
        assertEquals(first, seenLater);
    }

    /** Two sessions must not share a history, which is what the thread-local used to guarantee. */
    @Test
    public void sessionsDoNotSeeEachOthersLastQueryId() {
        final SessionContext one = sessionWithTable("s3", "sess_one");
        final SessionContext two = sessionWithTable("s4", "sess_two");
        engine.execute("SELECT 1", one);
        final String fromOne = engine.executeQuery("SELECT LAST_QUERY_ID()", one)
            .getRows().get(0).getValue(0).toString();
        engine.execute("SELECT 2", two);
        final String fromTwo = engine.executeQuery("SELECT LAST_QUERY_ID()", two)
            .getRows().get(0).getValue(0).toString();
        assertNotEquals(fromOne, fromTwo);

        // Both sessions ran on THIS thread in turn — the case a thread-keyed history mixed up.
        final String oneAgain = engine.executeQuery("SELECT LAST_QUERY_ID(-2)", one)
            .getRows().get(0).getValue(0).toString();
        assertEquals(fromOne, oneAgain);
    }

    /** A removed session's history goes with it rather than lingering under a reused id. */
    @Test
    public void removingASessionForgetsItsHistory() {
        final SessionContext session = sessionWithTable("s5", "forgotten");
        engine.execute("SELECT 1", session);
        assertNotNull(engine.executeQuery("SELECT LAST_QUERY_ID()", session)
            .getRows().get(0).getValue(0));

        engine.removeSession("s5");
        final SessionContext reused = engine.getOrCreateSession("s5");
        engine.execute("USE DATABASE qid_db", reused);
        // Deeper than anything the fresh session has run, so the old history would have answered here.
        assertNull(engine.executeQuery("SELECT LAST_QUERY_ID(-9)", reused)
            .getRows().get(0).getValue(0));
    }
}
