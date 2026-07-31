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

package dev.frostlake.storage;

import dev.frostlake.types.DataType;

public class ResultSetColumn {
    private final String name;
    private final DataType dataType;
    private final String tableName;
    // The type this column is STATICALLY KNOWN to produce, or null when it could not be determined —
    // deliberately kept ALONGSIDE dataType rather than replacing it, so what every existing reader
    // (JDBC metadata, CTAS, set-operation coercion) sees is unchanged and only the compile-time type
    // rules gain the knowledge. Null by default: a column is only statically typed by saying so, so an
    // un-audited producer can never make an outer query trust a guess.
    private final DataType staticType;

    public ResultSetColumn(final String name, final DataType dataType) {
        this(name, dataType, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName) {
        this(name, dataType, tableName, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType) {
        this.name = name;
        this.dataType = dataType;
        this.tableName = tableName;
        this.staticType = staticType;
    }

    public String getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * The type this column is statically KNOWN to produce, or null when a projection could not tell —
     * in which case {@link #getDataType()} is a placeholder that no type rule may read. A derived table
     * built from this result set carries the same distinction into the enclosing query.
     */
    public DataType getStaticType() {
        return staticType;
    }
}
