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

import dev.frostlake.executor.expressions.CollatedKey;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.FoldedValues;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.UuidType;
import dev.frostlake.values.ApproximateValues;
import dev.frostlake.values.HexDoubleText;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stateless set-operation row math — UNION / INTERSECT / EXCEPT (ALL and distinct variants) and DISTINCT —
 * extracted from {@link QueryExecutor}. Pure {@code List<Row>} transforms; dedup uses a hash key on the
 * row's values (O(n) rather than O(n^2)).
 *
 * <p>Row keys are built through per-column {@link SetOpColumnCoercion} normalizers, because Snowflake
 * unifies the column types of set-operation branches before comparing: a VARCHAR branch against a
 * TIMESTAMP branch coerces to TIMESTAMP ({@code '2025-09-29 14:49:57.461'} matches the timestamp value),
 * a VARCHAR branch against a NUMBER branch coerces to NUMBER, and NUMBER branches of different scales
 * compare by value ({@code 1.0} matches {@code 1}). The coercion for a column engages only when its
 * values actually mix families across the two inputs — two VARCHAR branches stay text ({@code '01'}
 * remains distinct from {@code '1'}) — and numeric normalization is exact {@link BigDecimal}, never a
 * double, so 20-digit NUMBER(38,0) keys that differ only in their low digits are never merged.
 */
public final class SetOperations {

    /**
     * Apply UNION operation. The distinct variant deduplicates via a hash set on normalized row keys,
     * turning the former O(n^2) scan into O(n).
     */
    public static List<Row> applyUnion(final List<Row> leftRows, final List<Row> rightRowsRaw, final boolean all) {
        return applyUnion(leftRows, rightRowsRaw, all, null);
    }

    /**
     * Apply UNION, with the LEADING branch's column layout available so an empty leading branch still
     * types the union. Snowflake unifies branch types from the branch's declared column TYPE, not from
     * the rows it happens to produce, so {@code SELECT ts FROM t WHERE FALSE UNION ALL SELECT '…'} still
     * converts the string branch to TIMESTAMP.
     */
    public static List<Row> applyUnion(final List<Row> leftRows, final List<Row> rightRowsRaw,
                                       final boolean all, final List<ResultSetColumn> leadingColumns) {
        return applyUnion(leftRows, rightRowsRaw, all, leadingColumns, null);
    }

    /**
     * Apply UNION where some columns compare under a collation: rows equal under it are ONE row, and the row
     * reports the smallest of the values that folded into it by raw text, from either branch (live-verified).
     *
     * @param collations the collation of each column, null entries for the columns with none; null for none
     */
    public static List<Row> applyUnion(final List<Row> leftRows, final List<Row> rightRowsRaw,
                                       final boolean all, final List<ResultSetColumn> leadingColumns,
                                       final CollationSpec[] collations) {
        final List<Row> rightRows = coerceToFirstBranchTypes(leftRows, rightRowsRaw, leadingColumns);
        if (!all && anyCollation(collations)) {
            final List<Row> both = new ArrayList<>(leftRows.size() + rightRows.size());
            both.addAll(leftRows);
            both.addAll(rightRows);
            return foldCollated(both, columnCoercions(leftRows, rightRows), collations, null, false);
        }
        if (all) {
            // UNION ALL: keep all rows
            final List<Row> result = new ArrayList<>(leftRows);
            result.addAll(rightRows);
            return result;
        }

        // UNION: distinct rows — left in order, then rows from right not already present.
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        final List<Row> result = new ArrayList<>();
        final Set<List<Object>> seen = new HashSet<>();
        for (final Row row : leftRows) {
            if (seen.add(rowKey(row, coercions))) {
                result.add(row);
            }
        }
        for (final Row row : rightRows) {
            if (seen.add(rowKey(row, coercions))) {
                result.add(row);
            }
        }
        return result;
    }


