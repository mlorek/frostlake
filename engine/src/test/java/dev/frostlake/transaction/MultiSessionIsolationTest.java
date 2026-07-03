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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Multi-session READ COMMITTED isolation (Phase 2). With deferred apply (the default), each session carries
 * its own transaction + write set — round-tripped through its {@link SessionContext} by
 * {@link ConcurrentDatabaseEngine} — so one session's UNCOMMITTED writes are invisible to another until
 * COMMIT, while each session always sees its own. This is the multi-session form of the per-transaction
 * overlay. See docs/acid-snowflake-plan.md.
 */
public class MultiSessionIsolationTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void uncommittedWritesAreInvisibleToOtherSessionsUntilCommit() {
        final SessionContext s1 = engine.createSession();
        final SessionContext s2 = engine.createSession();

        // Shared, committed table (DDL + an autocommit INSERT).
        engine.execute("CREATE DATABASE db", s1);
        engine.execute("USE DATABASE db", s1);
        engine.execute("CREATE SCHEMA s", s1);
        engine.execute("USE SCHEMA s", s1);
        engine.execute("CREATE TABLE db.s.t (id INTEGER)", s1);
        engine.execute("INSERT INTO db.s.t VALUES (1)", s1);   // committed

        // Session 1 opens an explicit transaction and inserts — uncommitted.
        engine.execute("BEGIN", s1);
        engine.execute("INSERT INTO db.s.t VALUES (2)", s1);

        // Session 1 sees its own uncommitted write; session 2 must NOT (READ COMMITTED).
        assertEquals(2, engine.executeQuery("SELECT * FROM db.s.t", s1).getRowCount(),
            "a session sees its own uncommitted write");
        assertEquals(1, engine.executeQuery("SELECT * FROM db.s.t", s2).getRowCount(),
            "another session must NOT see uncommitted writes");

        // After commit, the write becomes visible to session 2.
        engine.execute("COMMIT", s1);
        assertEquals(2, engine.executeQuery("SELECT * FROM db.s.t", s2).getRowCount(),
            "committed writes become visible to other sessions");
    }
}
