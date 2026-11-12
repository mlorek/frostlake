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

package dev.frostlake.values;

/**
 * A text a function builds piece by piece that may hold no more characters than a text can (see {@link LobSize}): a
 * piece that would carry it past 128 MB is refused before it is appended, so a replacement that multiplies its
 * subject's length ends in an SQL error rather than in an {@code OutOfMemoryError} that takes the heap every other
 * session shares.
 */
public final class BoundedText {

    private final StringBuilder text;
    private long characters;
    /** What a refusal names in place of the text, or null to show the text. */
    private String named;
    /** The function a refusal names after the text it shows, or null for none. */
    private String function;

    /**
     * An empty text.
     *
     * @param capacity the characters to make room for at first
     */
    public BoundedText(final int capacity) {
        this.text = new StringBuilder((int) Math.min(capacity, LobSize.MAX_TEXT_BYTES));
    }

    /**
     * An empty text whose refusal names it, {@code String '(<named>)' is too long and would be truncated}.
     *
     * @param capacity the characters to make room for at first
     * @param named    what the refusal names
     * @return the text
     */
    public static BoundedText named(final int capacity, final String named) {
        final BoundedText bounded = new BoundedText(capacity);
        bounded.named = named;
        return bounded;
    }

    /**
     * An empty text whose refusal shows it and names the function that builds it, {@code ... in '<function>'}.
     *
     * @param capacity the characters to make room for at first
     * @param function the function named
     * @return the text
     */
    public static BoundedText builtBy(final int capacity, final String function) {
        final BoundedText bounded = new BoundedText(capacity);
        bounded.function = function;
        return bounded;
    }

    /**
     * Append part of a text.
     *
     * @param part the text
     * @param from where the part starts
     * @param to   where it ends, exclusive
     * @return this text
     */
    public BoundedText append(final CharSequence part, final int from, final int to) {
        final long grown = characters + Character.codePointCount(part, from, to);
        if (grown > LobSize.MAX_TEXT_BYTES) {
            if (named != null) {
                throw LobSize.tooLongNamed(named);
            }
            final StringBuilder beginning = new StringBuilder(text.subSequence(0,
                Math.min(text.length(), LobSize.shownBeginning())));
            beginning.append(part, from, Math.min(to, from + Math.max(0, LobSize.shownBeginning()
                - beginning.length())));
            if (function != null) {
                throw LobSize.tooLongIn(beginning.toString(), function);
            }
            LobSize.requireBuilt(beginning, grown);
        }
        text.append(part, from, to);
        characters = grown;
        return this;
    }

    /**
     * Append a text.
     *
     * @param part the text
     * @return this text
     */
    public BoundedText append(final CharSequence part) {
        return append(part, 0, part.length());
    }

    @Override
    public String toString() {
        return text.toString();
    }
}
