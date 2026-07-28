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

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded-persistent {@code jdbc:frostlake:file:<dir>} URL: state written through one connection
 * survives closing the engine and re-opening the same directory (WAL replay by default,
 * {@code ?wal=false} snapshot persistence), same-directory connections share one engine, and the
 * {@code frostlake.lock} file rejects a directory already held by another process.
 */
public class FileUrlConnectionTest {

    private static final Logger logger = LoggerFactory.getLogger(FileUrlConnectionTest.class);

    private Path dataDir;

    @BeforeEach
    public void setUp() throws Exception {
        Class.forName("dev.frostlake.jdbc.DatabaseDriver");
        dataDir = Files.createTempDirectory("frostlake_file_url_test_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        DatabaseDriver.closeFileEngine(dataDir.toString());
        deleteRecursively(dataDir.toFile());
    }

    private String url(final String params) {
        return "jdbc:frostlake:file:" + dataDir + (params == null ? "" : params);
    }

    private void createAndFill(final String urlParams) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url(urlParams));
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE dev");
            stmt.execute("USE DATABASE dev");
            stmt.execute("CREATE SCHEMA app");
            stmt.execute("USE SCHEMA app");
            stmt.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
            stmt.execute("INSERT INTO t VALUES (1, 'one'), (2, 'two')");
        }
    }

    private long countRows(final String urlParams) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url(urlParams));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t")) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    @Test
    public void walModeStateSurvivesReopen() throws Exception {
        createAndFill(null);
        assertTrue(Files.exists(dataDir.resolve("wal.log")), "WAL mode should write wal.log in the data dir");

        assertNotNull(DatabaseDriver.closeFileEngine(dataDir.toString()), "the engine should have been open");
        logger.info("re-opening {} after WAL-mode close", dataDir);

        assertEquals(2L, countRows("?database=DEV&schema=APP"));
    }

    @Test
    public void snapshotModeStateSurvivesReopen() throws Exception {
        createAndFill("?wal=false");

        assertNotNull(DatabaseDriver.closeFileEngine(dataDir.toString()), "the engine should have been open");
        logger.info("re-opening {} after snapshot-mode close", dataDir);

        assertEquals(2L, countRows("?wal=false&database=DEV&schema=APP"));
    }

    @Test
    public void sameDirectorySharesOneEngine() throws Exception {
        try (Connection first = DriverManager.getConnection(url(null));
             Connection second = DriverManager.getConnection(url(null))) {
            final DatabaseEngine engineA = first.unwrap(DatabaseEngine.class);
            final DatabaseEngine engineB = second.unwrap(DatabaseEngine.class);
            assertSame(engineA, engineB, "connections to the same directory must share one engine");
        }
    }

    @Test
    public void lockedDirectoryIsRejectedWithAClearError() throws Exception {
        createAndFill(null);
        DatabaseDriver.closeFileEngine(dataDir.toString());

        final Path lock = dataDir.resolve("frostlake.lock");
        Files.writeString(lock, "pid=99999\n");     // simulate another process holding the directory
        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                DriverManager.getConnection(url(null));
            }
        });
        assertTrue(e.getMessage().contains("frostlake.lock"),
            "the error should name the lock file, got: " + e.getMessage());

        Files.deleteIfExists(lock);
        assertEquals(2L, countRows("?database=DEV&schema=APP"));
    }

    @Test
    public void venvParameterPinsPythonVenv() throws Exception {
        try (Connection conn = DriverManager.getConnection(url("?venv=/opt/graalpy%20packages/venv"))) {
            final DatabaseEngine engine = conn.unwrap(DatabaseEngine.class);
            assertEquals("/opt/graalpy packages/venv", engine.getConfig().getPythonVenv(),
                "the URL-encoded venv parameter should reach python.venv decoded");
        }
    }

    @Test
    public void closingAnUnopenedDirectoryReturnsNull() throws IOException {
        final Path other = Files.createTempDirectory("frostlake_file_url_unopened_");
        try {
            assertNull(DatabaseDriver.closeFileEngine(other.toString()));
        } finally {
            deleteRecursively(other.toFile());
        }
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
