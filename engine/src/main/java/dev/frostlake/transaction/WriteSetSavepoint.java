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

package dev.frostlake.transaction;

import dev.frostlake.storage.Row;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A COPY-ON-FIRST-MUTATION statement savepoint over a {@link TransactionWriteSet}. The former
 * savepoint copied the ENTIRE write set per statement — a transaction that had loaded k rows paid
 * O(k) pointer copies for every later statement, however few tables that statement touched. This
 * one records a table's pre-images only when the statement FIRST mutates that table, so rollback
 * restores exactly the touched entries and untouched tables are never copied at all.
 *
 * <p>A null pre-image list/map/set means the table had NO entry of that kind before the statement
 * (restore removes the entry). The lock map is snapshotted eagerly — it holds one entry per touched
 * table, never per row.
 */
public final class WriteSetSavepoint {

    private final Set<String> touched = new HashSet<>();
    private final Map<String, List<Row>> insertsBefore = new HashMap<>();
    private final Map<String, Map<Long, Row>> updatesBefore = new HashMap<>();
    private final Map<String, Set<Long>> deletesBefore = new HashMap<>();
    private final Map<String, TableLock> locksBefore;

    WriteSetSavepoint(final Map<String, TableLock> lockSnapshot) {
        this.locksBefore = lockSnapshot;
    }

    boolean isTouched(final String table) {
        return touched.contains(table);
    }

    void rememberTable(final String table, final List<Row> insertsPre, final Map<Long, Row> updatesPre,
                       final Set<Long> deletesPre) {
        touched.add(table);
        insertsBefore.put(table, insertsPre);
        updatesBefore.put(table, updatesPre);
        deletesBefore.put(table, deletesPre);
    }

    Set<String> touchedTables() {
        return touched;
    }

    List<Row> insertsBefore(final String table) {
        return insertsBefore.get(table);
    }

    Map<Long, Row> updatesBefore(final String table) {
        return updatesBefore.get(table);
    }

    Set<Long> deletesBefore(final String table) {
        return deletesBefore.get(table);
    }

    Map<String, TableLock> locksBefore() {
        return locksBefore;
    }
}
