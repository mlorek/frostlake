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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;

/**
 * The part of a statement the HTTP and the in-process drivers share, each call answered as the account's driver
 * answers it (live-verified):
 * <ul>
 *   <li>a closed statement refuses its calls with {@code Statement is closed.} (SQLSTATE 0A000, code 200024);</li>
 *   <li>the fetch size starts at 50 and keeps whatever it is given, the maximum row count and the query timeout
 *       keep a negative value too, and the widest field is 134217728;</li>
 *   <li>results are forward only, read only and closed at commit;</li>
 *   <li>a named cursor, a fetch direction other than forward, a maximum field size, pooling and closing on
 *       completion are features it does not have, refused with no message;</li>
 *   <li>an empty text is refused before it is sent: {@code Invalid SQL text: } (03000, 200021).</li>
 * </ul>
 */
public abstract class AbstractFrostlakeStatement implements Statement {

    /** The fetch size a statement starts with. */
    static final int DEFAULT_FETCH_SIZE = 50;

    /** The widest field a value holds, as the maximum field size answers it. */
    static final int MAX_FIELD_SIZE = 134217728;

    /** Whether the statement is closed. */
    protected boolean closed;

    /** The most rows a result hands out; 0 or less for all of them. */
    protected int maxRows;

    /** The query timeout in seconds, as set; 0 or less for none (see {@link #queryTimeout()}). */
    protected volatile int queryTimeoutSeconds;

    /** The fetch size, as set. */
    private int fetchSize = DEFAULT_FETCH_SIZE;

    /**
     * Refuses a call on a closed statement.
     *
     * @throws SQLException when the statement is closed
     */
    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed.", "0A000", 200024);
        }
    }

    /**
     * Refuses a text with nothing in it, as the account's driver does before it sends one.
     *
     * @param sql the text
     * @throws SQLException when the text is empty
     */
    protected static void requireSqlText(final String sql) throws SQLException {
        if (sql != null && sql.isEmpty()) {
            throw new SQLException("Invalid SQL text: " + sql, "03000", 200021);
        }
    }

    /**
     * The query timeout a statement runs under, in seconds: none for a timeout of zero or less.
     *
     * @return the timeout, 0 for none
     */
    protected int queryTimeout() {
        return Math.max(0, queryTimeoutSeconds);
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        checkClosed();
        return MAX_FIELD_SIZE;
    }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkClosed();
        return maxRows;
    }

    @Override
    public void setMaxRows(final int max) throws SQLException {
        checkClosed();
        this.maxRows = max;
    }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        checkClosed();
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        return queryTimeoutSeconds;
    }

    /** The statements this object runs are canceled once they run this long: "SQL execution canceled". */
    @Override
    public void setQueryTimeout(final int seconds) throws SQLException {
        checkClosed();
        queryTimeoutSeconds = seconds;
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
    public void setCursorName(final String name) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        if (direction != ResultSet.FETCH_FORWARD) {
            throw new SQLFeatureNotSupportedException();
        }
        checkClosed();
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        checkClosed();
        this.fetchSize = rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return fetchSize;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkClosed();
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        checkClosed();
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        checkClosed();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException {
        return getMoreResults();
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
