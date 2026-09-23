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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.BatchUpdateException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC PreparedStatement implementation for Frostlake SQL Engine
 */
public class DatabasePreparedStatement extends DatabaseStatement implements PreparedStatement {
    private final String originalSql;
    private final Map<Integer, Object> parameters;
    private final List<Map<Integer, Object>> batchParameters = new ArrayList<>();
    private final Map<Integer, String> batchBindTypes = new HashMap<>();

    public DatabasePreparedStatement(final DatabaseConnection connection, final HttpClient httpClient, final String sql) {
        super(connection, httpClient);
        this.originalSql = sql;
        this.parameters = new HashMap<>();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return super.executeQuery(buildSql());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return super.executeUpdate(buildSql());
    }

    @Override
    public void setNull(final int parameterIndex, final int sqlType) throws SQLException {
        parameters.put(parameterIndex, null);
    }

    @Override
    public void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setByte(final int parameterIndex, final byte x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setShort(final int parameterIndex, final short x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setInt(final int parameterIndex, final int x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setLong(final int parameterIndex, final long x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setFloat(final int parameterIndex, final float x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDouble(final int parameterIndex, final double x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setString(final int parameterIndex, final String x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setDate(final int parameterIndex, final Date x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTime(final int parameterIndex, final Time x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    public void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    public void clearParameters() throws SQLException {
        parameters.clear();
    }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setObject(final int parameterIndex, final Object x) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        return super.execute(buildSql());
    }

    @Override
    public void addBatch() throws SQLException {
        // Live refuses a row whose bind types differ from the earlier rows' (array binding).
        JdbcBindTypes.validateBatchRow(batchBindTypes, parameters, batchParameters.size() + 1);
        batchParameters.add(new HashMap<>(parameters));
    }

    @Override
    public void clearBatch() throws SQLException {
        super.clearBatch();
        batchParameters.clear();
        batchBindTypes.clear();
    }

    /**
     * Live executes a prepared batch as one array-bound statement: a failure surfaces as that
     * statement's own {@link SQLException} — never a {@link BatchUpdateException} — and the batch
     * applies nothing. Frostlake executes the rows one by one, so rows before the failing one
     * stay applied; the exception surface matches live, and the remaining rows are not attempted.
     */
    @Override
    public int[] executeBatch() throws SQLException {
        final int[] results = new int[batchParameters.size()];
        long total = 0;
        try {
            for (int i = 0; i < batchParameters.size(); i++) {
                final Map<Integer, Object> saved = new HashMap<>(parameters);
                parameters.clear();
                parameters.putAll(batchParameters.get(i));
                try {
                    // Live Snowflake reports the real affected-row count per batch entry, not
                    // SUCCESS_NO_INFO.
                    results[i] = executeUpdate();
                    total += results[i];
                } finally {
                    parameters.clear();
                    parameters.putAll(saved);
                }
            }
        } finally {
            batchParameters.clear();
            batchBindTypes.clear();
        }
        // The driver runs the batch as ONE array-bound statement, so what getUpdateCount() reports after it is
        // the whole batch's count (live-verified: two one-row entries leave 2).
        takeBatchCount(total);
        return results;
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final int length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setRef(final int parameterIndex, final Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Ref not supported");
    }

    @Override
    public void setBlob(final int parameterIndex, final Blob x) throws SQLException {
        parameters.put(parameterIndex, x == null ? null : x.getBytes(1, (int) x.length()));
    }

    @Override
    public void setClob(final int parameterIndex, final Clob x) throws SQLException {
        parameters.put(parameterIndex, x == null ? null : x.getSubString(1, (int) x.length()));
    }

    @Override
    public void setArray(final int parameterIndex, final Array x) throws SQLException {
        parameters.put(parameterIndex, x == null ? null : x.getArray());
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return prepareResultMetaData();
    }

    @Override
    public void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setNull(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
        parameters.put(parameterIndex, null);
    }

    @Override
    public void setURL(final int parameterIndex, final URL x) throws SQLException {
        parameters.put(parameterIndex, x.toString());
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return new JdbcParameterMetaData(JdbcMarshaling.countPlaceholders(originalSql));
    }

    @Override
    public void setRowId(final int parameterIndex, final RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public void setNString(final int parameterIndex, final String value) throws SQLException {
        parameters.put(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader value, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(value));
    }

    @Override
    public void setNClob(final int parameterIndex, final NClob value) throws SQLException {
        parameters.put(parameterIndex, value == null ? null : value.getSubString(1, (int) value.length()));
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToBytes(inputStream));
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setSQLXML(final int parameterIndex, final SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scaleOrLength) throws SQLException {
        parameters.put(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream x) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream x) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader value) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(value));
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToBytes(inputStream));
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
        parameters.put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    /**
     * Result-column metadata for a result-returning statement (null for DML / non-queries). Uses the last
     * executed result if present, else describes the query by executing a copy with every unbound placeholder
     * set to NULL (a parameterized SELECT then returns zero rows but still carries the projection's columns).
     */
    private ResultSetMetaData prepareResultMetaData() throws SQLException {
        if (currentResultSet != null) {
            return currentResultSet.getMetaData();
        }
        final String head = originalSql.trim().toUpperCase();
        if (!(head.startsWith("SELECT") || head.startsWith("WITH") || head.startsWith("SHOW")
                || head.startsWith("DESC") || head.startsWith("EXPLAIN") || head.startsWith("CALL"))) {
            return null;
        }
        final Map<Integer, Object> filled = new HashMap<>(parameters);
        final int count = JdbcMarshaling.countPlaceholders(originalSql);
        for (int i = 1; i <= count; i++) {
            filled.putIfAbsent(i, null);
        }
        return super.executeQuery(JdbcMarshaling.substitutePlaceholders(originalSql, filled)).getMetaData();
    }

    // Helper method to build SQL with parameters
    private String buildSql() throws SQLException {
        // Inlined client-side (no server-side binding); escapes literals and skips '?' inside string literals.
        return JdbcMarshaling.substitutePlaceholders(originalSql, parameters);
    }
}
