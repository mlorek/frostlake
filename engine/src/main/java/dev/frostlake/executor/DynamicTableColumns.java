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
package dev.frostlake.executor;

import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.DynamicTableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The column list a dynamic table declares over its query: {@code CREATE DYNAMIC TABLE t (x, y NUMBER(10,2)
 * COMMENT 'c') … AS SELECT …}. The list names every column the query produces, in order; a column it types takes
 * the query's values cast to that type, and what it leaves out — a type, a comment — the query supplies.
 *
 * <p>A list of another length than the query's is {@code Invalid column definition list}, a name given twice
 * {@code duplicate column name 'X'}, and a type the query's column cannot be written to
 * {@code incompatible types: [NUMBER(38,0)] and [DATE]} (live-verified).
 */
public final class DynamicTableColumns {

    private DynamicTableColumns() {
    }

    /**
     * Refuses a column list that names a column twice.
     *
     * @param declared the declared columns
     */
    public static void requireDistinct(final List<DynamicTableColumn> declared) {
        final Set<String> seen = new HashSet<String>();
        for (final DynamicTableColumn column : declared) {
            if (!seen.add(column.getName())) {
                throw new RuntimeException(SqlCompilationError.of("duplicate column name '"
                    + SqlIdentifiers.spellCanonical(column.getName()) + "'"));
            }
        }
    }

    /**
     * The table's columns: the query's own, renamed and retyped as the table's column list says.
     *
     * @param table the dynamic table
     * @param body the query's result, of which the columns are read
     * @return the table's shape: its columns and no row
     */
    public static ResultSet shape(final DynamicTable table, final ResultSet body) {
        final List<DynamicTableColumn> declared = table.getDeclaredColumns();
        if (declared.isEmpty()) {
            return body;
        }
        final List<ResultSetColumn> source = body.getColumns();
        if (declared.size() != source.size()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid column definition list"));
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (int i = 0; i < declared.size(); i++) {
            final DynamicTableColumn column = declared.get(i);
            if (column.getType() != null) {
                ColumnTypeFamilies.rejectIncompatible(column.getType(), typeOf(source.get(i)));
                columns.add(new ResultSetColumn(column.getName(), column.getType()));
            } else {
                columns.add(new ResultSetColumn(column.getName(), typeOf(source.get(i))));
            }
        }
        final ResultSet shape = new ResultSet(columns, new ArrayList<Row>());
        shape.setRelationStatistics(body.getRelationStatistics());
        return shape;
    }

    /**
     * The query's rows as the table holds them: each value of a typed column cast to the column's type.
     *
     * @param table the dynamic table
     * @param shape the table's shape, from {@link #shape}
     * @param body the query's result
     * @return the table's rows under its own columns
     */
    public static ResultSet rows(final DynamicTable table, final ResultSet shape, final ResultSet body) {
        final List<DynamicTableColumn> declared = table.getDeclaredColumns();
        if (declared.isEmpty()) {
            return body;
        }
        final List<Row> rows = new ArrayList<Row>();
        for (final Row row : body.getRows()) {
            final List<Object> values = new ArrayList<Object>(row.getValues());
            for (int i = 0; i < declared.size() && i < values.size(); i++) {
                if (declared.get(i).getType() != null) {
                    // The value comes from a query's column, which may hold NULL.
                    values.set(i, ValueCaster.castValue(values.get(i),
                        SqlTypeNames.canonical(declared.get(i).getType()), true));
                }
            }
            rows.add(new Row(values));
        }
        final ResultSet result = new ResultSet(shape.getColumns(), rows);
        result.setRelationStatistics(body.getRelationStatistics());
        return result;
    }

    private static DataType typeOf(final ResultSetColumn column) {
        return column.getStaticType() != null ? column.getStaticType() : column.getDataType();
    }
}
