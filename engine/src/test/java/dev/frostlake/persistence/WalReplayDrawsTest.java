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
 * A statement replayed from the write-ahead log draws what it first drew: a UUID_STRING default, RANDOM, UNIFORM and
 * the rows SAMPLE picks come from a stream its recorded instant seeds, and an IDENTITY or a sequence hands each
 * statement the values it first handed it - so a transaction that committed after another session's insert keeps the
 * ids it drew, and the counter goes on past both.
 */
public class WalReplayDrawsTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_draws_");
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
    public void aReplayedStatementDrawsWhatItFirstDrew() {
        final String[] checks = {
            "SELECT id FROM d.PUBLIC.ev",
            "SELECT x FROM d.PUBLIC.r",
            "SELECT x FROM d.PUBLIC.u ORDER BY x",
            "SELECT a FROM d.PUBLIC.samp ORDER BY a",
            "SELECT i FROM d.PUBLIC.ids ORDER BY i",
            "SHOW SEQUENCES LIKE 'SQ' IN SCHEMA d.PUBLIC ->> SELECT \"next_value\" FROM $1",
            "SELECT customer, id FROM d.PUBLIC.orders ORDER BY customer",
        };
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            final SessionContext a = engine.createSession();
            final SessionContext b = engine.createSession();
            final String[][] script = {
                {"A", "CREATE DATABASE d"},
                {"A", "USE SCHEMA d.PUBLIC"},
                {"B", "USE SCHEMA d.PUBLIC"},
                {"A", "CREATE TABLE ev (id VARCHAR DEFAULT UUID_STRING(), payload VARCHAR)"},
                {"A", "INSERT INTO ev (payload) VALUES ('first')"},
                {"A", "CREATE TABLE r (x NUMBER(19, 0))"},
                {"A", "INSERT INTO r SELECT RANDOM()"},
                {"A", "CREATE TABLE u (x INT)"},
                {"A", "INSERT INTO u SELECT UNIFORM(1, 1000000, RANDOM()) FROM TABLE(GENERATOR(ROWCOUNT => 5))"},
                {"A", "CREATE TABLE src (a INT)"},
                {"A", "INSERT INTO src SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 100))"},
                {"A", "CREATE TABLE samp AS SELECT a FROM src SAMPLE (3 ROWS)"},
                {"A", "CREATE SEQUENCE sq"},
                {"A", "CREATE TABLE ids (i INT)"},
                {"A", "INSERT INTO ids SELECT sq.NEXTVAL FROM TABLE(GENERATOR(ROWCOUNT => 3))"},
                {"A", "CREATE TABLE orders (id INT IDENTITY, customer VARCHAR)"},
                {"A", "BEGIN"},
                {"A", "INSERT INTO orders (customer) VALUES ('alice')"},
                {"B", "INSERT INTO orders (customer) VALUES ('bob')"},
                {"A", "COMMIT"},
            };
            for (final String[] line : script) {
                final String answered = answer(engine, "A".equals(line[0]) ? a : b, line[1]);
                assertTrue(!answered.startsWith("REFUSED"), line[1] + " => " + answered);
            }
            before = answers(engine, checks);
        } finally {
            engine.shutdown();
        }
        assertTrue(before.contains("ORDER BY customer => alice, 1 | bob, 2\n"), before);
        final ConcurrentDatabaseEngine recovered = new ConcurrentDatabaseEngine(walConfig());
        try {
            assertEquals(before, answers(recovered, checks));
            final SessionContext next = recovered.createSession();
            answer(recovered, next, "INSERT INTO d.PUBLIC.orders (customer) VALUES ('carol')");
            assertEquals("3", answer(recovered, next, "SELECT id FROM d.PUBLIC.orders WHERE customer = 'carol'"));
            assertTrue(recovered.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(recovered.getEngine().getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }
}
