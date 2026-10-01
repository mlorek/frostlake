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
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;
import java.util.Set;

/**
 * Each target of a block's SELECT … INTO must name a variable its statement sees, and the block compiles its targets
 * before any of it runs, on a branch that never runs too (live-verified): a name nothing declares is "invalid
 * identifier" at the target's name, and so is an exception's, which lives in a namespace of its own; a cursor is "
 * Invalid use of cursor 'C'." and a RESULTSET " Invalid use of resultset 'R'." there. A FOR loop's variable and a
 * procedure's parameter may be assigned. A block a RESULTSET is filled from compiles when it runs, and is left alone.
 *
 * <p>The targets are judged after the block's other names, so a statement that also reads an undeclared bind, or
 * names a target twice, answers with that fault.
 */
final class IntoTargetDeclarations {

    private IntoTargetDeclarations() {
    }

    /**
     * Refuse the first SELECT … INTO target of {@code block}, in the order of the text, that names no variable.
     *
     * @param block   the block being compiled
     * @param seeded  the canonical names already in scope where the block stands — a procedure's parameters, the
     *                variables of a block running this one
     */
    static void requireDeclared(final ParseTree block, final Set<String> seeded) {
        if (block instanceof FrostlakeParser.ResultSetBlockContext) {
            return;
        }
        if (block instanceof FrostlakeParser.SelectIntoStatementContext) {
            final FrostlakeParser.SelectIntoStatementContext into = (FrostlakeParser.SelectIntoStatementContext) block;
            for (final FrostlakeParser.IntoTargetContext target : into.intoTargetList().intoTarget()) {
                requireVariable(into, target, seeded);
            }
            return;
        }
        for (int i = 0; i < block.getChildCount(); i++) {
            requireDeclared(block.getChild(i), seeded);
        }
    }

    private static void requireVariable(final FrostlakeParser.SelectIntoStatementContext into,
                                        final FrostlakeParser.IntoTargetContext target, final Set<String> seeded) {
        final String written = target.identifier().getText();
        final String name = ScriptingNameValidator.canonical(written);
        final ParserRuleContext declaration = DeclarationLookup.declarationOf(into, name);
        final Token at = target.identifier().getStart();
        final boolean declared = declaration != null ? !DeclarationLookup.isException(declaration)
            : seeded.contains(name) || ScriptingNameValidator.isScriptSupplied(name);
        if (!declared) {
            final boolean quoted = at.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "invalid identifier '" + (quoted ? written : written.toUpperCase(Locale.ROOT)) + "'"));
        }
        final String misuse = DeclarationLookup.misuse(declaration);
        if (misuse != null) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                " Invalid use of " + misuse + " '" + name + "'."));
        }
    }
}
