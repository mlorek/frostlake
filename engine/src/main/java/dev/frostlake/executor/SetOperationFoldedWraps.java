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

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.values.VariantValue;

import java.util.List;

/**
 * A set operation's column that still projects one folded constant wrap. Live folds a number or boolean
 * constant wrapped into a VARIANT through a set operation whose every arm projects that same constant, and
 * a cast of the combined column to a sized VARCHAR then converts unchecked, as the wrap written in place
 * does: {@code SELECT v FROM c UNION ALL SELECT v FROM c}, a UNION or INTERSECT of it, three such arms, a
 * second CTE holding the same wrap, or {@code TO_VARIANT(123)} beside {@code 123::VARIANT} all read
 * {@code 123} through {@code ::VARCHAR(1)}. Arms holding different constants stay checked
 * (all live-verified).
 */
final class SetOperationFoldedWraps {

    private SetOperationFoldedWraps() {
    }

    /**
     * Marks each combined column every arm of which projects the same folded constant wrap.
     *
     * @param combined the set operation's columns, replaced in place where marked
     * @param branches every arm's own columns, positionally aligned with the combined ones
     * @param rows     every arm's rows
     */
    static void keep(final List<ResultSetColumn> combined, final List<List<ResultSetColumn>> branches,
                     final List<List<Row>> rows) {
        for (int i = 0; i < combined.size(); i++) {
            if (allArmsWrap(i, branches) && projectsOneWrap(i, rows)) {
                combined.set(i, combined.get(i).withUncheckedConstant());
            }
        }
    }

    /**
     * Whether any of the combined columns could be marked — every arm's column is a folded constant wrap — so
     * that {@link #keep} needs the arms' rows to decide; decided from the arms' shapes alone.
     *
     * @param columns  how many columns the set operation combines
     * @param branches every arm's own columns, positionally aligned with the combined ones
     * @return whether the rows are needed
     */
    static boolean needsRows(final int columns, final List<List<ResultSetColumn>> branches) {
        for (int i = 0; i < columns; i++) {
            if (allArmsWrap(i, branches)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allArmsWrap(final int column, final List<List<ResultSetColumn>> branches) {
        for (final List<ResultSetColumn> branch : branches) {
            if (column >= branch.size() || !branch.get(column).isUncheckedConstant()) {
                return false;
            }
        }
        return true;
    }

    private static boolean projectsOneWrap(final int column, final List<List<Row>> rows) {
        String shared = null;
        for (final List<Row> branchRows : rows) {
            for (final Row row : branchRows) {
                final String value = constantText(row.getValue(column));
                if (shared != null && !shared.equals(value)) {
                    return false;
                }
                shared = value;
            }
        }
        return true;
    }

    /** The constant as text: a wrap holds it as a VARIANT or, cast straight to one, as the number itself. */
    private static String constantText(final Object value) {
        return value instanceof VariantValue ? ((VariantValue) value).text() : String.valueOf(value);
    }
}
