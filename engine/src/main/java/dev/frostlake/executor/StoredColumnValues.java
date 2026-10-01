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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.DayTimeInterval;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a catalog column's stored rows tell the planner besides a numeric interval, read over the whole table as
 * the account's statistics are: whether every row holds one value that is never NULL, the least and the greatest
 * text a column holds, and whether any row is NULL. The account keeps these for every scalar family — a VARCHAR, a
 * FLOAT, a DATE, a BOOLEAN, a BINARY, a timestamp or an INTERVAL column holding one value is read as that value —
 * but not for a VARIANT, an ARRAY or an OBJECT. Text is ordered by code point, the order of its UTF-8 bytes. A
 * column is read once per instance.
 */
public final class StoredColumnValues {

    private final QueryExecutor executor;
    private final Map<String, Boolean> oneValue = new HashMap<>();
    private final Map<String, String[]> textRanges = new HashMap<>();
    private final Map<String, Boolean> nulls = new HashMap<>();

    /**
     * @param executor the executor whose storage the rows are read from
     */
    public StoredColumnValues(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Whether every row of a column holds one value that is never NULL.
     *
     * @param owner  the relation the column belongs to; only a catalog table, or a copy of one, has stored rows
     * @param column the column's name
     * @return false where the rows cannot be read, the table is empty or the column's family keeps no statistic
     */
    public boolean holdsOneValue(final Table owner, final String column) {
        final String key = read(owner, column);
        return key != null && Boolean.TRUE.equals(oneValue.get(key));
    }

    /**
     * The least and the greatest text a column holds, by code point.
     *
     * @return the two bounds, or null where the column is no uncollated text column, holds no text or cannot be read
     */
    String[] textRange(final Table owner, final String column) {
        final String key = read(owner, column);
        return key == null ? null : textRanges.get(key);
    }

    /**
     * Whether some row of a column is NULL.
     *
     * @return the answer, or null where the rows cannot be read
     */
    Boolean holdsNull(final Table owner, final String column) {
        final String key = read(owner, column);
        return key == null ? null : nulls.get(key);
    }

    /** The key a column's summary is kept under once its rows are read, or null where they cannot be read. */
    private String read(final Table owner, final String column) {
        final Table stored = owner == null ? null : owner.residentSource();
        if (stored == null || column == null || !stored.hasColumn(column)) {
            return null;
        }
        final String qualifiedName = stored.getQualifiedName();
        if (qualifiedName == null || !executor.getStorageEngine().hasTable(qualifiedName)) {
            return null;
        }
        final TableColumn declared = stored.getColumn(column);
        final String key = qualifiedName + "\u0000" + declared.getName();
        if (nulls.containsKey(key)) {
            return key;
        }
        final int index = stored.getColumnIndex(declared.getName());
        final boolean text = declared.getDataType() instanceof StringType && declared.getCollation() == null;
        final List<Row> rows = executor.readTableRowsForTransaction(qualifiedName);
        boolean holdsNull = false;
        boolean same = !rows.isEmpty() && keepsStatistics(declared.getDataType());
        Object first = null;
        String least = null;
        String greatest = null;
        for (final Row row : rows) {
            final Object value = index < row.size() ? row.getValue(index) : null;
            if (value == null) {
                holdsNull = true;
                same = false;
                continue;
            }
            if (first == null) {
                first = value;
            } else if (same && !sameValue(first, value)) {
                same = false;
            }
            if (text && value instanceof String) {
                final String written = (String) value;
                if (least == null || compareText(written, least) < 0) {
                    least = written;
                }
                if (greatest == null || compareText(written, greatest) > 0) {
                    greatest = written;
                }
            }
        }
        nulls.put(key, Boolean.valueOf(holdsNull));
        oneValue.put(key, Boolean.valueOf(same));
        if (least != null) {
            textRanges.put(key, new String[] {least, greatest});
        }
        return key;
    }

    /** Whether the account keeps a column family's least and greatest value: every scalar family but the semi-structured. */
    private static boolean keepsStatistics(final DataType type) {
        return type instanceof NumericType || type instanceof StringType || type instanceof DateTimeType
            || type instanceof BooleanType || type instanceof BinaryType || type instanceof IntervalDayTimeType
            || type instanceof IntervalYearMonthType;
    }

    private static boolean sameValue(final Object first, final Object value) {
        if (first instanceof BigDecimal && value instanceof BigDecimal) {
            return ((BigDecimal) first).compareTo((BigDecimal) value) == 0;
        }
        if (first instanceof DayTimeInterval && value instanceof DayTimeInterval) {
            return ((DayTimeInterval) first).compareTo((DayTimeInterval) value) == 0;
        }
        return first.equals(value);
    }

    /** Two texts in the order of their code points, which is the order of their UTF-8 bytes. */
    static int compareText(final String left, final String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            final int a = left.codePointAt(i);
            final int b = right.codePointAt(j);
            if (a != b) {
                return a < b ? -1 : 1;
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        final boolean leftDone = i >= left.length();
        final boolean rightDone = j >= right.length();
        if (leftDone == rightDone) {
            return 0;
        }
        return leftDone ? -1 : 1;
    }
}
