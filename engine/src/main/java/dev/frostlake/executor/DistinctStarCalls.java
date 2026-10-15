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

import dev.frostlake.parser.FrostlakeParser;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A bare star under DISTINCT or ALL as the whole argument of a call named by one word - {@code COUNT(DISTINCT *)},
 * {@code COUNT(ALL *)}, {@code HASH_AGG(DISTINCT *)}, windowed or not, and under a name that is no function's too -
 * which the account refuses as a feature it does not have, with no prefix line and no position. It judges the
 * statement's text: the refusal comes ahead of every name, a table or a column that is not there included, and in
 * whichever clause the call stands (live-verified). A star qualified by its relation or filtered by EXCLUDE or
 * ILIKE is counted as before.
 */
final class DistinctStarCalls {

    private DistinctStarCalls() {
    }

    /**
     * Refuse the script when it calls a function named by one word over a bare star under DISTINCT or ALL.
     *
     * @param script the parsed script
     */
    static void requireNone(final ParseTree script) {
        if (script == null) {
            return;
        }
        if (isDistinctStar(script) && isOneWord(nameOf(script))) {
            throw new RuntimeException("Unsupported feature 'TOK_STAR'.");
        }
        for (int i = 0; i < script.getChildCount(); i++) {
            requireNone(script.getChild(i));
        }
    }

    private static boolean isDistinctStar(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext call = (FrostlakeParser.FunctionCallStarExprContext) node;
            return call.DISTINCT() != null && call.starQualifiedName() == null && call.starArgumentModifier().isEmpty();
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.DISTINCT() == null && call.ALL() == null || call.functionArgList() == null) {
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

    /** Whether a call's name is one word: a built-in function's, or a name that is no function's at all. */
    private static boolean isOneWord(final FrostlakeParser.FunctionNameContext written) {
        return written != null && written.identifier().size() == 1;
    }
}
