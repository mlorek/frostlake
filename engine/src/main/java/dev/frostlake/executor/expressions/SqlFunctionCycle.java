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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.Function;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The SQL functions whose bodies are being evaluated on this thread. A SQL function's body is part of the query
 * that calls it, so a function met again while its own body is evaluated is a cycle that no CREATE refused: one
 * made by renaming a function onto a name its own body calls, or one written with qualified names, neither of
 * which the check at CREATE follows (live-verified). An account has no answer of its own for such a call — it
 * abandons the statement as an internal failure — so the call is refused here in the sentence a cycle gets where
 * it is seen in time: {@code Detected a cycle in SQL UDF: F}.
 */
final class SqlFunctionCycle {

    private static final ThreadLocal<Set<Function>> RUNNING = new ThreadLocal<Set<Function>>() {
        @Override
        protected Set<Function> initialValue() {
            return Collections.newSetFromMap(new IdentityHashMap<Function, Boolean>());
        }
    };

    private SqlFunctionCycle() {
    }

    /**
     * Notes that a function's body starts being evaluated, and refuses one that is being evaluated already.
     *
     * @param function the function
     */
    static void enter(final Function function) {
        if (!RUNNING.get().add(function)) {
            throw new SqlFunctionCycleFailure("Detected a cycle in SQL UDF: "
                + SqlIdentifiers.spellCanonical(function.getName()));
        }
    }

    /**
     * Notes that a function's body has been evaluated.
     *
     * @param function the function {@link #enter} noted
     */
    static void exit(final Function function) {
        RUNNING.get().remove(function);
    }
}
