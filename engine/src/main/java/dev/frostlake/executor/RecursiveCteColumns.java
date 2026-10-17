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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.ColumnNullability;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.RecursiveTermItem;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.ValueRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A recursive CTE's columns, typed from its anchor and its recursive terms together.
 *
 * <p>A column starts at the anchor's type. What the recursive terms compute for it then decides, column by
 * column (live-verified):
 *
 * <pre>
 *   SELECT 1 … SELECT n            the column itself, passed through      NUMBER(1,0)
 *   SELECT 1 … SELECT -n           computed from itself, the same type    NUMBER(1,0)
 *   SELECT 1 … SELECT n + 1        computed from itself, another type     NUMBER(38,0)
 *   SELECT 'a' … SELECT s || 'b'   computed from itself, another length   VARCHAR at its widest
 *   SELECT 1, 2 … SELECT b, a      two numbers computed from each other   NUMBER(38,0), both
 *   SELECT 1 … SELECT 10           computed from nothing of the CTE's     NUMBER(2,0), as a UNION types it
 *   SELECT 1, 1 … SELECT a, a + 1  computed from another column           A NUMBER(1,0), B NUMBER(2,0)
 * </pre>
 *
 * <p>A column computed from itself grows with every step, so it is given its family's widest type at once: a
 * number its 38 digits at the scale the two branches share, a text its full length. A column computed from
 * other columns alone grows only as they do, and is typed as a UNION of the anchor and the term types it once
 * they are settled.
 *
 * <p>A column holds no NULL when neither branch can put one there (see {@link RecursiveTermItem}): the anchor's
 * item and every term's item, the terms read with the CTE's own columns as they come out of this very rule.
 */
final class RecursiveCteColumns {

    private static final int WIDEST_NUMBER = 38;

    private RecursiveCteColumns() {
    }

