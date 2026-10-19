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
package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.SystemFunctionNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The refusal of a text whose only faults are calls written as a block's statements
 * ({@link BlockCallStatementReading}). The account looks every such call's name up first: a name no built-in function
 * has — a user's function, a variable, a name nothing has — is refused as an unknown function, the first of them in
 * the text, wherever the calls of built-in functions stand; with none of those, the first call is refused as no
 * statement of a block (live-verified). Which names are built in is the function registry's to tell, so the refusal
 * is judged where the registry is at hand; read without it, a {@code SYSTEM$} name the engine has no function of is
 * the unknown one.
 */
public final class BlockCallStatementRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> names;

    /**
     * @param names the names of the calls, as written and in the order written
     */
    BlockCallStatementRefusal(final List<String> names) {
        super(sentence(names, null));
        this.names = Collections.unmodifiableList(new ArrayList<>(names));
    }

    /**
     * The refusal as the account words it, the names judged by the registry.
     *
     * @param registry the engine's function registry
     * @return the refusal
     */
    public RuntimeException judged(final FunctionRegistry registry) {
        return new RuntimeException(sentence(names, registry));
    }

    private static String sentence(final List<String> names, final FunctionRegistry registry) {
        for (final String name : names) {
            if (!isBuiltIn(name, registry)) {
                // The account's own sentence leaves its template half filled; this is the sentence it means.
                return SqlCompilationError.of("Unknown function " + name);
            }
        }
        return "Unsupported feature 'Function " + names.get(0) + " cannot be executed as a statement in Snowscript'.";
    }

    private static boolean isBuiltIn(final String name, final FunctionRegistry registry) {
        final String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SYSTEM$")) {
            return SystemFunctionNames.contains(upper);
        }
        return registry == null || registry.hasFunction(upper) || registry.hasBuiltInAggregateFunction(upper)
            || registry.hasTableFunction(upper);
    }
}
