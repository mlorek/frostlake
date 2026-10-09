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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A bare star under DISTINCT as a built-in function's whole argument - {@code COUNT(DISTINCT *)},
 * {@code HASH_AGG(DISTINCT *)}, windowed or not - which the account refuses as a feature it does not have, with no
 * prefix line and no position. It judges the statement's text: the refusal comes ahead of every name, a table that
 * is not there included, and in whichever clause the call stands (live-verified). A star qualified by its relation
 * or filtered by EXCLUDE or ILIKE is counted as before.
 */
final class DistinctStarCalls {

    private DistinctStarCalls() {
    }

    /**
     * Refuse the script when it calls a built-in function over a bare star under DISTINCT.
     *
     * @param script    the parsed script
     * @param functions the registry that tells a built-in function's name
     */
    static void requireNone(final ParseTree script, final FunctionRegistry functions) {
        if (script == null) {
            return;
        }
        if (isDistinctStar(script) && isBuiltIn(nameOf(script), functions)) {
            throw new RuntimeException("Unsupported feature 'TOK_STAR'.");
        }
        for (int i = 0; i < script.getChildCount(); i++) {
            requireNone(script.getChild(i), functions);
        }
    }

    private static boolean isDistinctStar(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext call = (FrostlakeParser.FunctionCallStarExprContext) node;
            return call.DISTINCT() != null && call.starQualifiedName() == null && call.starArgumentModifier().isEmpty();
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.DISTINCT() == null || call.functionArgList() == null) {
                return false;
            }
            final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList().functionArg();
            return arguments.size() == 1 && arguments.get(0).STAR() != null
                && arguments.get(0).starQualifiedName() == null && arguments.get(0).starArgumentModifier().isEmpty();
        }
        return false;
    }

    private static FrostlakeParser.FunctionNameContext nameOf(final ParseTree call) {
        return call instanceof FrostlakeParser.FunctionCallStarExprContext
            ? ((FrostlakeParser.FunctionCallStarExprContext) call).functionName()
            : ((FrostlakeParser.FunctionCallExprContext) call).functionName();
    }

    /** Whether a call's name is one word that names a built-in scalar, aggregate or window function. */
    private static boolean isBuiltIn(final FrostlakeParser.FunctionNameContext written,
                                     final FunctionRegistry functions) {
        if (written == null || written.identifier().size() != 1 || functions == null) {
            return false;
        }
        final String name = SqlIdentifiers.canonical(written.identifier(0)).toUpperCase(Locale.ROOT);
        return functions.getFunction(name) != null || functions.hasAggregateFunction(name)
            || WindowFunctionNames.handles(name);
    }
}
