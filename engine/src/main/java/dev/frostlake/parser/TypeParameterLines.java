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
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Where a type's parameter list is refused. The list is judged as soon as its opening parenthesis is read, by what
 * its type takes (see {@link TypeParameterFamily}) and by where the type stands — the token live names differs
 * between a {@code ::} cast, a CAST and everything else (all live-verified):
 *
 * <pre>
 *   SELECT 1::NUMBER()                 ')'                      the token that cannot continue the list
 *   SELECT 1::INT(5,2)                 '5', then ')'            and the closing ')' when more stood before it
 *   SELECT '1'::VARCHAR(5,2)           ',', then ')'
 *   SELECT '1'::VARCHAR(5,)            ',' alone
 *   SELECT CAST(1 AS NUMBER())         '(' alone                a list that cannot begin is refused at its '('
 *   SELECT CAST(1 AS INT(5,2))         '(', then '5', then ')'
 *   SELECT CAST(1 AS VARCHAR(5,2))     ',', then ')'            a list that begins is refused as after '::'
 *   CREATE TABLE t (a NUMBER())        '(' alone                and nothing more, here and in every other place
 *   CREATE TABLE t (a VARCHAR(5,))     ',' alone
 * </pre>
 *
 * <p>A list refused at its own closing parenthesis after {@code ::} leaves the rest of the statement to be read
 * and reported; every other refusal ends the statement's report.
 *
 * <p>An ARRAY and an OBJECT judge the first token inside their parentheses themselves, and a cast names that token
 * twice: {@code NULL::ARRAY(5)} is '5' and '5' again, {@code CAST(x AS OBJECT(5,2))} '5', '5' and then ')' — but an
 * empty pair inside a CAST once.
 *
 * <p>In a list of definitions — a table's columns, a routine's parameters, an OBJECT's fields — a fault that one
 * token more or less does not mend makes the account read the list on: the comma is taken for the one between two
 * definitions, so the number after it is refused as no name, and the first ')' met ends the list, so that what
 * follows it is refused too:
 *
 * <pre>
 *   CREATE TABLE t (a VARCHAR(5,2))            ',', '2', then the list's own ')'
 *   CREATE TABLE t (a VARCHAR(5,2), b INT)     ',', '2', then the ',' after the type
 *   CREATE TABLE t (a VARCHAR(5,2,3))          ',', '2', '3', then ')'
 *   CREATE TABLE t (a ARRAY(5))                '5', then ')'
 *   CREATE TABLE t (a ARRAY() NOT NULL)        ')', then 'NOT'
 *   ALTER TABLE t ADD COLUMN a VARCHAR(5,2)    ',' and '2': no list stands open around the column
 *   CREATE TABLE t (a VARCHAR(5,))             ',' alone: without it the list reads
 * </pre>
 */
public final class TypeParameterLines {

    private TypeParameterLines() {
    }

    /**
     * The refusal of a parameter list, judged when its opening parenthesis has just been read.
     *
     * @param list   the list, holding its opening parenthesis
     * @param tokens the statement's tokens, standing at the first token inside the parentheses
     * @return the refusal, or null when the type takes the list
     */
    static TypeParameterRefusal refusal(final FrostlakeParser.TypeParametersContext list, final TokenStream tokens) {
        final Token open = list.getStart();
        final List<Token> written = new ArrayList<>();
        int ahead = 1;
        Token next = tokens.LT(ahead);
        while (next.getType() == FrostlakeParser.MINUS || next.getType() == FrostlakeParser.INTEGER_LITERAL
                || next.getType() == FrostlakeParser.COMMA) {
            written.add(next);
            ahead++;
            next = tokens.LT(ahead);
        }
        final Token close = next.getType() == FrostlakeParser.RPAREN ? next : null;
        final FrostlakeParser.DataTypeNameContext type = typeBefore(list);
        final boolean structured = structured(type);
        final boolean afterColons = list.getParent() instanceof FrostlakeParser.CastExpr2Context;
        final TypeParameterRefusal refusal = refusal(open, written, close, TypeParameterFamily.of(type), structured,
            afterColons, inCast(list));
        if (refusal == null || !inDefinitionList(list)) {
            return refusal;
        }
        final List<Token> read = readOn(refusal, close, structured, tokens,
            list.getParent() instanceof FrostlakeParser.AlterColumnItemActionContext || underAlter(list));
        return read == null ? refusal : new TypeParameterRefusal(read, true);
    }

    /** How far ahead a list of definitions is read on after a fault in one of its types. */
    private static final int READ_AHEAD = 400;

