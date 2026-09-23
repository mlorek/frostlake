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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The argument list that names one overload in DROP, DESCRIBE, COMMENT ON and SHOW GRANTS ON a function or a
 * procedure takes types alone. An argument written with its name, as a CREATE writes it, is a syntax error at
 * its type, in one line: {@code DROP FUNCTION f1(x INT)} is refused at the {@code INT}. Only ALTER … RENAME TO
 * takes a named argument. A plain word standing for a type is {@code Unsupported data type 'X'.}, the word as
 * it resolves (live-verified). The arguments are judged in the order written.
 */
final class RoutineSignatureForm {

    private RoutineSignatureForm() {
    }

    /**
     * Refuse the first argument written with a name, at its type.
     *
     * @param signature the written argument list, or null when there is none
     */
    static void requireTypesOnly(final FrostlakeParser.DataTypeListContext signature) {
        if (signature == null) {
            return;
        }
        boolean named = false;
        for (int i = 0; i < signature.getChildCount(); i++) {
            final ParseTree child = signature.getChild(i);
            if (child instanceof FrostlakeParser.IdentifierContext) {
                final ParseTree next = i + 1 < signature.getChildCount() ? signature.getChild(i + 1) : null;
                if (!(next instanceof FrostlakeParser.DataTypeNameContext)) {
                    throw new RuntimeException(SqlCompilationError.of("Unsupported data type '"
                        + SqlIdentifiers.canonical((FrostlakeParser.IdentifierContext) child) + "'."));
                }
                named = true;
            } else if (child instanceof FrostlakeParser.DataTypeNameContext && named) {
                final Token type = ((ParserRuleContext) child).getStart();
                final int[] shown = LeadingCommentOffset.rebase(type.getLine(), type.getCharPositionInLine());
                throw new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
                    + shown[1] + " unexpected '" + type.getText() + "'."));
            } else if (child instanceof TerminalNode
                    && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.COMMA) {
                named = false;
            }
        }
    }
}
