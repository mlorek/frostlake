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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.functions.window.WindowFunctionNames;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A stage written bare as an expression. The account reads {@code GET_PRESIGNED_URL(@st, 'f.csv')} as
 * {@code GET_PRESIGNED_URL('@st', 'f.csv')}, but only where a stage is taken: the FIRST argument of GET_PRESIGNED_URL,
 * BUILD_SCOPED_FILE_URL, BUILD_STAGE_FILE_URL, GET_ABSOLUTE_PATH, GET_RELATIVE_PATH and GET_STAGE_LOCATION, and a
 * positional argument of CALL, which passes the stage on as the text written — a path, a user or table stage and a
 * stage that does not exist included. Everywhere else it is refused, in two phases, in live's words:
 *
 * <pre>
 *   while the text parses — the whole request is refused, none of its statements runs:
 *   SELECT (@st), SELECT 1 IN (@st)         syntax error line 1 at position 8 unexpected '@st'.
 *   SELECT BUILD_STAGE_FILE_URL(@st/d, 'x') syntax error line 1 at position 28 unexpected '@st/d'.
 *                                           syntax error line 1 at position 27 unexpected '('.
 *   SELECT BUILD_STAGE_FILE_URL(@ st, 'x')  syntax error line 1 at position 30 unexpected 'st'.
 *   CALL p(@st || 'x')                      syntax error line 1 at position 11 unexpected '||'.
 *
 *   while its statement compiles — the statements before it in the request have run:
 *   SELECT UPPER(@st)                       SQL compilation error: error line 1 at position 7
 *                                           invalid argument for function [UPPER] unexpected argument [@st] at position 0,
 *   SELECT BUILD_STAGE_FILE_URL(@st, @st)   … [BUILD_STAGE_FILE_URL] unexpected argument [@st] at position 1,
 *   SELECT LOWER(UPPER(@st))                … at position 13 — the call the stage is an argument of
 *   SELECT * FROM TABLE(FLATTEN(@db.s.st/d))  … at position 20 … [FLATTEN] unexpected argument [ST] …
 *   SELECT LAG(@st) OVER (ORDER BY 1)       … error line 0 at position -1 …, for a function that is a window function only
 * </pre>
 *
 * <p>The argument sentence names the function by its own name, upper-cased unless quoted, and the stage as written —
 * a table function's operand by the stage's name alone — and it is raised before any name the statement holds is
 * resolved: {@code SELECT UPPER(@st) FROM nosuch} is refused for the argument, not for the table. A stage is one word
 * on the account, so a blank inside it — between its parts, or inside a quoted part — ends it, and what follows is
 * refused as the next token.
 *
 * <p>A routine's body is judged in the frame it compiles in, its positions its own: a SQL UDF's expression body, a
 * table function's query and a policy's body inside a frame of parentheses — one character on either side, so a place
 * on the body's first line moves by one — their refusals opening with "Compilation of SQL UDF failed: "; a procedure's
 * block and a scalar UDF's query or block as a statement of their own.
 */
public final class StageArgumentSyntax {

