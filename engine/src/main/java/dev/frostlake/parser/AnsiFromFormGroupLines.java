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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;

/**
 * The lines live stacks after the FROM of a call in the refused FROM form whose value a select item carries, bare or
 * inside parentheses and casts. Live refuses the FROM, then gives the enclosing constructs up from the inside out, and
 * says nothing more until it has read a token again (all live-verified; r1, r2, … are the first, second, … ')' after
 * the FROM):
 *
 * <pre>
 *   SELECT EXTRACT('wks' FROM d) FROM t                  'FROM', then r1 — the FROM read as the query's
 *   SELECT (EXTRACT('wks' FROM 2)) FROM t                'FROM', then '2'
 *   SELECT ((EXTRACT('wks' FROM 2))) FROM t              'FROM', then r2
 *   SELECT (((EXTRACT('wks' FROM 2)))) FROM t            'FROM', then r3
 *   SELECT ((EXTRACT('wks' FROM d))) FROM t              'FROM' alone
 *   SELECT (((EXTRACT('wks' FROM d)))) FROM t            'FROM', then r3
 *   SELECT ((EXTRACT('wks' FROM d x))) FROM t            'FROM', then 'x'
 *   SELECT ((EXTRACT('wks' FROM d) + 1)) FROM t          'FROM', then r2
 *   SELECT ((EXTRACT('wks' FROM d)) + 1) FROM t          'FROM' alone
 *   SELECT CAST(EXTRACT('wks' FROM 2) AS INT) FROM t     'FROM', then r2 — the CAST's own ')'
 *   SELECT CAST((EXTRACT('wks' FROM d)) AS INT) FROM t   'FROM', then r3
 * </pre>
 *
 * <p>Each construct, innermost first, meets the token the one inside it stopped at. A parenthesis group reads its ')'
 * there, or drops one token to read the ')' right after it, or — the outermost group — takes its ')' as read when the
 * token can follow the select item's value; a CAST reads its AS the same ways. An operator carries the construct's
 * value on. Any read token puts the parse back in step, and the statement reads on from there as written: a FROM goes on
 * as the query's FROM clause, a name as the item's alias. A construct that can do none of that gives up: it passes over
 * the token the construct inside it failed at, and every token after it up to one that can go on — a name, an operator,
 * AS, a comma, a clause's keyword, and a ')' while a group encloses it — so a number, a string or NULL is passed over.
 * When all constructs are given up without a token read, a fault at the very next token is not reported, and neither is
 * the rest of the statement. Back in step, a construct that cannot read the token it meets — a CAST a ')', a group AS or
 * a name — has that token refused, and is given up from there the same way without passing over the refused token.
 */
final class AnsiFromFormGroupLines {

    private AnsiFromFormGroupLines() {
    }