    /**
     * A set operation's column type comes from its FIRST branch, and later branches' VALUES are
     * converted to it — not merely compared against it. Live-verified on a real account:
     * {@code SELECT 1, True AS c UNION ALL SELECT 5, 'Y'} yields TRUE and TRUE ('Y' is a TO_BOOLEAN
     * text form), the same union over {@code 'zzz'} fails "Boolean value 'zzz' is not recognized", and
     * {@code SELECT 1, 1 AS c UNION ALL SELECT 5, 'Y'} fails "Numeric value 'Y' is not recognized".
     * A STRING on the later side is converted, and so is a number under a BOOLEAN column —
     * {@code SELECT TRUE UNION ALL SELECT 0} answers TRUE and FALSE, and any other number TRUE; every other
     * shape is left to the key coercion.
     *
     * <p>The leading type comes from the first branch's VALUES when it produced any, and otherwise from
     * its declared COLUMN types — an EMPTY leading branch still types the union in Snowflake
     * (live-verified on a real account: {@code SELECT ts FROM t WHERE FALSE UNION ALL
     * SELECT '9999-12-31 00:00:003'} answers the TIMESTAMP {@code 9999-12-31 00:00:03.000}, and
     * INSERTing that union succeeds even though INSERTing the bare literal is rejected, because the
     * union already converted it; the same union over {@code 'not-a-timestamp'} fails "Timestamp
     * 'not-a-timestamp' is not recognized").
     */
    private static List<Row> coerceToFirstBranchTypes(final List<Row> leftRows, final List<Row> rightRows,
                                                      final List<ResultSetColumn> leadingColumns) {
        if (rightRows.isEmpty()) {
            return rightRows;
        }
        final List<Object> firstValues = leftRows.isEmpty()
            ? Collections.emptyList() : leftRows.get(0).getValues();
        final List<Row> converted = new ArrayList<>(rightRows.size());
        for (final Row row : rightRows) {
            final List<Object> values = row.getValues();
            List<Object> replaced = null;
            for (int i = 0; i < values.size(); i++) {
                final Object value = values.get(i);
                final Object leading = i < firstValues.size() ? firstValues.get(i) : null;
                if (value instanceof Number && !(value instanceof Boolean)) {
                    // A number under a BOOLEAN column is that BOOLEAN: zero FALSE, anything else TRUE.
                    final boolean booleanColumn = leading != null ? leading instanceof Boolean
                        : declaredTypeAt(leadingColumns, i) instanceof BooleanType;
                    if (booleanColumn) {
                        if (replaced == null) {
                            replaced = new ArrayList<>(values);
                        }
                        replaced.set(i, Boolean.valueOf(isNonZero((Number) value)));
                    }
                    continue;
                }
                if (!(value instanceof CharSequence)) {
                    continue;
                }
                final Object coerced = leading != null
                    ? coerceStringToLeadingType(leading, value.toString())
                    : coerceStringToDeclaredType(declaredTypeAt(leadingColumns, i), value.toString());
                if (coerced == null) {
                    continue;
                }
                if (replaced == null) {
                    replaced = new ArrayList<>(values);
                }
                replaced.set(i, coerced);
            }
            converted.add(replaced == null ? row : new Row(replaced));
        }
        return converted;
    }

