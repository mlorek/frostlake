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
 * The members of a VARIANT, an ARRAY and an OBJECT keep their types across an engine clone, a checkpoint and a
 * restart from one: a double, a FLOAT's double, a non-finite one, a DATE, a TIME, the timestamps, a BINARY, a VECTOR,
 * a UUID and a scaled NUMBER's whole number read back as they were written. The text alone spells a double as a
 * decimal and the others as texts, and its bare NaN read back as no value at all.
 */
public class VariantMemberTypesPersistenceTest {

    private static final String CHECK = "SELECT TYPEOF(o:d), TYPEOF(o:b), TYPEOF(o:t), TYPEOF(o:n), TYPEOF(o:l),"
        + " TYPEOF(o:z), TYPEOF(o:f), TYPEOF(o:v), TYPEOF(o:u), TYPEOF(o:w), TYPEOF(a[0]), TYPEOF(x:x), TYPEOF(x:y),"
        + " TYPEOF(n), n::VARCHAR, TO_JSON(o:f), TO_JSON(x), TO_JSON(i), o:d::VARCHAR, AS_BINARY(o:b) FROM d.PUBLIC.t";

    private static final String EXPECTED = "DATE, BINARY, TIME, TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ, DOUBLE,"
        + " VECTOR, UUID, DECIMAL, BINARY, DOUBLE, DECIMAL, DOUBLE, NaN, 1.500000000000000e+00,"
        + " {\"x\":1.000000000000000e+05,\"y\":1.5,\"z\":3.000000000000000e+00}, {\"k\":Infinity}, 2024-01-02, ABCD";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("variant_members_");
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

    /** Fill a table of semi-structured values holding one member of each kind. */
    private static void fill(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE d");
        engine.execute("CREATE TABLE d.PUBLIC.t (o OBJECT, a ARRAY, x VARIANT, n VARIANT, i OBJECT)");
        engine.execute("INSERT INTO d.PUBLIC.t SELECT OBJECT_CONSTRUCT('d', '2024-01-02'::DATE,"
            + " 'b', TO_BINARY('ABCD', 'HEX'), 't', '10:11:12'::TIME, 'n', '2024-01-02 10:11:12'::TIMESTAMP_NTZ,"
            + " 'l', '2024-01-02 10:11:12'::TIMESTAMP_LTZ, 'z', '2024-01-02 10:11:12 +01:00'::TIMESTAMP_TZ,"
            + " 'f', 1.5::FLOAT, 'v', [1, 2]::VECTOR(INT, 2), 'u', UUID_STRING()::UUID, 'w', 3.00::NUMBER(10, 2)),"
            + " ARRAY_CONSTRUCT(TO_BINARY('AB', 'HEX')), PARSE_JSON('{\"x\": 1e5, \"y\": 1.5, \"z\": 3e0}'),"
            + " TO_VARIANT('NaN'::FLOAT), OBJECT_CONSTRUCT('k', 'inf'::FLOAT)");
    }

    /** The check's rows, cells joined by a comma and rows by a bar. */
    private static String answer(final DatabaseEngine engine) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(CHECK).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    @Test
    public void aCloneAndASnapshotKeepTheMembersTypes() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        try {
            fill(original);
            assertEquals(EXPECTED, answer(original));
            final DatabaseEngine clone = original.cloneInstance();
            try {
                assertEquals(EXPECTED, answer(clone));
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
            assertEquals(EXPECTED, answer(restored));
        } finally {
            restored.shutdown();
        }
    }

    @Test
    public void aRestartFromACheckpointKeepsTheMembersTypes() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            fill(engine);
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals(EXPECTED, answer(recovered));
        } finally {
            recovered.shutdown();
        }
    }
}
