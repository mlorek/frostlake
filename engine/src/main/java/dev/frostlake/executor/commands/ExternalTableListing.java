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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW EXTERNAL TABLES: the listing's nineteen columns, and no rows.
 *
 * <p>An external table reads files a cloud location holds, which this engine has no way to reach, so there
 * are never any to list. What the statement still has to answer for is its SHAPE — the columns come back
 * even when nothing does, and a client reading the listing reads them.
 */
final class ExternalTableListing {

    private ExternalTableListing() {
    }

    /** The empty listing, with the columns the account declares for it. */
    static ResultSet listing() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON));
        for (final String name : Arrays.asList("name", "database_name", "schema_name", "invalid",
                "invalid_reason", "owner", "comment", "stage", "location", "file_format_name",
                "file_format_type", "cloud", "region", "notification_channel")) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        columns.add(new ResultSetColumn("last_refreshed_on", ShowResultHelpers.CREATED_ON));
        for (final String name : Arrays.asList("table_format", "last_refresh_details", "owner_role_type")) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return new ResultSet(columns);
    }
}
