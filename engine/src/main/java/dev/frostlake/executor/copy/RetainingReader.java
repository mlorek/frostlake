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

package dev.frostlake.executor.copy;

import java.io.IOException;
import java.io.Reader;

/**
 * A reader that keeps what it has read from a given offset on, so a parser reading ahead through it can still
 * look back at the text of the record it is in: where a JSON key written with escapes ends.
 */
final class RetainingReader extends Reader {

    private final Reader source;

    /** The characters read since {@link #base}. */
    private final StringBuilder kept = new StringBuilder();

    /** The offset, in the whole text, of the first kept character. */
    private long base;

    /**
     * A reader over a source.
     *
     * @param source the source
     */
    RetainingReader(final Reader source) {
        this.source = source;
    }

    @Override
    public int read(final char[] buffer, final int offset, final int length) throws IOException {
        final int count = source.read(buffer, offset, length);
        if (count > 0) {
            kept.append(buffer, offset, count);
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    /**
     * The character at an offset of the whole text, or -1 when it is not kept.
     *
     * @param offset the offset, from 0
     * @return the character
     */
    int charAt(final long offset) {
        final long index = offset - base;
        return index < 0 || index >= kept.length() ? -1 : kept.charAt((int) index);
    }

    /**
     * Stop keeping what comes before an offset.
     *
     * @param offset the offset, from 0
     */
    void discardBefore(final long offset) {
        final long drop = Math.min(offset - base, kept.length());
        if (drop > 0) {
            kept.delete(0, (int) drop);
            base += drop;
        }
    }
}
