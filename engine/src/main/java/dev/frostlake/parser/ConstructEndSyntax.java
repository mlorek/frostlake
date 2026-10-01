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

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Where live refuses the END of a Snowflake Scripting construct: a loop left without its semicolon, and a body left
 * empty (all live-verified).
 *
 * <p>★ A LOOP'S END TAKES A LABEL. After {@code END WHILE}, {@code END LOOP}, {@code END FOR} or {@code END REPEAT} the
 * account reads the next word as the loop's trailing label whenever a label can be that word — any name, RETURN, LET,
 * CALL, IF, WHILE, CASE, MERGE, EXCEPTION and their kind, but not END, BEGIN, FOR, NULL, SELECT, INSERT, UPDATE,
 * DELETE or CREATE — so a loop run into the next statement is refused at the token AFTER that word, and the recovery
 * stacks its usual second line from there:
 *
 * <pre>
 *   WHILE (i &lt; 3) DO i := i + 1; END WHILE RETURN i; END;    'i', then 'END'      RETURN is the label
 *   LOOP … END LOOP CALL p(); END;                             'p', then '('
 *   LOOP … END LOOP BREAK; END LOOP END; RETURN 1; END;        'END', then 'RETURN' END is no label
 * </pre>
 *
 * <p>★ AN EMPTY BODY IS REFUSED AT THE TOKEN THAT CLOSES IT — the END of a loop, an IF branch, a CASE branch, a block
 * or an exception handler, the UNTIL of a REPEAT — where this parser read that END as the first word of a statement
 * and refused the keyword after it. The recovery then reads the construct's closing words as written:
 *
 * <pre>
 *   WHILE (FALSE) DO END WHILE; …                  'END'
 *   WHILE (FALSE) DO END; …                        'END', then ';'            the WHILE after END is missing
 *   LOOP END LOOP x y; END;                        'END', 'y', then 'END'     x is the label
 *   IF (TRUE) THEN END IF RETURN 1; END;           'END', 'RETURN', then '1'  an IF takes no label
 * </pre>
 */
public final class ConstructEndSyntax {

    private ConstructEndSyntax() {
    }

    /**
     * The token live refuses when a block statement run into the next word without its semicolon is a loop ending at
     * its END keyword pair, and that word can be the loop's label: the token after the word. Null otherwise, or when
     * nothing refusable follows the word.
     *
     * @param recognizer the parser that met the missing semicolon
     * @param word the word the statement ran into
     * @return the token after the swallowed label, or null
     */
    static Token labelFollower(final Recognizer<?, ?> recognizer, final Token word) {
        if (!(recognizer instanceof Parser) || word.getType() == Token.EOF || word.getType() == FrostlakeLexer.END
                || !SyntaxErrorListener.isNameLike((Parser) recognizer, word)) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        if (!endsUnlabelledLoop(finishedStatement(parser))) {
            return null;
        }
        final Token next = nextSpoken(filled(parser.getInputStream()), word.getTokenIndex());
        return next == null || next.getType() == Token.EOF || next.getType() == FrostlakeLexer.SEMI ? null : next;
    }

