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

import dev.frostlake.executor.IntoClausePlacement;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules a CREATE FUNCTION or CREATE PROCEDURE statement is judged by from its own text alone, before any name
 * in it resolves (live-verified): the order of its options, a syntax rule (see {@link RoutineOptionOrder}); then the
 * types it declares, a width out of range first ({@code CREATE FUNCTION f(a VARCHAR(0)) ... LANGUAGE COBOL} is
 * {@code Invalid character length: 0.}, ahead of a missing schema too); then a LANGUAGE the account does not know,
 * {@code Unknown function language: COBOL.}, "function" for a procedure too; then, for a function, EXECUTE AS,
 * {@code Unsupported invocation type for function.}, which carries no compilation-error prefix.
 *
 * <p>A Snowflake Scripting block applies them to every routine statement it holds while it compiles, whether or not
 * the branch holding it can run, so the whole block is refused and none of it runs: first the options' order across
 * the block, then one pass in the order the block is written that judges each declared type where it stands, each
 * DECLARE item's name once the item's own types are judged, each routine statement's language and invocation type
 * where the statement ends, and each INTO clause standing where none may at its INTO (see IntoClausePlacement). A
 * width declared above a routine statement is refused ahead of its language, one declared below it after, and a
 * name a DECLARE section introduces twice is refused at its second item once that item's own type and initialiser
 * are judged ({@code DECLARE x INT; x VARCHAR(0);} is the width). A procedure's parameter that a DECLARE item
 * repeats, a LET naming a variable twice, an unnamed bind and a name that resolves to nothing all wait for that pass.
 */
final class RoutineStatementRules {

    private RoutineStatementRules() {
    }

    /**
     * Judge a routine statement run on its own. The types judged are the signature's and the result's: an unquoted
     * body's own are judged where the body compiles, in the body's frame.
     */
    static void judge(final FrostlakeParser.CreateStatementContext statement) {
        RoutineOptionOrder.requireOrder(statement);
        for (int i = 0; i < statement.getChildCount(); i++) {
            if (!(statement.getChild(i) instanceof FrostlakeParser.BodyDefinitionContext)) {
                ScriptTypeCompiler.validateDeclaredTypes(statement.getChild(i));
            }
        }
        rejectUnknownLanguage(statement);
        rejectInvocationType(statement);
    }

    /** Refuse a block holding a routine statement whose options are out of order. */
    static void requireNestedOptionOrder(final ParseTree block) {
        for (final FrostlakeParser.CreateStatementContext statement : routinesIn(block)) {
            RoutineOptionOrder.requireOrder(statement);
        }
    }

    /**
     * Judge a block's declared types, its DECLARE sections' names, its routine statements and its INTO clauses in the
     * order they are written: a type where it stands, a DECLARE item's name once the item's own types are judged, a
     * routine statement's language and invocation type once the types it declares itself are judged, an INTO clause
     * standing where none may at its INTO (see IntoClausePlacement#rejectInBlockAt). Every type and every token is
     * handed to {@link ScriptTypeCompiler#validateDeclaredTypes}, which judges what it knows of each.
     */
    static void judgeNestedTypesAndRoutines(final ParseTree node) {
        judgeInOrder(node, false);
    }

