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
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A definition may not carry a bind — neither the unnamed {@code ?} nor a named {@code :var}, and a
 * positional {@code :1} is a named one, as is a LIMIT, OFFSET or FETCH count written {@code :n}. Written into
 * a view's query or a UDF's body it is refused when
 * the object is CREATED — "Bind variables not allowed in view and UDF definitions." — where the same
 * bind in an ordinary statement is the unset-bind refusal instead, and where a value IS in scope (a
 * scripting block's variable) the definition is still refused rather than the value being baked in. A
 * stored PROCEDURE's body is not held to this, nor is a CTAS, nor is a SQL UDF body that is a SCRIPTING
 * BLOCK — there {@code :name} READS a parameter or a declared variable rather than binding a value, and
 * the account creates it (live-verified).
 *
 * <p>The refusal points at the bind: in a view, at its place in the whole statement; in a routine body,
 * at its place within the BODY, counted from one — the frame every body-compilation refusal uses.
 *
 * <p>A named bind is told from the semi-structured path that shares its colon ({@code v:a}) by the PARSE
 * TREE, never by the text around it.
 */
public final class BindsInDefinition {

    private static final String REFUSAL = "Bind variables not allowed in view and UDF definitions.";

    private BindsInDefinition() {
    }

    /**
     * Refuse a bind anywhere in a definition's parse tree.
     *
     * @param definition the view's query, or any part of a statement that defines an object
     */
    public static void reject(final ParseTree definition) {
        if (definition == null) {
            return;
        }
        if (definition instanceof FrostlakeParser.BindVarExprContext) {
            final Token colon = ((FrostlakeParser.BindVarExprContext) definition).getStart();
            throw new RuntimeException(SqlCompilationError.at(colon.getLine(),
                colon.getCharPositionInLine(), REFUSAL));
        }
        if (definition instanceof TerminalNode) {
            final Token token = ((TerminalNode) definition).getSymbol();
            if (token.getType() == FrostlakeLexer.QUESTION || isRowCountBind((TerminalNode) definition)) {
                throw new RuntimeException(SqlCompilationError.at(token.getLine(),
                    token.getCharPositionInLine(), REFUSAL));
            }
            return;
        }
        for (int i = 0; i < definition.getChildCount(); i++) {
            reject(definition.getChild(i));
        }
    }

    /**
     * Whether a token is the colon of a LIMIT, OFFSET or FETCH count written as a bind ({@code LIMIT :n}), which
     * those clauses spell with their own colon rather than as a bind expression.
     */
    private static boolean isRowCountBind(final TerminalNode token) {
        return token.getSymbol().getType() == FrostlakeLexer.COLON
            && (token.getParent() instanceof FrostlakeParser.LimitClauseContext
                || token.getParent() instanceof FrostlakeParser.FetchClauseContext);
    }

    /**
     * Refuse a bind anywhere in a routine's body text. The unnamed one is found by LEXING, so a question
     * mark inside a string literal or a comment is left alone; the named one is found in the body's own
     * PARSE TREE, which is what separates {@code :var} from the {@code v:a} path spelled with the same
     * colon. A body that does not parse carries no named bind this check can name, and its own
     * compilation refusal follows anyway.
     *
     * @param queryExecutor the executor whose parser reads the body; null skips the named-bind pass
     * @param body          the body as written, already stripped of its delimiters
     */
    public static void rejectInBody(final QueryExecutor queryExecutor, final String body) {
        rejectUnnamedInBody(body);
        if (queryExecutor == null || body == null || body.indexOf(':') < 0) {
            return;
        }
        // The body is parsed UNTRIMMED so a bind's coordinates land in the body's own frame.
        final ParseTree parsed = bodyTree(queryExecutor, body);
        if (parsed != null) {
            rejectNamedInBody(parsed);
        }
    }

    /**
     * The body as a parse tree when the body is a QUERY or an EXPRESSION, and null for a Snowflake
     * Scripting BLOCK — a block body is the one shape where {@code :name} is no bind at all but the way
     * a parameter and a declared variable are READ, so the account creates
     * {@code AS $$ BEGIN RETURN :n; END $$} over a parameter {@code n} and answers with its value.
     */
    private static ParseTree bodyTree(final QueryExecutor queryExecutor, final String body) {
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(body);
        if (query != null) {
            return query;
        }
        if (queryExecutor.proceduralBlockOf(body) != null) {
            return null;
        }
        try {
            return AntlrExpressionParser.parseTree(body);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /** Refuse a named bind anywhere in a body's parse tree, positioned within the body. */
    private static void rejectNamedInBody(final ParseTree tree) {
        if (tree instanceof FrostlakeParser.BindVarExprContext) {
            final Token colon = ((FrostlakeParser.BindVarExprContext) tree).getStart();
            throw new RuntimeException(inBodyFrame(colon.getLine(), colon.getCharPositionInLine() + 1));
        }
        if (tree instanceof TerminalNode && isRowCountBind((TerminalNode) tree)) {
            final Token colon = ((TerminalNode) tree).getSymbol();
            throw new RuntimeException(inBodyFrame(colon.getLine(), colon.getCharPositionInLine() + 1));
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            rejectNamedInBody(tree.getChild(i));
        }
    }

    private static void rejectUnnamedInBody(final String body) {
        if (body == null || body.indexOf('?') < 0) {
            return;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        for (final Token token : tokens) {
            if (token.getType() == FrostlakeLexer.QUESTION) {
                throw new RuntimeException(inBodyFrame(token.getLine(), token.getCharPositionInLine() + 1));
            }
        }
    }

    /**
     * The refusal in the BODY's own frame — one-based within the body, which is where a body's own
     * refusals are counted from. The place is written into the message rather than handed to
     * {@link SqlCompilationError#at}, because a statement inside a scripting block rebases what that
     * one is given onto the statement's place in the block, and a body position is already relative to
     * the body: live reports position 1 for a body of {@code :s}, inside a block and at the top level
     * alike.
     */
    private static String inBodyFrame(final int line, final int column) {
        return SqlCompilationError.inline("error line " + line + " at position " + column + "\n" + REFUSAL);
    }
}
