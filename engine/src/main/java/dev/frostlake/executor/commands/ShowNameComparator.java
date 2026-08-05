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
 * Orders SHOW listing rows by their {@code name} column, byte-wise.
 *
 * <p>Byte-wise is the point: a real account listing a schema that holds DT_A, T_A…T_D and a quoted
 * "t_lower" returns the lowercase name last, so uppercase sorts before lowercase and the ordering is
 * {@link String#compareTo}'s rather than {@link String#CASE_INSENSITIVE_ORDER}'s. Rows with no name
 * sort first, so a listing that leaves the column null cannot throw here.
 */
final class ShowNameComparator implements Comparator<Row> {

    private final int nameIndex;

    ShowNameComparator(final int nameIndex) {
        this.nameIndex = nameIndex;
    }

    @Override
    public int compare(final Row left, final Row right) {
        final Object leftName = left.getValue(nameIndex);
        final Object rightName = right.getValue(nameIndex);
        if (leftName == null) {
            return rightName == null ? 0 : -1;
        }
        if (rightName == null) {
            return 1;
        }
        return leftName.toString().compareTo(rightName.toString());
    }
}
