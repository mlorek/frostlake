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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Set;

/**
 * The names a SQL UDF's EXPRESSION body may use. Such a body has no FROM clause, so every bare name in
 * it is one the signature declares or nothing at all, and a real account resolves them at CREATE: it
 * refuses the first unknown one with the positioned invalid-identifier sentence, in the body's own
 * frame - line 1 counts from position 1, every later line from 0 - and a comment before the name does
 * not move it (live-verified).
 *
 * <p>Only a name written as ONE part is judged. A dotted name is something else - {@code s1.nextval}
 * reaches a sequence, and a real account creates that function - and the keyword constructs that look
 * like bare names ({@code CURRENT_DATE}, {@code CURRENT_TIMESTAMP}, {@code LOCALTIME}) are their own
 * expressions in the grammar, so they never reach here.
 *
 * <p>Parameters match without regard to case: a body over {@code (x INT)} may write {@code x + 1} or
 * {@code X + 1}, and both are created.
 */
final class SqlUdfBodyNames {

    private SqlUdfBodyNames() {
    }

    /**
     * Refuse the first name in the body that the signature does not declare.
     *
     * @param body       the parsed expression body
     * @param parameters the canonical names the signature declares, upper-cased
     */
    static void rejectUnknownNames(final ParseTree body, final Set<String> parameters) {
        rejectUnknownNames(body, parameters, 1);
    }

    /**
     * Refuse the first name in the body that the signature does not declare, for a text whose first line stands
     * {@code firstLineShift} characters from the frame's: 1 for the body as written, 0 for a statement that already
     * opens with the frame's own parenthesis.
     *
     * @param body           the parsed expression body
     * @param parameters     the canonical names the signature declares, upper-cased
     * @param firstLineShift how far the text's first line stands from the frame's
     */
    static void rejectUnknownNames(final ParseTree body, final Set<String> parameters, final int firstLineShift) {
        final FrostlakeParser.QualifiedNameContext unknown = firstUnknown(body, parameters);
        if (unknown == null) {
            return;
        }
        final Token at = unknown.getStart();
        throw new RuntimeException(SqlCompilationError.at(at.getLine(),
            at.getCharPositionInLine() + (at.getLine() == 1 ? firstLineShift : 0),
            "invalid identifier '" + unknown.getText().toUpperCase() + "'"));
    }

    /** The first single-part name the signature does not declare, in source order, or null. */
    private static FrostlakeParser.QualifiedNameContext firstUnknown(final ParseTree node,
                                                                     final Set<String> parameters) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // A SUBQUERY inside the body brings its own FROM, so the names under it are that relation's
            // and not the signature's. They are resolved where the subquery runs, as they always were.
            return null;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            if (name != null && name.namePart().isEmpty()
                    && !parameters.contains(name.getText().toUpperCase())) {
                return name;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.QualifiedNameContext found = firstUnknown(node.getChild(i), parameters);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
