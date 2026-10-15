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

package dev.frostlake.executor.expressions;

/**
 * The refusal of a bare word standing where a function reads a date or time unit first — DATEADD, DATEDIFF,
 * DATE_TRUNC, DATE_PART and their aliases — when the word names no unit. A query is refused by the function's own
 * sentence, which this carries; a block's own expression is refused for the word instead, as the name nothing
 * declares that it is there, and with no place of its own (live-verified):
 * <pre>
 *   SELECT DATEADD(nosuch, 1, d)                ['NOSUCH'] is not a valid date/time component for function DATEADD.
 *   BEGIN RETURN DATEADD(nosuch, 1, d); END;    … error line 1 at position 0 / invalid identifier 'NOSUCH'
 * </pre>
 */
public final class UnknownUnitWord extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** How far a failure's causes are searched for the refusal. */
    private static final int DEPTH = 8;

    private final String word;

    /**
     * @param refusal the function's own refusal of the word
     * @param word    the word as the refusal names it
     */
    public UnknownUnitWord(final RuntimeException refusal, final String word) {
        super(refusal.getMessage());
        this.word = word;
    }

    /**
     * @return the word that names no unit
     */
    public String word() {
        return word;
    }

    /**
     * @param failure a failure
     * @return the refusal of a unit's word that it is or was raised for, or null
     */
    public static UnknownUnitWord in(final Throwable failure) {
        Throwable at = failure;
        for (int i = 0; i < DEPTH && at != null; i++) {
            if (at instanceof UnknownUnitWord) {
                return (UnknownUnitWord) at;
            }
            at = at.getCause();
        }
        return null;
    }
}
