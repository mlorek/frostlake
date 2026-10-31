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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * One call on a JDBC connection or statement, answered as a text two drivers can be compared by: a value as its
 * text and any other object as {@code <object>}, a refusal as its family (sql, unsupported, clientinfo or the
 * exception's own name) with its message, SQLSTATE and code, and the class a refusal to unwrap names as
 * {@code <class>}.
 */
final class DriverSessionSurface {

    /** A query of three rows. */
    private static final String THREE_ROWS = "SELECT column1 FROM VALUES (1), (2), (3)";

    private DriverSessionSurface() {
    }

    /** The number of rows the statement's query reads. */
    private static int rowsOf(final Statement s, final String sql) throws SQLException {
        int read = 0;
        try (ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                read++;
            }
        }
        return read;
    }

    private static String show(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().getName().startsWith("java.lang.") || value instanceof Map) {
            return String.valueOf(value);
        }
        return "<object>";
    }

    private static String failure(final Throwable thrown) {
        final String family = thrown instanceof SQLClientInfoException ? "clientinfo"
            : thrown instanceof SQLFeatureNotSupportedException ? "unsupported"
            : thrown instanceof SQLException ? "sql" : thrown.getClass().getSimpleName();
        String message = String.valueOf(thrown.getMessage()).replace("\n", " | ");
        final int unwrappable = message.indexOf(" not unwrappable from ");
        if (unwrappable > 0) {
            message = "<class>" + message.substring(unwrappable);
        }
        final StringBuilder out = new StringBuilder("THROWS ").append(family).append(": ").append(message);
        if (thrown instanceof SQLException) {
            out.append(" | ").append(((SQLException) thrown).getSQLState()).append(" | ")
                .append(((SQLException) thrown).getErrorCode());
        }
        return out.toString();
    }

    static String connectionAnswer(final Connection c, final String op) {
        try {
            switch (op) {
                case "createBlob": return show(c.createBlob());
                case "createNClob": return show(c.createNClob());
                case "createSQLXML": return show(c.createSQLXML());
                case "createArrayOf": return show(c.createArrayOf("INTEGER", new Object[] {1, 2}));
                case "getTypeMap": return show(c.getTypeMap());
                case "setTypeMap":
                    c.setTypeMap(new HashMap<String, Class<?>>());
                    return "ok";
                case "getHoldability": return show(c.getHoldability());
                case "setHoldabilityClose":
                    c.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
                    return "ok, then " + c.getHoldability();
                case "isReadOnly": return show(c.isReadOnly());
                case "setReadOnlyTrue":
                    c.setReadOnly(true);
                    return "ok, then " + c.isReadOnly();
                case "getTransactionIsolation": return show(c.getTransactionIsolation());
                case "setIsolationSerializable":
                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    return "ok, then " + c.getTransactionIsolation();
                case "setIsolationReadCommitted":
                    c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    return "ok, then " + c.getTransactionIsolation();
                case "setIsolationNone":
                    c.setTransactionIsolation(Connection.TRANSACTION_NONE);
                    return "ok, then " + c.getTransactionIsolation();
                case "getCatalog": return show(c.getCatalog());
                case "getSchema": return show(c.getSchema());
                case "setCatalogTestDb":
                    c.setCatalog("TEST_DB");
                    return "ok, then " + c.getCatalog();
                case "setSchemaTestSchema":
                    c.setSchema("TEST_SCHEMA");
                    return "ok, then " + c.getSchema();
                case "getWarnings": return show(c.getWarnings());
                case "clearWarnings":
                    c.clearWarnings();
                    return "ok";
                case "isValid0": return show(c.isValid(0));
                case "isValid5": return show(c.isValid(5));
                case "isValidNeg": return show(c.isValid(-1));
                case "nativeSQL": return show(c.nativeSQL("SELECT 1"));
                case "nativeSQLEscape": return show(c.nativeSQL("SELECT {fn NOW()}"));
                case "setSavepoint": return show(c.setSavepoint());
                case "setSavepointNamed": return show(c.setSavepoint("sp"));
                case "releaseSavepoint":
                    c.releaseSavepoint(null);
                    return "ok";
                case "rollbackSavepoint":
                    c.rollback(null);
                    return "ok";
                case "isWrapperForConnection": return show(c.isWrapperFor(Connection.class));
                case "isWrapperForString": return show(c.isWrapperFor(String.class));
                case "unwrapConnection": return show(c.unwrap(Connection.class) == c);
                case "unwrapString": return show(c.unwrap(String.class));
                case "getClientInfo": return show(c.getClientInfo());
                case "getClientInfoName": return show(c.getClientInfo("ApplicationName"));
                case "setClientInfoName":
                    c.setClientInfo("ApplicationName", "surface");
                    return "ok, then " + c.getClientInfo("ApplicationName");
                case "setClientInfoProps": {
                    final Properties properties = new Properties();
                    properties.setProperty("ApplicationName", "surface b");
                    c.setClientInfo(properties);
                    return "ok, then " + c.getClientInfo("ApplicationName");
                }
                case "getNetworkTimeout": return show(c.getNetworkTimeout());
                case "setNetworkTimeout":
                    c.setNetworkTimeout(null, 1000);
                    return "ok, then " + c.getNetworkTimeout();
                case "getAutoCommit": return show(c.getAutoCommit());
                case "setAutoCommitFalse":
                    c.setAutoCommit(false);
                    return "ok, then " + c.getAutoCommit();
                case "commit":
                    c.commit();
                    return "ok";
                case "rollback":
                    c.rollback();
                    return "ok";
                case "setAutoCommitTrue":
                    c.setAutoCommit(true);
                    return "ok, then " + c.getAutoCommit();
                case "prepareCallSelect": return show(c.prepareCall("SELECT 1"));
                case "createStatementScroll":
                    return show(c.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
                case "createStatementUpdatable":
                    return show(c.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
                case "createStatementHoldability":
                    return show(c.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
                        ResultSet.CLOSE_CURSORS_AT_COMMIT));
                case "isClosed": return show(c.isClosed());
                case "close":
                    c.close();
                    return "ok";
                case "createStatement": return show(c.createStatement());
                case "prepareStatement": return show(c.prepareStatement("SELECT 1"));
                case "getMetaData": return show(c.getMetaData());
                default: return "?";
            }
        } catch (final Throwable thrown) {
            return failure(thrown);
        }
    }

    static String statementAnswer(final Statement s, final String op) {
        try {
            switch (op) {
                case "getFetchDirection": return show(s.getFetchDirection());
                case "setFetchDirectionReverse":
                    s.setFetchDirection(ResultSet.FETCH_REVERSE);
                    return "ok, then " + s.getFetchDirection();
                case "getResultSetConcurrency": return show(s.getResultSetConcurrency());
                case "getResultSetHoldability": return show(s.getResultSetHoldability());
                case "getResultSetType": return show(s.getResultSetType());
                case "setCursorName":
                    s.setCursorName("surface");
                    return "ok";
                case "isWrapperForStatement": return show(s.isWrapperFor(Statement.class));
                case "unwrapStatement": return show(s.unwrap(Statement.class) == s);
                case "isWrapperForString": return show(s.isWrapperFor(String.class));
                case "cancelIdle":
                    s.cancel();
                    return "ok";
                case "getMoreResultsFresh": return show(s.getMoreResults());
                case "getUpdateCountFresh": return show(s.getUpdateCount());
                case "getResultSetFresh": return show(s.getResultSet());
                case "executeQueryInsert": return show(s.executeQuery("INSERT INTO surface_t VALUES (2)"));
                case "executeUpdateSelect": return show(s.executeUpdate("SELECT 1"));
                case "setQueryTimeoutNeg":
                    s.setQueryTimeout(-1);
                    return "ok, then " + s.getQueryTimeout();
                case "setFetchSizeNeg":
                    s.setFetchSize(-1);
                    return "ok, then " + s.getFetchSize();
                case "setMaxFieldSizeNeg":
                    s.setMaxFieldSize(-1);
                    return "ok, then " + s.getMaxFieldSize();
                case "getMaxFieldSize": return show(s.getMaxFieldSize());
                case "setEscapeProcessingFalse":
                    s.setEscapeProcessing(false);
                    return "ok";
                case "isPoolable": return show(s.isPoolable());
                case "setPoolableTrue":
                    s.setPoolable(true);
                    return "ok, then " + s.isPoolable();
                case "closeOnCompletion":
                    s.closeOnCompletion();
                    return "ok, then " + s.isCloseOnCompletion();
                case "getLargeUpdateCountFresh": return show(s.getLargeUpdateCount());
                case "executeLargeUpdateInsert": return show(s.executeLargeUpdate("INSERT INTO surface_t VALUES (3)"));
                case "getLargeMaxRows": return show(s.getLargeMaxRows());
                case "setLargeMaxRows5":
                    s.setLargeMaxRows(5);
                    return "ok, then " + s.getLargeMaxRows();
                case "batchWithQuery": {
                    s.addBatch("SELECT 1");
                    final int[] counts = s.executeBatch();
                    return "ok, " + Arrays.toString(counts);
                }
                case "executeQueryEmptyString": return show(s.executeQuery(""));
                case "getFetchSizeDefault": return show(s.getFetchSize());
                case "getQueryTimeoutDefault": return show(s.getQueryTimeout());
                case "getMaxRowsDefault": return show(s.getMaxRows());
                case "setFetchSize100":
                    s.setFetchSize(100);
                    return "ok, then " + s.getFetchSize();
                case "setMaxRowsNeg":
                    s.setMaxRows(-1);
                    return "ok, then " + s.getMaxRows();
                case "setMaxRowsNegRead":
                    s.setMaxRows(-1);
                    return "ok, read " + rowsOf(s, THREE_ROWS);
                case "setQueryTimeoutNegRun":
                    s.setQueryTimeout(-1);
                    return "ok, read " + rowsOf(s, "SELECT 1");
                case "setFetchSizeNegRead":
                    s.setFetchSize(-1);
                    return "ok, read " + rowsOf(s, THREE_ROWS);
                case "executeQueryTwo": return show(s.executeQuery("SELECT 1; SELECT 2"));
                case "isClosed": return show(s.isClosed());
                case "close":
                    s.close();
                    return "ok";
                case "executeQuery": return show(s.executeQuery("SELECT 1"));
                case "execute": return show(s.execute("SELECT 1"));
                case "executeUpdate": return show(s.executeUpdate("INSERT INTO surface_t VALUES (9)"));
                case "getFetchSize": return show(s.getFetchSize());
                case "setFetchSize":
                    s.setFetchSize(1);
                    return "ok";
                case "getMaxRows": return show(s.getMaxRows());
                case "setMaxRows":
                    s.setMaxRows(1);
                    return "ok";
                case "getQueryTimeout": return show(s.getQueryTimeout());
                case "setQueryTimeout":
                    s.setQueryTimeout(1);
                    return "ok";
                case "getWarnings": return show(s.getWarnings());
                case "clearWarnings":
                    s.clearWarnings();
                    return "ok";
                case "getConnection": return show(s.getConnection() != null);
                case "cancel":
                    s.cancel();
                    return "ok";
                case "getMoreResults": return show(s.getMoreResults());
                case "getUpdateCount": return show(s.getUpdateCount());
                case "getResultSet": return show(s.getResultSet());
                case "addBatch":
                    s.addBatch("SELECT 1");
                    return "ok";
                case "clearBatch":
                    s.clearBatch();
                    return "ok";
                case "executeBatch": return "ok, " + Arrays.toString(s.executeBatch());
                default: return "?";
            }
        } catch (final Throwable thrown) {
            return failure(thrown);
        }
    }
}
