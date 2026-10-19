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

package dev.frostlake.stage;

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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An engine that keeps nothing across a restart is a database of its own, and so are its internal stages: two such
 * engines on one stage root, whose named stages, table stages and user stages share their names, never see or erase
 * each other's files, and an engine's files end with it.
 */
public class EngineOwnStagesTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    private Path stages;

    @BeforeEach
    public void setUp() throws IOException {
        stages = Files.createTempDirectory("engine_own_stages_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(stages.toFile());
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

    /** An engine on the shared stage root, in database FL. */
    private DatabaseEngine engine() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, stages.toString());
        final DatabaseEngine engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE fl");
        engine.execute("USE SCHEMA fl.PUBLIC");
        return engine;
    }

    /** Every row's first cell, a bar between rows. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    /** The names directly under the stage root. */
    private List<String> entries() {
        final List<String> names = new ArrayList<>();
        final File[] files = stages.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }

    @Test
    public void aNamedStageOfTheSameNameIsTheOtherEnginesOwn() {
        final DatabaseEngine a = engine();
        final DatabaseEngine b = engine();
        try {
            a.execute("CREATE STAGE s");
            a.execute("COPY INTO @s/from_a FROM (SELECT 'from A')" + CSV);
            // A stage created in the other engine starts empty and erases nothing.
            b.execute("CREATE STAGE s");
            assertEquals("", answer(b, "LIST @s"));
            b.execute("COPY INTO @s/from_b FROM (SELECT 'from B')" + CSV);
            assertEquals("s/from_a", answer(a, "LIST @s"));
            assertEquals("from A", answer(a, "SELECT $1 FROM @s"));
            assertEquals("s/from_b", answer(b, "LIST @s"));
        } finally {
            a.shutdown();
            b.shutdown();
        }
    }

    @Test
    public void aTableStageOfTheSameNameIsTheOtherEnginesOwn() {
        final DatabaseEngine a = engine();
        final DatabaseEngine b = engine();
        try {
            a.execute("CREATE TABLE t (id INT, v VARCHAR)");
            a.execute("COPY INTO @%t/f FROM (SELECT 1, 'one')" + CSV);
            b.execute("CREATE TABLE t (id INT, v VARCHAR)");
            assertEquals("f", answer(a, "LIST @%t"));
            assertEquals("", answer(b, "LIST @%t"));
        } finally {
            a.shutdown();
            b.shutdown();
        }
    }

    @Test
    public void theUserStageIsTheEnginesOwn() {
        final DatabaseEngine a = engine();
        final DatabaseEngine b = engine();
        try {
            a.execute("COPY INTO @~/u FROM (SELECT 1)" + CSV);
            assertEquals(1, a.executeQuery("LIST @~").getRows().size());
            assertEquals(0, b.executeQuery("LIST @~").getRows().size());
        } finally {
            a.shutdown();
            b.shutdown();
        }
    }

    @Test
    public void anEnginesStageFilesEndWithIt() {
        final DatabaseEngine a = engine();
        final DatabaseEngine b = engine();
        try {
            a.execute("CREATE STAGE s");
            a.execute("COPY INTO @s/from_a FROM (SELECT 'from A')" + CSV);
            b.execute("CREATE STAGE s");
            b.execute("COPY INTO @s/from_b FROM (SELECT 'from B')" + CSV);
            assertEquals(2, entries().size());
            a.shutdown();
            assertEquals(1, entries().size());
            assertEquals("s/from_b", answer(b, "LIST @s"));
        } finally {
            b.shutdown();
        }
        assertEquals(List.of(), entries());
    }
}
