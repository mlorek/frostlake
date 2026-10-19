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

import java.util.Collections;
import java.util.List;

/**
 * A statement read with every word that names no type taken as one: the text with a known type standing in each
 * word's place, character for character, and the words in the order written. The text parses cleanly, so whatever
 * else the statement says of its types can be judged before a word is refused.
 */
public final class UnknownTypeReading {

    private final String substituted;
    private final List<UnknownTypeWord> words;
    private final boolean iceberg;

    /**
     * @param substituted the statement with a known type in each word's place
     * @param words       the words, in the order written
     * @param iceberg     whether the statement creates or alters an Iceberg table
     */
    UnknownTypeReading(final String substituted, final List<UnknownTypeWord> words, final boolean iceberg) {
        this.substituted = substituted;
        this.words = words;
        this.iceberg = iceberg;
    }

    /** The statement with a known type in each word's place, every other character where it was. */
    public String getSubstituted() {
        return substituted;
    }

    /** The words that name no type, in the order written. */
    public List<UnknownTypeWord> getWords() {
        return Collections.unmodifiableList(words);
    }

    /** Whether the statement creates or alters an Iceberg table, whose column types are read by name. */
    public boolean isIceberg() {
        return iceberg;
    }

    /**
     * The word the statement is refused for when nothing else about its types is: an Iceberg table's first, and
     * elsewhere the first written with parameters before the first written bare (live-verified).
     */
    public UnknownTypeWord refused() {
        if (!iceberg) {
            for (final UnknownTypeWord word : words) {
                if (word.isParameterized()) {
                    return word;
                }
            }
        }
        return words.get(0);
    }

    /** The sentence the statement is refused in for {@link #refused()}. */
    public String sentence() {
        return iceberg ? refused().icebergSentence() : refused().sentence();
    }
}
