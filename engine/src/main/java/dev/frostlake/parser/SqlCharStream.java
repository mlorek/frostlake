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

package dev.frostlake.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * A text as the lexer reads it: one UTF-16 code unit at a time. A token's place — its start and stop index and
 * its position in its line — then counts a character outside the Basic Multilingual Plane twice, as the text's own
 * indexes do and as live's positions do: {@code SELECT '\uD83D\uDE00' x y} refuses the y at position 14, not 13
 * (live-verified). A stream of code points counted such a character once, so a position after one was a column
 * short, and a rule that rewrote the text by its tokens' indexes rewrote the wrong characters.
 */
public final class SqlCharStream implements CharStream {

    private final char[] text;

    private int position;

    private SqlCharStream(final String text) {
        this.text = text.toCharArray();
    }

    /**
     * The stream over a text.
     *
     * @param text the text
     * @return its stream, at the first character
     */
    public static CharStream of(final String text) {
        return new SqlCharStream(text);
    }

    @Override
    public void consume() {
        if (position >= text.length) {
            throw new IllegalStateException("cannot consume EOF");
        }
        position++;
    }

    @Override
    public int LA(final int offset) {
        if (offset == 0) {
            return 0;
        }
        final int at = offset < 0 ? position + offset : position + offset - 1;
        return at < 0 || at >= text.length ? IntStream.EOF : text[at];
    }

    @Override
    public int mark() {
        return -1;
    }

    @Override
    public void release(final int marker) {
        // Every character is kept, so a mark holds nothing to release.
    }

    @Override
    public int index() {
        return position;
    }

    @Override
    public void seek(final int index) {
        position = Math.max(0, Math.min(index, text.length));
    }

    @Override
    public int size() {
        return text.length;
    }

    @Override
    public String getSourceName() {
        return IntStream.UNKNOWN_SOURCE_NAME;
    }

    @Override
    public String getText(final Interval interval) {
        final int start = Math.max(0, interval.a);
        final int stop = Math.min(interval.b, text.length - 1);
        return start > stop ? "" : new String(text, start, stop - start + 1);
    }

    @Override
    public String toString() {
        return new String(text);
    }
}
