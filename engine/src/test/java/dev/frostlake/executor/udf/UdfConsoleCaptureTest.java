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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link UdfConsoleCapture}: a handler's console lines reach the log once the handler
 * returns — never from inside the write that printed them, which runs holding the console stream's
 * monitor — and output written outside a handler passes straight through. The end-to-end path through
 * a compiled Java UDF is {@code JarHandlerTest#handlerConsoleOutputIsCapturedNotPrinted}.
 */
public class UdfConsoleCaptureTest {

    private static final long WAIT_SECONDS = 10;

    private PrintStream priorOut;
    private PrintStream priorErr;
    private Logger priorRelay;
    private ByteArrayOutputStream consoleOut;
    private ByteArrayOutputStream consoleErr;
    private ConsoleRelayRecorder relay;

    @BeforeEach
    public void installConsoleAndRecorder() {
        priorOut = System.out;
        priorErr = System.err;
        consoleOut = new ByteArrayOutputStream();
        consoleErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(consoleOut, true));
        System.setErr(new PrintStream(consoleErr, true));
        relay = new ConsoleRelayRecorder();
        priorRelay = UdfConsoleCapture.relayTo(relay);
    }

    @AfterEach
    public void restoreConsoleAndLogger() {
        UdfConsoleCapture.relayTo(priorRelay);
        System.setOut(priorOut);
        System.setErr(priorErr);
    }

    @Test
    public void handlerLinesAreLoggedWhenItReturnsAndNeverPrinted() {
        UdfConsoleCapture.enter();
        try {
            System.out.println("udf-first");
            System.err.println("udf-second");
            assertTrue(relay.messages().isEmpty(), "logged from inside the handler: " + relay.messages());
        } finally {
            UdfConsoleCapture.exit();
        }
        System.out.println("outside-handler");

        assertEquals(List.of("INFO [stdout] udf-first", "INFO [stderr] udf-second"), relay.messages());
        assertFalse(relay.relayedUnderConsoleMonitor(), "relayed while holding a console stream's monitor");
        assertFalse(consoleOut.toString().contains("udf-first"), "UDF stdout leaked: " + consoleOut);
        assertFalse(consoleErr.toString().contains("udf-second"), "UDF stderr leaked: " + consoleErr);
        assertTrue(consoleOut.toString().contains("outside-handler"), "non-UDF write lost: " + consoleOut);
    }

    @Test
    public void stackTracePrintedUnderTheStreamMonitorIsLoggedLineByLine() {
        // Throwable.printStackTrace holds the stream's monitor for the whole trace, so no relay that runs
        // inside the write could ever avoid holding it.
        UdfConsoleCapture.enter();
        try {
            new IllegalStateException("handler-trace").printStackTrace();
        } finally {
            UdfConsoleCapture.exit();
        }

        final List<String> messages = relay.messages();
        assertEquals("INFO [stderr] java.lang.IllegalStateException: handler-trace", messages.get(0));
        assertTrue(messages.size() > 1 && messages.get(1).startsWith("INFO [stderr] \tat "), messages.toString());
        assertFalse(relay.relayedUnderConsoleMonitor(), "relayed while holding a console stream's monitor");
        assertFalse(consoleErr.toString().contains("handler-trace"), "UDF stderr leaked: " + consoleErr);
    }

    @Test
    public void unterminatedLineIsLoggedWhenTheOutermostHandlerReturns() {
        UdfConsoleCapture.enter();
        try {
            System.out.print("outer ");
            UdfConsoleCapture.enter();
            try {
                System.out.print("inner\r\nnext ");
            } finally {
                UdfConsoleCapture.exit();
            }
            // A nested return relays the completed lines; the line still being written stays open.
            assertEquals(List.of("INFO [stdout] outer inner"), relay.messages());
            System.out.print("tail");
        } finally {
            UdfConsoleCapture.exit();
        }

        assertEquals(List.of("INFO [stdout] outer inner", "INFO [stdout] next tail"), relay.messages());
    }

    @Test
    public void linesBeyondTheBufferLimitAreCountedNotKept() {
        final int limit = UdfConsoleCapture.MAX_PENDING_LINES;
        UdfConsoleCapture.enter();
        try {
            for (int i = 0; i < limit + 3; i++) {
                System.out.println("line " + i);
            }
        } finally {
            UdfConsoleCapture.exit();
        }

        final List<String> messages = relay.messages();
        assertEquals(limit + 1, messages.size());
        assertEquals("INFO [stdout] line " + (limit - 1), messages.get(limit - 1));
        assertTrue(messages.get(limit).startsWith("WARN 3 more console lines"), messages.get(limit));
    }

    @Test
    public void loggerWritingToTheConsoleUnderItsLockDoesNotDeadlockAPrintingHandler() throws Exception {
        // The hang seen running a vendor test suite: one thread logged through slf4j-simple, which holds
        // its own lock while it writes to System.err, just as a UDF on another thread printed to
        // System.err and the capture logged that line while still holding the stream's monitor. Each
        // thread then waited for the lock the other held. The recorder plays the appender's lock.
        final CountDownLatch loggerHoldsItsLock = new CountDownLatch(1);
        final AtomicReference<String> problem = new AtomicReference<>();
        UdfConsoleCapture.enter();
        UdfConsoleCapture.exit();

        final Thread logger = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (relay.lock()) {
                    loggerHoldsItsLock.countDown();
                    if (!await(relay.relayStarted())) {
                        problem.set("the handler's line was never relayed");
                    }
                    System.err.println("logger-line");
                }
            }
        }, "console-logger");
        final Thread handler = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!await(loggerHoldsItsLock)) {
                    problem.set("the logger thread never took its lock");
                    return;
                }
                UdfConsoleCapture.enter();
                try {
                    System.err.println("handler-line");
                } finally {
                    UdfConsoleCapture.exit();
                }
            }
        }, "udf-handler");
        // Daemons, so a regression that deadlocks them fails this test without keeping the JVM alive.
        logger.setDaemon(true);
        handler.setDaemon(true);
        logger.start();
        handler.start();
        logger.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS * 2));
        handler.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertFalse(logger.isAlive() || handler.isAlive(),
            "deadlocked: logger " + logger.getState() + ", handler " + handler.getState());
        assertNull(problem.get());
        assertEquals(List.of("INFO [stderr] handler-line"), relay.messages());
        assertTrue(consoleErr.toString().contains("logger-line"), "logger write lost: " + consoleErr);
        assertFalse(consoleErr.toString().contains("handler-line"), "UDF stderr leaked: " + consoleErr);
    }

    private static boolean await(final CountDownLatch latch) {
        try {
            return latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
