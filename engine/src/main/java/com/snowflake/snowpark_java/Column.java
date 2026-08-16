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
import dev.frostlake.executor.SqlStringLiterals;

import java.util.Locale;
import java.util.Optional;

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

    /**
     * The SQL text this column carries. Internal: the real {@code Column} publishes no SQL accessor —
     * its {@code toString()} dumps an internal expression tree — so offering one here would let a
     * handler compile against the stub and fail to compile on Snowflake.
     */
    String sql() {
        return sql;
    }

    // --- naming --------------------------------------------------------------------------------

    public Column as(final String alias) {
        return new Column(sql + " AS " + alias);
    }

    public Column alias(final String alias) {
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

    public Column unary_not() {
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
            text.append(values[i].sql());
        }
        return new Column(text.append("))").toString());
    }

    // --- conversion and ordering ---------------------------------------------------------------

    public Column cast(final DataType to) {
        return new Column("CAST(" + sql + " AS " + SnowparkSqlTypes.of(to) + ")");
    }

    // --- the rest of the real Column surface -----------------------------------------------------
    //
    // Signatures measured by reflection on the account, not inferred: a method with the right NAME but
    // the wrong parameter or return type would compile here and fail on Snowflake, which is the exact
    // defect this class was corrected for once already.
    //
    // Four measured members are deliberately absent because they need types Frostlake has no model
    // for: over() / over(WindowSpec) want a WindowSpec, withinGroup(Column...) wants an ordering
    // context, and equal_nan() has no SQL spelling here that was measured. Missing a method is the
    // safe direction — a handler naming one fails to compile, loudly.

    /** {@code BETWEEN}, inclusive on both ends as in SQL. */
    public Column between(final Column lower, final Column upper) {
        return new Column("(" + sql + " BETWEEN " + lower.sql() + " AND " + upper.sql() + ")");
    }

    /** The real Column carries BOTH spellings; {@link #is_null()} is the other one. */
    public Column isNull() {
        return is_null();
    }

    public Column unary_minus() {
        return new Column("(-" + sql + ")");
    }

    /** NULL-safe equality — two NULLs compare equal, unlike {@code =}. */
    public Column equal_null(final Column other) {
        return new Column("EQUAL_NULL(" + sql + ", " + other.sql() + ")");
    }

    public Column asc_nulls_first() {
        return new Column(sql + " ASC NULLS FIRST");
    }

    public Column asc_nulls_last() {
        return new Column(sql + " ASC NULLS LAST");
    }

    public Column desc_nulls_first() {
        return new Column(sql + " DESC NULLS FIRST");
    }

    public Column desc_nulls_last() {
        return new Column(sql + " DESC NULLS LAST");
    }

    public Column bitand(final Column other) {
        return new Column("BITAND(" + sql + ", " + other.sql() + ")");
    }

    public Column bitor(final Column other) {
        return new Column("BITOR(" + sql + ", " + other.sql() + ")");
    }

    public Column bitxor(final Column other) {
        return new Column("BITXOR(" + sql + ", " + other.sql() + ")");
    }

    public Column collate(final String collationSpecification) {
        return new Column("COLLATE(" + sql + ", "
            + SqlStringLiterals.encode(collationSpecification) + ")");
    }

    public Column regexp(final Column pattern) {
        return new Column("(" + sql + " REGEXP " + pattern.sql() + ")");
    }

    /** A field of a semi-structured value: {@code v['name']}. */
    public Column subField(final String field) {
        return new Column(sql + "[" + SqlStringLiterals.encode(field) + "]");
    }

    /** An element of a semi-structured array: {@code v[0]}. */
    public Column subField(final int index) {
        return new Column(sql + "[" + index + "]");
    }

    /**
     * The column's name when it has one — measured to return {@code Optional<String>}.
     *
     * <p>Frostlake answers it from the SQL this column carries: a bare identifier IS its name, and
     * anything composed (an expression, a function call, a literal) has none, which is what the empty
     * Optional says.
     *
     * <p>The name comes back QUOTED and folded — {@code col("name").getName()} is {@code "NAME"},
     * quotes included — because that is what live answers, and it is the resolved identifier rather
     * than the text the caller typed.
     */
    public Optional<String> getName() {
        if (!isPlainIdentifier(sql)) {
            return Optional.<String>empty();
        }
        return Optional.of(sql.charAt(0) == '"' ? sql : "\"" + sql.toUpperCase(Locale.ROOT) + "\"");
    }

    private static boolean isPlainIdentifier(final String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '"') {
                return false;
            }
        }
        return !Character.isDigit(text.charAt(0));
    }

    public Column asc() {
        return new Column(sql + " ASC");
    }

    public Column desc() {
        return new Column(sql + " DESC");
    }

    private Column binary(final String operator, final Column other) {
        return new Column("(" + sql + " " + operator + " " + other.sql() + ")");
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