    /**
     * The lines after the FROM's, or null for a shape this reading does not cover: a bracket or a comma list met where a
     * construct resumes, a type name met by a CAST, or a token no measured rule places.
     *
     * @param item     the select item holding the call
     * @param from     the call's FROM
     * @param forToken the first FOR of the call's tail, or null
     * @param levels   the parentheses and casts between the call and the item, innermost first
     * @param tokens   the token stream the text was read from
     * @param sql      the text
     * @return the lines, or null
     */
    static List<String> lines(final FrostlakeParser.ExprItemContext item, final Token from, final Token forToken,
                              final List<ParserRuleContext> levels, final TokenStream tokens, final String sql) {
        final List<Token> spoken = new ArrayList<>();
        int p = -1;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                if (token.getTokenIndex() == from.getTokenIndex()) {
                    p = spoken.size();
                }
                spoken.add(token);
            }
        }
        if (p < 0) {
            return null;
        }
        return givenUpFrom(item, levels, 0, spoken, p, p, forToken, sql);
    }

    /**
     * The lines once the constructs from {@code first} out are given up one by one, the one at {@code first} meeting
     * the token at {@code start}. A construct meeting the token at {@code passedFrom}, where the one inside it stopped,
     * passes over that token before it looks for one that can go on.
     */
    private static List<String> givenUpFrom(final FrostlakeParser.ExprItemContext item,
                                            final List<ParserRuleContext> levels, final int first,
                                            final List<Token> spoken, final int start, final int passedFrom,
                                            final Token forToken, final String sql) {
        int p = start;
        int passedAt = passedFrom;
        for (int level = first; level < levels.size(); level++) {
            final boolean group = levels.get(level) instanceof FrostlakeParser.ParenExprContext;
            final boolean outermost = level == levels.size() - 1;
            final int type = spoken.get(p).getType();
            final boolean ends = type == Token.EOF || type == FrostlakeLexer.SEMI;
            if (type == FrostlakeLexer.LPAREN || group && type == FrostlakeLexer.COMMA) {
                return null;
            }
            if (isOperator(type) || !group && type == FrostlakeLexer.AS) {
                return inStep(item, levels, level, spoken, p, forToken, sql);
            }
            if (group && type == FrostlakeLexer.RPAREN) {
                return inStep(item, levels, level + 1, spoken, p + 1, forToken, sql);
            }
            final int next = ends || p + 1 >= spoken.size() ? -1 : spoken.get(p + 1).getType();
            if (group && next == FrostlakeLexer.RPAREN) {
                p += 2;
                continue;
            }
            if (!group && next == FrostlakeLexer.AS) {
                return inStep(item, levels, level, spoken, p + 1, forToken, sql);
            }
            if (!group && isTypeWord(type)) {
                return null;
            }
            if (group && outermost) {
                if (!followsTheItem(type)) {
                    return null;
                }
                break;
            }
            if (group && type == FrostlakeLexer.AS && !(levels.get(level + 1) instanceof FrostlakeParser.ParenExprContext)) {
                continue;
            }
            if (passedAt == p && !ends) {
                p++;
            }
            passedAt = p;
            final boolean enclosed = groupAbove(levels, level);
            while (!goesOn(spoken.get(p).getType(), enclosed)) {
                if (!passedOver(spoken.get(p).getType())) {
                    return null;
                }
                p++;
            }
        }
        return afterGivingUp(item, spoken, p, forToken, sql);
    }

    /**
     * The lines once the parse is back in step at {@code p}: the constructs from {@code open} out are still open, the
     * inner ones are read.
     */
    private static List<String> inStep(final FrostlakeParser.ExprItemContext item, final List<ParserRuleContext> levels,
                                       final int open, final List<Token> spoken, final int p, final Token forToken,
                                       final String sql) {
        if (open >= levels.size()) {
            return readOn(item, spoken, p, false, forToken, sql);
        }
        final List<String> refused = refusedInStep(item, levels, open, spoken, p, forToken, sql);
        if (refused != null) {
            return refused;
        }
        final Token resume = spoken.get(p);
        final char[] text = blanked(sql, item.getStart().getStartIndex(), resume.getStartIndex());
        int operand = item.getStart().getStartIndex();
        for (int level = levels.size() - 1; level >= open; level--) {
            final ParserRuleContext construct = levels.get(level);
            final Token opener = construct.getStart();
            for (int c = opener.getStartIndex(); c <= opener.getStopIndex(); c++) {
                text[c] = sql.charAt(c);
            }
            operand = opener.getStopIndex() + 1;
            if (!(construct instanceof FrostlakeParser.ParenExprContext)) {
                final Token paren = nextSpoken(spoken, opener);
                if (paren == null || paren.getType() != FrostlakeLexer.LPAREN) {
                    return null;
                }
                text[paren.getStartIndex()] = '(';
                operand = paren.getStartIndex() + 1;
            }
        }
        while (operand < resume.getStartIndex() && (text[operand] == '\n' || text[operand] == '\r')) {
            operand++;
        }
        if (operand >= resume.getStartIndex()) {
            return null;
        }
        text[operand] = '1';
        final List<String> read = linesFrom(new String(text), resume);
        if (forToken == null) {
            final List<String> refusedLater = refusedAfterReading(item, levels, open, spoken, p, read, sql);
            if (refusedLater != null) {
                return refusedLater;
            }
        }
        return withForTail(read, forToken, spoken);
    }

    /**
     * The lines when the first fault of the text read on from {@code p} is a token that one of the constructs still
     * open refuses, as {@code refusedInStep} reads it: a CAST, having read an operator and its operand, meeting a ')'
     * before its AS, or a group meeting AS or a name. {@code SELECT CAST(((EXTRACT('wks' FROM d + 1))) AS INT) FROM t}
     * is 'FROM', ')' at 38, then the CAST's own ')' at 48 (live-verified). Null when that fault stands elsewhere.
     */
    private static List<String> refusedAfterReading(final FrostlakeParser.ExprItemContext item,
                                                    final List<ParserRuleContext> levels, final int open,
                                                    final List<Token> spoken, final int p, final List<String> read,
                                                    final String sql) {
        final int fault = read.isEmpty() ? -1 : spokenIndex(spoken, read.get(0));
        if (fault <= p) {
            return null;
        }
        int level = open;
        int depth = 0;
        boolean asRead = false;
        for (int i = p; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.RPAREN) {
                level++;
                asRead = false;
                if (level >= levels.size()) {
                    return null;
                }
            } else if (type == FrostlakeLexer.AS && depth == 0) {
                asRead = true;
            }
        }
        if (depth > 0) {
            return null;
        }
        final int type = spoken.get(fault).getType();
        final boolean refused = levels.get(level) instanceof FrostlakeParser.ParenExprContext
            ? !isOperator(type) && (type == FrostlakeLexer.AS || isName(type))
            : type == FrostlakeLexer.RPAREN && !asRead;
        final List<String> after = refused ? givenUpFrom(item, levels, level, spoken, fault, -1, null, sql) : null;
        if (after == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.addAll(after);
        return lines;
    }

    /** The index among {@code spoken} of the token a reported line names, or -1. */
    private static int spokenIndex(final List<Token> spoken, final String line) {
        for (int i = 0; i < spoken.size(); i++) {
            if (samePlace(line, spoken.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The lines when a construct still open refuses the token it meets with the parse back in step: a CAST meeting a
     * ')', a parenthesis group meeting AS or a name, after the groups inside it have read the ')' that close them. Live
     * refuses that token, then gives that construct and the ones around it up from there as it does after the FROM,
     * except that the refused token is not passed over first (all live-verified):
     *
     * <pre>
     *   SELECT CAST(((EXTRACT('wks' FROM 2))) AS INT) FROM t     'FROM', ')' at 35, ')' at 44 — AS INT the alias
     *   SELECT (CAST(((EXTRACT('wks' FROM 2))) AS INT)) FROM t   'FROM', ')' at 36, ')' at 37 — the group's own ')'
     *   SELECT ((CAST(EXTRACT('wks' FROM 2) AS INT))) FROM t     'FROM', 'AS' at 36, ')' at 42
     *   SELECT ((CAST(EXTRACT('wks' FROM 2) y))) FROM t          'FROM', 'y' at 36 alone
     * </pre>
     *
     * Null for any other token, and for a shape the reading of what follows does not cover.
     */
    private static List<String> refusedInStep(final FrostlakeParser.ExprItemContext item,
                                              final List<ParserRuleContext> levels, final int open,
                                              final List<Token> spoken, final int p, final Token forToken,
                                              final String sql) {
        int level = open;
        int at = p;
        while (level < levels.size() && levels.get(level) instanceof FrostlakeParser.ParenExprContext
                && spoken.get(at).getType() == FrostlakeLexer.RPAREN) {
            level++;
            at++;
        }
        if (level >= levels.size()) {
            return null;
        }
        final int type = spoken.get(at).getType();
        final boolean refused = levels.get(level) instanceof FrostlakeParser.ParenExprContext
            ? !isOperator(type) && (type == FrostlakeLexer.AS || isName(type))
            : type == FrostlakeLexer.RPAREN;
        if (!refused) {
            return null;
        }
        final List<String> after = givenUpFrom(item, levels, level, spoken, at, -1, forToken, sql);
        if (after == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
        lines.addAll(after);
        return lines;
    }

    /**
     * The lines once every construct is given up and the item goes on at {@code p} with no token read. A name there is
     * read as the item's alias, so the token after it is refused unless it ends the item; anything else reads on, and a
     * fault right at {@code p} is not reported.
     */
    private static List<String> afterGivingUp(final FrostlakeParser.ExprItemContext item, final List<Token> spoken,
                                              final int p, final Token forToken, final String sql) {
        final int type = spoken.get(p).getType();
        if (!endsTheItem(type) && type != FrostlakeLexer.AS && isName(type) && p + 1 < spoken.size()) {
            if (endsTheItem(spoken.get(p + 1).getType())) {
                return readOn(item, spoken, p + 1, false, forToken, sql);
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(p + 1)));
            lines.addAll(laterStatements(item, spoken, p + 1, sql));
            return lines;
        }
        return readOn(item, spoken, p, true, forToken, sql);
    }

    /**
     * The lines of the item read on at {@code p} after its value, as the statement reads on from there. With
     * {@code quietHere} a first fault at {@code p} itself is not reported, nor is anything else in the statement: live's
     * recovery passes over the rest of it. A FOR of the call's tail is read and the token after it refused.
     */
    private static List<String> readOn(final FrostlakeParser.ExprItemContext item, final List<Token> spoken, final int p,
                                       final boolean quietHere, final Token forToken, final String sql) {
        final Token resume = spoken.get(p);
        if (forToken != null && resume.getType() == FrostlakeLexer.FOR && p + 1 < spoken.size()) {
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(p + 1)));
            lines.addAll(laterStatements(item, spoken, p, sql));
            return lines;
        }
        final List<String> all = linesFrom(itemRead(item, resume, sql), resume);
        if (all.isEmpty() || !quietHere || !samePlace(all.get(0), resume)) {
            return withForTail(all, forToken, spoken);
        }
        final List<String> later = new ArrayList<>();
        final Token end = statementEnd(spoken, p);
        for (final String line : all) {
            if (end != null && after(line, end)) {
                later.add(line);
            }
        }
        return later;
    }

    /** The lines of the statements after the one {@code p} stands in, read with the item's value in place. */
    private static List<String> laterStatements(final FrostlakeParser.ExprItemContext item, final List<Token> spoken,
                                                final int p, final String sql) {
        final List<String> later = new ArrayList<>();
        final Token end = statementEnd(spoken, p);
        if (end == null) {
            return later;
        }
        for (final String line : linesFrom(itemRead(item, spoken.get(p), sql), spoken.get(p))) {
            if (after(line, end)) {
                later.add(line);
            }
        }
        return later;
    }

    /** The text with the item from its start up to {@code resume} replaced by a one-character value. */
    private static String itemRead(final FrostlakeParser.ExprItemContext item, final Token resume, final String sql) {
        final int start = item.getStart().getStartIndex();
        final char[] text = blanked(sql, start, resume.getStartIndex());
        if (start < resume.getStartIndex()) {
            text[start] = '1';
        }
        return new String(text);
    }

    /**
     * The lines with the call's FOR tail read as live reads it: a line at that FOR is the token after it instead, and
     * a tail the lines stop short of is refused after its FOR.
     */
    private static List<String> withForTail(final List<String> lines, final Token forToken, final List<Token> spoken) {
        if (forToken == null) {
            return lines;
        }
        Token afterFor = null;
        for (int i = 0; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == forToken.getTokenIndex()) {
                afterFor = spoken.get(i + 1);
            }
        }
        if (afterFor == null) {
            return lines;
        }
        if (!lines.isEmpty() && samePlace(lines.get(0), forToken)) {
            final List<String> atFor = new ArrayList<>();
            atFor.add(SyntaxErrorListener.sentence(afterFor));
            return atFor;
        }
        if (!lines.isEmpty() && before(lines.get(lines.size() - 1), forToken)) {
            final List<String> withTail = new ArrayList<>(lines);
            withTail.add(SyntaxErrorListener.sentence(afterFor));
            return withTail;
        }
        return lines;
    }

    /** The text with the characters from {@code start} up to {@code end} blanked, line breaks kept. */
    private static char[] blanked(final String sql, final int start, final int end) {
        final char[] text = sql.toCharArray();
        for (int c = start; c < end && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        return text;
    }

    /** The lines a parse of {@code text} reports at {@code resume} or after it. */
    private static List<String> linesFrom(final String text, final Token resume) {
        final List<String> lines = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedFor(text, true)) {
            if (samePlace(line, resume) || after(line, resume)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The token ending the statement that {@code p} stands in: its semicolon, or null at the end of the input. */
    private static Token statementEnd(final List<Token> spoken, final int p) {
        for (int i = p; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return spoken.get(i);
            }
        }
        return null;
    }

    private static Token nextSpoken(final List<Token> spoken, final Token token) {
        for (int i = 0; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == token.getTokenIndex()) {
                return spoken.get(i + 1);
            }
        }
        return null;
    }

    private static boolean samePlace(final String line, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        final int[] tokenAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        return at != null && tokenAt != null && at[0] == tokenAt[0] && at[1] == tokenAt[1];
    }

    private static boolean after(final String line, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        final int[] tokenAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        return at != null && tokenAt != null && (at[0] > tokenAt[0] || at[0] == tokenAt[0] && at[1] > tokenAt[1]);
    }

    private static boolean before(final String line, final Token token) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        final int[] tokenAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
        return at != null && tokenAt != null && (at[0] < tokenAt[0] || at[0] == tokenAt[0] && at[1] < tokenAt[1]);
    }

    /** Whether a parenthesis group encloses the construct at {@code level}. */
    private static boolean groupAbove(final List<ParserRuleContext> levels, final int level) {
        for (int above = level + 1; above < levels.size(); above++) {
            if (levels.get(above) instanceof FrostlakeParser.ParenExprContext) {
                return true;
            }
        }
        return false;
    }

    /** Whether an operator of this type carries a value on. */
    private static boolean isOperator(final int type) {
        switch (type) {
            case FrostlakeLexer.PLUS:
            case FrostlakeLexer.MINUS:
            case FrostlakeLexer.STAR:
            case FrostlakeLexer.SLASH:
            case FrostlakeLexer.PERCENT:
            case FrostlakeLexer.PIPE_PIPE:
            case FrostlakeLexer.DOUBLE_COLON:
            case FrostlakeLexer.EQ:
            case FrostlakeLexer.NEQ:
            case FrostlakeLexer.LT:
            case FrostlakeLexer.LTE:
            case FrostlakeLexer.GT:
            case FrostlakeLexer.GTE:
            case FrostlakeLexer.AND:
            case FrostlakeLexer.OR:
            case FrostlakeLexer.NOT:
            case FrostlakeLexer.IS:
            case FrostlakeLexer.IN:
            case FrostlakeLexer.LIKE:
            case FrostlakeLexer.ILIKE:
            case FrostlakeLexer.RLIKE:
            case FrostlakeLexer.REGEXP:
            case FrostlakeLexer.BETWEEN:
                return true;
            default:
                return false;
        }
    }

    /** Whether a construct given up stops passing over tokens at one of this type. */
    private static boolean goesOn(final int type, final boolean enclosed) {
        if (type == FrostlakeLexer.RPAREN) {
            return enclosed;
        }
        switch (type) {
            case Token.EOF:
            case FrostlakeLexer.SEMI:
            case FrostlakeLexer.COMMA:
            case FrostlakeLexer.TRUE:
            case FrostlakeLexer.FALSE:
            case FrostlakeLexer.CASE:
            case FrostlakeLexer.POSITIONAL_PARAMETER:
                return true;
            default:
                return isOperator(type) || followsTheItem(type);
        }
    }

    /** Whether a construct given up passes over a token of this type. */
    private static boolean passedOver(final int type) {
        switch (type) {
            case FrostlakeLexer.INTEGER_LITERAL:
            case FrostlakeLexer.FLOAT_LITERAL:
            case FrostlakeLexer.STRING_LITERAL:
            case FrostlakeLexer.NULL:
            case FrostlakeLexer.SELECT:
            case FrostlakeLexer.DOT:
            case FrostlakeLexer.RPAREN:
                return true;
            default:
                return false;
        }
    }

    /** Whether a token of this type can follow a select item's value: an alias, AS, or what ends the select list. */
    private static boolean followsTheItem(final int type) {
        switch (type) {
            case Token.EOF:
            case FrostlakeLexer.SEMI:
            case FrostlakeLexer.AS:
            case FrostlakeLexer.QUOTED_IDENTIFIER:
            case FrostlakeLexer.FROM:
            case FrostlakeLexer.WHERE:
            case FrostlakeLexer.GROUP:
            case FrostlakeLexer.HAVING:
            case FrostlakeLexer.QUALIFY:
            case FrostlakeLexer.ORDER:
            case FrostlakeLexer.LIMIT:
            case FrostlakeLexer.UNION:
            case FrostlakeLexer.EXCEPT:
            case FrostlakeLexer.MINUS_KW:
            case FrostlakeLexer.INTERSECT:
            case FrostlakeLexer.FOR:
                return true;
            default:
                return isName(type);
        }
    }

    /** Whether a token of this type ends a select item after its alias: the list goes on, or a clause begins. */
    private static boolean endsTheItem(final int type) {
        switch (type) {
            case Token.EOF:
            case FrostlakeLexer.SEMI:
            case FrostlakeLexer.COMMA:
            case FrostlakeLexer.FROM:
            case FrostlakeLexer.WHERE:
            case FrostlakeLexer.GROUP:
            case FrostlakeLexer.HAVING:
            case FrostlakeLexer.QUALIFY:
            case FrostlakeLexer.ORDER:
            case FrostlakeLexer.LIMIT:
            case FrostlakeLexer.UNION:
            case FrostlakeLexer.EXCEPT:
            case FrostlakeLexer.MINUS_KW:
            case FrostlakeLexer.INTERSECT:
            case FrostlakeLexer.FOR:
                return true;
            default:
                return false;
        }
    }

    /** Whether a token of this type can be a name. */
    private static boolean isName(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(type);
    }

    /** Whether a token of this type can begin a data type's name. */
    private static boolean isTypeWord(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_dataTypeName]).contains(type);
    }
}
