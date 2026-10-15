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

import java.util.List;
import java.util.Locale;

/**
 * One word of a statement that stands where a data type belongs and names none, as the account refuses it. A word
 * written bare is a name, resolved once the statement has parsed: the last part of a dotted one, upper-cased, a
 * quoted one as written. A word written with parameters is a type the account builds as it reads, and is echoed as
 * it was written. An Iceberg table's column list reads a bare word by its first part, quotes kept (live-verified):
 *
 * <pre>
 *   (c nosuchtype)       Unsupported data type 'NOSUCHTYPE'.
 *   (c x.nosuch)         Unsupported data type 'NOSUCH'.
 *   (c "nosuch")         Unsupported data type 'nosuch'.
 *   (c NoSuch(5, 2))     Unsupported data type 'NoSuch(5, 2)'.
 *   Iceberg (c x.nosuch) Unsupported data type 'x' for iceberg tables.
 * </pre>
 */
public final class UnknownTypeWord {

    private final int start;
    private final String firstPart;
    private final String lastPart;
    private final boolean lastQuoted;
    private final List<String> parameters;
    private final boolean mapKey;

    /**
     * @param start      the character the word starts at
     * @param firstPart  its first dotted part as written, quotes kept
     * @param lastPart   its last dotted part, a quoted one without its quotes
     * @param lastQuoted whether the last part was written quoted
     * @param parameters the parameters written after it, or null for a bare word
     * @param mapKey     whether the word stands as the key type of a MAP
     */
    UnknownTypeWord(final int start, final String firstPart, final String lastPart, final boolean lastQuoted,
                    final List<String> parameters, final boolean mapKey) {
        this.start = start;
        this.firstPart = firstPart;
        this.lastPart = lastPart;
        this.lastQuoted = lastQuoted;
        this.parameters = parameters;
        this.mapKey = mapKey;
    }

    /** The character the word starts at. */
    public int getStart() {
        return start;
    }

    /** Whether the word was written with parameters. */
    public boolean isParameterized() {
        return parameters != null;
    }

    /** The sentence the word is refused in where any type may stand. */
    public String sentence() {
        if (mapKey && parameters == null) {
            // A MAP whose key names no type is refused as the MAP it is (live-verified).
            return "Unsupported data type 'MAP with key type UNKNOWN'.";
        }
        if (parameters != null) {
            return "Unsupported data type '" + lastPart + "(" + String.join(", ", parameters) + ")'.";
        }
        return "Unsupported data type '" + (lastQuoted ? lastPart : lastPart.toUpperCase(Locale.ROOT)) + "'.";
    }

    /** The sentence the word is refused in as the type of an Iceberg table's column. */
    public String icebergSentence() {
        return parameters != null || mapKey ? sentence()
            : "Unsupported data type '" + firstPart + "' for iceberg tables.";
    }

    /** Whether the word's sentence is one any table gives, an Iceberg table's column list included. */
    public boolean isRefusedAlike() {
        return parameters != null || mapKey;
    }
}
