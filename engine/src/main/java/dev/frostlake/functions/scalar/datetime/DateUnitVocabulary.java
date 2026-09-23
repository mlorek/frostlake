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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;

/**
 * Which unit words the date functions read, asked in one place so that the statement's compilation and
 * the row's computation give the same answer. Every spelling {@link IntervalUnit} carries is a unit of
 * DATEADD, DATEDIFF, DATE_TRUNC and TRUNC and their aliases — {@code w} a WEEK among them — except the
 * week COMPONENTS {@code woy}, {@code weekofyear} and {@code wy}, which only DATE_PART, EXTRACT and
 * INTERVAL read. TIME_SLICE takes the same words from a second up to a year, and no fraction of a
 * second. A word outside a function's vocabulary is refused while the statement compiles, over an empty
 * table too: {@code DATEADD(wy, 1, d)} is "['WY'] is not a valid date/time component for function
 * DATEADD." (all live-verified).
 */
public final class DateUnitVocabulary {

    private DateUnitVocabulary() {
    }

    /**
     * The interval a word names for DATEADD, DATEDIFF, DATE_TRUNC and TRUNC.
     *
     * @param word the unit as written, in any case
     * @return the unit, or null when the word names none or names a component only
     */
    public static IntervalUnit intervalUnit(final Object word) {
        if (SharedFunctionHelpers.isComponentOnlyUnit(word)) {
            return null;
        }
        return IntervalUnit.fromSpelling(SharedFunctionHelpers.canonicalDateUnit(word));
    }

    /**
     * The slice a word names for TIME_SLICE: {@code 'W'}, {@code 'WK'} and {@code 'WEEKS'} are a week,
     * {@code 'MS'} and {@code 'NS'} are refused.
     *
     * @param word the unit as written, in any case
     * @return the unit, or null when TIME_SLICE does not read the word
     */
    public static IntervalUnit sliceUnit(final Object word) {
        final IntervalUnit unit = intervalUnit(word);
        return unit == null || unit == IntervalUnit.MILLISECOND || unit == IntervalUnit.MICROSECOND
            || unit == IntervalUnit.NANOSECOND ? null : unit;
    }
}
