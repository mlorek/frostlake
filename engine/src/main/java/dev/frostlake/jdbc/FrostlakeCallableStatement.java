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
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * The callable statement surface both transports share, written once over each transport's prepared statement. A
 * {@code :name} parameter binds at the position it has among the named ones; an OUT parameter is a column of the
 * CALL's result, read from its first row through the result set getters, so {@code getString(1)} answers what
 * {@code getResultSet().getString(1)} answers.
 *
 * <p>Both are this driver's own surface: the account's driver binds no named parameter and models a procedure's
 * return value only as a result set.
 */
interface FrostlakeCallableStatement extends CallableStatement {

    /**
     * This statement's named parameters and the row its OUT parameters are read from.
     *
     * @return the statement's call parameters
     */
    CallParameters callParameters();

    // ---------------------------------------------------------------- OUT registration

    @Override
    default void registerOutParameter(final int parameterIndex, final int sqlType) throws SQLException {
        if (isClosed()) {
            throw new SQLException("Statement is closed");
        }
    }

    @Override
    default void registerOutParameter(final int parameterIndex, final int sqlType, final int scale)
            throws SQLException {
        registerOutParameter(parameterIndex, sqlType);
    }

    @Override
    default void registerOutParameter(final int parameterIndex, final int sqlType, final String typeName)
            throws SQLException {
        registerOutParameter(parameterIndex, sqlType);
    }

    @Override
    default void registerOutParameter(final String parameterName, final int sqlType) throws SQLException {
        registerOutParameter(callParameters().index(parameterName), sqlType);
    }

    @Override
    default void registerOutParameter(final String parameterName, final int sqlType, final int scale)
            throws SQLException {
        registerOutParameter(parameterName, sqlType);
    }

    @Override
    default void registerOutParameter(final String parameterName, final int sqlType, final String typeName)
            throws SQLException {
        registerOutParameter(parameterName, sqlType);
    }

    // ---------------------------------------------------------------- OUT values by position

    @Override
    default boolean wasNull() throws SQLException {
        return callParameters().wasNull();
    }

    @Override
    default String getString(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getString(parameterIndex);
    }

    @Override
    default boolean getBoolean(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row != null && row.getBoolean(parameterIndex);
    }

    @Override
    default byte getByte(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getByte(parameterIndex);
    }

    @Override
    default short getShort(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getShort(parameterIndex);
    }

    @Override
    default int getInt(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getInt(parameterIndex);
    }

    @Override
    default long getLong(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getLong(parameterIndex);
    }

    @Override
    default float getFloat(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getFloat(parameterIndex);
    }

    @Override
    default double getDouble(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? 0 : row.getDouble(parameterIndex);
    }

