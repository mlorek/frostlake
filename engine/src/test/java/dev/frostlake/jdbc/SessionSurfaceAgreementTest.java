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

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The calls on a connection and on a statement that need no query answered, as the account's driver answers them
 * (live-verified) and both Frostlake drivers now do: the sentence, SQLSTATE and code a closed connection or
 * statement refuses its calls with, the features neither has, the result kinds and isolations it refuses, and the
 * values each starts with and keeps. Each table lists the calls in the order they run, each with its answer.
 */
public class SessionSurfaceAgreementTest extends BaseJdbcTest {

    /** The calls on an open connection, which keeps the state the earlier ones leave. */
    private static final String OPEN_CONNECTION = """
        createBlob => THROWS unsupported: null | null | 0
        createNClob => THROWS unsupported: null | null | 0
        createSQLXML => THROWS unsupported: null | null | 0
        createArrayOf => <object>
        getTypeMap => {}
        setTypeMap => THROWS unsupported: null | null | 0
        getHoldability => 2
        setHoldabilityClose => ok, then 2
        isReadOnly => false
        setReadOnlyTrue => ok, then false
        getTransactionIsolation => 0
        setIsolationSerializable => THROWS unsupported: Transaction Isolation 8 not supported. | 0A000 | 200035
        setIsolationReadCommitted => ok, then 2
        setIsolationNone => ok, then 0
        setCatalogTestDb => ok, then TEST_DB
        setSchemaTestSchema => ok, then TEST_SCHEMA
        getWarnings => null
        clearWarnings => ok
        isValid0 => true
        isValid5 => true
        isValidNeg => THROWS sql: timeout is less than 0 | null | 0
        nativeSQL => SELECT 1
        nativeSQLEscape => SELECT {fn NOW()}
        setSavepoint => THROWS unsupported: null | null | 0
        setSavepointNamed => THROWS unsupported: null | null | 0
        releaseSavepoint => THROWS unsupported: null | null | 0
        rollbackSavepoint => THROWS unsupported: null | null | 0
        isWrapperForConnection => true
        isWrapperForString => false
        unwrapConnection => true
        unwrapString => THROWS sql: <class> not unwrappable from java.lang.String | null | 0
        getClientInfo => {}
        getClientInfoName => null
        setClientInfoName => THROWS clientinfo: The client property cannot be set by setClientInfo. | 22023 | 200047
        setClientInfoProps => THROWS clientinfo: The client property cannot be set by setClientInfo. | 22023 | 200047
        getNetworkTimeout => 0
        setNetworkTimeout => ok, then 1000
        getAutoCommit => true
        setAutoCommitFalse => ok, then false
        commit => ok
        rollback => ok
        setAutoCommitTrue => ok, then true
        prepareCallSelect => <object>
        createStatementScroll => THROWS unsupported: ResultSet type 1004 is not supported. | 0A000 | 200035
        createStatementUpdatable => THROWS unsupported: ResultSet concurrency 1008 is not supported. | 0A000 | 200035
        createStatementHoldability => <object>
        """;

    /** The calls on a closed connection. */
    private static final String CLOSED_CONNECTION = """
        isClosed => true
        close => ok
        createStatement => THROWS sql: Connection has been closed. | 08003 | 200052
        prepareStatement => THROWS sql: Connection has been closed. | 08003 | 200052
        getHoldability => THROWS sql: Connection has been closed. | 08003 | 200052
        isReadOnly => THROWS sql: Connection has been closed. | 08003 | 200052
        getCatalog => THROWS sql: Connection has been closed. | 08003 | 200052
        getSchema => THROWS sql: Connection has been closed. | 08003 | 200052
        getAutoCommit => THROWS sql: Connection has been closed. | 08003 | 200052
        isValid0 => false
        getMetaData => THROWS sql: Connection has been closed. | 08003 | 200052
        nativeSQL => THROWS sql: Connection has been closed. | 08003 | 200052
        clearWarnings => THROWS sql: Connection has been closed. | 08003 | 200052
        getWarnings => THROWS sql: Connection has been closed. | 08003 | 200052
        setClientInfoName => THROWS clientinfo: The connection is not opened. | 08003 | 200052
        getClientInfo => THROWS sql: Connection has been closed. | 08003 | 200052
        getTransactionIsolation => THROWS sql: Connection has been closed. | 08003 | 200052
        commit => THROWS sql: Connection has been closed. | 08003 | 200052
        rollback => THROWS sql: Connection has been closed. | 08003 | 200052
        setAutoCommitFalse => THROWS sql: Connection has been closed. | 08003 | 200052
        getTypeMap => THROWS sql: Connection has been closed. | 08003 | 200052
        createBlob => THROWS unsupported: null | null | 0
        createArrayOf => <object>
        isWrapperForConnection => true
        getNetworkTimeout => THROWS sql: Connection has been closed. | 08003 | 200052
        setSchemaTestSchema => THROWS sql: Connection has been closed. | 08003 | 200052
        setCatalogTestDb => THROWS sql: Connection has been closed. | 08003 | 200052
        setReadOnlyTrue => THROWS sql: Connection has been closed. | 08003 | 200052
        setHoldabilityClose => THROWS sql: Connection has been closed. | 08003 | 200052
        setIsolationReadCommitted => THROWS sql: Connection has been closed. | 08003 | 200052
        """;

