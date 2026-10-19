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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A dropped table's, schema's or database's stage files wait for its UNDROP where an object that takes a name later
 * does not reach them, and go when the version can no longer be undropped. An engine that keeps nothing across a
 * restart keeps them in a directory of its own, which it deletes when it shuts down, or the JVM deletes when it exits,
 * so no run leaves them behind. Several engines share the stage root, and none deletes what another still needs.
 */
public class DroppedObjectStagesTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    private Path stages;

    @BeforeEach
    public void setUp() throws IOException {
        stages = Files.createTempDirectory("dropped_object_stages_");
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

    private DatabaseEngine engine() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, stages.toString());
        return new DatabaseEngine(cfg);
    }

    /** Every row's first cell, a bar between rows. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    /** The stage directories set aside anywhere under the stage root, an engine's own included, by their names. */
    private List<String> setAside() throws IOException {
        final List<String> found = new ArrayList<>();
        try (final java.util.stream.Stream<Path> walk = Files.walk(stages)) {
            final Iterator<Path> paths = walk.iterator();
            while (paths.hasNext()) {
                final String name = paths.next().getFileName().toString();
                if (name.contains("@")) {
                    found.add(name.substring(0, name.indexOf('@')));
                }
            }
        }
        return found;
    }

    /** The names directly under a directory, sorted. */
    private static List<String> entries(final Path dir) {
        final List<String> names = new ArrayList<>();
        final File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                names.add(file.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private static void stageFile(final DatabaseEngine engine, final String stage, final int value) {
        engine.execute("COPY INTO " + stage + " FROM (SELECT " + value + " AS a)" + CSV);
    }

    @Test
    public void aDroppedSchemasTablesAndStagesWaitForItsUndrop() {
        final DatabaseEngine engine = engine();
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("CREATE SCHEMA d.s");
            engine.execute("CREATE TABLE d.s.t (a INT)");
            stageFile(engine, "@d.s.%t/f", 1);
            engine.execute("CREATE STAGE d.s.st");
            stageFile(engine, "@d.s.st/g", 2);
            engine.execute("DROP SCHEMA d.s");
            // The objects that take the names start empty.
            engine.execute("CREATE SCHEMA d.s");
            engine.execute("CREATE TABLE d.s.t (a INT)");
            engine.execute("CREATE STAGE d.s.st");
            assertEquals("", answer(engine, "LIST @d.s.%t"));
            assertEquals("", answer(engine, "LIST @d.s.st"));
            engine.execute("ALTER SCHEMA d.s RENAME TO d.s_new");
            engine.execute("UNDROP SCHEMA d.s");
            assertEquals("f", answer(engine, "LIST @d.s.%t"));
            assertEquals("st/g", answer(engine, "LIST @d.s.st"));
            assertEquals("2", answer(engine, "SELECT $1 FROM @d.s.st/g"));
            assertEquals("", answer(engine, "LIST @d.s_new.%t"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aDroppedDatabasesTablesWaitForItsUndrop() {
        final DatabaseEngine engine = engine();
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("CREATE TABLE d.PUBLIC.t (a INT)");
            stageFile(engine, "@d.PUBLIC.%t/f", 1);
            engine.execute("DROP DATABASE d");
            engine.execute("CREATE DATABASE d");
            engine.execute("CREATE TABLE d.PUBLIC.t (a INT)");
            assertEquals("", answer(engine, "LIST @d.PUBLIC.%t"));
            engine.execute("ALTER DATABASE d RENAME TO d_new");
            engine.execute("UNDROP DATABASE d");
            assertEquals("f", answer(engine, "LIST @d.PUBLIC.%t"));
            assertEquals("1", answer(engine, "SELECT $1 FROM @d.PUBLIC.%t/f"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aVersionThatCanNoLongerBeUndroppedTakesItsStageWithIt() throws IOException {
        final DatabaseEngine engine = engine();
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE z (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
            stageFile(engine, "@%z/h", 3);
            engine.execute("CREATE TABLE k (a INT)");
            stageFile(engine, "@%k/h", 4);
            engine.execute("DROP TABLE z");
            engine.execute("DROP TABLE k");
            // A retention of 0 ends the version at once: the next look at the dropped versions forgets it.
            engine.executeQuery("SHOW TABLES HISTORY");
            assertEquals(List.of("D.PUBLIC.K"), setAside());
            engine.execute("UNDROP TABLE k");
            assertEquals("4", answer(engine, "SELECT $1 FROM @%k/h"));
            assertEquals(List.of(), setAside());
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void anEngineThatKeepsNothingAcrossARestartDeletesWhatItSetAsideWhenItShutsDown() throws IOException {
        final DatabaseEngine engine = engine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        stageFile(engine, "@%t/f", 1);
        engine.execute("DROP TABLE t");
        assertEquals(List.of("D.PUBLIC.T"), setAside());
        engine.shutdown();
        assertEquals(List.of(), setAside());
    }

    @Test
    public void anEngineShuttingDownLeavesWhatAnotherOneSetAside() throws IOException {
        final DatabaseEngine first = engine();
        final DatabaseEngine second = engine();
        try {
            first.execute("CREATE DATABASE d1");
            first.execute("CREATE TABLE d1.PUBLIC.t (a INT)");
            stageFile(first, "@d1.PUBLIC.%t/f", 1);
            first.execute("DROP TABLE d1.PUBLIC.t");
            second.execute("CREATE DATABASE d2");
            second.execute("CREATE TABLE d2.PUBLIC.t (a INT)");
            stageFile(second, "@d2.PUBLIC.%t/f", 2);
            second.execute("DROP TABLE d2.PUBLIC.t");
            second.shutdown();
            assertEquals(List.of("D1.PUBLIC.T"), setAside());
            first.execute("UNDROP TABLE d1.PUBLIC.t");
            assertEquals("1", answer(first, "SELECT $1 FROM @d1.PUBLIC.%t/f"));
        } finally {
            first.shutdown();
        }
    }

    @Test
    public void aJvmThatExitsWithoutShuttingTheEngineDownLeavesNothingBehind() throws IOException, InterruptedException {
        final String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=off", ExitWithoutShutdown.class.getName(), stages.toString())
            .redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        // The dropped table's stage was set aside while the engine ran, and nothing of it is left.
        assertEquals("D.PUBLIC.T", new String(Files.readAllBytes(stages.resolve("set-aside.txt")),
            StandardCharsets.UTF_8));
        assertEquals(List.of(), setAside());
        // Nor of the engine's own stage root, the live table stage's file included.
        assertEquals(List.of("set-aside.txt"), entries(stages));
    }
}
