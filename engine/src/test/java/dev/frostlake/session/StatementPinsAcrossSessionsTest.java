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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement that starts while another session's statement is still running is the outermost statement of its own
 * thread: it reads its session's time zone and its refusals address its session's role, as they do when it runs
 * alone. The engine counted statements in progress across every thread, so such a statement ran with no zone and
 * no role pinned at all — a TIMESTAMP_LTZ rendered in UTC and a missing object's refusal lost its privilege
 * sentence.
 */
public class StatementPinsAcrossSessionsTest {

    private static final String MISSING = "SELECT * FROM nosuch_pin_t";
    private static final String ZONED = "SELECT TO_VARCHAR('2024-01-01 00:00:00 +00:00'::TIMESTAMP_LTZ)";
    private static final long PATIENCE_SECONDS = 60L;

    private ConcurrentDatabaseEngine concurrent;

    @AfterEach
    public void tearDown() {
        if (concurrent != null) {
            concurrent.shutdown();
        }
    }

    private String refusal(final String sql, final SessionContext session) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                concurrent.executeQuery(sql, session);
            }
        }).getMessage();
    }

    private String text(final String sql, final SessionContext session) {
        final ResultSet answer = concurrent.executeQuery(sql, session);
        return String.valueOf(answer.getRows().get(0).getValue(0));
    }

    @Test
    public void aStatementStartedWhileAnotherSessionsRunsKeepsItsZoneAndItsRole() throws InterruptedException {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext holder = concurrent.createSession();
        final SessionContext other = concurrent.createSession();
        concurrent.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'", other);
        final String refusedAlone = refusal(MISSING, other);
        final String zonedAlone = text(ZONED, other);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        concurrent.getEngine().getFunctionRegistry().register(new BuiltInFunction("FL_HOLD_OPEN", StringType.VARCHAR) {
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
                concurrent.executeQuery("SELECT FL_HOLD_OPEN()", holder);
            }
        });
        holding.start();
        try {
            assertTrue(entered.await(PATIENCE_SECONDS, TimeUnit.SECONDS), "the holding statement never started");
            assertEquals(refusedAlone, refusal(MISSING, other));
            assertEquals(zonedAlone, text(ZONED, other));
        } finally {
            release.countDown();
            holding.join();
        }
        assertTrue(refusedAlone.contains("Your primary role"), refusedAlone);
        assertEquals("2023-12-31 16:00:00.000 -0800", zonedAlone);
    }
}
