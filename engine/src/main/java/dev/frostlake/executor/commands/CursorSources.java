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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The name a cursor is declared over, {@code c CURSOR FOR res}, is a RESULTSET declared before the cursor, in its
 * block or in one around it. Any other name is refused while the body compiles, at the name, whether the cursor is
 * ever opened or not — {@code invalid identifier 'NOSUCH'}: a name nothing declares, a variable that is no
 * RESULTSET, the cursor's own name, a RESULTSET declared after the cursor and a table's name alike, and a quoted
 * name with its quotes. Both spellings of the declaration are held to it, the DECLARE section's and LET's
 * (live-verified).
 */
final class CursorSources {

    private CursorSources() {
    }

    /**
     * Refuse the first cursor of a body that is declared over a name that is no RESULTSET where it stands.
     *
     * @param block the top block of a body
     */
    static void require(final FrostlakeParser.BeginEndBlockContext block) {
        walkBlock(block, new HashSet<String>());
    }

    private static void walkBlock(final FrostlakeParser.BeginEndBlockContext block, final Set<String> outer) {
        final Set<String> resultSets = new HashSet<String>(outer);
        if (block.declareSection() != null) {
            for (final FrostlakeParser.DeclarationItemContext item : block.declareSection().declarationItem()) {
                if (item.CURSOR() != null) {
                    requireResultSet(item.cursorSource(), resultSets);
                } else if (item.RESULTSET() != null) {
                    resultSets.add(SqlIdentifiers.canonical(item.identifier()));
                }
            }
        }
        walk(block.statementList(), resultSets);
        if (block.exceptionSection() != null) {
            walk(block.exceptionSection(), new HashSet<String>(resultSets));
        }
    }

    private static void walk(final ParseTree node, final Set<String> resultSets) {
        if (node == null || node instanceof FrostlakeParser.BodyDefinitionContext) {
            // A routine created in the body has a body of its own.
            return;
        }
        if (node instanceof FrostlakeParser.BeginEndBlockContext) {
            walkBlock((FrostlakeParser.BeginEndBlockContext) node, resultSets);
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.CURSOR() != null) {
                requireResultSet(let.cursorSource(), resultSets);
            } else if (let.RESULTSET() != null) {
                resultSets.add(SqlIdentifiers.canonical(let.variableName()));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), resultSets);
        }
    }

    private static void requireResultSet(final FrostlakeParser.CursorSourceContext source,
                                         final Set<String> resultSets) {
        if (source == null || source.variableName() == null
                || resultSets.contains(SqlIdentifiers.canonical(source.variableName()))) {
            return;
        }
        final Token name = source.variableName().getStart();
        final String written = name.getText();
        throw new RuntimeException(SqlCompilationError.at(name.getLine(), name.getCharPositionInLine(),
            "invalid identifier '" + (written.startsWith("\"") ? written : written.toUpperCase(Locale.ROOT)) + "'"));
    }
}