    /**
     * The lines of a fault in a type of a list of definitions, read on as the account reads the list on, or null
     * when the fault is one the list is not read on after: one token more or less would mend it.
     *
     * @param refusal    the refusal as a type standing alone earns it
     * @param close      the type list's closing parenthesis when the list is numbers, signs and commas to its end
     * @param structured whether the type is an ARRAY or an OBJECT
     * @param tokens     the statement's tokens, standing at the first token inside the parentheses
     * @param unlisted   whether the definition stands in no parentheses of its own list, as ALTER TABLE's do
     * @return the lines, or null
     */
    private static List<Token> readOn(final TypeParameterRefusal refusal, final Token close, final boolean structured,
                                      final TokenStream tokens, final boolean unlisted) {
        final Token fault = refusal.line(0);
        final List<Token> ahead = new ArrayList<>();
        for (int k = 1; k <= READ_AHEAD; k++) {
            final Token token = tokens.LT(k);
            ahead.add(token);
            if (token.getType() == Token.EOF) {
                break;
            }
        }
        int at = ahead.indexOf(fault);
        if (at < 0) {
            return null;
        }
        final boolean comma = fault.getType() == FrostlakeParser.COMMA;
        if (!structured && (!comma || ahead.get(at + 1).getType() == FrostlakeParser.RPAREN)) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(fault);
        int end = fault == close ? at : -1;
        if (comma) {
            // The comma is read as the one between two definitions, and what follows it is no name.
            at++;
            lines.add(ahead.get(at));
            if (ahead.get(at).getType() == Token.EOF) {
                return lines;
            }
        }
        while (end < 0) {
            at++;
            if (at >= ahead.size()) {
                return lines;
            }
            final int kind = ahead.get(at).getType();
            if (kind == FrostlakeParser.RPAREN) {
                end = at;
            } else if (kind == Token.EOF) {
                return lines;
            } else if (kind == FrostlakeParser.COMMA && at + 1 < ahead.size()
                    && ahead.get(at + 1).getType() == FrostlakeParser.INTEGER_LITERAL) {
                at++;
                lines.add(ahead.get(at));
            } else if (kind == FrostlakeParser.COMMA) {
                return lines;
            } else {
                // Anything else is passed over up to the first ')', which ends the list.
                while (at < ahead.size() && ahead.get(at).getType() != FrostlakeParser.RPAREN
                        && ahead.get(at).getType() != Token.EOF) {
                    at++;
                }
                if (at >= ahead.size() || ahead.get(at).getType() == Token.EOF) {
                    return lines;
                }
                end = at;
            }
        }
        if (!unlisted && end + 1 < ahead.size()) {
            // The list is over, and what stands after it is no part of the statement there.
            final int after = ahead.get(end + 1).getType();
            if (after == FrostlakeParser.RPAREN || after == FrostlakeParser.COMMA || after == FrostlakeParser.NOT) {
                lines.add(ahead.get(end + 1));
            }
        }
        return lines;
    }

    /**
     * Whether a list belongs to a definition of a list of them: a column, a RETURNS TABLE column, a parameter, an
     * OBJECT's field.
     */
    private static boolean inDefinitionList(final FrostlakeParser.TypeParametersContext list) {
        final ParserRuleContext parent = list.getParent();
        return parent instanceof FrostlakeParser.ColumnDefContext
            || parent instanceof FrostlakeParser.ReturnColumnContext
            || parent instanceof FrostlakeParser.ParameterDefContext
            || parent instanceof FrostlakeParser.StructuredFieldContext
            || parent instanceof FrostlakeParser.AlterColumnItemActionContext;
    }

    /** Whether a list's definition is an ALTER statement's, which opens no list of its own around it. */
    private static boolean underAlter(final FrostlakeParser.TypeParametersContext list) {
        for (ParserRuleContext context = list.getParent(); context != null; context = context.getParent()) {
            if (context instanceof FrostlakeParser.AlterStatementContext) {
                return true;
            }
            if (context instanceof FrostlakeParser.CreateStatementContext) {
                return false;
            }
        }
        return false;
    }

    /**
     * The refusal of a parameter list a text stops in, or just after: the parse gives up at the end of input before
     * it reaches the list, so the list is found from the tokens — after {@code ::}, or after the AS of a CAST the
     * text leaves open — and judged as it would have been (live-verified: {@code SELECT NULL::INT(5} is refused at
     * the 5 alone, and a function body {@code x::NUMBER(}, closed by the parenthesis the body is read in, at that
     * parenthesis and then at the end of input).
     *
     * @param tokens the text's tokens
     * @param end    the end-of-input token
     * @return the refusal, or null when the text ends in no list its type refuses
     */
    static TypeParameterRefusal unfinished(final TokenStream tokens, final Token end) {
        final int open = trailingListOpen(tokens, end);
        if (open < 0) {
            return null;
        }
        final List<Token> written = new ArrayList<>();
        Token close = null;
        for (int i = open + 1; i < end.getTokenIndex(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeParser.RPAREN) {
                close = token;
            } else {
                written.add(token);
            }
        }
        final int name = typeNameStart(tokens, open);
        final int word = tokens.get(name).getType();
        final boolean afterColons = spokenBefore(tokens, name) >= 0
            && tokens.get(spokenBefore(tokens, name)).getType() == FrostlakeParser.DOUBLE_COLON;
        final boolean structured = word == FrostlakeParser.ARRAY || word == FrostlakeParser.OBJECT;
        if (structured && written.isEmpty() && close == null) {
            // The text stops where an ARRAY's element type or an OBJECT's first field stands: its end is that
            // first token, named twice as any other is.
            final List<Token> lines = new ArrayList<>();
            lines.add(end);
            lines.add(end);
            return new TypeParameterRefusal(lines, true);
        }
        return refusal(tokens.get(open), written, close, TypeParameterFamily.ofWord(word), structured, afterColons,
            !afterColons);
    }

