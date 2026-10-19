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

package dev.frostlake.features;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statement clock orders statements across sessions and threads, not within one thread only. The engine kept
 * the last instant SQL read per thread, so a statement of another session, or of the same session on a pooled
 * thread, could read the very millisecond a watermark was taken in, and a loader reading the rows stamped after it
 * missed them. Two sessions creating objects at once drew the same tokens. And a write committed just before
 * {@code SET t = CURRENT_TIMESTAMP()} fell outside {@code BEFORE(TIMESTAMP => $t)}.
 */
public class StatementClockAcrossSessionsTest {

    private ConcurrentDatabaseEngine concurrent;
    private DatabaseEngine engine;

    @AfterEach
    public void tearDown() {
        if (concurrent != null) {
            concurrent.shutdown();
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Run one statement on a thread of its own, as a pooled server thread would, and wait for it. */
    private ResultSet onAnotherThread(final String sql, final SessionContext session) throws InterruptedException {
        final ResultSet[] result = new ResultSet[1];
        final RuntimeException[] failure = new RuntimeException[1];
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = concurrent.executeQuery(sql, session);
                } catch (final RuntimeException e) {
                    failure[0] = e;
                }
            }
        });
        thread.start();
        thread.join();
        if (failure[0] != null) {
            throw failure[0];
        }
        return result[0];
    }

    @Test
    public void aWatermarkAnotherSessionTookPrecedesEveryLaterWrite() throws InterruptedException {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext reader = concurrent.createSession();
        final SessionContext writer = concurrent.createSession();
        onAnotherThread("CREATE DATABASE wm", reader);
        onAnotherThread("CREATE TABLE wm.PUBLIC.ev (id INT, ts TIMESTAMP_LTZ)", reader);
        int missed = 0;
        for (int i = 0; i < 100; i++) {
            onAnotherThread("SET hw = CURRENT_TIMESTAMP()", reader);
            onAnotherThread("INSERT INTO wm.PUBLIC.ev SELECT " + i + ", CURRENT_TIMESTAMP()", writer);
            final ResultSet after = onAnotherThread(
                "SELECT COUNT(*) FROM wm.PUBLIC.ev WHERE id = " + i + " AND ts > $hw", reader);
            if (((Number) after.getRows().get(0).getValue(0)).longValue() != 1L) {
                missed++;
            }
        }
        assertEquals(0, missed, "rows written after the watermark was read, but not after it");
    }

    @Test
    public void sessionsCreatingObjectsAtOnceDrawDistinctTokens() throws InterruptedException {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext setup = concurrent.createSession();
        concurrent.executeQuery("CREATE DATABASE tk", setup);
        final CountDownLatch go = new CountDownLatch(1);
        final List<Thread> threads = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            final int session = t;
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final SessionContext own = concurrent.createSession();
                        go.await();
                        for (int i = 0; i < 100; i++) {
                            concurrent.executeQuery("CREATE NOTEBOOK tk.PUBLIC.nb_" + session + "_" + i, own);
                        }
                    } catch (final Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    }
                }
            }));
        }
        for (final Thread thread : threads) {
            thread.start();
        }
        go.countDown();
        for (final Thread thread : threads) {
            thread.join();
        }
        assertTrue(errors.isEmpty(), "the sessions threw: " + errors);
        concurrent.executeQuery("SHOW NOTEBOOKS IN SCHEMA tk.PUBLIC", setup);
        final ResultSet listed = concurrent.executeQuery(
            "SELECT \"url_id\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", setup);
        final Set<Object> urlIds = new HashSet<>();
        for (final Row row : listed.getRows()) {
            urlIds.add(row.getValue(0));
        }
        assertEquals(200, listed.getRows().size());
        assertEquals(200, urlIds.size(), "every notebook has its own url_id");
    }

    @Test
    public void beforeAnInstantReadAfterACommitIncludesTheCommit() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE bf");
        engine.execute("USE SCHEMA bf.PUBLIC");
        int missed = 0;
        for (int i = 0; i < 50; i++) {
            engine.execute("CREATE TABLE b" + i + " (a INT)");
            engine.execute("INSERT INTO b" + i + " VALUES (1)");
            engine.execute("SET t = CURRENT_TIMESTAMP()");
            final ResultSet before = engine.executeQuery("SELECT COUNT(*) FROM b" + i + " BEFORE(TIMESTAMP => $t)");
            if (((Number) before.getRows().get(0).getValue(0)).longValue() != 1L) {
                missed++;
            }
        }
        assertEquals(0, missed, "an insert committed before the instant was read is before it");
    }
}
