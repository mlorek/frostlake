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

import dev.frostlake.functions.SemiStructuredRejection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The functions that refuse an interval argument while the statement compiles, keyed by the registered
 * function's own name so every alias of one follows it (live-verified, a day-time and a year-month interval
 * alike):
 *
 * <pre>
 *   UPPER, LOWER, LENGTH, SUBSTR, CONCAT, CONCAT_WS, TRIM, LTRIM, RTRIM, PARSE_JSON, LISTAGG
 *                                                              incompatible types: [INTERVAL DAY(9) TO SECOND(9)] and [VARCHAR(134217728)]
 *   ARRAY_CONSTRUCT(_COMPACT), OBJECT_CONSTRUCT, ARRAY_AGG    Function ARRAY_CONSTRUCT does not support INTERVAL DAY(9) argument type
 *   DATE_TRUNC, TRUNC                                          Function DATE_TRUNC does not support INTERVAL DAY(9) TO SECOND(9) argument type
 *   TO_VARIANT, TO_ARRAY, TO_OBJECT                            invalid type [TO_VARIANT(TO_INTERVAL_DAY_TIME('1'))] for parameter 'TO_VARIANT'
 *   TO_JSON, OBJECT_AGG                                        Invalid argument types for function 'TO_JSON': (INTERVAL DAY(9) TO SECOND(9))
 *   VARIANCE, STDDEV, KURTOSIS                                 Invalid argument types for function '*': (… , …)
 * </pre>
 *
 * <p>An interval converts to no text implicitly, so a function reading its argument as text refuses it in the
 * conversion's words. The other shapes are the ones those functions already give an OBJECT.
 */
final class IntervalArgumentRules {

    /** The text functions, each reading its first argument as text. */
    private static final Set<String> FIRST_ARGUMENT_TEXT = new HashSet<>(Arrays.asList(
        "UPPER", "LOWER", "LENGTH", "SUBSTRING", "PARSE_JSON", "LISTAGG", "TRIM", "LTRIM", "RTRIM"));

    /** The text functions reading every argument as text. */
    private static final Set<String> EVERY_ARGUMENT_TEXT = new HashSet<>(Arrays.asList("CONCAT", "CONCAT_WS"));

    private static final Set<String> CONSTRUCTORS = new HashSet<>(Arrays.asList(
        "ARRAY_CONSTRUCT", "ARRAY_CONSTRUCT_COMPACT", "ARRAY_AGG"));

    private static final Set<String> CONVERSIONS = new HashSet<>(Arrays.asList(
        "TO_VARIANT", "TO_ARRAY", "TO_OBJECT"));

    private static final Set<String> SQUARES = new HashSet<>(Arrays.asList(
        "VARIANCE", "STDDEV", "KURTOSIS"));

    private IntervalArgumentRules() {
    }

    /**
     * @param name     the registered function's name
     * @param position the argument's position, counted from zero
     * @return whether the position reads text, where an interval is refused as incompatible with VARCHAR
     */
    static boolean readsText(final String name, final int position) {
        return EVERY_ARGUMENT_TEXT.contains(name) || position == 0 && FIRST_ARGUMENT_TEXT.contains(name);
    }

    /**
     * @param name     the registered function's name
     * @param position the argument's position, counted from zero
     * @return how the position refuses an interval, or NONE
     */
    static SemiStructuredRejection rejection(final String name, final int position) {
        if (CONSTRUCTORS.contains(name) || "OBJECT_CONSTRUCT".equals(name) && position % 2 == 1
                || "DATE_TRUNC".equals(name) && position == 1 || "TRUNC".equals(name) && position == 0) {
            return SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE;
        }
        if (CONVERSIONS.contains(name)) {
            return SemiStructuredRejection.INVALID_TYPE_PARAMETER;
        }
        if ("TO_JSON".equals(name) || "OBJECT_AGG".equals(name) && position == 1) {
            return SemiStructuredRejection.ARGUMENT_TYPES;
        }
        return SQUARES.contains(name) ? SemiStructuredRejection.MULTIPLY_OPERANDS : SemiStructuredRejection.NONE;
    }
}
