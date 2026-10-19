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

package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.http.SessionContext;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CTE is in scope for its own statement alone: while one session's statement runs inside its WITH, another
 * session's statement naming a table of the same name reads the table.
 */
public class CteScopeAcrossSessionsTest {

    private static final long PATIENCE_SECONDS = 60L;

    private ConcurrentDatabaseEngine concurrent;

    @AfterEach
    public void tearDown() {
        if (concurrent != null) {
            concurrent.shutdown();
        }
    }

    private String text(final String sql, final SessionContext session) {
        return String.valueOf(concurrent.executeQuery(sql, session).getRows().get(0).getValue(0));
    }

    @Test
    public void anotherSessionsCteIsNotInScope() throws InterruptedException {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext holder = concurrent.createSession();
        final SessionContext other = concurrent.createSession();
        concurrent.execute("CREATE DATABASE cte_db", other);
        concurrent.execute("USE SCHEMA cte_db.PUBLIC", other);
        concurrent.execute("CREATE TABLE t (a INT)", other);
        concurrent.execute("INSERT INTO t VALUES (1), (2), (3)", other);
        concurrent.execute("USE SCHEMA cte_db.PUBLIC", holder);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        concurrent.getEngine().getFunctionRegistry().register(new BuiltInFunction("FL_HOLD_CTE", StringType.VARCHAR) {
            @Override
            public Object evaluate(final List<Object> args) {
                entered.countDown();
                try {
                    release.await(PATIENCE_SECONDS, TimeUnit.SECONDS);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return "held";
            }

            @Override
            public int getMinArgCount() {
                return 0;
            }

            @Override
            public int getMaxArgCount() {
                return 0;
            }
        });
        final Thread holding = new Thread(new Runnable() {
            @Override
            public void run() {
                concurrent.executeQuery("WITH t AS (SELECT 42 AS a) SELECT FL_HOLD_CTE() FROM t", holder);
            }
        });
        holding.start();
        try {
            assertTrue(entered.await(PATIENCE_SECONDS, TimeUnit.SECONDS), "the holding statement never started");
            assertEquals("3", text("SELECT COUNT(*) FROM t", other));
            assertEquals("3", text("SELECT COUNT(*) FROM t WHERE a IN (SELECT a FROM t)", other));
        } finally {
            release.countDown();
            holding.join();
        }
    }
}
