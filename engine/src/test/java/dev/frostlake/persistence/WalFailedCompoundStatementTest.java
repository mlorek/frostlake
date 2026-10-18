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
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CALL, a block or an EXECUTE IMMEDIATE that fails after its statements wrote leaves that work in place, as
 * Snowflake's procedures and blocks do: under autocommit each statement committed as it ended, and in an explicit
 * transaction the work commits with the transaction. The failed statement is never logged, so it owes a checkpoint,
 * and a restart keeps the rows, the table and the sequence values the failed statement left.
 */
public class WalFailedCompoundStatementTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_failed_compound_");
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

    private static void fails(final DatabaseEngine engine, final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    /** What the restarted engine answers for each check, its replay failing nowhere. */
    private String[] afterRestart(final String... checks) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            final String[] answers = new String[checks.length];
            for (int i = 0; i < checks.length; i++) {
                answers[i] = answer(recovered, checks[i]);
            }
            return answers;
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aFailedProcedureAndBlockKeepTheirWork() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final String made = "SELECT COUNT(*) FROM d.PUBLIC.made";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("""
                CREATE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
                BEGIN
                  INSERT INTO t VALUES (1);
                  CREATE TABLE made (k INT);
                  SELECT 1/0;
                  RETURN 'done';
                END;
                $$""");
            fails(engine, "CALL p()");
            fails(engine, "BEGIN INSERT INTO t VALUES (2); SELECT 1/0; END;");
            assertEquals("1 | 2", answer(engine, rows));
            assertEquals("0", answer(engine, made));
        } finally {
            engine.shutdown();
        }
        final String[] after = afterRestart(rows, made);
        assertEquals("1 | 2", after[0]);
        assertEquals("0", after[1]);
    }

    @Test
    public void aFailedExecuteImmediateInsideATransaction() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("BEGIN TRANSACTION");
            engine.execute("INSERT INTO t VALUES (3)");
            fails(engine, "EXECUTE IMMEDIATE 'BEGIN INSERT INTO t VALUES (4); SELECT 1/0; END;'");
            engine.execute("COMMIT");
            assertEquals("3 | 4", answer(engine, rows));
        } finally {
            engine.shutdown();
        }
        assertEquals("3 | 4", afterRestart(rows)[0]);
    }

    @Test
    public void aSequenceNeverHandsOutAValueTwice() {
        final String rows = "SELECT v FROM d.PUBLIC.t ORDER BY 1";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE t (v INT)");
            engine.execute("CREATE SEQUENCE sq");
            fails(engine, "BEGIN INSERT INTO t SELECT sq.NEXTVAL; SELECT 1/0; END;");
            assertEquals("1", answer(engine, rows));
        } finally {
            engine.shutdown();
        }
        final String[] after = afterRestart(rows, "SELECT d.PUBLIC.sq.NEXTVAL");
        assertEquals("1", after[0]);
        assertEquals("2", after[1]);
    }
}