    /** The calls on an open statement, each on a statement of its own. */
    private static final String OPEN_STATEMENT = """
        getFetchDirection => 1000
        setFetchDirectionReverse => THROWS unsupported: null | null | 0
        getResultSetConcurrency => 1007
        getResultSetHoldability => 2
        getResultSetType => 1003
        setCursorName => THROWS unsupported: null | null | 0
        isWrapperForStatement => true
        unwrapStatement => true
        isWrapperForString => false
        cancelIdle => ok
        getMoreResultsFresh => false
        getUpdateCountFresh => -1
        getResultSetFresh => null
        executeQueryInsert => <object>
        executeUpdateSelect => THROWS sql: Statement 'SELECT 1' cannot be executed using current API. | 0A000 | 200042
        setQueryTimeoutNeg => ok, then -1
        setFetchSizeNeg => ok, then -1
        setMaxFieldSizeNeg => THROWS unsupported: null | null | 0
        getMaxFieldSize => 134217728
        setEscapeProcessingFalse => ok
        isPoolable => false
        setPoolableTrue => THROWS unsupported: null | null | 0
        closeOnCompletion => THROWS unsupported: null | null | 0
        getLargeUpdateCountFresh => -1
        executeLargeUpdateInsert => 1
        getLargeMaxRows => 0
        setLargeMaxRows5 => THROWS UnsupportedOperationException: setLargeMaxRows not implemented
        batchWithQuery => ok, [-2]
        executeQueryEmptyString => THROWS sql: Invalid SQL text:  | 03000 | 200021
        getFetchSizeDefault => 50
        getQueryTimeoutDefault => 0
        getMaxRowsDefault => 0
        setFetchSize100 => ok, then 100
        setMaxRowsNeg => ok, then -1
        setMaxRowsNegRead => ok, read 3
        setQueryTimeoutNegRun => ok, read 1
        setFetchSizeNegRead => ok, read 3
        executeQueryTwo => THROWS sql: Actual statement count 2 did not match the desired statement count 1. | 0A000 | 8
        """;

