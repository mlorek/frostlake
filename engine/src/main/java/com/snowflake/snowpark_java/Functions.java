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

import dev.frostlake.executor.SqlStringLiterals;

/**
 * The static column factories a handler imports as {@code import static
 * com.snowflake.snowpark_java.Functions.*;}.
 *
 * <p>Every one builds a {@link Column} over the SQL Frostlake would run for it, so the set is open-ended
 * by construction: anything named here is spelled out, and anything not named is still reachable through
 * {@link #callBuiltin} or {@link #sqlExpr}. That is deliberate — Snowpark's own Functions class has
 * hundreds of entries, and mirroring it exhaustively would be a list to maintain rather than behaviour
 * to get right.
 */
public final class Functions {

    private Functions() {
    }

    // --- references and literals ---------------------------------------------------------------

    public static Column col(final String name) {
        return new Column(name);
    }

    public static Column column(final String name) {
        return new Column(name);
    }

    /** Raw SQL, passed through untouched. */
    public static Column sqlExpr(final String sql) {
        return new Column(sql);
    }

    /**
     * A literal. Strings are quoted the way the engine's own decoder expects, so an apostrophe in the
     * value cannot end the literal early.
     */
    public static Column lit(final Object value) {
        if (value == null) {
            return new Column("NULL");
        }
        if (value instanceof Column) {
            return (Column) value;
        }
        if (value instanceof Boolean) {
            return new Column(((Boolean) value).booleanValue() ? "TRUE" : "FALSE");
        }
        if (value instanceof Number) {
            return new Column(value.toString());
        }
        return new Column(SqlStringLiterals.encode(String.valueOf(value)));
    }

    // --- aggregates ----------------------------------------------------------------------------

    public static Column count(final Column column) {
        return callBuiltin("COUNT", column);
    }

    public static Column sum(final Column column) {
        return callBuiltin("SUM", column);
    }

    public static Column avg(final Column column) {
        return callBuiltin("AVG", column);
    }

    public static Column mean(final Column column) {
        return avg(column);
    }

    public static Column min(final Column column) {
        return callBuiltin("MIN", column);
    }

    public static Column max(final Column column) {
        return callBuiltin("MAX", column);
    }

    public static Column stddev(final Column column) {
        return callBuiltin("STDDEV", column);
    }

    public static Column count_distinct(final Column... columns) {
        final StringBuilder text = new StringBuilder("COUNT(DISTINCT ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(columns[i].sql());
        }
        return new Column(text.append(")").toString());
    }

    // --- strings -------------------------------------------------------------------------------

    public static Column upper(final Column column) {
        return callBuiltin("UPPER", column);
    }

    public static Column lower(final Column column) {
        return callBuiltin("LOWER", column);
    }

    public static Column trim(final Column column) {
        return callBuiltin("TRIM", column);
    }

    public static Column length(final Column column) {
        return callBuiltin("LENGTH", column);
    }

    public static Column concat(final Column... columns) {
        return callBuiltin("CONCAT", columns);
    }

    public static Column substring(final Column column, final Column start, final Column count) {
        return callBuiltin("SUBSTRING", column, start, count);
    }

    // --- numbers and null handling -------------------------------------------------------------

    public static Column abs(final Column column) {
        return callBuiltin("ABS", column);
    }

    public static Column round(final Column column) {
        return callBuiltin("ROUND", column);
    }

    public static Column floor(final Column column) {
        return callBuiltin("FLOOR", column);
    }

    public static Column ceil(final Column column) {
        return callBuiltin("CEIL", column);
    }

    public static Column coalesce(final Column... columns) {
        return callBuiltin("COALESCE", columns);
    }

    public static Column iff(final Column condition, final Column whenTrue, final Column whenFalse) {
        return callBuiltin("IFF", condition, whenTrue, whenFalse);
    }

    // --- dates ---------------------------------------------------------------------------------

    public static Column current_date() {
        return new Column("CURRENT_DATE()");
    }

    public static Column current_timestamp() {
        return new Column("CURRENT_TIMESTAMP()");
    }

    public static Column to_date(final Column column) {
        return callBuiltin("TO_DATE", column);
    }

    // --- the escape hatches --------------------------------------------------------------------

    /** Any built-in by name, so a function this class does not spell out is still reachable. */
    public static Column callBuiltin(final String functionName, final Column... arguments) {
        final StringBuilder text = new StringBuilder(functionName).append("(");
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(arguments[i].sql());
        }
        return new Column(text.append(")").toString());
    }

    /** A user-defined function by name. Identical in shape to {@link #callBuiltin}; kept for the API. */
    public static Column callUDF(final String udfName, final Column... arguments) {
        return callBuiltin(udfName, arguments);
    }
}
