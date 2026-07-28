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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine instance cloning via {@link DatabaseEngine#checkpointStateTo(Path)} /
 * {@link DatabaseEngine#restoreStateFrom(Path)} — the template pattern behind the test-harness seed
 * cache: migrate once, checkpoint, boot any number of equivalent engines from the snapshot. The
 * clone must carry data, views, sequence positions, procedures and live streams, and be fully
 * isolated from the original.
 */
public class EngineStateCloneTest {

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    @Test
    public void checkpointRestoreClonesAWorkingIsolatedEngine() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE seed");
        original.execute("USE DATABASE seed");
        original.execute("CREATE SCHEMA s");
        original.execute("USE SCHEMA s");
        original.execute("CREATE TABLE t (id INTEGER, name VARCHAR, payload VARIANT)");
        original.execute("INSERT INTO t VALUES (1, 'a', PARSE_JSON('{\"k\":1}')), (2, 'b', NULL)");
        original.execute("CREATE SEQUENCE seq1 START = 100");
        original.execute("SELECT seq1.NEXTVAL");
        original.execute("CREATE VIEW v1 AS SELECT COUNT(*) c FROM t");
        original.execute("CREATE PROCEDURE p1() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'hi'; END");
        original.execute("CREATE STREAM st1 ON TABLE t");
        original.execute("CREATE TABLE scaled (v NUMBER(12,4))");
        original.execute("INSERT INTO scaled VALUES (0.0770), (0.0772)");
        original.execute("CREATE STAGE jar_stage URL='file:///tmp/engine_clone_stage'");

        dir = Files.createTempDirectory("engine_state_clone_");
        original.checkpointStateTo(dir);

        final DatabaseEngine clone = new DatabaseEngine();
        clone.restoreStateFrom(dir);
        clone.execute("USE DATABASE seed");
        clone.execute("USE SCHEMA s");

        assertEquals(2L, scalar(clone, "SELECT COUNT(*) FROM t"));
        assertEquals(2L, scalar(clone, "SELECT c FROM v1"));
        // The sequence resumes where the original left off — it does not restart at 100.
        assertEquals(101L, scalar(clone, "SELECT seq1.NEXTVAL"));
        assertEquals("hi", scalar(clone, "CALL p1()").toString());

        // NUMBER(p,s) keeps its scale through the round-trip — a bare-name restore recreated the
        // column as NUMBER (scale 0) and every fractional computation silently went integer.
        assertEquals(0, new BigDecimal("0.0770")
            .compareTo(new BigDecimal(scalar(clone, "SELECT MIN(v) FROM scaled").toString())));
        assertEquals(0, new BigDecimal("0.0771")
            .compareTo(new BigDecimal(scalar(clone, "SELECT AVG(v) FROM scaled").toString())));

        // Stages round-trip by definition — a restored engine must still resolve @stage references
        // (COPY, UDF IMPORTS); they were silently dropped before StageSnapshot moved to schema scope.
        assertEquals("file:///tmp/engine_clone_stage", clone.getCatalog().getStage("jar_stage").getUrl());

        // The restored stream is live: it captures writes made in the clone.
        clone.execute("INSERT INTO t SELECT 3, 'clone-only', NULL");
        assertEquals(1L, scalar(clone, "SELECT COUNT(*) FROM st1"));

        // Full isolation: the clone's write is invisible to the original, and vice versa.
        assertEquals(2L, scalar(original, "SELECT COUNT(*) FROM t"));
        original.execute("USE DATABASE seed");
        original.execute("USE SCHEMA s");
        original.execute("INSERT INTO t SELECT 4, 'original-only', NULL");
        assertEquals(3L, scalar(clone, "SELECT COUNT(*) FROM t"));
        assertEquals(3L, scalar(original, "SELECT COUNT(*) FROM t"));
    }

    @Test
    public void cloneInstanceIsAOneCallIndependentTwin() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE d");
        original.execute("USE DATABASE d");
        original.execute("CREATE SCHEMA s");
        original.execute("USE SCHEMA s");
        original.execute("CREATE TABLE t (id INTEGER)");
        original.execute("INSERT INTO t VALUES (1), (2)");

        final DatabaseEngine clone = original.cloneInstance();
        clone.execute("USE DATABASE d");
        clone.execute("USE SCHEMA s");
        assertEquals(2L, scalar(clone, "SELECT COUNT(*) FROM t"));

        clone.execute("INSERT INTO t VALUES (3)");
        assertEquals(3L, scalar(clone, "SELECT COUNT(*) FROM t"));
        assertEquals(2L, scalar(original, "SELECT COUNT(*) FROM t"));
    }

    @Test
    public void directConnectionUnwrapsToItsEngine() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:frostlake:direct:unwrap_test_db")) {
            assertTrue(conn.isWrapperFor(DatabaseEngine.class));
            final DatabaseEngine engine = conn.unwrap(DatabaseEngine.class);
            try (Connection again = DriverManager.getConnection("jdbc:frostlake:direct:unwrap_test_db")) {
                // Same direct name — the driver hands out the same shared engine instance.
                assertSame(engine, again.unwrap(DatabaseEngine.class));
            }
        }
    }

    private static Object scalar(final DatabaseEngine engine, final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
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