    @Override
    @SuppressWarnings("deprecation")
    default BigDecimal getBigDecimal(final int parameterIndex, final int scale) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getBigDecimal(parameterIndex, scale);
    }

    @Override
    default BigDecimal getBigDecimal(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getBigDecimal(parameterIndex);
    }

    @Override
    default byte[] getBytes(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getBytes(parameterIndex);
    }

    @Override
    default Date getDate(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getDate(parameterIndex);
    }

    @Override
    default Date getDate(final int parameterIndex, final Calendar cal) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getDate(parameterIndex, cal);
    }

    @Override
    default Time getTime(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getTime(parameterIndex);
    }

    @Override
    default Time getTime(final int parameterIndex, final Calendar cal) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getTime(parameterIndex, cal);
    }

    @Override
    default Timestamp getTimestamp(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getTimestamp(parameterIndex);
    }

    @Override
    default Timestamp getTimestamp(final int parameterIndex, final Calendar cal) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getTimestamp(parameterIndex, cal);
    }

    @Override
    default Object getObject(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getObject(parameterIndex);
    }

    @Override
    default Object getObject(final int parameterIndex, final Map<String, Class<?>> map) throws SQLException {
        return getObject(parameterIndex);
    }

    @Override
    default <T> T getObject(final int parameterIndex, final Class<T> type) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getObject(parameterIndex, type);
    }

    @Override
    default Array getArray(final int parameterIndex) throws SQLException {
        final ResultSet row = callParameters().at(this, parameterIndex);
        return row == null ? null : row.getArray(parameterIndex);
    }

    @Override
    default String getNString(final int parameterIndex) throws SQLException {
        return getString(parameterIndex);
    }

    // ---------------------------------------------------------------- OUT values by name

    @Override
    default String getString(final String parameterName) throws SQLException {
        return getString(callParameters().index(parameterName));
    }

    @Override
    default boolean getBoolean(final String parameterName) throws SQLException {
        return getBoolean(callParameters().index(parameterName));
    }

    @Override
    default byte getByte(final String parameterName) throws SQLException {
        return getByte(callParameters().index(parameterName));
    }

    @Override
    default short getShort(final String parameterName) throws SQLException {
        return getShort(callParameters().index(parameterName));
    }

    @Override
    default int getInt(final String parameterName) throws SQLException {
        return getInt(callParameters().index(parameterName));
    }

    @Override
    default long getLong(final String parameterName) throws SQLException {
        return getLong(callParameters().index(parameterName));
    }

    @Override
    default float getFloat(final String parameterName) throws SQLException {
        return getFloat(callParameters().index(parameterName));
    }

    @Override
    default double getDouble(final String parameterName) throws SQLException {
        return getDouble(callParameters().index(parameterName));
    }

    @Override
    default BigDecimal getBigDecimal(final String parameterName) throws SQLException {
        return getBigDecimal(callParameters().index(parameterName));
    }

    @Override
    default byte[] getBytes(final String parameterName) throws SQLException {
        return getBytes(callParameters().index(parameterName));
    }

    @Override
    default Date getDate(final String parameterName) throws SQLException {
        return getDate(callParameters().index(parameterName));
    }

    @Override
    default Date getDate(final String parameterName, final Calendar cal) throws SQLException {
        return getDate(callParameters().index(parameterName), cal);
    }

    @Override
    default Time getTime(final String parameterName) throws SQLException {
        return getTime(callParameters().index(parameterName));
    }

    @Override
    default Time getTime(final String parameterName, final Calendar cal) throws SQLException {
        return getTime(callParameters().index(parameterName), cal);
    }

    @Override
    default Timestamp getTimestamp(final String parameterName) throws SQLException {
        return getTimestamp(callParameters().index(parameterName));
    }

    @Override
    default Timestamp getTimestamp(final String parameterName, final Calendar cal) throws SQLException {
        return getTimestamp(callParameters().index(parameterName), cal);
    }

    @Override
    default Object getObject(final String parameterName) throws SQLException {
        return getObject(callParameters().index(parameterName));
    }

    @Override
    default Object getObject(final String parameterName, final Map<String, Class<?>> map) throws SQLException {
        return getObject(callParameters().index(parameterName));
    }

    @Override
    default <T> T getObject(final String parameterName, final Class<T> type) throws SQLException {
        return getObject(callParameters().index(parameterName), type);
    }

    @Override
    default Array getArray(final String parameterName) throws SQLException {
        return getArray(callParameters().index(parameterName));
    }

    @Override
    default String getNString(final String parameterName) throws SQLException {
        return getString(callParameters().index(parameterName));
    }

    // ---------------------------------------------------------------- named binds: each binds its position

    @Override
    default void setNull(final String parameterName, final int sqlType) throws SQLException {
        setNull(callParameters().index(parameterName), sqlType);
    }

    @Override
    default void setNull(final String parameterName, final int sqlType, final String typeName) throws SQLException {
        setNull(callParameters().index(parameterName), sqlType, typeName);
    }

    @Override
    default void setBoolean(final String parameterName, final boolean x) throws SQLException {
        setBoolean(callParameters().index(parameterName), x);
    }

    @Override
    default void setByte(final String parameterName, final byte x) throws SQLException {
        setByte(callParameters().index(parameterName), x);
    }

    @Override
    default void setShort(final String parameterName, final short x) throws SQLException {
        setShort(callParameters().index(parameterName), x);
    }

    @Override
    default void setInt(final String parameterName, final int x) throws SQLException {
        setInt(callParameters().index(parameterName), x);
    }

    @Override
    default void setLong(final String parameterName, final long x) throws SQLException {
        setLong(callParameters().index(parameterName), x);
    }

    @Override
    default void setFloat(final String parameterName, final float x) throws SQLException {
        setFloat(callParameters().index(parameterName), x);
    }

    @Override
    default void setDouble(final String parameterName, final double x) throws SQLException {
        setDouble(callParameters().index(parameterName), x);
    }

    @Override
    default void setBigDecimal(final String parameterName, final BigDecimal x) throws SQLException {
        setBigDecimal(callParameters().index(parameterName), x);
    }

    @Override
    default void setString(final String parameterName, final String x) throws SQLException {
        setString(callParameters().index(parameterName), x);
    }

    @Override
    default void setNString(final String parameterName, final String value) throws SQLException {
        setNString(callParameters().index(parameterName), value);
    }

    @Override
    default void setBytes(final String parameterName, final byte[] x) throws SQLException {
        setBytes(callParameters().index(parameterName), x);
    }

    @Override
    default void setDate(final String parameterName, final Date x) throws SQLException {
        setDate(callParameters().index(parameterName), x);
    }

    @Override
    default void setDate(final String parameterName, final Date x, final Calendar cal) throws SQLException {
        setDate(callParameters().index(parameterName), x, cal);
    }

    @Override
    default void setTime(final String parameterName, final Time x) throws SQLException {
        setTime(callParameters().index(parameterName), x);
    }

    @Override
    default void setTime(final String parameterName, final Time x, final Calendar cal) throws SQLException {
        setTime(callParameters().index(parameterName), x, cal);
    }

    @Override
    default void setTimestamp(final String parameterName, final Timestamp x) throws SQLException {
        setTimestamp(callParameters().index(parameterName), x);
    }

    @Override
    default void setTimestamp(final String parameterName, final Timestamp x, final Calendar cal)
            throws SQLException {
        setTimestamp(callParameters().index(parameterName), x, cal);
    }

    @Override
    default void setObject(final String parameterName, final Object x) throws SQLException {
        setObject(callParameters().index(parameterName), x);
    }

    @Override
    default void setObject(final String parameterName, final Object x, final int targetSqlType)
            throws SQLException {
        setObject(callParameters().index(parameterName), x, targetSqlType);
    }

    @Override
    default void setObject(final String parameterName, final Object x, final int targetSqlType, final int scale)
            throws SQLException {
        setObject(callParameters().index(parameterName), x, targetSqlType, scale);
    }

    @Override
    default void setURL(final String parameterName, final URL val) throws SQLException {
        setURL(callParameters().index(parameterName), val);
    }

    @Override
    default void setRowId(final String parameterName, final RowId x) throws SQLException {
        setRowId(callParameters().index(parameterName), x);
    }

    @Override
    default void setSQLXML(final String parameterName, final SQLXML xmlObject) throws SQLException {
        setSQLXML(callParameters().index(parameterName), xmlObject);
    }

    @Override
    default void setAsciiStream(final String parameterName, final InputStream x) throws SQLException {
        setAsciiStream(callParameters().index(parameterName), x);
    }

    @Override
    default void setAsciiStream(final String parameterName, final InputStream x, final int length)
            throws SQLException {
        setAsciiStream(callParameters().index(parameterName), x, length);
    }

    @Override
    default void setAsciiStream(final String parameterName, final InputStream x, final long length)
            throws SQLException {
        setAsciiStream(callParameters().index(parameterName), x, length);
    }

    @Override
    default void setBinaryStream(final String parameterName, final InputStream x) throws SQLException {
        setBinaryStream(callParameters().index(parameterName), x);
    }

    @Override
    default void setBinaryStream(final String parameterName, final InputStream x, final int length)
            throws SQLException {
        setBinaryStream(callParameters().index(parameterName), x, length);
    }

    @Override
    default void setBinaryStream(final String parameterName, final InputStream x, final long length)
            throws SQLException {
        setBinaryStream(callParameters().index(parameterName), x, length);
    }

    @Override
    default void setCharacterStream(final String parameterName, final Reader reader) throws SQLException {
        setCharacterStream(callParameters().index(parameterName), reader);
    }

    @Override
    default void setCharacterStream(final String parameterName, final Reader reader, final int length)
            throws SQLException {
        setCharacterStream(callParameters().index(parameterName), reader, length);
    }

    @Override
    default void setCharacterStream(final String parameterName, final Reader reader, final long length)
            throws SQLException {
        setCharacterStream(callParameters().index(parameterName), reader, length);
    }

    @Override
    default void setNCharacterStream(final String parameterName, final Reader value) throws SQLException {
        setNCharacterStream(callParameters().index(parameterName), value);
    }

    @Override
    default void setNCharacterStream(final String parameterName, final Reader value, final long length)
            throws SQLException {
        setNCharacterStream(callParameters().index(parameterName), value, length);
    }

    @Override
    default void setBlob(final String parameterName, final Blob x) throws SQLException {
        setBlob(callParameters().index(parameterName), x);
    }

    @Override
    default void setBlob(final String parameterName, final InputStream inputStream) throws SQLException {
        setBlob(callParameters().index(parameterName), inputStream);
    }

    @Override
    default void setBlob(final String parameterName, final InputStream inputStream, final long length)
            throws SQLException {
        setBlob(callParameters().index(parameterName), inputStream, length);
    }

    @Override
    default void setClob(final String parameterName, final Clob x) throws SQLException {
        setClob(callParameters().index(parameterName), x);
    }

    @Override
    default void setClob(final String parameterName, final Reader reader) throws SQLException {
        setClob(callParameters().index(parameterName), reader);
    }

    @Override
    default void setClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        setClob(callParameters().index(parameterName), reader, length);
    }

    @Override
    default void setNClob(final String parameterName, final NClob value) throws SQLException {
        setNClob(callParameters().index(parameterName), value);
    }

    @Override
    default void setNClob(final String parameterName, final Reader reader) throws SQLException {
        setNClob(callParameters().index(parameterName), reader);
    }

    @Override
    default void setNClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        setNClob(callParameters().index(parameterName), reader, length);
    }

    // ---------------------------------------------------------------- what no OUT parameter reads as

    @Override
    default Ref getRef(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Ref getRef(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Blob getBlob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Blob getBlob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Clob getClob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Clob getClob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default NClob getNClob(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default NClob getNClob(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default SQLXML getSQLXML(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default SQLXML getSQLXML(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default RowId getRowId(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default RowId getRowId(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default URL getURL(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default URL getURL(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Reader getCharacterStream(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Reader getCharacterStream(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Reader getNCharacterStream(final int parameterIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    default Reader getNCharacterStream(final String parameterName) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
}
