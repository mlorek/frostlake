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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a stored procedure's Snowpark session allows across threads.
 *
 * <p>Snowflake documents the limitation as "you cannot submit queries from multiple threads", which
 * reads like a thread-identity rule, and Frostlake enforced it as one: the {@code Session} captured
 * the handler's thread and refused any other. Measured on a real account, that is too strict —
 * a handler that hands its session to a worker thread and waits for the result runs fine, twice over:
 *
 * <pre>
 *   session.sql()   on the handler's own thread                   accepted
 *   session.sql()   from a worker thread, handler waiting         accepted
 *   session.table() from a worker thread, handler waiting         accepted
 *   four threads released at once                                 first one wins, the rest fail
 * </pre>
 *
 * <p>So the constraint is on queries being in flight at the same time, not on which thread submits
 * them. This test pins the sequential half, which is the half Frostlake used to get wrong: refusing
 * a handler that a real account accepts. The refusing half is a race by nature, and pinning it would
 * need a sleep, so it is left to the guard itself.
 */
public class SessionConcurrencyTest {

    private static final Logger logger = LoggerFactory.getLogger(SessionConcurrencyTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * The handler never runs a query itself — it hands the session to a worker and waits. Both calls
     * therefore arrive from a thread that is not the one running the procedure, one after the other.
     */
    @Test
    public void aWorkerThreadMaySubmitWhileTheHandlerWaits() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE worker_queries()
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER='WorkerQueries.run'
            AS
            $$
            import com.snowflake.snowpark_java.Session;
            import java.util.concurrent.Callable;
            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public class WorkerQueries {
              public String run(final Session session) throws Exception {
                ExecutorService pool = Executors.newSingleThreadExecutor();
                Future<String> selected = pool.submit(new Callable<String>() {
                  public String call() {
                    return String.valueOf(session.sql("SELECT n FROM nums").collect().length);
                  }
                });
                String first = selected.get();
                Future<String> scanned = pool.submit(new Callable<String>() {
                  public String call() {
                    return String.valueOf(session.table("nums").collect().length);
                  }
                });
                String second = scanned.get();
                pool.shutdown();
                return first + "," + second;
              }
            }
            $$
            """);

        final ResultSet rs = engine.executeQuery("CALL worker_queries()");
        final Object out = rs.getRows().get(0).getValues().get(0);
        logger.info("sequential cross-thread submission returned: {}", out);
        assertEquals("3,3", String.valueOf(out));
    }
}
