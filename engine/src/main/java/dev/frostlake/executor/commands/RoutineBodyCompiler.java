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

    /** Shared, like the UDF invoker's own: javac start-up is what costs, not the compile. */
    private static final JavaFunctionCompiler JAVA_COMPILER = new JavaFunctionCompiler();

    private RoutineBodyCompiler() {
    }

    /**
     * Reject a {@code LANGUAGE SQL} stored-procedure body that is not a Snowflake-Scripting block. A body in any
     * other language, an absent body (handler-based routines) and a blank body are left alone.
     */
    static void compileProcedureBody(final QueryExecutor queryExecutor, final UdfLanguage language,
                                     final String body) {
        if (queryExecutor == null || language != UdfLanguage.SQL || body == null || body.trim().isEmpty()) {
            return;
        }
        final String trimmed = body.trim();
        if (queryExecutor.isProceduralBlock(trimmed)) {
            return;
        }
        // Reject ONLY the positively-known-bad shape: a body that is a single plain SQL statement
        // ('SELECT a + b', 'UPDATE …'), which Snowflake rejects live. Anything else is left alone —
        // Frostlake's grammar is a SUBSET of Snowflake's, so a body it cannot fully read (a long
        // deployment block with a construct the engine does not model, a multi-variable DECLARE
        // section) means "unknown", NOT "invalid". Rejecting those at CREATE would break working
        // schemas that the account itself accepts.
        if (!queryExecutor.isSinglePlainStatement(trimmed)) {
            return;
        }
        // Snowflake reports end-of-input here (live-verified) — it reads the whole body looking for the block
        // that never arrives — so report the body's real EOF position.
        throw new RuntimeException("SQL compilation error:\nsyntax error " + describe(endOfInput(trimmed))
            + ". A stored procedure body in LANGUAGE SQL must be a Snowflake Scripting block"
            + " (BEGIN ... END, optionally preceded by DECLARE).");
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
                                    final String routineName) {
        if (queryExecutor == null || language != UdfLanguage.SQL || body == null) {
            return;
        }
        final String trimmed = body.trim();
        final ScriptingUdfBodyValidator validator =
            new ScriptingUdfBodyValidator(queryExecutor, queryExecutor.getCatalog(), routineName);

        final FrostlakeParser.SqlScriptContext queryBody = queryExecutor.queryStatementOf(trimmed);
        if (queryBody != null) {
            validator.rejectScriptingUdfCalls(queryBody);
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
            // FAIL-OPEN, for the same reason as the procedure check: a body the engine's grammar cannot
            // read is unknown, not invalid. Rejecting it would break bodies the account itself accepts.
            return;
        }
        validator.rejectScriptingUdfCalls(expression);
    }

    /** The EOF token of {@code body}, lexed with the engine's own lexer so line/column are the real ones. */
    private static Token endOfInput(final String body) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        return tokens.get(tokens.size() - 1);
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
