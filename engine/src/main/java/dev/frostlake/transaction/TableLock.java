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

/**
 * One table-level lock a transaction is HOLDING, as {@code SHOW LOCKS} reports it (live-verified):
 * DML that writes a table — an UPDATE, DELETE, MERGE or TRUNCATE, an UPDATE, DELETE or MERGE even
 * when it matches no rows, and an INSERT into a table that holds committed rows, though not into an
 * empty one — registers a {@code PARTITIONS} lock on the table's fully qualified name, one row per
 * (transaction, table) however many statements touch it, released when the transaction ends.
 * {@code acquiredOn} is the FIRST touch; {@code queryId} names the latest statement that changed a
 * row of the table, or the one that touched it first while none has.
 */
public class TableLock {

    private final long acquiredOn;
    private final String queryId;

    public TableLock(final long acquiredOn, final String queryId) {
        this.acquiredOn = acquiredOn;
        this.queryId = queryId;
    }

    /** Epoch millis of the first statement that touched the table in this transaction. */
    public long getAcquiredOn() {
        return acquiredOn;
    }

    /** The id of the statement a lock refusal and SHOW LOCKS name for the lock. */
    public String getQueryId() {
        return queryId;
    }
}