    /**
     * The token a text that stops in or just after a type's parameter list is refused at first, for a parse that
     * reports one token alone (see {@link #unfinished}).
     *
     * @param tokens the text's tokens
     * @param end    the end-of-input token
     * @return the token, or null when the text ends in no list its type refuses
     */
    public static Token unfinishedAt(final TokenStream tokens, final Token end) {
        final TypeParameterRefusal refusal = unfinished(tokens, end);
        return refusal == null ? null : refusal.line(0);
    }

    /** Whether a text stops inside the parentheses of a type written after {@code ::}, however far it got. */
    static boolean stopsInColonCastList(final TokenStream tokens, final Token end) {
        final int open = trailingListOpen(tokens, end);
        if (open < 0) {
            return false;
        }
        final int before = spokenBefore(tokens, typeNameStart(tokens, open));
        return before >= 0 && tokens.get(before).getType() == FrostlakeParser.DOUBLE_COLON;
    }

    /**
     * The index of the opening parenthesis of a type's parameter list that the text ends in or just after — numbers,
     * signs and commas up to the end, with or without the closing parenthesis — where the type stands after
     * {@code ::} or after the AS of a CAST still open; -1 when the text ends otherwise.
     */
    private static int trailingListOpen(final TokenStream tokens, final Token end) {
        int at = spokenBefore(tokens, end.getTokenIndex());
        if (at >= 0 && tokens.get(at).getType() == FrostlakeParser.RPAREN) {
            at = spokenBefore(tokens, at);
        }
        while (at >= 0 && (tokens.get(at).getType() == FrostlakeParser.MINUS
                || tokens.get(at).getType() == FrostlakeParser.INTEGER_LITERAL
                || tokens.get(at).getType() == FrostlakeParser.COMMA)) {
            at = spokenBefore(tokens, at);
        }
        if (at < 0 || tokens.get(at).getType() != FrostlakeParser.LPAREN) {
            return -1;
        }
        final int name = typeNameStart(tokens, at);
        if (name < 0 || TypeParameterFamily.ofWord(tokens.get(name).getType()) == TypeParameterFamily.UNJUDGED) {
            return -1;
        }
        final int before = spokenBefore(tokens, name);
        if (before < 0) {
            return -1;
        }
        final int lead = tokens.get(before).getType();
        return lead == FrostlakeParser.DOUBLE_COLON || lead == FrostlakeParser.AS && insideOpenCast(tokens, before)
            ? at : -1;
    }

    /** The index of the first word of the type name that ends just before a parenthesis: two words for some. */
    private static int typeNameStart(final TokenStream tokens, final int open) {
        final int last = spokenBefore(tokens, open);
        if (last < 0) {
            return -1;
        }
        final int word = tokens.get(last).getType();
        final int first = spokenBefore(tokens, last);
        if (first >= 0 && (word == FrostlakeParser.PRECISION && tokens.get(first).getType() == FrostlakeParser.DOUBLE
                || word == FrostlakeParser.VARYING && (tokens.get(first).getType() == FrostlakeParser.CHAR
                    || tokens.get(first).getType() == FrostlakeParser.CHARACTER
                    || tokens.get(first).getType() == FrostlakeParser.NCHAR))) {
            return first;
        }
        return last;
    }

    /** Whether an AS stands directly inside the parentheses of a CAST or a TRY_CAST nothing has closed. */
    private static boolean insideOpenCast(final TokenStream tokens, final int as) {
        int depth = 0;
        for (int i = spokenBefore(tokens, as); i >= 0; i = spokenBefore(tokens, i)) {
            final int kind = tokens.get(i).getType();
            if (kind == FrostlakeParser.RPAREN) {
                depth++;
            } else if (kind == FrostlakeParser.LPAREN) {
                if (depth == 0) {
                    final int word = spokenBefore(tokens, i);
                    return word >= 0 && (tokens.get(word).getType() == FrostlakeParser.CAST
                        || tokens.get(word).getType() == FrostlakeParser.TRY_CAST);
                }
                depth--;
            }
        }
        return false;
    }

