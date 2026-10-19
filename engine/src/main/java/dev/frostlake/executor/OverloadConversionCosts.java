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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an argument's conversion to a parameter's type weighs when a call chooses among a routine's overloads. A
 * call runs the overload whose conversions weigh least in total, over all its arguments together — not the one its
 * first argument prefers — and an untyped NULL weighs nothing anywhere (live-verified over pairs and triples of
 * two- and three-argument overloads, of functions and of procedures, called by position and by name):
 *
 * <pre>
 *   argument   parameter, with its weight
 *   NUMBER     NUMBER 0, FLOAT 1, BOOLEAN 2, VARIANT 3, VARCHAR 4, TIMESTAMP 5
 *   FLOAT      FLOAT 0, BOOLEAN 1, VARIANT 2, VARCHAR 4, NUMBER 8
 *   VARCHAR    VARCHAR 0, BOOLEAN 1, FLOAT 2, DATE 2, TIME 2, TIMESTAMP 2, NUMBER 3, VARIANT 4
 *   BOOLEAN    BOOLEAN 0, VARCHAR 1, VARIANT 2, FLOAT 4, NUMBER 8
 *   DATE       DATE 0, TIMESTAMP 1, VARCHAR 4
 *   TIME       TIME 0, TIMESTAMP 1, VARCHAR 2
 * </pre>
 *
 * <p>The weights are the smallest whole numbers every measured call agrees with, and three of those calls weigh
 * their two overloads alike: {@code f(a NUMBER, b BOOLEAN)} and {@code f(a FLOAT, b VARCHAR)} for
 * {@code (1, 'x')}, {@code f(a VARCHAR, b VARCHAR)} and {@code f(a FLOAT, b NUMBER)} for {@code (1, 'x')}, and
 * {@code f(a NUMBER, b NUMBER)} and {@code f(a FLOAT, b FLOAT)} for {@code ('x', 1)}. Overloads that weigh alike
 * are told apart by how the call was written: by position the first in the order of their signatures runs — the
 * account's internal type names in alphabetical order, parameter by parameter, whatever order the overloads were
 * created in — and by name the call is refused, {@code More than one function of F are resolved in prepare
 * state.} The rows of the other families keep the order each was measured in over pairs of one-argument
 * overloads. A TIME reaches a timestamp parameter of any kind before a text one, and is then refused there, since
 * no TIME converts to a timestamp: {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}.
 */
public final class OverloadConversionCosts {

    /** The family of an untyped NULL, which weighs nothing for any parameter. */
    public static final String UNTYPED_NULL = "NULL";

    /** The parameter families in the order of the account's internal type names, which is how signatures sort. */
    private static final List<String> SIGNATURE_ORDER = Arrays.asList("ARRAY", "BINARY", "BOOLEAN", "DATE",
        "NUMBER", "OBJECT", "FLOAT", "VARCHAR", "TIME", "TIMESTAMP", "VARIANT");

    /** What each conversion weighs, by the argument's family and then the parameter's. */
    private static final Map<String, Map<String, Integer>> WEIGHTS = new HashMap<String, Map<String, Integer>>();

    static {
        row("NUMBER", new String[] {"NUMBER", "FLOAT", "BOOLEAN", "VARIANT", "VARCHAR", "TIMESTAMP"},
            new int[] {0, 1, 2, 3, 4, 5});
        row("FLOAT", new String[] {"FLOAT", "BOOLEAN", "VARIANT", "VARCHAR", "NUMBER"}, new int[] {0, 1, 2, 4, 8});
        row("VARCHAR", new String[] {"VARCHAR", "BOOLEAN", "FLOAT", "DATE", "TIME", "TIMESTAMP", "NUMBER", "VARIANT"},
            new int[] {0, 1, 2, 2, 2, 2, 3, 4});
        row("DECFLOAT", new String[] {"DECFLOAT", "NUMBER", "FLOAT", "VARCHAR", "VARIANT", "BOOLEAN"},
            new int[] {0, 1, 2, 3, 4, 5});
        row("UUID", new String[] {"UUID", "VARCHAR", "BOOLEAN", "FLOAT", "NUMBER", "VARIANT"},
            new int[] {0, 1, 2, 3, 4, 5});
        row("BOOLEAN", new String[] {"BOOLEAN", "VARCHAR", "VARIANT", "FLOAT", "NUMBER"},
            new int[] {0, 1, 2, 4, 8});
        row("DATE", new String[] {"DATE", "TIMESTAMP", "VARCHAR"}, new int[] {0, 1, 4});
        row("TIME", new String[] {"TIME", "TIMESTAMP", "VARCHAR"}, new int[] {0, 1, 2});
        row("TIMESTAMP", new String[] {"TIMESTAMP", "DATE", "VARCHAR"}, new int[] {0, 1, 2});
        row("VARIANT", new String[] {"VARIANT", "ARRAY", "OBJECT", "BOOLEAN", "VARCHAR", "NUMBER", "FLOAT"},
            new int[] {0, 1, 2, 3, 4, 5, 6});
        row("ARRAY", new String[] {"ARRAY", "VARIANT", "OBJECT", "VARCHAR"}, new int[] {0, 1, 2, 3});
        row("OBJECT", new String[] {"OBJECT", "VARIANT", "ARRAY", "VARCHAR"}, new int[] {0, 1, 2, 3});
    }

    private OverloadConversionCosts() {
    }

    private static void row(final String argument, final String[] parameters, final int[] weights) {
        final Map<String, Integer> row = new HashMap<String, Integer>();
        for (int i = 0; i < parameters.length; i++) {
            row.put(parameters[i], weights[i]);
        }
        WEIGHTS.put(argument, row);
    }

    /**
     * What an argument's conversion to a parameter weighs.
     *
     * @param argument  the argument's family, {@link #UNTYPED_NULL} for an untyped NULL, or null for one no row
     *                  names
     * @param parameter the parameter's family
     * @return the weight, lower being preferred, or -1 when the argument's row does not name the parameter
     */
    public static int of(final String argument, final String parameter) {
        if (UNTYPED_NULL.equals(argument)) {
            // An untyped NULL reaches every parameter but a DECFLOAT one (live-verified).
            return "DECFLOAT".equals(parameter) ? -1 : 0;
        }
        final Map<String, Integer> row = argument == null ? null : WEIGHTS.get(argument);
        final Integer weight = row == null || parameter == null ? null : row.get(parameter);
        return weight == null ? -1 : weight;
    }

    /**
     * Where a parameter's family stands in the order signatures sort by.
     *
     * @param parameter the parameter's family
     * @return the position, a family the order does not name last
     */
    public static int signatureOrder(final String parameter) {
        final int at = SIGNATURE_ORDER.indexOf(parameter);
        return at < 0 ? SIGNATURE_ORDER.size() : at;
    }
}
