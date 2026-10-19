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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thread's {@link UdfConsoleCapture} state: how many handler calls it is inside, whether it is
 * relaying, the line each console stream is in the middle of, and the completed lines waiting for the
 * relay. Confined to its thread (held in a {@code ThreadLocal}), so it needs no locking — which is the
 * point: a capturing write runs under the console stream's monitor and must not take any other lock.
 */
final class UdfConsoleScope {

    private final int maxPendingLines;
    private final Map<String, ByteArrayOutputStream> partialLines = new LinkedHashMap<>();
    private final List<String[]> pendingLines = new ArrayList<>();
    private int depth;
    private boolean relaying;
    private int droppedLines;

    UdfConsoleScope(final int maxPendingLines) {
        this.maxPendingLines = maxPendingLines;
    }

    void enter() {
        depth++;
    }

    /** Leave one handler call; leaving the outermost also ends each stream's unterminated line. */
    void exit() {
        if (depth > 0) {
            depth--;
        }
        if (depth == 0) {
            for (final Map.Entry<String, ByteArrayOutputStream> partial : partialLines.entrySet()) {
                if (partial.getValue().size() > 0) {
                    completeLine(partial.getKey(), partial.getValue());
                }
            }
        }
    }

    boolean isCapturing() {
        return depth > 0 && !relaying;
    }

    void capture(final String streamName, final int b) {
        accept(streamName, partial(streamName), (byte) b);
    }

    void capture(final String streamName, final byte[] buf, final int off, final int len) {
        final ByteArrayOutputStream partial = partial(streamName);
        for (int i = off; i < off + len; i++) {
            accept(streamName, partial, buf[i]);
        }
    }

    /** Start a relay; false when nothing is waiting or this thread is already relaying. */
    boolean beginRelay() {
        if (relaying || (pendingLines.isEmpty() && droppedLines == 0)) {
            return false;
        }
        relaying = true;
        return true;
    }

    /** The completed lines, oldest first, each as {@code {streamName, line}}. */
    List<String[]> pendingLines() {
        return pendingLines;
    }

    /** Lines completed after {@code maxPendingLines} were already waiting; counted instead of kept. */
    int droppedLines() {
        return droppedLines;
    }

    void endRelay() {
        pendingLines.clear();
        droppedLines = 0;
        relaying = false;
    }

    private void accept(final String streamName, final ByteArrayOutputStream partial, final byte b) {
        if (b == '\n') {
            completeLine(streamName, partial);
        } else if (b != '\r') {
            partial.write(b);
        }
    }

    private ByteArrayOutputStream partial(final String streamName) {
        ByteArrayOutputStream partial = partialLines.get(streamName);
        if (partial == null) {
            partial = new ByteArrayOutputStream();
            partialLines.put(streamName, partial);
        }
        return partial;
    }

    private void completeLine(final String streamName, final ByteArrayOutputStream partial) {
        final String line = partial.toString(Charset.defaultCharset());
        partial.reset();
        if (pendingLines.size() < maxPendingLines) {
            pendingLines.add(new String[] {streamName, line});
        } else {
            droppedLines++;
        }
    }
}
