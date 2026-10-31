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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DECFLOAT column keeps its type and every digit of its values across an engine clone, a checkpoint and a restart
 * from the write-ahead log: a 38-digit integer, a third held to 38 digits, a FLOAT's binary value and a value whose
 * exponent no NUMBER reaches all read back as they were written. A DECFLOAT inside a VARIANT, an ARRAY or an OBJECT
 * keeps its type there too, whichever way the state is carried.
 */
public class DecfloatPersistenceTest {

    private static final String CHECK = "SELECT k, d::VARCHAR, SYSTEM$TYPEOF(d) FROM d.PUBLIC.t ORDER BY k";

    private static final String EXPECTED = "a, 12345678901234567890123456789012345678, DECFLOAT(38)[DECFLOAT18]"
        + " | b, 3.3333333333333333333333333333333333333e-1, DECFLOAT(38)[DECFLOAT18]"
        + " | c, 1.0000000000000000555111512312578270212e-1, DECFLOAT(38)[DECFLOAT18]"
        + " | e, 1.23e7002, DECFLOAT(38)[DECFLOAT18]";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("decfloat_persistence_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** Fill a DECFLOAT table on the engine. */
    private static void fill(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (k VARCHAR, d DECFLOAT)");
        engine.execute("INSERT INTO t SELECT 'a', 12345678901234567890123456789012345678::DECFLOAT");
        engine.execute("INSERT INTO t SELECT 'b', 1::DECFLOAT / 3");
        engine.execute("INSERT INTO t SELECT 'c', 0.1::FLOAT::DECFLOAT");
        engine.execute("INSERT INTO t SELECT 'e', DECFLOAT '123e7000'");
    }

    /** The check's rows, cells joined by a comma and rows by a bar. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    @Test
    public void aCloneAndACheckpointKeepEveryDigit() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        try {
            fill(original);
            assertEquals(EXPECTED, answer(original, CHECK));
            final DatabaseEngine clone = original.cloneInstance();
            try {
                assertEquals(EXPECTED, answer(clone, CHECK));
            } finally {
                clone.shutdown();
            }
            original.checkpointStateTo(dir.resolve("snapshot"));
        } finally {
            original.shutdown();
        }
        final DatabaseEngine restored = new DatabaseEngine();
        try {
            restored.restoreStateFrom(dir.resolve("snapshot"));
            assertEquals(EXPECTED, answer(restored, CHECK));
            assertEquals("DECFLOAT", answer(restored,
                "SELECT DATA_TYPE FROM d.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T' AND COLUMN_NAME = 'D'"));
        } finally {
            restored.shutdown();
        }
    }

    @Test
    public void aRestartFromTheLogKeepsEveryDigit() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            fill(engine);
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals(EXPECTED, answer(recovered, CHECK));
        } finally {
            recovered.shutdown();
        }
    }

    /** The semi-structured table's check: each member's type, its text, and its equality with a fresh one. */
    private static final String MEMBERS = "SELECT TYPEOF(v), TYPEOF(a[0]), TYPEOF(a[1]:k), TYPEOF(o:k), TYPEOF(o:n[0]),"
        + " TO_JSON(v), TO_JSON(a), TO_JSON(o), v = TO_VARIANT(DECFLOAT '1.5'),"
        + " ARRAY_CONTAINS(DECFLOAT '2.5'::VARIANT, a), o:k::VARCHAR FROM d.PUBLIC.s";

    private static final String MEMBERS_EXPECTED = "DECFLOAT, DECFLOAT, DECFLOAT, DECFLOAT, VARCHAR,"
        + " \"1.5\", [\"2.5\",{\"k\":\"3.3333333333333333333333333333333333333e-1\"}],"
        + " {\"k\":\"1e100\",\"n\":[\"1e100\"]}, true, true, 1e100";

    /** Fill a table of VARIANT, ARRAY and OBJECT values holding DECFLOATs, beside a text that spells one. */
    private static void fillMembers(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE IF NOT EXISTS d");
        engine.execute("CREATE TABLE d.PUBLIC.s (v VARIANT, a ARRAY, o OBJECT)");
        engine.execute("INSERT INTO d.PUBLIC.s SELECT TO_VARIANT(DECFLOAT '1.5'),"
            + " ARRAY_CONSTRUCT(DECFLOAT '2.5', OBJECT_CONSTRUCT('k', DECFLOAT '1' / 3)),"
            + " OBJECT_CONSTRUCT('k', DECFLOAT '1e100', 'n', ARRAY_CONSTRUCT('1e100'))");
    }

    @Test
    public void aDecfloatInsideAVariantKeepsItsTypeAcrossACloneAndASnapshot() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        try {
            fillMembers(original);
            assertEquals(MEMBERS_EXPECTED, answer(original, MEMBERS));
            original.checkpointStateTo(dir.resolve("snapshot"));
            final DatabaseEngine clone = original.cloneInstance();
            try {
                assertEquals(MEMBERS_EXPECTED, answer(clone, MEMBERS));
            } finally {
                clone.shutdown();
            }
        } finally {
            original.shutdown();
        }
        final DatabaseEngine restored = new DatabaseEngine();
        try {
            restored.restoreStateFrom(dir.resolve("snapshot"));
            assertEquals(MEMBERS_EXPECTED, answer(restored, MEMBERS));
        } finally {
            restored.shutdown();
        }
    }

    @Test
    public void aDecfloatInsideAVariantKeepsItsTypeAcrossACheckpointAndARestart() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            fillMembers(engine);
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals(MEMBERS_EXPECTED, answer(recovered, MEMBERS));
        } finally {
            recovered.shutdown();
        }
        final DatabaseEngine walOnly = new DatabaseEngine(walConfig());
        try {
            assertEquals(MEMBERS_EXPECTED, answer(walOnly, MEMBERS));
        } finally {
            walOnly.shutdown();
        }
    }
}
