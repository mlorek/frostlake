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

/**
 * What ALTER DYNAMIC TABLE … REFRESH tells of the refresh: what the table's own rows did, and how many dynamic tables
 * the refresh refreshed — the table and every one it reads, none when the table had nothing new (live-verified).
 */
public final class DynamicTableRefreshOutcome {

    private final long[] changed;
    private final int refreshedCount;

    /**
     * @param changed        the rows inserted, copied and deleted, or null when nothing changed
     * @param refreshedCount the dynamic tables refreshed
     */
    DynamicTableRefreshOutcome(final long[] changed, final int refreshedCount) {
        this.changed = changed;
        this.refreshedCount = refreshedCount;
    }

    /**
     * The rows the refresh inserted, copied and deleted, in that order.
     *
     * @return the counts, or null when nothing changed
     */
    public long[] getChanged() {
        return changed;
    }

    /**
     * How many dynamic tables the refresh refreshed.
     *
     * @return the count
     */
    public int getRefreshedCount() {
        return refreshedCount;
    }
}
