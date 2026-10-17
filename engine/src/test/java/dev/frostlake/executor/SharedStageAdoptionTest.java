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

package dev.frostlake.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A data directory a former version wrote takes a copy of the shared stage root once, leaving out in-memory engines'
 * own directories; a new data directory takes nothing, and a marked one is never copied into again.
 */
public class SharedStageAdoptionTest {

    private Path shared;
    private Path data;

    @BeforeEach
    public void setUp() throws IOException {
        shared = Files.createTempDirectory("shared_stages_");
        data = Files.createTempDirectory("data_dir_");
        Files.createDirectories(shared.resolve("named/D.PUBLIC.ST"));
        Files.write(shared.resolve("named/D.PUBLIC.ST/a.csv"), "1".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(shared.resolve("engine-1234"));
        Files.write(shared.resolve("engine-1234/x.csv"), "2".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void tearDown() throws IOException {
        delete(shared);
        delete(data);
    }

    private static void delete(final Path root) throws IOException {
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

    @Test
    public void aFormerVersionsDataDirectoryTakesTheSharedStagesOnce() throws IOException {
        Files.write(data.resolve("wal.log"), "x".getBytes(StandardCharsets.UTF_8));
        SharedStageAdoption.prepare(data, shared);
        assertTrue(Files.isRegularFile(data.resolve("stages/named/D.PUBLIC.ST/a.csv")));
        assertFalse(Files.exists(data.resolve("stages/engine-1234")));
        Files.delete(data.resolve("stages/named/D.PUBLIC.ST/a.csv"));
        SharedStageAdoption.prepare(data, shared);
        assertFalse(Files.exists(data.resolve("stages/named/D.PUBLIC.ST/a.csv")));
    }

    @Test
    public void aNewDataDirectoryTakesNothing() throws IOException {
        SharedStageAdoption.prepare(data, shared);
        assertTrue(Files.isDirectory(data.resolve("stages")));
        assertFalse(Files.exists(data.resolve("stages/named")));
    }
}
