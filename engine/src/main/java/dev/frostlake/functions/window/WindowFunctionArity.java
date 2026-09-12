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

package dev.frostlake.functions.window;

import java.util.HashMap;
import java.util.Map;

/**
 * How many arguments each window function takes, and live's two sentences for a call that gets it
 * wrong. Every bound here is live-measured:
 *
 * <pre>
 *   ROW_NUMBER RANK DENSE_RANK PERCENT_RANK CUME_DIST   0        RANK(a) is "expected 0, got 1"
 *   LEAD LAG                                            1 to 3   LAG(a,1,0) runs; a fourth is refused
 *   NTILE FIRST_VALUE LAST_VALUE                        1
 *   CONDITIONAL_TRUE_EVENT CONDITIONAL_CHANGE_EVENT     1
 *   NTH_VALUE                                           2        NTH_VALUE(a) is "expected 2, got 1"
 * </pre>
 *
 * <p>★ THE TWO SENTENCES ARE PUNCTUATED DIFFERENTLY, which is measured rather than tidied: the
 * too-few one carries a comma after the bracket and the too-many one does not.
 *
 * <p>★ ARITY OUTRANKS EVERYTHING ELSE ABOUT THE CALL. {@code LEAD()} is refused for its argument count
 * whether or not it carries an OVER clause — the missing specification is never reached — and the
 * refusal is POSITIONED on the call itself, where the missing-specification sentence carries no
 * position at all.
 *
 * <p>★ WHAT IS DELIBERATELY ABSENT: RATIO_TO_REPORT, whose refusal live words in SUM's name rather
 * than its own, and the dual-role aggregates (SUM, COUNT, AVG, MIN, MAX), which are answerable with no
 * window at all and carry an aggregate's arity rules instead.
 */
public final class WindowFunctionArity {

    private static final Map<String, Integer> MINIMUM = new HashMap<>();
    private static final Map<String, Integer> MAXIMUM = new HashMap<>();

    static {
        declare("ROW_NUMBER", 0, 0);
        declare("RANK", 0, 0);
        declare("DENSE_RANK", 0, 0);
        declare("PERCENT_RANK", 0, 0);
        declare("CUME_DIST", 0, 0);
        declare("LEAD", 1, 3);
        declare("LAG", 1, 3);
        declare("NTILE", 1, 1);
        declare("FIRST_VALUE", 1, 1);
        declare("LAST_VALUE", 1, 1);
        declare("CONDITIONAL_TRUE_EVENT", 1, 1);
        declare("CONDITIONAL_CHANGE_EVENT", 1, 1);
        declare("NTH_VALUE", 2, 2);
    }

    private WindowFunctionArity() {
    }

    private static void declare(final String name, final int minimum, final int maximum) {
        MINIMUM.put(name, Integer.valueOf(minimum));
        MAXIMUM.put(name, Integer.valueOf(maximum));
    }

    /** Whether this name's argument count is one we know how to judge. */
    public static boolean isMeasured(final String name) {
        return MINIMUM.containsKey(name);
    }

    /**
     * Live's refusal for a call of the wrong arity, or null when the count is legal.
     *
     * @param name the upper-cased function name
     * @param echo the call as the plan prints it, brackets excluded
     * @param given how many arguments were written
     * @return the refusal detail, or null
     */
    public static String refusal(final String name, final String echo, final int given) {
        if (!isMeasured(name)) {
            return null;
        }
        final int minimum = MINIMUM.get(name).intValue();
        final int maximum = MAXIMUM.get(name).intValue();
        if (given < minimum) {
            return "not enough arguments for function [" + echo + "], expected " + minimum
                + ", got " + given;
        }
        if (given > maximum) {
            return "too many arguments for function [" + echo + "] expected " + maximum
                + ", got " + given;
        }
        return null;
    }
}
