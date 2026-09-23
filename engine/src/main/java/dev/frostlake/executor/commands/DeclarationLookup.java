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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * What a name written in a Snowflake Scripting block refers to where it is written: the declaration in sight of it
 * in the block's text, read from the parse tree while the block compiles. A LET earlier in an enclosing statement
 * list, a FOR loop's variable inside its body and an enclosing block's DECLARE item are in sight; an exception handler
 * sees every LET of its block's statements. A variable, cursor or RESULTSET is preferred to an exception of the same
 * name, which lives in a namespace of its own.
 */
final class DeclarationLookup {

    /** What {@link #misuse} names a cursor. */
    static final String CURSOR = "cursor";

    /** What {@link #misuse} names a RESULTSET. */
    static final String RESULTSET = "resultset";

    private DeclarationLookup() {
    }

    /**
     * The declaration of {@code name} that {@code at} sees, or null when there is none in sight — a procedure's
     * parameter, a variable of a block running this one, a name the script supplies, or no name at all.
     *
     * @param at   the statement or expression naming it
     * @param name the canonical name
     * @return the LET, FOR loop or DECLARE item, or null
     */
    static ParserRuleContext declarationOf(final ParserRuleContext at, final String name) {
        ParserRuleContext exception = null;
        ParseTree child = at;
        for (ParserRuleContext up = at.getParent(); up != null; child = up, up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                final List<FrostlakeParser.StatementContext> statements =
                    ((FrostlakeParser.StatementListContext) up).statement();
                final ParserRuleContext let = latestLet(statements, statements.indexOf(child), name);
                if (let != null) {
                    return let;
                }
            } else if (up instanceof FrostlakeParser.ForStatementContext) {
                final FrostlakeParser.ForStatementContext loop = (FrostlakeParser.ForStatementContext) up;
                if (child == loop.statementList() && loop.identifier() != null
                        && name.equals(ScriptingNameValidator.canonical(loop.identifier().getText()))) {
                    return loop;
                }
            } else if (up instanceof FrostlakeParser.BeginEndBlockContext) {
                final FrostlakeParser.BeginEndBlockContext enclosing = (FrostlakeParser.BeginEndBlockContext) up;
                if (child == enclosing.exceptionSection() && enclosing.statementList() != null) {
                    final List<FrostlakeParser.StatementContext> statements = enclosing.statementList().statement();
                    final ParserRuleContext let = latestLet(statements, statements.size(), name);
                    if (let != null) {
                        return let;
                    }
                }
                final ParserRuleContext item = declarationItem(enclosing.declareSection(), name);
                if (item != null && !isException(item)) {
                    return item;
                }
                if (item != null && exception == null) {
                    exception = item;
                }
            } else if (up instanceof FrostlakeParser.ResultSetBlockContext) {
                // A RESULTSET's block source runs as a text of its own: nothing outside it is in its sight.
                final ParserRuleContext item =
                    declarationItem(((FrostlakeParser.ResultSetBlockContext) up).declareSection(), name);
                return item != null ? item : exception;
            }
        }
        return exception;
    }

    /**
     * What a declaration makes its name when it is no variable a value is assigned to or read from: a cursor, a
     * RESULTSET, or null.
     *
     * @param declaration the declaration, or null
     * @return {@link #CURSOR}, {@link #RESULTSET} or null
     */
    static String misuse(final ParserRuleContext declaration) {
        if (declaration instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) declaration;
            return let.CURSOR() != null ? CURSOR : let.RESULTSET() != null ? RESULTSET : null;
        }
        if (declaration instanceof FrostlakeParser.DeclarationItemContext) {
            final FrostlakeParser.DeclarationItemContext item = (FrostlakeParser.DeclarationItemContext) declaration;
            return item.CURSOR() != null ? CURSOR : item.RESULTSET() != null ? RESULTSET : null;
        }
        return null;
    }

    /**
     * Whether a declaration declares an exception.
     *
     * @param declaration the declaration, or null
     * @return true for an EXCEPTION item
     */
    static boolean isException(final ParserRuleContext declaration) {
        return declaration instanceof FrostlakeParser.DeclarationItemContext
            && ((FrostlakeParser.DeclarationItemContext) declaration).EXCEPTION() != null;
    }

    /**
     * Whether {@code at} stands in an exception handler's statements, where the handler's SQLSTATE and SQLERRM are
     * in scope.
     *
     * @param at any node of the block
     * @return true inside a handler
     */
    static boolean insideExceptionHandler(final ParseTree at) {
        for (ParseTree up = at.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExceptionHandlerContext) {
                return true;
            }
        }
        return false;
    }

    /** The last LET of {@code name} among the first {@code before} statements, or null. */
    private static ParserRuleContext latestLet(final List<FrostlakeParser.StatementContext> statements,
                                               final int before, final String name) {
        for (int i = before - 1; i >= 0; i--) {
            final FrostlakeParser.StatementContext earlier = statements.get(i);
            final FrostlakeParser.LetStatementContext let = earlier.proceduralStatement() == null ? null
                : earlier.proceduralStatement().letStatement();
            if (let != null && name.equals(ScriptingNameValidator.canonical(let.identifier().getText()))) {
                return let;
            }
        }
        return null;
    }

    /** The DECLARE item of {@code name} in a section, a variable's before an exception's, or null. */
    private static ParserRuleContext declarationItem(final FrostlakeParser.DeclareSectionContext section,
                                                     final String name) {
        if (section == null) {
            return null;
        }
        ParserRuleContext exception = null;
        for (final FrostlakeParser.DeclarationItemContext item : section.declarationItem()) {
            if (name.equals(ScriptingNameValidator.canonical(item.identifier().getText()))) {
                if (item.EXCEPTION() == null) {
                    return item;
                }
                exception = item;
            }
        }
        for (final FrostlakeParser.UntypedDeclarationItemContext item : section.untypedDeclarationItem()) {
            if (name.equals(ScriptingNameValidator.canonical(item.identifier().getText()))) {
                return item;
            }
        }
        return exception;
    }
}
