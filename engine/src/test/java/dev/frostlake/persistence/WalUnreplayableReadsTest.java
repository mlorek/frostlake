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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A logged write that read what its record cannot read again keeps what it wrote across a restart. A COPY, a query of
 * a stage and an EXECUTE IMMEDIATE FROM read the stage's files as they were then, which an overwrite or a purge changes
 * after them. A function written in a handler language and not declared IMMUTABLE may answer otherwise when called
 * again. Each such write owes a checkpoint, so the restart does not run it again.
 */
public class WalUnreplayableReadsTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_unreplayable_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

    /** Remove a test's data directory whole: the engine keeps its stages in a directory inside it. */
    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or "REFUSED " and the refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
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

    /** Write a local file and put it on the stage, replacing what the stage holds under its name. */
    private Path put(final DatabaseEngine engine, final String name, final String content) throws IOException {
        final Path file = dir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        engine.execute("PUT 'file://" + file + "' @st AUTO_COMPRESS = FALSE OVERWRITE = TRUE");
        return file;
    }

    /** What the restarted engine answers for the check, its replay failing nowhere. */
    private String afterRestart(final String check) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            return answer(recovered, check);
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aLoadOverFilesOverwrittenOrPurgedAfterIt() throws IOException {
        final String check = "SELECT a FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE STAGE st");
            engine.execute("CREATE TABLE t (a INT)");
            put(engine, "a.csv", "1\n2\n");
            engine.execute("COPY INTO t FROM @st/a.csv");
            put(engine, "a.csv", "99\n");
            put(engine, "b.csv", "7\n");
            engine.execute("COPY INTO t FROM @st/b.csv PURGE = TRUE");
            engine.execute("INSERT INTO t SELECT $1 FROM @st/a.csv");
            put(engine, "a.csv", "5\n");
            assertEquals("1 | 2 | 7 | 99", answer(engine, check));
        } finally {
            engine.shutdown();
        }
        assertEquals("1 | 2 | 7 | 99", afterRestart(check));
    }

    @Test
    public void aScriptOverwrittenAfterItRan() throws IOException {
        final String check = "SELECT s FROM d.PUBLIC.t2 ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE STAGE st");
            engine.execute("CREATE TABLE t2 (s STRING)");
            put(engine, "script.sql", "INSERT INTO t2 VALUES ('script');\n");
            engine.execute("EXECUTE IMMEDIATE FROM @st/script.sql");
            put(engine, "script.sql", "INSERT INTO t2 VALUES ('other');\n");
            assertEquals("script", answer(engine, check));
        } finally {
            engine.shutdown();
        }
        assertEquals("script", afterRestart(check));
    }

    @Test
    public void aHandlerFunctionsAnswerIsKept() {
        final String check = "SELECT x FROM d.PUBLIC.n";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("""
                CREATE FUNCTION r() RETURNS DOUBLE LANGUAGE JAVA HANDLER = 'R.r'
                AS 'class R { public static double r() { return Math.random(); } }'""");
            engine.execute("CREATE TABLE n AS SELECT r() AS x");
            before = answer(engine, check);
        } finally {
            engine.shutdown();
        }
        assertEquals(before, afterRestart(check));
    }
}
