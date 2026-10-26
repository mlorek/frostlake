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
import dev.frostlake.jdbc.DatabaseDriver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An engine configured for durability that cannot open its write-ahead log refuses to start, naming the log, rather
 * than run on without one, so that nothing it committed would survive a restart; a {@code jdbc:frostlake:file:}
 * connection to such a directory fails to connect and leaves the directory free for the next.
 */
public class WalOpenFailureTest {

    private final List<Path> dirs = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        for (final Path dir : dirs) {
            deleteRecursively(dir.toFile());
        }
    }

    private Path tempDir() throws Exception {
        final Path dir = Files.createTempDirectory("wal_open_failure_");
        dirs.add(dir);
        return dir;
    }

    @Test
    public void anEngineWhoseLogCannotBeOpenedRefusesToStart() throws Exception {
        final Path dir = tempDir();
        // A log path under a regular file cannot be created.
        final Path file = Files.writeString(dir.resolve("not-a-directory"), "x");
        final Properties cfg = new Properties();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, file.resolve("wal.log").toString());
        final IllegalStateException refused = assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                new DatabaseEngine(new EngineConfig(cfg)).shutdown();
            }
        });
        assertTrue(refused.getMessage().startsWith("Cannot open the write-ahead log at "
            + file.resolve("wal.log") + ": "), refused.getMessage());
    }

    @Test
    public void aFileConnectionWhoseLogCannotBeOpenedFailsAndFreesTheDirectory() throws Exception {
        final Path dir = tempDir();
        // The directory's own stages come first: a data directory that holds anything else and no stages is one a
        // former version wrote, and its first start copies the shared stage root into it (SharedStageAdoption).
        final Path stages = Files.createDirectory(dir.resolve("stages"));
        // The log's own path is a directory, which the log cannot be opened as.
        final Path log = Files.createDirectory(dir.resolve("wal.log"));
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                DriverManager.getConnection("jdbc:frostlake:file:" + dir).close();
            }
        });
        assertTrue(refused.getMessage().contains("Cannot open the write-ahead log at " + log), refused.getMessage());
        try (DirectoryStream<Path> adopted = Files.newDirectoryStream(stages)) {
            assertTrue(!adopted.iterator().hasNext(), "no stage was copied into the directory");
        }
        Files.delete(log);
        try (Connection connection = DriverManager.getConnection("jdbc:frostlake:file:" + dir);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE wof");
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.DATABASES"
                + " WHERE DATABASE_NAME = 'WOF'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        } finally {
            DatabaseDriver.closeFileEngine(dir.toString());
        }
        assertTrue(Files.isRegularFile(log), "the second connection opened its log");
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
}
