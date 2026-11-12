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

import dev.frostlake.jdbc.DatabaseDriver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unload consumes the streams its query reads ("a COPY INTO location transaction", documented), and a persistent
 * engine brings that back with the rest of the committed transaction: a replayed unload leaves its files as they are
 * and reads its query again, so the stream stays where the unload left it. The replay skipped the unload whole, and
 * after a reopen the stream listed every change the unload had already written out.
 */
public class UnloadStreamReplayTest {

    private Path dataDir;

    @BeforeEach
    public void setUp() throws Exception {
        Class.forName("dev.frostlake.jdbc.DatabaseDriver");
        dataDir = Files.createTempDirectory("frostlake_unload_replay_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        DatabaseDriver.closeFileEngine(dataDir.toString());
        deleteRecursively(dataDir.toFile());
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

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:frostlake:file:" + dataDir);
    }

    /** The rows a query answers, each one's cells joined by '|', the rows by " ;; ". */
    private static String answer(final Statement statement, final String sql) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    out.append(i == 1 ? "" : "|").append(rows.getString(i));
                }
            }
        }
        return out.toString();
    }

    private void prepare(final Statement statement) throws SQLException {
        statement.execute("CREATE DATABASE d1");
        statement.execute("USE SCHEMA d1.public");
        statement.execute("CREATE TABLE t (id INT)");
        statement.execute("CREATE STREAM s ON TABLE t");
        statement.execute("CREATE STAGE st");
        statement.execute("INSERT INTO t VALUES (1), (2)");
    }

    /** After a reopen: the stream lists nothing, the one file is there, and a later change is listed. */
    private void requireConsumedAfterReopen() throws Exception {
        DatabaseDriver.closeFileEngine(dataDir.toString());
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("USE SCHEMA d1.public");
            assertEquals("0", answer(statement, "SELECT COUNT(*) FROM s"));
            final String listed = answer(statement, "LIST @st");
            assertTrue(listed.startsWith("st/x_") && !listed.contains(" ;; "), listed);
            statement.execute("INSERT INTO t VALUES (3)");
            assertEquals("3", answer(statement, "SELECT id FROM s"));
        }
    }

    @Test
    public void aCommittedUnloadsStreamReadSurvivesAReopen() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            prepare(statement);
            statement.execute("BEGIN");
            statement.execute("COPY INTO @st/x FROM (SELECT id FROM s)");
            statement.execute("COMMIT");
            assertEquals("0", answer(statement, "SELECT COUNT(*) FROM s"));
        }
        requireConsumedAfterReopen();
    }

    @Test
    public void anAutocommitUnloadsStreamReadSurvivesAReopen() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            prepare(statement);
            statement.execute("COPY INTO @st/x FROM (SELECT id FROM s)");
            assertEquals("0", answer(statement, "SELECT COUNT(*) FROM s"));
        }
        requireConsumedAfterReopen();
    }

    @Test
    public void anUnloadThatAnswersItsRowsConsumesNothingAfterAReopenEither() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            prepare(statement);
            statement.execute("COPY INTO @st/x FROM (SELECT id FROM s) VALIDATION_MODE = RETURN_ROWS");
            assertEquals("2", answer(statement, "SELECT COUNT(*) FROM s"));
        }
        DatabaseDriver.closeFileEngine(dataDir.toString());
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("USE SCHEMA d1.public");
            assertEquals("2", answer(statement, "SELECT COUNT(*) FROM s"));
        }
    }
}
