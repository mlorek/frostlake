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

package dev.frostlake.executor.commands;

import dev.frostlake.metastore.model.Function;

/**
 * The handler check a TEMPORARY Python or Java function leaves to its first call (live-verified). CREATE answers
 * {@code Temporary function TE successfully created, but not validated.} and judges nothing of the body; the
 * first row that reaches a call runs the checks CREATE would have run, refused in the sentence CREATE would have
 * given — {@code Could not find handler in function TE with handler nosuch} — and a call no row reaches answers
 * as any other does.
 */
public final class DeferredHandlerCheck {

    private DeferredHandlerCheck() {
    }

    /**
     * Judges the function's handler if it is still to be judged; once it passes, later calls run it at once.
     *
     * @param function the function about to run
     */
    public static void require(final Function function) {
        if (function.isUnvalidated()) {
            RoutineBodyCompiler.compileFunctionBody(function);
            function.setUnvalidated(false);
        }
    }
}
