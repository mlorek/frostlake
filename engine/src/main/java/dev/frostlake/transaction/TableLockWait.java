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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A statement that met a table another session's open transaction holds (see {@link TableLockGate}), raised before
 * the statement writes anything. The statement's dispatch waits for the holder and runs the statement again; when
 * waiting is not allowed or lasts past the session's LOCK_TIMEOUT the statement is refused in the account's words
 * (live-verified), and a transaction it runs in stays open.
 */
public final class TableLockWait extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** How often a waiting statement looks whether the holder is still open. */
    private static final long POLL_MILLIS = 20L;

    private final transient Transaction holder;
    private final String fullyQualifiedName;
    private final String tableName;

    TableLockWait(final Transaction holder, final String fullyQualifiedName, final String tableName) {
        super("table " + tableName + " is locked by transaction " + holder.getPublicId(), null, false, false);
        this.holder = holder;
        this.fullyQualifiedName = fullyQualifiedName;
        this.tableName = tableName;
    }

    /**
     * Waits for the holder to commit or roll back, the engine's lock given up meanwhile. The wait ends early, with the
     * statement, when its timeout passes, it is canceled, its session is aborted or the engine shuts down.
     *
     * @param transactions   the engine's transactions
     * @param timeoutSeconds the session's LOCK_TIMEOUT
     * @param waitingSince   when the statement first waited ({@link System#nanoTime()}), which bounds its waits
     *                       however often it runs again
     * @param stop           what ends the statement before the holder does
     */
    public void await(final TransactionManager transactions, final long timeoutSeconds, final long waitingSince,
                      final LockWaitStop stop) {
        if (timeoutSeconds <= 0) {
            throw refusal("is currently not allowed.");
        }
        final long deadline = waitingSince + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        final EngineLockRelease release = TableLockGate.release();
        release.release();
        try {
            while (stillHeld(transactions) && System.nanoTime() < deadline) {
                stop.check();
                Thread.sleep(Math.max(1L, Math.min(POLL_MILLIS,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            release.reacquire();
        }
        if (stillHeld(transactions)) {
            throw refusal("is limited to " + timeoutSeconds + " seconds.");
        }
    }

    /** Whether the holder is still open; what it has written is its own thread's to read while it runs. */
    private boolean stillHeld(final TransactionManager transactions) {
        return transactions.getActiveTransactions().contains(holder);
    }

    private RuntimeException refusal(final String waiting) {
        return new RuntimeException("Statement '" + holder.lockQueryId(fullyQualifiedName) + "' has locked table '"
            + tableName + "' in transaction " + holder.getPublicId() + " and this lock has not yet been released.\n"
            + "Your statement '" + UUID.randomUUID() + "' was aborted because waiting for this lock " + waiting);
    }
}
