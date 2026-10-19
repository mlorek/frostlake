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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A connection with autocommit off keeps its transaction open when a statement fails in it: the failed statement's own
 * changes are undone and the rows written before it commit with the transaction, as a session with AUTOCOMMIT off
 * keeps them on a live account.
 */
public class AutoCommitOffFailureTest {

    private DatabaseEngine engine;
    private Connection connection;

    @BeforeEach
    public void setUp() throws SQLException {
        engine = new DatabaseEngine();
        connection = new DirectConnection(engine);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE d");
            statement.execute("USE SCHEMA d.PUBLIC");
            statement.execute("CREATE TABLE tx (id INT)");
        }
    }

    @AfterEach
    public void tearDown() throws SQLException {
        connection.close();
        engine.shutdown();
    }

    private String ids() throws SQLException {
        final StringBuilder ids = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM tx ORDER BY id")) {
            while (rows.next()) {
                ids.append(ids.length() > 0 ? "," : "").append(rows.getInt(1));
            }
        }
        return ids.toString();
    }

    private void fails(final String sql) {
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }, sql);
    }

    @Test
    public void aFailedQueryLeavesTheTransactionOpen() throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tx VALUES (1)");
        }
        fails("SELECT * FROM nosuch");
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tx VALUES (2)");
        }
        connection.commit();
        assertEquals("1,2", ids());
    }

    @Test
    public void aFailedInsertAndARefusedDdlUndoOnlyThemselves() throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tx VALUES (3)");
        }
        fails("INSERT INTO tx VALUES ('x')");
        fails("DROP TABLE nosuch");
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tx VALUES (4)");
        }
        connection.rollback();
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tx VALUES (5)");
        }
        fails("CREATE VIEW vbad AS SELECT nosuch FROM tx");
        connection.commit();
        assertEquals("5", ids());
    }
}
