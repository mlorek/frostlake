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
import java.util.List;
import java.util.Optional;

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
     * Project the given columns — a PLAN operation, so nothing runs until an action asks for rows.
     * This and {@link #filter} are the paths a handler uses a {@link Column} through; the real API has
     * no way to ask a Column for its SQL, so building one is only ever a means to one of these.
     */
    public DataFrame select(final Column... columns) {
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(columns[i].sql());
        }
        return derived(text.append(" FROM (").append(sql).append(")").toString());
    }

    /** Keep the rows the condition holds for. A plan operation, like {@link #select}. */
    public DataFrame filter(final Column condition) {
        return derived("SELECT * FROM (" + sql + ") WHERE " + condition.sql());
    }

    /** Snowpark's spelling of {@link #filter}; both exist on the real DataFrame. */
    public DataFrame where(final Column condition) {
        return filter(condition);
    }

    // --- the rest of the real DataFrame surface, as plan operations --------------------------------
    //
    // Signatures measured by reflection on the account. Everything here is a PLAN operation, so it
    // submits nothing until an action asks for rows, exactly like select/filter above.
    //
    // Absent on purpose, because each needs a type Frostlake has no model for: groupBy returns a
    // RelationalGroupedDataFrame, join has eleven overloads spanning TableFunction and column maps,
    // and write/na/stat/async return their own builder objects. Missing a method fails a handler at
    // COMPILE time here, which is the loud, safe direction.

    /** {@code ORDER BY}, taking the ordering columns as {@code asc()} / {@code desc_nulls_last()} etc. */
    public DataFrame sort(final Column... columns) {
        final StringBuilder text = new StringBuilder("SELECT * FROM (").append(sql).append(") ORDER BY ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(columns[i].sql());
        }
        return derived(text.toString());
    }

    public DataFrame limit(final int count) {
        return derived("SELECT * FROM (" + sql + ") LIMIT " + count);
    }

    public DataFrame distinct() {
        return derived("SELECT DISTINCT * FROM (" + sql + ")");
    }

    /** Snowflake's {@code SELECT * EXCLUDE (…)} carries this one directly. */
    public DataFrame drop(final String... columnNames) {
        final StringBuilder text = new StringBuilder("SELECT * EXCLUDE (");
        for (int i = 0; i < columnNames.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(columnNames[i]);
        }
        return derived(text.append(") FROM (").append(sql).append(")").toString());
    }

    public DataFrame union(final DataFrame other) {
        return derived("(" + sql + ") UNION (" + other.sql + ")");
    }

    public DataFrame unionAll(final DataFrame other) {
        return derived("(" + sql + ") UNION ALL (" + other.sql + ")");
    }

    public DataFrame except(final DataFrame other) {
        return derived("(" + sql + ") EXCEPT (" + other.sql + ")");
    }

    public DataFrame intersect(final DataFrame other) {
        return derived("(" + sql + ") INTERSECT (" + other.sql + ")");
    }

    public DataFrame crossJoin(final DataFrame other) {
        return derived("SELECT * FROM (" + sql + ") CROSS JOIN (" + other.sql + ")");
    }

    /** Append a computed column, keeping the ones already there. */
    public DataFrame withColumn(final String columnName, final Column value) {
        return derived("SELECT *, " + value.sql() + " AS " + columnName + " FROM (" + sql + ")");
    }

    /** Rename one column, leaving the rest and their order alone — {@code SELECT * RENAME (…)}. */
    public DataFrame rename(final String newName, final Column column) {
        return derived("SELECT * RENAME (" + column.sql() + " AS " + newName + ") FROM (" + sql + ")");
    }

    /** Aggregate the whole frame, with no grouping. */
    public DataFrame agg(final Column... aggregates) {
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int i = 0; i < aggregates.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(aggregates[i].sql());
        }
        return derived(text.append(" FROM (").append(sql).append(")").toString());
    }

    /** A column of this frame, by name. */
    public Column col(final String columnName) {
        return new Column(columnName);
    }

    /** The first row, or empty when the frame has none. An ACTION: it runs the plan. */
    public Optional<Row> first() {
        final Row[] rows = limit(1).collect();
        return rows.length == 0 ? Optional.<Row>empty() : Optional.of(rows[0]);
    }

    /** The first {@code count} rows. An ACTION. */
    public Row[] first(final int count) {
        return limit(count).collect();
    }

    /** A further plan over this one. A frame built over finished rows has no session to plan against. */
    private DataFrame derived(final String derivedSql) {
        if (session == null) {
            throw new UnsupportedOperationException(
                "this DataFrame holds a finished result, not a plan: select/filter need a session");
        }
        return new DataFrame(session, derivedSql);
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

    /**
     * The engine's own result, for Frostlake's plumbing rather than for a handler. Its return type does
     * not exist on Snowflake, so — unlike a method returning only JDK types — no portable handler can
     * be written against it by accident.
     */
    public ResultSet getResultSet() {
        return run();
    }
}
