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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A result a statement read by RESULT_SCAN, as the write-ahead log keeps it with the statement's record: each
 * column's name, types, nullability and collation, and each row's values (see {@link StatementResults}).
 */
public final class RecordedResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ArrayList<String> names = new ArrayList<String>();
    private final ArrayList<DataType> types = new ArrayList<DataType>();
    private final ArrayList<String> tables = new ArrayList<String>();
    private final ArrayList<DataType> staticTypes = new ArrayList<DataType>();
    private final ArrayList<Boolean> nullable = new ArrayList<Boolean>();
    private final ArrayList<Boolean> nullabilityKnown = new ArrayList<Boolean>();
    private final ArrayList<String> collations = new ArrayList<String>();
    private final ArrayList<Boolean> collationsExplicit = new ArrayList<Boolean>();
    private final ArrayList<ArrayList<Object>> rows = new ArrayList<ArrayList<Object>>();

    private RecordedResult() {
    }

    /**
     * A result as a statement read it, its rows copied.
     *
     * @param result the result
     * @return its record
     */
    public static RecordedResult of(final ResultSet result) {
        final RecordedResult recorded = new RecordedResult();
        for (final ResultSetColumn column : result.getColumns()) {
            recorded.names.add(column.getName());
            recorded.types.add(column.getDataType());
            recorded.tables.add(column.getTableName());
            recorded.staticTypes.add(column.getStaticType());
            recorded.nullable.add(Boolean.valueOf(column.isNullable()));
            recorded.nullabilityKnown.add(Boolean.valueOf(column.isNullabilityKnown()));
            recorded.collations.add(column.getCollation());
            recorded.collationsExplicit.add(Boolean.valueOf(column.isCollationExplicit()));
        }
        for (final Row row : result.getRows()) {
            recorded.rows.add(new ArrayList<Object>(row.getValues()));
        }
        return recorded;
    }

    /**
     * The result again, as the statement first read it.
     *
     * @return a result of its own, its rows copied
     */
    public ResultSet toResultSet() {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (int i = 0; i < names.size(); i++) {
            final ResultSetColumn column = new ResultSetColumn(names.get(i), types.get(i), tables.get(i),
                staticTypes.get(i), nullable.get(i).booleanValue(), nullabilityKnown.get(i).booleanValue());
            columns.add(collations.get(i) == null ? column
                : column.withCollation(collations.get(i), collationsExplicit.get(i).booleanValue()));
        }
        final List<Row> copied = new ArrayList<Row>();
        for (final List<Object> values : rows) {
            copied.add(new Row(new ArrayList<Object>(values)));
        }
        return new ResultSet(columns, copied);
    }
}
