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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * The driver's rule for an integer getter over an exact number of a scale above 0: refused whatever the number holds,
 * {@code Cannot convert value in the driver from type:3 to type:long, value=3.00.}, where a scale-0 NUMBER reads as
 * the integer it is (live-verified against the account's own driver, {@code getLong} and {@code getInt}).
 */
final class ScaledNumberReads {

    private ScaledNumberReads() {
    }

    /**
     * Refuse an integer getter over a scaled exact number.
     *
     * @param metaData the result's metadata
     * @param column   the column, from 1
     * @param value    the cell, not NULL
     * @param target   the getter's type as the refusal names it, {@code long} or {@code int}
     * @throws SQLException when the column is an exact number of a scale above 0
     */
    static void refuseInteger(final ResultSetMetaData metaData, final int column, final Object value,
                              final String target) throws SQLException {
        if (metaData.getColumnType(column) != Types.DECIMAL || metaData.getScale(column) <= 0) {
            return;
        }
        final String text = value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString();
        throw new SQLException("Cannot convert value in the driver from type:" + Types.DECIMAL + " to type:" + target
            + ", value=" + text + ".");
    }
}
