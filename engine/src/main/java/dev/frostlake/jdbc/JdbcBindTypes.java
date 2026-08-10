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
import java.math.BigInteger;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeMap;

/**
 * Live-verified array-bind typing rules for PreparedStatement batches: every batched row must
 * bind the same driver-side type per column ({@code setNull} carries no type and matches
 * anything), and a mismatching row is refused at {@code addBatch} time with the driver's own
 * wording, SQLSTATE {@code 0A000} and vendor code 200023.
 */
public final class JdbcBindTypes {

    private JdbcBindTypes() {
    }

    /**
     * The driver-side bind type name a parameter value carries in the mixed-type refusal:
     * integral and decimal numbers are JAVA_BIGDECIMAL, floating point JAVA_DOUBLE, date-time
     * values JAVA_TIMESTAMP, byte arrays JAVA_BYTES, booleans JAVA_BOOLEAN, and everything else
     * binds as JAVA_STRING. NULL has no type.
     */
    static String bindTypeName(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigDecimal || value instanceof BigInteger) {
            return "JAVA_BIGDECIMAL";
        }
        if (value instanceof Double || value instanceof Float) {
            return "JAVA_DOUBLE";
        }
        if (value instanceof Boolean) {
            return "JAVA_BOOLEAN";
        }
        if (value instanceof Timestamp || value instanceof Time || value instanceof java.util.Date) {
            return "JAVA_TIMESTAMP";
        }
        if (value instanceof byte[]) {
            return "JAVA_BYTES";
        }
        return "JAVA_STRING";
    }

    /**
     * Validates one row being added to a batch against the types earlier rows established,
     * recording this row's types on success. {@code rowNumber} is the 1-based batch row the
     * refusal names.
     */
    static void validateBatchRow(final Map<Integer, String> established,
                                 final Map<Integer, Object> row,
                                 final int rowNumber) throws SQLException {
        final TreeMap<Integer, Object> ordered = new TreeMap<>(row);
        for (final Map.Entry<Integer, Object> entry : ordered.entrySet()) {
            final String current = bindTypeName(entry.getValue());
            if (current == null) {
                continue;
            }
            final String previous = established.get(entry.getKey());
            if (previous == null) {
                established.put(entry.getKey(), current);
            } else if (!previous.equals(current)) {
                throw new SQLException(
                        "Array bind for values with mixed types not supported. Previous type: " + previous
                                + ", Current type: " + current + " at Column: " + entry.getKey()
                                + ", Row: " + rowNumber + ".",
                        "0A000", 200023);
            }
        }
    }
}
