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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.FunctionRegistry;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A call written as a statement of a block, {@code BEGIN SYSTEM$WAIT(0); RETURN 1; END;}. The account reads the
 * statement and refuses it while the block compiles — {@code Unsupported feature 'Function SYSTEM$WAIT cannot be
 * executed as a statement in Snowscript'.}, the function named as written, {@code upper} for {@code upper('a')} —
 * where this grammar reads no such statement and refuses the name or its parenthesis. So a text whose call
 * statements are its only faults is refused in the account's words ({@link BlockCallStatementRefusal}): after a
 * RETURN, on an IF branch, in a loop, in an exception handler, in a nested block and in a block with a DECLARE
 * section alike. A text with another fault keeps its syntax error, which the account reports first too (all
 * live-verified).
 *
 * <p>A call statement is one word opening a block's statement — not a quoted name, not a path, not IDENTIFIER(), not
 * a word that opens a statement of its own, nor LEFT, RIGHT, CAST or TRY_CAST — followed by its arguments in
 * parentheses and the statement's semicolon. The arguments are read as values: a star, DISTINCT or a named argument's
 * arrow is refused where it stands ({@code COUNT(*)} at the star), and so is any other token no value reads
 * ({@code ABS(1 2)} at the 2). A call with no semicolon after it, or with an operator after its parenthesis, keeps
 * its syntax error here, where the account reports the token after the call.
 */
public final class BlockCallStatementReading {

    /** The statement that does nothing, which a call gives way to while the rest of the text is read. */
    private static final String NOTHING = "NULL";

    /** The most calls one text is read past before the reading gives up. */
    private static final int MOST_CALLS = 64;

    /** The words that open a statement of their own, each of which may be followed by a parenthesis. */
    private static final Set<Integer> STATEMENT_WORDS = new HashSet<>(Arrays.asList(
        FrostlakeLexer.RETURN, FrostlakeLexer.SELECT, FrostlakeLexer.VALUES, FrostlakeLexer.WITH, FrostlakeLexer.IF,
        FrostlakeLexer.CASE, FrostlakeLexer.WHILE, FrostlakeLexer.FOR, FrostlakeLexer.REPEAT, FrostlakeLexer.LOOP,
        FrostlakeLexer.CALL, FrostlakeLexer.EXECUTE, FrostlakeLexer.LET, FrostlakeLexer.RAISE, FrostlakeLexer.OPEN,
        FrostlakeLexer.FETCH, FrostlakeLexer.CLOSE, FrostlakeLexer.NULL, FrostlakeLexer.BEGIN, FrostlakeLexer.DECLARE,
        FrostlakeLexer.INSERT, FrostlakeLexer.UPDATE, FrostlakeLexer.DELETE, FrostlakeLexer.MERGE,
        FrostlakeLexer.CREATE, FrostlakeLexer.ALTER, FrostlakeLexer.DROP, FrostlakeLexer.SHOW,
        FrostlakeLexer.DESCRIBE, FrostlakeLexer.DESC, FrostlakeLexer.USE, FrostlakeLexer.GRANT, FrostlakeLexer.REVOKE,
        FrostlakeLexer.COPY, FrostlakeLexer.TRUNCATE, FrostlakeLexer.COMMIT, FrostlakeLexer.ROLLBACK,
        FrostlakeLexer.BREAK, FrostlakeLexer.CONTINUE, FrostlakeLexer.SET, FrostlakeLexer.UNSET,
        FrostlakeLexer.COMMENT, FrostlakeLexer.UNDROP, FrostlakeLexer.EXPLAIN, FrostlakeLexer.KW_IDENTIFIER,
        FrostlakeLexer.END, FrostlakeLexer.ELSE, FrostlakeLexer.ELSEIF, FrostlakeLexer.WHEN, FrostlakeLexer.EXCEPTION,
        FrostlakeLexer.ASYNC, FrostlakeLexer.AWAIT, FrostlakeLexer.LEFT, FrostlakeLexer.RIGHT,
        FrostlakeLexer.CAST, FrostlakeLexer.TRY_CAST));

    private BlockCallStatementReading() {
    }

    /**
     * Refuse the text for its first call written as a block's statement when the parse met a fault and such calls
     * are all of it.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener) {
        final RuntimeException refusal = listener.hasErrors() ? refusalOf(sql) : null;
        if (refusal != null) {
            throw refusal;
        }
    }

    /**
     * As {@link #requireBefore(String, SyntaxErrorListener)}, the calls' names judged by the engine's registry.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     * @param registry the engine's function registry
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener,
                                     final FunctionRegistry registry) {
        final RuntimeException refusal = listener.hasErrors() ? refusalOf(sql) : null;
        if (refusal instanceof BlockCallStatementRefusal) {
            throw ((BlockCallStatementRefusal) refusal).judged(registry);
        }
        if (refusal != null) {
            throw refusal;
        }
    }

    /**
     * @param sql a text that does not parse
     * @return the refusal of its calls written as a block's statements, the syntax error of the first call's
     *         argument no value reads, or null when such calls are not all that keeps the text from parsing
     */
    static RuntimeException refusalOf(final String sql) {
        if (sql == null) {
            return null;
        }
        final char[] text = sql.toCharArray();
        final List<Integer> blanked = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (int round = 0; round < MOST_CALLS; round++) {
            final List<Token> spoken = new ArrayList<>();
            final Token fault = firstFault(new String(text), spoken);
            if (fault == null) {
                break;
            }
            final int[] call = callAround(spoken, fault);
            if (call == null) {
                return null;
            }
            final Token name = spoken.get(call[0]);
            final int from = name.getStartIndex();
            final int to = spoken.get(call[1]).getStopIndex();
            if (to - from + 1 < NOTHING.length()) {
                return null;
            }
            final Token refused = argumentRefused(spoken, call);
            if (refused != null) {
                // The first fault is an argument no value reads, and the account names it where it stands.
                final String line = SyntaxErrorListener.sentence(refused);
                return new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), sql);
            }
            names.add(name.getText());
            // The statement gives way to one that does nothing, standing where it stood.
            for (int c = from; c <= to; c++) {
                if (text[c] != '\n') {
                    text[c] = c - from < NOTHING.length() ? NOTHING.charAt(c - from) : ' ';
                }
            }
            blanked.add(Integer.valueOf(from));
        }
        final String repaired = new String(text);
        if (names.isEmpty() || !SyntaxErrorListener.linesReportedAfresh(repaired).isEmpty()
                || !inBlocks(repaired, blanked)) {
            return null;
        }
        return new BlockCallStatementRefusal(names);
    }

    /**
     * The call statement around the token a parse refused, as the indexes of its name and of its closing parenthesis
     * among the spoken tokens, or null when the token stands in no such statement.
     */
    private static int[] callAround(final List<Token> spoken, final Token fault) {
        for (int i = 1; i + 1 < spoken.size(); i++) {
            final Token name = spoken.get(i);
            if (!opensStatement(spoken.get(i - 1).getType()) || !isCallName(name)
                    || spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN) {
                continue;
            }
            final int close = closingParen(spoken, i + 1);
            if (close < 0 || close + 1 >= spoken.size() || spoken.get(close + 1).getType() != FrostlakeLexer.SEMI) {
                continue;
            }
            if (fault.getStartIndex() >= name.getStartIndex()
                    && fault.getStartIndex() <= spoken.get(close + 1).getStopIndex()) {
                return new int[] {i, close};
            }
        }
        return null;
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 when none does. */
    private static int closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * The first token of the call's arguments no value reads, or null when each argument is a value: a star or
     * DISTINCT opening an argument, a named argument's arrow, and whatever a query that calls the function where a
     * value stands refuses.
     */
    private static Token argumentRefused(final List<Token> spoken, final int[] call) {
        final List<Token> query = new ArrayList<>();
        final Token name = spoken.get(call[0]);
        final CommonToken select = new CommonToken(FrostlakeLexer.SELECT, "SELECT");
        select.setLine(name.getLine());
        select.setCharPositionInLine(name.getCharPositionInLine());
        query.add(select);
        int depth = 0;
        for (int i = call[0]; i <= call[1]; i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            final int before = spoken.get(i - 1).getType();
            final boolean opensArgument = depth == 1
                && (before == FrostlakeLexer.LPAREN || before == FrostlakeLexer.COMMA);
            if (opensArgument && (type == FrostlakeLexer.STAR || type == FrostlakeLexer.DISTINCT)
                    || depth == 1 && type == FrostlakeLexer.ARROW) {
                return token;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            query.add(token);
        }
        query.add(new CommonToken(Token.EOF, "<EOF>"));
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(new ListTokenSource(query)));
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        final Token[] refused = new Token[1];
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (refused[0] == null && offendingSymbol instanceof Token) {
                    refused[0] = (Token) offendingSymbol;
                }
            }
        });
        parser.sqlScript();
        return refused[0];
    }

    /**
     * The first token a parse of the text refuses, or null when it parses cleanly; its spoken tokens fill
     * {@code into}.
     */
    private static Token firstFault(final String text, final List<Token> into) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final boolean[] unlexed = {false};
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                unlexed[0] = true;
            }
        });
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        final Token[] fault = new Token[1];
        final boolean[] refused = {false};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (!refused[0] && offendingSymbol instanceof Token) {
                    fault[0] = (Token) offendingSymbol;
                }
                refused[0] = true;
            }
        });
        parser.sqlScript();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                into.add(token);
            }
        }
        if (unlexed[0] || refused[0] && fault[0] == null) {
            // A fault the lexer met, or one with no token, is no call's: an EOF token stands outside every call.
            return new CommonToken(Token.EOF, "<EOF>");
        }
        return fault[0];
    }

    /** Whether every statement a call gave way to stands among a block's statements. */
    private static boolean inBlocks(final String text, final List<Integer> blanked) {
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(new FrostlakeLexer(
            SqlCharStream.of(text))));
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        for (final Integer from : blanked) {
            if (!nothingInBlockAt(script, from.intValue(), false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean nothingInBlockAt(final ParseTree node, final int from, final boolean inBlock) {
        final boolean block = inBlock || node instanceof FrostlakeParser.BeginEndBlockContext;
        if (node instanceof FrostlakeParser.NullStatementContext) {
            return block && ((ParserRuleContext) node).getStart().getStartIndex() == from;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (nothingInBlockAt(node.getChild(i), from, block)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call's name may be the token: one word, not a statement's own. */
    private static boolean isCallName(final Token token) {
        final String text = token.getText();
        if (text == null || text.isEmpty() || STATEMENT_WORDS.contains(Integer.valueOf(token.getType()))) {
            return false;
        }
        final char lead = text.charAt(0);
        return Character.isLetter(lead) || lead == '_';
    }

    /** Whether a statement may open after a token of that type: a block's or a branch's first, or after a semicolon. */
    private static boolean opensStatement(final int type) {
        return type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.THEN
            || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO || type == FrostlakeLexer.LOOP
            || type == FrostlakeLexer.REPEAT;
    }
}
