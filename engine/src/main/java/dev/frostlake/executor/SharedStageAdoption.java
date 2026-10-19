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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The one-time move to a data directory's own stages. An engine whose data outlives it keeps its internal stages in
 * {@code stages} inside its data directory; an earlier version kept every data directory's in the one shared root,
 * named only by the stages' and tables' names, so which of them were a directory's own cannot be told. A data
 * directory a former version wrote takes a copy of everything the shared root holds — what it saw before — once, and
 * from then on its stages are its own. An in-memory engine's own directory there ({@code engine-<id>}) is never one
 * of them.
 */
final class SharedStageAdoption {

    private SharedStageAdoption() {
    }

    /**
     * Prepare a data directory's own stages: one a former version wrote — data there, and no {@code stages} of its
     * own — takes a copy of the shared root's stages; then its {@code stages} exists, so no later start copies again.
     *
     * @param data   the data directory
     * @param shared the shared root
     * @throws IOException when the directory cannot be read or written
     */
    static void prepare(final Path data, final Path shared) throws IOException {
        final Path own = data.resolve("stages");
        if (Files.isDirectory(own)) {
            return;
        }
        if (holdsFormerRun(data)) {
            adopt(shared, own);
        }
        Files.createDirectories(own);
    }

    /**
     * Whether a data directory holds a former run's data: anything but its lock and its own stages.
     *
     * @param data the data directory
     * @return whether a former run left data there
     * @throws IOException when the directory cannot be listed
     */
    static boolean holdsFormerRun(final Path data) throws IOException {
        if (!Files.isDirectory(data)) {
            return false;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(data)) {
            for (final Path entry : entries) {
                final String name = entry.getFileName().toString();
                if (!"stages".equals(name) && !"frostlake.lock".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Copy what the shared root holds into a data directory's own stage root.
     *
     * @param shared the shared root
     * @param own    the data directory's stage root, which does not exist yet
     * @throws IOException when a file cannot be copied
     */
    static void adopt(final Path shared, final Path own) throws IOException {
        if (!Files.isDirectory(shared)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(shared)) {
            for (final Path entry : entries) {
                if (!entry.getFileName().toString().startsWith("engine-")) {
                    copyTree(entry, own.resolve(entry.getFileName().toString()));
                }
            }
        }
    }

    private static void copyTree(final Path from, final Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