    /**
     * The CTE's columns.
     *
     * @param anchor      the anchor's columns, already named as the CTE names them
     * @param first       the recursive terms' columns over the anchor's
     * @param anchorItems the anchor's select items, one per column, or null when they do not stand so
     * @param termItems   each term's select items, one per column, or null when some term's do not stand so
     * @param followed    whether every term reads the CTE itself, rather than through a subquery
     * @param qualifiers  the names the CTE goes by in each term's FROM, upper-cased
     * @param shapes      plans the terms over other columns
     * @return the columns the CTE answers with
     */
    static List<ResultSetColumn> resolve(final List<ResultSetColumn> anchor, final List<List<ResultSetColumn>> first,
                                         final List<Expression> anchorItems,
                                         final List<List<Expression>> termItems, final boolean followed,
                                         final List<Set<String>> qualifiers, final RecursiveTermShapes shapes) {
        final int width = anchor.size();
        if (termItems == null || !sameWidth(first, width)) {
            return anchor;
        }
        final String[] names = new String[width];
        for (int k = 0; k < width; k++) {
            names[k] = anchor.get(k).getName().toUpperCase(Locale.ROOT);
        }
        final boolean[][] reach = new boolean[width][width];
        final boolean[] passedThrough = new boolean[width];
        final ColumnNullability unknown = new ColumnNullability() {
            @Override
            public boolean isNotNull(final String column) {
                return false;
            }
        };
        for (int k = 0; k < width; k++) {
            passedThrough[k] = true;
            for (int t = 0; t < termItems.size(); t++) {
                final RecursiveTermItem item = RecursiveTermItem.of(termItems.get(t).get(k), unknown, qualifiers.get(t));
                if (!names[k].equals(item.passedThrough())) {
                    passedThrough[k] = false;
                }
                for (int j = 0; j < width; j++) {
                    if (!followed || item.isOpaque() || item.reads().contains(names[j])) {
                        reach[k][j] = true;
                    }
                }
            }
        }
        close(reach);

        final DataType[] types = new DataType[width];
        for (int k = 0; k < width; k++) {
            types[k] = typeOf(anchor.get(k));
        }
        for (int k = 0; k < width; k++) {
            if (!reach[k][k] || passedThrough[k]) {
                continue;
            }
            DataType union = types[k];
            boolean moved = false;
            for (final List<ResultSetColumn> term : first) {
                final DataType computed = typeOf(term.get(k));
                if (union == null || !sameFamily(union, computed)) {
                    union = null;
                    break;
                }
                moved = moved || !sameType(types[k], computed);
                union = union(union, computed);
            }
            if (union != null && (moved || followed && isFixed(types[k]) && sharesACycle(reach, k))) {
                types[k] = widest(union);
            }
        }
        // A column computed from other columns alone is typed once they are, however long the chain.
        for (int pass = 0; pass <= width; pass++) {
            final List<List<ResultSetColumn>> settled = shapes.over(typed(anchor, types, null));
            boolean moved = false;
            if (sameWidth(settled, width)) {
                for (int k = 0; k < width; k++) {
                    if (reach[k][k]) {
                        continue;
                    }
                    DataType union = isUntypedNull(typeOf(anchor.get(k))) ? null : typeOf(anchor.get(k));
                    boolean typed = true;
                    for (final List<ResultSetColumn> term : settled) {
                        final DataType computed = typeOf(term.get(k));
                        if (union == null || computed instanceof VariantType) {
                            // A bare NULL takes the term's type, and a VARIANT takes any other in.
                            union = computed;
                        } else if (union instanceof VariantType) {
                            continue;
                        } else if (sameFamily(union, computed)) {
                            union = union(union, computed);
                        } else {
                            typed = false;
                        }
                    }
                    if (typed && union != null && !sameType(union, types[k])) {
                        types[k] = union;
                        moved = true;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return typed(anchor, types, notNull(anchor, first, anchorItems, termItems, followed, qualifiers, names));
    }

    /**
     * The columns with each one a fixed-point anchor and a BOOLEAN term meet in declared BOOLEAN, as DESCRIBE reports
     * it (live-verified); reading the CTE refuses the pair as a type mismatch (see {@link #mismatched}).
     *
     * @param columns the columns as resolved
     * @param anchor  the anchor's columns
     * @param first   the recursive terms' columns over the anchor's
     * @return the columns, a BOOLEAN where such a pair meets
     */
    static List<ResultSetColumn> booleanTerms(final List<ResultSetColumn> columns, final List<ResultSetColumn> anchor,
                                              final List<List<ResultSetColumn>> first) {
        final List<ResultSetColumn> typed = new ArrayList<>(columns);
        for (int k = 0; k < typed.size() && k < anchor.size(); k++) {
            if (anchor.get(k).getStaticType() == null || !isFixed(typeOf(anchor.get(k)))) {
                continue;
            }
            for (final List<ResultSetColumn> term : first) {
                if (k < term.size() && term.get(k).getStaticType() instanceof BooleanType) {
                    final ResultSetColumn column = typed.get(k);
                    typed.set(k, new ResultSetColumn(column.getName(), BooleanType.BOOLEAN, column.getTableName(),
                        BooleanType.BOOLEAN, true, false));
                    break;
                }
            }
        }
        return typed;
    }

    /**
     * A step's row with each value of another family than its column's converted to the column's type: a DATE
     * column holds the date of a timestamp a term computes, a timestamp column the timestamp of a date, a FLOAT
     * column the double of a number (live-verified).
     *
     * @param row     the row a recursive term answered
     * @param columns the CTE's columns
     * @return the row, its values converted where their family differs
     */
    static Row settled(final Row row, final List<ResultSetColumn> columns) {
        List<Object> values = null;
        for (int k = 0; k < columns.size() && k < row.getValues().size(); k++) {
            final Object value = row.getValue(k);
            final DataType type = typeOf(columns.get(k));
            if (value != null && ofAnotherFamily(type, value)) {
                if (values == null) {
                    values = new ArrayList<>(row.getValues());
                }
                values.set(k, ValueCaster.castValue(value, SqlTypeNames.canonical(type), true));
            }
        }
        return values == null ? row : new Row(values);
    }

    private static boolean ofAnotherFamily(final DataType type, final Object value) {
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            return "DATE".equals(name) ? value instanceof LocalDateTime
                : name.startsWith("TIMESTAMP") && value instanceof LocalDate;
        }
        return isFloat(type) && value instanceof Number && !(value instanceof Double);
    }

    /**
     * The first column whose anchor and recursive term are of families the account does not bring together: a
     * number against a float it would have to round, a number against a text, either against a boolean.
     *
     * @param anchor the anchor's columns
     * @param first  the recursive terms' columns over the anchor's
     * @return the column's name, or null when every column's branches agree
     */
    static String mismatched(final List<ResultSetColumn> anchor, final List<List<ResultSetColumn>> first) {
        for (final List<ResultSetColumn> term : first) {
            for (int k = 0; k < anchor.size() && k < term.size(); k++) {
                final DataType kept = typeOf(anchor.get(k));
                final DataType computed = typeOf(term.get(k));
                // Only what both branches declare is judged: a type the plan merely assumed proves nothing.
                if (anchor.get(k).getStaticType() == null || term.get(k).getStaticType() == null
                        || isUntypedNull(kept) || isUntypedNull(computed)) {
                    continue;
                }
                final boolean refused = isFixed(kept) && (isFloat(computed) || computed instanceof StringType
                        || computed instanceof BooleanType)
                    || kept instanceof StringType && isFixed(computed)
                    || kept instanceof BooleanType && isFixed(computed);
                if (refused) {
                    return anchor.get(k).getName();
                }
            }
        }
        return null;
    }

    /** Which columns hold no NULL: the anchor's item holds none, and no term's item can put one there. */
    private static boolean[] notNull(final List<ResultSetColumn> anchor, final List<List<ResultSetColumn>> first,
                                     final List<Expression> anchorItems, final List<List<Expression>> termItems,
                                     final boolean followed, final List<Set<String>> qualifiers,
                                     final String[] names) {
        final int width = anchor.size();
        final boolean[] holdsNone = new boolean[width];
        final ColumnNullability unknown = new ColumnNullability() {
            @Override
            public boolean isNotNull(final String column) {
                return false;
            }
        };
        for (int k = 0; k < width; k++) {
            final RecursiveTermItem item = anchorItems == null ? null
                : RecursiveTermItem.of(anchorItems.get(k), unknown, Collections.<String>emptySet());
            // A column the anchor passes through holds what its own source says it holds.
            holdsNone[k] = item == null || item.passedThrough() != null || item.passesForeignColumn()
                ? !anchor.get(k).isNullable() : item.isNotNull();
        }
        final ColumnNullability settled = new ColumnNullability() {
            @Override
            public boolean isNotNull(final String column) {
                for (int k = 0; k < width; k++) {
                    if (names[k].equals(column)) {
                        return holdsNone[k];
                    }
                }
                return false;
            }
        };
        for (int pass = 0; pass <= width; pass++) {
            boolean moved = false;
            for (int k = 0; k < width; k++) {
                for (int t = 0; holdsNone[k] && t < termItems.size(); t++) {
                    final RecursiveTermItem item =
                        RecursiveTermItem.of(termItems.get(t).get(k), settled, qualifiers.get(t));
                    final String passed = item.passedThrough();
                    final boolean none;
                    if (!followed) {
                        none = (passed != null || item.passesForeignColumn()) && !first.get(t).get(k).isNullable();
                    } else if (item.passesForeignColumn() || passed != null && !isNamed(names, passed)) {
                        // A column of another relation, passed through: what its own source says of it.
                        none = !first.get(t).get(k).isNullable();
                    } else {
                        none = item.isNotNull();
                    }
                    if (!none) {
                        holdsNone[k] = false;
                        moved = true;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return holdsNone;
    }

    private static boolean isNamed(final String[] names, final String column) {
        for (final String name : names) {
            if (name.equals(column)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameWidth(final List<List<ResultSetColumn>> terms, final int width) {
        if (terms == null || terms.isEmpty()) {
            return false;
        }
        for (final List<ResultSetColumn> term : terms) {
            if (term == null || term.size() != width) {
                return false;
            }
        }
        return true;
    }

    /** Make {@code reach} say which columns each column is computed from, however indirectly. */
    private static void close(final boolean[][] reach) {
        final int width = reach.length;
        for (int via = 0; via < width; via++) {
            for (int from = 0; from < width; from++) {
                for (int to = 0; to < width; to++) {
                    if (reach[from][via] && reach[via][to]) {
                        reach[from][to] = true;
                    }
                }
            }
        }
    }

    /** Whether a column and some other column are each computed from the other. */
    private static boolean sharesACycle(final boolean[][] reach, final int column) {
        for (int other = 0; other < reach.length; other++) {
            if (other != column && reach[column][other] && reach[other][column]) {
                return true;
            }
        }
        return false;
    }

    private static List<ResultSetColumn> typed(final List<ResultSetColumn> anchor, final DataType[] types,
                                               final boolean[] holdsNone) {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>(anchor.size());
        for (int k = 0; k < anchor.size(); k++) {
            final ResultSetColumn kept = anchor.get(k);
            final boolean retyped = !sameType(types[k], typeOf(kept));
            final boolean nullable = holdsNone == null ? kept.isNullable() : !holdsNone[k];
            // The column's values come out of the recursion, so it is no longer the constant the anchor wrote: a
            // number's interval is its type's widest, whatever the anchor's was, and the literal's spelling is gone.
            final ValueRange interval = isFixed(types[k]) ? ValueRange.WIDEST.withNullable(nullable) : null;
            ResultSetColumn column = new ResultSetColumn(kept.getName(), retyped ? types[k] : kept.getDataType(),
                kept.getTableName(), retyped ? types[k] : kept.getStaticType(), nullable,
                nullable ? kept.isNullabilityKnown() : true, interval, null);
            if (kept.getCollation() != null) {
                column = column.withCollation(kept.getCollation(), kept.isCollationExplicit());
            }
            if (!retyped && kept.isUncheckedConstant()) {
                column = column.withUncheckedConstant();
            }
            if (!retyped && kept.isFoldedDouble()) {
                column = column.withFoldedDouble();
            }
            if (kept.getProjectionPolicyName() != null) {
                column = column.withProjectionPolicy(kept.getProjectionPolicyName());
            }
            columns.add(column);
        }
        return columns;
    }

    private static DataType typeOf(final ResultSetColumn column) {
        return column.getStaticType() != null ? column.getStaticType() : column.getDataType();
    }

    private static boolean isFixed(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }

    private static boolean isFloat(final DataType type) {
        return type instanceof NumericType && NumericType.isApproximate(type);
    }

    /** A bare NULL's type: a text of no length, which every other type takes over. */
    private static boolean isUntypedNull(final DataType type) {
        return type instanceof StringType && ((StringType) type).getMaxLength() == 0;
    }

    private static boolean sameFamily(final DataType left, final DataType right) {
        return isFixed(left) && isFixed(right) || left instanceof StringType && right instanceof StringType;
    }

    private static boolean sameType(final DataType left, final DataType right) {
        if (isFixed(left) && isFixed(right)) {
            return ((NumericType) left).getPrecision() == ((NumericType) right).getPrecision()
                && ((NumericType) left).getScale() == ((NumericType) right).getScale();
        }
        if (left instanceof StringType && right instanceof StringType) {
            return ((StringType) left).getMaxLength() == ((StringType) right).getMaxLength();
        }
        return left == right || left != null && right != null && left.getName().equals(right.getName());
    }

    private static DataType union(final DataType left, final DataType right) {
        if (isFixed(left) && isFixed(right)) {
            return DeclaredTypeFold.numericSupertype((NumericType) left, (NumericType) right);
        }
        return ((StringType) left).getMaxLength() >= ((StringType) right).getMaxLength() ? left : right;
    }

    private static DataType widest(final DataType type) {
        if (isFixed(type)) {
            return new NumericType("NUMBER", WIDEST_NUMBER, ((NumericType) type).getScale());
        }
        return new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
    }
}
