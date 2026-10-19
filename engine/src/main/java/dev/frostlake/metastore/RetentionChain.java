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

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;

/**
 * The DATA_RETENTION_TIME_IN_DAYS an object keeps, resolved through the chain SHOW reports: the object's own
 * declared value, else its schema's, else its database's, else the account default of 1. INHERITANCE IS LIVE
 * (live-verified: a schema with no value of its own follows its database's CURRENT value, ALTER included, and UNSET
 * restores exactly that fallback).
 *
 * <p>One rule for every reader: SHOW's {@code retention_time}, INFORMATION_SCHEMA, the time-travel window and the
 * dropped versions UNDROP can still restore.
 */
public final class RetentionChain {

    /** The account default: one day. */
    public static final int ACCOUNT_DEFAULT_DAYS = 1;

    private RetentionChain() {
    }

    /**
     * The retention in effect for a table, a schema or a database.
     *
     * @param database the database, or null when there is none to inherit from
     * @param schema the schema, or null for a database's own retention
     * @param table the table, or null for a schema's or a database's own retention
     * @return the days
     */
    public static int effectiveDays(final Database database, final Schema schema, final Table table) {
        if (table != null && table.getDataRetentionTimeInDays() != null) {
            return table.getDataRetentionTimeInDays();
        }
        if (schema != null && schema.getDataRetentionTimeInDays() != null) {
            return schema.getDataRetentionTimeInDays();
        }
        if (database != null && database.getDataRetentionTimeInDays() != null) {
            return database.getDataRetentionTimeInDays();
        }
        return ACCOUNT_DEFAULT_DAYS;
    }
}
