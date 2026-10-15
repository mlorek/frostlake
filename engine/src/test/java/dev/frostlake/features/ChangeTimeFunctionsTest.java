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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$LAST_CHANGE_COMMIT_TIME answers a token that moves only when the table's rows do, and
 * SYSTEM$STREAM_GET_TABLE_TIMESTAMP the stamp of the table version at the stream's offset, which moves only when
 * the stream is consumed. Both answered a fresh clock reading on every call, and both answered for objects that
 * do not exist.
 */
public class ChangeTimeFunctionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ctf");
        engine.execute("USE SCHEMA ctf.PUBLIC");
        engine.execute("CREATE TABLE t1 (a INT)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private long number(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return ((Number) row.getValue(0)).longValue();
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void theTokenMovesWithTheRowsAndOnlyThen() {
        final long created = number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('t1')");
        assertEquals(created, number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('t1')"), "no write, the same token");
        engine.execute("INSERT INTO t1 VALUES (1)");
        final long first = number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('ctf.public.t1')");
        engine.execute("INSERT INTO t1 VALUES (2)");
        final long second = number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('t1')");
        assertTrue(created < first && first < second, created + " < " + first + " < " + second);
        assertTrue(second > 1_000_000_000_000_000_000L, "nanoseconds since the epoch: " + second);
        assertTrue(refusal("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('no_such_table')")
            .contains("Object 'NO_SUCH_TABLE' does not exist"));
    }

    @Test
    public void aViewsTokenMovesWithTheTablesItReads() {
        engine.execute("CREATE TABLE t2 (b INT)");
        engine.execute("CREATE VIEW v AS WITH c AS (SELECT a FROM t1) SELECT a FROM c GROUP BY a");
        final long before = number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('v')");
        engine.execute("INSERT INTO t2 VALUES (1)");
        assertEquals(before, number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('v')"), "a table the view does not read");
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertNotEquals(before, number("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('v')"), "a table the view reads");
    }

    /** Returns once the wall clock has moved on to another millisecond. */
    private static void awaitNextMillisecond() {
        final long now = System.currentTimeMillis();
        while (System.currentTimeMillis() == now) {
            Thread.onSpinWait();
        }
    }

    @Test
    public void theStreamsTableTimestampMovesWhenItIsConsumed() {
        engine.execute("CREATE STREAM s1 ON TABLE t1");
        final long offset = number("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('s1')");
        // The stamp is that of the table's version at the stream's offset and counts milliseconds, and a warm
        // engine runs these statements within one: the version the stream moves on to is made in a later
        // millisecond than the one it starts at.
        awaitNextMillisecond();
        engine.execute("INSERT INTO t1 VALUES (1)");
        assertEquals(offset, number("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('s1')"), "unconsumed");
        engine.execute("CREATE TABLE sink (a INT)");
        engine.execute("INSERT INTO sink SELECT a FROM s1");
        assertTrue(number("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('s1')") > offset, "consumed");
        assertTrue(refusal("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('no_such_stream')")
            .contains("must be a valid stream name"));
        engine.execute("CREATE VIEW vs AS SELECT a FROM t1");
        engine.execute("CREATE STREAM sv ON VIEW vs");
        assertTrue(refusal("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('sv')").contains("on a view"));
    }
}
