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

import dev.frostlake.metastore.model.DynamicTableRefreshRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * The refreshes of the account's dynamic tables, oldest first, each under the name its table held when it ran
 * (live-verified): the history outlives the table, so the refreshes of a table replaced, dropped, undropped or renamed
 * stay listed under the name they ran under, and a table made again by that name, of any kind, reads them too. Kept in
 * memory, as many as one listing of it may ask for.
 */
public final class DynamicTableRefreshHistory {

    /** How many refreshes the history keeps, as many as one listing of it may ask for. */
    public static final int KEPT = 10000;

    private final List<DynamicTableRefreshRecord> records = new ArrayList<DynamicTableRefreshRecord>();

    /** Record a refresh that ran. */
    public synchronized void add(final DynamicTableRefreshRecord record) {
        records.add(record);
        if (records.size() > KEPT) {
            records.remove(0);
        }
    }

    /** Every refresh kept, oldest first. */
    public synchronized List<DynamicTableRefreshRecord> records() {
        return new ArrayList<DynamicTableRefreshRecord>(records);
    }
}
