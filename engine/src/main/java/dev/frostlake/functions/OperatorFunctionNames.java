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

package dev.frostlake.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Built-in function <em>names</em> Snowflake gives to operators and grammar forms rather than to callable
 * functions — {@code +}, {@code IS NULL}, {@code ||}, {@code LIKE_ANY}, {@code COUNT(*)}, … A real account
 * lists all of them in {@code SHOW BUILTIN FUNCTIONS} (the 30 non-word names it
 * returns are {@code != % * + - / : < <= <> = > >= COUNT(*) COUNT_INTERNAL(*) [] ||}, the four
 * {@code INTERVAL … } arithmetic entries, {@code IS NULL}, {@code IS NOT NULL},
 * {@code OBJECT_CONSTRUCT(*)}, {@code OBJECT_CONSTRUCT_KEEP_NULL(*)} and
 * {@code OBJECT_CONSTRUCT_KEEP_NULL_STRUCTURED(*)}, alongside word-shaped operator names such as
 * {@code AND}, {@code OR}, {@code NOT}, {@code BETWEEN}, {@code IN}, {@code LIKE_ANY} and {@code DIV}).
 *
 * <p>Unlike the other name sets feeding {@link FunctionRegistry#allDispatchableNames()}, this one cannot
 * be load-bearing for dispatch — the grammar implements these, not a name lookup. Membership is therefore
 * a deliberate, curated claim with two conditions, and {@code OperatorFunctionNamesTest} re-checks the
 * second one for every entry:
 *
 * <ol>
 *   <li>Snowflake lists the name (checked against the 926 names its {@code SHOW BUILTIN FUNCTIONS}
 * returned), and</li>
 *   <li>Frostlake implements the corresponding operator form.</li>
 * </ol>
 *
 * <p>Names that fail either test stay out. {@code DIV} is the instructive one: Snowflake lists it, but it
 * is not invocable there either ({@code SELECT DIV(10, 3)} answers "Unsupported feature 'DIV'") and
 * Frostlake has no {@code DIV} in any form — {@code 10 DIV 3} is a syntax error — so it is not listed.
 * {@code ILIKE_ALL} is absent from Snowflake's own listing and rejected by it
 * ("Unknown function ILIKE_ALL"), matching Frostlake's grammar, which allows {@code ILIKE ANY} but not
 * {@code ILIKE ALL}. The interval-arithmetic quartets are split for the same reason: Frostlake evaluates
 * {@code <timestamp> + INTERVAL '1 day'} and the matching subtraction, so the PLUS and MINUS entries are
 * listed, but {@code INTERVAL '1 day' * 2} and {@code INTERVAL '2 day' / 2} do not parse, so MULTIPLY and
 * DIVIDE are not.
 *
 * <p>These names are also why {@code LIKE_ANY} is genuinely supported rather than merely listed:
 * Snowflake's {@code LIKE_ANY} is the function name of the {@code x LIKE ANY (…)} operator
 * ({@code SELECT LIKE_ANY('abc', 'a%')} answers "not enough arguments for function
 * ['abc' LIKE_ANY 'a%'], expected 3, got 2"), and Frostlake evaluates that operator.
 */
public final class OperatorFunctionNames {

    private static final SortedSet<String> NAMES = Collections.unmodifiableSortedSet(
        new TreeSet<String>(Arrays.asList(
            "!=",
            "%",
            "*",
            "+",
            "-",
            "/",
            ":",
            "<",
            "<=",
            "<>",
            "=",
            ">",
            ">=",
            "AND",
            "BETWEEN",
            "CASE",
            "COUNT(*)",
            "ILIKE_ANY",
            "IN",
            "INTERVAL DAY TIME MINUS",
            "INTERVAL DAY TIME PLUS",
            "INTERVAL YEAR MONTH MINUS",
            "INTERVAL YEAR MONTH PLUS",
            "IS NOT NULL",
            "IS NULL",
            "LIKE_ALL",
            "LIKE_ANY",
            "NOT",
            "OBJECT_CONSTRUCT(*)",
            "OBJECT_CONSTRUCT_KEEP_NULL(*)",
            "OR",
            "REGEXP",
            "[]",
            "||"
        )));

    private OperatorFunctionNames() {
    }

    /** Whether this name is one of the operator / grammar forms the listing declares. */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase());
    }

    /** Every operator-implemented built-in name, sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