    /**
     * @param inStatement whether the node sits inside a SQL statement: a block there is another routine's body,
     *                    whose DECLARE section is not one of this block's scopes
     */
    private static void judgeInOrder(final ParseTree node, final boolean inStatement) {
        if (node instanceof TerminalNode) {
            IntoClausePlacement.rejectInBlockAt((TerminalNode) node);
        }
        if (node instanceof FrostlakeParser.DataTypeNameContext || node instanceof TerminalNode) {
            ScriptTypeCompiler.validateDeclaredTypes(node);
            return;
        }
        final boolean statement = inStatement || isSqlStatement(node);
        if (node instanceof FrostlakeParser.DeclareSectionContext && !statement) {
            judgeDeclarations((FrostlakeParser.DeclareSectionContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            judgeInOrder(node.getChild(i), statement);
        }
        if (node instanceof FrostlakeParser.CreateStatementContext
                && isRoutine((FrostlakeParser.CreateStatementContext) node)) {
            rejectUnknownLanguage((FrostlakeParser.CreateStatementContext) node);
            rejectInvocationType((FrostlakeParser.CreateStatementContext) node);
        }
    }

    /**
     * One block's DECLARE section, item by item: the item's own types first, then its name, refused as
     * {@code Variable with name 'X' declared twice.} at the item when the section has introduced it already. An
     * EXCEPTION's name collides only with another EXCEPTION's.
     */
    private static void judgeDeclarations(final FrostlakeParser.DeclareSectionContext section) {
        final Set<String> names = new HashSet<String>();
        final Set<String> exceptions = new HashSet<String>();
        for (int i = 0; i < section.getChildCount(); i++) {
            final ParseTree child = section.getChild(i);
            judgeInOrder(child, false);
            if (child instanceof FrostlakeParser.DeclarationItemContext) {
                final FrostlakeParser.DeclarationItemContext item = (FrostlakeParser.DeclarationItemContext) child;
                introduce(item.EXCEPTION() != null ? exceptions : names, item.identifier(), item.getStart());
            } else if (child instanceof FrostlakeParser.UntypedDeclarationItemContext) {
                final FrostlakeParser.UntypedDeclarationItemContext item =
                    (FrostlakeParser.UntypedDeclarationItemContext) child;
                introduce(names, item.identifier(), item.getStart());
            }
        }
    }

    private static void introduce(final Set<String> names, final FrostlakeParser.IdentifierContext identifier,
                                  final Token at) {
        if (identifier == null) {
            return;
        }
        final String name = ScriptingNameValidator.canonical(identifier.getText());
        if (!names.add(name)) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                " Variable with name '" + name + "' declared twice."));
        }
    }

    /** Whether a node is a SQL statement rather than a scripting one. */
    private static boolean isSqlStatement(final ParseTree node) {
        return node instanceof FrostlakeParser.SelectStatementContext
            || node instanceof FrostlakeParser.DmlStatementContext
            || node instanceof FrostlakeParser.SelectIntoStatementContext
            || node instanceof FrostlakeParser.ExecuteImmediateStatementContext
            || node instanceof FrostlakeParser.DdlStatementContext;
    }

    /** Whether a CREATE statement creates a function or a procedure. */
    static boolean isRoutine(final FrostlakeParser.CreateStatementContext statement) {
        return statement.returnType() != null && (statement.FUNCTION() != null || statement.PROCEDURE() != null);
    }

    /** A LANGUAGE the account does not know, refused by the word as written: {@code language cobol} is cobol. */
    static void rejectUnknownLanguage(final FrostlakeParser.CreateStatementContext statement) {
        for (final FrostlakeParser.FunctionOptionContext option : statement.functionOption()) {
            if (option.languageClause() != null && option.languageClause().IDENTIFIER() != null) {
                throw new RuntimeException(SqlCompilationError.of("Unknown function language: "
                    + option.languageClause().IDENTIFIER().getText() + "."));
            }
        }
    }

    /** A function runs with no invocation type of its own. */
    private static void rejectInvocationType(final FrostlakeParser.CreateStatementContext statement) {
        if (statement.FUNCTION() != null && !statement.executeAsClause().isEmpty()) {
            throw new RuntimeException("Unsupported invocation type for function.");
        }
    }

    /** The routine statements under a parse tree, in the order they are written. */
    private static List<FrostlakeParser.CreateStatementContext> routinesIn(final ParseTree node) {
        final List<FrostlakeParser.CreateStatementContext> found = new ArrayList<FrostlakeParser.CreateStatementContext>();
        collect(node, found);
        return found;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.CreateStatementContext> found) {
        if (node instanceof FrostlakeParser.CreateStatementContext
                && isRoutine((FrostlakeParser.CreateStatementContext) node)) {
            found.add((FrostlakeParser.CreateStatementContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), found);
        }
    }
}
