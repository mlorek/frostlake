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

import org.antlr.v4.runtime.Token;

import java.util.HashSet;
import java.util.Set;

/**
 * The shape of a CALL's named arguments, judged before the procedure is looked for — an unknown procedure's CALL is
 * refused for its shape first — and, for a CALL a block holds, while the block compiles, on a branch that never
 * runs too. A name written twice is refused at its second writing, {@code duplicate named argument N for function
 * P}; then a mix of named and positional arguments at the procedure's name, {@code illegal mixing of named and
 * positional arguments for function P}. Both are plain compilation errors placed in the text that holds the CALL
 * (live-verified). A name no parameter has is judged when the CALL runs.
 */
public final class CallNamedShape {

    private CallNamedShape() {
    }

    /**
     * Refuse a CALL that writes one argument's name twice or mixes named and positional arguments.
     *
     * @param call the CALL
     */
    public static void require(final FrostlakeParser.CallStatementContext call) {
        if (call.callArguments() == null || call.qualifiedName() == null) {
            return;
        }
        // The procedure is named as the CALL writes it: test_schema.p is TEST_SCHEMA.P, "p" keeps its quotes.
        final StringBuilder procedure = new StringBuilder();
        for (final String part : ParseTreeText.qualifiedNameParts(call.qualifiedName())) {
            procedure.append(procedure.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        final Set<String> seen = new HashSet<String>();
        boolean named = false;
        boolean positional = false;
        for (final FrostlakeParser.CallArgumentContext argument : call.callArguments().callArgument()) {
            if (argument.namedArgument() == null) {
                positional = true;
                continue;
            }
            named = true;
            final String name = SqlIdentifiers.canonical(argument.namedArgument().identifier());
            if (!seen.add(name)) {
                final Token at = argument.namedArgument().identifier().getStart();
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "duplicate named argument " + name + " for function " + procedure));
            }
        }
        if (named && positional) {
            final Token at = call.qualifiedName().getStart();
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "illegal mixing of named and positional arguments for function " + procedure));
        }
    }
}
