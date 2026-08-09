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

package com.snowflake.snowpark_java;

import com.snowflake.snowpark_java.types.DataType;
import com.snowflake.snowpark_java.types.DataTypes;
import com.snowflake.snowpark_java.types.StructField;
import com.snowflake.snowpark_java.types.StructType;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.TypeCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Snowpark DataFrame: a PLAN, not a result.
 *
 * <p>{@code session.sql(...)} submits nothing — the statement runs when an action asks for its rows, and
 * runs AGAIN for each further action. Measured on live: a handler that calls {@code collect()} twice on
 * {@code session.sql("INSERT INTO sink VALUES (1)")} leaves two rows, and {@code count()} counts as an
 * action alongside {@code collect()}. A handler that never calls an action submits nothing at all, which
 * is why the same body that appears to create a temporary table under owner's rights is silently a no-op
 * there. Executing inside {@code sql()} — as this stub used to — made every such handler do real work
 * here and nothing on Snowflake, a divergence that hides rather than fails.
 */
public class DataFrame {

    /** The session to run the plan through, or null when this frame was built over a finished result. */
    private final Session session;
    private final String sql;
    private final ResultSet materialised;

    /** A plan. Nothing runs until an action. */
    DataFrame(final Session session, final String sql) {
        this.session = session;
        this.sql = sql;
        this.materialised = null;
    }

    /** A frame over rows that have already been produced. */
    public DataFrame(final ResultSet resultSet) {
        this.session = null;
        this.sql = null;
        this.materialised = resultSet;
    }

    /**
     * Run the plan. Deliberately NOT memoised: Snowpark re-executes on every action, and a handler that
     * collects twice sees the statement happen twice.
     */
    private ResultSet run() {
        return materialised != null ? materialised : session.runPlan(sql);
    }

    /**
     * The rows, as Snowpark's {@link Row} rather than the engine's own — a handler calls
     * {@code rows[0].getString(0)}, which the engine's row has no method for.
     */
    public Row[] collect() {
        final List<dev.frostlake.storage.Row> source = run().getRows();
        final Row[] out = new Row[source.size()];
        for (int i = 0; i < source.size(); i++) {
            out[i] = new Row(source.get(i).getValues().toArray());
        }
        return out;
    }

    /** The column names and types, as Snowpark describes them. */
    public StructType schema() {
        final List<StructField> fields = new ArrayList<StructField>();
        for (final ResultSetColumn column : run().getColumns()) {
            fields.add(new StructField(column.getName(), snowparkType(column), true));
        }
        return new StructType(fields);
    }

    /**
     * Map a Frostlake column type onto the Snowpark type that names it.
     *
     * <p>The engine's own {@code DataType} and Snowpark's share a simple name, so this is the one place
     * the fully-qualified engine type is spelled out rather than imported.
     */
    private static DataType snowparkType(final ResultSetColumn column) {
        final dev.frostlake.types.DataType type =
            column.getStaticType() != null ? column.getStaticType() : column.getDataType();
        if (type == null) {
            return DataTypes.StringType;
        }
        final TypeCategory category = type.getCategory();
        if (category == TypeCategory.NUMERIC) {
            if (type instanceof NumericType) {
                final NumericType numeric = (NumericType) type;
                // A scale-free NUMBER is what Snowpark calls a Long; anything scaled stays a Decimal.
                if (numeric.getScale() == 0) {
                    return DataTypes.LongType;
                }
                return DataTypes.createDecimalType(numeric.getPrecision(), numeric.getScale());
            }
            return DataTypes.DoubleType;
        }
        if (category == TypeCategory.BOOLEAN) {
            return DataTypes.BooleanType;
        }
        if (category == TypeCategory.BINARY) {
            return DataTypes.BinaryType;
        }
        if (category == TypeCategory.DATE_TIME) {
            final String name = type.toString().toUpperCase();
            if (name.startsWith("DATE")) {
                return DataTypes.DateType;
            }
            if (name.startsWith("TIME") && !name.startsWith("TIMESTAMP")) {
                return DataTypes.TimeType;
            }
            return DataTypes.TimestampType;
        }
        if (category == TypeCategory.SEMI_STRUCTURED) {
            return DataTypes.VariantType;
        }
        if (category == TypeCategory.GEOSPATIAL) {
            return DataTypes.GeographyType;
        }
        return DataTypes.StringType;
    }

    public long count() {
        return run().getRowCount();
    }

    public List<Map<String, Object>> toMapList() {
        // One run for the whole action: rows and columns must come from the SAME execution, and running
        // the plan per row would submit the statement once for every row it returned.
        final ResultSet executed = run();
        final List<ResultSetColumn> columns = executed.getColumns();
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final dev.frostlake.storage.Row row : executed.getRows()) {
            final Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                map.put(columns.get(i).getName(), row.getValue(i));
            }
            result.add(map);
        }
        return result;
    }

    public ResultSet getResultSet() {
        return run();
    }
}
