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

package dev.frostlake.persistence;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An engine's copies - {@code cloneInstance()}, and a {@code checkpointStateTo} snapshot rebuilt by
 * {@code restoreStateFrom} - carry nothing a session of the source created TEMPORARY. A temporary object's lifetime
 * is the session that created it, "not visible to other users or sessions" and purged when it ends (Snowflake's
 * temporary tables), and a copy has none of the source's sessions: in the copy the names are free, as they are after
 * a restart, while the source's session keeps its own. What the restoring engine's own sessions hold stays theirs.
 */
public class TemporaryObjectsLeaveEngineCopiesTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext first;
    private SessionContext second;
    private Path dir;

    @BeforeEach
    public void startEngine() {
        engine = new ConcurrentDatabaseEngine();
        first = engine.createSession();
        second = engine.createSession();
        run(engine, second, "CREATE DATABASE d");
        run(engine, second, "USE SCHEMA d.public");
        run(engine, first, "USE SCHEMA d.public");
        run(engine, second, "CREATE TABLE perm (v INT)");
        run(engine, second, "INSERT INTO perm VALUES (1)");
        run(engine, second, "CREATE TEMPORARY TABLE tmp (v INT)");
        run(engine, second, "INSERT INTO tmp VALUES (10)");
        run(engine, second, "CREATE TEMPORARY VIEW tv AS SELECT 1 AS one");
        run(engine, second, "CREATE TEMPORARY FUNCTION tf() RETURNS INT AS '7'");
        run(engine, second, "CREATE TEMPORARY STAGE ts");
    }

    @AfterEach
    public void stopEngine() {
        engine.shutdown();
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    @Test
    public void aCloneCarriesNoneOfTheSourceSessionsTemporaries() throws Exception {
        assertTrue(answer(engine, first, "SELECT v FROM tmp").startsWith("REFUSED"));
        final DatabaseEngine clone = engine.getEngine().cloneInstance();
        try {
            assertCopyHoldsNoTemporaries(clone);
        } finally {
            clone.shutdown();
        }
        assertEquals("10", answer(engine, second, "SELECT v FROM tmp"));
    }

    @Test
    public void aRestoredSnapshotCarriesNoneOfThemEither() throws Exception {
        dir = Files.createTempDirectory("temporary_copies_");
        engine.getEngine().checkpointStateTo(dir);
        final DatabaseEngine copy = new DatabaseEngine();
        try {
            copy.restoreStateFrom(dir);
            assertCopyHoldsNoTemporaries(copy);
        } finally {
            copy.shutdown();
        }
        assertEquals("10", answer(engine, second, "SELECT v FROM tmp"));
    }

    @Test
    public void aRestoreKeepsWhatTheRestoringEnginesOwnSessionsHold() throws Exception {
        dir = Files.createTempDirectory("temporary_copies_");
        engine.getEngine().checkpointStateTo(dir);
        final ConcurrentDatabaseEngine target = new ConcurrentDatabaseEngine();
        try {
            final SessionContext own = target.createSession();
            run(target, own, "CREATE DATABASE e");
            run(target, own, "USE SCHEMA e.public");
            run(target, own, "CREATE TEMPORARY TABLE mine (v INT)");
            run(target, own, "INSERT INTO mine VALUES (5)");
            target.getEngine().restoreStateFrom(dir);
            assertEquals("5", answer(target, own, "SELECT v FROM e.public.mine"));
            assertEquals("1", answer(target, own, "SELECT v FROM d.public.perm"));
            assertRefused(answer(target, own, "SELECT v FROM d.public.tmp"), "does not exist");
        } finally {
            target.shutdown();
        }
    }

    /** The permanent table came with the copy; every temporary object of the source's session stayed behind. */
    private static void assertCopyHoldsNoTemporaries(final DatabaseEngine copy) {
        assertEquals("1", answer(copy, "SELECT COUNT(*) FROM d.public.perm"));
        assertRefused(answer(copy, "SELECT v FROM d.public.tmp"), "does not exist");
        assertRefused(answer(copy, "SELECT one FROM d.public.tv"), "does not exist");
        assertRefused(answer(copy, "SELECT d.public.tf()"), "");
        assertRefused(answer(copy, "LIST @d.public.ts"), "does not exist");
        // The name is free in the copy: a temporary table of its own takes it.
        final String created = answer(copy, "CREATE TEMPORARY TABLE d.public.tmp (v INT)");
        assertTrue(!created.startsWith("REFUSED"), created);
        assertEquals("0", answer(copy, "SELECT COUNT(*) FROM d.public.tmp"));
    }

    private static void assertRefused(final String answered, final String expected) {
        assertTrue(answered.startsWith("REFUSED") && answered.contains(expected), answered);
    }

    /** A statement's first cell on an embedded engine, or its refusal on one line. */
    private static String answer(final DatabaseEngine target, final String sql) {
        try {
            final ResultSet result = target.executeQuery(sql);
            return result.getRows().isEmpty() ? "" : String.valueOf(result.getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
    }

    /** A statement's first cell in a session, or its refusal on one line. */
    private static String answer(final ConcurrentDatabaseEngine target, final SessionContext session,
                                 final String sql) {
        final ExecutionResult result;
        try {
            result = target.execute(sql, session);
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

    private static void run(final ConcurrentDatabaseEngine target, final SessionContext session, final String sql) {
        final String answered = answer(target, session, sql);
        assertTrue(!answered.startsWith("REFUSED"), sql + " ==> " + answered);
    }

    private static void deleteRecursively(final File f) {
        final File[] children = f.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