    /**
     * The line live stacks after a loop's swallowed label when the loop stands in a BEGIN … END block nested in other
     * blocks: its recovery reads the END of each nested block, with the semicolon after it, and names the token after
     * them — the outermost block's END, or the next statement's first word (all live-verified):
     *
     * <pre>
     *   BEGIN BEGIN WHILE … END WHILE RETURN i; END; END;              'i', then the outer 'END'
     *   BEGIN BEGIN WHILE … END WHILE RETURN i; END; RETURN 2; END;    'i', then 'RETURN'
     * </pre>
     *
     * <p>A loop inside an IF, a CASE, another loop or an exception handler keeps the line it has.
     *
     * @param recognizer the parser that met the missing semicolon
     * @param stacked the line the block-level reading names
     * @return the line live names
     */
    static Token pastNestedBlockEnds(final Recognizer<?, ?> recognizer, final Token stacked) {
        if (!(recognizer instanceof Parser) || stacked == null) {
            return stacked;
        }
        final Parser parser = (Parser) recognizer;
        int nested = -1;
        for (ParserRuleContext context = parser.getContext(); context != null; context = context.getParent()) {
            if (context instanceof FrostlakeParser.BeginEndBlockContext) {
                nested++;
            } else if (context instanceof FrostlakeParser.IfStatementContext
                    || context instanceof FrostlakeParser.CaseStatementContext
                    || context instanceof FrostlakeParser.LoopStatementContext
                    || context instanceof FrostlakeParser.WhileStatementContext
                    || context instanceof FrostlakeParser.ForStatementContext
                    || context instanceof FrostlakeParser.RepeatStatementContext
                    || context instanceof FrostlakeParser.ExceptionHandlerContext) {
                return stacked;
            }
        }
        final TokenStream stream = filled(parser.getInputStream());
        Token token = stacked;
        while (nested > 0 && token.getType() == FrostlakeLexer.END) {
            Token next = nextSpoken(stream, token.getTokenIndex());
            if (next != null && next.getType() == FrostlakeLexer.SEMI) {
                next = nextSpoken(stream, next.getTokenIndex());
            }
            if (next == null || next.getType() == Token.EOF) {
                return token;
            }
            token = next;
            nested--;
        }
        return token;
    }

    /**
     * The tokens live reports when the parser dead-ends on an END written where a loop's label, or a BREAK's or
     * CONTINUE's, may stand: that END — no label can be one — then the lines live's recovery stacks from it; or on the
     * word after a loop's written label (see {@link #afterWrittenLabel}). Null for any other fault.
     *
     * @param recognizer the parser that met the fault
     * @param reported the token the fault is currently reported at
     * @param e the fault
     * @return the tokens, or null
     */
    static List<Token> endAsLabel(final Recognizer<?, ?> recognizer, final Token reported,
                                  final RecognitionException e) {
        if (!(recognizer instanceof Parser) || !(e instanceof NoViableAltException)) {
            return null;
        }
        if (reported.getType() != FrostlakeLexer.END) {
            return afterWrittenLabel((Parser) recognizer, reported);
        }
        final Parser parser = (Parser) recognizer;
        final ParserRuleContext context = parser.getContext();
        final Token before = previousSpoken(parser.getInputStream(), reported.getTokenIndex());
        if (before == null) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        if (context instanceof FrostlakeParser.BreakStatementContext
                || context instanceof FrostlakeParser.ContinueStatementContext) {
            lines.add(reported);
            lines.addAll(afterLoopClosedAt(parser, context, reported));
            return lines;
        }
        final boolean loop = context instanceof FrostlakeParser.LoopStatementContext
            || context instanceof FrostlakeParser.WhileStatementContext
            || context instanceof FrostlakeParser.ForStatementContext
            || context instanceof FrostlakeParser.RepeatStatementContext;
        final Token end = previousSpoken(parser.getInputStream(), before.getTokenIndex());
        if (!loop || end == null || end.getType() != FrostlakeLexer.END) {
            return null;
        }
        lines.add(reported);
        final Token again = SyntaxErrorListener.afterFirstName(parser, reported);
        if (again != null) {
            lines.add(again);
        }
        return lines;
    }

