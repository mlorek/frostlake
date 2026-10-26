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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * How a statement reads once the words that name no type are taken as types. The account's grammar takes any name
 * where a type belongs — dotted, quoted, and a single bare word with parameters after it — and judges the names
 * when the statement has parsed, so a statement with such a word is refused for its syntax first, wherever the
 * fault stands, and for the word only when the rest of it parses (live-verified):
 *
 * <pre>
 *   CREATE TABLE t (n nosuch, m other)        Unsupported data type 'NOSUCH'. — a word this grammar keeps as a
 *                                             keyword but reads as a name, OTHER, is a word like any other
 *   CREATE TABLE t (n nosuch,)                syntax error ... unexpected ')'.
 *   CREATE TABLE t (n x.nosuch(5))            syntax error ... unexpected '('.
 *   SELECT CAST(1 AS "nosuch"(5))             syntax error ... unexpected '('. and ... unexpected '5'.
 * </pre>
 *
 * Each word is replaced by a known type of its own length, so every other character keeps its place, and the
 * text is read again until it parses or faults at something that is no such word.
 */
public final class UnknownTypeWords {

    /** More words than any statement of the kind carries; the reading gives up past it. */
    private static final int MOST_WORDS = 32;
    /** The known type a word is replaced by, and the shortest region that holds it. */
    private static final String STAND_IN = "INT";
    /** Known types that take no parameters, which stand in for a dotted or quoted name written with some. */
    private static final String[] PARAMETERLESS = {"BOOLEAN", "DATE"};

    private final UnknownTypeReading reading;
    private final List<String> lines;

    private UnknownTypeWords(final UnknownTypeReading reading, final List<String> lines) {
        this.reading = reading;
        this.lines = lines;
    }

    /** The statement read with its words as types, when it then parses cleanly; null otherwise. */
    UnknownTypeReading reading() {
        return reading;
    }

    /** The lines the statement is refused in for the syntax left once its words are types; null when it has none. */
    List<String> syntaxLines() {
        return lines;
    }

