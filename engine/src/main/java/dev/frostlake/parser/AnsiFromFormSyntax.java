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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A call written in the ANSI FROM form that no name accepts — {@code SUBSTRING(b FROM 2)}, {@code TRIM(' ' FROM b)},
 * {@code EXTRACT('wks' FROM d)} with anything but a date part before the FROM — refused at the FROM, where live
 * refuses it. The grammar reads the form so the parse carries on to the FROM; nothing it matches is accepted.
 *
 * <p>Live's recovery then reads the FROM as the query's own FROM clause when the call stands in a select item of a
 * query that is a statement of its own — as the item's value, an operand of an arithmetic operator or a cast, or
 * inside parentheses — and reports the first fault that reading meets (all live-verified):
 *
 * <pre>
 *   SELECT EXTRACT('wks' FROM d) FROM t          'FROM' at 21, then ')' at 27
 *   SELECT EXTRACT('wks' FROM d + 1) FROM t      'FROM' at 21, then '+' at 28
 *   SELECT SUBSTRING(b FROM 2) FROM t            'FROM' at 19, then '2' at 24
 *   SELECT SUBSTRING(b FROM 2 FOR 1) FROM t      'FROM' at 19, then '2' at 24, then '1' at 30
 *   SELECT 1 FROM t WHERE EXTRACT('wks' FROM d)  'FROM' at 22 alone: a WHERE takes no FROM clause
 *   SELECT UPPER(EXTRACT('wks' FROM d))          'FROM' at 19 alone
 * </pre>
 *
 * <p>That reading is rebuilt on the text: everything of the select item before the FROM becomes a one-character
 * item, every position stays where it was written, and the lines after the FROM's are what a parse of that text
 * reports. When that parse stops inside the FROM's operand, live resumes at a FOR tail and refuses the token after
 * the FOR as well. Parentheses and casts around the call are given up one by one before that, so two pairs or a CAST
 * read otherwise — see {@code AnsiFromFormGroupLines}.
 *
 * <p>Inside another call's parentheses the call's own closing parenthesis closes that enclosing call instead: live
 * drops the call from its name to its operand, reads on, and reports the first fault it meets — none when that fault
 * is the very next token. Directly inside parentheses of its own, which stand in another call, the parentheses are
 * dropped with it. In a WHERE, the call's closing parenthesis closes whatever bracket holds it, except a CAST's,
 * which reads on from its AS:
 *
 * <pre>
 *   SELECT CONCAT(EXTRACT('wks' FROM d), 'x') FROM t       'FROM' at 28, then ')' at 40
 *   SELECT UPPER(EXTRACT('wks' FROM d)) FROM t             'FROM' at 27 alone
 *   SELECT CONCAT((EXTRACT('wks' FROM d)), 'x') FROM t     'FROM' at 29, then ')' at 36
 *   SELECT 1 FROM t WHERE CONCAT(EXTRACT('wks' FROM d), 'x') = 'a'   'FROM' at 43, then ',' at 50
 *   SELECT 1 FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1     'FROM' at 41 alone
 * </pre>
 *
 * <p>In a select item the operand of such a nested call is one token: a name followed by anything but the call's
 * closing parenthesis is refused at that next token, any other longer operand at the closing parenthesis, and a FOR
 * tail at the token after the FOR — each as the one line after the FROM's.
 *
 * <p>A call that is the operand of a WHERE, a HAVING or an ORDER BY itself is passed over, and the statement read on:
 * a later fault is reported, and so is a second call in the refused form —
 * {@code WHERE EXTRACT('wks' FROM d) = 1 ORDER BY a x y} is the FROM, then the x. A cast around the call under more
 * than one bracket, a CASE around it and a name that has no FROM form of its own stack lines of their own (see
 * {@code shapedLines}), and a block's query reports these lines and then the block's (see {@link BlockQueryLines}).
 */
public final class AnsiFromFormSyntax {

    private static final String EXTRACT = "EXTRACT";

    private AnsiFromFormSyntax() {
    }