    /**
     * The tokens live reports when the parser dead-ends on the word after a written loop label — a loop run into an
     * exception handler reads EXCEPTION as its label (live-verified: {@code … END WHILE EXCEPTION WHEN OTHER THEN …}
     * is 'WHEN', then 'OTHER'): that word, then the token after the first name from it on. A loop whose body is
     * empty is left to {@link #emptyBody}.
     */
    private static List<Token> afterWrittenLabel(final Parser parser, final Token reported) {
        final ParserRuleContext context = parser.getContext();
        if (reported.getType() == Token.EOF || reported.getType() == FrostlakeLexer.SEMI
                || !(context instanceof FrostlakeParser.LoopStatementContext
                    || context instanceof FrostlakeParser.WhileStatementContext
                    || context instanceof FrostlakeParser.ForStatementContext
                    || context instanceof FrostlakeParser.RepeatStatementContext)) {
            return null;
        }
        final TokenStream stream = filled(parser.getInputStream());
        final Token label = previousSpoken(stream, reported.getTokenIndex());
        final Token keyword = label == null ? null : previousSpoken(stream, label.getTokenIndex());
        final Token end = keyword == null ? null : previousSpoken(stream, keyword.getTokenIndex());
        final Token lastOfBody = end == null ? null : previousSpoken(stream, end.getTokenIndex());
        if (lastOfBody == null || lastOfBody.getType() != FrostlakeLexer.SEMI || end.getType() != FrostlakeLexer.END
                || !isLoopKeyword(keyword) || !SyntaxErrorListener.isNameLike(parser, label)) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(reported);
        final Token again = SyntaxErrorListener.afterFirstName(parser, reported);
        if (again != null) {
            lines.add(again);
        }
        return lines;
    }

    private static boolean isLoopKeyword(final Token token) {
        return token.getType() == FrostlakeLexer.LOOP || token.getType() == FrostlakeLexer.WHILE
            || token.getType() == FrostlakeLexer.FOR || token.getType() == FrostlakeLexer.REPEAT;
    }

    /**
     * The lines live stacks after a BREAK or a CONTINUE run into an END (all live-verified): it takes that END as its
     * loop's own, refuses the token after it unless that is the loop's keyword, and reads on in the block around the
     * loop, whose closing END the loop's written END now is; the token after that END, its keyword and its semicolon
     * is refused too:
     *
     * <pre>
     *   BEGIN LOOP BREAK END; END LOOP; RETURN 1; END;    'END', ';', then 'RETURN'
     *   BEGIN LOOP BREAK END; RETURN 1; END;              'END', then ';'
     *   BEGIN LOOP BREAK END LOOP; RETURN 1; END;         'END'
     * </pre>
     */
    private static List<Token> afterLoopClosedAt(final Parser parser, final ParserRuleContext statement,
                                                 final Token end) {
        final List<Token> lines = new ArrayList<>();
        ParserRuleContext body = null;
        ParserRuleContext owner = statement;
        while (owner != null && !(owner instanceof FrostlakeParser.LoopStatementContext
                || owner instanceof FrostlakeParser.WhileStatementContext
                || owner instanceof FrostlakeParser.ForStatementContext
                || owner instanceof FrostlakeParser.RepeatStatementContext)) {
            if (owner instanceof FrostlakeParser.StatementListContext) {
                body = owner;
            }
            owner = owner.getParent();
        }
        final TokenStream stream = filled(parser.getInputStream());
        if (owner == null || body == null || !(owner.getParent() instanceof FrostlakeParser.ProceduralStatementContext)) {
            return lines;
        }
        final int keyword = closingKeyword(stream, owner, (FrostlakeParser.StatementListContext) body);
        final Token after = nextSpoken(stream, end.getTokenIndex());
        if (after == null || after.getType() == Token.EOF || after.getType() == keyword) {
            return lines;
        }
        lines.add(after);
        final Token closing = firstEndAfter(stream, after);
        if (closing == null) {
            return lines;
        }
        Token next = nextSpoken(stream, closing.getTokenIndex());
        if (next != null && next.getType() == keyword) {
            next = nextSpoken(stream, next.getTokenIndex());
        } else if (next != null && next.getType() != FrostlakeLexer.SEMI && next.getType() != Token.EOF) {
            return lines;
        }
        if (next != null && next.getType() == FrostlakeLexer.SEMI) {
            next = nextSpoken(stream, next.getTokenIndex());
        }
        if (next != null && next.getType() != Token.EOF) {
            lines.add(next);
        }
        return lines;
    }

