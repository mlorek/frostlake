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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * {@code getObject(int, Class)} as Snowflake's own driver answers it, shared by both transports: each class it
 * supports is read through the TYPED getter of that class — so every conversion, refusal and NULL rule is that
 * getter's — and every other class is refused, NULL cell or not.
 *
 * <p>Measured through snowflake-jdbc over twelve column types, each NULL and not:
 * <ul>
 *   <li>String, Long, Integer, Short, Byte, Double, Float, Boolean, BigDecimal, Timestamp, Date and Time are
 *       supported, and a NULL reads as the getter's answer — {@code 0}, {@code 0.0} or {@code false} for the
 *       boxed primitives, with {@code wasNull()} true, and null for the rest;</li>
 *   <li>every other class — {@code byte[]} and the {@code java.time} classes included — is refused with
 *       {@code Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. Type: <name>}.</li>
 * </ul>
 */
final class TypedObjectReads {

    private TypedObjectReads() {
    }

    /**
     * One cell read as {@code type}.
     *
     * @param rs          the result set the cell is in, whose typed getters do the reading
     * @param columnIndex the cell's 1-based column
     * @param type        the class asked for
     * @param <T>         that class
     * @return the cell as that class
     * @throws SQLException for a class the driver does not support, or a value its getter cannot convert
     */
    static <T> T getObject(final ResultSet rs, final int columnIndex, final Class<T> type) throws SQLException {
        final Object value;
        if (type == String.class) {
            value = rs.getString(columnIndex);
        } else if (type == Long.class) {
            value = Long.valueOf(rs.getLong(columnIndex));
        } else if (type == Integer.class) {
            value = Integer.valueOf(rs.getInt(columnIndex));
        } else if (type == Short.class) {
            value = Short.valueOf(rs.getShort(columnIndex));
        } else if (type == Byte.class) {
            value = Byte.valueOf(rs.getByte(columnIndex));
        } else if (type == Double.class) {
            value = Double.valueOf(rs.getDouble(columnIndex));
        } else if (type == Float.class) {
            value = Float.valueOf(rs.getFloat(columnIndex));
        } else if (type == Boolean.class) {
            value = Boolean.valueOf(rs.getBoolean(columnIndex));
        } else if (type == BigDecimal.class) {
            value = rs.getBigDecimal(columnIndex);
        } else if (type == Timestamp.class) {
            value = rs.getTimestamp(columnIndex);
        } else if (type == Date.class) {
            value = rs.getDate(columnIndex);
        } else if (type == Time.class) {
            value = rs.getTime(columnIndex);
        } else {
            throw new SQLException("Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. Type: "
                + type.getName());
        }
        return type.cast(value);
    }
}
