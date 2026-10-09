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

package dev.frostlake.functions.table;

/**
 * Which input record a table function is being called for — what the SEQ column of FLATTEN, SPLIT_TO_TABLE and
 * STRTOK_SPLIT_TO_TABLE reports. A call written over the rows of the relations to its left
 * ({@code FROM t, LATERAL FLATTEN(t.v)}, {@code FROM t, TABLE(SPLIT_TO_TABLE(t.c, ','))}) is made once per row,
 * and the rows are numbered from 1 in the order the call receives them: a row whose input yields nothing still
 * takes its number, a row a filter removed beforehand takes none, a second call chained after the first numbers
 * the rows the first produced, and every query block counts afresh (all live-verified). A call made on its own
 * serves one record, numbered 1.
 *
 * <p>The number belongs to the CALL SITE, not to the function, so the stage that iterates the left rows sets it
 * and the function only reads it. Scoped to the thread, as the statement's other per-row state is.
 */
public final class InputRecordSequence {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<Long>();

    private InputRecordSequence() {
    }

    /**
     * Note the record the calls that follow serve, returning the number it displaced so a nested evaluation can
     * put it back.
     *
     * @param sequence the record's number, from 1
     * @return the displaced number, or null when none was in force
     */
    public static Long begin(final long sequence) {
        final Long previous = CURRENT.get();
        CURRENT.set(Long.valueOf(sequence));
        return previous;
    }

    /**
     * Put back whatever {@link #begin} displaced.
     *
     * @param previous the displaced number; null clears
     */
    public static void end(final Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /**
     * The number of the record being served.
     *
     * @return the number, 1 when no call site set one
     */
    public static long current() {
        final Long sequence = CURRENT.get();
        return sequence == null ? 1L : sequence.longValue();
    }
}