    /** The functions whose first argument may be a bare stage. */
    public static final Set<String> STAGE_FUNCTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "GET_PRESIGNED_URL", "BUILD_SCOPED_FILE_URL", "BUILD_STAGE_FILE_URL", "GET_ABSOLUTE_PATH",
        "GET_RELATIVE_PATH", "GET_STAGE_LOCATION")));

    /** What a refusal from a routine's body compiled inside a frame of parentheses opens with. */
    public static final String UDF_COMPILATION_FAILED = "Compilation of SQL UDF failed: ";

    /** The window functions that are aggregates as well: their argument refusal keeps the call's own place. */
    private static final Set<String> WINDOW_AGGREGATES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "AVG", "COUNT", "MAX", "MIN", "SUM")));

    private StageArgumentSyntax() {
    }

    /**
     * Refuse, as the text parses, the first stage in the order written that no call can take: outside every call's
     * argument list, with a path after it, or with a blank inside it.
     *
     * @param script the parsed script
     * @param sql    the script's source text
     */
    public static void requireSyntax(final ParseTree script, final String sql) {
        if (script == null || sql == null || sql.indexOf('@') < 0) {
            return;
        }
        for (final FrostlakeParser.StageReferenceExprContext stage : stages(script)) {
            final RuntimeException refused = syntaxRefusal(stage, false);
            if (refused != null) {
                throw refused;
            }
        }
    }

    /**
     * Refuse, as one statement compiles, the first stage in the order written that is an argument of a call that
     * does not take it. Its place counts from the origin {@link LeadingCommentOffset} holds.
     *
     * @param statement the statement, or any part of a parsed text
     */
    public static void requireArguments(final ParseTree statement) {
        if (statement == null) {
            return;
        }
        for (final FrostlakeParser.StageReferenceExprContext stage : stages(statement)) {
            final RuntimeException refused = argumentRefusal(stage, false);
            if (refused != null) {
                throw refused;
            }
        }
    }

    /**
     * Both phases over a text compiled whole on its own — a procedure's block, a scalar UDF's query or block, the
     * text of one statement EXECUTE IMMEDIATE runs — its places counted from the text's own first token.
     *
     * @param script the parsed text
     * @param sql    the text
     */
    public static void requirePlacement(final ParseTree script, final String sql) {
        if (script == null || sql == null || sql.indexOf('@') < 0) {
            return;
        }
        final SourcePosition displaced = LeadingCommentOffset.begin(LeadingCommentOffset.of(sql));
        try {
            requireSyntax(script, sql);
            requireArguments(script);
        } finally {
            LeadingCommentOffset.end(displaced);
        }
    }

    /**
     * The first phase alone over the text EXECUTE IMMEDIATE runs, its places counted from the text's own first token:
     * the text is refused for where its stages stand before its statements are counted, and a statement of several is
     * judged for its arguments when it runs, as a statement of a request is ({@link #requireArguments}).
     *
     * @param script the parsed text
     * @param sql    the text
     */
    public static void requireTextSyntax(final ParseTree script, final String sql) {
        if (script == null || sql == null || sql.indexOf('@') < 0) {
            return;
        }
        final SourcePosition displaced = LeadingCommentOffset.begin(LeadingCommentOffset.of(sql));
        try {
            requireSyntax(script, sql);
        } finally {
            LeadingCommentOffset.end(displaced);
        }
    }

    /**
     * Both phases over a routine body compiled inside a frame of parentheses — a SQL UDF's expression, a table
     * function's query — its places the body's own, moved by one on its first line, each refusal opening with
     * {@link #UDF_COMPILATION_FAILED}.
     *
     * @param body the parsed body
     */
    public static void requireFramedPlacement(final ParseTree body) {
        if (body == null) {
            return;
        }
        final SourcePosition displaced = LeadingCommentOffset.begin(null);
        try {
            final List<FrostlakeParser.StageReferenceExprContext> stages = stages(body);
            for (final FrostlakeParser.StageReferenceExprContext stage : stages) {
                final RuntimeException refused = syntaxRefusal(stage, true);
                if (refused != null) {
                    throw refused;
                }
            }
            for (final FrostlakeParser.StageReferenceExprContext stage : stages) {
                final RuntimeException refused = argumentRefusal(stage, true);
                if (refused != null) {
                    throw refused;
                }
            }
        } finally {
            LeadingCommentOffset.end(displaced);
        }
    }

    /**
     * Refuse one bare stage, as a text parsed on its own holds it, unless it is taken where it stands. A stage that is
     * the whole text passes — a CALL argument is re-read on its own — so a refusal here is what no statement,
     * body or argument judged before it would take.
     *
     * @param stage the stage as parsed
     */
    public static void requireStandalone(final FrostlakeParser.StageReferenceExprContext stage) {
        if (isWholeText(stage)) {
            return;
        }
        final RuntimeException syntax = syntaxRefusal(stage, false);
        if (syntax != null) {
            throw syntax;
        }
        final RuntimeException argument = argumentRefusal(stage, false);
        if (argument != null) {
            throw argument;
        }
    }

    /**
     * The stage's text as the statement spells it.
     *
     * @param stage the stage as parsed
     * @return its source text
     */
    public static String writtenText(final FrostlakeParser.StageReferenceExprContext stage) {
        final Token start = stage.getStart();
        final Token stop = stage.getStop();
        if (start == null || stop == null || start.getInputStream() == null) {
            return stage.getText();
        }
        return start.getInputStream().getText(Interval.of(start.getStartIndex(), stop.getStopIndex()));
    }

    private static List<FrostlakeParser.StageReferenceExprContext> stages(final ParseTree root) {
        final List<FrostlakeParser.StageReferenceExprContext> stages = new ArrayList<>();
        collect(root, stages);
        return stages;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.StageReferenceExprContext> into) {
        if (node instanceof FrostlakeParser.StageReferenceExprContext) {
            into.add((FrostlakeParser.StageReferenceExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }

    /** The syntax error a stage earns where it stands, or null when the parse takes it there. */
    private static RuntimeException syntaxRefusal(final FrostlakeParser.StageReferenceExprContext stage,
                                                  final boolean framed) {
        final List<Token> tokens = new ArrayList<>();
        terminals(stage, tokens);
        final ParserRuleContext call = owningCall(stage);
        final List<String> blankRefusal = blankInside(tokens, call, framed);
        if (!blankRefusal.isEmpty()) {
            return refusal(blankRefusal, framed);
        }
        if (stage.getParent() instanceof FrostlakeParser.CallArgumentContext) {
            return null;
        }
        final Token afterStage = operatorAfterCallArgument(stage);
        if (afterStage != null) {
            return refusal(Collections.singletonList(sentence(afterStage, afterStage.getText(), framed)), framed);
        }
        if (stage.stageRef().stagePath() != null && !isTableOperand(call)) {
            final List<String> lines = new ArrayList<>();
            lines.add(sentence(stage.getStart(), writtenText(stage), framed));
            if (call != null && framed) {
                // Inside a body's frame the account reports the stage twice and then the frame's own closing
                // parenthesis, where it resumes.
                lines.add(sentence(stage.getStart(), writtenText(stage), true));
                lines.add(endOfFrame(stage));
            } else if (call != null) {
                final Token open = openingParenthesis(call);
                if (open != null) {
                    lines.add(sentence(open, open.getText(), false));
                }
            }
            return refusal(lines, framed);
        }
        if (call == null) {
            return refusal(Collections.singletonList(sentence(stage.getStart(), writtenText(stage), framed)), framed);
        }
        return null;
    }

    /** The argument sentence a stage earns from the call it is an argument of, or null when that call takes it. */
    private static RuntimeException argumentRefusal(final FrostlakeParser.StageReferenceExprContext stage,
                                                    final boolean framed) {
        final ParserRuleContext call = owningCall(stage);
        if (call == null) {
            return null;
        }
        final boolean tableOperand = isTableOperand(call);
        final int index = argumentIndex(stage);
        if (!tableOperand && index == 0 && takesStage(call)) {
            return null;
        }
        final String detail = "invalid argument for function [" + callName(call) + "] unexpected argument ["
            + (tableOperand ? stageName(stage) : writtenText(stage)) + "] at position " + index + ",";
        if (isWindowOnly(call)) {
            final String unplaced = placed(0, -1, detail);
            return new RuntimeException(framed ? UDF_COMPILATION_FAILED + unplaced : unplaced);
        }
        final ParserRuleContext policy = policyBody(stage);
        if (policy != null) {
            // A policy's body compiles as a UDF body of its own, in its frame, however the statement placed it.
            final int line = call.getStart().getLine() - policy.getStart().getLine() + 1;
            final int column = line == 1
                ? call.getStart().getCharPositionInLine() - policy.getStart().getCharPositionInLine()
                : call.getStart().getCharPositionInLine();
            return new RuntimeException(UDF_COMPILATION_FAILED + placed(line, line == 1 ? column + 1 : column,
                detail));
        }
        if (framed) {
            final int line = call.getStart().getLine();
            final int column = call.getStart().getCharPositionInLine();
            return new RuntimeException(UDF_COMPILATION_FAILED + placed(line, line == 1 ? column + 1 : column,
                detail));
        }
        return new RuntimeException(SqlCompilationError.at(call.getStart().getLine(),
            call.getStart().getCharPositionInLine(), detail));
    }

    /** A positioned compilation error at exactly this place, whatever origin the statement rebases on. */
    private static String placed(final int line, final int position, final String detail) {
        final SourcePosition displaced = LeadingCommentOffset.begin(null);
        try {
            return SqlCompilationError.at(line, position, detail);
        } finally {
            LeadingCommentOffset.end(displaced);
        }
    }

    private static void terminals(final ParseTree node, final List<Token> into) {
        if (node instanceof TerminalNode) {
            into.add(((TerminalNode) node).getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            terminals(node.getChild(i), into);
        }
    }

    /**
     * The lines a blank inside the stage earns, none when it has no blank. A blank between two of its parts ends the
     * stage there: the word after it is refused, and so is the call's closing parenthesis, where the account resumes.
     * A blank inside a quoted part ends it too and the word after the blank is refused; the part's closing quote then
     * opens a quoted name, read on by {@link #quotedPartLines}.
     */
    private static List<String> blankInside(final List<Token> tokens, final ParserRuleContext call,
                                            final boolean framed) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (i > 0 && tokens.get(i - 1).getStopIndex() + 1 < token.getStartIndex()) {
                lines.add(sentence(token, token.getText(), framed));
                final Token close = call == null ? null : closingParenthesis(call);
                if (close != null) {
                    lines.add(sentence(close, close.getText(), framed));
                }
                return lines;
            }
            final String text = token.getText();
            int blank = -1;
            for (int c = 0; c < text.length(); c++) {
                if (Character.isWhitespace(text.charAt(c))) {
                    blank = c;
                    break;
                }
            }
            if (blank < 0) {
                continue;
            }
            int word = blank;
            while (word < text.length() && Character.isWhitespace(text.charAt(word))) {
                word++;
            }
            int end = word;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '"') {
                end++;
            }
            final int[] at = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine() + word);
            final String wordLine = "syntax error line " + at[0] + " at position " + framedColumn(at, framed)
                + " unexpected '" + text.substring(word, end) + "'.";
            if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
                return quotedPartLines(token, wordLine, framed);
            }
            lines.add(wordLine);
            return lines;
        }
        return lines;
    }

    /**
     * The lines a quoted part with a blank inside earns: the account's lexer reads its closing quote as opening a
     * quoted name, and the text from there on is lexed here the same way. When no quote closes that name the end of
     * the text is refused first, ahead of the word; when one does, the name is refused after the word, and a string
     * left open after it refuses the end of the text last.
     */
    private static List<String> quotedPartLines(final Token part, final String wordLine, final boolean framed) {
        final CharStream source = part.getInputStream();
        final List<String> lines = new ArrayList<>();
        if (source == null) {
            lines.add(wordLine);
            return lines;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(
            source.getText(Interval.of(0, source.size() - 1))));
        final int[] lexerErrors = new int[1];
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                lexerErrors[0]++;
            }
        });
        // A quoted part lies on one line, so its closing quote is its last character on the part's own line.
        final int quote = part.getStopIndex();
        lexer.getInputStream().seek(quote);
        lexer.setLine(part.getLine());
        lexer.setCharPositionInLine(part.getCharPositionInLine() + part.getText().length() - 1);
        final Token opened = lexer.nextToken();
        Token next = opened;
        while (next.getType() != Token.EOF) {
            next = lexer.nextToken();
        }
        final int[] end = LeadingCommentOffset.rebase(next.getLine(), next.getCharPositionInLine());
        final String endOfText = "parse error line " + end[0] + " at position " + framedColumn(end, framed)
            + " near '<EOF>'.";
        if (opened.getType() != FrostlakeLexer.QUOTED_IDENTIFIER || opened.getStartIndex() != quote) {
            lines.add(endOfText);
            lines.add(wordLine);
            return lines;
        }
        lines.add(wordLine);
        lines.add(sentence(opened, opened.getText(), framed));
        if (lexerErrors[0] > 0) {
            lines.add(endOfText);
        }
        return lines;
    }

    /**
     * The call the stage is written directly inside as one of its arguments, or null when it stands anywhere
     * else — in parentheses of its own, an IN list, an operator's operand, a CALL's argument list.
     */
    private static ParserRuleContext owningCall(final FrostlakeParser.StageReferenceExprContext stage) {
        ParserRuleContext up = stage.getParent();
        if (up instanceof FrostlakeParser.ValueExprContext) {
            up = up.getParent();
        }
        if (up instanceof FrostlakeParser.FunctionArgContext) {
            final ParserRuleContext list = up.getParent();
            return list == null ? null : list.getParent();
        }
        if (up instanceof FrostlakeParser.BooleanExprListContext
                && up.getParent() instanceof FrostlakeParser.SystemFuncExprContext) {
            return up.getParent();
        }
        if (up instanceof FrostlakeParser.ExpressionListContext
                && up.getParent() instanceof FrostlakeParser.SystemStreamHasDataExprContext) {
            return up.getParent();
        }
        if (up instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                || up instanceof FrostlakeParser.TableFunctionExprContext
                || up instanceof FrostlakeParser.AggregateFunctionContext
                || up instanceof FrostlakeParser.CollateFuncExprContext) {
            return up;
        }
        return null;
    }

    /**
     * The token after a stage that opens a CALL's positional argument without being all of it, {@code ||} in
     * {@code CALL p(@st || 'x')}: the account takes the stage as the argument and refuses what follows it. Null
     * anywhere else.
     */
    private static Token operatorAfterCallArgument(final FrostlakeParser.StageReferenceExprContext stage) {
        ParserRuleContext operand = stage;
        while (operand.getParent() instanceof FrostlakeParser.ExpressionContext
                && operand.getParent().getStart() == stage.getStart()) {
            operand = operand.getParent();
        }
        if (operand == stage || !(operand.getParent() instanceof FrostlakeParser.CallArgumentContext)) {
            return null;
        }
        final List<Token> tokens = new ArrayList<>();
        terminals(operand, tokens);
        for (final Token token : tokens) {
            if (token.getStartIndex() > stage.getStop().getStopIndex()) {
                return token;
            }
        }
        return null;
    }

    /** Whether the call is the one a {@code TABLE(…)} source or a bare table-function source calls. */
    private static boolean isTableOperand(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.TableFunctionExprContext) {
            return true;
        }
        return call != null && call.getParent() instanceof FrostlakeParser.TableSourceContext
            && ((FrostlakeParser.TableSourceContext) call.getParent()).TABLE() != null;
    }

    /** Whether the call is one of the stage functions, named without a schema. */
    private static boolean takesStage(final ParserRuleContext call) {
        final FrostlakeParser.FunctionNameContext name = functionName(call);
        return name != null && name.identifier().size() == 1 && STAGE_FUNCTIONS.contains(callName(call));
    }

    /** Whether the call is a window function that is no aggregate, called over a window. */
    private static boolean isWindowOnly(final ParserRuleContext call) {
        final boolean overWindow = call instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) call).overClause() != null
            || call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                && ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).overClause() != null;
        final String name = callName(call);
        return overWindow && WindowFunctionNames.handles(name) && !WINDOW_AGGREGATES.contains(name);
    }

    /**
     * The body of the masking, row access, projection or aggregation policy the stage is written in — the expression
     * after the policy's arrow — or null when it is in none.
     */
    private static ParserRuleContext policyBody(final ParserRuleContext stage) {
        ParserRuleContext node = stage;
        while (node.getParent() != null) {
            final ParserRuleContext parent = node.getParent();
            if (node instanceof FrostlakeParser.BooleanExprContext
                    && (parent instanceof FrostlakeParser.CreateStatementContext
                        || parent instanceof FrostlakeParser.PolicyActionContext)) {
                final int at = parent.children.indexOf(node);
                final ParseTree before = at > 0 ? parent.getChild(at - 1) : null;
                if (before instanceof TerminalNode
                        && ((TerminalNode) before).getSymbol().getType() == FrostlakeLexer.THIN_ARROW) {
                    return node;
                }
            }
            node = parent;
        }
        return null;
    }

    /** Whether the stage is all the text that was parsed. */
    private static boolean isWholeText(final FrostlakeParser.StageReferenceExprContext stage) {
        final ParserRuleContext up = stage.getParent();
        return up == null || up instanceof FrostlakeParser.ValueExprContext && up.getParent() == null;
    }

    /** The zero-based place of the stage among its call's arguments. */
    private static int argumentIndex(final FrostlakeParser.StageReferenceExprContext stage) {
        ParseTree argument = stage;
        ParserRuleContext up = stage.getParent();
        if (up instanceof FrostlakeParser.ValueExprContext) {
            argument = up;
            up = up.getParent();
        }
        if (up instanceof FrostlakeParser.FunctionArgContext) {
            argument = up;
            up = up.getParent();
        }
        int index = 0;
        for (int i = 0; i < up.getChildCount(); i++) {
            final ParseTree child = up.getChild(i);
            if (child == argument) {
                return index;
            }
            if (child instanceof ParserRuleContext && !(child instanceof FrostlakeParser.FunctionNameContext)) {
                index++;
            }
        }
        return index;
    }

    private static FrostlakeParser.FunctionNameContext functionName(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.TableFunctionExprContext) {
            return ((FrostlakeParser.TableFunctionExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.AggregateFunctionContext) {
            return ((FrostlakeParser.AggregateFunctionContext) call).functionName();
        }
        return null;
    }

    /**
     * The called function's own name, as the argument sentence names it: the last part of a qualified name,
     * upper-cased unless it was quoted.
     */
    private static String callName(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.SystemFuncExprContext) {
            return ((FrostlakeParser.SystemFuncExprContext) call).SYSTEM_FUNC().getText().toUpperCase(Locale.ROOT);
        }
        if (call instanceof FrostlakeParser.SystemStreamHasDataExprContext) {
            return ((FrostlakeParser.SystemStreamHasDataExprContext) call).SYSTEM_STREAM_HAS_DATA().getText()
                .toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.FunctionNameContext name = functionName(call);
        if (name == null) {
            return call.getStart().getText().toUpperCase(Locale.ROOT);
        }
        final List<FrostlakeParser.IdentifierContext> parts = name.identifier();
        return parts.isEmpty() ? name.getText().toUpperCase(Locale.ROOT)
            : SqlIdentifiers.canonical(parts.get(parts.size() - 1));
    }

    /** A table function's stage operand as the argument sentence names it: the stage's own name, canonical. */
    private static String stageName(final FrostlakeParser.StageReferenceExprContext stage) {
        final List<FrostlakeParser.IdentifierContext> parts = stage.stageRef().identifier();
        return parts.isEmpty() ? writtenText(stage) : SqlIdentifiers.canonical(parts.get(parts.size() - 1));
    }

    /** The '(' that opens a call's argument list. */
    private static Token openingParenthesis(final ParserRuleContext call) {
        for (int i = 0; i < call.getChildCount(); i++) {
            final ParseTree child = call.getChild(i);
            if (child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.LPAREN) {
                return ((TerminalNode) child).getSymbol();
            }
        }
        return null;
    }

    /** The ')' that closes a call's argument list. */
    private static Token closingParenthesis(final ParserRuleContext call) {
        for (int i = call.getChildCount() - 1; i >= 0; i--) {
            final ParseTree child = call.getChild(i);
            if (child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.RPAREN) {
                return ((TerminalNode) child).getSymbol();
            }
        }
        return null;
    }

    /** The line the frame's closing parenthesis earns: it stands where the body's text ends. */
    private static String endOfFrame(final FrostlakeParser.StageReferenceExprContext stage) {
        final CharStream source = stage.getStart().getInputStream();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(
            source.getText(Interval.of(0, source.size() - 1))));
        lexer.removeErrorListeners();
        Token next = lexer.nextToken();
        while (next.getType() != Token.EOF) {
            next = lexer.nextToken();
        }
        return sentence(next, ")", true);
    }

    private static String sentence(final Token token, final String text, final boolean framed) {
        final int[] at = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + at[0] + " at position " + framedColumn(at, framed) + " unexpected '" + text
            + "'.";
    }

    /** A place's column, moved by the frame's opening parenthesis when it lies on the body's first line. */
    private static int framedColumn(final int[] at, final boolean framed) {
        return framed && at[0] == 1 ? at[1] + 1 : at[1];
    }

    private static RuntimeException refusal(final List<String> lines, final boolean framed) {
        final String joined = String.join("\n", lines);
        if (framed) {
            return new RuntimeException(UDF_COMPILATION_FAILED + SqlCompilationError.of(joined));
        }
        return new SqlSyntaxException(SqlCompilationError.of(joined), lines, null);
    }
}
