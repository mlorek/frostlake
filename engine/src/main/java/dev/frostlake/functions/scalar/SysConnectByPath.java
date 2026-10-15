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

package dev.frostlake.functions.scalar;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * SYS_CONNECT_BY_PATH(expr, separator) — in a hierarchical query, the path from a row's root down to the row: the
 * separator and the expression's value of each row on the way, the root's first, as text; NULL when any of them
 * is NULL (live-verified). It answers only in a query with CONNECT BY, where the expansion keeps each row's path
 * and the evaluator reads it; a row read anywhere else is refused with the account's unknown-function sentence.
 */
public class SysConnectByPath extends BuiltInFunction {

    /** The function's name. */
    public static final String NAME = "SYS_CONNECT_BY_PATH";

    public SysConnectByPath() {
        super(NAME, new StringType("VARCHAR", 134217728));
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // The evaluator answers a call inside a hierarchical query itself; one that reaches here stands outside.
        throw new RuntimeException(SqlCompilationError.of("Unknown function " + NAME + "."));
    }

    /** Its two arguments are counted where it is evaluated, in the account's words for an exact arity. */
    @Override
    public int getMinArgCount() {
        return 0;
    }

    @Override
    public int getMaxArgCount() {
        return Integer.MAX_VALUE;
    }
}
