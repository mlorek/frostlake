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

package dev.frostlake.parser;

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A LIMIT or OFFSET value written as a string is the empty string or nothing: {@code LIMIT ''} means no limit and
 * {@code OFFSET ''} no offset, and any other string is a syntax error at the literal. The account judges it with the
 * text it compiles, so the text decides both when the refusal is raised and where it is placed (all live-verified):
 *
 * <ul>
 *   <li>A statement is judged whole before any of it runs, wherever the clause stands in it — a subquery, a derived
 *       table, a CTE, a view's body, a block's statement — so the refusal is placed in the statement, past its
 *       leading comments, and outranks a name nothing resolves and every type fault of the query around it. A flow
 *       chain is judged whole before its first stage runs; the statements of a request are judged each as it
 *       runs, so the statements before the refused one still run.</li>
 *   <li>A text a statement compiles when it runs is judged then, and the refusal is that statement's failure,
 *       placed in the text as it is written: EXECUTE IMMEDIATE's text (a comment before the EXECUTE IMMEDIATE moves
 *       nothing, a comment or a line break inside the text counts), and a procedure's body, quoted or not, whose
 *       first line is line 1 — after the procedure's signature has been judged.</li>
 *   <li>A policy's body, at CREATE and at SET BODY, is compiled as a SQL UDF's body is: in the UDF's frame, one
 *       position in on the body's first line, and refused in the UDF's words.</li>
 *   <li>A task's or an alert's statements are stored as written: creating or altering the task or the alert does
 *       not judge them, while a task's WHEN condition is judged with its statement.</li>
 * </ul>
 *
 * <p>Only the first such value in the text is reported, and a text that does not parse reports its parse fault
 * instead.
 *
 * <pre>
 *   SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x')     unexpected ''x'' at 49
 *   SELECT a FROM t WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM t LIMIT 'x')     at 68, not the '+'
 *   SELECT a FROM t LIMIT 'x' OFFSET 'y'                      ''x'' at 22 alone
 *   SELECT a FROM t LIMIT '' OFFSET 'y'                       ''y'' at 32
 *   CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS BEGIN RETURN (SELECT a FROM t LIMIT 'x'); END
 *                                                             at 36, counted from BEGIN
 *   CREATE MASKING POLICY m AS (v INT) RETURNS INT -&gt; (SELECT a FROM t LIMIT 'x')
 *                                                             Compilation of SQL UDF failed: … at 24
 *   CREATE TASK k SCHEDULE = '60 MINUTE' AS SELECT a FROM t LIMIT 'x'                created
 * </pre>
 */
public final class LimitValueSyntax {

    private static final String EMPTY_STRING = "''";

    /** How the account words the refusal of a body it compiles as a SQL UDF's. */
    private static final String UDF_BODY_FAILURE = "Compilation of SQL UDF failed: ";

    private LimitValueSyntax() {
    }

    /**
     * Refuse, before a statement runs, the first string LIMIT or OFFSET value in it, placed in the statement past its
     * leading comments.
     *
     * @param statement the statement's parse tree
     * @param sql       the text it was parsed from
     */
    public static void requireNumericValues(final ParseTree statement, final String sql) {
        final Token refused = firstRefusedValue(statement);
        if (refused != null) {
            final int[] shown = LeadingCommentOffset.rebase(refused.getLine(), refused.getCharPositionInLine());
            final List<String> lines = new ArrayList<>();
            lines.add(sentence(shown[0], shown[1], refused));
            throw new SqlSyntaxException(SqlCompilationError.of(lines.get(0)), lines, sql);
        }
    }

    /**
     * Refuse the first string LIMIT or OFFSET value of a text a statement compiles when it runs — EXECUTE IMMEDIATE's
     * text, a procedure's body — placed in that text as it is written, as the running statement's failure.
     *
     * @param text the text's parse tree
     */
    public static void requireNumericValuesAsWritten(final ParseTree text) {
        final Token refused = firstRefusedValue(text);
        if (refused != null) {
            throw new RuntimeException(SqlCompilationError.of(
                sentence(refused.getLine(), refused.getCharPositionInLine(), refused)));
        }
    }

    /**
     * {@link #requireNumericValuesAsWritten(ParseTree)} for statements given as text, parsed on their own; a text that
     * does not parse whole is left to the checks that say why.
     *
     * @param text the statements' text
     */
    public static void requireNumericValuesInText(final String text) {
        if (text == null) {
            return;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = parser.sqlScript();
        } catch (final ParseCancellationException doesNotParse) {
            return;
        }
        requireNumericValuesAsWritten(script);
    }

    /**
     * Refuse the first string LIMIT or OFFSET value of a body the account compiles as a SQL UDF's, a policy's: placed
     * in the UDF's frame, one position in on the body's first line and unmoved on the lines after it, and worded as the
     * UDF's refusal.
     *
     * @param body the body's parse tree, parsed from the body's own text
     */
    public static void requireNumericValuesInUdfBody(final ParseTree body) {
        final Token refused = firstRefusedValue(body);
        if (refused != null) {
            final int position = refused.getLine() == 1
                ? refused.getCharPositionInLine() + 1 : refused.getCharPositionInLine();
            throw new RuntimeException(UDF_BODY_FAILURE
                + SqlCompilationError.of(sentence(refused.getLine(), position, refused)));
        }
    }

    private static String sentence(final int line, final int position, final Token refused) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + refused.getText() + "'.";
    }

    /**
     * The first LIMIT or OFFSET value, in the order written, that is a string other than the empty one, outside every
     * body the text holds that is compiled or stored on its own; null when there is none.
     */
    private static Token firstRefusedValue(final ParseTree root) {
        if (root == null) {
            return null;
        }
        Token first = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.LimitClauseContext) {
                final Token refused = refusedValue((FrostlakeParser.LimitClauseContext) node);
                if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                    first = refused;
                }
                continue;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                if (!compiledOnItsOwn(node, i)) {
                    pending.push(node.getChild(i));
                }
            }
        }
        return first;
    }

    /**
     * Whether a node's child is a body the account stores or compiles on its own rather than with the text around
     * it: a task's or an alert's statements, a routine's body, and a policy's body — what follows the arrow of a
     * policy's CREATE or SET BODY. A routine's and a policy's body are judged when their statement runs, by
     * {@link #requireNumericValuesAsWritten(ParseTree)} and {@link #requireNumericValuesInUdfBody}.
     */
    private static boolean compiledOnItsOwn(final ParseTree parent, final int index) {
        final ParseTree child = parent.getChild(index);
        if (child instanceof FrostlakeParser.TaskBodyContext || child instanceof FrostlakeParser.AlertConditionContext
                || child instanceof FrostlakeParser.BodyDefinitionContext) {
            return true;
        }
        return (parent instanceof FrostlakeParser.CreateStatementContext
                || parent instanceof FrostlakeParser.PolicyActionContext)
            && index > 0 && parent.getChild(index - 1) instanceof TerminalNode
            && ((TerminalNode) parent.getChild(index - 1)).getSymbol().getType() == FrostlakeLexer.THIN_ARROW;
    }

    /** A clause's first value that is a string other than the empty one, or null. */
    private static Token refusedValue(final FrostlakeParser.LimitClauseContext clause) {
        for (int i = 0; i < clause.getChildCount(); i++) {
            if (clause.getChild(i) instanceof TerminalNode) {
                final Token token = ((TerminalNode) clause.getChild(i)).getSymbol();
                if (token.getType() == FrostlakeLexer.STRING_LITERAL && !EMPTY_STRING.equals(token.getText())) {
                    return token;
                }
            }
        }
        return null;
    }
}
