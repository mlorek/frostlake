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

/**
 * A column expression, carried as the SQL it stands for.
 *
 * <p>Snowpark builds an expression tree and renders it when the plan runs; the stub renders eagerly and
 * keeps the text, which is the same thing for every purpose a handler can observe — {@code col("a").gt(lit(1))}
 * is {@code (A > 1)} either way. Operators parenthesise their operands so precedence survives nesting.
 *
 * <p>Each operator has a symbolic spelling and the word spelling Snowpark gives it (Java has no operator
 * overloading, so {@code equal_to} and {@code gt} are the real API, not a convenience).
 */
public class Column {

    private final String sql;

    public Column(final String sql) {
        this.sql = sql;
    }

    /** The SQL this column stands for. */
    public String getSql() {
        return sql;
    }

    // --- naming --------------------------------------------------------------------------------

    public Column as(final String alias) {
        return new Column(sql + " AS " + alias);
    }

    public Column alias(final String alias) {
        return as(alias);
    }

    public Column name(final String alias) {
        return as(alias);
    }

    // --- arithmetic ----------------------------------------------------------------------------

    public Column plus(final Column other) {
        return binary("+", other);
    }

    public Column minus(final Column other) {
        return binary("-", other);
    }

    public Column multiply(final Column other) {
        return binary("*", other);
    }

    public Column divide(final Column other) {
        return binary("/", other);
    }

    public Column mod(final Column other) {
        return binary("%", other);
    }

    // --- comparison ----------------------------------------------------------------------------

    public Column equal_to(final Column other) {
        return binary("=", other);
    }

    public Column not_equal(final Column other) {
        return binary("!=", other);
    }

    public Column gt(final Column other) {
        return binary(">", other);
    }

    public Column lt(final Column other) {
        return binary("<", other);
    }

    public Column geq(final Column other) {
        return binary(">=", other);
    }

    public Column leq(final Column other) {
        return binary("<=", other);
    }

    // --- logic ---------------------------------------------------------------------------------

    public Column and(final Column other) {
        return binary("AND", other);
    }

    public Column or(final Column other) {
        return binary("OR", other);
    }

    public Column not() {
        return new Column("(NOT " + sql + ")");
    }

    public Column is_null() {
        return new Column("(" + sql + " IS NULL)");
    }

    public Column is_not_null() {
        return new Column("(" + sql + " IS NOT NULL)");
    }

    public Column like(final Column pattern) {
        return binary("LIKE", pattern);
    }

    public Column in(final Column... values) {
        final StringBuilder text = new StringBuilder("(" + sql + " IN (");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(values[i].getSql());
        }
        return new Column(text.append("))").toString());
    }

    // --- conversion and ordering ---------------------------------------------------------------

    public Column cast(final DataType to) {
        return new Column("CAST(" + sql + " AS " + to.sqlTypeName() + ")");
    }

    public Column asc() {
        return new Column(sql + " ASC");
    }

    public Column desc() {
        return new Column(sql + " DESC");
    }

    private Column binary(final String operator, final Column other) {
        return new Column("(" + sql + " " + operator + " " + other.getSql() + ")");
    }

    @Override
    public String toString() {
        return sql;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Column && sql.equals(((Column) other).sql);
    }

    @Override
    public int hashCode() {
        return sql.hashCode();
    }
}
