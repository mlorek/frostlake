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

import dev.frostlake.metastore.model.Stream;

import java.util.Set;

/**
 * The table locks open transactions hold against other sessions' writers (live-verified). A transaction that has
 * written a row of a table holds the table until it commits or rolls back: another session's UPDATE, DELETE, MERGE
 * or TRUNCATE that would change a row there waits for it, then runs over what the holder left, and is refused when
 * the session's LOCK_TIMEOUT passes first (see {@link TableLockWait}). A statement that changes no row neither takes
 * a table nor waits for one, and an INSERT never waits.
 *
 * <p>Writers wait in the sessions the concurrent engine runs and in the in-process driver's connections, each request
 * with an {@link EngineLockRelease}: an engine its caller drives directly has nobody who could let a lock go, and
 * there a writer does not wait.
 */
public final class TableLockGate {

    private static final ThreadLocal<EngineLockRelease> RELEASES = new ThreadLocal<EngineLockRelease>();

    private TableLockGate() {
    }

    /**
     * The request running on this thread may wait for a lock, giving up the engine's lock meanwhile.
     *
     * @param release how the request gives the engine's lock up and takes it back
     * @return the release of a request this one runs inside, to put back with {@link #restore}, or null
     */
    public static EngineLockRelease register(final EngineLockRelease release) {
        final EngineLockRelease displaced = RELEASES.get();
        RELEASES.set(release);
        return displaced;
    }

    /** The request running on this thread has ended. */
    public static void unregister() {
        RELEASES.remove();
    }

    /**
     * The request running on this thread has ended, inside the one {@link #register} displaced, if any.
     *
     * @param displaced what {@link #register} answered
     */
    public static void restore(final EngineLockRelease displaced) {
        if (displaced == null) {
            RELEASES.remove();
        } else {
            RELEASES.set(displaced);
        }
    }

    /**
     * Whether the request running on this thread may wait for a lock: one that runs alone under its engine's own
     * lock, which it gives up while it waits.
     *
     * @return whether it may
     */
    public static boolean mayWait() {
        return RELEASES.get() != null;
    }

    /** What the request running on this thread gives up while it waits, or null when it may not wait. */
    static EngineLockRelease release() {
        return RELEASES.get();
    }

    /**
     * Refuses to let the running statement write a table another session's open transaction holds: the statement
     * is to wait for the holder and run again (see {@link TableLockWait}).
     *
     * <p>A session that holds a temporary table of its own under the name wrote that table, not the one the running
     * statement writes, so its transaction holds nothing here (live-verified).
     *
     * @param transactions       the engine's transactions
     * @param ownSession         the number of the session the statement runs in, or null
     * @param fullyQualifiedName the table's storage name
     * @param tableName          the table's name as the refusal names it
     * @param temporaryHolders   the sessions whose own temporary table the storage name holds
     */
    public static void requireFree(final TransactionManager transactions, final Long ownSession,
                                   final String fullyQualifiedName, final String tableName,
                                   final Set<Long> temporaryHolders) {
        if (RELEASES.get() == null || ownSession == null) {
            return;
        }
        final Transaction own = transactions.getCurrentTransaction();
        for (final Transaction other : transactions.getActiveTransactions()) {
            if (other != own && other.isExplicit() && other.getSessionNumber() != null
                    && !ownSession.equals(other.getSessionNumber())
                    && !temporaryHolders.contains(other.getSessionNumber()) && other.hasWritten(fullyQualifiedName)) {
                throw new TableLockWait(other, fullyQualifiedName, tableName);
            }
        }
    }

    /**
     * Refuses to let the running statement read a stream another session's open transaction holds: the statement is
     * to wait for the holder and run again (see {@link TableLockWait}). An explicit transaction's read of a stream,
     * a query or a consuming DML, holds it until the transaction ends (see {@link StreamLock}), and the reads that
     * would take it wait meanwhile - another explicit transaction's, and a consuming DML's or a CTAS's in any
     * session - where an autocommit query reads at once (live-verified).
     *
     * @param transactions the engine's transactions
     * @param ownSession   the number of the session the statement runs in, or null
     * @param stream       the stream
     * @param resource     its full name
     * @param streamName   its name as the refusal names it
     */
    public static void requireStreamFree(final TransactionManager transactions, final Long ownSession,
                                         final Stream stream, final String resource, final String streamName) {
        if (RELEASES.get() == null || ownSession == null) {
            return;
        }
        final Transaction own = transactions.getCurrentTransaction();
        for (final Transaction other : transactions.getActiveTransactions()) {
            if (other != own && other.isExplicit() && other.getSessionNumber() != null
                    && !ownSession.equals(other.getSessionNumber()) && other.holdsStreamLock(stream)) {
                throw TableLockWait.onStream(other, stream, resource, streamName);
            }
        }
    }

    /**
     * Refuses to let the running COPY load a file that another session's open transaction has loaded into the same
     * table: the COPY is to wait for that transaction and run again (see {@link TableLockWait}), and then skips the
     * file when the load committed and loads it when it rolled back. A COPY that may not wait goes on.
     *
     * @param transactions       the engine's transactions
     * @param loader             the transaction whose load read the file
     * @param ownSession         the number of the session the statement runs in, or null
     * @param fullyQualifiedName the table's storage name
     * @param tableName          the table's name as the refusal names it
     * @param temporaryHolders   the sessions whose own temporary table the storage name holds, whose loads went there
     */
    public static void requireLoadFree(final TransactionManager transactions, final Transaction loader,
                                       final Long ownSession, final String fullyQualifiedName,
                                       final String tableName, final Set<Long> temporaryHolders) {
        if (RELEASES.get() == null || ownSession == null || loader.getSessionNumber() == null
                || ownSession.equals(loader.getSessionNumber())
                || temporaryHolders.contains(loader.getSessionNumber())
                || !transactions.getActiveTransactions().contains(loader)) {
            return;
        }
        throw new TableLockWait(loader, fullyQualifiedName, tableName);
    }
}
