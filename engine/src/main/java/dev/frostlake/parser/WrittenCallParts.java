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

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The parts of a call the grammar reads the same way in its positional, named and mixed spellings: the name, the
 * opening and closing parentheses, the quantifier before the arguments and the null treatments written with it.
 */
final class WrittenCallParts {

    /** The call's name. */
    final FrostlakeParser.FunctionNameContext name;

    /** The parenthesis opening the arguments. */
    final TerminalNode open;

    /** The parenthesis closing the arguments. */
    final TerminalNode close;

    /** The DISTINCT or ALL written before the arguments, or null. */
    final TerminalNode quantifier;

    /** Every null treatment written with the call, inside its parentheses or after them. */
    final List<FrostlakeParser.NullHandlingContext> treatments;

    private WrittenCallParts(final FrostlakeParser.FunctionNameContext name, final TerminalNode open,
                             final TerminalNode close, final TerminalNode quantifier,
                             final List<FrostlakeParser.NullHandlingContext> treatments) {
        this.name = name;
        this.open = open;
        this.close = close;
        this.quantifier = quantifier;
        this.treatments = treatments;
    }

    /**
     * The parts of {@code node} when it is a call in one of those spellings.
     *
     * @param node any parse node
     * @return its parts, or null when it is no such call
     */
    static WrittenCallParts of(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            return new WrittenCallParts(call.functionName(), call.LPAREN(), call.RPAREN(),
                call.DISTINCT() != null ? call.DISTINCT() : call.ALL(), call.nullHandling());
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext call =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) node;
            return new WrittenCallParts(call.functionName(), call.LPAREN(), call.RPAREN(),
                call.DISTINCT() != null ? call.DISTINCT() : call.ALL(), call.nullHandling());
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) node;
            return new WrittenCallParts(call.functionName(), call.LPAREN(), call.RPAREN(),
                call.DISTINCT() != null ? call.DISTINCT() : call.ALL(), call.nullHandling());
        }
        return null;
    }
}
