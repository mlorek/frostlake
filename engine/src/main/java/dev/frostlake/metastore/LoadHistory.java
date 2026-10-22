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

package dev.frostlake.metastore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The files COPY has loaded into the account's tables, one record a file (documented): a COPY that ran to completion
 * records each file it read, errors or not, and one an ABORT_STATEMENT error stopped records none; a table's records go
 * when it is dropped, replaced or truncated. Kept in memory, as the files a load skips are.
 */
public final class LoadHistory {

    private final List<LoadRecord> records = new ArrayList<LoadRecord>();

    /** Record the files of one completed COPY. */
    public synchronized void add(final List<LoadRecord> loads) {
        records.addAll(loads);
    }

    /**
     * Forget a table's records.
     *
     * @param storageKey the table's storage key
     * @param temporary  whether the table is the temporary one of that name
     */
    public synchronized void forget(final String storageKey, final boolean temporary) {
        final Iterator<LoadRecord> it = records.iterator();
        while (it.hasNext()) {
            final LoadRecord load = it.next();
            if (load.isTemporary() == temporary && load.getStorageKey().equals(storageKey)) {
                it.remove();
            }
        }
    }

    /**
     * Replace every record with a restored state's.
     *
     * @param loads the restored records, oldest first
     */
    public synchronized void restore(final List<LoadRecord> loads) {
        records.clear();
        records.addAll(loads);
    }

    /** Every record, oldest first. */
    public synchronized List<LoadRecord> records() {
        return new ArrayList<LoadRecord>(records);
    }
}
