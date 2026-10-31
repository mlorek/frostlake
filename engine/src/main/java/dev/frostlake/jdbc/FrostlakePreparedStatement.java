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
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * The parameter setters both transports' prepared statements share, written once over the values the statement has
 * bound: each one binds its value at its position, as the client-side substitution reads it (see JdbcMarshaling). A
 * Ref, a RowId and an SQLXML are refused.
 */
interface FrostlakePreparedStatement extends PreparedStatement {

    /**
     * The values bound so far, by position.
     *
     * @return the statement's parameter map, which the setters write
     */
    Map<Integer, Object> boundParameters();

    @Override
    default void setNull(final int parameterIndex, final int sqlType) throws SQLException {
        boundParameters().put(parameterIndex, null);
    }

    @Override
    default void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setByte(final int parameterIndex, final byte x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setShort(final int parameterIndex, final short x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setInt(final int parameterIndex, final int x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setLong(final int parameterIndex, final long x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setFloat(final int parameterIndex, final float x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setDouble(final int parameterIndex, final double x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setString(final int parameterIndex, final String x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setDate(final int parameterIndex, final Date x) throws SQLException {
        boundParameters().put(parameterIndex, x);
    }

    @Override
    default void setTime(final int parameterIndex, final Time x) throws SQLException {
        // The time its moment shows in UTC, whatever the client's zone or the session's (live-verified).
        boundParameters().put(parameterIndex, JdbcMarshaling.timeAtUtc(x));
    }

    @Override
    default void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
        // Its moment, as the account's driver binds it (see JdbcMarshaling.boundMoment).
        boundParameters().put(parameterIndex, JdbcMarshaling.boundMoment(x));
    }

    @Override
    default void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    default void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    default void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    default void clearParameters() throws SQLException {
        boundParameters().clear();
    }

    /** The value converted to the type it is bound as (see JdbcMarshaling.boundAs). */
    @Override
    default void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.boundAs(x, targetSqlType, null));
    }

    @Override
    default void setObject(final int parameterIndex, final Object x) throws SQLException {
        JdbcMarshaling.requireBindable(x);
        boundParameters().put(parameterIndex, JdbcMarshaling.boundObject(x));
    }

    @Override
    default void setCharacterStream(final int parameterIndex, final Reader reader, final int length)
            throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setRef(final int parameterIndex, final Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setRef not supported");
    }

    @Override
    default void setBlob(final int parameterIndex, final Blob x) throws SQLException {
        boundParameters().put(parameterIndex, x == null ? null : x.getBytes(1, (int) x.length()));
    }

    @Override
    default void setClob(final int parameterIndex, final Clob x) throws SQLException {
        boundParameters().put(parameterIndex, x == null ? null : x.getSubString(1, (int) x.length()));
    }

    @Override
    default void setArray(final int parameterIndex, final Array x) throws SQLException {
        boundParameters().put(parameterIndex, x == null ? null : x.getArray());
    }

    @Override
    default void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException {
        // The day the value's moment falls on in the Calendar's zone (live-verified).
        boundParameters().put(parameterIndex, JdbcMarshaling.dateAtCalendar(x, cal));
    }

    @Override
    default void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
        // The account's driver reads no Calendar here: the time its moment shows in UTC (live-verified).
        boundParameters().put(parameterIndex, JdbcMarshaling.timeAtUtc(x));
    }

    @Override
    default void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
        // The Calendar's wall clock, bound as the account's driver binds it (see CalendarBoundInstant).
        boundParameters().put(parameterIndex, JdbcMarshaling.boundAtCalendar(x, cal));
    }

    @Override
    default void setNull(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
        boundParameters().put(parameterIndex, null);
    }

    @Override
    default void setURL(final int parameterIndex, final URL x) throws SQLException {
        boundParameters().put(parameterIndex, x == null ? null : x.toString());
    }

    @Override
    default void setRowId(final int parameterIndex, final RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setRowId not supported");
    }

    @Override
    default void setNString(final int parameterIndex, final String value) throws SQLException {
        boundParameters().put(parameterIndex, value);
    }

    @Override
    default void setNCharacterStream(final int parameterIndex, final Reader value, final long length)
            throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(value));
    }

    @Override
    default void setNClob(final int parameterIndex, final NClob value) throws SQLException {
        boundParameters().put(parameterIndex, value == null ? null : value.getSubString(1, (int) value.length()));
    }

    @Override
    default void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setBlob(final int parameterIndex, final InputStream inputStream, final long length)
            throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToBytes(inputStream));
    }

    @Override
    default void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setSQLXML(final int parameterIndex, final SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("setSQLXML not supported");
    }

    /** The value converted to the type it is bound as, at the scale a DECIMAL names (see JdbcMarshaling.boundAs). */
    @Override
    default void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scaleOrLength)
            throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.boundAs(x, targetSqlType, Integer.valueOf(scaleOrLength)));
    }

    @Override
    default void setAsciiStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    default void setBinaryStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    default void setCharacterStream(final int parameterIndex, final Reader reader, final long length)
            throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setAsciiStream(final int parameterIndex, final InputStream x) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(x));
    }

    @Override
    default void setBinaryStream(final int parameterIndex, final InputStream x) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToBytes(x));
    }

    @Override
    default void setCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setNCharacterStream(final int parameterIndex, final Reader value) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(value));
    }

    @Override
    default void setClob(final int parameterIndex, final Reader reader) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }

    @Override
    default void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToBytes(inputStream));
    }

    @Override
    default void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
        boundParameters().put(parameterIndex, JdbcMarshaling.readToString(reader));
    }
}