    private static boolean isNonZero(final Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).signum() != 0;
        }
        if (value instanceof Double || value instanceof Float) {
            return value.doubleValue() != 0.0d;
        }
        return value instanceof BigInteger ? ((BigInteger) value).signum() != 0 : value.longValue() != 0L;
    }

    /**
     * Convert every branch's values toward the unified column types, in place in {@code branchRows}.
     * Live: when a set operation mixes a VARCHAR branch with a numeric or date branch, the STRING
     * side's values convert to the non-string side's type whichever side LEADS — {@code 'x' ∪ 1} and
     * {@code 1 ∪ 'x'} both fail "Numeric value 'x' is not recognized", an empty string fails the
     * same way, and a date pairing fails "Date 'y' is not recognized" — and every branch's values
     * take the unified NUMBER's scale ({@code '5' ∪ 1::NUMBER(10,2)} renders 5.00 and 1.00,
     * {@code '2.7'::VARCHAR(3) ∪ 1} renders 2.70000 and 1.00000 at the unified NUMBER(18,5)).
     * Only columns where a string branch actually participates are touched, so all-numeric unions
     * keep their existing values. Rows are REPLACED, never mutated — a branch's rows can alias
     * storage.
     */
    public static void coerceStringBranches(final List<List<Row>> branchRows,
                                            final List<List<ResultSetColumn>> branchColumns,
                                            final List<ResultSetColumn> unified) {
        if (unified == null) {
            return;
        }
        for (int col = 0; col < unified.size(); col++) {
            final DataType target = unified.get(col).getStaticType();
            if (!isStringCoercionTarget(target) && !isSemiStructuredTarget(target)) {
                continue;
            }
            // A NUMERIC unified type applies to EVERY branch, not only to the string ones: live
            // renders an INT branch's 1 as 1.00 under a unified NUMBER(38,2), and a NUMBER(10,2)
            // branch's 2.50 as 2.5000 under NUMBER(12,4). The gate below used to skip the whole
            // column unless some branch was a string, so a column declared with a scale handed back
            // values that did not carry it.
            if (!(target instanceof NumericType) && !isTimestampTarget(target) && !isZonedTimestampTarget(target)
                    && !isLocalTimestampTarget(target) && !isSemiStructuredTarget(target)) {
                boolean anyStringBranch = false;
                for (final List<ResultSetColumn> columns : branchColumns) {
                    if (col < columns.size()
                            && columns.get(col).getStaticType() instanceof StringType) {
                        anyStringBranch = true;
                        break;
                    }
                }
                if (!anyStringBranch) {
                    continue;
                }
            }
            for (final List<Row> rows : branchRows) {
                for (int r = 0; r < rows.size(); r++) {
                    final Row row = rows.get(r);
                    if (col >= row.getValues().size()) {
                        continue;
                    }
                    final Object value = row.getValue(col);
                    final Object converted = toUnifiedColumnValue(value, target);
                    if (converted != value) {
                        final List<Object> replaced = new ArrayList<>(row.getValues());
                        replaced.set(col, converted);
                        rows.set(r, new Row(replaced));
                    }
                }
            }
        }
    }

    /**
     * Converts the DATE arms' values of a set operation that is no UNION toward the timestamp the other arms hold, in
     * place in {@code branchRows}: live widens a DATE beside a TIMESTAMP before it compares the rows, so {@code
     * '2024-01-01'::DATE INTERSECT '2024-01-01 00:00:00'::TIMESTAMP_LTZ} keeps the one row, as the timestamp of its
     * midnight, and EXCEPT drops it (live-verified). Only a column whose arms are DATEs and timestamps of one kind
     * is touched: a widening cannot fail, so it needs none of the conversions a subtractive operator leaves undone
     * over an empty left side.
     *
     * @param branchRows    the rows to convert, each list replaced row by row
     * @param branchColumns every arm's own columns
     */
    public static void widenDateBranches(final List<List<Row>> branchRows,
                                         final List<List<ResultSetColumn>> branchColumns) {
        final int width = branchColumns.isEmpty() ? 0 : branchColumns.get(0).size();
        for (int col = 0; col < width; col++) {
            final DataType target = widenedTimestamp(branchColumns, col);
            if (target == null) {
                continue;
            }
            for (final List<Row> rows : branchRows) {
                for (int r = 0; r < rows.size(); r++) {
                    final Row row = rows.get(r);
                    if (col < row.getValues().size() && row.getValue(col) instanceof LocalDate) {
                        final List<Object> replaced = new ArrayList<>(row.getValues());
                        replaced.set(col, toUnifiedColumnValue(row.getValue(col), target));
                        rows.set(r, new Row(replaced));
                    }
                }
            }
        }
    }

    /**
     * The timestamp a column's DATE arms widen into: the type of its timestamp arms where every arm is a DATE or a
     * timestamp of one kind, NTZ, LTZ or TZ, and both are there; otherwise null.
     */
    private static DataType widenedTimestamp(final List<List<ResultSetColumn>> branchColumns, final int col) {
        DataType timestamp = null;
        boolean date = false;
        for (final List<ResultSetColumn> columns : branchColumns) {
            final DataType type = col < columns.size() ? columns.get(col).getStaticType() : null;
            if (!(type instanceof DateTimeType)) {
                return null;
            }
            if ("DATE".equalsIgnoreCase(type.getName())) {
                date = true;
            } else if (isTimestampTarget(type) || isLocalTimestampTarget(type) || isZonedTimestampTarget(type)) {
                if (timestamp != null && !timestamp.getName().equalsIgnoreCase(type.getName())) {
                    return null;
                }
                timestamp = timestamp == null ? type : timestamp;
            } else {
                return null;
            }
        }
        return date ? timestamp : null;
    }

    /**
     * Whether the unified type is a semi-structured ARRAY or OBJECT, which a VARIANT branch's value converts into
     * (see toUnifiedColumnValue); a structured one keeps its own rules.
     */
    private static boolean isSemiStructuredTarget(final DataType target) {
        return target instanceof ArrayType && !(target instanceof StructuredArrayType)
            || target instanceof ObjectType && !(target instanceof StructuredObjectType);
    }

    /** Whether the unified type is a TIMESTAMP_TZ, which a naive branch's value becomes at the session's offset. */
    private static boolean isZonedTimestampTarget(final DataType target) {
        return target instanceof DateTimeType && "TIMESTAMP_TZ".equalsIgnoreCase(target.getName());
    }

    /** Whether the unified type is a TIMESTAMP_LTZ, which a DATE branch's value becomes in the session's zone. */
    private static boolean isLocalTimestampTarget(final DataType target) {
        return target instanceof DateTimeType && "TIMESTAMP_LTZ".equalsIgnoreCase(target.getName());
    }

    /** Whether the unified type is a naive TIMESTAMP, which a DATE branch's value widens into. */
    private static boolean isTimestampTarget(final DataType target) {
        return target instanceof DateTimeType && "TIMESTAMP_NTZ".equalsIgnoreCase(target.getName());
    }

    /**
     * Whether {@code target} is a unified type string-branch values convert INTO. A UUID is one, whichever arm
     * leads: {@code <uuid> UNION ALL 'AAAAAAAA-...'} reads both rows as UUIDs, the text lower-cased, and a text
     * arm that spells no UUID is "UUID 'abc' is invalid, ..." (live-verified).
     */
    private static boolean isStringCoercionTarget(final DataType target) {
        if (target instanceof NumericType || target instanceof UuidType || isZonedTimestampTarget(target)
                || isLocalTimestampTarget(target) || target instanceof BooleanType) {
            return true;
        }
        return target instanceof DateTimeType
            && ("DATE".equalsIgnoreCase(target.getName())
                || "TIMESTAMP_NTZ".equalsIgnoreCase(target.getName()));
    }

    /** One value converted to the unified column type ({@code null} and already-fitting values pass). */
    private static Object toUnifiedColumnValue(final Object value, final DataType target) {
        if (value == null) {
            return null;
        }
        if (target instanceof UuidType) {
            return value instanceof CharSequence ? FoldedValues.presented(value, target) : value;
        }
        // A VARIANT branch's value under an ARRAY column becomes an array as TO_ARRAY makes one, 2 reading [2],
        // {"a":1} reading [{"a":1}] and a JSON null reading NULL, and under an OBJECT column it must hold an object:
        // 2 is "Failed to cast variant value 2 to OBJECT" (live-verified).
        if (isSemiStructuredTarget(target)) {
            return value instanceof VariantValue
                ? ValueCaster.castValue(value, target instanceof ArrayType ? "ARRAY" : "OBJECT") : value;
        }
        if (target instanceof NumericType) {
            final NumericType numeric = (NumericType) target;
            if ("FLOAT".equalsIgnoreCase(numeric.getName()) || "DOUBLE".equalsIgnoreCase(numeric.getName())) {
                if (value instanceof CharSequence) {
                    try {
                        return Double.valueOf(Double.parseDouble(value.toString().trim()));
                    } catch (final NumberFormatException notANumber) {
                        final Double hex = HexDoubleText.withoutExponent(value.toString().trim(), true);
                        if (hex != null) {
                            return hex;
                        }
                        throw new RuntimeException("Numeric value '" + value + "' is not recognized");
                    }
                }
                // An EXACT branch beside a FLOAT one becomes a float too: live gives
                // NUMBER(10,2) 2.50 back as 2.5 under a unified FLOAT, not as the scaled decimal.
                if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float)) {
                    return Double.valueOf(((Number) value).doubleValue());
                }
                return value;
            }
            if (value instanceof CharSequence) {
                final BigDecimal parsed;
                try {
                    parsed = new BigDecimal(value.toString().trim());
                } catch (final NumberFormatException notANumber) {
                    throw new RuntimeException("Numeric value '" + value + "' is not recognized");
                }
                return parsed.setScale(numeric.getScale(), RoundingMode.HALF_UP);
            }
            if (value instanceof Double || value instanceof Float) {
                return value;
            }
            if (value instanceof Number) {
                // The unified scale applies to every branch's values — live renders the NUMBER
                // branch's 1 as 1.00 under a unified NUMBER(10,2). A SCALE-0 target changes nothing
                // about an integral value, so it is left as the Long or Integer it already was:
                // re-wrapping it as a BigDecimal would alter what the driver hands back without
                // altering a single rendered digit.
                final BigDecimal scaled =
                    new BigDecimal(value.toString()).setScale(numeric.getScale(), RoundingMode.HALF_UP);
                if (numeric.getScale() == 0 && !(value instanceof BigDecimal)
                        && scaled.compareTo(new BigDecimal(value.toString())) == 0) {
                    return value;
                }
                return scaled;
            }
            return value;
        }
        // A DATE branch beside a TIMESTAMP_LTZ one becomes the instant of its midnight in the session's zone, as
        // its cast does, and so meets an equal moment of the other branch (live-verified).
        if (isLocalTimestampTarget(target)) {
            return value instanceof LocalDate || value instanceof LocalDateTime
                ? FoldedValues.presented(value, target) : value;
        }
        // A BOOLEAN column reads a text branch's value as a cast does: 'maybe' is "Boolean value 'maybe' is not
        // recognized" (live-verified).
        if (target instanceof BooleanType) {
            return value instanceof CharSequence ? FoldedValues.presented(value, target) : value;
        }
        // A TIMESTAMP_NTZ branch after a TIMESTAMP_TZ one takes the session's offset, as its cast does, so it
        // meets an equal moment of the other branch (live-verified); the other order is refused while the
        // statement compiles.
        if (isZonedTimestampTarget(target)) {
            return value instanceof LocalDateTime || value instanceof LocalDate
                ? SharedFunctionHelpers.toWrittenOffsetTimestamp(value) : value;
        }
        // A DATE branch beside a TIMESTAMP one becomes a timestamp at midnight, which is the value
        // live hands back — the DATE branch of d UNION ts reads 2020-01-01T00:00 and not 2020-01-01.
        if (target instanceof DateTimeType && "TIMESTAMP_NTZ".equalsIgnoreCase(target.getName())
                && value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        if (value instanceof CharSequence) {
            return toDateBranchValue(value.toString());
        }
        return value;
    }

    /**
     * One later-branch STRING converted to the leading branch's runtime type, or null to leave it.
     * Shared with the ordering conditionals (GREATEST / LEAST), which coerce a string operand toward
     * the other family exactly as a set operation's arms do — same rule, same sentences.
     */
    public static Object coerceStringToLeadingType(final Object leading, final String text) {
        if (leading instanceof Boolean) {
            return toBooleanBranchValue(text);
        }
        if (leading instanceof Number) {
            return ApproximateValues.isApproximate(leading) ? toApproximateBranchValue(text) : toNumericBranchValue(text);
        }
        if (leading instanceof LocalDateTime) {
            return toTimestampBranchValue(text);
        }
        if (leading instanceof LocalDate) {
            return toDateBranchValue(text);
        }
        if (leading instanceof LocalTime) {
            return toTimeBranchValue(text);
        }
        if (IntervalCells.isInterval(leading)) {
            // A text beside an interval is read in the interval's fields: 'x' is refused as the Day-Time Interval
            // it cannot be (live-verified).
            return IntervalCasts.convert(text, IntervalCasts.typeOfValue(leading), true);
        }
        return null;
    }

    /** The declared type of the leading branch's column {@code index}, or null when unknown. */
    private static DataType declaredTypeAt(final List<ResultSetColumn> leadingColumns, final int index) {
        if (leadingColumns == null || index >= leadingColumns.size()) {
            return null;
        }
        final ResultSetColumn column = leadingColumns.get(index);
        return column == null ? null : column.getDataType();
    }

    /** One later-branch STRING converted to the leading branch's DECLARED type, or null to leave it. */
    private static Object coerceStringToDeclaredType(final DataType declared, final String text) {
        if (IntervalCasts.isIntervalType(declared)) {
            return IntervalCasts.convert(text, declared, true);
        }
        if (declared instanceof BooleanType) {
            return toBooleanBranchValue(text);
        }
        if (declared instanceof NumericType) {
            return NumericType.isApproximate(declared) ? toApproximateBranchValue(text) : toNumericBranchValue(text);
        }
        if (declared instanceof DateTimeType) {
            final String name = declared.getName().toUpperCase(Locale.ROOT);
            if (name.startsWith("DATE")) {
                return toDateBranchValue(text);
            }
            if (name.startsWith("TIME") && !name.startsWith("TIMESTAMP")) {
                return toTimeBranchValue(text);
            }
            return toTimestampBranchValue(text);
        }
        return null;
    }

    private static Object toBooleanBranchValue(final String text) {
        final String lower = text.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("t") || lower.equals("yes") || lower.equals("y")
                || lower.equals("on") || lower.equals("1")) {
            return Boolean.TRUE;
        }
        if (lower.equals("false") || lower.equals("f") || lower.equals("no") || lower.equals("n")
                || lower.equals("off") || lower.equals("0")) {
            return Boolean.FALSE;
        }
        throw new RuntimeException("Boolean value '" + text + "' is not recognized");
    }

    /** A string beside a FLOAT: a hexadecimal number as the double it spells (see HexDoubleText), else as beside a NUMBER. */
    private static Object toApproximateBranchValue(final String text) {
        final Double hex = HexDoubleText.withoutExponent(text.trim(), true);
        return hex != null ? hex : toNumericBranchValue(text);
    }

    private static Object toNumericBranchValue(final String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
    }

    private static Object toTimestampBranchValue(final String text) {
        try {
            return SharedFunctionHelpers.toLocalDateTime(text);
        } catch (final RuntimeException notATimestamp) {
            throw new RuntimeException("Timestamp '" + text + "' is not recognized");
        }
    }

    private static Object toDateBranchValue(final String text) {
        try {
            return SharedFunctionHelpers.toLocalDate(text);
        } catch (final RuntimeException notADate) {
            throw new RuntimeException("Date '" + text + "' is not recognized");
        }
    }

    private static Object toTimeBranchValue(final String text) {
        try {
            return SharedFunctionHelpers.toLocalTime(text);
        } catch (final RuntimeException notATime) {
            throw new RuntimeException("Time '" + text + "' is not recognized");
        }
    }

    /**
     * The result of a SUBTRACTIVE set operation (MINUS / EXCEPT / INTERSECT) whose LEFT side produced no
     * rows: empty, WITHOUT touching the right side. Snowflake short-circuits these — live-verified on a
     * real account, {@code SELECT n FROM tnum MINUS SELECT s FROM tstr} over an EMPTY
     * numeric left and a VARCHAR right holding a non-numeric value answers zero rows, as do the same
     * shapes with EXCEPT and INTERSECT, while UNION [ALL] over the identical inputs errors "Numeric
     * value '…' is not recognized" and MINUS over a NON-empty left errors too. So the right branch's
     * values are converted only when a left row can actually be compared against them.
     */
    private static List<Row> emptyLeftResult() {
        return new ArrayList<>();
    }

    /**
     * Apply INTERSECT operation. ALL keeps min(count_left, count_right) occurrences via a right-side
     * count map; the distinct variant uses hash-set membership — both O(n) instead of O(n^2).
     */
    public static List<Row> applyIntersect(final List<Row> leftRows, final List<Row> rightRowsRaw, final boolean all) {
        return applyIntersect(leftRows, rightRowsRaw, all, null);
    }

    /**
     * Apply INTERSECT where some columns compare under a collation: a left row survives when the right branch
     * holds a row equal to it under the collation, and the rows it folds with report the smallest LEFT value by
     * raw text (live-verified: {@code 'a' INTERSECT 'A'} answers {@code a}).
     *
     * @param collations the collation of each column, null entries for the columns with none; null for none
     */
    public static List<Row> applyIntersect(final List<Row> leftRows, final List<Row> rightRowsRaw, final boolean all,
                                           final CollationSpec[] collations) {
        if (leftRows.isEmpty()) {
            return emptyLeftResult();
        }
        // Converted past the short-circuit, for the reason given on applyExcept.
        final List<Row> rightRows = coerceToFirstBranchTypes(leftRows, rightRowsRaw, null);
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        if (!all && anyCollation(collations)) {
            return foldCollated(leftRows, coercions, collations, collatedKeySet(rightRows, coercions, collations), true);
        }
        final List<Row> result = new ArrayList<>();

        if (all) {
            // INTERSECT ALL: for each left row, include min(count_left, count_right) occurrences.
            final Map<List<Object>, Integer> rightCounts = countByKey(rightRows, coercions);
            for (final Row leftRow : leftRows) {
                final List<Object> key = rowKey(leftRow, coercions);
                final Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    result.add(leftRow);
                    rightCounts.put(key, remaining - 1);
                }
            }
        } else {
            // INTERSECT: distinct left rows that also exist in right.
            final Set<List<Object>> rightKeys = keySet(rightRows, coercions);
            final Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                final List<Object> key = rowKey(leftRow, coercions);
                if (rightKeys.contains(key) && seen.add(key)) {
                    result.add(leftRow);
                }
            }
        }

        return result;
    }

    /**
     * Apply EXCEPT operation. ALL keeps count_left - count_right occurrences via a right-side count
     * map; the distinct variant uses hash-set membership — both O(n) instead of O(n^2).
     */
    public static List<Row> applyExcept(final List<Row> leftRows, final List<Row> rightRowsRaw, final boolean all) {
        return applyExcept(leftRows, rightRowsRaw, all, null);
    }

    /**
     * Apply EXCEPT where some columns compare under a collation: a left row survives when the right branch holds
     * no row equal to it under the collation, and the left rows it folds with report the smallest by raw text.
     *
     * @param collations the collation of each column, null entries for the columns with none; null for none
     */
    public static List<Row> applyExcept(final List<Row> leftRows, final List<Row> rightRowsRaw, final boolean all,
                                        final CollationSpec[] collations) {
        if (leftRows.isEmpty()) {
            return emptyLeftResult();
        }
        // PAST the short-circuit, and only there: a left row exists to compare against, so the right
        // branch is CONVERTED to the leading types exactly as a UNION converts it, and a value that
        // cannot convert refuses the statement. Live does both — it answers over an empty left and
        // refuses over a non-empty one — so the conversion belongs after this return and not before it.
        final List<Row> rightRows = coerceToFirstBranchTypes(leftRows, rightRowsRaw, null);
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        if (!all && anyCollation(collations)) {
            return foldCollated(leftRows, coercions, collations, collatedKeySet(rightRows, coercions, collations), false);
        }
        final List<Row> result = new ArrayList<>();

        if (all) {
            // EXCEPT ALL: include each left row unless a remaining right occurrence cancels it.
            final Map<List<Object>, Integer> rightCounts = countByKey(rightRows, coercions);
            for (final Row leftRow : leftRows) {
                final List<Object> key = rowKey(leftRow, coercions);
                final Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    rightCounts.put(key, remaining - 1);
                } else {
                    result.add(leftRow);
                }
            }
        } else {
            // EXCEPT: distinct left rows that do not exist in right.
            final Set<List<Object>> rightKeys = keySet(rightRows, coercions);
            final Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                final List<Object> key = rowKey(leftRow, coercions);
                if (!rightKeys.contains(key) && seen.add(key)) {
                    result.add(leftRow);
                }
            }
        }

        return result;
    }

    /**
     * Apply DISTINCT to remove duplicate rows (first occurrence kept, order preserved) — O(n) via a
     * hash set on normalized row keys.
     */
    public static List<Row> applyDistinct(final List<Row> rows) {
        return applyDistinct(rows, null);
    }

    /**
     * Apply DISTINCT where some columns compare under a collation: values that differ only below the
     * collation's strength are ONE row, and the row reports the smallest of them by raw text.
     *
     * @param rows       the rows to de-duplicate
     * @param collations the collation of each column, null entries for the columns with none; null for
     *                   a projection where no column carries one
     * @return the distinct rows, first occurrence kept and order preserved
     */
    public static List<Row> applyDistinct(final List<Row> rows, final CollationSpec[] collations) {
        final SetOpColumnCoercion[] coercions = columnCoercions(rows, null);
        if (!anyCollation(collations)) {
            final List<Row> distinctRows = new ArrayList<>();
            final Set<List<Object>> seen = new HashSet<>();
            for (final Row row : rows) {
                if (seen.add(rowKey(row, coercions))) {
                    distinctRows.add(row);
                }
            }
            return distinctRows;
        }
        final Map<List<Object>, List<Object>> reported = new LinkedHashMap<>();
        for (final Row row : rows) {
            final List<Object> key = rowKey(row, coercions, collations);
            final List<Object> kept = reported.get(key);
            if (kept == null) {
                reported.put(key, new ArrayList<>(row.getValues()));
                continue;
            }
            for (int i = 0; i < kept.size() && i < collations.length; i++) {
                if (collations[i] != null) {
                    kept.set(i, CollatedKey.leastOf(kept.get(i), row.getValues().get(i)));
                }
            }
        }
        final List<Row> distinctRows = new ArrayList<>(reported.size());
        for (final Map.Entry<List<Object>, List<Object>> entry : reported.entrySet()) {
            distinctRows.add(new Row(entry.getValue()));
        }
        return distinctRows;
    }

    /**
     * Rows de-duplicated under their collations, each group reporting its smallest value per collated column by
     * raw text, first occurrence order kept. With {@code others}, only the rows whose key is in that set
     * ({@code keepMembers}) or not in it survive.
     */
    private static List<Row> foldCollated(final List<Row> rows, final SetOpColumnCoercion[] coercions,
                                          final CollationSpec[] collations, final Set<List<Object>> others,
                                          final boolean keepMembers) {
        final Map<List<Object>, List<Object>> reported = new LinkedHashMap<>();
        for (final Row row : rows) {
            final List<Object> key = rowKey(row, coercions, collations);
            if (others != null && others.contains(key) != keepMembers) {
                continue;
            }
            final List<Object> kept = reported.get(key);
            if (kept == null) {
                reported.put(key, new ArrayList<>(row.getValues()));
                continue;
            }
            for (int i = 0; i < kept.size() && i < collations.length; i++) {
                if (collations[i] != null) {
                    kept.set(i, CollatedKey.leastOf(kept.get(i), row.getValues().get(i)));
                }
            }
        }
        final List<Row> folded = new ArrayList<>(reported.size());
        for (final Map.Entry<List<Object>, List<Object>> entry : reported.entrySet()) {
            folded.add(new Row(entry.getValue()));
        }
        return folded;
    }

    /** The collated row keys of a branch, for INTERSECT and EXCEPT membership. */
    private static Set<List<Object>> collatedKeySet(final List<Row> rows, final SetOpColumnCoercion[] coercions,
                                                    final CollationSpec[] collations) {
        final Set<List<Object>> keys = new HashSet<>();
        for (final Row row : rows) {
            keys.add(rowKey(row, coercions, collations));
        }
        return keys;
    }

    /** Whether any column of a projection compares under a collation. */
    static boolean anyCollation(final CollationSpec[] collations) {
        if (collations == null) {
            return false;
        }
        for (final CollationSpec rules : collations) {
            if (rules != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A hash/equality key for a whole row: a snapshot of its values with each column passed through its
     * {@link SetOpColumnCoercion} normalizer. {@code List} equality is a size check plus per-element
     * null-aware {@code equals} (NULL matches NULL, as Snowflake set operations require), and its
     * hashCode is consistent with that equality, so it is a drop-in dedup key.
     */
    static List<Object> rowKey(final Row row, final SetOpColumnCoercion[] coercions) {
        return rowKey(row, coercions, null);
    }

    /** {@link #rowKey(Row, SetOpColumnCoercion[])} with the collated columns keyed under their rules. */
    static List<Object> rowKey(final Row row, final SetOpColumnCoercion[] coercions,
                               final CollationSpec[] collations) {
        final List<Object> values = row.getValues();
        final List<Object> key = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            final SetOpColumnCoercion coercion = i < coercions.length ? coercions[i] : SetOpColumnCoercion.NONE;
            final CollationSpec rules = collations != null && i < collations.length ? collations[i] : null;
            // Deduplicating reads every cell, so a cell a relation deferred raises its fault here.
            key.add(CollatedKey.of(normalizeValue(coercion, DeferredFault.read(values.get(i))), rules));
        }
        return key;
    }


    /** Distinct set of row keys (membership tests for INTERSECT/EXCEPT). */
    static Set<List<Object>> keySet(final List<Row> rows, final SetOpColumnCoercion[] coercions) {
        final Set<List<Object>> keys = new HashSet<>();
        for (final Row row : rows) {
            keys.add(rowKey(row, coercions));
        }
        return keys;
    }

    /** Multiset of row keys (occurrence counts) for the ALL variants of INTERSECT/EXCEPT. */
    static Map<List<Object>, Integer> countByKey(final List<Row> rows, final SetOpColumnCoercion[] coercions) {
        final Map<List<Object>, Integer> counts = new HashMap<>();
        for (final Row row : rows) {
            final List<Object> key = rowKey(row, coercions);
            final Integer c = counts.get(key);
            counts.put(key, c == null ? 1 : c + 1);
        }
        return counts;
    }

    /**
     * Decide, per column, how values must be normalized so that cross-branch comparisons follow
     * Snowflake's implicit set-operation type unification. The decision is driven by the value classes
     * actually present across both inputs ({@code right} may be null for single-input DISTINCT):
     *
     * <ul>
     *   <li>a temporal mixed with strings (or DATE mixed with TIMESTAMP) coerces to the temporal kind;</li>
     *   <li>numbers mixed with strings, numbers of different classes, or any BigDecimal (whose
     *       {@code equals} is scale-sensitive: {@code 1.0} vs {@code 1}) coerce to exact BigDecimal;</li>
     *   <li>booleans mixed with strings coerce the recognized TO_BOOLEAN literals;</li>
     *   <li>anything else — including all-VARCHAR columns — is left untouched.</li>
     * </ul>
     */
    private static SetOpColumnCoercion[] columnCoercions(final List<Row> left, final List<Row> right) {
        int width = 0;
        for (final Row row : left) {
            width = Math.max(width, row.getValues().size());
        }
        if (right != null) {
            for (final Row row : right) {
                width = Math.max(width, row.getValues().size());
            }
        }
        final boolean[] hasString = new boolean[width];
        final boolean[] hasBoolean = new boolean[width];
        final boolean[] hasDateTime = new boolean[width];
        final boolean[] hasDate = new boolean[width];
        final boolean[] hasTime = new boolean[width];
        final boolean[] hasNumber = new boolean[width];
        final boolean[] hasBigDecimal = new boolean[width];
        final boolean[] mixedNumberClasses = new boolean[width];
        final Class<?>[] numberClass = new Class<?>[width];
        scanValueClasses(left, hasString, hasBoolean, hasDateTime, hasDate, hasTime,
                hasNumber, hasBigDecimal, mixedNumberClasses, numberClass);
        if (right != null) {
            scanValueClasses(right, hasString, hasBoolean, hasDateTime, hasDate, hasTime,
                    hasNumber, hasBigDecimal, mixedNumberClasses, numberClass);
        }

        final SetOpColumnCoercion[] coercions = new SetOpColumnCoercion[width];
        for (int i = 0; i < width; i++) {
            if (hasDateTime[i] && (hasString[i] || hasDate[i])) {
                coercions[i] = SetOpColumnCoercion.TIMESTAMP;
            } else if (hasDate[i] && hasString[i]) {
                coercions[i] = SetOpColumnCoercion.DATE;
            } else if (hasTime[i] && hasString[i]) {
                coercions[i] = SetOpColumnCoercion.TIME;
            } else if (hasNumber[i] && (hasString[i] || mixedNumberClasses[i] || hasBigDecimal[i])) {
                coercions[i] = SetOpColumnCoercion.NUMERIC;
            } else if (hasBoolean[i] && hasString[i]) {
                coercions[i] = SetOpColumnCoercion.BOOLEAN;
            } else {
                coercions[i] = SetOpColumnCoercion.NONE;
            }
        }
        return coercions;
    }

    private static void scanValueClasses(final List<Row> rows, final boolean[] hasString,
                                         final boolean[] hasBoolean, final boolean[] hasDateTime,
                                         final boolean[] hasDate, final boolean[] hasTime,
                                         final boolean[] hasNumber, final boolean[] hasBigDecimal,
                                         final boolean[] mixedNumberClasses, final Class<?>[] numberClass) {
        for (final Row row : rows) {
            final List<Object> values = row.getValues();
            for (int i = 0; i < values.size(); i++) {
                final Object value = values.get(i);
                if (value == null) {
                    continue;
                }
                if (value instanceof CharSequence) {
                    hasString[i] = true;
                } else if (value instanceof Boolean) {
                    hasBoolean[i] = true;
                } else if (value instanceof LocalDateTime) {
                    hasDateTime[i] = true;
                } else if (value instanceof LocalDate) {
                    hasDate[i] = true;
                } else if (value instanceof LocalTime) {
                    hasTime[i] = true;
                } else if (value instanceof Number) {
                    hasNumber[i] = true;
                    if (value instanceof BigDecimal) {
                        hasBigDecimal[i] = true;
                    }
                    if (numberClass[i] == null) {
                        numberClass[i] = value.getClass();
                    } else if (numberClass[i] != value.getClass()) {
                        mixedNumberClasses[i] = true;
                    }
                }
            }
        }
    }

    /**
     * Normalize one value for key comparison under a column's coercion. A string that the column's
     * unified type cannot read is an ERROR, not simply an unmatched row — live-verified on a real
     * account: {@code SELECT 'not-a-timestamp' EXCEPT SELECT <ts>} fails "Timestamp
     * 'not-a-timestamp' is not recognized" and {@code SELECT 'abc' UNION SELECT 1} fails "Numeric value
     * 'abc' is not recognized", while the same statements over a coercible string run. BOOLEAN stays
     * lenient: an unrecognized string compared against a boolean is FALSE there, not an error
     * (live: {@code SELECT 'zz' = TRUE} is FALSE).
     */
    private static Object normalizeValue(final SetOpColumnCoercion coercion, final Object value) {
        if (value instanceof VariantValue) {
            // Set-operation keys compare semi-structured values by their JSON text, so a typed value
            // dedups/intersects against a text-carried equal one.
            return ((VariantValue) value).text();
        }
        if (value == null) {
            return null;
        }
        if (coercion == SetOpColumnCoercion.NONE) {
            // A column of doubles keeps its values as its keys, with one adjustment: the two zeros are
            // ONE distinct value on the account — SELECT DISTINCT over -0.0::FLOAT and 0.0::FLOAT is a
            // single row there, the FIRST one — where Double.equals tells them apart. Adding a positive
            // zero folds -0.0 into 0.0 and leaves every other double alone. A column that mixes a
            // double with an exact number is NUMERIC below, whose decimal key has no sign of zero.
            // A zoned timestamp keys by its moment: two spellings of one moment are one row (live-verified).
            if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
                return ValueComparisons.canonicalGroupKeyValue(value);
            }
            return value instanceof Double ? Double.valueOf(((Double) value).doubleValue() + 0.0) : value;
        }
        switch (coercion) {
            case TIMESTAMP:
                if (value instanceof LocalDate) {
                    return ((LocalDate) value).atStartOfDay();
                }
                if (value instanceof CharSequence) {
                    try {
                        return SharedFunctionHelpers.toLocalDateTime(value.toString());
                    } catch (final RuntimeException notATimestampString) {
                        throw new RuntimeException("Timestamp '" + value + "' is not recognized");
                    }
                }
                return value;
            case DATE:
                if (value instanceof CharSequence) {
                    try {
                        return SharedFunctionHelpers.toLocalDate(value.toString());
                    } catch (final RuntimeException notADateString) {
                        throw new RuntimeException("Date '" + value + "' is not recognized");
                    }
                }
                return value;
            case TIME:
                if (value instanceof CharSequence) {
                    try {
                        return SharedFunctionHelpers.toLocalTime(value.toString());
                    } catch (final RuntimeException notATimeString) {
                        throw new RuntimeException("Time '" + value + "' is not recognized");
                    }
                }
                return value;
            case NUMERIC:
                if (value instanceof Number || value instanceof CharSequence) {
                    try {
                        return new BigDecimal(value.toString().trim()).stripTrailingZeros();
                    } catch (final NumberFormatException notANumericValue) {
                        throw new RuntimeException("Numeric value '" + value + "' is not recognized");
                    }
                }
                return value;
            case BOOLEAN:
                if (value instanceof CharSequence) {
                    final String text = value.toString().trim().toLowerCase(Locale.ROOT);
                    if (text.equals("true") || text.equals("t") || text.equals("yes") || text.equals("y")
                            || text.equals("on") || text.equals("1")) {
                        return Boolean.TRUE;
                    }
                    if (text.equals("false") || text.equals("f") || text.equals("no") || text.equals("n")
                            || text.equals("off") || text.equals("0")) {
                        return Boolean.FALSE;
                    }
                }
                return value;
            default:
                return value;
        }
    }

    private SetOperations() {
    }
}
