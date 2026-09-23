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

import java.util.HashMap;
import java.util.Map;

/**
 * How many arguments the SYSTEM$ functions take. They live outside the registry — dispatched by name
 * from the expression evaluator — so the registry's declared bounds never see them, and a call with
 * the wrong count used to fall through to the implementation and answer whatever it answered
 * ({@code SYSTEM$TYPEOF()} was "NULL[LOB]").
 *
 * <p>Live refuses these in the ORDINARY arity vocabulary, at the call's own position:
 *
 * <pre>
 *   SYSTEM$TYPEOF()                     not enough arguments for function [SYSTEM$TYPEOF()],
 *                                       expected 1, got 0
 *   SYSTEM$TYPEOF(1, 2)                 too many arguments for function [SYSTEM$TYPEOF(1, 2)]
 *                                       expected 1, got 2
 *   SYSTEM$WAIT(0, 'SECONDS', 3)       … expected 2, got 3
 *   SYSTEM$GET_TAG('t')                … expected 3, got 1
 * </pre>
 *
 * <p>★ EVERY BOUND HERE IS MEASURED, and a name with no entry is left unchecked on purpose — inventing
 * a bound refuses SQL live accepts, which is the worse direction. A maximum of −1 means the minimum
 * was measured and the maximum was not. SYSTEM$ALLOWLIST is deliberately absent: one argument there is
 * a PARAMETER refusal live words its own way, not an arity.
 */
public final class SystemFunctionArity {

    private static final Map<String, Integer> MINIMUM = new HashMap<>();
    private static final Map<String, Integer> MAXIMUM = new HashMap<>();

    static {
        declare("SYSTEM$TYPEOF", 1, 1);
        declare("SYSTEM$WAIT", 1, 2);
        declare("SYSTEM$CANCEL_QUERY", 1, -1);
        declare("SYSTEM$STREAM_HAS_DATA", 1, 1);
        declare("SYSTEM$PIPE_STATUS", 1, -1);
        declare("SYSTEM$CLUSTERING_DEPTH", 1, -1);
        declare("SYSTEM$ABORT_SESSION", 1, -1);
        declare("SYSTEM$LAST_CHANGE_COMMIT_TIME", 1, -1);
        declare("SYSTEM$GET_TAG", 3, -1);
        declare("SYSTEM$GET_TASK_GRAPH_CONFIG", 0, 1);
    }

    private SystemFunctionArity() {
    }

    private static void declare(final String name, final int minimum, final int maximum) {
        MINIMUM.put(name, Integer.valueOf(minimum));
        MAXIMUM.put(name, Integer.valueOf(maximum));
    }

    /**
     * The measured minimum, or −1 when this name's count has not been measured.
     *
     * @param name the upper-cased function name
     * @return the minimum, or −1
     */
    public static int minimumOr(final String name) {
        final Integer minimum = MINIMUM.get(name);
        return minimum == null ? -1 : minimum.intValue();
    }

    /**
     * The measured maximum, or −1 when unbounded or unmeasured.
     *
     * @param name the upper-cased function name
     * @return the maximum, or −1
     */
    public static int maximumOr(final String name) {
        final Integer maximum = MAXIMUM.get(name);
        return maximum == null ? -1 : maximum.intValue();
    }
}
