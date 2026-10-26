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

package dev.frostlake.executor.udf;

import dev.frostlake.executor.StatementCancellation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ends a guest language's execution once the statement it runs for is over: canceled, timed out by its session's
 * STATEMENT_TIMEOUT_IN_SECONDS or a driver's query timeout, or its session aborted. The engine's own long-running
 * points check for that (see StatementCancellation), and a handler's code reaches none of them, so a watcher thread
 * checks for it while the guest runs and stops it. The account ends a Python function that sleeps, a JavaScript
 * function or procedure that spins, and a Python procedure that catches what its queries raise alike, each with the
 * statement's own refusal (live-verified); the runtime that calls the guest words that refusal (see TrackedRuntime).
 */
public final class GuestWatch {

    /** How often the watcher looks at the statements whose guests run. */
    private static final long PERIOD_MILLIS = 50;

    /** The executions watched now, each by the key {@link #begin} answered for it. */
    private static final Map<Object, GuestStop> WATCHED = new ConcurrentHashMap<>();

    /** The watcher: one daemon thread for the JVM, started by the first watch. */
    private static final ScheduledExecutorService WATCHER =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread thread = new Thread(r, "frostlake-guest-watch");
                thread.setDaemon(true);
                return thread;
            }
        });

    /** Whether the watcher's rounds are scheduled. */
    private static final AtomicBoolean WATCHING = new AtomicBoolean();

    private GuestWatch() {
    }

    /**
     * Watch the statement this thread runs while a guest executes for it: once the statement is over, {@code stop}
     * runs, once, on a thread of its own.
     *
     * @param stop what ends the guest's execution
     * @return what to hand {@link #end} once the execution returns; null where no statement runs on this thread,
     *         which leaves the guest unwatched
     */
    public static Object begin(final Runnable stop) {
        final StatementCancellation token = StatementCancellation.pinned();
        if (token == null) {
            return null;
        }
        final Object key = new Object();
        WATCHED.put(key, new GuestStop(token, stop));
        if (WATCHING.compareAndSet(false, true)) {
            WATCHER.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    for (final GuestStop guest : WATCHED.values()) {
                        guest.stopIfOver();
                    }
                }
            }, PERIOD_MILLIS, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        }
        return key;
    }

    /**
     * The execution {@link #begin} watched has returned.
     *
     * @param watch what {@link #begin} answered, or null
     */
    public static void end(final Object watch) {
        if (watch != null) {
            WATCHED.remove(watch);
        }
    }
}
