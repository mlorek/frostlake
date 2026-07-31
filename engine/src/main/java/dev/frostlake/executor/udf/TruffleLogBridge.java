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

import org.graalvm.polyglot.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Routes Truffle/polyglot log output into SLF4J — the "configure logging using the polyglot
 * embedding API" option: installed as the {@link Engine.Builder#logHandler(Handler) engine log
 * handler}, so GraalVM's warnings and diagnostics land in the engine log (with per-language logger
 * names under the {@code polyglot.} prefix) instead of being printed to stderr.
 *
 * <p>{@link #installOn(Engine.Builder)} respects the documented host-launcher override: when
 * {@code -Dpolyglot.log.file=<path>} is set, no handler is installed and the polyglot runtime
 * writes to that file itself.
 */
public final class TruffleLogBridge extends Handler {

    private static final String LOGGER_PREFIX = "polyglot.";

    /**
     * Add this bridge to {@code builder} unless the user asked for a log file via
     * {@code -Dpolyglot.log.file}, which the polyglot runtime should then honour directly.
     */
    public static void installOn(final Engine.Builder builder) {
        if (System.getProperty("polyglot.log.file") == null) {
            builder.logHandler(new TruffleLogBridge());
        }
    }

    @Override
    public void publish(final LogRecord record) {
        if (record == null) {
            return;
        }
        final String name = record.getLoggerName();
        final Logger target = LoggerFactory.getLogger(LOGGER_PREFIX + (name != null ? name : "truffle"));
        final String message = formatMessage(record);
        final Throwable thrown = record.getThrown();
        final int level = record.getLevel().intValue();
        if (level >= Level.SEVERE.intValue()) {
            target.error(message, thrown);
        } else if (level >= Level.WARNING.intValue()) {
            target.warn(message, thrown);
        } else if (level >= Level.INFO.intValue()) {
            target.info(message, thrown);
        } else if (level >= Level.FINE.intValue()) {
            target.debug(message, thrown);
        } else {
            target.trace(message, thrown);
        }
    }

    /** JUL messages may carry {@code {0}}-style parameters; render them like java.util.logging would. */
    private String formatMessage(final LogRecord record) {
        final String message = record.getMessage();
        final Object[] parameters = record.getParameters();
        if (message == null || parameters == null || parameters.length == 0) {
            return message;
        }
        try {
            return MessageFormat.format(message, parameters);
        } catch (final IllegalArgumentException e) {
            return message;
        }
    }

    @Override
    public void flush() {
        // SLF4J backends flush themselves.
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
