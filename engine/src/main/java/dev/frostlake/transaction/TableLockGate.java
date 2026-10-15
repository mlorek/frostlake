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
 * The table locks open transactions hold against other sessions' writers (live-verified). A transaction that has
 * written a row of a table holds the table until it commits or rolls back: another session's UPDATE, DELETE, MERGE
 * or TRUNCATE that would change a row there waits for it, then runs over what the holder left, and is refused when
 * the session's LOCK_TIMEOUT passes first (see {@link TableLockWait}). A statement that changes no row neither takes
 * a table nor waits for one, and an INSERT never waits.
 *
 * <p>Writers wait in the sessions the concurrent engine runs, each request with an {@link EngineLockRelease}: an
 * engine one thread drives has nobody who could let a lock go, and there a writer does not wait.
 */
public final class TableLockGate {

    private static final ThreadLocal<EngineLockRelease> RELEASES = new ThreadLocal<EngineLockRelease>();

    private TableLockGate() {
    }

    /**
     * The request running on this thread may wait for a lock, giving up the engine's lock meanwhile.
     *
     * @param release how the request gives the engine's lock up and takes it back
     */
    public static void register(final EngineLockRelease release) {
        RELEASES.set(release);
    }

    /** The request running on this thread has ended. */
    public static void unregister() {
        RELEASES.remove();
    }

    /** What the request running on this thread gives up while it waits, or null when it may not wait. */
    static EngineLockRelease release() {
        return RELEASES.get();
    }

    /**
     * Refuses to let the running statement write a table another session's open transaction holds: the statement
     * is to wait for the holder and run again (see {@link TableLockWait}).
     *
     * @param transactions       the engine's transactions
     * @param ownSession         the number of the session the statement runs in, or null
     * @param fullyQualifiedName the table's storage name
     * @param tableName          the table's name as the refusal names it
     */
    public static void requireFree(final TransactionManager transactions, final Long ownSession,
                                   final String fullyQualifiedName, final String tableName) {
        if (RELEASES.get() == null || ownSession == null) {
            return;
        }
        final Transaction own = transactions.getCurrentTransaction();
        for (final Transaction other : transactions.getActiveTransactions()) {
            if (other != own && other.isExplicit() && other.getSessionNumber() != null
                    && !ownSession.equals(other.getSessionNumber()) && other.hasWritten(fullyQualifiedName)) {
                throw new TableLockWait(other, fullyQualifiedName, tableName);
            }
        }
    }
}
