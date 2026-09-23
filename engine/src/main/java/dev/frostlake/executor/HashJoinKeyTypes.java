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
        return isVariant(first, left, right) == isVariant(second, left, right);
    }

    private static boolean isVariant(final int[] column, final Table left, final Table right) {
        final List<TableColumn> columns = (column[0] == 0 ? left : right).getColumns();
        if (column[1] < 0 || column[1] >= columns.size()) {
            return false;
        }
        final DataType type = columns.get(column[1]).getDataType();
        return type instanceof VariantType;
    }
}
