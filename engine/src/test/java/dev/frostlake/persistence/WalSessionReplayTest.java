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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
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
 * An engine recovering from the write-ahead log replays each session in a session of its own: one session's USE,
 * SET, ALTER SESSION and USE ROLE reach that session's later statements and no other's, so a row lands in the table
 * its statement named, a variable and a time zone read as their session set them, and a table is owned by the role
 * that created it.
 */
public class WalSessionReplayTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_sessions_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        final File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (final File file : files) {
                Files.deleteIfExists(file.toPath());
            }
        }
        Files.deleteIfExists(dir);
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or the refusal. */
    private static String answer(final ConcurrentDatabaseEngine engine, final SessionContext session,
                                 final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql, session).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    private static String answers(final ConcurrentDatabaseEngine engine, final String[] checks) {
        final SessionContext reader = engine.createSession();
        final StringBuilder out = new StringBuilder();
        for (final String check : checks) {
            out.append(check).append(" => ").append(answer(engine, reader, check)).append('\n');
        }
        return out.toString();
    }

    @Test
    public void eachSessionReplaysInItsOwnContext() {
        final String[] checks = {
            "SELECT a FROM d.s2.t",
            "SELECT COUNT(*) FROM d.PUBLIC.t",
            "SELECT v FROM d.PUBLIC.vt",
            "SELECT s FROM d.PUBLIC.tz",
            "SHOW GRANTS ON TABLE d.PUBLIC.secret ->> SELECT \"privilege\", \"grantee_name\" FROM $1",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            final SessionContext a = engine.createSession();
            final SessionContext b = engine.createSession();
            final String[][] script = {
                {"A", "CREATE DATABASE d"},
                {"A", "CREATE SCHEMA d.s2"},
                {"A", "CREATE TABLE d.s2.t (a INT)"},
                {"A", "CREATE TABLE d.PUBLIC.t (a INT)"},
                {"A", "USE SCHEMA d.s2"},
                {"A", "SET v = 'set by A'"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"B", "SET v = 'set by B'"},
                {"B", "ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'"},
                {"B", "USE ROLE ACCOUNTADMIN"},
                {"A", "INSERT INTO t VALUES (1)"},
                {"A", "CREATE TABLE d.PUBLIC.secret (card VARCHAR)"},
                {"A", "CREATE TABLE d.PUBLIC.vt AS SELECT $v AS v"},
                {"A", "CREATE TABLE d.PUBLIC.tz AS SELECT TO_VARCHAR('2024-01-01 00:00:00 +00:00'::TIMESTAMP_TZ"
                    + "::TIMESTAMP_LTZ) AS s"},
            };
            for (final String[] line : script) {
                final String answered = answer(engine, "A".equals(line[0]) ? a : b, line[1]);
                assertTrue(!answered.startsWith("REFUSED"), line[1] + " => " + answered);
            }
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("SELECT a FROM d.s2.t => 1\n"), before);
        assertTrue(before.contains("SELECT COUNT(*) FROM d.PUBLIC.t => 0\n"), before);
        assertTrue(before.contains("SELECT v FROM d.PUBLIC.vt => set by A\n"), before);
        final ConcurrentDatabaseEngine recovered = new ConcurrentDatabaseEngine(walConfig());
        try {
            assertEquals(before, answers(recovered, checks));
            assertTrue(recovered.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(recovered.getEngine().getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }
}
