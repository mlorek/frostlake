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
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * Whether an equality between a column of each side of a join can serve as a hash key: whether equal values of
 * the two columns meet in one bucket as they are carried.
 *
 * <p>A VARIANT beside a column of another type does not: the comparison converts one side first — the VARIANT is
 * cast to a DATE, a TIME, a timestamp, or to a BOOLEAN, an ARRAY or an OBJECT on its left, and a number beside it
 * is read as a VARIANT — so {@code va = n} over a VARIANT 1 and a NUMBER 1 matches, and {@code va = d} beside a
 * DATE fails "Failed to cast variant value 1 to DATE" (live-verified). Such an equality is left to the condition,
 * which compares the pair as the comparison does. Two VARIANT columns, and two columns of which neither is one,
 * key as before.
 *
 * <p>Nor does a text beside a column of another kind: the comparison reads the text as the other side's type — a
 * BOOLEAN, a DATE, a TIME, a timestamp, a number — so {@code b = 'true'} and {@code d = '2024-01-01'} match where the
 * buckets would keep them apart, and a text that does not read is refused ({@code Numeric value '1a' is not
 * recognized}) where a bucket would skip it (live-verified). And a collated text compares as its collation says — an
 * accent- or trailing-space-insensitive one matches values the buckets keep apart — so it does not key either. Nor,
 * last, do two columns of different kinds the comparison converts between, as a number beside a BOOLEAN, read as
 * one ({@code TRUE = 2} holds, live-verified), or a TIME beside a date or a timestamp: only columns of one kind key,
 * a number beside a number, a date or a timestamp beside a date or a timestamp, whose values bucket alike.
 */
final class HashJoinKeyTypes {

    private HashJoinKeyTypes() {
    }

    /**
     * Whether the equality between two classified join columns may key the hash join.
     *
     * @param first  the first column as {tableSide (0 = left, 1 = right), columnIndex}
     * @param second the second column, alike
     * @param left   the left relation
     * @param right  the right relation
     * @return true when equal values of the two columns share a bucket as carried
     */
    static boolean keysByValue(final int[] first, final int[] second, final Table left, final Table right) {
        final TableColumn a = columnOf(first, left, right);
        final TableColumn b = columnOf(second, left, right);
        if (isVariant(a) != isVariant(b)) {
            return false;
        }
        if (a == null || b == null) {
            return true;
        }
        if (isText(a) != isText(b) || isCollated(a) || isCollated(b)) {
            return false;
        }
        return ofOneKind(a.getDataType(), b.getDataType());
    }

    /** Whether two types compare without converting either side; a type not known keys as before. */
    private static boolean ofOneKind(final DataType a, final DataType b) {
        if (a == null || b == null) {
            return true;
        }
        if (a.getCategory() != b.getCategory()) {
            return false;
        }
        return a.getCategory() != TypeCategory.DATE_TIME || "TIME".equals(a.getName()) == "TIME".equals(b.getName());
    }

    /** The classified column, or null when its position is not one of its relation's. */
    private static TableColumn columnOf(final int[] column, final Table left, final Table right) {
        final List<TableColumn> columns = (column[0] == 0 ? left : right).getColumns();
        return column[1] < 0 || column[1] >= columns.size() ? null : columns.get(column[1]);
    }

    private static boolean isVariant(final TableColumn column) {
        return column != null && column.getDataType() instanceof VariantType;
    }

    private static boolean isText(final TableColumn column) {
        final DataType type = column.getDataType();
        return type instanceof StringType;
    }

    private static boolean isCollated(final TableColumn column) {
        return column.getCollation() != null && !column.getCollation().isEmpty();
    }
}