    /**
     * Refuse the first call, in the order written, that uses the FROM form no name accepts.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql    the script's source text
     */
    public static void requireCallForms(final FrostlakeParser.SqlScriptContext script, final TokenStream tokens,
                                        final String sql) {
        if (script == null || tokens == null || !mayHoldFromForm(tokens)) {
            return;
        }
        ParserRuleContext firstCall = null;
        Token firstFrom = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            final Token from = refusedFrom(node);
            if (from != null && (firstFrom == null || from.getTokenIndex() < firstFrom.getTokenIndex())) {
                firstFrom = from;
                firstCall = (ParserRuleContext) node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (firstFrom != null) {
            // A block's query reports the lines of a statement of its own, then the block's reading after them.
            final List<String> block = sql == null || !standsInBlock(firstCall) ? null
                : BlockQueryLines.lines(sql, BlockReadOn.spoken(sql), firstFrom.getTokenIndex(), true);
            final List<String> lines = new ArrayList<>();
            if (block != null) {
                lines.addAll(block);
            } else {
                lines.add(SyntaxErrorListener.sentence(firstFrom));
                lines.addAll(readAsFromClause(firstCall, firstFrom, tokens, sql));
            }
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
    }

    private static boolean standsInBlock(final ParserRuleContext call) {
        for (ParserRuleContext up = call.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return true;
            }
        }
        return false;
    }

    /**
     * The lines a text reports: its syntax faults, or, when it parses, those of the first call it holds in the
     * refused form.
     *
     * @param text the text
     * @return the lines, none for a text nothing refuses
     */
    static List<String> linesOf(final String text) {
        final List<String> syntax = SyntaxErrorListener.linesReportedFor(text, true);
        if (!syntax.isEmpty()) {
            return syntax;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(stream);
        parser.removeErrorListeners();
        final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
        if (parser.getNumberOfSyntaxErrors() == 0) {
            try {
                requireCallForms(tree, stream, text);
            } catch (final SqlSyntaxException refused) {
                return refused.getSyntaxErrors();
            }
        }
        return new ArrayList<>();
    }

    /** Whether a FROM stands inside a parenthesis that follows a word, so the walk runs only where it can find one. */
    private static boolean mayHoldFromForm(final TokenStream tokens) {
        final List<Boolean> opened = new ArrayList<>();
        Token previous = null;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                opened.add(Boolean.valueOf(previous != null && previous.getType() != FrostlakeLexer.LPAREN
                    && Character.isLetter(previous.getText().charAt(0))));
            } else if (type == FrostlakeLexer.RPAREN && !opened.isEmpty()) {
                opened.remove(opened.size() - 1);
            } else if (type == FrostlakeLexer.FROM && !opened.isEmpty()
                    && Boolean.TRUE.equals(opened.get(opened.size() - 1))) {
                return true;
            }
            previous = token;
        }
        return false;
    }

    /** The FROM of a call in the refused FROM form, or null. */
    private static Token refusedFrom(final ParseTree node) {
        if (node instanceof FrostlakeParser.AnsiSubstringExprContext) {
            return ((FrostlakeParser.AnsiSubstringExprContext) node).FROM().getSymbol();
        }
        if (node instanceof FrostlakeParser.ExtractFromExprContext) {
            final FrostlakeParser.ExtractFromExprContext extract = (FrostlakeParser.ExtractFromExprContext) node;
            return EXTRACT.equals(extract.functionName().getText().toUpperCase(Locale.ROOT))
                ? null : extract.FROM().getSymbol();
        }
        return null;
    }

