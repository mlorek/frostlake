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

import dev.frostlake.metastore.model.Function;

/** The table function a call's arguments matched, and the argument each of its parameters takes. */
final class TableFunctionMatch {

    private final Function function;
    private final int[] taken;

    /**
     * @param function the matched overload
     * @param taken    per parameter, the index of the argument it takes — positional ones first — or -1 where
     *                 the parameter's default stands
     */
    TableFunctionMatch(final Function function, final int[] taken) {
        this.function = function;
        this.taken = taken;
    }

    Function getFunction() {
        return function;
    }

    /** The index of the argument a parameter takes, or -1 for its default. */
    int argumentOf(final int parameter) {
        return taken[parameter];
    }
}
