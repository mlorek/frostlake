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

package dev.frostlake.executor.commands;

import dev.frostlake.storage.Row;
import java.util.Comparator;

/**
 * Orders SHOW listing rows the way a real account returns them: by database, then schema, then
 * the {@code name} column, each compared byte-wise.
 *
 * <p>The wider scopes are why the database and schema participate: {@code IN ACCOUNT} and
 * {@code IN DATABASE} listings group rows by database and schema before name (live-verified), so
 * a table named AATOP in a second schema sorts after ZZTOP in the first. Single-scope listings
 * see no difference — every row shares the same database and schema there.
 *
 * <p>Byte-wise is the point: a real account listing a schema that holds DT_A, T_A…T_D and a quoted
 * "t_lower" returns the lowercase name last, so uppercase sorts before lowercase and the ordering is
 * {@link String#compareTo}'s rather than {@link String#CASE_INSENSITIVE_ORDER}'s. Rows with no name
 * sort first, so a listing that leaves a column null cannot throw here.
 */
final class ShowNameComparator implements Comparator<Row> {

    private final int databaseIndex;
    private final int schemaIndex;
    private final int nameIndex;

    ShowNameComparator(final int nameIndex) {
        this(-1, -1, nameIndex);
    }

    ShowNameComparator(final int databaseIndex, final int schemaIndex, final int nameIndex) {
        this.databaseIndex = databaseIndex;
        this.schemaIndex = schemaIndex;
        this.nameIndex = nameIndex;
    }

    @Override
    public int compare(final Row left, final Row right) {
        int order = compareAt(left, right, databaseIndex);
        if (order != 0) {
            return order;
        }
        order = compareAt(left, right, schemaIndex);
        if (order != 0) {
            return order;
        }
        return compareAt(left, right, nameIndex);
    }

    private int compareAt(final Row left, final Row right, final int index) {
        if (index < 0) {
            return 0;
        }
        final Object leftValue = left.getValue(index);
        final Object rightValue = right.getValue(index);
        if (leftValue == null) {
            return rightValue == null ? 0 : -1;
        }
        if (rightValue == null) {
            return 1;
        }
        return leftValue.toString().compareTo(rightValue.toString());
    }
}
