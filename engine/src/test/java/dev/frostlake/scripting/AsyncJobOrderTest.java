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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When an asynchronous job takes effect on this engine, which runs one statement at a time: a job runs when it is
 * awaited, in the order the jobs were started, and one the body never waits for never runs. On an account such a
 * job is ended with its block however far it came, so what it leaves behind there depends on the moment; the
 * class states the engine's own, settled answer and runs on the embedded engine alone. Everything about ASYNC
 * and AWAIT that does not depend on the moment is asserted on both sides in {@link AsyncJobTest}.
 */
public class AsyncJobOrderTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE jobs (n NUMBER)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String rows() {
        return answer("SELECT COUNT(*) || ':' || LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM jobs");
    }

    @Test
    public void aJobNeverAwaitedNeverRuns() {
        assertEquals("not awaited", answer(
            "EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs VALUES (9)); RETURN 'not awaited'; END; $$"));
        assertEquals("0:", rows());
        assertEquals("0 before", answer("""
            EXECUTE IMMEDIATE $$ DECLARE r RESULTSET; BEGIN r := ASYNC (INSERT INTO jobs VALUES (70)); \
            RETURN (SELECT COUNT(*) FROM jobs WHERE n = 70) || ' before'; END; $$"""));
        assertEquals("0:", rows());
    }

    @Test
    public void awaitAllRunsTheJobsInOrderAndStopsAtTheFirstFailure() {
        // The job before the failing one is done; the one after it is ended with the block.
        assertEquals("Division by zero / 0", answer("""
            EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs VALUES (1)); ASYNC (SELECT 1/0); \
            ASYNC (INSERT INTO jobs VALUES (4)); AWAIT ALL; RETURN 'no failure'; EXCEPTION WHEN OTHER THEN \
            RETURN SQLERRM || ' / ' || (SELECT COUNT(*) FROM jobs WHERE n = 4); END; $$"""));
        assertEquals("1:1", rows());
    }

    @Test
    public void theJobsAfterAFailureStayToBeAwaited() {
        assertEquals("1", answer("""
            EXECUTE IMMEDIATE $$ BEGIN ASYNC (SELECT 1/0); ASYNC (INSERT INTO jobs VALUES (40)); \
            BEGIN AWAIT ALL; EXCEPTION WHEN OTHER THEN LET z := 1; END; AWAIT ALL; \
            RETURN (SELECT COUNT(*) FROM jobs WHERE n = 40); END; $$"""));
        assertEquals("1:40", rows());
    }

    @Test
    public void aBodyAwaitsItsOwnJobsAlone() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE leaves_a_job() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN ASYNC (INSERT INTO jobs VALUES (5)); RETURN 'left'; END; $$""");
        // The procedure's job ends with its body: the caller's AWAIT ALL waits for the caller's own.
        assertEquals("6", answer("""
            EXECUTE IMMEDIATE $$ BEGIN ASYNC (INSERT INTO jobs VALUES (6)); CALL leaves_a_job(); AWAIT ALL; \
            RETURN (SELECT LISTAGG(n, ',') WITHIN GROUP (ORDER BY n) FROM jobs); END; $$"""));
    }
}
