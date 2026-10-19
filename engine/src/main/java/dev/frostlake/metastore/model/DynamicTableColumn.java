/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.metastore.model;

import dev.frostlake.types.DataType;

/**
 * A column a dynamic table declares in its own column list: its name, and the type and the comment where the
 * list gives them. What the list leaves out the table's query supplies.
 */
public final class DynamicTableColumn {

    private final String name;
    private final DataType type;
    private final String comment;

    /**
     * @param name the column's canonical name
     * @param type the declared type, or null when the query's own is kept
     * @param comment the column's comment, or null
     */
    public DynamicTableColumn(final String name, final DataType type, final String comment) {
        this.name = name;
        this.type = type;
        this.comment = comment;
    }

    /** The column's canonical name. */
    public String getName() {
        return name;
    }

    /** The declared type, or null when the query's own is kept. */
    public DataType getType() {
        return type;
    }

    /** The column's comment, or null. */
    public String getComment() {
        return comment;
    }
}
