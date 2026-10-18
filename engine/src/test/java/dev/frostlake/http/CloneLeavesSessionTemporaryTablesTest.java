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
import dev.frostlake.ExecutionResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database or schema clone made in a session that holds a temporary table copies the permanent table the temporary
 * one hides: a session's temporary table is no part of the database (Snowflake's CLONE pages), so its rows never
 * reach the clone, which every session reads, and a temporary table of another shape does not stop the clone.
 */
public class CloneLeavesSessionTemporaryTablesTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext first;
    private SessionContext second;

    @BeforeEach
    public void startEngine() {
        engine = new ConcurrentDatabaseEngine();
        first = engine.createSession();
        second = engine.createSession();
        run(first, "CREATE DATABASE d");
        run(first, "USE SCHEMA d.public");
        run(second, "USE SCHEMA d.public");
        run(first, "CREATE TABLE t (id INT, v STRING)");
        run(first, "INSERT INTO t VALUES (1, 'perm')");
        run(first, "CREATE TEMPORARY TABLE t (id INT, v STRING)");
        run(first, "INSERT INTO t VALUES (9, 'temp')");
    }

    @AfterEach
    public void stopEngine() {
        engine.shutdown();
    }

    /** A statement's first cell, or its refusal on one line. */
    private String answer(final SessionContext session, final String sql) {
        final ExecutionResult result;
        try {
            result = engine.execute(sql, session);
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
        if (!result.isSuccess()) {
            return "REFUSED " + String.valueOf(result.getErrorMessage()).replace("\n", " | ");
        }
        if (result.getResultSets().isEmpty() || result.getResultSets().get(0).getRows().isEmpty()) {
            return "";
        }
        return String.valueOf(result.getResultSets().get(0).getRows().get(0).getValue(0));
    }

    private void run(final SessionContext session, final String sql) {
        final String answered = answer(session, sql);
        assertTrue(!answered.startsWith("REFUSED"), sql + " ==> " + answered);
    }

    @Test
    public void aDatabaseCloneCopiesThePermanentTable() {
        run(first, "CREATE DATABASE q CLONE d");
        assertEquals("perm", answer(second, "SELECT v FROM q.public.t"));
        assertEquals("perm", answer(first, "SELECT v FROM q.public.t"));
        assertEquals("1", answer(second, "SELECT COUNT(*) FROM q.public.t"));
        assertEquals("temp", answer(first, "SELECT v FROM d.public.t"));
        assertEquals("perm", answer(second, "SELECT v FROM d.public.t"));
    }

    @Test
    public void aSchemaCloneIsNotStoppedByAnotherShape() {
        run(first, "CREATE TABLE t2 (id INT, v INT)");
        run(first, "INSERT INTO t2 VALUES (1, 2)");
        run(first, "CREATE TEMPORARY TABLE t2 (a STRING, b STRING, c STRING)");
        run(first, "INSERT INTO t2 VALUES ('x', 'y', 'z')");
        run(first, "CREATE SCHEMA s2 CLONE public");
        assertEquals("2", answer(second, "SELECT v FROM d.s2.t2"));
        assertEquals("perm", answer(second, "SELECT v FROM d.s2.t"));
    }
}
