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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A crash during an append leaves a torn record at the end of the log, which recovery discards. Recovery also cuts
 * it off the file, so what the restarted engine commits follows the last whole record and is read back on every
 * later start: appended behind the torn bytes, it was read as the torn record's rest and lost, without an error,
 * at the next start.
 */
public class WalTornTailTest {

    private Path dir;
    private Path walFile;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_torn_");
        walFile = dir.resolve("wal.log");
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
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, walFile.toString());
        return cfg;
    }

    private static String values(final DatabaseEngine engine) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT n FROM w.PUBLIC.t ORDER BY n").getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    /** Cut the last bytes off the log, as a crash during its last append leaves it. */
    private void tear(final int bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(walFile, StandardOpenOption.WRITE)) {
            channel.truncate(channel.size() - bytes);
        }
    }

    @Test
    public void commitsAfterATornRecordSurviveEveryLaterStart() throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE w");
        first.execute("CREATE TABLE w.PUBLIC.t (n INT)");
        first.execute("INSERT INTO w.PUBLIC.t VALUES (1)");
        first.execute("INSERT INTO w.PUBLIC.t VALUES (2)");
        first.execute("INSERT INTO w.PUBLIC.t VALUES (3)");
        first.shutdown();
        tear(3);

        final DatabaseEngine second = new DatabaseEngine(walConfig());
        try {
            assertEquals("1,2", values(second), "the torn record's transaction is gone");
            second.execute("INSERT INTO w.PUBLIC.t VALUES (4)");
            assertEquals("1,2,4", values(second));
        } finally {
            second.shutdown();
        }

        final DatabaseEngine third = new DatabaseEngine(walConfig());
        try {
            assertEquals("1,2,4", values(third), "what the second engine committed is read back");
            third.execute("INSERT INTO w.PUBLIC.t VALUES (4)");
        } finally {
            third.shutdown();
        }

        final DatabaseEngine fourth = new DatabaseEngine(walConfig());
        try {
            assertEquals("1,2,4,4", values(fourth));
        } finally {
            fourth.shutdown();
        }
    }

    @Test
    public void readingTheLogCutsATornRecordOffTheFile() throws IOException {
        final WriteAheadLog log = new WriteAheadLog(walFile);
        try {
            log.appendTransaction(Collections.singletonList("SELECT 1"));
            final long whole = Files.size(walFile);
            log.appendTransaction(Collections.singletonList("SELECT 2"));
            tear(20);
            assertEquals(1, log.readAll().size());
            assertEquals(whole, Files.size(walFile), "the file ends where its last whole record ends");
            log.appendTransaction(Collections.singletonList("SELECT 3"));
            assertEquals(2, log.readAll().size());
            assertEquals("SELECT 3", log.readAll().get(1).getStatements().get(0));
        } finally {
            log.close();
        }
    }

    @Test
    public void aLogWhoseHeaderIsTornBeginsAgain() throws IOException {
        final WriteAheadLog first = new WriteAheadLog(walFile);
        first.close();
        tear(5);
        final WriteAheadLog log = new WriteAheadLog(walFile);
        try {
            assertEquals(0, log.readAll().size());
            log.appendTransaction(Collections.singletonList("SELECT 1"));
            assertEquals(1, log.readAll().size());
            assertEquals("SELECT 1", log.readAll().get(0).getStatements().get(0));
        } finally {
            log.close();
        }
    }
}
