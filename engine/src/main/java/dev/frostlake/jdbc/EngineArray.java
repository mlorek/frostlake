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

import tools.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link java.sql.Array} over an engine ARRAY value. The engine renders ARRAY as a JSON-array string (see
 * {@code ArrayConstruct}), which is what both transports surface — directly in-process, and as a JSON string
 * over the HTTP wire — so {@link #from} accepts either that string or an already-parsed {@link List}. Elements
 * are VARIANT in the engine's model, so the base type is reported as {@code OTHER}/{@code "VARIANT"}.
 * {@code getResultSet()} is unsupported (minimal implementation).
 */
class EngineArray implements Array {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<?> elements;

    EngineArray(final List<?> elements) {
        this.elements = elements;
    }

    /** Wrap an engine ARRAY value (a {@link List} or a JSON-array string); returns {@code null} for a null value. */
    static EngineArray from(final Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return new EngineArray((List<?>) value);
        }
        if (value instanceof Object[]) {
            return new EngineArray(Arrays.asList((Object[]) value));
        }
        final String text = value.toString().trim();
        if (text.startsWith("[")) {
            try {
                return new EngineArray(MAPPER.readValue(text, List.class));
            } catch (final RuntimeException e) {
                throw new SQLException("Could not parse ARRAY value: " + text, e);
            }
        }
        throw new SQLException("Column value is not an ARRAY: " + value);
    }

    @Override
    public String getBaseTypeName() {
        return "VARIANT";
    }

    @Override
    public int getBaseType() {
        return Types.OTHER;
    }

    @Override
    public Object getArray() {
        return elements.toArray();
    }

    @Override
    public Object getArray(final Map<String, Class<?>> map) {
        return getArray();
    }

    @Override
    public Object getArray(final long index, final int count) throws SQLException {
        return slice(index, count).toArray();
    }

    @Override
    public Object getArray(final long index, final int count, final Map<String, Class<?>> map) throws SQLException {
        return getArray(index, count);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        throw new SQLFeatureNotSupportedException("Array.getResultSet not supported");
    }

    @Override
    public ResultSet getResultSet(final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array.getResultSet not supported");
    }

    @Override
    public ResultSet getResultSet(final long index, final int count) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array.getResultSet not supported");
    }

    @Override
    public ResultSet getResultSet(final long index, final int count, final Map<String, Class<?>> map)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("Array.getResultSet not supported");
    }

    @Override
    public void free() {
        // Nothing to release — the elements are an in-memory list.
    }

    private List<?> slice(final long index, final int count) throws SQLException {
        final int from = (int) (index - 1);   // JDBC array indices are 1-based
        if (from < 0 || from > elements.size()) {
            throw new SQLException("Array index out of range: " + index);
        }
        return elements.subList(from, Math.min(from + count, elements.size()));
    }
}
