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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The two conditional-event window functions, and what their plan echo shows about their argument.
 * Live prints the conversions its plan carries, and these two do NOT carry the same ones — measured
 * side by side across nine argument shapes each:
 *
 * <pre>
 *                                  CONDITIONAL_TRUE_EVENT      CONDITIONAL_CHANGE_EVENT
 *   b            BOOLEAN column    CE.B                        CE.B
 *   NOT b / b AND b / TRUE         as written                  as written
 *   i &gt; 1        a comparison      CAST(CE.I &gt; 1 AS BOOLEAN)   CAST(CE.I &gt; 1 AS BOOLEAN)
 *   b IS NULL                      CAST(… AS BOOLEAN)          CAST(… AS BOOLEAN)
 *   STARTSWITH(t,'1')              CAST(… AS BOOLEAN)          CAST(… AS BOOLEAN)
 *   t            VARCHAR column    CAST(CE.T AS BOOLEAN)       CE.T
 *   UPPER(t)     a VARCHAR call    CAST(… AS BOOLEAN)          UPPER(CE.T)
 *   i + 1        arithmetic        —                           CE.I + 1
 * </pre>
 *
 * <p>★ TWO SEPARATE RULES PRODUCE THAT TABLE, and they have to be kept apart:
 *
 * <p>1. A BOOLEAN-VALUED EXPRESSION THAT IS NOT ONE OF THE LOGICAL NODES is materialised as a cast, by
 * BOTH functions. A comparison, an IS NULL and a boolean-returning call are all booleans already, and
 * all three are cast anyway; a BOOLEAN column, a boolean literal and NOT / AND / OR are not. So the
 * cast is not "the type had to change" — it is the plan spelling out a predicate it did not build from
 * its own logical vocabulary.
 *
 * <p>2. THE PARAMETER'S OWN TYPE. CONDITIONAL_TRUE_EVENT counts how often a PREDICATE holds, so a
 * non-boolean argument is converted and the conversion is printed. CONDITIONAL_CHANGE_EVENT counts how
 * often a VALUE changes and takes any type, so it converts nothing — which is why the last three rows
 * of the table differ.
 */
public final class BooleanArgumentWindowFunctions {

    private static final Set<String> EVENT_FUNCTIONS = new HashSet<>(Arrays.asList(
        "CONDITIONAL_TRUE_EVENT", "CONDITIONAL_CHANGE_EVENT"));

    private BooleanArgumentWindowFunctions() {
    }

    /**
     * Whether this function's echo spells out a boolean argument it did not build itself — rule 1.
     * Measured for these two; every other window function's echo is left as it was.
     *
     * @param name the upper-cased function name
     * @return true for the two conditional-event functions
     */
    public static boolean spellsOutABooleanArgument(final String name) {
        return EVENT_FUNCTIONS.contains(name);
    }

    /**
     * Whether this function CONVERTS its argument to a boolean — rule 2, which is
     * CONDITIONAL_TRUE_EVENT alone.
     *
     * @param name the upper-cased function name
     * @return true when the parameter is a predicate rather than a value
     */
    public static boolean coercesArgumentToBoolean(final String name) {
        return "CONDITIONAL_TRUE_EVENT".equals(name);
    }
}