    /**
     * The lines live stacks after the FROM: those of the reading the call's place decides — the enclosing parentheses
     * and casts given up one by one for a call carrying a select item's value ({@code AnsiFromFormGroupLines}, the
     * query's own FROM clause where that reading does not reach), the enclosing bracket for a call nested in another
     * call or standing in a WHERE, a CAST's AS for a call a CAST holds in a WHERE, and the closing parenthesis of a
     * cast that holds an EXTRACT or a SUBSTRING inside one other call. None anywhere else.
     */
    private static List<String> readAsFromClause(final ParserRuleContext call, final Token from,
                                                 final TokenStream tokens, final String sql) {
        final List<String> stacked = new ArrayList<>();
        if (sql == null) {
            return stacked;
        }
        boolean carried = true;
        ParserRuleContext innermost = null;
        boolean callAbove = false;
        int callsAbove = 0;
        boolean inCase = false;
        ParserRuleContext place = null;
        final List<ParserRuleContext> levels = new ArrayList<>();
        for (ParserRuleContext up = call.getParent(); up != null && place == null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext || up instanceof FrostlakeParser.WhereClauseContext
                    || up instanceof FrostlakeParser.HavingClauseContext
                    || up instanceof FrostlakeParser.OrderItemContext
                        && !(up.getParent().getParent() instanceof FrostlakeParser.OverClauseContext)) {
                place = up;
            } else if (up instanceof FrostlakeParser.CaseExpressionContext
                    || up instanceof FrostlakeParser.WhenClauseContext) {
                inCase = true;
                carried = false;
            } else if (isBracket(up)) {
                if (innermost == null) {
                    innermost = up;
                } else if (up instanceof FrostlakeParser.FunctionCallExprContext) {
                    callAbove = true;
                    callsAbove++;
                }
                levels.add(up);
                carried = carried && carriesTheValue(up);
            } else if (up instanceof FrostlakeParser.FunctionArgContext
                    || up instanceof FrostlakeParser.FunctionArgListContext) {
                carried = false;
            } else if (up instanceof FrostlakeParser.ExpressionContext
                    || up instanceof FrostlakeParser.BooleanExprContext) {
                carried = carried && carriesTheValue(up);
            } else {
                return stacked;
            }
        }
        if (place == null || !isStatementQuery(place)) {
            return stacked;
        }
        if (!(place instanceof FrostlakeParser.ExprItemContext)) {
            if (inCase) {
                return stacked;
            }
            if (innermost instanceof FrostlakeParser.CastExprContext
                    || innermost instanceof FrostlakeParser.TryCastExprContext) {
                return castResyncLines(innermost, from, tokens, sql);
            }
            if (innermost == null && closesInPlace(call, tokens)) {
                // A call that is a clause's own operand: a FOR tail has its value refused, and any other call is
                // passed over and the statement read on.
                final Token afterFor = hasForTail(call) ? tokenAfter(call, firstFor(call)) : null;
                if (afterFor != null) {
                    stacked.add(SyntaxErrorListener.sentence(afterFor));
                    return stacked;
                }
                return hasForTail(call) ? stacked : clauseReadOn(call, from, sql);
            }
            return innermost == null || hasForTail(call) || !closesInPlace(call, tokens)
                    || !(place instanceof FrostlakeParser.WhereClauseContext) ? stacked
                : droppedCallLines(call, from, null, false, tokens, sql);
        }
        final List<String> shaped = shapedLines(call, levels, inCase, carried, tokens);
        if (shaped != null) {
            return shaped;
        }
        if (inCase) {
            return stacked;
        }
        if (carried) {
            final List<String> givenUp = AnsiFromFormGroupLines.lines(
                (FrostlakeParser.ExprItemContext) place, from, firstFor(call), levels, tokens, sql);
            return givenUp != null ? givenUp : fromClauseLines((FrostlakeParser.ExprItemContext) place, call, from, sql);
        }
        if ((innermost instanceof FrostlakeParser.CastExprContext
                    || innermost instanceof FrostlakeParser.TryCastExprContext)
                && callsAbove == 1 && !hasForTail(call) && hasFromFormOfItsOwn(call)
                && innermost.getStop() != null && innermost.getStop().getType() == FrostlakeLexer.RPAREN) {
            // A cast holding the call, inside one other call: the cast's closing parenthesis is refused too.
            stacked.add(SyntaxErrorListener.sentence(innermost.getStop()));
            return stacked;
        }
        final boolean inCall = innermost instanceof FrostlakeParser.FunctionCallExprContext;
        final boolean inGroupOfCall = innermost instanceof FrostlakeParser.ParenExprContext && callAbove;
        if (!inCall && !inGroupOfCall) {
            return stacked;
        }
        final Token group = inGroupOfCall ? innermost.getStart() : null;
        final List<String> operandLines = nestedOperandLines(call, from, group, inCall, tokens, sql);
        if (operandLines != null) {
            return operandLines;
        }
        final String operandFault = nestedOperandFault(call, from, tokens);
        if (operandFault != null) {
            stacked.add(operandFault);
            return stacked;
        }
        if (!closesInPlace(call, tokens)) {
            return stacked;
        }
        return droppedCallLines(call, from, inGroupOfCall ? innermost.getStart() : null, inCall, tokens, sql);
    }

    /** Whether the refused call is one of the two names the account reads a FROM form of: EXTRACT and SUBSTRING. */
    private static boolean hasFromFormOfItsOwn(final ParserRuleContext call) {
        final FrostlakeParser.FunctionNameContext name = call instanceof FrostlakeParser.AnsiSubstringExprContext
            ? ((FrostlakeParser.AnsiSubstringExprContext) call).functionName()
            : call instanceof FrostlakeParser.ExtractFromExprContext
                ? ((FrostlakeParser.ExtractFromExprContext) call).functionName() : null;
        final String word = name == null ? "" : name.getText().toUpperCase(Locale.ROOT);
        return EXTRACT.equals(word) || "SUBSTRING".equals(word) || "SUBSTR".equals(word);
    }

    /** Whether a context brackets the call: a call's parentheses, a CAST's or TRY_CAST's, or plain parentheses. */
    private static boolean isBracket(final ParserRuleContext context) {
        return context instanceof FrostlakeParser.FunctionCallExprContext
            || context instanceof FrostlakeParser.ParenExprContext
            || context instanceof FrostlakeParser.CastExprContext
            || context instanceof FrostlakeParser.TryCastExprContext;
    }

    /**
     * The lines for a call whose innermost bracket in a WHERE is a CAST or a TRY_CAST: live gives the cast's operand up
     * at the FROM and reads on from the first AS after it inside the cast's parentheses, so nothing more is reported at
     * the call, and the rest of the statement reads on as written (all live-verified):
     *
     * <pre>
     *   SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1               'FROM' at 41 alone
     *   SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT x) = 1             'FROM', then 'x' at 56
     *   SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 ORDER BY a x  'FROM', then 'x' at 72
     *   SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM (d)) AS INT) = 1             'FROM', then 'd' at 47
     *   SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM UPPER(d)) AS INT) = 1        'FROM', then 'd' at 52
     * </pre>
     *
     * <p>An operand after the FROM that opens a parenthesis at once, or right after its first word, has the token after
     * that parenthesis refused first. With no AS inside the cast's parentheses nothing more is reported.
     */
    private static List<String> castResyncLines(final ParserRuleContext cast, final Token from,
                                                final TokenStream tokens, final String sql) {
        final List<String> stacked = new ArrayList<>();
        final Token open = nextSpoken(tokens, cast.getStart().getTokenIndex());
        if (open == null || open.getType() != FrostlakeLexer.LPAREN) {
            return stacked;
        }
        final Token operand = nextSpoken(tokens, from.getTokenIndex());
        final Token second = operand == null ? null : nextSpoken(tokens, operand.getTokenIndex());
        final boolean word = operand != null && !operand.getText().isEmpty()
            && Character.isLetter(operand.getText().charAt(0));
        final Token bracket = operand != null && operand.getType() == FrostlakeLexer.LPAREN ? operand
            : word && second != null && second.getType() == FrostlakeLexer.LPAREN ? second : null;
        final Token inside = bracket == null ? null : nextSpoken(tokens, bracket.getTokenIndex());
        if (inside != null && inside.getType() != Token.EOF && inside.getType() != FrostlakeLexer.RPAREN) {
            stacked.add(SyntaxErrorListener.sentence(inside));
        }
        Token as = null;
        int depth = 0;
        for (int i = open.getTokenIndex(); i < tokens.size() && as == null; i++) {
            final Token token = tokens.get(i);
            final int type = token.getType();
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                break;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                break;
            } else if (type == FrostlakeLexer.AS && i > from.getTokenIndex()) {
                as = token;
            }
        }
        if (as == null) {
            return stacked;
        }
        final char[] text = sql.toCharArray();
        int placeholder = -1;
        for (int c = open.getStopIndex() + 1; c < as.getStartIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
                if (placeholder < 0) {
                    placeholder = c;
                }
            }
        }
        if (placeholder < 0) {
            return stacked;
        }
        text[placeholder] = '1';
        stacked.addAll(linesAfterWithForms(new String(text), from));
        return stacked;
    }

    /** The statement read on with the call passed over as a value: its faults, and a later call in the refused form. */
    private static List<String> clauseReadOn(final ParserRuleContext call, final Token from, final String sql) {
        final char[] text = sql.toCharArray();
        final int start = call.getStart().getStartIndex();
        for (int c = start; c <= call.getStop().getStopIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        return linesAfterWithForms(new String(text), from);
    }

    private static List<String> linesAfterWithForms(final String text, final Token from) {
        final List<String> stacked = new ArrayList<>();
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(from));
        for (final String line : linesOf(text)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                stacked.add(line);
            }
        }
        return stacked;
    }

    /**
     * The lines after the FROM for a call under the brackets the account was asked about, by their kinds from the
     * call outwards — C a CAST's or a TRY_CAST's parenthesis, F a call's, P a plain one — or null for any other
     * stack (all live-verified). For EXTRACT and SUBSTRING, which have a FROM form of their own:
     *
     * <pre>
     *   C F F, C P P, and C F under a CASE   the cast's AS, then its ')'
     *   C F F F                              the cast's AS, then the ')' after the cast's
     *   C P                                  the cast's ')'
     *   C under a CASE                       the call's ')', then the cast's
     *   P P                                  the inner parenthesis's ')'
     * </pre>
     *
     * <p>The stacks with a P are read so around a call whose operand is a number, with a comparison in a
     * parenthesis — {@code ((EXTRACT('wks' FROM 2)) = 1)} — or, for P P, a comma after the number; a name there
     * keeps the readings of {@code AnsiFromFormGroupLines}. The others:
     *
     * <pre>
     *   F C F                                nothing
     *   P C F                                the parenthesis's ')'
     *   P P C F, F F C F                     the second bracket's ')'
     * </pre>
     *
     * <p>For any other name, which has none: under F the call's ')' of that F, under C the cast's ')', and under
     * C F the cast's AS and its ')'. A FOR tail has the token after its FOR refused whatever stands around it.
     */
    private static List<String> shapedLines(final ParserRuleContext call, final List<ParserRuleContext> levels,
                                            final boolean inCase, final boolean carried, final TokenStream tokens) {
        final StringBuilder kinds = new StringBuilder();
        int castAt = -1;
        for (int i = 0; i < levels.size(); i++) {
            final ParserRuleContext level = levels.get(i);
            final boolean cast = level instanceof FrostlakeParser.CastExprContext
                || level instanceof FrostlakeParser.TryCastExprContext;
            if (cast && castAt < 0) {
                castAt = i;
            }
            kinds.append(cast ? 'C' : level instanceof FrostlakeParser.FunctionCallExprContext ? 'F' : 'P');
        }
        final String shape = kinds.toString();
        final List<String> lines = new ArrayList<>();
        if (hasForTail(call)) {
            final Token afterFor = shape.startsWith("C") && shape.length() > 1 ? tokenAfter(call, firstFor(call))
                : null;
            if (afterFor == null) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(afterFor));
            return lines;
        }
        final boolean own = hasFromFormOfItsOwn(call);
        final ParserRuleContext cast = castAt < 0 ? null : levels.get(castAt);
        // Parentheses that only carry the item's value are given up one by one (AnsiFromFormGroupLines): the stacks
        // with a P are those whose parentheses hold a comparison, around a call whose operand is a number.
        final int operand = numberOperand(call, tokens);
        final boolean compared = !carried && operand >= 0;
        final boolean asThenClose = own
            ? "CFF".equals(shape) || "CPP".equals(shape) && compared || "CF".equals(shape) && inCase
            : "CF".equals(shape) && !inCase;
        if (asThenClose || own && "CFFF".equals(shape) && !inCase) {
            final Token as = asOf(cast, tokens);
            final Token close = closeOf(asThenClose ? cast : levels.get(1), tokens);
            if (as == null || close == null) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(as));
            lines.add(SyntaxErrorListener.sentence(close));
            return lines;
        }
        if (inCase) {
            final Token own1 = own && "C".equals(shape) ? closeOf(call, tokens) : null;
            final Token own2 = own1 == null ? null : closeOf(cast, tokens);
            if (own2 == null) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(own1));
            lines.add(SyntaxErrorListener.sentence(own2));
            return lines;
        }
        if (own && "FCF".equals(shape)) {
            return lines;
        }
        final int closed = !own ? ("F".equals(shape) || "C".equals(shape) ? 0 : -1)
            : "CP".equals(shape) && compared || "PCF".equals(shape) ? 0
            : "PPCF".equals(shape) || "FFCF".equals(shape) ? 1
            : "PP".equals(shape) && (compared || operand == 1) ? 0 : -1;
        final Token close = closed < 0 ? null : closeOf(levels.get(closed), tokens);
        if (close == null) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(close));
        return lines;
    }

    /** The ')' that closes a bracket's own parenthesis in the text as written, or null. */
    private static Token closeOf(final ParserRuleContext bracket, final TokenStream tokens) {
        Token open = bracket.getStart();
        while (open != null && open.getType() != FrostlakeLexer.LPAREN && open.getType() != Token.EOF) {
            open = nextSpoken(tokens, open.getTokenIndex());
        }
        return open == null || open.getType() != FrostlakeLexer.LPAREN ? null
            : closingParen(tokens, open.getTokenIndex());
    }

    /** The AS written directly inside a cast's parenthesis, or null. */
    private static Token asOf(final ParserRuleContext cast, final TokenStream tokens) {
        final Token close = closeOf(cast, tokens);
        if (close == null) {
            return null;
        }
        int depth = 0;
        for (int i = cast.getStart().getTokenIndex(); i < close.getTokenIndex(); i++) {
            final Token token = tokens.get(i);
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (token.getType() == FrostlakeLexer.AS && depth == 1) {
                return token;
            }
        }
        return null;
    }

    /** Whether the call's operand, as written, is a number: 1 for one a comma follows, 0 for any other, -1 for none. */
    private static int numberOperand(final ParserRuleContext call, final TokenStream tokens) {
        final Token close = closeOf(call, tokens);
        for (int i = call.getStart().getTokenIndex(); close != null && i < close.getTokenIndex(); i++) {
            if (tokens.get(i).getType() == FrostlakeLexer.FROM) {
                final Token operand = nextSpoken(tokens, i);
                final Token after = operand == null ? null : nextSpoken(tokens, operand.getTokenIndex());
                if (operand == null || after == null || operand.getType() != FrostlakeLexer.INTEGER_LITERAL
                        && operand.getType() != FrostlakeLexer.FLOAT_LITERAL) {
                    return -1;
                }
                return after.getType() == FrostlakeLexer.COMMA ? 1 : 0;
            }
        }
        return -1;
    }

    /**
     * The reading of the FROM as the select item's query's own FROM clause: everything of the item before the FROM
     * becomes a one-character item, and a FOR tail the reading does not reach is refused after its FOR.
     */
    private static List<String> fromClauseLines(final FrostlakeParser.ExprItemContext item,
                                                final ParserRuleContext call, final Token from, final String sql) {
        final char[] text = sql.toCharArray();
        final int start = item.getStart().getStartIndex();
        for (int c = start; c < from.getStartIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        final List<String> stacked = linesAfter(new String(text), from);
        final Token forToken = firstFor(call);
        if (forToken != null && !stacked.isEmpty() && precedes(stacked.get(stacked.size() - 1), forToken)) {
            final Token afterFor = tokenAfter(call, forToken);
            if (afterFor != null) {
                stacked.add(SyntaxErrorListener.sentence(afterFor));
            }
        }
        return stacked;
    }

    /**
     * The reading that drops the call from its name to its operand, so that its closing parenthesis closes the
     * bracket around it. {@code group} is the '(' of parentheses dropped along with the call, or null; with
     * {@code quietNext} a first fault at the token right after that parenthesis is not reported, and the ones after it
     * are: {@code SELECT CAST(CONCAT(EXTRACT('wks' FROM d), 'x') AS INT) FROM t} is 'FROM', then CONCAT's own ')' at
     * 45, the comma the CAST refuses after its operand left out (live-verified).
     */
    private static List<String> droppedCallLines(final ParserRuleContext call, final Token from, final Token group,
                                                 final boolean quietNext, final TokenStream tokens,
                                                 final String sql) {
        return droppedCallLines(call, call.getStop(), from, group, quietNext, tokens, sql);
    }

    /** As {@link #droppedCallLines(ParserRuleContext, Token, Token, boolean, TokenStream, String)}, up to {@code close}. */
    private static List<String> droppedCallLines(final ParserRuleContext call, final Token close, final Token from,
                                                 final Token group, final boolean quietNext, final TokenStream tokens,
                                                 final String sql) {
        final char[] text = sql.toCharArray();
        final int start = call.getStart().getStartIndex();
        for (int c = start; c < close.getStartIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        if (group != null) {
            text[group.getStartIndex()] = ' ';
        }
        final List<String> stacked = linesAfter(new String(text), from);
        if (quietNext && !stacked.isEmpty()) {
            final Token next = nextSpoken(tokens, close.getTokenIndex());
            final int[] at = SyntaxErrorListener.sentenceCoordinates(stacked.get(0));
            final int[] nextAt = next == null ? null
                : SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(next));
            if (at != null && nextAt != null && at[0] == nextAt[0] && at[1] == nextAt[1]) {
                stacked.remove(0);
            }
        }
        return stacked;
    }

    /**
     * The lines for a nested call's operand that is bracketed or runs into a comma, or null for any other operand (all
     * live-verified):
     *
     * <pre>
     *   SELECT CONCAT(EXTRACT('wks' FROM (d)), 'x') FROM t      'FROM', then 'd' at 34, then ')' at 35
     *   SELECT CONCAT(EXTRACT('wks' FROM (d + 1)), 'x') FROM t  'FROM', then 'd' at 34
     *   SELECT CONCAT(EXTRACT('wks' FROM ((d))), 'x') FROM t    'FROM', then '(' at 34, then ')' at 36
     *   SELECT CONCAT(EXTRACT('wks' FROM (1)), 'x') FROM t      'FROM', then '1' at 34, then ')' at 42
     *   SELECT CONCAT(EXTRACT('wks' FROM d, 'x')) FROM t        'FROM', then ')' at 39
     *   SELECT CONCAT(SUBSTRING(b FROM 2, 1)) FROM t            'FROM', then ')' at 36
     * </pre>
     *
     * <p>A bracket after the FROM has its first token refused: a name together with a ')' right after it, a bracket
     * together with its own ')', anything else followed by the lines of the reading that drops the call. A name that runs
     * into a comma has the call's own closing parenthesis refused instead; any other operand that does is read as the
     * dropped call up to the comma. A FOR with no operand written is read as the dropped call up to the call's written
     * ')': SELECT CONCAT(SUBSTRING(b FROM 2 FOR), 'x') FROM t is 'FROM', then ')' at 42.
     */
    private static List<String> nestedOperandLines(final ParserRuleContext call, final Token from, final Token group,
                                                   final boolean quietNext, final TokenStream tokens,
                                                   final String sql) {
        final Token forToken = firstFor(call);
        if (forToken != null) {
            // A FOR with no operand written: the call's written ')' closes the call around it.
            final Token written = nextSpoken(tokens, forToken.getTokenIndex());
            return written == null || written.getType() != FrostlakeLexer.RPAREN ? null
                : droppedCallLines(call, written, from, group, quietNext, tokens, sql);
        }
        final Token first = nextSpoken(tokens, from.getTokenIndex());
        if (first == null || first.getType() == Token.EOF) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        if (first.getType() == FrostlakeLexer.LPAREN) {
            final Token inner = nextSpoken(tokens, first.getTokenIndex());
            if (inner == null || inner.getType() == Token.EOF || inner.getType() == FrostlakeLexer.RPAREN) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(inner));
            if (isName(inner)) {
                final Token after = nextSpoken(tokens, inner.getTokenIndex());
                if (after != null && after.getType() == FrostlakeLexer.RPAREN) {
                    lines.add(SyntaxErrorListener.sentence(after));
                }
            } else if (inner.getType() == FrostlakeLexer.LPAREN) {
                final Token close = closingParen(tokens, inner.getTokenIndex());
                if (close != null) {
                    lines.add(SyntaxErrorListener.sentence(close));
                }
            } else if (closesInPlace(call, tokens)) {
                lines.addAll(droppedCallLines(call, from, group, quietNext, tokens, sql));
            }
            return lines;
        }
        final Token comma = operandComma(tokens, first);
        if (comma == null) {
            return null;
        }
        if (isName(first) && nextSpoken(tokens, first.getTokenIndex()) == comma) {
            final Token open = nextSpoken(tokens, call.getStart().getTokenIndex());
            final Token close = open == null ? null : closingParen(tokens, open.getTokenIndex());
            if (close != null) {
                lines.add(SyntaxErrorListener.sentence(close));
            }
            return lines;
        }
        final char[] text = sql.toCharArray();
        final int start = call.getStart().getStartIndex();
        for (int c = start; c < comma.getStartIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        return linesAfter(new String(text), from);
    }

    /** The comma that ends the operand starting at {@code first} inside its call's parentheses, or null. */
    private static Token operandComma(final TokenStream tokens, final Token first) {
        int depth = 0;
        for (int i = first.getTokenIndex(); i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.COMMA) {
                return token;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        return null;
    }

    /** The ')' closing the '(' at token index {@code open} in the written text, or null. */
    private static Token closingParen(final TokenStream tokens, final int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN && --depth == 0) {
                return token;
            } else if (token.getType() == Token.EOF) {
                return null;
            }
        }
        return null;
    }

    /** Whether a token can be a name. */
    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    /**
     * The one line for a nested call's operand that is not a single token — or for a FOR tail — or null when the
     * operand is one token and the call's parenthesis closes the bracket around it.
     */
    private static String nestedOperandFault(final ParserRuleContext call, final Token from, final TokenStream tokens) {
        final Token forToken = firstFor(call);
        if (forToken != null) {
            final Token afterFor = tokenAfter(call, forToken);
            return afterFor == null ? null : SyntaxErrorListener.sentence(afterFor);
        }
        final Token close = call.getStop();
        final Token first = nextSpoken(tokens, from.getTokenIndex());
        if (first == null || close == null || first.getTokenIndex() >= close.getTokenIndex()) {
            return null;
        }
        final Token second = nextSpoken(tokens, first.getTokenIndex());
        final boolean closeInPlace = closesInPlace(call, tokens);
        if (second == null || second.getType() == Token.EOF
                || closeInPlace && second.getTokenIndex() == close.getTokenIndex()) {
            return null;
        }
        final ATN atn = FrostlakeParser._ATN;
        if (atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(first.getType())) {
            return SyntaxErrorListener.sentence(second);
        }
        return closeInPlace ? SyntaxErrorListener.sentence(close) : null;
    }

    /**
     * Whether the call's closing parenthesis is one of the text's own tokens — not one a repair of a broken text
     * supplied — so a reading can resume there.
     */
    private static boolean closesInPlace(final ParserRuleContext call, final TokenStream tokens) {
        final Token close = call.getStop();
        if (close == null || close.getType() != FrostlakeLexer.RPAREN || close.getTokenIndex() < 0
                || close.getTokenIndex() >= tokens.size()) {
            return false;
        }
        final Token written = tokens.get(close.getTokenIndex());
        return written.getType() == FrostlakeLexer.RPAREN && written.getStartIndex() == close.getStartIndex();
    }

    /** Whether the call carries a FOR tail. */
    private static boolean hasForTail(final ParserRuleContext call) {
        return firstFor(call) != null;
    }

    /** The first FOR of the call's tail, or null. */
    private static Token firstFor(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.AnsiSubstringExprContext) {
            final List<TerminalNode> fors = ((FrostlakeParser.AnsiSubstringExprContext) call).FOR();
            return fors.isEmpty() ? null : fors.get(0).getSymbol();
        }
        return null;
    }

    /** The token of the call that follows {@code token}, or null when the call has none. */
    private static Token tokenAfter(final ParserRuleContext call, final Token token) {
        final Token[] found = new Token[1];
        final boolean[] seen = new boolean[1];
        collectAfter(call, token, found, seen);
        return found[0];
    }

    /** Walk {@code node}'s terminals in order, keeping the first one after {@code token}. */
    private static void collectAfter(final ParseTree node, final Token token, final Token[] found, final boolean[] seen) {
        if (found[0] != null) {
            return;
        }
        if (node instanceof TerminalNode) {
            final Token symbol = ((TerminalNode) node).getSymbol();
            if (seen[0]) {
                found[0] = symbol;
            } else if (symbol == token) {
                seen[0] = true;
            }
            return;
        }
        for (int i = 0; i < node.getChildCount() && found[0] == null; i++) {
            collectAfter(node.getChild(i), token, found, seen);
        }
    }

    /** Whether the sentence names a place before {@code token}. */
    private static boolean precedes(final String sentence, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(sentence);
        final int[] tokenAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        return at != null && tokenAt != null && (at[0] < tokenAt[0] || at[0] == tokenAt[0] && at[1] < tokenAt[1]);
    }

    /** The lines a parse of {@code text} reports after the FROM's place. */
    private static List<String> linesAfter(final String text, final Token from) {
        final List<String> stacked = new ArrayList<>();
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(from));
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                stacked.add(line);
            }
        }
        return stacked;
    }

    /** The next default-channel token after token index {@code index}, or null. */
    private static Token nextSpoken(final TokenStream tokens, final int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /** Whether a context between the call and its select item passes the call's value on: a cast, a sign, an arithmetic
     *  operator, a concatenation, parentheses, or the boolean tier's plain value. */
    private static boolean carriesTheValue(final ParserRuleContext context) {
        return context instanceof FrostlakeParser.ValueExprContext
            || context instanceof FrostlakeParser.CastExprContext
            || context instanceof FrostlakeParser.TryCastExprContext
            || context instanceof FrostlakeParser.CastExpr2Context
            || context instanceof FrostlakeParser.UnaryExprContext
            || context instanceof FrostlakeParser.ParenExprContext
            || context instanceof FrostlakeParser.AdditiveExprContext
            || context instanceof FrostlakeParser.MultiplicativeExprContext
            || context instanceof FrostlakeParser.ConcatExprContext;
    }

    /**
     * Whether a select item or a WHERE belongs to a query that is a statement of the script's own, not a subquery or
     * a block's.
     */
    private static boolean isStatementQuery(final ParserRuleContext place) {
        FrostlakeParser.SelectStatementContext query = null;
        for (ParserRuleContext up = place.getParent(); up != null && query == null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.SelectStatementContext) {
                query = (FrostlakeParser.SelectStatementContext) up;
            }
        }
        if (query == null || !(query.getParent() instanceof FrostlakeParser.QueryStatementContext)) {
            return false;
        }
        for (ParserRuleContext up = query.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return false;
            }
        }
        return true;
    }
}
