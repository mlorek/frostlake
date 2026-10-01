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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a fault in the column list of a CREATE TABLE (all live-verified). Live reads the list
 * one column at a time. A column with no type is refused at the comma or bracket standing where the type belongs,
 * and the list reads on from there; any other token that is no column name, or no type, is refused where it stands,
 * and the recovery skips to the first ')' at or after it, taking that bracket as the end of the list. The statement
 * then reads on, and its next fault is one more line:
 *
 * <pre>
 *   CREATE TABLE tq ('n' || 'x') (a INT)      ''n'', then '('     the first ')' ends the list
 *   CREATE TABLE tq (UPPER('n')) (a INT)      '(', then ')'       UPPER's own ')' ends the list
 *   CREATE TABLE tq (x) (a INT)               ')', then '('       the typeless column's ')' ends it
 *   CREATE TABLE tq (x, y) (a INT)            ',', ')', then '('
 *   CREATE TABLE tq (a INT, 'b' INT) x y      ''b'', then 'y'     x reads as a property's name
 *   CREATE TABLE tq ('n') AS SELECT 1         ''n'', then 'AS'
 * </pre>
 *
 * <p>This parser split such a statement differently: unable to predict the list at all, it opened a statement at a
 * bracket and named whatever inside that could not begin one. A fault after a column's type — in its options or its
 * type's parameters — and a table constraint are left to the parse, as is a typeless list followed by AS.
 */
public final class ColumnListRecovery {

    private static final String PLACEHOLDER_TABLE = "x";

    private ColumnListRecovery() {
    }

