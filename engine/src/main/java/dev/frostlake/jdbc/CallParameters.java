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
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a callable statement keeps beside its bound values: where each {@code :name} parameter stands, and the row its
 * OUT parameters are read from. That row is the first row of the statement's result, read through the very result
 * set getters a query's rows go through, so an OUT parameter reads as its column of the result does.
 */
final class CallParameters {

    private final Map<String, Integer> named = new HashMap<>();
    // The last CALL's result, on its first row; null before a CALL and when its result has no row.
    private ResultSet row;
    // The row the last OUT getter read; null when it found no value there.
    private ResultSet lastRead;

    /**
     * The named parameters of a statement's text, each at the position it has among them.
     *
     * @param sql the statement as written, {@code :name} parameters included
     */
    CallParameters(final String sql) {
        int index = 1;
        for (final String name : JdbcMarshaling.namedParameterOrder(sql)) {
            named.put(name, index++);
        }
    }

    /**
     * A callable statement's text as it runs: the JDBC escape braces around it dropped, as the account's driver drops
     * them, and each {@code :name} parameter a {@code ?} at its place.
     *
     * @param sql the statement as written
     * @return the statement to prepare
     */
    static String positional(final String sql) {
        final String trimmed = sql == null ? null : sql.trim();
        final String unbraced = trimmed != null && trimmed.startsWith("{") && trimmed.endsWith("}")
            ? trimmed.substring(1, trimmed.length() - 1) : sql;
        return JdbcMarshaling.namedParametersToPositional(unbraced);
    }

    /**
     * The position of a named parameter.
     *
     * @param parameterName the name, in any case
     * @return its position, from 1
     * @throws SQLException when the statement has no parameter of that name
     */
    int index(final String parameterName) throws SQLException {
        final Integer index = parameterName == null ? null : named.get(parameterName.toUpperCase(Locale.ROOT));
        if (index == null) {
            throw new SQLException("Parameter not found: " + parameterName);
        }
        return index.intValue();
    }

    /**
     * Takes the result the statement now stands on; its first row holds the OUT values.
     *
     * @param result a result set of its own over the statement's current result, or null for none
     * @throws SQLException when the result cannot be read
     */
    void capture(final ResultSet result) throws SQLException {
        row = result != null && result.next() ? result : null;
        lastRead = null;
    }

    /**
     * The row an OUT getter reads a parameter from.
     *
     * @param owner the statement asking
     * @param index the parameter, from 1
     * @return the row, or null when no value stands at that position
     * @throws SQLException when the statement is closed
     */
    ResultSet at(final Statement owner, final int index) throws SQLException {
        if (owner.isClosed()) {
            throw new SQLException("Statement is closed");
        }
        lastRead = row != null && index >= 1 && index <= row.getMetaData().getColumnCount() ? row : null;
        return lastRead;
    }

    /**
     * Whether the last OUT getter read SQL NULL, or found no value.
     *
     * @return whether it did
     * @throws SQLException when the row cannot be read
     */
    boolean wasNull() throws SQLException {
        return lastRead == null || lastRead.wasNull();
    }
}