    /** The index of the default-channel token before an index, or -1. */
    private static int spokenBefore(final TokenStream tokens, final int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The lines for a list, given what it holds and where its type stands.
     *
     * @param open        the list's opening parenthesis
     * @param written     the numbers, signs and commas after it
     * @param close       its closing parenthesis, or null when the list is not closed
     * @param family      what the type takes
     * @param structured  whether the type is an ARRAY or an OBJECT
     * @param afterColons whether the type stands after {@code ::}
     * @param inCast      whether the type is the target of a CAST or a TRY_CAST
     * @return the refusal, or null when the type takes the list
     */
    private static TypeParameterRefusal refusal(final Token open, final List<Token> written, final Token close,
                                                final TypeParameterFamily family, final boolean structured,
                                                final boolean afterColons, final boolean inCast) {
        final Token fault = firstFault(written, close, family);
        if (fault == null) {
            return null;
        }
        final boolean cannotBegin = written.isEmpty() ? fault == close : fault == written.get(0);
        // The closing parenthesis is named once more when something stood between the fault and it.
        final boolean closeNamed = close != null && fault != close && written.get(written.size() - 1) != fault;
        final List<Token> lines = new ArrayList<>();
        if (afterColons || structured || inCast && !cannotBegin) {
            lines.add(fault);
            if (structured && cannotBegin && (afterColons || inCast && fault != close)) {
                // The first token inside an ARRAY's or an OBJECT's parentheses is named twice by a cast; an
                // empty pair inside a CAST once.
                lines.add(fault);
            }
            if (closeNamed) {
                lines.add(close);
            }
            return new TypeParameterRefusal(lines, fault != close);
        }
        if (!cannotBegin) {
            lines.add(fault);
            return new TypeParameterRefusal(lines, fault != close);
        }
        lines.add(open);
        if (inCast) {
            if (fault != close) {
                lines.add(fault);
            }
            if (closeNamed) {
                lines.add(close);
            }
        }
        return new TypeParameterRefusal(lines, true);
    }

    /**
     * The first token the type's list cannot hold where it stands: a number past the last the type takes, a comma
     * with nothing before it or too many before it, a closing parenthesis reached too early, or — for a type that
     * takes no list — whatever follows the opening parenthesis.
     *
     * @return the token, or null when the list is one the type takes, or is not closed and may still become one
     */
    private static Token firstFault(final List<Token> written, final Token close, final TypeParameterFamily family) {
        if (family.refusesItsParentheses()) {
            return written.isEmpty() ? close : written.get(0);
        }
        int count = 0;
        boolean wantsNumber = true;
        int at = 0;
        while (at < written.size()) {
            final Token token = written.get(at);
            final int kind = token.getType();
            if (wantsNumber) {
                final boolean signed = kind == FrostlakeParser.MINUS && at + 1 < written.size()
                    && written.get(at + 1).getType() == FrostlakeParser.INTEGER_LITERAL;
                if (kind != FrostlakeParser.INTEGER_LITERAL && !signed) {
                    if (kind == FrostlakeParser.MINUS) {
                        return at + 1 < written.size() ? written.get(at + 1) : close;
                    }
                    return token;
                }
                at += signed ? 2 : 1;
                count++;
                wantsNumber = false;
            } else if (kind == FrostlakeParser.COMMA && count < family.most()) {
                wantsNumber = true;
                at++;
            } else {
                return token;
            }
        }
        if (close == null) {
            return null;
        }
        return count < family.fewest() || count > 0 && wantsNumber ? close : null;
    }

    /** Whether the type is an ARRAY or an OBJECT, whose parentheses hold an element type or fields. */
    private static boolean structured(final FrostlakeParser.DataTypeNameContext type) {
        return type != null && (type.getStart().getType() == FrostlakeParser.ARRAY
            || type.getStart().getType() == FrostlakeParser.OBJECT);
    }

    /** Whether the list's type is the target of a CAST or a TRY_CAST. */
    private static boolean inCast(final FrostlakeParser.TypeParametersContext list) {
        return list.getParent() instanceof FrostlakeParser.CastExprContext
            || list.getParent() instanceof FrostlakeParser.TryCastExprContext;
    }

    /** The type name a list follows: the node before it under the same parent, when that is a type's name. */
    private static FrostlakeParser.DataTypeNameContext typeBefore(final FrostlakeParser.TypeParametersContext list) {
        final ParserRuleContext parent = list.getParent();
        if (parent == null) {
            return null;
        }
        ParseTree before = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            final ParseTree child = parent.getChild(i);
            if (child == list) {
                break;
            }
            before = child;
        }
        return before instanceof FrostlakeParser.DataTypeNameContext
            ? (FrostlakeParser.DataTypeNameContext) before : null;
    }
}
