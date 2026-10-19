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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each data directory keeps its own stages: an engine with a write-ahead log and no stage root configured keeps its
 * internal stages in {@code stages} inside its data directory, so two data directories whose stages share a name
 * neither see nor erase each other's files, and the engine started on a directory again finds its own.
 */
public class DataDirectoryStageRootTest {

    private Path first;
    private Path second;

    @BeforeEach
    public void setUp() throws IOException {
        first = Files.createTempDirectory("stage_root_a_");
        second = Files.createTempDirectory("stage_root_b_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        delete(first);
        delete(second);
    }

    private static void delete(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException failed) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static EngineConfig walConfig(final Path dir) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    private static DatabaseEngine withStage(final Path dir) {
        final DatabaseEngine engine = new DatabaseEngine(walConfig(dir));
        engine.execute("CREATE DATABASE IF NOT EXISTS d");
        engine.execute("USE SCHEMA d.public");
        engine.execute("CREATE STAGE IF NOT EXISTS st");
        return engine;
    }

    @Test
    public void twoDataDirectoriesKeepTheirOwnStages() {
        final DatabaseEngine a = withStage(first);
        final DatabaseEngine b = withStage(second);
        try {
            a.execute("COPY INTO @st/a FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
            b.execute("CREATE OR REPLACE STAGE st");
            assertEquals(1, a.executeQuery("LIST @st").getRowCount());
            assertEquals(0, b.executeQuery("LIST @st").getRowCount());
        } finally {
            a.shutdown();
            b.shutdown();
        }
        final DatabaseEngine again = withStage(first);
        try {
            assertEquals(1, again.executeQuery("LIST @st").getRowCount());
        } finally {
            again.shutdown();
        }
    }
}
