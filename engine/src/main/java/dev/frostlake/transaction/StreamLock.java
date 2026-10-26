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
 * The lock a transaction holds on a stream it read, as {@code SHOW LOCKS} reports it (live-verified): a read of the
 * stream inside an explicit transaction - a query or a consuming DML - takes it, the first read naming it, and the
 * transaction's end releases it. Another session's read that would take it waits meanwhile (see
 * {@link TableLockGate#requireStreamFree}).
 */
public final class StreamLock {

    private final String resource;
    private final long acquiredOn;
    private final String queryId;

    /**
     * @param resource   the stream's full name, as SHOW LOCKS lists it
     * @param acquiredOn when the transaction first read the stream, in epoch millis
     * @param queryId    the statement that first read it
     */
    public StreamLock(final String resource, final long acquiredOn, final String queryId) {
        this.resource = resource;
        this.acquiredOn = acquiredOn;
        this.queryId = queryId;
    }

    /** The stream's full name. */
    public String getResource() {
        return resource;
    }

    /** Epoch millis of the transaction's first read of the stream. */
    public long getAcquiredOn() {
        return acquiredOn;
    }

    /** The id of the statement a lock refusal and SHOW LOCKS name for the lock. */
    public String getQueryId() {
        return queryId;
    }
}
