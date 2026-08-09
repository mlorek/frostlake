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

package dev.frostlake.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * The recursive character chunker behind SPLIT_TEXT_RECURSIVE_CHARACTER: split on the most structural
 * separator that appears, recurse into any piece still over the size bound with the next separator
 * down, then merge neighbouring pieces back up to — but not over — {@code chunkSize}, carrying
 * {@code overlap} characters of the previous chunk into the next.
 *
 * <p>No model is involved. Chunking a document for retrieval is arithmetic over the text, and running
 * it locally makes it deterministic and free, which is what a caller splitting a corpus wants.
 */
final class TextChunker {

    private TextChunker() {
    }

    /** The chunks, in order. An empty or blank text chunks to nothing. */
    static List<String> chunk(final String text, final List<String> separators,
                              final int chunkSize, final int overlap) {
        final List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (chunkSize <= 0) {
            throw new RuntimeException("chunk_size must be greater than zero.");
        }
        final int carried = Math.max(0, Math.min(overlap, chunkSize - 1));
        merge(split(text, separators, chunkSize), chunkSize, carried, chunks);
        return chunks;
    }

    /** Pieces that each fit the bound where the text allows it, splitting on ever finer separators. */
    private static List<String> split(final String text, final List<String> separators,
                                      final int chunkSize) {
        final List<String> pieces = new ArrayList<>();
        if (text.length() <= chunkSize || separators.isEmpty()) {
            if (!text.isEmpty()) {
                pieces.add(text);
            }
            return pieces;
        }
        final String separator = chosenSeparator(text, separators);
        final List<String> remaining = separators.subList(
            separators.indexOf(separator) + 1, separators.size());
        for (final String part : splitKeeping(text, separator)) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.length() <= chunkSize) {
                pieces.add(part);
            } else {
                pieces.addAll(split(part, remaining, chunkSize));
            }
        }
        return pieces;
    }

    /** The first separator the text actually contains, or the last one — which always cuts. */
    private static String chosenSeparator(final String text, final List<String> separators) {
        for (final String separator : separators) {
            if (separator.isEmpty() || text.contains(separator)) {
                return separator;
            }
        }
        return separators.get(separators.size() - 1);
    }

    /**
     * Split on the separator, keeping it at the START of each piece after the first. A markdown
     * heading IS its separator, so dropping it would lose the heading the chunk is about.
     */
    private static List<String> splitKeeping(final String text, final String separator) {
        final List<String> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            for (int i = 0; i < text.length(); i++) {
                parts.add(text.substring(i, i + 1));
            }
            return parts;
        }
        int from = 0;
        int at = text.indexOf(separator);
        while (at >= 0) {
            parts.add(text.substring(from, at));
            from = at;
            at = text.indexOf(separator, at + separator.length());
        }
        parts.add(text.substring(from));
        return parts;
    }

    /** Glue neighbouring pieces up to the bound, carrying the tail of each chunk into the next. */
    private static void merge(final List<String> pieces, final int chunkSize, final int overlap,
                              final List<String> chunks) {
        StringBuilder current = new StringBuilder();
        for (final String piece : pieces) {
            if (current.length() > 0 && current.length() + piece.length() > chunkSize) {
                emit(chunks, current.toString());
                final String tail = overlap == 0 ? ""
                    : current.substring(Math.max(0, current.length() - overlap));
                current = new StringBuilder(tail);
            }
            current.append(piece);
        }
        emit(chunks, current.toString());
    }

    /**
     * Add a chunk, TRIMMED. A split keeps its separator at the front of the following piece, which is
     * right for a separator that is part of the text — splitting {@code a|b|c} on {@code |} really does
     * give {@code ["a", "|b", "|c"]}, measured — but wrong for the whitespace separators the default
     * ladder is made of: the same measurement gives {@code ["The quick", "brown fox", …]} with no
     * leading space, and a markdown split gives {@code ["# Title\nsome text", "## Sub\nmore text"]}
     * with no leading newlines. Trimming the assembled chunk satisfies all three, where special-casing
     * whitespace separators during the split would not survive the mixed ladder.
     *
     * <p>A chunk that is nothing but whitespace disappears rather than being emitted empty.
     */
    private static void emit(final List<String> chunks, final String chunk) {
        final String trimmed = chunk.trim();
        if (!trimmed.isEmpty()) {
            chunks.add(trimmed);
        }
    }
}
