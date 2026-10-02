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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes a JVM-handler UDF's console output ({@code System.out}/{@code System.err} written on the
 * invoking thread) into the engine log instead of the embedder's console. Snowflake never surfaces a
 * UDF's console output to the SQL client — e.g. an ANTLR-based handler's default error listener
 * printing {@code line 1:1 missing '\n' at …} on every recovered parse — so an embedder's test run
 * should not see it either; it lands here as {@code dev.frostlake.udf.console} INFO lines.
 *
 * <p>Installation swaps {@code System.out}/{@code System.err} for pass-through wrappers
 * ({@link UdfConsoleStream}) the first time a handler runs; threads outside {@link #enter()}/
 * {@link #exit()} write through to the original streams byte-for-byte. A test harness may replace the
 * system streams between tests (Surefire does), so {@link #enter()} re-installs when the wrapper has
 * been displaced. The relay guard stops recursion when the log appender itself writes to the console.
 *
 * <p>Captured lines are buffered per thread and logged when the handler returns ({@link #exit()}), never
 * from inside the write. A write arrives holding the console stream's monitor — {@code PrintStream}
 * synchronizes every print, and {@code Throwable.printStackTrace} holds it for the whole trace — while a
 * console log appender takes its own lock first and then writes to that same stream (slf4j-simple holds
 * its configuration lock across the write to {@code System.err}). Logging from inside the write took the
 * two locks in the opposite order, so a handler printing on one thread while any other thread logged
 * could deadlock both, and with them every thread waiting on the engine lock either one held.
 */
public final class UdfConsoleCapture {

    /** Most completed lines a thread buffers between relays; lines beyond it are counted, not kept. */
    static final int MAX_PENDING_LINES = 10_000;

    private static final ThreadLocal<UdfConsoleScope> SCOPE = new ThreadLocal<UdfConsoleScope>() {
        @Override
        protected UdfConsoleScope initialValue() {
            return new UdfConsoleScope(MAX_PENDING_LINES);
        }
    };

    private static volatile Logger relayLogger = LoggerFactory.getLogger("dev.frostlake.udf.console");

    /** Mark the current thread as executing a UDF handler; console writes are captured until {@link #exit()}. */
    public static void enter() {
        installIfNeeded();
        SCOPE.get().enter();
    }

    /**
     * Leave the handler scope entered by {@link #enter()} (call from a {@code finally} block) and log
     * the lines it printed. Leaving the outermost scope also logs a last line left without a newline.
     */
    public static void exit() {
        final UdfConsoleScope scope = SCOPE.get();
        scope.exit();
        relay(scope);
    }

    /** True when the current thread's console output should be captured rather than passed through. */
    static boolean isCapturing() {
        return SCOPE.get().isCapturing();
    }

    /** Buffer one byte the current thread wrote to {@code streamName}; called only while capturing. */
    static void capture(final String streamName, final int b) {
        SCOPE.get().capture(streamName, b);
    }

    /** Buffer bytes the current thread wrote to {@code streamName}; called only while capturing. */
    static void capture(final String streamName, final byte[] buf, final int off, final int len) {
        SCOPE.get().capture(streamName, buf, off, len);
    }

    /**
     * Send relayed lines to {@code target} instead of the {@code dev.frostlake.udf.console} logger and
     * return the logger it replaces — the seam a test uses to observe the relay. Not for production use.
     */
    static Logger relayTo(final Logger target) {
        final Logger previous = relayLogger;
        relayLogger = target;
        return previous;
    }

    private static void relay(final UdfConsoleScope scope) {
        if (!scope.beginRelay()) {
            return;
        }
        try {
            final Logger target = relayLogger;
            for (final String[] line : scope.pendingLines()) {
                target.info("[{}] {}", line[0], line[1]);
            }
            if (scope.droppedLines() > 0) {
                target.warn("{} more console lines from the handler were not logged (at most {} are kept"
                    + " until it returns)", scope.droppedLines(), MAX_PENDING_LINES);
            }
        } finally {
            scope.endRelay();
        }
    }

    private static synchronized void installIfNeeded() {
        if (!(System.out instanceof UdfConsolePrintStream)) {
            System.setOut(new UdfConsolePrintStream(new UdfConsoleStream("stdout", System.out)));
        }
        if (!(System.err instanceof UdfConsolePrintStream)) {
            System.setErr(new UdfConsolePrintStream(new UdfConsoleStream("stderr", System.err)));
        }
    }

    private UdfConsoleCapture() {
    }
}
