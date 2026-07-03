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

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC Struct implementation for Frostlake SQL Engine.
 * Represents SQL STRUCT/ROW type with named fields.
 */
public class DirectStruct implements Struct {

    private final String typeName;
    private final Object[] attributes;
    private final String[] attributeNames;
    private boolean freed = false;

    /**
     * Create a Struct from attribute names and values
     */
    public DirectStruct(final String typeName, final String[] attributeNames, final Object[] attributes) {
        this.typeName = typeName;
        this.attributeNames = attributeNames != null ? attributeNames.clone() : new String[0];
        this.attributes = attributes != null ? attributes.clone() : new Object[0];

        if (this.attributeNames.length != this.attributes.length) {
            throw new IllegalArgumentException("Attribute names and values must have same length");
        }
    }

    /**
     * Create a Struct from a Map of field names to values
     */
    public DirectStruct(final String typeName, final Map<String, Object> fields) {
        this.typeName = typeName;
        this.attributeNames = fields.keySet().toArray(new String[0]);
        this.attributes = fields.values().toArray();
    }

    @Override
    public String getSQLTypeName() throws SQLException {
        checkFreed();
        return typeName;
    }

    @Override
    public Object[] getAttributes() throws SQLException {
        checkFreed();
        return attributes.clone();
    }

    @Override
    public Object[] getAttributes(final Map<String, Class<?>> map) throws SQLException {
        return getAttributes();
    }

    /**
     * Get the attribute names (field names) of this struct
     */
    public String[] getAttributeNames() throws SQLException {
        checkFreed();
        return attributeNames.clone();
    }

    /**
     * Get a specific attribute by name
     */
    public Object getAttribute(final String name) throws SQLException {
        checkFreed();
        if (name == null) {
            throw new SQLException("Attribute name cannot be null");
        }

        for (int i = 0; i < attributeNames.length; i++) {
            if (attributeNames[i].equalsIgnoreCase(name)) {
                return attributes[i];
            }
        }

        throw new SQLException("Attribute not found: " + name);
    }

    /**
     * Get a specific attribute by index (1-based)
     */
    public Object getAttribute(final int index) throws SQLException {
        checkFreed();
        if (index < 1 || index > attributes.length) {
            throw new SQLException("Index out of bounds: " + index);
        }
        return attributes[index - 1];
    }

    /**
     * Get this struct as a Map
     */
    public Map<String, Object> asMap() throws SQLException {
        checkFreed();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < attributeNames.length; i++) {
            result.put(attributeNames[i], attributes[i]);
        }
        return result;
    }

    /**
     * Get the number of attributes in this struct
     */
    public int size() throws SQLException {
        checkFreed();
        return attributes.length;
    }

    @Override
    public String toString() {
        if (freed) {
            return "DirectStruct{freed}";
        }
        StringBuilder sb = new StringBuilder("DirectStruct{typeName='");
        sb.append(typeName).append("', fields={");
        for (int i = 0; i < attributeNames.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(attributeNames[i]).append("=").append(attributes[i]);
        }
        sb.append("}}");
        return sb.toString();
    }

    // Note: free() method is not part of java.sql.Struct interface in Java 8
    // Adding it for consistency with other JDBC large objects
    public void free() throws SQLException {
        freed = true;
    }

    private void checkFreed() throws SQLException {
        if (freed) {
            throw new SQLException("Struct has been freed");
        }
    }
}
