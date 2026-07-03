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

import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless set-operation row math — UNION / INTERSECT / EXCEPT (ALL and distinct variants) and DISTINCT —
 * extracted from {@link QueryExecutor}. Pure {@code List<Row>} transforms; dedup uses a hash key on the
 * row's values, where {@code List} equality is exactly the old value-by-value row comparison (so these are
 * O(n) rather than O(n^2)).
 */
public final class SetOperations {

    /**
     * Apply UNION operation. The distinct variant deduplicates via a hash set on the row's values
     * (List equality is exactly the old value-by-value comparison), turning the former O(n^2) scan
     * into O(n).
     */
    public static List<Row> applyUnion(final List<Row> leftRows, final List<Row> rightRows, final boolean all) {
        if (all) {
            // UNION ALL: keep all rows
            List<Row> result = new ArrayList<>(leftRows);
            result.addAll(rightRows);
            return result;
        }

        // UNION: distinct rows — left in order, then rows from right not already present.
        List<Row> result = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
        for (final Row row : leftRows) {
            if (seen.add(rowKey(row))) {
                result.add(row);
            }
        }
        for (final Row row : rightRows) {
            if (seen.add(rowKey(row))) {
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
        List<Row> result = new ArrayList<>();

        if (all) {
            // INTERSECT ALL: for each left row, include min(count_left, count_right) occurrences.
            Map<List<Object>, Integer> rightCounts = countByKey(rightRows);
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow);
                Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    result.add(leftRow);
                    rightCounts.put(key, remaining - 1);
                }
            }
        } else {
            // INTERSECT: distinct left rows that also exist in right.
            Set<List<Object>> rightKeys = keySet(rightRows);
            Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow);
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
        List<Row> result = new ArrayList<>();

        if (all) {
            // EXCEPT ALL: include each left row unless a remaining right occurrence cancels it.
            Map<List<Object>, Integer> rightCounts = countByKey(rightRows);
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow);
                Integer remaining = rightCounts.get(key);
                if (remaining != null && remaining > 0) {
                    rightCounts.put(key, remaining - 1);
                } else {
                    result.add(leftRow);
                }
            }
        } else {
            // EXCEPT: distinct left rows that do not exist in right.
            Set<List<Object>> rightKeys = keySet(rightRows);
            Set<List<Object>> seen = new HashSet<>();
            for (final Row leftRow : leftRows) {
                List<Object> key = rowKey(leftRow);
                if (!rightKeys.contains(key) && seen.add(key)) {
                    result.add(leftRow);
                }
            }
        }

        return result;
    }

    /**
     * A hash/equality key for a whole row: a snapshot of its values. {@code List} equality is a size
     * check plus per-element null-aware {@code equals} — identical to the former value-by-value row
     * comparison — and its hashCode is consistent with that equality, so it is a drop-in dedup key.
     */
    public static List<Object> rowKey(final Row row) {
        return new ArrayList<>(row.getValues());
    }

    /** Distinct set of row keys (membership tests for INTERSECT/EXCEPT). */
    public static Set<List<Object>> keySet(final List<Row> rows) {
        Set<List<Object>> keys = new HashSet<>();
        for (final Row row : rows) {
            keys.add(rowKey(row));
        }
        return keys;
    }

    /** Multiset of row keys (occurrence counts) for the ALL variants of INTERSECT/EXCEPT. */
    public static Map<List<Object>, Integer> countByKey(final List<Row> rows) {
        Map<List<Object>, Integer> counts = new HashMap<>();
        for (final Row row : rows) {
            List<Object> key = rowKey(row);
            Integer c = counts.get(key);
            counts.put(key, c == null ? 1 : c + 1);
        }
        return counts;
    }

    /**
     * Apply DISTINCT to remove duplicate rows (first occurrence kept, order preserved) — O(n) via a
     * hash set on row keys.
     */
    public static List<Row> applyDistinct(final List<Row> rows) {
        List<Row> distinctRows = new ArrayList<>();
        Set<List<Object>> seen = new HashSet<>();
        for (final Row row : rows) {
            if (seen.add(rowKey(row))) {
                distinctRows.add(row);
            }
        }
        return distinctRows;
    }

    private SetOperations() {
    }
}