    /**
     * The tokens live reports when the statement the parser was reading opens an empty body at the END (or UNTIL)
     * that closes it: that token first, then the lines the recovery stacks after it. Null for any other fault.
     *
     * @param recognizer the parser that met the fault
     * @param reported the token the fault is currently reported at
     * @return the tokens, or null
     */
    static List<Token> emptyBody(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        FrostlakeParser.StatementListContext list = null;
        FrostlakeParser.StatementContext first = null;
        for (ParserRuleContext context = parser.getContext(); context != null && list == null;
                context = context.getParent()) {
            if (context instanceof FrostlakeParser.StatementContext
                    && context.getParent() instanceof FrostlakeParser.StatementListContext) {
                first = (FrostlakeParser.StatementContext) context;
                list = (FrostlakeParser.StatementListContext) context.getParent();
            }
        }
        if (list == null || list.getChildCount() == 0 || list.getChild(0) != first || first.getStart() == null
                || first.getStart().getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        final Token opener = first.getStart();
        final ParserRuleContext owner = list.getParent();
        final boolean untilOfRepeat = opener.getType() == FrostlakeLexer.UNTIL
            && owner instanceof FrostlakeParser.RepeatStatementContext;
        if (opener.getType() != FrostlakeLexer.END && !untilOfRepeat) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(opener);
        if (owner instanceof FrostlakeParser.BeginEndBlockContext
                || owner instanceof FrostlakeParser.ExceptionHandlerContext) {
            return lines;
        }
        final TokenStream stream = filled(parser.getInputStream());
        Token end = opener;
        if (untilOfRepeat) {
            end = endAfterCondition(stream, opener);
            if (end == null) {
                // A bare condition then stacks its own pair: its first token, where the '(' belongs, and the END
                // where the ')' does — UNTIL TRUE END REPEAT is 'UNTIL', 'TRUE', 'END'.
                final Token condition = nextSpoken(stream, opener.getTokenIndex());
                final Token close = firstEndAfter(stream, opener);
                if (condition != null && close != null && condition.getTokenIndex() < close.getTokenIndex()) {
                    lines.add(condition);
                    lines.add(close);
                }
                return lines;
            }
        }
        final int closer = closingKeyword(stream, owner, list);
        if (closer < 0) {
            return null;
        }
        final Token keyword = nextSpoken(stream, end.getTokenIndex());
        if (keyword == null || keyword.getType() == Token.EOF) {
            return lines;
        }
        if (keyword.getType() != closer) {
            if (!(owner instanceof FrostlakeParser.CaseStatementContext && keyword.getType() == FrostlakeLexer.SEMI)) {
                lines.add(keyword);
            }
            return lines;
        }
        Token fault = nextSpoken(stream, keyword.getTokenIndex());
        if (fault == null || fault.getType() == Token.EOF || fault.getType() == FrostlakeLexer.SEMI) {
            return lines;
        }
        final boolean loop = !(owner instanceof FrostlakeParser.IfStatementContext
            || owner instanceof FrostlakeParser.CaseStatementContext);
        if (loop && fault.getType() != FrostlakeLexer.END && SyntaxErrorListener.isNameLike(parser, fault)) {
            fault = nextSpoken(stream, fault.getTokenIndex());
            if (fault == null || fault.getType() == Token.EOF || fault.getType() == FrostlakeLexer.SEMI) {
                return lines;
            }
        }
        lines.add(fault);
        final ParserRuleContext construct = owner.getParent() == null ? null : owner.getParent().getParent();
        final ParserRuleContext holder = construct == null ? null : construct.getParent();
        if (holder instanceof FrostlakeParser.StatementListContext
                && (holder.getParent() instanceof FrostlakeParser.BeginEndBlockContext
                    || holder.getParent() instanceof FrostlakeParser.ExceptionHandlerContext)) {
            final Token again = SyntaxErrorListener.afterFirstName(parser, fault);
            if (again != null) {
                lines.add(again);
            }
        }
        return lines;
    }

    /** The statement a block's statement list finished last, when the parser stands in that list; or null. */
    private static FrostlakeParser.StatementContext finishedStatement(final Parser parser) {
        if (!(parser.getContext() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final ParserRuleContext list = parser.getContext();
        for (int i = list.getChildCount() - 1; i >= 0; i--) {
            if (list.getChild(i) instanceof FrostlakeParser.StatementContext) {
                return (FrostlakeParser.StatementContext) list.getChild(i);
            }
        }
        return null;
    }

    /** Whether a statement is a loop that ends at its END keyword pair, with no label after it. */
    private static boolean endsUnlabelledLoop(final FrostlakeParser.StatementContext statement) {
        if (statement == null || statement.proceduralStatement() == null) {
            return false;
        }
        final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
        final ParserRuleContext loop;
        final FrostlakeParser.LoopLabelContext label;
        if (procedural.whileStatement() != null) {
            loop = procedural.whileStatement();
            label = procedural.whileStatement().loopLabel();
        } else if (procedural.loopStatement() != null) {
            loop = procedural.loopStatement();
            label = procedural.loopStatement().loopLabel();
        } else if (procedural.forStatement() != null) {
            loop = procedural.forStatement();
            label = procedural.forStatement().loopLabel();
        } else if (procedural.repeatStatement() != null) {
            loop = procedural.repeatStatement();
            label = procedural.repeatStatement().loopLabel();
        } else {
            return false;
        }
        return label == null && loop.getStop() != null && loop.getStop().getType() != FrostlakeLexer.SEMI
            && loop.getStop().getType() != FrostlakeLexer.END;
    }

    /** The keyword that must follow the END closing a body of {@code owner}, or -1 for an owner with no such keyword. */
    private static int closingKeyword(final TokenStream stream, final ParserRuleContext owner,
                                      final FrostlakeParser.StatementListContext body) {
        if (owner instanceof FrostlakeParser.WhileStatementContext) {
            final Token opener = previousSpoken(stream, body.getStart().getTokenIndex());
            return opener != null && opener.getType() == FrostlakeLexer.LOOP ? FrostlakeLexer.LOOP : FrostlakeLexer.WHILE;
        }
        if (owner instanceof FrostlakeParser.LoopStatementContext) {
            return FrostlakeLexer.LOOP;
        }
        if (owner instanceof FrostlakeParser.ForStatementContext) {
            return FrostlakeLexer.FOR;
        }
        if (owner instanceof FrostlakeParser.RepeatStatementContext) {
            return FrostlakeLexer.REPEAT;
        }
        if (owner instanceof FrostlakeParser.IfStatementContext) {
            return FrostlakeLexer.IF;
        }
        if (owner instanceof FrostlakeParser.CaseStatementContext) {
            return FrostlakeLexer.CASE;
        }
        return -1;
    }

    /** The END after a REPEAT's bracketed UNTIL condition, or null when the condition is not written that way. */
    private static Token endAfterCondition(final TokenStream stream, final Token until) {
        final Token open = nextSpoken(stream, until.getTokenIndex());
        if (open == null || open.getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int depth = 0;
        for (Token token = open; token != null && token.getType() != Token.EOF;
                token = nextSpoken(stream, token.getTokenIndex())) {
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN && --depth == 0) {
                final Token end = nextSpoken(stream, token.getTokenIndex());
                return end != null && end.getType() == FrostlakeLexer.END ? end : null;
            }
        }
        return null;
    }

    /** The first END after {@code from} outside any bracket, or null. */
    private static Token firstEndAfter(final TokenStream stream, final Token from) {
        int depth = 0;
        for (Token token = nextSpoken(stream, from.getTokenIndex()); token != null && token.getType() != Token.EOF;
                token = nextSpoken(stream, token.getTokenIndex())) {
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (token.getType() == FrostlakeLexer.END && depth == 0) {
                return token;
            }
        }
        return null;
    }

    private static TokenStream filled(final TokenStream stream) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        return stream;
    }

    /** The next default-channel token after token index {@code after}, or null past the end. */
    private static Token nextSpoken(final TokenStream stream, final int after) {
        for (int i = after + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    /** The default-channel token before token index {@code before}, or null at the start. */
    private static Token previousSpoken(final TokenStream stream, final int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
