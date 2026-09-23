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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every session has its own number, and every surface that names a session names it the same way: CURRENT_SESSION()
 * answers a 16-digit number that differs between two sessions and holds for one session's life, and SHOW VARIABLES'
 * session_id, SHOW TRANSACTIONS' and SHOW LOCKS IN ACCOUNT's session and QUERY_HISTORY's SESSION_ID carry that
 * number for the session the row belongs to. Embedded, the sessions share one concurrent engine the way the HTTP
 * server's do; live, they are separate connections. Live-verified.
 */
public class SessionNumberTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext[] sessions;
    private Connection[] connections;
    private String database;

    @BeforeEach
    public void open() throws SQLException {
        database = "FL_SESSION_NUMBER_" + Long.toString(System.nanoTime(), 36).toUpperCase();
        if (LiveSnowflake.enabled()) {
            connections = new Connection[] {LiveSnowflake.open(), LiveSnowflake.open(), LiveSnowflake.open()};
        } else {
            engine = new ConcurrentDatabaseEngine();
            sessions = new SessionContext[] {engine.createSession(), engine.createSession(), engine.createSession()};
        }
        run(0, "CREATE OR REPLACE DATABASE " + database);
        for (int session = 0; session < 3; session++) {
            run(session, "USE DATABASE " + database);
        }
    }

    @AfterEach
    public void close() throws SQLException {
        try {
            run(0, "ROLLBACK");
            run(1, "ROLLBACK");
            run(2, "DROP DATABASE IF EXISTS " + database);
        } finally {
            if (connections != null) {
                for (final Connection connection : connections) {
                    connection.close();
                }
            }
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    private void run(final int session, final String sql) throws SQLException {
        if (connections != null) {
            try (Statement statement = connections[session].createStatement()) {
                statement.execute(sql);
            }
        } else {
            final ExecutionResult result = engine.execute(sql, sessions[session]);
            if (!result.isSuccess()) {
                throw new IllegalStateException(sql + ": " + result.getErrorMessage());
            }
        }
    }

    /** Every value of one column of a query's result, as text, in row order. */
    private List<String> column(final int session, final String sql, final String column) throws SQLException {
        final List<String> values = new ArrayList<>();
        if (connections != null) {
            try (Statement statement = connections[session].createStatement();
                 java.sql.ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    values.add(String.valueOf(rs.getString(column)));
                }
            }
            return values;
        }
        final ResultSet rs = engine.executeQuery(sql, sessions[session]);
        final int index = rs.getColumnIndex(column);
        for (final Row row : rs.getRows()) {
            values.add(String.valueOf(row.getValue(index)));
        }
        return values;
    }

    private String currentSession(final int session) throws SQLException {
        final List<String> values = column(session, "SELECT CURRENT_SESSION() AS s", "s");
        assertEquals(1, values.size());
        return values.get(0);
    }

    /** Three sessions, three different 16-digit numbers, each the same on every call. */
    @Test
    public void eachSessionHasItsOwnNumber() throws SQLException {
        final Set<String> seen = new HashSet<>();
        for (int session = 0; session < 3; session++) {
            final String number = currentSession(session);
            assertTrue(number.matches("[1-9][0-9]{15}"), "a 16-digit number: " + number);
            assertEquals(number, currentSession(session), "one session keeps its number");
            seen.add(number);
        }
        assertEquals(3, seen.size(), "every session has its own number: " + seen);
    }

    /** SHOW VARIABLES lists the session's own variables under its own number. */
    @Test
    public void showVariablesNamesTheSession() throws SQLException {
        run(0, "SET mine = 1");
        run(1, "SET yours = 2");
        assertEquals(List.of(currentSession(0)), column(0, "SHOW VARIABLES", "session_id"));
        assertEquals(List.of(currentSession(1)), column(1, "SHOW VARIABLES", "session_id"));
    }

    /**
     * A transaction is listed under the session that began it: two sessions' open transactions carry the two
     * sessions' numbers in SHOW TRANSACTIONS, and their locks carry them in SHOW LOCKS IN ACCOUNT.
     */
    @Test
    public void aTransactionAndItsLocksCarryTheirSession() throws SQLException {
        run(0, "CREATE TABLE ta (a INT)");
        run(0, "CREATE TABLE tb (b INT)");
        run(0, "INSERT INTO ta VALUES (1)");
        run(0, "INSERT INTO tb VALUES (1)");
        run(0, "BEGIN");
        run(0, "UPDATE ta SET a = 2");
        run(1, "BEGIN");
        run(1, "UPDATE tb SET b = 2");
        final String first = currentSession(0);
        final String second = currentSession(1);
        final List<String> transactions = column(2, "SHOW TRANSACTIONS", "session");
        assertTrue(transactions.contains(first) && transactions.contains(second),
            "both sessions' transactions, each under its own number: " + transactions + " vs " + first + ", " + second);
        final List<String> locks = column(2, "SHOW LOCKS IN ACCOUNT", "session");
        assertTrue(locks.contains(first) && locks.contains(second),
            "both sessions' locks, each under its own number: " + locks);
        assertNotEquals(first, currentSession(2));
    }

    /** QUERY_HISTORY records the number of the session that ran each statement, whichever session reads it. */
    @Test
    public void theQueryHistoryNamesTheSession() throws SQLException {
        final String marker = "SELECT '" + database + "' AS marker";
        run(1, marker);
        final List<String> recorded = column(2, "SELECT TO_VARCHAR(SESSION_ID) AS s"
            + " FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY(RESULT_LIMIT => 10000))"
            + " WHERE QUERY_TEXT = '" + marker.replace("'", "''") + "'", "s");
        assertEquals(List.of(currentSession(1)), recorded);
    }

    /**
     * Embedded only: the engine's own session and every JDBC connection a driver URL opens are sessions too, each
     * with its own number.
     */
    @Test
    public void aDirectConnectionIsASessionOfItsOwn() throws SQLException {
        if (LiveSnowflake.enabled()) {
            return;
        }
        final DatabaseEngine embedded = new DatabaseEngine();
        try (Connection one = new DirectConnection(embedded);
             Connection two = new DirectConnection(embedded)) {
            final String own = String.valueOf(embedded.executeQuery("SELECT CURRENT_SESSION() AS s")
                .getRows().get(0).getValue(0));
            final String first = directSession(one);
            final String second = directSession(two);
            assertEquals(3, new HashSet<>(List.of(own, first, second)).size(), own + " " + first + " " + second);
            assertEquals(first, directSession(one));
        } finally {
            embedded.shutdown();
        }
    }

    private static String directSession(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery("SELECT CURRENT_SESSION() AS s")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
