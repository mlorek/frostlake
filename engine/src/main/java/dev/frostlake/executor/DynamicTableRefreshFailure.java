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

import dev.frostlake.metastore.model.DynamicTable;

/**
 * The refusal of a manual refresh of a dynamic table whose query reads a relation that is gone. The account
 * expands the table's query as it does a view's body, so the miss is reported under the table's name, notes the
 * role the table runs as, and is told as a failed refresh (live-verified).
 */
final class DynamicTableRefreshFailure {

    private static final String NOTE = "Ensure this role has access to all referenced objects. See"
        + " https://docs.snowflake.com/en/user-guide/dynamic-tables/troubleshoot-permissions for details.";

    private DynamicTableRefreshFailure() {
    }

    /**
     * @param dynamicTable the table refreshed
     * @param missing      the miss its query's expansion met
     * @return the refusal the refresh answers
     */
    static RuntimeException manual(final DynamicTable dynamicTable, final ViewExpansionException missing) {
        final String expanded = missing.within(dynamicTable.getName()).getMessage()
            + " (Note: the dynamic table runs as owner role '" + dynamicTable.getOwner() + "'. " + NOTE + ")";
        return new RuntimeException(SqlCompilationError.inline("Failed to refresh dynamic table with"
            + " refresh_trigger MANUAL at data_timestamp " + StatementClock.instant().toEpochMilli()
            + " because of the error: " + SqlCompilationError.inline("Target table failed to refresh: " + expanded)));
    }
}
