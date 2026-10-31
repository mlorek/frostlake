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

package dev.frostlake.executor;

/**
 * A sentence the CURRENT statement's DDL handler adds after its status sentence: a dynamic table created with
 * REFRESH_MODE AUTO that settles on FULL says why ("FULL refresh mode was selected because: …", live-verified).
 * Held in a ThreadLocal and consumed per statement, as {@link ConditionalDdlOutcome} is.
 */
public final class DdlStatusNote {

    private static final ThreadLocal<String> NOTE = new ThreadLocal<String>();

    private DdlStatusNote() {
    }

    /**
     * Record the sentence the statement's status adds.
     *
     * @param sentence the sentence
     */
    public static void add(final String sentence) {
        NOTE.set(sentence);
    }

    /** Clear whatever an earlier statement may have left, before a statement runs. */
    public static void clear() {
        NOTE.remove();
    }

    /**
     * The sentence the statement just executed adds, consumed.
     *
     * @return the sentence, or null when it adds none
     */
    public static String take() {
        final String note = NOTE.get();
        NOTE.remove();
        return note;
    }
}
