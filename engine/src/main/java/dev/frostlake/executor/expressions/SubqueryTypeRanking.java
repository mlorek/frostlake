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
 * A thread-local mode, entered while a query compiles the subqueries its clauses hold, in which a subquery's
 * refusal about TYPES is raised as the subquery compiles instead of waiting for the query around it: live settles a
 * subquery whole — its names, argument and predicate types, ORDER BY and GROUP BY positions, arity and constant
 * arguments — before it judges the enclosing query's own types, so {@code WHERE 'o' + TRUE = 1 AND a = (SELECT
 * 'x' + TRUE)} is the subquery's '+'. What a subquery refuses about PLACEMENT — an aggregate or a window where
 * none may stand, the grouped select list, a QUALIFY without a window — still waits behind the enclosing query's
 * types (live-verified, each kind measured against an enclosing argument type written before the subquery).
 *
 * <p>The mode belongs to one query's compilation of its own clause subqueries: a subquery compiling inside it runs
 * with the mode off and enters it again for its own clauses.
 */
public final class SubqueryTypeRanking {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private SubqueryTypeRanking() {
    }

    /** Whether a subquery compiling now raises its type refusals at once. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Enter ({@code active}) or leave the mode; hands back the state to put back with {@link #end}. */
    public static boolean begin(final boolean active) {
        final boolean previous = isActive();
        if (active) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
        return previous;
    }

    public static void end(final boolean previous) {
        if (previous) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
    }

    /**
     * What a query reports when computing its select items as it plans them — a FROM-less select does — raised
     * {@code raised} while a subquery of its items or its WHERE waits with {@code waiting}: the subquery's refusal
     * when it is one that ranks ahead of the enclosing query's types and what the items raised is no unresolvable
     * name, and what they raised otherwise. So {@code SELECT 'o' + TRUE, (SELECT 'x' + TRUE)} is the subquery's '+',
     * and a correlated subquery nested in a FROM-less one is refused for its argument types ahead of its shape,
     * while {@code SELECT nosuch, (SELECT 'x' + TRUE)} is the name (live-verified).
     *
     * @param waiting the refusal a subquery's compilation handed back, or null
     * @param raised  what computing the items raised
     * @return the refusal to report
     */
    public static RuntimeException ahead(final RuntimeException waiting, final RuntimeException raised) {
        if (waiting == null || !ranksAheadOfEnclosingTypes(waiting.getMessage())
                || SubqueryEvaluator.refusesAName(String.valueOf(raised.getMessage()))) {
            return raised;
        }
        return waiting;
    }

    /**
     * Whether a subquery's compilation refusal is one live raises ahead of the enclosing query's types: an argument
     * type, a predicate type, a position past the select list in ORDER BY or GROUP BY, an argument count, an argument
     * that must be constant (GETVARIABLE's name excepted — live judges that one last of all), a conversion or
     * parameter type, an ordered-set call a function does not support, and a set operation's column count. A
     * grouping refusal shares the GROUP BY sentence, so only a bracketed position counts there.
     */
    static boolean ranksAheadOfEnclosingTypes(final String message) {
        if (message == null) {
            return false;
        }
        if (message.contains("Invalid argument types for function")
                || message.contains("too many arguments for function")
                || message.contains("not enough arguments for function")
                || message.contains("] for predicate [")
                || message.contains("is not a valid order by expression")
                || message.contains("Can not convert parameter")
                || message.contains("does not support WITHIN GROUP clause")
                || message.contains("invalid number of result columns for set operator input branches")
                || message.contains("] for parameter '")) {
            return true;
        }
        if (message.contains("needs to be constant, found")) {
            return !message.contains("to function GETVARIABLE needs to be constant");
        }
        return positionalGroupKey(message);
    }

    /** Whether a GROUP BY refusal names a position, {@code [9] is not a valid group by expression}. */
    private static boolean positionalGroupKey(final String message) {
        final String sentence = "] is not a valid group by expression";
        final int end = message.indexOf(sentence);
        if (end < 0) {
            return false;
        }
        final int open = message.lastIndexOf('[', end);
        if (open < 0 || open + 1 == end) {
            return false;
        }
        for (int i = open + 1; i < end; i++) {
            if (!Character.isDigit(message.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
