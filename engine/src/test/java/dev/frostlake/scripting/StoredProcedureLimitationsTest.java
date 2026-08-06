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

package dev.frostlake.scripting;

import com.snowflake.snowpark_java.Row;
import com.snowflake.snowpark_java.Session;
import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Java/Scala stored-procedure limitations enforced by the Snowpark {@link Session} stub (which
 * {@code JavaProcedureExecutor} and the rt-scala module's {@code ScalaProcedureExecutor} inject into
 * handlers): only one query in flight
 * at a time (concurrency), no named temporary objects under owner's rights, and the standing bans on
 * creating a new session / obtaining the JDBC connection. Mirrors
 * <a href="https://docs.snowflake.com/en/developer-guide/stored-procedure/java/procedure-java-limitations">the
 * documented limitations</a>.
 */
public class StoredProcedureLimitationsTest extends BaseDatabaseTest {

    @Test
    public void queryOnTheOwningThreadWorks() {
        final Session session = new Session(engine);
        assertEquals(1, session.sql("SELECT 1").collect().length);
    }

    /**
     * Nothing below submits until an action asks it to, so every test here collects. Measured on live:
     * a handler that calls {@code session.sql(...)} and never collects submits no statement at all — the
     * permit, the owner's-rights refusal and the query itself all happen at the action.
     */
    @Test
    public void aPlanWithNoActionSubmitsNothing() {
        final Session session = new Session(engine);
        engine.execute("CREATE TABLE permit_sink (n INTEGER)");
        session.sql("INSERT INTO permit_sink VALUES (1)");
        assertEquals(0, engine.executeQuery("SELECT * FROM permit_sink").getRowCount(),
            "session.sql alone must not run the statement");
        session.sql("INSERT INTO permit_sink VALUES (1)").collect();
        assertEquals(1, engine.executeQuery("SELECT * FROM permit_sink").getRowCount());
    }

    /**
     * A worker thread may submit, as long as nothing else is running. Frostlake used to refuse this: the
     * documented limitation ("you cannot submit queries from multiple threads") was read as a thread-identity
     * rule and the session captured its creating thread. A real account accepts it — the handler hands its
     * session to a worker, waits, and gets rows back — so refusing was a false rejection.
     */
    @Test
    public void aWorkerThreadMaySubmitWhenNothingElseIsRunning() throws InterruptedException {
        final Session session = new Session(engine);
        final Object[] outcome = new Object[1];
        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    outcome[0] = session.sql("SELECT 1").collect();
                } catch (final Throwable t) {
                    outcome[0] = t;
                }
            }
        });
        worker.start();
        worker.join();
        assertTrue(outcome[0] instanceof Row[], "a worker thread's query must be accepted, got: " + outcome[0]);
    }

    /**
     * What live actually refuses: a second query while the first is still running. The engine here blocks
     * inside {@code executeQuery} until the test releases it, so the overlap is real rather than raced —
     * the refusal happens before the second call reaches the engine, which is why nothing deadlocks.
     */
    @Test
    public void aSecondQueryWhileOneIsRunningIsRejected() throws InterruptedException {
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final DatabaseEngine blocking = new DatabaseEngine() {
            @Override
            public ResultSet executeQuery(final String sql) {
                running.countDown();
                try {
                    release.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.executeQuery(sql);
            }
        };
        final Session session = new Session(blocking);
        final Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                session.sql("SELECT 1").collect();
            }
        });
        first.start();
        running.await();

        final UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
            new Executable() {
                @Override
                public void execute() {
                    session.sql("SELECT 2").collect();
                }
            });
        assertTrue(ex.getMessage().contains("Concurrency"), ex.getMessage());

        release.countDown();
        first.join();
        blocking.shutdown();
    }

    /** And the permit is returned: once the first query finishes, the session takes queries again. */
    @Test
    public void thePermitIsReleasedWhenTheQueryFinishes() {
        final Session session = new Session(engine);
        assertEquals(1, session.sql("SELECT 1").collect().length);
        assertEquals(1, session.sql("SELECT 2").collect().length);
    }

    @Test
    public void ownersRightsRejectsNamedTemporaryObject() {
        final Session owner = new Session(engine, true);
        final UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
            new Executable() {
                @Override
                public void execute() {
                    owner.sql("CREATE TEMPORARY TABLE tmp_owner (a INTEGER)").collect();
                }
            });
        assertEquals("Stored procedure execution error: Unsupported statement type 'temporary TABLE'.",
            ex.getMessage(), "the refusal must be live's sentence, naming the kind");
    }

    @Test
    public void callersRightsAllowsTemporaryObject() {
        // EXECUTE AS CALLER (ownersRights = false) is unrestricted: the temp table is actually created.
        final Session caller = new Session(engine, false);
        assertNotNull(caller.sql("CREATE TEMPORARY TABLE tmp_caller (a INTEGER)").collect());
    }

    @Test
    public void newSessionAndJdbcConnectionRemainRejected() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                Session.builder().create();
            }
        });
        final Session session = new Session(engine);
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                session.jdbcConnection();
            }
        });
    }
}
