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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * The part of a connection the HTTP and the in-process drivers share, each call answered as the account's driver
 * answers it (live-verified):
 * <ul>
 *   <li>a closed connection refuses its calls with {@code Connection has been closed.} (SQLSTATE 08003, code
 *       200052);</li>
 *   <li>results are closed at commit, the connection is never read only, its type map is empty, and the network
 *       timeout keeps what it is given;</li>
 *   <li>the isolation is none until READ COMMITTED is set, and any other level is refused:
 *       {@code Transaction Isolation 8 not supported.} (0A000, 200035);</li>
 *   <li>a statement over a result that scrolls or updates is refused:
 *       {@code ResultSet type 1004 is not supported.}, {@code ResultSet concurrency 1008 is not supported.};</li>
 *   <li>savepoints, a type map of its own, BLOB, CLOB, NCLOB and SQLXML values are features it does not have,
 *       refused with no message, and a client property cannot be set:
 *       {@code The client property cannot be set by setClientInfo.} (22023, 200047).</li>
 * </ul>
 */
public abstract class AbstractFrostlakeConnection implements Connection {

    /** The SQLSTATE and code of a call on a closed connection. */
    private static final String CLOSED_STATE = "08003";
    private static final int CLOSED_CODE = 200052;

    /** The SQLSTATE and code of a result kind or an isolation the connection does not have. */
    private static final String UNSUPPORTED_STATE = "0A000";
    private static final int UNSUPPORTED_CODE = 200035;

    /** The isolation the connection reports: none until READ COMMITTED is set. */
    private int transactionIsolation = Connection.TRANSACTION_NONE;

    /** The network timeout the connection was given, in milliseconds. */
    private int networkTimeout;

    /**
     * Refuses a call on a closed connection.
     *
     * @throws SQLException when the connection is closed
     */
    protected void checkClosed() throws SQLException {
        if (isClosed()) {
            throw new SQLException("Connection has been closed.", CLOSED_STATE, CLOSED_CODE);
        }
    }

    /**
     * Refuses a negative timeout, as isValid does before it looks at the connection.
     *
     * @param timeout the timeout in seconds
     * @throws SQLException when the timeout is negative
     */
    protected static void requireValidTimeout(final int timeout) throws SQLException {
        if (timeout < 0) {
            throw new SQLException("timeout is less than 0");
        }
    }

    /** Refuses a result that scrolls or updates: the connection's results are forward only and read only. */
    private static void requireResultKind(final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        if (resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
            throw new SQLFeatureNotSupportedException("ResultSet type " + resultSetType + " is not supported.",
                UNSUPPORTED_STATE, UNSUPPORTED_CODE);
        }
        if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
            throw new SQLFeatureNotSupportedException("ResultSet concurrency " + resultSetConcurrency
                + " is not supported.", UNSUPPORTED_STATE, UNSUPPORTED_CODE);
        }
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return createStatement();
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
                                     final int resultSetHoldability) throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency) throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency, final int resultSetHoldability)
            throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return prepareCall(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
                                         final int resultSetHoldability) throws SQLException {
        requireResultKind(resultSetType, resultSetConcurrency);
        return prepareCall(sql);
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
        checkClosed();
        return sql;
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        checkClosed();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        checkClosed();
        if (level != Connection.TRANSACTION_NONE && level != Connection.TRANSACTION_READ_COMMITTED) {
            throw new SQLFeatureNotSupportedException("Transaction Isolation " + level + " not supported.",
                UNSUPPORTED_STATE, UNSUPPORTED_CODE);
        }
        transactionIsolation = level;
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return transactionIsolation;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkClosed();
        return new HashMap<>();
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        checkClosed();
    }

    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    /** An array of the given elements, which a closed connection still makes. */
    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        return new DirectArray(typeName, elements);
    }

    @Override
    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
        refuseClientInfo();
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
        refuseClientInfo();
    }

    /** A client property is never set; a closed connection says so first. */
    private void refuseClientInfo() throws SQLClientInfoException {
        boolean open;
        try {
            open = !isClosed();
        } catch (final SQLException unknown) {
            open = false;
        }
        throw open
            ? new SQLClientInfoException("The client property cannot be set by setClientInfo.", "22023", 200047,
                null)
            : new SQLClientInfoException("The connection is not opened.", CLOSED_STATE, CLOSED_CODE, null);
    }

    @Override
    public String getClientInfo(final String name) throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkClosed();
        return new Properties();
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
        checkClosed();
        networkTimeout = milliseconds;
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkClosed();
        return networkTimeout;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException(getClass().getName() + " not unwrappable from " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}
