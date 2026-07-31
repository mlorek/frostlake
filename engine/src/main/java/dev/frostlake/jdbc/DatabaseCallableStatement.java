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
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC CallableStatement implementation for Frostlake SQL Engine
 * Supports stored procedure calls with IN and OUT parameters
 */
public class DatabaseCallableStatement extends DatabasePreparedStatement implements CallableStatement {
    private final Map<Integer, Object> outParameters;
    private boolean lastReadWasNull;
    private final Map<String, Integer> namedParameters;

    public DatabaseCallableStatement(final DatabaseConnection connection, final HttpClient httpClient, final String sql) {
        super(connection, httpClient, sql);
        this.outParameters = new HashMap<>();
        this.namedParameters = new HashMap<>();
        parseNamedParameters(sql);
    }

    /**
     * Parse named parameters from the SQL (e.g., :param_name)
     */
    private void parseNamedParameters(final String sql) {
        int index = 1;
        for (final String name : JdbcMarshaling.namedParameterOrder(sql)) {
            namedParameters.put(name, index++);
        }
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType) throws SQLException {
        checkClosed();
        // Mark parameter as OUT parameter
        outParameters.put(parameterIndex, null);
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType, final int scale) throws SQLException {
        registerOutParameter(parameterIndex, sqlType);
    }

    @Override
    public boolean wasNull() throws SQLException {
        return lastReadWasNull;
    }

    /** Read an OUT parameter and record its nullness for {@link #wasNull()}. */
    private Object out(final int parameterIndex) {
        final Object value = outParameters.get(parameterIndex);
        lastReadWasNull = value == null;
        return value;
    }

