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
import dev.frostlake.DatabaseEngine;
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
 * A restart changes nothing committed: a statement that stored what CURRENT_SESSION, CURRENT_TRANSACTION,
 * LAST_TRANSACTION or LAST_QUERY_ID answered, or what a query-history listing held, stores it again when the
 * write-ahead log replays it, though the replay runs in a session of its own, with transactions and query IDs of its
 * own.
 */
public class WalReplayIdentityTest {
    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_identity_");
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

    /** A query's rows on one line each, as "a, b". */
    private static String rows(final Iterable<Row> rows) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : rows) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            out.append(cells).append('\n');
        }
        return out.toString();
    }

    /** What the engine recovering from the log answers, with no replay failing. */
    private String recovered(final String check) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            return rows(recovered.executeQuery(check).getRows());
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void storedIdentitiesComeBackAsCommitted() {
        final String check = "SELECT k, v FROM d.PUBLIC.n ORDER BY k";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE n (k VARCHAR, v VARCHAR)");
            engine.execute("INSERT INTO n SELECT 'uuid', UUID_STRING()");
            engine.execute("INSERT INTO n SELECT 'session', CURRENT_SESSION()::VARCHAR");
            engine.execute("INSERT INTO n SELECT 'lastq', LAST_QUERY_ID()");
            engine.execute("INSERT INTO n SELECT 'lastq2', LAST_QUERY_ID(-2)");
            engine.execute("INSERT INTO n SELECT 'firstq', LAST_QUERY_ID(1)");
            engine.execute("INSERT INTO n SELECT 'notxn', COALESCE(CURRENT_TRANSACTION(), 'none')");
            engine.execute("INSERT INTO n SELECT 'lasttxn-before', COALESCE(LAST_TRANSACTION(), 'none')");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO n SELECT 'txn', CURRENT_TRANSACTION()");
            engine.execute("INSERT INTO n SELECT 'txn-again', CURRENT_TRANSACTION()");
            engine.execute("COMMIT");
            engine.execute("INSERT INTO n SELECT 'lasttxn', LAST_TRANSACTION()");
            engine.execute("INSERT INTO n SELECT 'qh_sess', COUNT(*)::VARCHAR "
                + "FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())");
            engine.execute("INSERT INTO n SELECT 'qh', COUNT(*)::VARCHAR "
                + "FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())");
            engine.execute("INSERT INTO n SELECT 'row' || SEQ4(), LAST_QUERY_ID() "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 3))");
            before = rows(engine.executeQuery(check).getRows());
        } finally {
            engine.shutdown();
        }
        assertEquals(before, recovered(check));
    }

    @Test
    public void aProcedureStoresItsIdentitiesAgainOnReplay() {
        final String check = "SELECT k, v FROM d.PUBLIC.n ORDER BY k";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE n (k VARCHAR, v VARCHAR)");
            engine.execute("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS $$ BEGIN "
                + "INSERT INTO n SELECT 'proc-session', CURRENT_SESSION()::VARCHAR; "
                + "LET q VARCHAR := LAST_QUERY_ID(); "
                + "INSERT INTO n SELECT 'proc-lastq', :q; "
                + "INSERT INTO n SELECT 'proc-lastq-again', LAST_QUERY_ID(); "
                + "RETURN 1; END $$");
            engine.execute("CALL p()");
            before = rows(engine.executeQuery(check).getRows());
        } finally {
            engine.shutdown();
        }
        assertEquals(before, recovered(check));
    }

    @Test
    public void eachSessionStoresItsOwnIdentitiesAgainOnReplay() {
        final String check = "SELECT k, v FROM d.PUBLIC.n ORDER BY k";
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            final SessionContext first = engine.createSession();
            final SessionContext second = engine.createSession();
            engine.executeQuery("CREATE DATABASE d", first);
            engine.executeQuery("CREATE TABLE d.PUBLIC.n (k VARCHAR, v VARCHAR)", first);
            engine.executeQuery("INSERT INTO d.PUBLIC.n SELECT 'first', CURRENT_SESSION()::VARCHAR", first);
            engine.executeQuery("INSERT INTO d.PUBLIC.n SELECT 'second', CURRENT_SESSION()::VARCHAR", second);
            engine.executeQuery("INSERT INTO d.PUBLIC.n SELECT 'first-lastq', LAST_QUERY_ID()", first);
            engine.executeQuery("INSERT INTO d.PUBLIC.n SELECT 'second-lastq', LAST_QUERY_ID()", second);
            before = rows(engine.executeQuery(check, first).getRows());
        } finally {
            engine.shutdown();
        }
        assertEquals(before, recovered(check));
    }
}
