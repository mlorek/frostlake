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

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Stand-in for the {@code dev.frostlake.udf.console} logger in {@link UdfConsoleCaptureTest}. It records
 * each relayed message as {@code "LEVEL message"} and notes whether the relaying thread held a console
 * stream's monitor at the time. Like a console appender such as slf4j-simple, it does its work under a
 * lock of its own ({@link #lock()}), which a test can hold to play a thread that is logging.
 */
final class ConsoleRelayRecorder extends LegacyAbstractLogger {

    private static final long serialVersionUID = 1L;

    private final transient Object lock = new Object();
    private final transient CountDownLatch relayStarted = new CountDownLatch(1);
    private final transient List<String> messages = new ArrayList<>();
    private volatile boolean relayedUnderConsoleMonitor;

    ConsoleRelayRecorder() {
        this.name = "dev.frostlake.udf.console";
    }

    /** The lock every relayed message is recorded under. */
    Object lock() {
        return lock;
    }

    /** Counted down when the first message arrives, before the recorder takes {@link #lock()}. */
    CountDownLatch relayStarted() {
        return relayStarted;
    }

    /** A snapshot of the messages recorded so far, oldest first. */
    List<String> messages() {
        synchronized (lock) {
            return new ArrayList<>(messages);
        }
    }

    boolean relayedUnderConsoleMonitor() {
        return relayedUnderConsoleMonitor;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(final Level level, final Marker marker, final String messagePattern,
                                               final Object[] arguments, final Throwable throwable) {
        if (Thread.holdsLock(System.out) || Thread.holdsLock(System.err)) {
            relayedUnderConsoleMonitor = true;
        }
        relayStarted.countDown();
        synchronized (lock) {
            messages.add(level + " " + MessageFormatter.basicArrayFormat(messagePattern, arguments));
        }
    }
}
