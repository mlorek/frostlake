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

import java.io.IOException;
import java.io.Writer;

/**
 * A writer that keeps the first characters written to it and stops the writing, by failing it, once it holds them: so
 * the beginning of a text too long to be made whole is read without making the rest.
 */
final class PrefixWriter extends Writer {

    private final StringBuilder kept = new StringBuilder();
    private final int wanted;

    /**
     * @param wanted how many characters to keep
     */
    PrefixWriter(final int wanted) {
        this.wanted = wanted;
    }

    @Override
    public void write(final char[] buffer, final int offset, final int length) throws IOException {
        kept.append(buffer, offset, Math.min(wanted - kept.length(), length));
        if (kept.length() >= wanted) {
            throw new IOException("prefix complete");
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    /** The characters kept: the text's beginning, or the whole text when it is shorter. */
    String kept() {
        return kept.toString();
    }
}
