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

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC Array implementation for Frostlake SQL Engine.
 * Represents SQL ARRAY type.
 */
public class DirectArray implements Array {

    private final String typeName;
    private final Object[] elements;
    private boolean freed = false;

    /**
     * Create an Array from a List
     */
    public DirectArray(final String typeName, final List<?> elements) {
        this.typeName = typeName;
        this.elements = elements != null ? elements.toArray() : new Object[0];
    }

    /**
     * Create an Array from an Object array
     */
    public DirectArray(final String typeName, final Object[] elements) {
        this.typeName = typeName;
        this.elements = elements != null ? elements.clone() : new Object[0];
    }

    @Override
    public String getBaseTypeName() throws SQLException {
        checkFreed();
        return typeName;
    }

    @Override
    public int getBaseType() throws SQLException {
        checkFreed();
        // Map type name to SQL type code
        if (typeName == null) {
            return Types.OTHER;
        }
        switch (typeName.toUpperCase()) {
            case "INTEGER":
            case "INT":
                return Types.INTEGER;
            case "BIGINT":
                return Types.BIGINT;
            case "VARCHAR":
            case "STRING":
                return Types.VARCHAR;
            case "DOUBLE":
                return Types.DOUBLE;
            case "BOOLEAN":
                return Types.BOOLEAN;
            case "DATE":
                return Types.DATE;
            case "TIMESTAMP":
                return Types.TIMESTAMP;
            case "BINARY":
            case "VARBINARY":
                return Types.BINARY;
            default:
                return Types.OTHER;
        }
    }

    @Override
    public Object getArray() throws SQLException {
        checkFreed();
        return elements.clone();
    }

    @Override
    public Object getArray(final Map<String, Class<?>> map) throws SQLException {
        return getArray();
    }

    @Override
    public Object getArray(final long index, final int count) throws SQLException {
        checkFreed();

        // JDBC array indices are 1-based
        if (index < 1 || index > elements.length) {
            throw new SQLException("Index out of bounds: " + index);
        }

        int startIndex = (int) (index - 1);
        int actualCount = Math.min(count, elements.length - startIndex);

        Object[] result = new Object[actualCount];
        System.arraycopy(elements, startIndex, result, 0, actualCount);
        return result;
    }

    @Override
    public Object getArray(final long index, final int count, final Map<String, Class<?>> map) throws SQLException {
        return getArray(index, count);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        throw new SQLException("getResultSet() not supported for Array");
    }

    @Override
    public ResultSet getResultSet(final Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("getResultSet() not supported for Array");
    }

    @Override
    public ResultSet getResultSet(final long index, final int count) throws SQLException {
        throw new SQLException("getResultSet() not supported for Array");
    }

    @Override
    public ResultSet getResultSet(final long index, final int count, final Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("getResultSet() not supported for Array");
    }

    @Override
    public void free() throws SQLException {
        freed = true;
    }

    private void checkFreed() throws SQLException {
        if (freed) {
            throw new SQLException("Array has been freed");
        }
    }

    /**
     * Get the number of elements in the array
     */
    public int length() throws SQLException {
        checkFreed();
        return elements.length;
    }

    @Override
    public String toString() {
        if (freed) {
            return "DirectArray{freed}";
        }
        return "DirectArray{typeName='" + typeName + "', length=" + elements.length + "}";
    }
}
