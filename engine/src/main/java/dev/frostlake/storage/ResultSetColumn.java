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
import dev.frostlake.values.ValueRange;

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
    // Whether this column is known to accept NULL. True unless a projection said otherwise: Snowflake
    // carries a source column's NOT NULL out through a projection ONLY when the item is a column
    // reference, so nullable is the answer for every expression, aggregate, literal and set operation,
    // and for the null-extended side of an outer join.
    private final boolean nullable;
    // Whether the answer above is KNOWN or merely the default. The two metadata surfaces need
    // different halves of this: INFORMATION_SCHEMA and DESCRIBE report an expression column nullable,
    // while the JDBC driver reports columnNoNulls for it and columnNullable only for a column it knows
    // accepts NULL (live-verified). A boolean alone cannot tell "nullable because we looked" from
    // "nullable because we did not".
    private final boolean nullabilityKnown;
    // The interval the column's values lie in, propagated from the projection the way the account
    // propagates statistics, or null when none is known. Read only by the storage tag SYSTEM$TYPEOF
    // prints through a derived relation; never by a value path.
    private final ValueRange valueRange;
    // The NUMBER the column's values spell when the projection is a bare string literal, or a column
    // carrying one out of a derived relation; null for every other column. Read only by the conditional
    // fold, through a derived relation built from this result set.
    private final DataType spelledNumber;
    // The collation this column's values compare, sort and group under, or null for none. A projection
    // stamps it from the expression it projects, so a derived relation, a view and a CTAS all carry the
    // collation out to whoever reads the column next.
    private final String collation;
    // Whether the projection is a number or boolean constant wrapped into a VARIANT, or a column carrying
    // one out of a derived relation — the value a cast to a sized VARCHAR converts unchecked. Read only by
    // that cast, through a derived relation or a scalar subquery built from this result set.
    private final boolean uncheckedConstant;
    // Whether the projection is a double live's compiler folds, or a column carrying one out of a derived
    // relation — which TO_VARIANT wraps keeping its FLOAT origin. Read only by that wrap.
    private final boolean foldedDouble;

    public ResultSetColumn(final String name, final DataType dataType) {
        this(name, dataType, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName) {
        this(name, dataType, tableName, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType) {
        this(name, dataType, tableName, staticType, true);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType, final boolean nullable) {
        // A NOT NULL answer is only ever reached by looking, so it is known by construction; a
        // nullable one has to say whether it was.
        this(name, dataType, tableName, staticType, nullable, !nullable);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType, final boolean nullable,
                           final boolean nullabilityKnown) {
        this(name, dataType, tableName, staticType, nullable, nullabilityKnown, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType, final boolean nullable,
                           final boolean nullabilityKnown, final ValueRange valueRange) {
        this(name, dataType, tableName, staticType, nullable, nullabilityKnown, valueRange, null);
    }

    public ResultSetColumn(final String name, final DataType dataType, final String tableName,
                           final DataType staticType, final boolean nullable,
                           final boolean nullabilityKnown, final ValueRange valueRange,
                           final DataType spelledNumber) {
        this(name, dataType, tableName, staticType, nullable, nullabilityKnown, valueRange, spelledNumber, null,
            false, false);
    }

    private ResultSetColumn(final String name, final DataType dataType, final String tableName,
                            final DataType staticType, final boolean nullable,
                            final boolean nullabilityKnown, final ValueRange valueRange,
                            final DataType spelledNumber, final String collation,
                            final boolean uncheckedConstant, final boolean foldedDouble) {
        this.name = name;
        this.dataType = dataType;
        this.tableName = tableName;
        this.staticType = staticType;
        this.nullable = nullable;
        this.nullabilityKnown = nullabilityKnown;
        this.valueRange = valueRange;
        this.spelledNumber = spelledNumber;
        this.collation = collation;
        this.uncheckedConstant = uncheckedConstant;
        this.foldedDouble = foldedDouble;
    }

    /**
     * This column carrying a collation — what a projection stamps when the expression it projects has
     * one, so the derived relation, view or table built from this result set collates the column too.
     *
     * @param spec the collation specification, lower-cased
     * @return a copy of this column carrying it
     */
    public ResultSetColumn withCollation(final String spec) {
        return new ResultSetColumn(name, dataType, tableName, staticType, nullable, nullabilityKnown,
            valueRange, spelledNumber, spec, uncheckedConstant, foldedDouble);
    }

    /**
     * This column marked as projecting a constant wrapped into a VARIANT, which a cast to a sized VARCHAR
     * converts unchecked through the derived relation or scalar subquery built from this result set.
     *
     * @return a copy of this column so marked
     */
    public ResultSetColumn withUncheckedConstant() {
        return new ResultSetColumn(name, dataType, tableName, staticType, nullable, nullabilityKnown,
            valueRange, spelledNumber, collation, true, foldedDouble);
    }

    /**
     * This column marked as projecting a double live's compiler folds, which TO_VARIANT wraps keeping its
     * FLOAT origin through the derived relation or scalar subquery built from this result set.
     *
     * @return a copy of this column so marked
     */
    public ResultSetColumn withFoldedDouble() {
        return new ResultSetColumn(name, dataType, tableName, staticType, nullable, nullabilityKnown,
            valueRange, spelledNumber, collation, uncheckedConstant, true);
    }

    /**
     * Whether this column projects a double live's compiler folds (see {@link #withFoldedDouble}).
     *
     * @return true for such a column
     */
    public boolean isFoldedDouble() {
        return foldedDouble;
    }

    /**
     * Whether this column projects a constant wrapped into a VARIANT (see {@link #withUncheckedConstant}).
     *
     * @return true for such a column
     */
    public boolean isUncheckedConstant() {
        return uncheckedConstant;
    }

    /**
     * The collation this column's values compare under, or null when it carries none.
     *
     * @return the specification, lower-cased
     */
    public String getCollation() {
        return collation;
    }

    /** The interval this column's values lie in, or null when none is known. */
    public ValueRange getValueRange() {
        return valueRange;
    }

    /** The NUMBER this column's values spell, or null — see TableColumn#getSpelledNumber. */
    public DataType getSpelledNumber() {
        return spelledNumber;
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

    /**
     * Whether this column accepts NULL. Only a projected COLUMN REFERENCE can answer false, and only
     * when its source column is NOT NULL — an expression over that same column, a cast of it, an
     * aggregate, a literal or either side of a set operation all accept NULL (live-verified).
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Whether {@link #isNullable()} was determined rather than defaulted. Only a projected COLUMN
     * REFERENCE determines it — an expression, an aggregate and a literal all leave it unknown, which
     * is exactly the distinction the JDBC driver reports and the catalog surfaces do not.
     */
    public boolean isNullabilityKnown() {
        return nullabilityKnown;
    }
}