    /** The calls on a closed statement. */
    private static final String CLOSED_STATEMENT = """
        isClosed => true
        close => ok
        executeQuery => THROWS sql: Statement is closed. | 0A000 | 200024
        execute => THROWS sql: Statement is closed. | 0A000 | 200024
        executeUpdate => THROWS sql: Statement is closed. | 0A000 | 200024
        getFetchSize => THROWS sql: Statement is closed. | 0A000 | 200024
        setFetchSize => THROWS sql: Statement is closed. | 0A000 | 200024
        getMaxRows => THROWS sql: Statement is closed. | 0A000 | 200024
        setMaxRows => THROWS sql: Statement is closed. | 0A000 | 200024
        getQueryTimeout => THROWS sql: Statement is closed. | 0A000 | 200024
        setQueryTimeout => THROWS sql: Statement is closed. | 0A000 | 200024
        getWarnings => THROWS sql: Statement is closed. | 0A000 | 200024
        clearWarnings => THROWS sql: Statement is closed. | 0A000 | 200024
        getConnection => THROWS sql: Statement is closed. | 0A000 | 200024
        cancel => THROWS sql: Statement is closed. | 0A000 | 200024
        getMoreResults => THROWS sql: Statement is closed. | 0A000 | 200024
        getUpdateCount => THROWS sql: Statement is closed. | 0A000 | 200024
        getResultSet => THROWS sql: Statement is closed. | 0A000 | 200024
        addBatch => THROWS sql: Statement is closed. | 0A000 | 200024
        clearBatch => THROWS sql: Statement is closed. | 0A000 | 200024
        executeBatch => THROWS sql: Statement is closed. | 0A000 | 200024
        getResultSetType => THROWS sql: Statement is closed. | 0A000 | 200024
        getFetchDirection => THROWS sql: Statement is closed. | 0A000 | 200024
        isPoolable => THROWS sql: Statement is closed. | 0A000 | 200024
        isWrapperForStatement => true
        setCursorName => THROWS unsupported: null | null | 0
        getResultSetConcurrency => THROWS sql: Statement is closed. | 0A000 | 200024
        setEscapeProcessingFalse => THROWS sql: Statement is closed. | 0A000 | 200024
        closeOnCompletion => THROWS unsupported: null | null | 0
        """;

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE SCHEMA test_schema");
    }

    /** The call each line of a table names. */
    private static String call(final String line) {
        return line.substring(0, line.indexOf(" => "));
    }

    /** The table's calls made on one connection, each with its answer. */
    private static String connectionAnswers(final Connection c, final String table) {
        final StringBuilder out = new StringBuilder();
        for (final String line : table.split("\n")) {
            out.append(call(line)).append(" => ").append(DriverSessionSurface.connectionAnswer(c, call(line)))
                .append('\n');
        }
        return out.toString();
    }

    /** The table's calls made on the connection's statements: a fresh one for each, or one already closed. */
    private static String statementAnswers(final Connection c, final String table, final boolean closed)
            throws SQLException {
        final StringBuilder out = new StringBuilder();
        final Statement shut = c.createStatement();
        shut.close();
        for (final String line : table.split("\n")) {
            final String answer;
            if (closed) {
                answer = DriverSessionSurface.statementAnswer(shut, call(line));
            } else {
                try (Statement s = c.createStatement()) {
                    answer = DriverSessionSurface.statementAnswer(s, call(line));
                }
            }
            out.append(call(line)).append(" => ").append(answer).append('\n');
        }
        return out.toString();
    }

    /** Both connection tables over connections the opener makes. */
    private static void assertConnectionSurface(final Connection open, final Connection closed)
            throws SQLException {
        try {
            assertEquals(OPEN_CONNECTION, connectionAnswers(open, OPEN_CONNECTION));
        } finally {
            open.close();
        }
        closed.close();
        assertEquals(CLOSED_CONNECTION, connectionAnswers(closed, CLOSED_CONNECTION));
    }

    /** Both statement tables over the connection's statements, with the table the inserts write. */
    private static void assertStatementSurface(final Connection c) throws SQLException {
        try (Statement setup = c.createStatement()) {
            setup.execute("USE SCHEMA test_db.test_schema");
            setup.execute("CREATE OR REPLACE TEMPORARY TABLE surface_t (a INT)");
        }
        assertEquals(OPEN_STATEMENT, statementAnswers(c, OPEN_STATEMENT, false));
        assertEquals(CLOSED_STATEMENT, statementAnswers(c, CLOSED_STATEMENT, true));
    }

    private Connection fresh() throws SQLException {
        return isLiveSnowflake() ? LiveSnowflake.open() : new DirectConnection(sharedEngine);
    }

    @Test
    public void aConnectionAnswersAsTheAccountsDriver() throws SQLException {
        assertConnectionSurface(fresh(), fresh());
    }

    @Test
    public void aStatementAnswersAsTheAccountsDriver() throws SQLException {
        try (Connection c = fresh()) {
            assertStatementSurface(c);
        }
    }

    /** The HTTP driver answers each call as the in-process one does. */
    @Test
    public void anHttpConnectionAndItsStatementsAnswerAsTheAccountsDriver() throws IOException, SQLException {
        assumeFalse(isLiveSnowflake(), "the HTTP driver talks to an embedded server");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try {
            final String url = "jdbc:frostlake://localhost:" + port;
            try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE test_db");
                s.execute("CREATE SCHEMA test_db.test_schema");
            }
            assertConnectionSurface(DriverManager.getConnection(url), DriverManager.getConnection(url));
            try (Connection c = DriverManager.getConnection(url)) {
                assertStatementSurface(c);
            }
        } finally {
            server.stop();
        }
    }
}
