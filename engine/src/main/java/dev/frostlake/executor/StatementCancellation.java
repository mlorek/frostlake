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

import java.util.HashMap;
import java.util.Map;

/**
 * What can end a running statement before its answer: its session's STATEMENT_TIMEOUT_IN_SECONDS, a driver's query
 * timeout, and a cancel — the driver's {@code Statement.cancel()} or SYSTEM$CANCEL_QUERY — from another thread. One
 * is pinned per outermost statement, as the statement's clock is, and the engine's long-running points call
 * {@link #check()}: the operator pipeline between stages, a join's rows, SYSTEM$WAIT, GENERATOR, a block's loops and
 * each statement a block runs. A timeout of zero is the longest, seven days (live-verified).
 *
 * <p>Setting the flag takes no lock, so a cancel never waits behind the statement it cancels.
 */
public final class StatementCancellation {

    /** The longest a statement may run, which a timeout of zero stands for (live-verified). */
    private static final long LONGEST_SECONDS = 604_800L;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final ThreadLocal<StatementCancellation> PINNED = new ThreadLocal<StatementCancellation>();

    /** The token a driver hands the statement it is about to run, taken when the statement is pinned. */
    private static final ThreadLocal<StatementCancellation> OFFERED = new ThreadLocal<StatementCancellation>();

    /** The running statements by query ID, which SYSTEM$CANCEL_QUERY finds them by. */
    private static final Map<String, StatementCancellation> RUNNING = new HashMap<String, StatementCancellation>();

    private final int driverSeconds;
    private volatile boolean canceled;
    private volatile long started;
    private volatile long sessionSeconds;
    private volatile String queryId;

    /**
     * A token for one statement.
     *
     * @param driverSeconds the driver's query timeout in seconds, 0 for none
     */
    public StatementCancellation(final int driverSeconds) {
        this.driverSeconds = driverSeconds;
    }

    /** Cancel the statement this token is pinned to, from any thread; a token not yet pinned starts canceled. */
    public void cancel() {
        canceled = true;
    }

    /**
     * Hand the next statement this thread runs a driver's token; null takes an offer back.
     *
     * @param token the token, or null
     */
    public static void offer(final StatementCancellation token) {
        if (token == null) {
            OFFERED.remove();
        } else {
            OFFERED.set(token);
        }
    }

    /**
     * Pin the cancellation of the outermost statement starting on this thread: the driver's offered token, or a fresh
     * one, timed from now by the session's timeout.
     *
     * @param sessionTimeoutSeconds the session's STATEMENT_TIMEOUT_IN_SECONDS, 0 for the longest
     * @return the token pinned before, to restore with {@link #end}
     */
    static StatementCancellation begin(final long sessionTimeoutSeconds) {
        final StatementCancellation displaced = PINNED.get();
        final StatementCancellation offered = OFFERED.get();
        OFFERED.remove();
        final StatementCancellation token = offered != null ? offered : new StatementCancellation(0);
        token.started = System.nanoTime();
        token.sessionSeconds = sessionTimeoutSeconds > 0 ? sessionTimeoutSeconds : LONGEST_SECONDS;
        PINNED.set(token);
        return displaced;
    }

    /**
     * Unpin the statement's token and put back what {@link #begin} displaced.
     *
     * @param displaced the token pinned before
     */
    static void end(final StatementCancellation displaced) {
        final StatementCancellation token = PINNED.get();
        if (token != null && token.queryId != null) {
            synchronized (RUNNING) {
                RUNNING.remove(token.queryId);
            }
        }
        if (displaced == null) {
            PINNED.remove();
        } else {
            PINNED.set(displaced);
        }
    }

    /**
     * Record the query ID the pinned statement runs under, so SYSTEM$CANCEL_QUERY can find it.
     *
     * @param id the query ID
     */
    static void runningAs(final String id) {
        final StatementCancellation token = PINNED.get();
        if (token == null || id == null) {
            return;
        }
        token.queryId = id;
        synchronized (RUNNING) {
            RUNNING.put(id, token);
        }
    }

    /**
     * Cancel the running statement a query ID names.
     *
     * @param id the query ID
     * @return whether one was running under it
     */
    static boolean cancelRunning(final String id) {
        final StatementCancellation token;
        synchronized (RUNNING) {
            token = RUNNING.get(id);
        }
        if (token == null) {
            return false;
        }
        token.cancel();
        return true;
    }

    /** End the pinned statement here if it was canceled or its time is up; nothing when none is pinned. */
    public static void check() {
        final StatementCancellation token = PINNED.get();
        if (token == null) {
            return;
        }
        if (token.canceled) {
            throw StatementCanceledException.canceled();
        }
        final long elapsed = System.nanoTime() - token.started;
        if (token.driverSeconds > 0 && elapsed >= token.driverSeconds * NANOS_PER_SECOND) {
            // A driver's query timeout is the driver canceling the statement (live-verified).
            throw StatementCanceledException.canceled();
        }
        if (elapsed >= token.sessionSeconds * NANOS_PER_SECOND) {
            throw StatementCanceledException.timedOut(token.sessionSeconds);
        }
    }
}
