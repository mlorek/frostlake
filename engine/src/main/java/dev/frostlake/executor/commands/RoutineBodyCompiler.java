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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

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
        final String trimmed = body.trim();
        final FrostlakeParser.BeginEndBlockContext block = queryExecutor.proceduralBlockOf(body);
        if (block != null) {
            // The whole block is compiled at CREATE, reachable branches or not, so a bad declared
            // width is refused HERE and the procedure is never created (live-verified). The body is
            // parsed UNTRIMMED so the refusal's coordinates land in the body's own frame — line 1 is
            // the first line after the opening quote, exactly where a real account points.
            ScriptingNameValidator.rejectDottedBindVariables(block);
            ScriptingNameValidator.rejectRedeclaration(block, parameters, true);
            ScriptTypeCompiler.validateDeclaredTypes(block);
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
     * a table function), and anything the engine cannot read is left alone. A body in any other language
     * and an absent body are left alone too.
     *
     * @param tableFunction whether the routine declares {@code RETURNS TABLE(…)}.
     * @param routineName   the routine's own unqualified name, so the validator can see a self-call.
     */
    static void compileFunctionBody(final QueryExecutor queryExecutor, final UdfLanguage language,
                                    final String body, final boolean tableFunction,
                                    final String routineName, final Set<String> parameters,
                                    final String scopeDatabase, final String scopeSchema) {
        if (queryExecutor == null || language != UdfLanguage.SQL || body == null) {
            return;
        }
        final Set<String> declared = parameters == null ? Collections.<String>emptySet() : parameters;
        final String trimmed = body.trim();
        final ScriptingUdfBodyValidator validator =
            new ScriptingUdfBodyValidator(queryExecutor, queryExecutor.getCatalog(), routineName);

        final FrostlakeParser.SqlScriptContext queryBody = queryExecutor.queryStatementOf(trimmed);
        if (queryBody != null) {
            rejectQueryBodyEnding(queryExecutor, body, trimmed, tableFunction);
            validator.rejectScriptingUdfCalls(queryBody);
            resolveQueryBodyNames(queryExecutor, trimmed, declared, scopeDatabase, scopeSchema);
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
            // Otherwise FAIL-OPEN, for the same reason as the procedure check: a body the engine's
            // grammar cannot read is unknown, not invalid. A real account DOES refuse such a body at
            // CREATE - "Compilation of SQL UDF failed: ... syntax error ... unexpected 'is'." - but
            // this grammar is a SUBSET of the account's, and refusing here rejected a vendor body the
            // account accepts, which stops a schema from migrating at all. The names below are judged
            // either way, because a body this CAN read is one it can be sure about.
            return;
        }
        // An expression body that ends inside a line comment leaves live's frame open, so the refusal
        // is at the end of the body.
        if (endsInsideLineComment(body)) {
            throw new RuntimeException("Compilation of SQL UDF failed: "
                + SqlCompilationError.of("syntax error " + describeEndOfBody(body) + "."));
        }
        validator.rejectScriptingUdfCalls(expression);
        SqlUdfBodyNames.rejectUnknownNames(expression, declared);
    }

    /**
     * Resolve a QUERY body's names at CREATE, as a real account does: a relation it cannot find and a
     * column nothing holds are refused there, not at the first call. The body is compiled for its SHAPE
     * alone, the way CREATE VIEW learns its columns, so a value-time fault in it stays the reader's.
     *
     * <p>A query body may also read the function's own PARAMETERS, which a body compiled on its own
     * cannot see - {@code AS 'SELECT a FROM t WHERE a = x'} is created on the account. Such a body is
     * left alone rather than refused, so a good function is never rejected for a name it does declare.
     */
    private static void resolveQueryBodyNames(final QueryExecutor queryExecutor, final String trimmed,
                                              final Set<String> parameters, final String scopeDatabase,
                                              final String scopeSchema) {
        try {
            queryExecutor.resolveViewShapeInScope(scopeDatabase, scopeSchema, trimmed, null);
        } catch (final RuntimeException unresolved) {
            final String message = String.valueOf(unresolved.getMessage());
            final Matcher named = INVALID_IDENTIFIER.matcher(message);
            if (named.find() && parameters.contains(named.group(1).toUpperCase())) {
                return;
            }
            throw new RuntimeException(inBodyFrame(message));
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
            compileJava(function.getBody(), function.getHandler(),
                function.getParameters() == null ? 0 : function.getParameters().size(),
                function.getName());
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
            compileJava(procedure.getBody(), procedure.getHandler(),
                procedure.getParameters().size() + 1, procedure.getName());
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
     * Compile an inline Java body and confirm the handler is in it. An {@code arity} of -1 skips the
     * argument-count check, which a procedure needs: its handler takes a leading session argument the
     * declared parameter list does not mention.
     */
    private static void compileJava(final String body, final String handler, final int arity,
                                    final String routineName) {
        final int dot = handler == null ? -1 : handler.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        final String methodName = handler.substring(dot + 1);
        final Class<?> compiled = JAVA_COMPILER.compile(body, handler.substring(0, dot));
        if (arity < 0) {
            return;
        }
        for (final Method method : compiled.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                return;
            }
        }
        throw new RuntimeException("Failed to find a public method named \"" + methodName + "\" with "
            + arity + " arguments in function " + routineName + " with handler " + handler);
    }
}
