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
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link DirectConnection} over a <em>persistence-enabled</em> embedded engine survives a full engine
 * restart: data written through one {@code DatabaseEngine} instance and flushed on {@code shutdown()} is read
 * back by a brand-new instance opened on the SAME persistence directory — here the on-disk catalog snapshot,
 * not a JVM-cached engine, is the source of truth. This is the cross-restart counterpart to
 * {@link DirectJdbcUrlTest}, whose default-config engines are in-memory only (data survives close/reopen
 * within a JVM but not a real restart).
 *
 * <p>The test constructs {@code new DirectConnection(new DatabaseEngine(config))} directly rather than using
 * the {@code jdbc:frostlake:direct:<name>} URL, because that URL builds a default (persistence-off) engine.
 */
public class DirectJdbcPersistenceTest {

    private static final String DATA_DIR = "./target/test_data_direct_restart";
    private static final String CFG_DATA_DIR = "./target/test_data_direct_restart_cfg";
    private static final String CFG_PROPS_FILE = "./target/test_data_direct_restart.properties";

    private EngineConfig config;

    @BeforeEach
    public void setup() {
        cleanupArtifacts();
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, DATA_DIR);
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void teardown() {
        cleanupArtifacts();
    }

    private void cleanupArtifacts() {
        deleteRecursively(new File(DATA_DIR));
        deleteRecursively(new File(CFG_DATA_DIR));
        deleteRecursively(new File(CFG_PROPS_FILE));
    }

    @Test
    public void tableSurvivesEngineRestartOverDirectConnection() throws SQLException {
        // First "process": open a DirectConnection, create + populate a table, then shut the engine down.
        // shutdown() flushes the persistence snapshot to DATA_DIR (autoSave is off, so this is the only write).
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        try {
            final Connection c1 = new DirectConnection(engine1);
            final Statement s1 = c1.createStatement();
            s1.execute("CREATE DATABASE test_db");
            s1.execute("USE DATABASE test_db");
            s1.execute("USE SCHEMA PUBLIC");
            s1.execute("CREATE TABLE survivor (id INTEGER, name VARCHAR)");
            s1.execute("INSERT INTO survivor VALUES (1, 'alice'), (2, 'bob')");
            s1.close();
            c1.close();
            assertTrue(c1.isClosed(), "the first connection should report closed");
        } finally {
            engine1.shutdown();
        }

        // Second "process": a brand-new engine on the SAME directory reloads the snapshot from disk, and a
        // fresh DirectConnection reads the table back.
        final DatabaseEngine engine2 = new DatabaseEngine(config);
        try {
            final Connection c2 = new DirectConnection(engine2);
            assertFalse(c2.isClosed(), "the reopened connection should be open");
            final Statement s2 = c2.createStatement();
            s2.execute("USE DATABASE test_db");
            s2.execute("USE SCHEMA PUBLIC");
            try (ResultSet rs = s2.executeQuery("SELECT id, name FROM survivor ORDER BY id")) {
                assertTrue(rs.next(), "row 1 should survive the engine restart");
                assertEquals(1, rs.getInt(1));
                assertEquals("alice", rs.getString(2));
                assertTrue(rs.next(), "row 2 should survive the engine restart");
                assertEquals(2, rs.getInt(1));
                assertEquals("bob", rs.getString(2));
                assertFalse(rs.next(), "exactly the two inserted rows should remain");
            }
            s2.close();
            c2.close();
        } finally {
            engine2.shutdown();
        }
    }

    @Test
    public void tableSurvivesEngineRestartWithConfigFileEnabledPersistence() throws IOException, SQLException {
        // Persistence is enabled purely by a frostlake.properties file — the same route a jdbc:frostlake:direct
        // connection travels (its default engine loads frostlake.properties from the classpath / user home), but
        // sourced from an explicit path here via the EngineConfig(path) constructor so the suite classpath is
        // left untouched.
        writeConfigFile();

        final EngineConfig fileConfig = new EngineConfig(CFG_PROPS_FILE);
        assertTrue(fileConfig.isPersistenceEnabled(), "the config file should enable persistence");
        assertEquals(CFG_DATA_DIR, fileConfig.getPersistenceDirectory(), "the config file should set the directory");

        // First "process": write through a DirectConnection over the file-configured engine, flush on shutdown().
        final DatabaseEngine engine1 = new DatabaseEngine(fileConfig);
        try {
            final Connection c1 = new DirectConnection(engine1);
            final Statement s1 = c1.createStatement();
            s1.execute("CREATE DATABASE test_db");
            s1.execute("USE DATABASE test_db");
            s1.execute("USE SCHEMA PUBLIC");
            s1.execute("CREATE TABLE survivor (id INTEGER, name VARCHAR)");
            s1.execute("INSERT INTO survivor VALUES (1, 'alice'), (2, 'bob')");
            s1.close();
            c1.close();
        } finally {
            engine1.shutdown();
        }

        // Second "process": a fresh engine re-reading the SAME config file reloads the snapshot from disk.
        final DatabaseEngine engine2 = new DatabaseEngine(new EngineConfig(CFG_PROPS_FILE));
        try {
            final Connection c2 = new DirectConnection(engine2);
            final Statement s2 = c2.createStatement();
            s2.execute("USE DATABASE test_db");
            s2.execute("USE SCHEMA PUBLIC");
            try (ResultSet rs = s2.executeQuery("SELECT id, name FROM survivor ORDER BY id")) {
                assertTrue(rs.next(), "row 1 should survive the restart");
                assertEquals(1, rs.getInt(1));
                assertEquals("alice", rs.getString(2));
                assertTrue(rs.next(), "row 2 should survive the restart");
                assertEquals(2, rs.getInt(1));
                assertEquals("bob", rs.getString(2));
                assertFalse(rs.next(), "exactly the two inserted rows should remain");
            }
            s2.close();
            c2.close();
        } finally {
            engine2.shutdown();
        }
    }

    /** Write a minimal frostlake.properties enabling persistence into a temp path for EngineConfig(path) to load. */
    private static void writeConfigFile() throws IOException {
        final File file = new File(CFG_PROPS_FILE);
        final File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final Properties p = new Properties();
        p.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        p.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, CFG_DATA_DIR);
        p.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        try (OutputStream os = new FileOutputStream(file)) {
            p.store(os, "Frostlake persistence test config");
        }
    }

    /** Recursively delete a directory tree with plain File I/O (no streams/lambdas, per project style). */
    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        final File[] children = f.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