    /**
     * Reads a CREATE TASK whose body is a block, each word in it that stands where a type does — a LET's variable's
     * among them — replaced by a known type of its own length: the block's parse faults past such a word, at the
     * block's END, so the words are found by their places rather than by a fault.
     *
     * @param sql the statement's text
     * @return the reading, or null when the statement is no CREATE TASK or no such word makes it parse
     */
    public static UnknownTypeReading readTaskBlock(final String sql) {
        final List<Token> all = tokensOf(sql);
        if (!createsATask(all)) {
            return null;
        }
        for (int i = 0; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && SyntaxErrorListener.isIdentifierToken(token)
                    && standsWhereATypeDoes(all, i)) {
                final UnknownTypeWords read = read(sql, token.getStartIndex());
                if (read != null && read.reading() != null) {
                    return read.reading();
                }
            }
        }
        return null;
    }

    /**
     * Reads a block - an EXECUTE IMMEDIATE's, a procedure's body, one run on its own - with each word in it that
     * stands where a type does replaced by a known type of its own length. The account parses a block whole before
     * any of it runs, so a block that parses once its words are types is refused for the first of them wherever it
     * stands, handler or no handler: {@code BEGIN IF (c) THEN CREATE TABLE t (a nosuch); END IF; RETURN 1; END} is
     * {@code Unsupported data type 'NOSUCH'.}, while a block with a fault of its own is refused for that fault (all
     * live-verified). The words are read from the parse's first fault, or by their places where the parse faults
     * elsewhere: at a query a bracket opens, or past them.
     *
     * @param sql the block's text
     * @return the reading, or null when the text parses as written or no such word makes it parse
     */
    public static UnknownTypeReading readBlock(final String sql) {
        final int firstFault = SyntaxErrorListener.firstFaultStartOf(sql);
        if (firstFault < 0) {
            return null;
        }
        final UnknownTypeWords atFault = read(sql, firstFault);
        if (atFault != null && atFault.reading() != null) {
            return atFault.reading();
        }
        final List<Token> all = tokensOf(sql);
        for (int i = 0; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getStartIndex() != firstFault && isWord(token)
                    && standsWhereATypeDoes(all, i) && !namesAKnownType(token.getText())) {
                final UnknownTypeWords read = read(sql, token.getStartIndex());
                if (read != null && read.reading() != null) {
                    return read.reading();
                }
            }
        }
        return null;
    }

    /**
     * The refusal of a block {@link #readBlock} reads: the sentence its first word earns, as the statement on its own
     * is refused in.
     *
     * @param sql the block's text
     * @return the refusal, or null when the block is refused for no such word
     */
    public static SqlSyntaxException blockRefusal(final String sql) {
        final UnknownTypeReading reading = readBlock(sql);
        if (reading == null) {
            return null;
        }
        final String sentence = reading.sentence();
        // An Iceberg table's unknown type is refused under the prefix its other type refusals carry.
        final String message = reading.isIceberg() && !reading.refused().isRefusedAlike()
            ? "SQL Compilation error:\n" + sentence : SqlCompilationError.of(sentence);
        return new SqlSyntaxException(message, Collections.singletonList(sentence), sql);
    }

    /**
     * A text read as the account's grammar reads it: each word that stands where a type does and names no known type
     * written as a known type of its own length, every other character where it was. The account takes any name there
     * and judges it once the text has parsed, so a block with faults of its own besides such a word is refused for
     * those, the word read as a name (live-verified: {@code BEGIN CREATE TABLE t (a nosuch); SELECT FROM; END} is
     * refused at the FROM and its semicolon, {@code BEGIN SELECT 1::nosuch; LET x := ; END} at the LET's semicolon
     * alone). A dotted or quoted name is replaced whole, and so is a single bare word with the parameters written after
     * it; a word shorter than every known type is left as written.
     *
     * @param sql the text
     * @return the reading, its words in the order written, or null when the text holds no such word or a known type
     *         in a word's place is itself refused
     */
    public static UnknownTypeReading standIns(final String sql) {
        final List<Token> all = tokensOf(sql);
        final List<UnknownTypeWord> words = new ArrayList<>();
        final List<int[]> regions = new ArrayList<>();
        String text = sql;
        for (int i = 0; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL || !isWord(token) || !standsWhereATypeDoes(all, i)
                    || namesAKnownType(token.getText())) {
                continue;
            }
            Token lastPart = token;
            int end = i;
            boolean dotted = false;
            boolean quoted = token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
            int next = SyntaxErrorListener.nextDefaultChannel(all, i);
            while (next >= 0 && all.get(next).getType() == FrostlakeLexer.DOT) {
                final int part = SyntaxErrorListener.nextDefaultChannel(all, next);
                if (part < 0 || !SyntaxErrorListener.isIdentifierToken(all.get(part))) {
                    break;
                }
                dotted = true;
                lastPart = all.get(part);
                quoted = quoted || lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
                end = part;
                next = SyntaxErrorListener.nextDefaultChannel(all, part);
            }
            List<String> parameters = null;
            if (next >= 0 && all.get(next).getType() == FrostlakeLexer.LPAREN) {
                // Only a single bare word takes parameters: after a dotted or quoted name they are a fault of the text.
                if (dotted || quoted) {
                    continue;
                }
                parameters = new ArrayList<>();
                end = closeOf(all, next, text, parameters);
                if (end < 0) {
                    continue;
                }
            }
            final int from = token.getStartIndex();
            final int to = all.get(end).getStopIndex() + 1;
            if (to - from < STAND_IN.length()) {
                continue;
            }
            String name = lastPart.getText();
            final boolean lastQuoted = lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
            if (lastQuoted) {
                name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            }
            words.add(new UnknownTypeWord(from, token.getText(), name, lastQuoted, parameters, isMapKey(all, i)));
            regions.add(new int[] {from, to});
            text = replaced(text, from, to, STAND_IN);
            i = end;
        }
        if (words.isEmpty()) {
            return null;
        }
        final int fault = SyntaxErrorListener.firstFaultStartOf(text);
        for (final int[] region : regions) {
            if (fault >= region[0] && fault < region[1]) {
                return null;
            }
        }
        return new UnknownTypeReading(text, words,
            SyntaxErrorListener.inIcebergTableStatement(all, tokenStartingAt(all, words.get(0).getStart())));
    }

    /**
     * The text a block is refused by when it faults for more than its words that name no type: the block with known
     * types in their places (see {@link #standIns}), where that text still faults; null where it reads whole then,
     * and the block is refused for the first word, or where the block holds no such word.
     *
     * @param sql the block's text
     * @return the text with its words as types, or null
     */
    public static String ownFaultText(final String sql) {
        final UnknownTypeReading typed = standIns(sql);
        return typed == null || SyntaxErrorListener.cleanParse(typed.getSubstituted()) != null ? null
            : typed.getSubstituted();
    }

    /** Whether a word is a type the grammar knows, which faults nowhere a type stands. */
    private static boolean namesAKnownType(final String word) {
        return SyntaxErrorListener.cleanParse("SELECT NULL::" + word) != null;
    }

    /** Whether the tokens open a CREATE [OR REPLACE] TASK. */
    private static boolean createsATask(final List<Token> all) {
        int word = 0;
        for (final Token token : all) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (word == 0 && type != FrostlakeLexer.CREATE) {
                return false;
            }
            if (word > 0 && type == FrostlakeLexer.TASK) {
                return true;
            }
            if (word > 0 && type != FrostlakeLexer.OR && type != FrostlakeLexer.REPLACE) {
                return false;
            }
            word++;
        }
        return false;
    }

    /**
     * Reads a statement whose first fault may be a word standing where a type belongs.
     *
     * @param sql        the statement's text
     * @param firstFault the character its first fault starts at
     * @return the reading, or null when the first fault is no such word or the text is not understood
     */
    static UnknownTypeWords read(final String sql, final int firstFault) {
        String text = sql;
        int at = firstFault;
        final List<UnknownTypeWord> words = new ArrayList<>();
        for (int round = 0; round < MOST_WORDS; round++) {
            final List<Token> all = tokensOf(text);
            int index = tokenStartingAt(all, at);
            if (index >= 0 && namesAColumn(all, index)) {
                // The fault is told at the column's name where the word after it is what does not read.
                index = SyntaxErrorListener.nextDefaultChannel(all, index);
            } else if (index >= 0 && structuredTypeAt(all, index) >= 0) {
                // A structured type that does not read is told at its column or its first member: the word is
                // the first within it that stands where a member's type does.
                index = memberWordWithin(all, structuredTypeAt(all, index));
            }
            if (index < 0 || !isWord(all.get(index)) || !standsWhereATypeDoes(all, index)) {
                break;
            }
            // The written name may be a dotted chain.
            Token lastPart = all.get(index);
            int end = index;
            boolean dotted = false;
            boolean quoted = lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
            int next = SyntaxErrorListener.nextDefaultChannel(all, index);
            while (next >= 0 && all.get(next).getType() == FrostlakeLexer.DOT) {
                final int part = SyntaxErrorListener.nextDefaultChannel(all, next);
                if (part < 0 || !SyntaxErrorListener.isIdentifierToken(all.get(part))) {
                    return null;
                }
                dotted = true;
                lastPart = all.get(part);
                quoted = quoted || lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
                end = part;
                next = SyntaxErrorListener.nextDefaultChannel(all, part);
            }
            final boolean parameterized = next >= 0 && all.get(next).getType() == FrostlakeLexer.LPAREN;
            final int from = all.get(index).getStartIndex();
            at = from;
            if (parameterized && (dotted || quoted)) {
                // Only a single bare word takes parameters: the name is a type and the parenthesis the fault.
                final String standIn = parameterless(all.get(end).getStopIndex() + 1 - from);
                if (standIn == null) {
                    return null;
                }
                final String named = replaced(text, from, all.get(end).getStopIndex() + 1, standIn);
                if (SyntaxErrorListener.firstFaultStartOf(named) < all.get(next).getStartIndex()) {
                    return null;
                }
                final List<String> faults = SyntaxErrorListener.linesReportedFor(named, true);
                return faults.isEmpty() ? null : new UnknownTypeWords(null, faults);
            }
            List<String> parameters = null;
            if (parameterized) {
                parameters = new ArrayList<>();
                end = closeOf(all, next, text, parameters);
                if (end < 0) {
                    return null;
                }
            }
            final int to = all.get(end).getStopIndex() + 1;
            if (to - from < STAND_IN.length()) {
                return shortWord(sql, words, all.get(index), from, to,
                    declaresAt(all, previousDefaultChannel(all, previousDefaultChannel(all, index))));
            }
            String name = lastPart.getText();
            final boolean lastQuoted = lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
            if (lastQuoted) {
                name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            }
            final String before = text;
            words.add(new UnknownTypeWord(from, all.get(index).getText(), name, lastQuoted, parameters,
                isMapKey(all, index)));
            text = replaced(text, from, to, STAND_IN);
            if (SyntaxErrorListener.cleanParse(text) != null) {
                for (final UnknownTypeWord word : words) {
                    if (!TypeSlotSubstitution.readsAsType(text, word.getStart())) {
                        return null;
                    }
                }
                return new UnknownTypeWords(new UnknownTypeReading(text, words,
                    SyntaxErrorListener.inIcebergTableStatement(tokensOf(sql), tokenStartingAt(tokensOf(sql),
                        words.get(0).getStart()))), null);
            }
            final int nextFault = SyntaxErrorListener.firstFaultStartOf(text);
            if (nextFault < to) {
                // A known type in the word's place moves the fault no further: the word stands where no type does.
                words.remove(words.size() - 1);
                text = before;
                break;
            }
            at = nextFault;
        }
        if (words.isEmpty()) {
            return null;
        }
        // What is left is a fault of the statement's own: it is told as the text with the words as types tells it.
        final List<String> faults = SyntaxErrorListener.linesReportedFor(text, true);
        return faults.isEmpty() ? null : new UnknownTypeWords(null, faults);
    }

    /**
     * Whether a token may be a type's word: an identifier, or a keyword of this grammar the account reads as a plain
     * name there, OTHER; LANGUAGE and SESSION are refused where they stand (all live-verified).
     */
    private static boolean isWord(final Token token) {
        return SyntaxErrorListener.isIdentifierToken(token) || token.getType() == FrostlakeLexer.OTHER;
    }

    /**
     * Whether a word stands where a type does, told from the tokens before it alone — which is all there is to go
     * by while the text does not parse: after a cast's {@code ::} or {@code AS}, after RETURNS, and — in a
     * statement that defines or alters an object — after the name of a column written in a list or after ADD
     * [COLUMN], and as a member of a structured type.
     */
    private static boolean standsWhereATypeDoes(final List<Token> all, final int index) {
        final int before = previousDefaultChannel(all, index);
        if (before < 0) {
            return false;
        }
        final int type = all.get(before).getType();
        if (type == FrostlakeLexer.DOUBLE_COLON || type == FrostlakeLexer.RETURNS) {
            return true;
        }
        if (type == FrostlakeLexer.AS) {
            return closesACastValue(all, before);
        }
        if (!definesAnObject(all, index)) {
            // A LET names its variable's type after the variable in a block run on its own too: BEGIN LET q nosuch;
            // … END is refused for the type, and so does a DECLARE section in a block's statements: BEGIN RETURN 1;
            // DECLARE q r; END is refused at the END its BEGIN is due at (live-verified).
            final int variable = SyntaxErrorListener.isIdentifierToken(all.get(before))
                ? previousDefaultChannel(all, before) : -1;
            return variable >= 0 && (all.get(variable).getType() == FrostlakeLexer.LET || declaresAt(all, variable));
        }
        if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.COMMA) {
            // A member of a structured type: ARRAY(nosuch), MAP(VARCHAR, nosuch). A VECTOR's element is one of
            // two words and no type of its own.
            return !opensAVector(all, before);
        }
        if (!SyntaxErrorListener.isIdentifierToken(all.get(before))) {
            return false;
        }
        final int earlier = previousDefaultChannel(all, before);
        if (earlier < 0) {
            return false;
        }
        final int opener = all.get(earlier).getType();
        // A LET names its variable's type after the variable, in a block a CREATE holds.
        return opener == FrostlakeLexer.LPAREN || opener == FrostlakeLexer.COMMA || opener == FrostlakeLexer.COLUMN
            || opener == FrostlakeLexer.ADD || opener == FrostlakeLexer.LET;
    }

    /**
     * Whether the token at {@code at} opens an item of a DECLARE section: the DECLARE itself, or the semicolon that
     * ends the item before it while no BEGIN has closed the section.
     */
    private static boolean declaresAt(final List<Token> all, final int at) {
        if (at < 0) {
            return false;
        }
        final int opener = all.get(at).getType();
        if (opener == FrostlakeLexer.DECLARE) {
            return true;
        }
        if (opener != FrostlakeLexer.SEMI) {
            return false;
        }
        for (int i = previousDefaultChannel(all, at); i >= 0; i = previousDefaultChannel(all, i)) {
            final int type = all.get(i).getType();
            if (type == FrostlakeLexer.DECLARE) {
                return true;
            }
            if (type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.END || type == FrostlakeLexer.THEN
                    || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO || type == FrostlakeLexer.LOOP
                    || type == FrostlakeLexer.LET || type == FrostlakeLexer.RETURN) {
                return false;
            }
        }
        return false;
    }

    /** Whether the AS at {@code index} stands after the value of a CAST or a TRY_CAST, at its own depth. */
    private static boolean closesACastValue(final List<Token> all, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            final Token token = all.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final int owner = previousDefaultChannel(all, i);
                    return owner >= 0 && (all.get(owner).getType() == FrostlakeLexer.CAST
                        || all.get(owner).getType() == FrostlakeLexer.TRY_CAST);
                }
                depth--;
            }
        }
        return false;
    }

    /**
     * Whether the statement a token stands in opens with CREATE or ALTER. A block's statement opens after the words
     * that open its place - BEGIN, ELSE, LOOP, REPEAT, and the THEN or DO that closes an IF, ELSEIF, CASE, WHEN,
     * EXCEPTION, FOR or WHILE head: {@code BEGIN IF (c) THEN CREATE TABLE t (a nosuch); END IF; END} is refused for
     * the type as the statement on its own is (live-verified).
     */
    private static boolean definesAnObject(final List<Token> all, final int index) {
        int first = -1;
        for (int i = 0; i <= index; i++) {
            final Token token = all.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int type = token.getType();
            if (type == FrostlakeLexer.SEMI || first >= 0 && closesABlockHead(all.get(first).getType(), type)) {
                first = -1;
            } else if (first < 0 && !opensABlockPlace(type)) {
                first = i;
            }
        }
        return first >= 0 && (all.get(first).getType() == FrostlakeLexer.CREATE
            || all.get(first).getType() == FrostlakeLexer.ALTER);
    }

    /** Whether a word opens a place in a block that a statement then fills. */
    private static boolean opensABlockPlace(final int type) {
        return type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.LOOP
            || type == FrostlakeLexer.REPEAT;
    }

    /** Whether a THEN or a DO closes the head of a block's construct the word {@code opener} opened. */
    private static boolean closesABlockHead(final int opener, final int type) {
        if (type == FrostlakeLexer.THEN) {
            return opener == FrostlakeLexer.IF || opener == FrostlakeLexer.ELSEIF || opener == FrostlakeLexer.CASE
                || opener == FrostlakeLexer.WHEN || opener == FrostlakeLexer.EXCEPTION;
        }
        return type == FrostlakeLexer.DO && (opener == FrostlakeLexer.FOR || opener == FrostlakeLexer.WHILE);
    }

    /** Whether a token is a column's name with a word after it: one written in a list, or after ADD [COLUMN]. */
    private static boolean namesAColumn(final List<Token> all, final int index) {
        if (!SyntaxErrorListener.isIdentifierToken(all.get(index)) || !definesAnObject(all, index)) {
            return false;
        }
        final int next = SyntaxErrorListener.nextDefaultChannel(all, index);
        final int before = previousDefaultChannel(all, index);
        if (next < 0 || before < 0 || !SyntaxErrorListener.isIdentifierToken(all.get(next))) {
            return false;
        }
        final int opener = all.get(before).getType();
        return opener == FrostlakeLexer.LPAREN || opener == FrostlakeLexer.COMMA || opener == FrostlakeLexer.COLUMN
            || opener == FrostlakeLexer.ADD;
    }

    /**
     * Whether a token is a word standing where a member of a structured type is typed: after the parenthesis or a
     * comma, or after a member's name, with nothing but the type's own punctuation behind it.
     */
    private static boolean isMemberWord(final List<Token> all, final int index) {
        if (!SyntaxErrorListener.isIdentifierToken(all.get(index)) || !standsWhereATypeDoes(all, index)) {
            return false;
        }
        final int next = SyntaxErrorListener.nextDefaultChannel(all, index);
        if (next < 0) {
            return true;
        }
        final int after = all.get(next).getType();
        return after == FrostlakeLexer.RPAREN || after == FrostlakeLexer.COMMA || after == FrostlakeLexer.LPAREN
            || after == FrostlakeLexer.DOT;
    }

    /**
     * The parenthesis opening the structured type a fault is told at: the one after ARRAY, OBJECT or MAP written
     * as the type of the column the fault names, the fault's own token, or the one that token is the first thing
     * within; -1 when the fault is told at none of them.
     */
    private static int structuredTypeAt(final List<Token> all, final int index) {
        final int next = SyntaxErrorListener.nextDefaultChannel(all, index);
        final int open = next < 0 ? -1 : SyntaxErrorListener.nextDefaultChannel(all, next);
        if (SyntaxErrorListener.isIdentifierToken(all.get(index)) && open >= 0 && isStructured(all.get(next))
                && all.get(open).getType() == FrostlakeLexer.LPAREN) {
            return open;
        }
        final int before = previousDefaultChannel(all, index);
        if (before >= 0 && all.get(index).getType() == FrostlakeLexer.LPAREN && isStructured(all.get(before))) {
            return index;
        }
        final int earlier = before < 0 ? -1 : previousDefaultChannel(all, before);
        return earlier >= 0 && all.get(before).getType() == FrostlakeLexer.LPAREN && isStructured(all.get(earlier))
            ? before : -1;
    }

    private static boolean isStructured(final Token token) {
        return token.getType() == FrostlakeLexer.ARRAY || token.getType() == FrostlakeLexer.OBJECT
            || token.getType() == FrostlakeLexer.MAP;
    }

    /** The first member word within the parentheses opened at {@code open}; -1 when there is none. */
    private static int memberWordWithin(final List<Token> all, final int open) {
        int depth = 0;
        for (int i = open; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getType() == Token.EOF) {
                return -1;
            }
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return -1;
                }
            } else if (isMemberWord(all, i)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the parenthesis at or before a token is a VECTOR's. */
    private static boolean opensAVector(final List<Token> all, final int index) {
        int depth = 0;
        for (int i = index; i >= 0; i--) {
            final Token token = all.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final int owner = previousDefaultChannel(all, i);
                    return owner >= 0 && all.get(owner).getType() == FrostlakeLexer.VECTOR;
                }
                depth--;
            }
        }
        return false;
    }

    /** Whether a word is the key type of a MAP: the first thing in its parentheses. */
    private static boolean isMapKey(final List<Token> all, final int index) {
        final int before = previousDefaultChannel(all, index);
        final int earlier = before < 0 ? -1 : previousDefaultChannel(all, before);
        return earlier >= 0 && all.get(before).getType() == FrostlakeLexer.LPAREN
            && all.get(earlier).getType() == FrostlakeLexer.MAP;
    }

    private static int previousDefaultChannel(final List<Token> all, final int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (all.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the parenthesis closing the group opened at {@code open}, its written parameters gathered; -1 when unclosed. */
    private static int closeOf(final List<Token> all, final int open, final String text, final List<String> into) {
        int depth = 0;
        int paramFrom = -1;
        int paramTo = -1;
        for (int i = open; i < all.size(); i++) {
            final Token t = all.get(i);
            if (t.getType() == Token.EOF) {
                return -1;
            }
            if (t.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (t.getType() == FrostlakeLexer.LPAREN) {
                depth++;
                if (depth == 1) {
                    continue;
                }
            } else if (t.getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    if (paramFrom >= 0) {
                        into.add(text.substring(all.get(paramFrom).getStartIndex(), all.get(paramTo).getStopIndex() + 1));
                    }
                    return i;
                }
            } else if (depth == 1 && t.getType() == FrostlakeLexer.COMMA) {
                if (paramFrom >= 0) {
                    into.add(text.substring(all.get(paramFrom).getStartIndex(), all.get(paramTo).getStopIndex() + 1));
                }
                paramFrom = -1;
                continue;
            }
            if (paramFrom < 0) {
                paramFrom = i;
            }
            paramTo = i;
        }
        return -1;
    }

    /**
     * The reading of a statement whose one word is shorter than any known type, {@code LET q r;}: the stand-in is
     * written in its place, the rest of the text moved along, and the statement is taken as read where the text
     * then parses cleanly. A DECLARE item's word ({@code declared}) whose text still faults past it is told by those
     * faults, their places put back (see {@link #widenedFaults}); any other is null, where the places of a later
     * fault would no longer be the written ones.
     */
    private static UnknownTypeWords shortWord(final String sql, final List<UnknownTypeWord> words, final Token word,
                                              final int from, final int to, final boolean declared) {
        if (!words.isEmpty() || word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return null;
        }
        final String widened = sql.substring(0, from) + STAND_IN + sql.substring(to);
        if (SyntaxErrorListener.cleanParse(widened) == null) {
            return declared ? widenedFaults(widened, word, from, to) : null;
        }
        if (!TypeSlotSubstitution.readsAsType(widened, from)) {
            return null;
        }
        final List<UnknownTypeWord> read = new ArrayList<>();
        read.add(new UnknownTypeWord(from, word.getText(), word.getText(), false, null, false));
        return new UnknownTypeWords(new UnknownTypeReading(widened, read,
            SyntaxErrorListener.inIcebergTableStatement(tokensOf(sql), tokenStartingAt(tokensOf(sql), from))), null);
    }

    /**
     * The lines of a text that still faults once a short word is widened to a type, every place after the word put
     * back where it was written: BEGIN RETURN 1; DECLARE q r; END is the END the section's BEGIN is due at
     * (live-verified). Null when a fault stands at the word or before it.
     */
    private static UnknownTypeWords widenedFaults(final String widened, final Token word, final int from,
                                                  final int to) {
        final int shift = STAND_IN.length() - (to - from);
        final int past = word.getCharPositionInLine() + STAND_IN.length();
        final List<String> lines = new ArrayList<>();
        for (final String fault : SyntaxErrorListener.linesReportedFor(widened, true)) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(fault);
            if (at == null || at[0] < word.getLine() || at[0] == word.getLine() && at[1] < past) {
                return null;
            }
            if (at[0] > word.getLine()) {
                lines.add(fault);
                continue;
            }
            final String written = " at position " + at[1] + " ";
            final int cut = fault.indexOf(written);
            if (cut < 0) {
                return null;
            }
            lines.add(fault.substring(0, cut) + " at position " + (at[1] - shift) + " "
                + fault.substring(cut + written.length()));
        }
        return lines.isEmpty() ? null : new UnknownTypeWords(null, lines);
    }

    /** A known type that takes no parameters and fits in {@code length} characters, or null when none does. */
    private static String parameterless(final int length) {
        for (final String candidate : PARAMETERLESS) {
            if (candidate.length() <= length) {
                return candidate;
            }
        }
        return null;
    }

    /** The text with the region replaced by the stand-in, padded with spaces to the region's own length. */
    private static String replaced(final String text, final int from, final int to, final String standIn) {
        final StringBuilder out = new StringBuilder(text.length());
        out.append(text, 0, from).append(standIn);
        for (int i = standIn.length(); i < to - from; i++) {
            // A line break inside the region keeps its place, so the lines after it keep their numbers.
            final char written = text.charAt(from + i);
            out.append(written == '\n' || written == '\r' ? written : ' ');
        }
        return out.append(text, to, text.length()).toString();
    }

    private static List<Token> tokensOf(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        return tokens.getTokens();
    }

    private static int tokenStartingAt(final List<Token> all, final int start) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getChannel() == Token.DEFAULT_CHANNEL && all.get(i).getStartIndex() == start) {
                return i;
            }
        }
        return -1;
    }
}