    @Override
    public String getString(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public boolean getBoolean(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public byte getByte(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        return Byte.parseByte(value.toString());
    }

    @Override
    public short getShort(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        return Short.parseShort(value.toString());
    }

    @Override
    public int getInt(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public long getLong(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    @Override
    public float getFloat(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return Float.parseFloat(value.toString());
    }

    @Override
    public double getDouble(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    @Override
    public BigDecimal getBigDecimal(final int parameterIndex, final int scale) throws SQLException {
        return getBigDecimal(parameterIndex);
    }

    @Override
    public byte[] getBytes(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Bytes not supported");
    }

    @Override
    public Date getDate(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        // Engine temporal text may be a full timestamp ("2026-01-15 10:30:00" or ISO with 'T');
        // a DATE conversion takes the leading date part.
        final String text = value.toString();
        return Date.valueOf(text.length() > 10 ? text.substring(0, 10) : text);
    }

    @Override
    public Time getTime(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof Time) {
            return (Time) value;
        }
        // A full timestamp (ISO 'T' or space separated) converts by its time-of-day part; trim
        // fractional seconds, which Time.valueOf does not accept.
        String text = value.toString();
        final int sep = Math.max(text.indexOf('T'), text.indexOf(' '));
        if (sep >= 0) {
            text = text.substring(sep + 1);
        }
        final int dot = text.indexOf('.');
        return Time.valueOf(dot >= 0 ? text.substring(0, dot) : text);
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }
        // Timestamp.valueOf requires the space-separated form; engine values may arrive ISO ('T').
        return Timestamp.valueOf(value.toString().replace('T', ' '));
    }

    @Override
    public Object getObject(final int parameterIndex) throws SQLException {
        checkClosed();
        return out(parameterIndex);
    }

    @Override
    public BigDecimal getBigDecimal(final int parameterIndex) throws SQLException {
        checkClosed();
        Object value = out(parameterIndex);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    @Override
    public Object getObject(final int parameterIndex, final Map<String, Class<?>> map) throws SQLException {
        return getObject(parameterIndex);
    }

    @Override
    public Ref getRef(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Ref not supported");
    }

    @Override
    public Blob getBlob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public Clob getClob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public Array getArray(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array not supported");
    }

    @Override
    public Date getDate(final int parameterIndex, final Calendar cal) throws SQLException {
        return getDate(parameterIndex);
    }

    @Override
    public Time getTime(final int parameterIndex, final Calendar cal) throws SQLException {
        return getTime(parameterIndex);
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex, final Calendar cal) throws SQLException {
        return getTimestamp(parameterIndex);
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
        registerOutParameter(parameterIndex, sqlType);
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType) throws SQLException {
        Integer index = namedParameters.get(parameterName.toUpperCase());
        if (index == null) {
            throw new SQLException("Parameter not found: " + parameterName);
        }
        registerOutParameter(index, sqlType);
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final int scale) throws SQLException {
        registerOutParameter(parameterName, sqlType);
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final String typeName) throws SQLException {
        registerOutParameter(parameterName, sqlType);
    }

    @Override
    public URL getURL(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("URL not supported");
    }

    @Override
    public void setURL(final String parameterName, final URL val) throws SQLException {
        throw new SQLFeatureNotSupportedException("URL not supported");
    }

    @Override
    public void setNull(final String parameterName, final int sqlType) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setNull(index, sqlType);
    }

    @Override
    public void setBoolean(final String parameterName, final boolean x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setBoolean(index, x);
    }

    @Override
    public void setByte(final String parameterName, final byte x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setByte(index, x);
    }

    @Override
    public void setShort(final String parameterName, final short x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setShort(index, x);
    }

    @Override
    public void setInt(final String parameterName, final int x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setInt(index, x);
    }

    @Override
    public void setLong(final String parameterName, final long x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setLong(index, x);
    }

    @Override
    public void setFloat(final String parameterName, final float x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setFloat(index, x);
    }

    @Override
    public void setDouble(final String parameterName, final double x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setDouble(index, x);
    }

    @Override
    public void setBigDecimal(final String parameterName, final BigDecimal x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setBigDecimal(index, x);
    }

    @Override
    public void setString(final String parameterName, final String x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setString(index, x);
    }

    @Override
    public void setBytes(final String parameterName, final byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Bytes not supported");
    }

    @Override
    public void setDate(final String parameterName, final Date x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setDate(index, x);
    }

    @Override
    public void setTime(final String parameterName, final Time x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setTime(index, x);
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setTimestamp(index, x);
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType, final int scale) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setObject(index, x, targetSqlType, scale);
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setObject(index, x, targetSqlType);
    }

    @Override
    public void setObject(final String parameterName, final Object x) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        setObject(index, x);
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setDate(final String parameterName, final Date x, final Calendar cal) throws SQLException {
        setDate(parameterName, x);
    }

    @Override
    public void setTime(final String parameterName, final Time x, final Calendar cal) throws SQLException {
        setTime(parameterName, x);
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x, final Calendar cal) throws SQLException {
        setTimestamp(parameterName, x);
    }

    @Override
    public void setNull(final String parameterName, final int sqlType, final String typeName) throws SQLException {
        setNull(parameterName, sqlType);
    }

    @Override
    public String getString(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getString(index);
    }

    @Override
    public boolean getBoolean(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getBoolean(index);
    }

    @Override
    public byte getByte(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getByte(index);
    }

    @Override
    public short getShort(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getShort(index);
    }

    @Override
    public int getInt(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getInt(index);
    }

    @Override
    public long getLong(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getLong(index);
    }

    @Override
    public float getFloat(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getFloat(index);
    }

    @Override
    public double getDouble(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getDouble(index);
    }

    @Override
    public byte[] getBytes(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Bytes not supported");
    }

    @Override
    public Date getDate(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getDate(index);
    }

    @Override
    public Time getTime(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getTime(index);
    }

    @Override
    public Timestamp getTimestamp(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getTimestamp(index);
    }

    @Override
    public Object getObject(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getObject(index);
    }

    @Override
    public BigDecimal getBigDecimal(final String parameterName) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getBigDecimal(index);
    }

    @Override
    public Object getObject(final String parameterName, final Map<String, Class<?>> map) throws SQLException {
        return getObject(parameterName);
    }

    @Override
    public Ref getRef(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Ref not supported");
    }

    @Override
    public Blob getBlob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public Clob getClob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public Array getArray(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array not supported");
    }

    @Override
    public Date getDate(final String parameterName, final Calendar cal) throws SQLException {
        return getDate(parameterName);
    }

    @Override
    public Time getTime(final String parameterName, final Calendar cal) throws SQLException {
        return getTime(parameterName);
    }

    @Override
    public Timestamp getTimestamp(final String parameterName, final Calendar cal) throws SQLException {
        return getTimestamp(parameterName);
    }

    @Override
    public URL getURL(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("URL not supported");
    }

    @Override
    public RowId getRowId(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public RowId getRowId(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public void setRowId(final String parameterName, final RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public void setNString(final String parameterName, final String value) throws SQLException {
        setString(parameterName, value);
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader value, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setNClob(final String parameterName, final NClob value) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public void setClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public NClob getNClob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public NClob getNClob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public void setSQLXML(final String parameterName, final SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public SQLXML getSQLXML(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public SQLXML getSQLXML(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public String getNString(final int parameterIndex) throws SQLException {
        return getString(parameterIndex);
    }

    @Override
    public String getNString(final String parameterName) throws SQLException {
        return getString(parameterName);
    }

    @Override
    public Reader getNCharacterStream(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public Reader getNCharacterStream(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public Reader getCharacterStream(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public Reader getCharacterStream(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setBlob(final String parameterName, final Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setClob(final String parameterName, final Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }

    @Override
    public void setClob(final String parameterName, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public <T> T getObject(final int parameterIndex, final Class<T> type) throws SQLException {
        Object value = getObject(parameterIndex);
        if (value == null) {
            return null;
        }
        if (type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        throw new SQLException("Cannot convert to " + type.getName());
    }

    @Override
    public <T> T getObject(final String parameterName, final Class<T> type) throws SQLException {
        Integer index = getParameterIndex(parameterName);
        return getObject(index, type);
    }

    // Helper methods

    private Integer getParameterIndex(final String parameterName) throws SQLException {
        Integer index = namedParameters.get(parameterName.toUpperCase());
        if (index == null) {
            throw new SQLException("Parameter not found: " + parameterName);
        }
        return index;
    }

    /**
     * Execute the callable statement
     * For procedures that return result sets, use execute()
     * For functions, the return value can be retrieved via getObject(1)
     */
    @Override
    public boolean execute() throws SQLException {
        // Execute the procedure/function
        boolean hasResultSet = super.execute();

        // If there's a result set, extract OUT parameters from it
        if (hasResultSet && currentResultSet != null) {
            extractOutParameters();
        }

        return hasResultSet;
    }

    /**
     * Extract OUT parameters from result set
     * Assumes first row contains OUT parameter values
     */
    private void extractOutParameters() throws SQLException {
        if (currentResultSet != null && currentResultSet.next()) {
            ResultSetMetaData meta = currentResultSet.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = currentResultSet.getObject(i);
                outParameters.put(i, value);
            }
            currentResultSet.beforeFirst(); // Reset for user
        }
    }
}
