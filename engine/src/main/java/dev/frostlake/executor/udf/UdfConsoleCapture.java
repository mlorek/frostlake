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

import java.io.PrintStream;

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
 */
public final class UdfConsoleCapture {

    private static final Logger logger = LoggerFactory.getLogger("dev.frostlake.udf.console");

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    private static final ThreadLocal<boolean[]> RELAYING = new ThreadLocal<boolean[]>() {
        @Override
        protected boolean[] initialValue() {
            return new boolean[1];
        }
    };

    /** Mark the current thread as executing a UDF handler; console writes are captured until {@link #exit()}. */
    public static void enter() {
        installIfNeeded();
        DEPTH.get()[0]++;
    }

    /** Leave the handler scope entered by {@link #enter()} (call from a {@code finally} block). */
    public static void exit() {
        final int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    /** True when the current thread's console output should be captured rather than passed through. */
    static boolean isCapturing() {
        return DEPTH.get()[0] > 0 && !RELAYING.get()[0];
    }

    /** Log one captured line; falls back to the original stream if the logger itself is mid-relay. */
    static void relayLine(final String streamName, final String line, final PrintStream original) {
        final boolean[] relaying = RELAYING.get();
        if (relaying[0]) {
            original.println(line);
            return;
        }
        relaying[0] = true;
        try {
            logger.info("[{}] {}", streamName, line);
        } finally {
            relaying[0] = false;
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
