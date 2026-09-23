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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.OuterJoinOperandException;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.EndOfInput;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.LimitValueSyntax;
import dev.frostlake.parser.StageArgumentSyntax;
import dev.frostlake.types.DataType;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the body of a {@code LANGUAGE SQL} routine at CREATE time, the way Snowflake does — a body that does
 * not parse is rejected there and then, not on first use (live-verified: {@code CREATE PROCEDURE … AS 'UPDATE
 * statement'} fails with {@code SQL compilation error: syntax error …}, and
 * {@code CREATE FUNCTION … AS 'this is not sql'} with {@code Compilation of SQL UDF failed: …}).
 *
 * <p>The two routine families have different body shapes, both live-verified:
 * <ul>
 *   <li>a stored procedure body must be a Snowflake-Scripting <em>block</em> ({@code BEGIN … END}, optionally
 *       preceded by a {@code DECLARE} section) — a bare statement such as {@code SELECT a + 1} is rejected;</li>
 *   <li>a scalar SQL UDF body may be a scalar <em>expression</em> ({@code x + 10}), a <em>query</em>
 *       ({@code SELECT …} / {@code WITH … SELECT …}), <em>or</em> a scripting block — live-verified
 * {@code CREATE FUNCTION f() RETURNS INT AS $$ BEGIN RETURN 1; END $$} then
 *       {@code SELECT f()} yields 1, and the block genuinely executes (variables, control flow and a
 *       first-RETURN-wins multi-statement body all work). A block body is restricted, though — see
 *       {@link ScriptingUdfBodyValidator}. A <em>table</em> function ({@code RETURNS TABLE(…)}) still
 *       takes no block: Snowflake reports the ordinary UDF syntax error for one.</li>
 * </ul>
 *
 * <p>A body declared in another language is not SQL and is never inspected by the two methods above; the
 * {@code compile*Body(Function)} / {@code (Procedure)} pair below hands it to the language's own compiler
 * instead, for the languages where a real account compiles at CREATE. Which those are is measured, and it
 * is NOT "every language that has a compiler":
 *
 * <pre>
 *                  FUNCTION   PROCEDURE
 *   JAVASCRIPT       no          no
 *   PYTHON          YES          no
 *   JAVA            YES         YES
 *   SCALA           YES         YES
 * </pre>
 *
 * <p>So a JavaScript routine whose body is {@code 'not code at all'} is created without complaint and fails
 * only when called, while the same body under LANGUAGE PYTHON is refused by CREATE — and a Python PROCEDURE
 * with that body is created, though a Python FUNCTION with it is not. The table is transcribed rather than
 * inferred, because no rule anyone would guess produces it.
 *
 * <p>These fail open on the same principle as the SQL checks: a language whose optional module is absent has
 * no compiler to ask, an {@code IMPORTS} handler lives in a jar rather than in the body, and a routine with
 * no body at all is somebody else's error. Refusing those would reject routines a real account accepts.
 *
 * <p>Every decision is made by the engine's own parser — {@link QueryExecutor#proceduralBlockOf(String)},
 * {@link QueryExecutor#queryStatementOf(String)} and {@link AntlrExpressionParser#parseTree(String)} —
 * never by sniffing the body text.
 */
final class RoutineBodyCompiler {

    /** A parenthesis that groups: the kind {@link #castLeavesFrameClosed} gives one no call or CAST opens. */
    private static final int GROUP_PAREN = 0;

    /** A parenthesis a call opens, or a CAST whose AS has been read. */
    private static final int CALL_PAREN = 1;

    /** A parenthesis a CAST or TRY_CAST opens, before its AS. */
    private static final int CAST_PAREN = 2;

    /** Where a refusal from the body's first line puts its position, which the body's frame moves by one. */
    private static final Pattern FIRST_LINE_POSITION = Pattern.compile("(error line 1 at position )(\\d+)");

    /** The name an unresolved-identifier refusal carries, so a body's own parameter can be told apart. */
    private static final Pattern INVALID_IDENTIFIER = Pattern.compile("invalid identifier '([^']*)'");

    /** Shared, like the UDF invoker's own: javac start-up is what costs, not the compile. */
    private static final JavaFunctionCompiler JAVA_COMPILER = new JavaFunctionCompiler();

    private RoutineBodyCompiler() {
    }

    /**
     * Reject a {@code LANGUAGE SQL} stored-procedure body that is not a Snowflake-Scripting block. A body in any
     * other language, an absent body (handler-based routines) and a blank body are left alone. A block's
     * DECLARE sections are compiled here too, so a name they declare twice — a parameter's included — is
     * refused at CREATE, while a repeated LET waits for CALL (live-verified).
     *
     * @param parameters the canonical names the procedure's signature declares
     */
    static void compileProcedureBody(final QueryExecutor queryExecutor, final UdfLanguage language,
                                     final String body, final Set<String> parameters) {
        if (queryExecutor == null || language != UdfLanguage.SQL || body == null || body.trim().isEmpty()) {
            return;
        }
        // A bare stage where none is taken is refused at CREATE, in the body's own frame (see StageArgumentSyntax).
        StageArgumentSyntax.requirePlacement(queryExecutor.routineBodyScript(body), body);
        final String trimmed = body.trim();
        final FrostlakeParser.BeginEndBlockContext block = queryExecutor.proceduralBlockOf(body);
        if (block != null) {
            // A string LIMIT or OFFSET value is the body's syntax error, placed in the body's own text.
            LimitValueSyntax.requireNumericValuesAsWritten(block);
            // The whole block is compiled at CREATE, reachable branches or not, so a bad declared
            // width is refused HERE and the procedure is never created (live-verified). The body is
            // parsed UNTRIMMED so the refusal's coordinates land in the body's own frame — line 1 is
            // the first line after the opening quote, exactly where a real account points.
            // The declared types and the integer literals, the DECLARE sections' names, the routine statements and
            // the INTO clauses standing where none may are judged in the order written, in the body's frame; a
            // DECLARE item repeating a parameter's name is refused after all of them (live-verified).
            RoutineStatementRules.requireNestedOptionOrder(block);
            ScriptingNameValidator.rejectDottedBindVariables(block);
            RoutineStatementRules.judgeNestedTypesAndRoutines(block);
            ScriptingNameValidator.rejectRedeclaration(block, parameters, true);
            return;
        }
        // A scripting condition written without its parentheses is positively known bad — a real
        // account rejects the procedure at CREATE with the two syntax-error lines the scanner
        // rebuilds (see BareScriptingConditionScanner).
        final String bareCondition = BareScriptingConditionScanner.describe(body, false);
        if (bareCondition != null) {
            throw new RuntimeException(SqlCompilationError.of(bareCondition));
        }
        queryExecutor.reportUnterminatedBlock(body);
        // A body that OPENS with a scripting statement and no block is positively known bad: a real
        // account refuses LET, RETURN, IF, WHILE, FOR, LOOP, CASE, BREAK and RAISE at the word
        // itself, in the body's own frame (live-verified for each of the nine).
        final Token strayOpener = strayScriptingOpener(body);
        if (strayOpener != null) {
            throw new RuntimeException(SqlCompilationError.of("syntax error " + describe(strayOpener) + "."));
        }
        // A statement inside the block that ends without its semicolon is positively known bad: the engine
        // read that statement whole, and the account refuses the body at CREATE (live-verified).
        final String missingTerminator = queryExecutor.missingTerminatorRefusal(body);
        if (missingTerminator != null) {
            throw new RuntimeException(missingTerminator);
        }
        // Everything else the engine cannot read whole is left alone — Frostlake's grammar is a
        // SUBSET of Snowflake's, so a body it cannot fully read (a long deployment block with a
        // construct the engine does not model, a multi-variable DECLARE section) means "unknown",
        // NOT "invalid". Rejecting those at CREATE would break working schemas that the account
        // itself accepts.
        if (!queryExecutor.isSinglePlainStatement(trimmed)) {
            return;
        }
        // A single plain statement IS a body a real account accepts, provided it is TERMINATED:
        // `$$ SELECT 1; $$`, `'INSERT INTO t VALUES (1);'` and `$$ CALL p(); $$` are created and CALL
        // answers NULL. Without its semicolon the body is refused at the end of the input, in the
        // frame live compiles it in — wrapped in a block of its own, BEGIN above and END; below — so
        // the sentence points at line (body lines + 2), position 4, for a one-, two- or three-line
        // body alike (live-verified).
        if (endsWithSemicolon(body)) {
            LimitValueSyntax.requireNumericValuesInText(body);
            return;
        }
        final int bodyLines = 1 + countNewlines(body);
        throw new RuntimeException(SqlCompilationError.of("syntax error line " + (bodyLines + 2)
            + " at position 4 unexpected '<EOF>'."));
    }

    /** The scripting keyword a block-less body opens with, or null when it opens any other way. */
    private static Token strayScriptingOpener(final String body) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
                continue;
            }
            final int type = token.getType();
            return type == FrostlakeLexer.LET || type == FrostlakeLexer.RETURN || type == FrostlakeLexer.IF
                || type == FrostlakeLexer.WHILE || type == FrostlakeLexer.FOR || type == FrostlakeLexer.LOOP
                || type == FrostlakeLexer.CASE || type == FrostlakeLexer.BREAK || type == FrostlakeLexer.RAISE
                ? token : null;
        }
        return null;
    }

    /** Whether the body's last spoken token is its terminating semicolon. */
    private static boolean endsWithSemicolon(final String body) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        Token last = null;
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                last = token;
            }
        }
        return last != null && last.getType() == FrostlakeLexer.SEMI;
    }

    private static int countNewlines(final String body) {
        int count = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * Compile a SQL UDF body: a query and a scalar expression are accepted outright, a scripting block is
     * accepted for a scalar function once {@link ScriptingUdfBodyValidator} has checked it (and refused for
     * a table function), a body that closes its own frame before a semicolon is the statement it ends there,
     * and a body that does not read is refused where live's frame breaks. A body in any other language and an
     * absent body are left alone.
     *
     * @param tableFunction whether the routine declares {@code RETURNS TABLE(…)}.
     * @param routineName   the routine's own unqualified name, so the validator can see a self-call.
     */
    /**
     * A body holding a plain word before a string — {@code val 'x'} — reads it as a typed literal of a type the
     * account does not know, and the function, or the policy, is refused by the pair's text while it compiles.
     */
    private static void rejectUnknownTypedLiteral(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.UnknownTypedLiteralExprContext) {
            final FrostlakeParser.UnknownTypedLiteralExprContext literal =
                (FrostlakeParser.UnknownTypedLiteralExprContext) tree;
            throw new RuntimeException("Compilation of SQL UDF failed: " + SqlCompilationError.of(
                "Unsupported data type literal '" + literal.IDENTIFIER().getText() + " "
                    + literal.STRING_LITERAL().getText() + "'."));
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            rejectUnknownTypedLiteral(tree.getChild(i));
        }
    }

    /**
     * An INTO clause anywhere in a SQL UDF's body is refused at CREATE, in the body's frame: {@code AS 'SELECT 1
     * INTO t'} is "error line 1 at position 1 INTO clause is not allowed in this context" (live-verified).
     */
    private static void rejectIntoClauses(final ParseTree body) {
        rejectIntoClauses(body, false);
    }

    /**
     * As {@link #rejectIntoClauses(ParseTree)}; {@code framePositions} when the statement's positions are the frame's
     * already, as they are in a statement that opens with the frame's own parenthesis (live-verified: {@code AS '1) +
     * (SELECT 1 INTO t);'} is refused at position 7).
     */
    private static void rejectIntoClauses(final ParseTree body, final boolean framePositions) {
        try {
            IntoClausePlacement.rejectInStatement(body, false);
        } catch (final RuntimeException refused) {
            final String message = framePositions ? refused.getMessage() : inBodyFrame(refused.getMessage());
            throw new RuntimeException("Compilation of SQL UDF failed: " + message, refused);
        }
    }

    /** Whether a script is one SELECT … INTO statement. */
    private static boolean isSelectIntoStatement(final FrostlakeParser.SqlScriptContext script) {
        if (script.flowChain().size() != 1 || script.flowChain().get(0).statement().size() != 1) {
            return false;
        }
        final FrostlakeParser.StatementContext statement = script.flowChain().get(0).statement().get(0);
        return statement.proceduralStatement() != null && statement.proceduralStatement().selectIntoStatement() != null;
    }

    static void compileFunctionBody(final QueryExecutor queryExecutor, final UdfLanguage language,
                                    final String body, final boolean tableFunction,
                                    final String routineName, final Set<String> parameters,
                                    final String scopeDatabase, final String scopeSchema) {
        if (queryExecutor == null || language != UdfLanguage.SQL || body == null) {
            return;
        }
        final int literal = literalLeftOpen(body);
        if (literal < 0) {
            compileReadableBody(queryExecutor, language, body, tableFunction, routineName, parameters, scopeDatabase,
                scopeSchema);
            return;
        }
        // Besides the literal, the account reads on: past a string's opening quote, where a quoted name's takes the
        // rest of the text, as this lexer's does. What it reads is refused for its syntax alone, and never as a
        // scripting block: its lexer stopped, so it reads the body in the frame, BEGIN RETURN 'abc; END at 'RETURN'.
        final String read = body.charAt(literal - 1) == '\''
            ? body.substring(0, literal - 1) + " " + body.substring(literal) : body;
        String syntax = null;
        if (queryExecutor.proceduralBlockOf(read.trim()) != null) {
            syntax = describeUnreadableExpression(queryExecutor, read);
        } else {
            try {
                compileReadableBody(queryExecutor, language, read, tableFunction, routineName, parameters,
                    scopeDatabase, scopeSchema);
            } catch (final RuntimeException refused) {
                syntax = syntaxLines(refused);
            }
        }
        throw new RuntimeException("Compilation of SQL UDF failed: "
            + SqlCompilationError.of(literalLeftOpenRefusal(body, literal, read, syntax)));
    }

    /** Compile a SQL UDF body the lexer reads to its end, or whose literal left open the account never reads. */
    private static void compileReadableBody(final QueryExecutor queryExecutor, final UdfLanguage language,
                                            final String body, final boolean tableFunction,
                                            final String routineName, final Set<String> parameters,
                                            final String scopeDatabase, final String scopeSchema) {
        final Set<String> declared = parameters == null ? Collections.<String>emptySet() : parameters;
        final ScriptingUdfBodyValidator validator =
            new ScriptingUdfBodyValidator(queryExecutor, queryExecutor.getCatalog(), routineName);
        requireStagePlacement(queryExecutor.routineBodyScript(body), body, tableFunction);
        // A body that closes its own frame and then writes a semicolon is the statement the semicolon ends; nothing
        // after it is read.
        final SqlUdfBodyFrame frame = SqlUdfBodyFrame.closedAtSemicolon(body);
        if (frame != null && !frame.isFramed()) {
            compileFunctionBody(queryExecutor, language, frame.statement(), tableFunction, routineName, parameters,
                scopeDatabase, scopeSchema);
            return;
        }
        if (frame != null) {
            compileFramedStatement(queryExecutor, body, frame.statement(), validator, routineName, declared,
                scopeDatabase, scopeSchema);
            return;
        }
        final String trimmed = body.trim();

        // A body written SELECT … INTO reads as the scripting statement, whose INTO clause a function cannot carry.
        final FrostlakeParser.SqlScriptContext script = queryExecutor.scriptOf(trimmed);
        if (script != null && isSelectIntoStatement(script)) {
            rejectIntoClauses(script);
        }
        final FrostlakeParser.SqlScriptContext queryBody = queryExecutor.queryStatementOf(trimmed);
        if (queryBody != null) {
            rejectQueryBodyEnding(queryExecutor, body, trimmed, tableFunction);
            rejectIntoClauses(queryBody);
            rejectUnknownTypedLiteral(queryBody);
            validator.rejectScriptingUdfCalls(queryBody);
            resolveQueryBodyNames(queryExecutor, trimmed, declared, scopeDatabase, scopeSchema);
            SqlUdfBodyCalls.reject(queryExecutor, queryBody, trimmed, routineName, scopeDatabase, scopeSchema);
            return;
        }
        final FrostlakeParser.BeginEndBlockContext block = queryExecutor.proceduralBlockOf(trimmed);
        if (block != null) {
            if (tableFunction) {
                // Live-verified: `RETURNS TABLE(x INT) AS $$ BEGIN RETURN TABLE(SELECT …); END $$` is a
                // plain syntax error on the account — a block is a body shape only a SCALAR UDF has.
                throw new RuntimeException("Compilation of SQL UDF failed: SQL compilation error: syntax error "
                    + describe(AntlrExpressionParser.failurePoint(trimmed))
                    + ". A table function body must be a query, not a Snowflake Scripting block.");
            }
            validator.validate(block);
            return;
        }
        final FrostlakeParser.BooleanExprContext expression;
        try {
            expression = AntlrExpressionParser.parseTree(trimmed);
        } catch (final RuntimeException notAnExpression) {
            // A parenthesised list of values reads as a ROW: its names and calls are judged here, and the type
            // check refuses the ROW no declared type takes.
            final FrostlakeParser.ExprTupleContext row = tableFunction ? null : SqlUdfBodyFrame.rowOf(body);
            if (row != null) {
                rejectUnknownTypedLiteral(row);
                validator.rejectScriptingUdfCalls(row);
                SqlUdfBodyNames.rejectUnknownNames(row, declared, 0);
                SqlUdfBodyCalls.reject(queryExecutor, row, "(" + body + ")", routineName, scopeDatabase, scopeSchema);
                return;
            }
            final Token straySemicolon = straySemicolonAfterExpression(body);
            if (straySemicolon != null) {
                throw new RuntimeException("Compilation of SQL UDF failed: "
                    + SqlCompilationError.of("syntax error " + describeInBody(straySemicolon) + "."));
            }
            // A scripting condition without its parentheses is positively known bad even here — a
            // real account rejects the function at CREATE, anchored on the condition keyword.
            final String bareCondition = BareScriptingConditionScanner.describe(body, true);
            if (bareCondition != null) {
                throw new RuntimeException(
                    "Compilation of SQL UDF failed: " + SqlCompilationError.of(bareCondition));
            }
            // Any other body that does not read is refused where live's frame breaks.
            final String unreadable = describeUnreadableExpression(queryExecutor, body);
            if (unreadable != null) {
                throw new RuntimeException(
                    "Compilation of SQL UDF failed: " + SqlCompilationError.of(unreadable));
            }
            // A body the account reads with a bare select-list alias this grammar lacks stays FAIL-OPEN (see
            // SqlUdfQueryBodyRefusal), as do a query this grammar stops reading before its end and a frame this
            // grammar reads whole.
            return;
        }
        // An expression body that ends inside a line comment leaves live's frame open, so the refusal
        // is at the end of the body.
        if (endsInsideLineComment(body)) {
            throw new RuntimeException("Compilation of SQL UDF failed: "
                + SqlCompilationError.of("syntax error " + describeEndOfBody(body) + "."));
        }
        // A character the lexer cannot read is no part of any expression: a body that reads only without it, x ! = y,
        // is refused rather than run as x = y.
        final String unreadCharacter = unreadCharacterRefusal(body);
        if (unreadCharacter != null) {
            throw new RuntimeException("Compilation of SQL UDF failed: " + SqlCompilationError.of(unreadCharacter));
        }
        // A bare stage is judged in the expression's frame, its places the body's own (the untrimmed body's).
        if (body.indexOf('@') >= 0) {
            StageArgumentSyntax.requireFramedPlacement(AntlrExpressionParser.parseTree(body));
        }
        rejectIntoClauses(expression);
        rejectUnknownTypedLiteral(expression);
        validator.rejectScriptingUdfCalls(expression);
        SqlUdfBodyNames.rejectUnknownNames(expression, declared);
        SqlUdfBodyCalls.reject(queryExecutor, expression, trimmed, routineName, scopeDatabase, scopeSchema);
    }

    /**
     * A query or block body's bare stages, judged at CREATE in the frame the body compiles in, its places the untrimmed
     * body's own: a table function's query inside the frame of parentheses an expression body has — "Compilation of
     * SQL UDF failed: " before the refusal, a place on the first line moved by one — and a scalar function's query or
     * block as a statement of its own, neither (live-verified). An expression body is judged once it reads as one.
     *
     * @param script        the body as parsed before any stage in it is judged, or null when it does not parse
     * @param body          the body's text
     * @param tableFunction whether the routine declares {@code RETURNS TABLE(…)}
     */
    private static void requireStagePlacement(final FrostlakeParser.SqlScriptContext script, final String body,
                                              final boolean tableFunction) {
        if (script == null || script.flowChain().size() != 1 || script.flowChain().get(0).statement().size() != 1) {
            return;
        }
        final FrostlakeParser.StatementContext statement = script.flowChain().get(0).statement().get(0);
        if (statement.queryStatement() != null) {
            if (tableFunction) {
                StageArgumentSyntax.requireFramedPlacement(statement);
            } else {
                StageArgumentSyntax.requirePlacement(statement, body);
            }
        } else if (!tableFunction && statement.proceduralStatement() != null
                && statement.proceduralStatement().beginEndBlock() != null) {
            StageArgumentSyntax.requirePlacement(statement, body);
        }
    }

    /**
     * Resolve a QUERY body's names at CREATE, as a real account does: a relation it cannot find and a
     * column nothing holds are refused there, not at the first call. The body is compiled for its SHAPE
     * alone, the way CREATE VIEW learns its columns, so a value-time fault in it stays the reader's.
     *
     * <p>A query body may also read the function's own PARAMETERS, which a body compiled on its own
     * cannot see - {@code AS 'SELECT a FROM t WHERE a = x'} is created on the account. A body that names one
     * is compiled again with its parameters in place, as a call runs it, so a name nothing declares is still
     * refused where the body writes it: {@code SELECT x, nosuch FROM t} at 'NOSUCH', position 11.
     */
    private static void resolveQueryBodyNames(final QueryExecutor queryExecutor, final String trimmed,
                                              final Set<String> parameters, final String scopeDatabase,
                                              final String scopeSchema) {
        try {
            queryExecutor.resolveViewShapeInScope(scopeDatabase, scopeSchema, trimmed, null);
        } catch (final OuterJoinOperandException misplacedMarker) {
            throw new RuntimeException("Compilation of SQL UDF failed: " + misplacedMarker.getMessage());
        } catch (final RuntimeException unresolved) {
            if (SqlUdfBodyCalls.isUnknownFunctionRefusal(unresolved)) {
                // The body's calls are judged next, where a function the account has built in is not unknown
                // and a cycle speaks first.
                return;
            }
            final String message = String.valueOf(unresolved.getMessage());
            final Matcher named = INVALID_IDENTIFIER.matcher(message);
            if (named.find() && parameters.contains(named.group(1).toUpperCase())) {
                resolveWithParameters(queryExecutor, trimmed, parameters, scopeDatabase, scopeSchema);
                return;
            }
            throw new RuntimeException(inBodyFrame(message));
        }
    }

    /**
     * Resolve a query body's names with its parameters in place: a name nothing declares is refused in the body's
     * own positions, and any other refusal is left to the type check and the run.
     */
    private static void resolveWithParameters(final QueryExecutor queryExecutor, final String trimmed,
                                              final Set<String> parameters, final String scopeDatabase,
                                              final String scopeSchema) {
        final ParameterSubstitutedBody substituted = ParameterSubstitutedBody.untyped(trimmed, parameters);
        try {
            queryExecutor.resolveViewShapeInScope(scopeDatabase, scopeSchema, substituted.text(), null);
        } catch (final RuntimeException unresolved) {
            final String message = String.valueOf(unresolved.getMessage());
            if (INVALID_IDENTIFIER.matcher(message).find()) {
                throw new RuntimeException(inBodyFrame(substituted.inWrittenPositions(message)));
            }
        }
    }

    /**
     * A refusal from the body, moved into the body's own frame. A body is compiled inside a frame of its
     * own on the account - one character on either side - so its first line counts from position 1 where
     * a statement's counts from 0, and every later line is unmoved (live-verified).
     */
    private static String inBodyFrame(final String message) {
        final Matcher onFirstLine = FIRST_LINE_POSITION.matcher(message);
        if (!onFirstLine.find()) {
            return message;
        }
        return onFirstLine.replaceFirst(onFirstLine.group(1)
            + (Integer.parseInt(onFirstLine.group(2)) + 1));
    }

    /**
     * How a QUERY body may end. It is a SQL UDF body only WITHOUT a terminating semicolon. With one, the
     * account reads the body as a Snowscript UDF, where a query is not a statement: {@code $$ SELECT 1; $$}
     * is refused as "Unsupported statement type for Snowscript UDF: query statement" — a CTE, a
     * parenthesised query, a single-quoted body, a doubled {@code ;;} and a trailing comment alike. More
     * than one statement is refused by COUNT before that, whatever the statements are ("Actual statement
     * count 2 did not match the desired statement count 1."). A body that ends INSIDE a line comment,
     * {@code $$ SELECT 1 -- c $$}, cannot be framed at all (see {@link #endsInsideLineComment}) and is a
     * syntax error where the frame breaks — as is either other shape in a TABLE function, which has no
     * Snowscript form. Live-verified, every cell.
     */
    private static void rejectQueryBodyEnding(final QueryExecutor queryExecutor, final String body,
                                              final String trimmed, final boolean tableFunction) {
        final int statements = queryExecutor.statementCountOf(trimmed);
        final boolean terminated = endsWithSemicolon(body);
        final boolean unframed = endsInsideLineComment(body);
        if (tableFunction) {
            if (terminated || statements > 1 || unframed) {
                throw new RuntimeException("Compilation of SQL UDF failed: "
                    + SqlCompilationError.of("syntax error " + misframedQueryBody(body) + "."));
            }
            return;
        }
        if (statements > 1) {
            throw new RuntimeException("Actual statement count " + statements
                + " did not match the desired statement count 1.");
        }
        if (terminated) {
            throw new RuntimeException("Unsupported statement type for Snowscript UDF: query statement");
        }
        if (unframed) {
            throw new RuntimeException("Compilation of SQL UDF failed: "
                + SqlCompilationError.of("syntax error " + misframedQueryBody(body) + "."));
        }
    }

    /**
     * Where a query body's frame breaks. Live compiles a SQL UDF body inside parentheses of its own, one
     * character on either side, so a body that opens with a parenthesised group reads that group as an
     * expression and must end right after it: the refusal names the first token past the group, or the
     * end of the body when nothing follows — {@code (SELECT 1 AS a);} in a table function at its
     * semicolon, {@code (SELECT 1) UNION (SELECT 2) -- c} at the UNION, {@code (SELECT 1) -- c} at the
     * end. Any other query body is refused at its first word.
     */
    private static String misframedQueryBody(final String body) {
        final List<Token> spoken = spokenTokens(body);
        if (spoken.get(0).getType() != FrostlakeLexer.LPAREN) {
            return describeInBody(spoken.get(0));
        }
        int depth = 0;
        for (int i = 0; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i + 1 < spoken.size() ? describeInBody(spoken.get(i + 1)) : describeEndOfBody(body);
                }
            }
        }
        return describeEndOfBody(body);
    }

    /**
     * The syntax errors of a body that does not read, or null for a body whose frame reads and for one the account
     * reads with a bare select-list alias this grammar lacks. Live reads the body inside a frame of parentheses, so
     * the refusal names the token the framed body breaks on, in the frame's own positions — {@code this is not sql}
     * at 'is', position 6, and {@code 1 2} at '2', position 3:
     * <ul>
     *   <li>a body holding a query that runs out of text is refused at the query's keyword (see
     *       {@link SqlUdfQueryBodyRefusal}); one this grammar stops reading before its end, {@code ORDER BY ALL},
     *       may be one only the account's grammar reads and is left alone;</li>
     *   <li>a bare {@code CASE} is refused at what follows it, as the frame's other unfinished tails are;</li>
     *   <li>a body that stops before its expression does is refused where the frame runs out: at the frame's
     *       closing character, {@code 1 +} at position 4, with the end of input refused after it while the
     *       body leaves a parenthesis of its own open; or at the end of input alone when the body's open
     *       parenthesis takes that closing character, {@code 1 + (2} at position 8;</li>
     *   <li>a predicate left unfinished after {@code IS}, {@code IS NOT} or {@code IS [NOT] DISTINCT}, and
     *       an operand {@code COLLATE} or {@code ESCAPE} cannot take, is refused at that keyword instead —
     *       {@code x IS 5} at 'IS', position 3, twice over for the DISTINCT forms;</li>
     *   <li>a body that closes the frame itself is refused at whatever follows, {@code 1) + 1} at the second
     *       ')', unless a semicolon ends the statement there: live accepts {@code 1); x}, and refuses a statement
     *       that stops before its expression does at that semicolon.</li>
     * </ul>
     * Live-verified.
     */
    private static String describeUnreadableExpression(final QueryExecutor queryExecutor, final String body) {
        final String framed = "(" + body + ")";
        final List<Token> framedTokens = spokenTokens(framed);
        final Token semicolon = semicolonAfterClosedFrame(framedTokens);
        if (semicolon != null) {
            return describeFramedStatement(framed, framedTokens, semicolon);
        }
        int unclosed = 0;
        boolean holdsQuery = false;
        final List<Token> bodyTokens = spokenTokens(body);
        for (final Token token : bodyTokens) {
            holdsQuery |= token.getType() == FrostlakeLexer.SELECT || token.getType() == FrostlakeLexer.WITH;
            if (token.getType() == FrostlakeLexer.LPAREN) {
                unclosed++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                unclosed--;
            }
        }
        Token stop = AntlrExpressionParser.offendingToken(framed);
        if (holdsQuery) {
            if (SqlUdfQueryBodyRefusal.readsWithBareAlias(queryExecutor, body)) {
                return null;
            }
            if (!readsToEarlyClose(framedTokens, stop)) {
                final String query = SqlUdfQueryBodyRefusal.describe(framed, framedTokens, stop, unclosed);
                if (query != null) {
                    // A query this grammar stops reading before its end may hold a construct only the account's
                    // grammar has, ORDER BY ALL or MATCH_RECOGNIZE, and stays FAIL-OPEN; one that runs out of text
                    // is refused.
                    return stop == null || SqlUdfQueryBodyRefusal.runsOut(framed) ? query : null;
                }
            }
        } else {
            // A character the lexer cannot read is a token no expression takes: the frame breaks there unless it
            // broke before it.
            final int unread = firstLexerFault(framed);
            if (unread >= 0 && !isQuote(framed.charAt(unread))
                    && (stop == null || stop.getStartIndex() >= framed.codePointCount(0, unread))) {
                return unreadCharacterRefusal(framed, unread);
            }
        }
        if (stop == null) {
            final Token rest = AntlrExpressionParser.failurePoint(framed);
            if (rest.getType() == Token.EOF || rest.getType() == FrostlakeLexer.SEMI) {
                return null;
            }
            return "syntax error " + describe(rest) + ".";
        }
        final String keywordRefusal = describeUnfinishedPredicate(framedTokens, stop);
        if (keywordRefusal != null) {
            return keywordRefusal;
        }
        final Token caseEnd = bareCaseEnd(framedTokens, stop);
        if (caseEnd != null) {
            stop = caseEnd;
        }
        final boolean castLeavesFrameClosed = castLeavesFrameClosed(bodyTokens);
        if (castLeavesFrameClosed && stop.getType() == Token.EOF) {
            // The account abandons the CAST at the frame's closing character, where this grammar reads on past it.
            stop = framedTokens.get(framedTokens.size() - 1);
        }
        final String refusal = "syntax error " + describe(stop) + ".";
        if (stop.getType() == FrostlakeLexer.RPAREN && stop.getStartIndex() == framed.length() - 1 && unclosed > 0
                && !castLeavesFrameClosed) {
            return refusal + "\nsyntax error " + describeEndOfBody(body) + ".";
        }
        final Token extra = caseEnd == null ? null : tokenAfterFrameClose(framedTokens, caseEnd);
        return extra == null ? refusal : refusal + "\nsyntax error " + describe(extra) + ".";
    }

    /**
     * The refusal of a ROW body one of whose values this engine cannot type. The account refuses every ROW body, so
     * this one is refused too, where its frame reads no expression, as the body is refused without the ROW reading.
     *
     * @param queryExecutor the executor whose parser reads the body
     * @param body          the body as the call runs it
     * @return the syntax error, without the compilation prefix
     */
    static String untypedRowRefusal(final QueryExecutor queryExecutor, final String body) {
        final String unreadable = describeUnreadableExpression(queryExecutor, body);
        return unreadable != null ? unreadable
            : "syntax error " + describe(AntlrExpressionParser.failurePoint("(" + body + ")")) + ".";
    }

    /**
     * Whether the parenthesis the body leaves open last is a CAST's, before its AS, with nothing but groups and other
     * such CASTs open around it. The account abandons that CAST at the frame's closing character and the frame
     * closes there, so no end of input is refused after it: {@code CAST(1} and {@code (CAST(} are refused at ')'
     * alone, where {@code ABS(CAST(1} and {@code CAST(1 AS} are refused at the end of input too (live-verified).
     */
    private static boolean castLeavesFrameClosed(final List<Token> body) {
        final List<Integer> open = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            final int type = body.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                final Token before = i > 0 ? body.get(i - 1) : null;
                open.add(before == null ? GROUP_PAREN
                    : before.getType() == FrostlakeLexer.CAST || before.getType() == FrostlakeLexer.TRY_CAST
                        ? CAST_PAREN : isCalledName(before) ? CALL_PAREN : GROUP_PAREN);
            } else if (type == FrostlakeLexer.RPAREN && !open.isEmpty()) {
                open.remove(open.size() - 1);
            } else if (type == FrostlakeLexer.AS && !open.isEmpty() && open.get(open.size() - 1) == CAST_PAREN) {
                open.set(open.size() - 1, CALL_PAREN);
            }
        }
        if (open.isEmpty() || open.get(open.size() - 1) != CAST_PAREN) {
            return false;
        }
        for (final int kind : open) {
            if (kind == CALL_PAREN) {
                return false;
            }
        }
        return true;
    }

    /** Whether a parenthesis after {@code token} opens a call's argument list: the token is a name. */
    private static boolean isCalledName(final Token token) {
        return token.getType() == FrostlakeLexer.IDENTIFIER || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /**
     * The token a bare {@code CASE} leaves the framed body at, or null when the parse broke elsewhere. The
     * account reads the CASE and breaks on what follows it — {@code CASE} at the frame's closing parenthesis,
     * position 5, {@code ABS(CASE)} at the ')' after it — where this grammar breaks on the CASE itself, or, for
     * {@code (CASE}, only at the end of input (live-verified).
     */
    private static Token bareCaseEnd(final List<Token> framed, final Token stop) {
        for (int i = 0; i + 1 < framed.size(); i++) {
            final Token next = framed.get(i + 1);
            if (framed.get(i).getType() == FrostlakeLexer.CASE && next.getType() == FrostlakeLexer.RPAREN
                    && (stop.getType() == Token.EOF || stop.getStartIndex() >= framed.get(i).getStartIndex())) {
                return next;
            }
        }
        return null;
    }

    /**
     * The token after {@code close} when that parenthesis closes the frame before the frame's own closing one, or
     * null: {@code CASE)} is refused at its ')', position 5, and again at the frame's, position 6 (live-verified).
     */
    private static Token tokenAfterFrameClose(final List<Token> framed, final Token close) {
        int depth = 0;
        for (int i = 0; i < framed.size(); i++) {
            if (framed.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (framed.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (framed.get(i) == close) {
                return depth == 0 && i + 1 < framed.size() ? framed.get(i + 1) : null;
            }
        }
        return null;
    }

    /**
     * Whether the framed body reads up to a parenthesis that closes the frame before the frame's own closing one:
     * {@code SELECT 1 FROM (SELECT 1)) x} holds a whole query in its frame, which the account reads as an
     * expression, and is refused at what follows, 'x', position 27.
     */
    private static boolean readsToEarlyClose(final List<Token> framed, final Token stop) {
        int depth = 0;
        for (int i = 0; i + 1 < framed.size(); i++) {
            if (framed.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (framed.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return stop == null || stop.getStartIndex() > framed.get(i).getStartIndex();
                }
            }
        }
        return false;
    }

    /**
     * The first semicolon of a framed body whose frame closed before it, or null — also when a parenthesis closed
     * more than the frame opened, which is refused where it stands. The account ends the statement at that
     * semicolon, so a body that reopens a parenthesis after closing its frame is refused there: {@code 1) + (2;} at
     * ';', position 8.
     */
    private static Token semicolonAfterClosedFrame(final List<Token> framed) {
        int depth = 0;
        boolean closed = false;
        for (final Token token : framed) {
            if (token.getType() == FrostlakeLexer.SEMI) {
                return closed ? token : null;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth < 0) {
                    return null;
                }
                closed |= depth == 0;
            }
        }
        return null;
    }

    /**
     * The refusal of the statement a semicolon ends after the body closed its frame: the token the statement breaks
     * on, or the semicolon when the statement stops before its expression does — {@code 1) +;} at ';',
     * position 5, {@code 1) 2;} at '2', position 4. The statement is an expression, so a query clause after the
     * frame's closing parenthesis is refused at its keyword: {@code SELECT 1) UNION ALL SELECT 2;} at 'UNION',
     * position 11, and {@code SELECT 1) ORDER BY 1;} at 'ORDER' (live-verified).
     */
    private static String describeFramedStatement(final String framed, final List<Token> framedTokens,
                                                  final Token semicolon) {
        final Token at = framedStatementBreak(framed, framedTokens, semicolon);
        final String keywordRefusal = describeUnfinishedPredicate(framedTokens, at);
        return keywordRefusal != null ? keywordRefusal : "syntax error " + describe(at) + ".";
    }

    /**
     * The token the statement a semicolon ends after the body closed its frame breaks on, or that semicolon (see
     * {@link #describeFramedStatement}).
     */
    private static Token framedStatementBreak(final String framed, final List<Token> framedTokens,
                                              final Token semicolon) {
        final String statement = framed.substring(0, semicolon.getStartIndex());
        // Where the statement's parse breaks, as a character index into the frame, or -1 for its end.
        final int breaks;
        final Token rest = AntlrExpressionParser.offendingToken(statement) == null
            ? AntlrExpressionParser.failurePoint(statement) : null;
        if (rest != null && !continuesExpression(rest)) {
            // The statement opens with a whole expression, which what follows does not continue: a query clause
            // after it, or a word, is where it breaks.
            breaks = rest.getType() == Token.EOF ? -1 : rest.getStartIndex();
        } else {
            // Read inside one more pair of parentheses, so the parse cannot stop short of its end: a statement that
            // stops before its expression does breaks at that parenthesis, which is the semicolon's place.
            final Token stop = AntlrExpressionParser.offendingToken("(" + statement + ")");
            breaks = stop == null || stop.getType() == Token.EOF ? -1 : stop.getStartIndex() - 1;
        }
        Token at = semicolon;
        for (final Token token : framedTokens) {
            if (token.getStartIndex() == breaks && token.getStartIndex() < statement.length()) {
                at = token;
            }
        }
        return at;
    }

    /**
     * Whether a token could carry an expression on: an operator, or a word that opens a predicate's other side or
     * joins another condition. A comma, a closing parenthesis and every other word end the expression before them.
     */
    private static boolean continuesExpression(final Token token) {
        final int type = token.getType();
        if (type == FrostlakeLexer.AND || type == FrostlakeLexer.OR || type == FrostlakeLexer.NOT
                || type == FrostlakeLexer.IS || type == FrostlakeLexer.IN || type == FrostlakeLexer.BETWEEN
                || type == FrostlakeLexer.LIKE || type == FrostlakeLexer.ILIKE || type == FrostlakeLexer.RLIKE
                || type == FrostlakeLexer.REGEXP || type == FrostlakeLexer.COLLATE || type == FrostlakeLexer.ESCAPE) {
            return true;
        }
        final String text = token.getText();
        if (type == Token.EOF || text == null || text.isEmpty()) {
            return false;
        }
        final char first = text.charAt(0);
        return !Character.isLetterOrDigit(first) && first != '_' && first != '$' && first != '"' && first != '\''
            && first != ',' && first != ')' && first != ';';
    }

    /**
     * Compile the statement a body ends at a semicolon after closing its own frame, when something follows that
     * closing parenthesis: {@code (1)) + 1;} is the expression {@code ((1)) + 1}. The statement stands in the frame's
     * own positions, so a name it does not declare is refused where it stands there.
     */
    private static void compileFramedStatement(final QueryExecutor queryExecutor, final String body,
                                               final String statement, final ScriptingUdfBodyValidator validator,
                                               final String routineName, final Set<String> declared,
                                               final String scopeDatabase, final String scopeSchema) {
        final FrostlakeParser.BooleanExprContext expression;
        try {
            expression = AntlrExpressionParser.parseTree(statement);
        } catch (final RuntimeException unreadable) {
            final String framed = "(" + body + ")";
            final List<Token> framedTokens = spokenTokens(framed);
            final Token semicolon = semicolonAfterClosedFrame(framedTokens);
            throw new RuntimeException("Compilation of SQL UDF failed: " + SqlCompilationError.of(
                describeFramedStatement(framed, framedTokens, semicolon)));
        }
        rejectIntoClauses(expression, true);
        rejectUnknownTypedLiteral(expression);
        validator.rejectScriptingUdfCalls(expression);
        SqlUdfBodyNames.rejectUnknownNames(expression, declared, 0);
        SqlUdfBodyCalls.reject(queryExecutor, expression, statement, routineName, scopeDatabase, scopeSchema);
    }

    /**
     * Where the lexer stops at a literal left open that the account reads, as a character index into the framed
     * body, or -1 when there is none. The account reads a statement's semicolon and the one token after it, and no
     * further, so a literal after a semicolon is read only when it is that token: {@code 1); 'abc} is refused, where
     * {@code 1); x 'abc}, {@code 1) ; ; 'abc} and {@code BEGIN RETURN 1; END 'abc} are read without it
     * (live-verified).
     */
    private static int literalLeftOpen(final String body) {
        final String framed = "(" + body + ")";
        final int fault = firstLexerFault(framed);
        if (fault < 0 || !isQuote(framed.charAt(fault))) {
            return -1;
        }
        final List<Token> before = spokenTokens(framed.substring(0, fault));
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).getType() == FrostlakeLexer.SEMI) {
                return i == before.size() - 1 ? fault : -1;
            }
        }
        return fault;
    }

    /**
     * The refusal of a body holding a literal left open. The account's lexer runs out of input inside the literal
     * and says so at the frame's end — {@code 'abc} at position 6, {@code 1); 'unterminated} at 19 — beside the
     * syntax errors of what it reads, {@code 1 'abc} at 'abc', position 4, and {@code "abc} at the end of input. Its
     * parser reads a token past a word or a semicolon before it reports a break there, so the lexer's line comes
     * first when the parse breaks on the literal or after it, or on a word or a semicolon the literal directly
     * follows — {@code BEGIN RETURN 'abc; END} ahead of 'RETURN' — and last when the parse breaks on any other token
     * before it: {@code 1 2 'abc} after '2', {@code BEGIN RETURN 1 + 'abc; END} after 'RETURN' (live-verified).
     *
     * @param body    the body as written
     * @param literal where the literal opens, as a character index into the framed body
     * @param read    the body as the account reads it besides the literal
     * @param syntax  the syntax errors of what the account reads, or null when it reads
     */
    private static String literalLeftOpenRefusal(final String body, final int literal, final String read,
                                                 final String syntax) {
        final String framed = "(" + body + ")";
        final String parse = "parse error line " + EndOfInput.line(framed) + " at position "
            + EndOfInput.position(framed) + " near '<EOF>'.";
        if (syntax == null) {
            return parse;
        }
        final String framedRead = "(" + read + ")";
        final List<Token> readTokens = spokenTokens(framedRead);
        final Token semicolon = semicolonAfterClosedFrame(readTokens);
        final Token offending = semicolon != null ? framedStatementBreak(framedRead, readTokens, semicolon)
            : AntlrExpressionParser.offendingToken(framedRead);
        final Token breaks = offending != null ? offending : AntlrExpressionParser.failurePoint(framedRead);
        final List<Token> before = spokenTokens(framed.substring(0, literal));
        final boolean literalFirst = breaks.getType() == Token.EOF || breaks.getStartIndex() >= literal
            || !before.isEmpty() && before.get(before.size() - 1).getStartIndex() == breaks.getStartIndex()
                && (isWord(breaks) || breaks.getType() == FrostlakeLexer.SEMI);
        return literalFirst ? parse + "\n" + syntax : syntax + "\n" + parse;
    }

    /** Whether a token is a word: a name, quoted or not, or a keyword. */
    private static boolean isWord(final Token token) {
        final String text = token.getText();
        return text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_' || text.charAt(0) == '"');
    }

    /** The syntax errors a body is refused for, one per line, or null when it is refused for anything else. */
    private static String syntaxLines(final RuntimeException refused) {
        final String header = "Compilation of SQL UDF failed: " + SqlCompilationError.of("");
        final String message = String.valueOf(refused.getMessage());
        if (!message.startsWith(header)) {
            return null;
        }
        final String lines = message.substring(header.length());
        for (final String line : lines.split("\n")) {
            if (!line.startsWith("syntax error line ") && !line.startsWith("parse error line ")) {
                return null;
            }
        }
        return lines;
    }

    /** Whether a character opens a literal: a string's or a quoted name's quote. */
    private static boolean isQuote(final char c) {
        return c == '\'' || c == '"';
    }

    /**
     * The refusal of a body holding a character the lexer cannot read, at that character, or null when the lexer
     * reads the body whole or stops at a literal left open.
     */
    private static String unreadCharacterRefusal(final String body) {
        final String framed = "(" + body + ")";
        final int fault = firstLexerFault(framed);
        return fault < 0 || isQuote(framed.charAt(fault)) ? null : unreadCharacterRefusal(framed, fault);
    }

    /**
     * The refusal at a character the lexer cannot read, in the frame's positions. The account's lexer makes one token
     * of that character and the one after it, whatever that is — a zero-width space before {@code 1} is refused with
     * both, and {@code x + 1 #} at '#)', position 7, with the frame's own parenthesis — and reads on after it, so a
     * quote the token takes leaves the rest of the frame an unterminated literal, refused at the frame's end too
     * (live-verified).
     *
     * @param framed the body inside its frame
     * @param fault  the character index of the character the lexer cannot read
     */
    private static String unreadCharacterRefusal(final String framed, final int fault) {
        final int second = fault + Character.charCount(framed.codePointAt(fault));
        final int end = second < framed.length() ? second + Character.charCount(framed.codePointAt(second)) : second;
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < fault; i++) {
            if (framed.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        final String refusal = "syntax error line " + line + " at position " + framed.codePointCount(lineStart, fault)
            + " unexpected '" + framed.substring(fault, end) + "'.";
        final String rest = framed.substring(end);
        final int unread = firstLexerFault(rest);
        if (unread >= 0 && isQuote(rest.charAt(unread))) {
            return refusal + "\nparse error line " + EndOfInput.line(framed) + " at position "
                + EndOfInput.position(framed) + " near '<EOF>'.";
        }
        return refusal;
    }

    /** Where the lexer first fails to read {@code text}, as a character index into it, or -1 when it reads it all. */
    private static int firstLexerFault(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final int[] fault = {-1};
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (fault[0] < 0 && e instanceof LexerNoViableAltException) {
                    fault[0] = ((LexerNoViableAltException) e).getStartIndex();
                }
            }
        });
        new CommonTokenStream(lexer).fill();
        // The lexer counts code points; a character outside the basic plane is two chars of the text.
        return fault[0] < 0 ? -1 : text.offsetByCodePoints(0, fault[0]);
    }

    /**
     * The refusal at the keyword a framed body's parse broke after, or null when it broke elsewhere: the
     * {@code IS} of an unfinished {@code IS}, {@code IS NOT}, {@code IS DISTINCT} or {@code IS NOT DISTINCT}
     * (named twice for the DISTINCT forms), or a {@code COLLATE} or {@code ESCAPE} whose operand did not
     * read. Live-verified.
     */
    private static String describeUnfinishedPredicate(final List<Token> framed, final Token stop) {
        int before = framed.size() - 1;
        for (int i = 0; i < framed.size(); i++) {
            if (framed.get(i).getStartIndex() >= stop.getStartIndex() && stop.getType() != Token.EOF) {
                before = i - 1;
                break;
            }
        }
        if (before < 0) {
            return null;
        }
        final int last = framed.get(before).getType();
        if (last == FrostlakeLexer.COLLATE || last == FrostlakeLexer.ESCAPE) {
            return "syntax error " + describe(framed.get(before)) + ".";
        }
        int keyword = before;
        final boolean distinct = framed.get(keyword).getType() == FrostlakeLexer.DISTINCT;
        if (distinct) {
            keyword--;
        }
        if (keyword >= 0 && framed.get(keyword).getType() == FrostlakeLexer.NOT) {
            keyword--;
        }
        if (keyword < 0 || framed.get(keyword).getType() != FrostlakeLexer.IS) {
            return null;
        }
        final String refusal = "syntax error " + describe(framed.get(keyword)) + ".";
        return distinct ? refusal + "\n" + refusal : refusal;
    }

    /**
     * Whether the body ends inside a line comment, {@code $$ 1 -- c $$} or {@code $$ SELECT 1\n-- c $$}.
     * The closing character of live's frame then falls inside the comment, so the frame never closes. A
     * comment followed by a line break, and a block comment, leave the frame alone.
     */
    private static boolean endsInsideLineComment(final String body) {
        final List<Token> framed = spokenTokens(body + ")");
        return framed.isEmpty() || framed.get(framed.size() - 1).getStartIndex() < body.length();
    }

    /**
     * The semicolon that ends an EXPRESSION body — {@code $$ 1; $$}, {@code $$ 1 + 1; $$}, a body that is
     * nothing but {@code ;}, or {@code $$ 1; 2; $$} at its FIRST semicolon — or null when the body is not
     * one. What precedes the semicolon must read as an expression, so no other unreadable body is
     * refused here.
     */
    private static Token straySemicolonAfterExpression(final String body) {
        if (!endsWithSemicolon(body)) {
            return null;
        }
        Token semicolon = null;
        for (final Token token : spokenTokens(body)) {
            if (token.getType() == FrostlakeLexer.SEMI) {
                semicolon = token;
                break;
            }
        }
        final String before = body.substring(0, semicolon.getStartIndex()).trim();
        if (before.isEmpty()) {
            return semicolon;
        }
        try {
            AntlrExpressionParser.parseTree(before);
            return semicolon;
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /**
     * Where a body refusal points, in live's frame (see {@link #misframedQueryBody}): the token's line,
     * and its offset in that line — one further on the body's first line, where the frame's opening
     * character stands before it. Live numbers {@code $$1;$$}'s semicolon 2, {@code $$  1;$$}'s 4,
     * {@code '1;'}'s 2 and {@code $$1\n;$$}'s line 2, position 0.
     */
    private static String describeInBody(final Token token) {
        final int frame = token.getLine() == 1 ? 1 : 0;
        return "line " + token.getLine() + " at position " + (token.getCharPositionInLine() + frame)
            + " unexpected '" + token.getText() + "'";
    }

    /**
     * The end of the body in live's frame: its last line, one past that line's length — the frame's
     * closing character — and one further on the first line, where the opening character stands too.
     * {@code $$ 1 -- c $$} ends at position 10, {@code $$1\n-- c$$} at line 2, position 5.
     */
    private static String describeEndOfBody(final String body) {
        final int line = 1 + countNewlines(body);
        final int lastLineLength = body.length() - (body.lastIndexOf('\n') + 1);
        return "line " + line + " at position " + (lastLineLength + 1 + (line == 1 ? 1 : 0))
            + " unexpected '<EOF>'";
    }

    /** The body's spoken tokens — comments and whitespace dropped, end of input excluded. */
    private static List<Token> spokenTokens(final String body) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                spoken.add(token);
            }
        }
        return spoken;
    }

    private static String describe(final Token token) {
        final String text = token.getType() == Token.EOF ? "<EOF>" : token.getText();
        return "line " + token.getLine() + " at position " + token.getCharPositionInLine()
            + " unexpected '" + text + "'";
    }

    // ── the non-SQL half: hand the body to the language's own compiler ────────────

    /** Compile {@code function}'s body, throwing whatever its compiler said if it will not build. */
    static void compileFunctionBody(final Function function) {
        if (function == null || !judgeable(function.getUdfLanguage(), function.getBody(),
                function.getImports().isEmpty())) {
            return;
        }
        if (function.getUdfLanguage() == UdfLanguage.JAVA) {
            final List<DataType> argumentTypes = new ArrayList<>();
            if (function.getParameters() != null) {
                for (final Parameter parameter : function.getParameters()) {
                    argumentTypes.add(parameter.getDataType());
                }
            }
            compileJava(function.getBody(), function.getHandler(), argumentTypes.size(), function.getName(),
                argumentTypes, function.isTableFunction() ? null : function.getReturnType(), false);
            return;
        }
        final UdfLanguageRuntime runtime = UdfRuntimes.installed(function.getUdfLanguage());
        if (runtime != null) {
            runtime.compileFunction(function);
        }
    }

    /** Compile {@code procedure}'s body — see the language table for which languages that means. */
    static void compileProcedureBody(final Procedure procedure) {
        if (procedure == null || procedure.getUdfLanguage() == UdfLanguage.PYTHON
                || !judgeable(procedure.getUdfLanguage(), procedure.getBody(),
                    procedure.getImports().isEmpty())) {
            return;
        }
        if (procedure.getUdfLanguage() == UdfLanguage.JAVA) {
            // A Java procedure's handler takes a leading Session the declared parameter list does not
            // mention, so its arity is one more than the signature. Live counts it the same way — a
            // no-argument procedure whose handler cannot be found is refused "with 1 arguments".
            final List<DataType> argumentTypes = new ArrayList<>();
            for (final Parameter parameter : procedure.getParameters()) {
                argumentTypes.add(parameter.getDataType());
            }
            compileJava(procedure.getBody(), procedure.getHandler(), argumentTypes.size() + 1, procedure.getName(),
                argumentTypes, procedure.returnsTable() ? null : procedure.getReturnType(), true);
            return;
        }
        final UdfLanguageRuntime runtime = UdfRuntimes.installed(procedure.getUdfLanguage());
        if (runtime != null) {
            runtime.compileProcedure(procedure);
        }
    }

    /** Whether a body of this language is the engine's to judge at all. */
    private static boolean judgeable(final UdfLanguage language, final String body,
                                     final boolean inlineBody) {
        return inlineBody
            && body != null
            && !body.trim().isEmpty()
            && (language == UdfLanguage.JAVA
                || language == UdfLanguage.PYTHON
                || language == UdfLanguage.SCALA);
    }

    /**
     * Compile an inline Java body, confirm the handler is in it — one public method of that name taking
     * {@code arity} arguments — and judge its Java types against the routine's (see
     * {@link JavaHandlerSignature}). An {@code arity} of -1 skips the argument-count check.
     *
     * @param argumentTypes the declared argument types to judge, or null to judge none
     * @param returnType    the declared result to judge, or null to judge none
     * @param procedure     whether the routine is a procedure, whose handler takes a Session first
     */
    private static void compileJava(final String body, final String handler, final int arity,
                                    final String routineName, final List<DataType> argumentTypes,
                                    final DataType returnType, final boolean procedure) {
        final int dot = handler == null ? -1 : handler.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        final String methodName = handler.substring(dot + 1);
        final Class<?> compiled = JAVA_COMPILER.compile(body, handler.substring(0, dot));
        if (arity < 0) {
            return;
        }
        Method found = null;
        int definitions = 0;
        for (final Method method : compiled.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                found = method;
                definitions++;
            }
        }
        if (found == null) {
            throw new RuntimeException("Failed to find a public method named \"" + methodName + "\" with "
                + arity + " arguments in function " + routineName + " with handler " + handler);
        }
        if (definitions > 1) {
            throw new RuntimeException("Cannot determine which implementation of handler \"" + methodName
                + "\" to invoke since there are multiple definitions with " + arity + " arguments in function "
                + routineName + " with handler " + handler);
        }
        JavaHandlerSignature.check(found, argumentTypes, returnType, routineName, handler, procedure);
    }
}
