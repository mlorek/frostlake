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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.DatabaseDriver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table another session renames, replaces or drops while an open transaction holds it keeps one lock in SHOW LOCKS,
 * named by the table's name: the transaction's own record of the lock follows the table as its buffered changes do,
 * and a table set aside by a DROP or a replacement is named by the name it had, never by an internal storage key.
 * Each lock showed twice, once under the old name, which locked nothing, and once under the storage key. Unmeasured on
 * live; the class runs embedded, over two in-process connections.
 */
public class ShowLocksAfterMoveTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection holder;
    private Connection mover;

    @BeforeEach
    public void openTwoConnections() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "moves tables under an embedded engine's open transaction");
        engineSequence++;
        engineName = "show_locks_after_move_" + engineSequence;
        holder = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        mover = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        say(holder, "CREATE DATABASE IF NOT EXISTS slam");
        say(holder, "USE SCHEMA slam.public");
        say(mover, "USE SCHEMA slam.public");
        for (final String table : new String[] {"t", "r", "q"}) {
            say(holder, "CREATE OR REPLACE TABLE " + table + " (id INT)");
            say(holder, "INSERT INTO " + table + " VALUES (1)");
        }
    }

    @AfterEach
    public void closeTwoConnections() throws SQLException {
        if (engineName == null) {
            return;
        }
        holder.close();
        mover.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static void say(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** The resources SHOW LOCKS lists, in its order. */
    private static List<String> lockedResources(final Connection connection) throws SQLException {
        final List<String> resources = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW LOCKS")) {
            final ResultSetMetaData columns = rows.getMetaData();
            int resource = 1;
            for (int i = 1; i <= columns.getColumnCount(); i++) {
                if ("resource".equalsIgnoreCase(columns.getColumnLabel(i))) {
                    resource = i;
                }
            }
            while (rows.next()) {
                resources.add(rows.getString(resource));
            }
        }
        return resources;
    }

    @Test
    public void eachHeldLockShowsOnceUnderItsTablesName() throws SQLException {
        say(holder, "BEGIN");
        for (final String table : new String[] {"t", "r", "q"}) {
            say(holder, "UPDATE " + table + " SET id = 2");
        }
        say(mover, "ALTER TABLE t RENAME TO t_new");
        say(mover, "CREATE OR REPLACE TABLE r (id INT)");
        say(mover, "DROP TABLE q");
        final List<String> resources = lockedResources(mover);
        resources.sort(null);
        assertEquals(List.of("SLAM.PUBLIC.Q", "SLAM.PUBLIC.R", "SLAM.PUBLIC.T_NEW"), resources);
        say(holder, "ROLLBACK");
    }
}
