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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * The {@link OutputStream} behind {@link UdfConsolePrintStream}: bytes written by a thread inside a
 * UDF handler ({@link UdfConsoleCapture#isCapturing()}) are buffered per thread and relayed to the
 * engine log line by line; every other write passes straight through to the original stream. Carriage
 * returns are dropped so Windows-style line ends produce clean log lines.
 */
public final class UdfConsoleStream extends OutputStream {

    private final String streamName;
    private final PrintStream original;

    private final ThreadLocal<ByteArrayOutputStream> pendingLine = new ThreadLocal<ByteArrayOutputStream>() {
        @Override
        protected ByteArrayOutputStream initialValue() {
            return new ByteArrayOutputStream();
        }
    };

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
        captureByte((byte) b);
    }

    @Override
    public void write(final byte[] buf, final int off, final int len) {
        if (!UdfConsoleCapture.isCapturing()) {
            original.write(buf, off, len);
            return;
        }
        for (int i = off; i < off + len; i++) {
            captureByte(buf[i]);
        }
    }

    @Override
    public void flush() {
        original.flush();
    }

    private void captureByte(final byte b) {
        if (b == '\n') {
            final ByteArrayOutputStream pending = pendingLine.get();
            final String line = pending.toString(Charset.defaultCharset());
            pending.reset();
            UdfConsoleCapture.relayLine(streamName, line, original);
        } else if (b != '\r') {
            pendingLine.get().write(b);
        }
    }
}