    /**
     * The tokens live reports for the first fault of a CREATE TABLE whose column list holds a fault this model
     * reads, in order, or null when the fault is no such case.
     *
     * @param recognizer the parser that met its first fault
     * @param reported the token that fault is currently reported at
     * @return up to three tokens, or null
     */
    static List<Token> refusal(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        FrostlakeParser.CreateStatementContext create = null;
        for (ParserRuleContext context = parser.getContext(); context != null && create == null;
                context = context.getParent()) {
            if (context instanceof FrostlakeParser.CreateStatementContext) {
                create = (FrostlakeParser.CreateStatementContext) context;
            }
        }
        if (create == null || create.getStart() == null || create.getStart().getType() != FrostlakeLexer.CREATE) {
            return null;
        }
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> spoken = spokenStatement(stream, create.getStart());
        try {
            final int open = listOpen(spoken, reported);
            return open < 0 ? null : linesFrom(parser, spoken, open, reported);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** The default-channel tokens from {@code start} to the statement's semicolon, inclusive, or to the end of input. */
    private static List<Token> spokenStatement(final TokenStream stream, final Token start) {
        final List<Token> spoken = new ArrayList<>();
        for (int i = start.getTokenIndex(); i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            spoken.add(token);
            if (token.getType() == FrostlakeLexer.SEMI || token.getType() == Token.EOF) {
                break;
            }
        }
        return spoken;
    }

    /**
     * The index in {@code spoken} of the '(' that opens the statement's column list, confirmed by the grammar: the
     * statement's words up to it, completed by a one-column list, parse as a CREATE TABLE whose list opens there.
     * The list must open before the reported fault. -1 when no bracket does.
     */
    private static int listOpen(final List<Token> spoken, final Token reported) {
        boolean table = false;
        for (int i = 1; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI
                    || token.getStartIndex() >= reported.getStartIndex() && reported.getType() != Token.EOF) {
                return -1;
            }
            table = table || token.getType() == FrostlakeLexer.TABLE;
            if (token.getType() != FrostlakeLexer.LPAREN || !table) {
                continue;
            }
            final List<Token> tokens = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                tokens.add(new CommonToken(spoken.get(j)));
            }
            tokens.add(placeholder(spoken.get(0), FrostlakeLexer.IDENTIFIER, "a"));
            tokens.add(placeholder(spoken.get(0), FrostlakeLexer.INT, "INT"));
            tokens.add(placeholder(spoken.get(0), FrostlakeLexer.RPAREN, ")"));
            final FrostlakeParser.SqlScriptContext script = parse(tokens, new ArrayList<Token>(), null);
            if (script != null && opensColumnList(script, token)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a parsed script is one CREATE TABLE whose column list opens at the bracket {@code open}. */
    private static boolean opensColumnList(final FrostlakeParser.SqlScriptContext script, final Token open) {
        if (script.flowChain().size() != 1 || script.flowChain(0).statement().size() != 1) {
            return false;
        }
        final FrostlakeParser.StatementContext statement = script.flowChain(0).statement(0);
        final FrostlakeParser.CreateStatementContext create = statement.ddlStatement() == null
            ? null : statement.ddlStatement().createStatement();
        if (create == null || create.TABLE() == null || create.columnList() == null) {
            return false;
        }
        for (int i = 0; i < create.getChildCount(); i++) {
            final ParseTree child = create.getChild(i);
            if (child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.LPAREN) {
                return ((TerminalNode) child).getSymbol().getStartIndex() == open.getStartIndex();
            }
        }
        return false;
    }

    /** The lines for the list opening at {@code spoken[open]}, or null when its first fault is not one this reads. */
    private static List<Token> linesFrom(final Parser parser, final List<Token> spoken, final int open,
                                         final Token reported) {
        final ATN atn = parser.getATN();
        final IntervalSet names = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_columnDefName]);
        final IntervalSet constraints = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_tableConstraint]);
        final IntervalSet types = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_dataTypeName]);
        final List<Token> lines = new ArrayList<>();
        boolean typelessOnly = true;
        int close = -1;
        int i = open + 1;
        while (close < 0) {
            final Token name = at(spoken, i);
            if (name == null || name.getType() == FrostlakeLexer.LPAREN || constraints.contains(name.getType())
                    || lines.size() == 3) {
                return null;
            }
            if (!names.contains(name.getType()) && name.getType() != FrostlakeLexer.QUOTED_IDENTIFIER) {
                lines.add(name);
                typelessOnly = false;
                close = firstCloseFrom(spoken, i);
                break;
            }
            final Token type = at(spoken, i + 1);
            if (type == null) {
                return null;
            }
            if (type.getType() == FrostlakeLexer.COMMA || type.getType() == FrostlakeLexer.RPAREN) {
                lines.add(type);
                if (type.getType() == FrostlakeLexer.RPAREN) {
                    close = i + 1;
                } else {
                    i += 2;
                }
                continue;
            }
            if (!types.contains(type.getType())) {
                lines.add(type);
                typelessOnly = false;
                close = firstCloseFrom(spoken, i + 1);
                break;
            }
            final int end = columnEnd(spoken, i + 2);
            if (end < 0 || reportedWithin(spoken, i + 1, end, reported)) {
                return null;
            }
            if (spoken.get(end).getType() == FrostlakeLexer.RPAREN) {
                close = end;
            } else {
                i = end + 1;
            }
        }
        if (lines.isEmpty() || close < 0 || lines.size() > 3) {
            return null;
        }
        final Token next = at(spoken, close + 1);
        if (next == null || next.getType() == Token.EOF || next.getType() == FrostlakeLexer.SEMI) {
            return lines;
        }
        if (typelessOnly && followedByAs(spoken, close + 1)) {
            return null;
        }
        final Token fault = next.getType() == FrostlakeLexer.LPAREN || next.getType() == FrostlakeLexer.RPAREN
            || next.getType() == FrostlakeLexer.COMMA || next.getType() == FrostlakeLexer.AS
            ? next : faultAfterList(spoken, close + 1);
        if (fault != null) {
            if (lines.size() == 3) {
                return null;
            }
            lines.add(fault);
        }
        return lines;
    }

    /** Whether an AS stands among the statement's words from {@code from} on: a typeless list may then be a CTAS's. */
    private static boolean followedByAs(final List<Token> spoken, final int from) {
        for (int i = from; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.AS) {
                return true;
            }
        }
        return false;
    }

    /** The token at {@code i}, or null past the statement. */
    private static Token at(final List<Token> spoken, final int i) {
        return i < spoken.size() && spoken.get(i).getType() != Token.EOF ? spoken.get(i) : null;
    }

    /** The index of the first ')' at or after {@code from}, or -1. */
    private static int firstCloseFrom(final List<Token> spoken, final int from) {
        for (int i = from; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The index of the ',' or ')' that ends a typed column whose type's parameters, if any, start at {@code from}:
     * the first one outside every bracket. -1 when the parameters are not plain numbers, or the list never ends.
     */
    private static int columnEnd(final List<Token> spoken, final int from) {
        int i = from;
        if (i < spoken.size() && spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
            i++;
            while (i < spoken.size() && spoken.get(i).getType() != FrostlakeLexer.RPAREN) {
                final int type = spoken.get(i).getType();
                if (type != FrostlakeLexer.INTEGER_LITERAL && type != FrostlakeLexer.COMMA
                        && type != FrostlakeLexer.MINUS) {
                    return -1;
                }
                i++;
            }
            i++;
        }
        int depth = 0;
        for (; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                return i;
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether the reported fault lies among the tokens from {@code from} to {@code to}, inclusive. */
    private static boolean reportedWithin(final List<Token> spoken, final int from, final int to, final Token reported) {
        return reported.getType() != Token.EOF && reported.getStartIndex() >= spoken.get(from).getStartIndex()
            && reported.getStartIndex() <= spoken.get(to).getStopIndex();
    }

    /**
     * The first fault of the statement's words after its column list, read as a CREATE TABLE whose list was whole;
     * null when they read without one.
     */
    private static Token faultAfterList(final List<Token> spoken, final int from) {
        final Token source = spoken.get(0);
        final List<Token> tokens = new ArrayList<>();
        tokens.add(placeholder(source, FrostlakeLexer.CREATE, "CREATE"));
        tokens.add(placeholder(source, FrostlakeLexer.TABLE, "TABLE"));
        tokens.add(placeholder(source, FrostlakeLexer.IDENTIFIER, PLACEHOLDER_TABLE));
        tokens.add(placeholder(source, FrostlakeLexer.LPAREN, "("));
        tokens.add(placeholder(source, FrostlakeLexer.IDENTIFIER, "a"));
        tokens.add(placeholder(source, FrostlakeLexer.INT, "INT"));
        tokens.add(placeholder(source, FrostlakeLexer.RPAREN, ")"));
        Token end = null;
        for (int i = from; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == Token.EOF) {
                end = spoken.get(i);
            } else {
                tokens.add(new CommonToken(spoken.get(i)));
            }
        }
        final List<Token> faults = new ArrayList<>();
        parse(tokens, faults, end);
        return faults.isEmpty() ? null : faults.get(0);
    }

    /** A token no text holds, written into a re-read with the same source as {@code source}, so recovery can use it. */
    private static Token placeholder(final Token source, final int type, final String text) {
        final CommonToken token = new CommonToken(new Pair<TokenSource, CharStream>(source.getTokenSource(),
            source.getInputStream()), type, Token.DEFAULT_CHANNEL, -1, -1);
        token.setText(text);
        token.setLine(source.getLine());
        token.setCharPositionInLine(source.getCharPositionInLine());
        return token;
    }

    /**
     * The script the tokens parse to, the end of input appended — the input's own {@code end} when given — or null
     * when it has a fault; each fault's offending token is added to {@code faults}.
     */
    private static FrostlakeParser.SqlScriptContext parse(final List<Token> tokens, final List<Token> faults,
                                                          final Token end) {
        final CommonToken eof = end != null ? new CommonToken(end)
            : (CommonToken) placeholder(tokens.get(0), Token.EOF, "<EOF>");
        tokens.add(eof);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(new ListTokenSource(tokens)));
        parser.removeErrorListeners();
        final int[] count = new int[1];
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                count[0]++;
                if (offendingSymbol instanceof Token) {
                    faults.add((Token) offendingSymbol);
                }
            }
        });
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        return count[0] == 0 ? script : null;
    }
}
