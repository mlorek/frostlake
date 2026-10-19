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

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * The {@link OutputStream} behind {@link UdfConsolePrintStream}: bytes written by a thread inside a
 * UDF handler ({@link UdfConsoleCapture#isCapturing()}) go to {@link UdfConsoleCapture}, which buffers
 * them per thread and logs the lines when the handler returns; every other write passes straight through
 * to the original stream. A capturing write only buffers — it runs under the {@code PrintStream}'s
 * monitor, where taking a log appender's lock could deadlock (see {@link UdfConsoleCapture}).
 */
public final class UdfConsoleStream extends OutputStream {

    private final String streamName;
    private final PrintStream original;

    public UdfConsoleStream(final String streamName, final PrintStream original) {
        this.streamName = streamName;
        this.original = original;
    }

    @Override
    public void write(final int b) {
        if (!UdfConsoleCapture.isCapturing()) {
            original.write(b);
            return;
        }
        UdfConsoleCapture.capture(streamName, b);
    }

    @Override
    public void write(final byte[] buf, final int off, final int len) {
        if (!UdfConsoleCapture.isCapturing()) {
            original.write(buf, off, len);
            return;
        }
        UdfConsoleCapture.capture(streamName, buf, off, len);
    }

    @Override
    public void flush() {
        original.flush();
    }
}
