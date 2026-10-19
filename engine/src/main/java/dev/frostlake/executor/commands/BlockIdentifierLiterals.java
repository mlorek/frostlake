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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The IDENTIFIER() arguments a block's statements write as text, judged while the block compiles: one that is no
 * identifier reference, {@code IDENTIFIER('')}, is refused before any statement runs, placed in the block's text and
 * with no uncaught-exception wrapper — {@code invalid identifier ''''} at the literal, in a plain statement as in a
 * RESULTSET's query (live-verified).
 */
final class BlockIdentifierLiterals {

    private BlockIdentifierLiterals() {
    }

    /**
     * Refuses the first IDENTIFIER() text in the block that names nothing.
     *
     * @param node the block, or any part of it
     */
    static void require(final ParseTree node) {
        if (node instanceof FrostlakeParser.IdentifierArgumentContext) {
            final FrostlakeParser.IdentifierArgumentContext argument = (FrostlakeParser.IdentifierArgumentContext) node;
            if (argument.STRING_LITERAL() == null) {
                return;
            }
            final Token written = argument.STRING_LITERAL().getSymbol();
            final Expression text = ExpressionEvaluator.parse(written.getText());
            final Object value = text instanceof LiteralExpression ? ((LiteralExpression) text).getValue() : null;
            if (value != null && !SqlIdentifiers.isIdentifierReference(value.toString())) {
                throw new RuntimeException(SqlCompilationError.at(written.getLine(), written.getCharPositionInLine(),
                    "invalid identifier '" + written.getText() + "'"));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            require(node.getChild(i));
        }
    }
}
