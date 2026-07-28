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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    public static List<Row> applyUnion(final List<Row> leftRows, final List<Row> rightRows, final boolean all) {
        if (all) {
            // UNION ALL: keep all rows
            List<Row> result = new ArrayList<>(leftRows);
            result.addAll(rightRows);
            return result;
        }

        // UNION: distinct rows — left in order, then rows from right not already present.
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        List<Row> result = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
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
     * Apply INTERSECT operation. ALL keeps min(count_left, count_right) occurrences via a right-side
     * count map; the distinct variant uses hash-set membership — both O(n) instead of O(n^2).
     */
    public static List<Row> applyIntersect(final List<Row> leftRows, final List<Row> rightRows, final boolean all) {
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        List<Row> result = new ArrayList<>();

        if (all) {
            // INTERSECT ALL: for each left row, include min(count_left, count_right) occurrences.
            Map<List<Object>, Integer> rightCounts = countByKey(rightRows, coercions);
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow, coercions);
                Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    result.add(leftRow);
                    rightCounts.put(key, remaining - 1);
                }
            }
        } else {
            // INTERSECT: distinct left rows that also exist in right.
            Set<List<Object>> rightKeys = keySet(rightRows, coercions);
            Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow, coercions);
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
    public static List<Row> applyExcept(final List<Row> leftRows, final List<Row> rightRows, final boolean all) {
        final SetOpColumnCoercion[] coercions = columnCoercions(leftRows, rightRows);
        List<Row> result = new ArrayList<>();

        if (all) {
            // EXCEPT ALL: include each left row unless a remaining right occurrence cancels it.
            Map<List<Object>, Integer> rightCounts = countByKey(rightRows, coercions);
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow, coercions);
                Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    rightCounts.put(key, remaining - 1);
                } else {
                    result.add(leftRow);
                }
            }
        } else {
            // EXCEPT: distinct left rows that do not exist in right.
            Set<List<Object>> rightKeys = keySet(rightRows, coercions);
            Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow, coercions);
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
        final SetOpColumnCoercion[] coercions = columnCoercions(rows, null);
        List<Row> distinctRows = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
        for (final Row row : rows) {
            if (seen.add(rowKey(row, coercions))) {
                distinctRows.add(row);
            }
        }
        return distinctRows;
    }

    /**
     * A hash/equality key for a whole row: a snapshot of its values with each column passed through its
     * {@link SetOpColumnCoercion} normalizer. {@code List} equality is a size check plus per-element
     * null-aware {@code equals} (NULL matches NULL, as Snowflake set operations require), and its
     * hashCode is consistent with that equality, so it is a drop-in dedup key.
     */
    static List<Object> rowKey(final Row row, final SetOpColumnCoercion[] coercions) {
        final List<Object> values = row.getValues();
        final List<Object> key = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            final SetOpColumnCoercion coercion = i < coercions.length ? coercions[i] : SetOpColumnCoercion.NONE;
            key.add(normalizeValue(coercion, values.get(i)));
        }
        return key;
    }

    /** Distinct set of row keys (membership tests for INTERSECT/EXCEPT). */
    static Set<List<Object>> keySet(final List<Row> rows, final SetOpColumnCoercion[] coercions) {
        Set<List<Object>> keys = new HashSet<>();
        for (final Row row : rows) {
            keys.add(rowKey(row, coercions));
        }
        return keys;
    }

    /** Multiset of row keys (occurrence counts) for the ALL variants of INTERSECT/EXCEPT. */
    static Map<List<Object>, Integer> countByKey(final List<Row> rows, final SetOpColumnCoercion[] coercions) {
        Map<List<Object>, Integer> counts = new HashMap<>();
        for (final Row row : rows) {
            List<Object> key = rowKey(row, coercions);
            Integer c = counts.get(key);
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
     * Normalize one value for key comparison under a column's coercion. A value that cannot be coerced
     * (a string that is not a valid timestamp/number/boolean literal) is kept as-is — Snowflake would
     * raise a cast error there; the engine stays lenient, so the row simply never matches the other side.
     */
    private static Object normalizeValue(final SetOpColumnCoercion coercion, final Object value) {
        if (value == null || coercion == SetOpColumnCoercion.NONE) {
            return value;
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
                        return value;
                    }
                }
                return value;
            case DATE:
                if (value instanceof CharSequence) {
                    try {
                        return SharedFunctionHelpers.toLocalDate(value.toString());
                    } catch (final RuntimeException notADateString) {
                        return value;
                    }
                }
                return value;
            case TIME:
                if (value instanceof CharSequence) {
                    try {
                        return SharedFunctionHelpers.toLocalTime(value.toString());
                    } catch (final RuntimeException notATimeString) {
                        return value;
                    }
                }
                return value;
            case NUMERIC:
                if (value instanceof Number || value instanceof CharSequence) {
                    try {
                        return new BigDecimal(value.toString().trim()).stripTrailingZeros();
                    } catch (final NumberFormatException notANumericValue) {
                        return value;
                    }
                }
                return value;
            case BOOLEAN:
                if (value instanceof CharSequence) {
                    final String text = value.toString().trim().toLowerCase();
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
