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
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sessions on the shared concurrent engine must not see each other's current database/schema:
 * read-locked statements run in PARALLEL, and the per-statement context used to be applied by
 * mutating one shared engine field — a USE in one session leaked into another mid-statement, and
 * the capture-back then rewired the victim session to the other session's database for good (the
 * "Database does not exist: <other test's dropped clone>" failure mode). Context is now bound
 * per-thread for the statement's duration.
 */
public class SessionContextIsolationTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new ConcurrentDatabaseEngine();
        final SessionContext admin = engine.createSession();
        engine.execute("CREATE DATABASE db_one", admin);
        engine.execute("CREATE DATABASE db_two", admin);
        engine.execute("CREATE TABLE db_one.public.marker (v VARCHAR)", admin);
        engine.execute("INSERT INTO db_one.public.marker VALUES ('one')", admin);
        engine.execute("CREATE TABLE db_two.public.marker (v VARCHAR)", admin);
        engine.execute("INSERT INTO db_two.public.marker VALUES ('two')", admin);
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testUseInOneSessionDoesNotLeakIntoAnother() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        engine.execute("USE DATABASE db_one", a);
        engine.execute("USE DATABASE db_two", b);

        // Session a must keep resolving against db_one no matter what b does.
        assertEquals("one", engine.executeQuery("SELECT v FROM public.marker", a).getRows().get(0).getValue(0));
        assertEquals("two", engine.executeQuery("SELECT v FROM public.marker", b).getRows().get(0).getValue(0));
        assertEquals("DB_ONE", engine.executeQuery("SELECT CURRENT_DATABASE()", a).getRows().get(0).getValue(0));
        assertEquals("DB_TWO", engine.executeQuery("SELECT CURRENT_DATABASE()", b).getRows().get(0).getValue(0));
        // And the session objects themselves must not have been cross-captured.
        assertEquals("DB_ONE", a.getCurrentDatabase());
        assertEquals("DB_TWO", b.getCurrentDatabase());
    }

    @Test
    public void testParallelReadsKeepPerSessionDatabase() throws InterruptedException {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        engine.execute("USE DATABASE db_one", a);
        engine.execute("USE DATABASE db_two", b);

        final int iterations = 300;
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<String> failure = new AtomicReference<>();

        final Thread readerA = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < iterations && failure.get() == null; i++) {
                        final ResultSet rs = engine.executeQuery("SELECT v FROM public.marker", a);
                        final Object v = rs.getRows().get(0).getValue(0);
                        if (!"one".equals(v)) {
                            failure.compareAndSet(null, "session a read '" + v + "' at iteration " + i);
                        }
                    }
                } catch (final Exception e) {
                    failure.compareAndSet(null, "session a failed: " + e.getMessage());
                }
            }
        });
        final Thread readerB = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    for (int i = 0; i < iterations && failure.get() == null; i++) {
                        final ResultSet rs = engine.executeQuery("SELECT v FROM public.marker", b);
                        final Object v = rs.getRows().get(0).getValue(0);
                        if (!"two".equals(v)) {
                            failure.compareAndSet(null, "session b read '" + v + "' at iteration " + i);
                        }
                    }
                } catch (final Exception e) {
                    failure.compareAndSet(null, "session b failed: " + e.getMessage());
                }
            }
        });

        readerA.start();
        readerB.start();
        start.countDown();
        readerA.join(60000);
        readerB.join(60000);

        assertNull(failure.get(), failure.get());
        assertEquals("DB_ONE", a.getCurrentDatabase());
        assertEquals("DB_TWO", b.getCurrentDatabase());
    }

    @Test
    public void testDroppedDatabaseOfOneSessionDoesNotBreakAnother() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        engine.execute("USE DATABASE db_one", a);
        engine.execute("USE DATABASE db_two", b);

        // b finishes and drops its database (the SyntaxTests-teardown shape); a must be unaffected.
        engine.execute("USE DATABASE SNOWFLAKE", b);
        engine.execute("DROP DATABASE db_two", b);

        engine.execute("DELETE FROM public.marker", a);
        assertEquals(0L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM public.marker", a).getRows()
            .get(0).getValue(0)).longValue());
        assertEquals("DB_ONE", a.getCurrentDatabase());
    }

    @Test
    public void testSessionOnDroppedDatabaseErrorsInsteadOfRetargeting() {
        final SessionContext admin = engine.createSession();
        final SessionContext c = engine.createSession();
        engine.execute("USE DATABASE db_two", c);
        engine.execute("DROP DATABASE db_two", admin);

        // The session stays pinned to its (now dropped) database and the statement FAILS — before the
        // per-thread scope, the failed context application silently ran the statement against whatever
        // database the previous session left on the shared engine, and the capture-back then rewired
        // this session to that database for good.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT v FROM public.marker", c);
            }
        });
        assertEquals("DB_TWO", c.getCurrentDatabase());
    }

    @Test
    public void testAutocommitModeDoesNotLeakBetweenSessions() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        engine.execute("USE DATABASE db_one", a);
        engine.execute("USE DATABASE db_one", b);

        a.setAutoCommit(false);
        engine.executeQuery("SELECT 1", a);
        engine.executeQuery("SELECT 1", b);

        assertEquals(false, a.isAutoCommit());
        assertEquals(true, b.isAutoCommit());
    }
}
