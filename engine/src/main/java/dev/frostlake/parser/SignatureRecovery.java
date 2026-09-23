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
import java.util.Locale;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * How the account reads the signature a DROP or a DESCRIBE writes after a table's or a view's name, and the tail after
 * it, when something in them is wrong: every line it reports, in its order.
 *
 * <p>The account reads the signature as a list of data types and recovers from a fault the way a recursive-descent
 * reader with single-token repair does. At a token it cannot match it first drops that token when the one after it is
 * the expected one ({@code (a b)} names the b), then supplies the expected token when the one at hand may follow it
 * ({@code DESCRIBE TABLE t1 (TRUE(1))} names the '(' as the missing '=' of a property TRUE); otherwise it names the
 * token, abandons the item, the list or the property it was reading, and skips to the first token that may follow
 * anything it is still inside — a ',' or ')' of an enclosing list, a DROP's CASCADE, a DESCRIBE's property name. After
 * one report it names nothing more until it has matched a token again, and a token it names again where it already
 * resynchronised is skipped. A DESCRIBE thus reads the rest of the text as properties once its signature is
 * abandoned — {@code DESCRIBE TABLE t1 ((a), b)} is '(' then ')' at 21 and ')' at 25, a and b read as property names —
 * while a DROP skips to its end: {@code DROP TABLE t1 ((a)) x} is the '(' alone.
 *
 * <p>Each position takes its own words: an item is a plain word, a quoted one or a data type (a keyword like TYPE, IF
 * or VIEW is none, and d, t, ts, fn and oj are keywords), a type takes the parameters its kind takes, entered only when
 * the token after its '(' can begin them ({@code (VARCHAR((1)))} names the first '('), and a plain word takes
 * parameters or a dotted path but not both. A property's name is almost any word; its value is predicted whole, so a
 * word value that a number, a '-', a '?' or a stage reference follows is refused there and read again as the next
 * property's name ({@code x = y 1} names the 1 twice). A stage reference — one token to the account, see
 * {@link StageReferenceTokens} — is a property's value, never an item, an item of a value list or a property's name.
 * After the bare word IDENTIFIER the statement itself judges the signature's first token, so one it cannot open is named
 * alone (all live-verified).
 *
 * <p>A shape outside this reading — another statement after a ';', a structured type, an expression as a property
 * value, a text this lexer cannot read the way the account does (a character it has no token for, an unclosed quote
 * or comment) — makes it answer nothing, and the parse's own lines stand.
 */
final class SignatureRecovery {

    private static final int EOF_CLASS = 0;
    private static final int SEMI = 1;
    private static final int LPAREN = 2;
    private static final int RPAREN = 3;
    private static final int COMMA = 4;
    private static final int DOT = 5;
    private static final int EQ = 6;
    private static final int MINUS = 7;
    private static final int INT = 8;
    private static final int NUM = 9;
    private static final int STR = 10;
    private static final int QUOTED = 11;
    private static final int WORD = 12;
    private static final int PATH_WORD = 13;
    private static final int BARE_TYPE = 14;
    private static final int SCALE_TYPE = 15;
    private static final int LENGTH_TYPE = 16;
    private static final int PRECISION_TYPE = 17;
    private static final int KEYWORD = 18;
    private static final int BOOLEAN = 19;
    private static final int NULL_WORD = 20;
    private static final int RESERVED = 21;
    private static final int QUESTION = 22;
    private static final int DROP_OPTION = 23;
    private static final int ALL_WORD = 24;
    private static final int EXPRESSION = 25;
    private static final int TABLE = 26;
    private static final int STRUCTURED = 27;
    private static final int COLON = 28;
    private static final int OTHER = 29;
    private static final int UNMODELLED = 30;
    private static final int END_OF_RULE = 31;
    private static final int CLAUSE_WORD = 32;
    private static final int WHEN = 33;
    private static final int STAGE = 34;

    private static final long EOR = bit(END_OF_RULE);
    private static final long TYPES = bit(BARE_TYPE) | bit(SCALE_TYPE) | bit(LENGTH_TYPE) | bit(PRECISION_TYPE);
    private static final long ITEM_START = bit(QUOTED) | bit(WORD) | bit(PATH_WORD) | TYPES | bit(STRUCTURED);
    private static final long PART = bit(QUOTED) | bit(WORD) | bit(PATH_WORD);
    private static final long WORD_VALUE = ITEM_START | bit(KEYWORD) | bit(CLAUSE_WORD) | bit(DROP_OPTION);
    private static final long PROPERTY = WORD_VALUE | bit(BOOLEAN) | bit(EXPRESSION) | bit(WHEN);
    private static final long LITERAL = bit(INT) | bit(NUM) | bit(STR) | bit(QUESTION) | bit(BOOLEAN) | bit(NULL_WORD)
        | bit(ALL_WORD);
    private static final long PATH_START = WORD_VALUE | bit(BOOLEAN);
    private static final long EQ_FOLLOW = LITERAL | WORD_VALUE | bit(LPAREN) | bit(MINUS) | bit(EXPRESSION) | bit(WHEN)
        | bit(COLON) | bit(STAGE);
    private static final long NUMBER = bit(INT) | bit(NUM);
    private static final long REREAD_AFTER_WORD = NUMBER | bit(MINUS) | bit(QUESTION) | bit(STAGE);
    private static final long FIRST_ITEM_READ_ON = bit(INT) | bit(NUM) | bit(NULL_WORD) | bit(QUESTION) | bit(MINUS);
    private static final long LIST_END = bit(COMMA) | bit(RPAREN);

    private static final int[] CLASSES = classes();

    private final List<Token> tokens;
    private final boolean lexed;
    private final boolean describe;
    private final boolean identifierName;
    private final List<Long> following = new ArrayList<>();
    private final List<Token> reported = new ArrayList<>();
    private int at;
    private boolean recovering;
    private int lastErrorIndex = -1;

    private SignatureRecovery(final List<Token> tokens, final boolean lexed, final boolean describe,
                              final boolean identifierName) {
        this.tokens = tokens;
        this.lexed = lexed;
        this.describe = describe;
        this.identifierName = identifierName;
    }

    /**
     * The reading of the script's first statement when it is a DROP or a DESCRIBE of a table or a view whose name a
     * '(' follows straight away, or null for any other script.
     *
     * @param script the parsed script
     * @param sql    the script's source text
     * @return the reading, or null
     */
    static SignatureRecovery of(final FrostlakeParser.SqlScriptContext script, final String sql) {
        final int open = signatureOpen(script, sql);
        if (open < 0) {
            return null;
        }
        final List<Integer> comments = new ArrayList<>();
        final List<Integer> unlexed = new ArrayList<>();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.setInterpreter(commentMarking(lexer, comments));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                unlexed.add(charPositionInLine);
            }
        });
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> region = new ArrayList<>();
        for (final Token token : stream.getTokens()) {
            if (token.getTokenIndex() >= open && token.getChannel() == Token.DEFAULT_CHANNEL) {
                region.add(token);
            }
        }
        if (region.isEmpty() || region.get(0).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final ParserRuleContext name = objectName(script.flowChain().get(0).statement().get(0));
        final boolean identifierName = name.getChildCount() == 1 && name.getChild(0) instanceof TerminalNode
            && ((TerminalNode) name.getChild(0)).getSymbol().getType() == FrostlakeParser.KW_IDENTIFIER_OPEN;
        final List<Token> joined = unlexed.isEmpty() ? StageReferenceTokens.joined(region, comments) : null;
        return new SignatureRecovery(joined == null ? region : joined, joined != null, isDescribe(script),
            identifierName);
    }

    /**
     * This lexer's own simulator, noting where each comment it skips starts: a stage reference takes in a comment
     * written straight after it (see {@link StageReferenceTokens}).
     */
    private static LexerATNSimulator commentMarking(final FrostlakeLexer lexer, final List<Integer> comments) {
        return new LexerATNSimulator(lexer, FrostlakeLexer._ATN, FrostlakeLexer._decisionToDFA,
                FrostlakeLexer._sharedContextCache) {
            @Override
            public int match(final CharStream input, final int mode) {
                final int start = input.index();
                final int type = super.match(input, mode);
                if (type == FrostlakeLexer.LINE_COMMENT || type == FrostlakeLexer.BLOCK_COMMENT) {
                    comments.add(start);
                }
                return type;
            }
        };
    }

    /**
     * The '(' that opens the signature.
     *
     * @return the token
     */
    Token open() {
        return tokens.get(0);
    }

    /**
     * The tokens the account names, in its order, from the signature on — none when it reads the rest whole — or null
     * when the text holds a shape this reading does not model.
     *
     * @return the named tokens, or null
     */
    List<Token> faults() {
        if (!lexed || !modelled(tokens)) {
            return null;
        }
        try {
            statement();
        } catch (final SignatureFault unmodelled) {
            return null;
        }
        return reported;
    }

    /**
     * The index of the token that opens the signature of the script's first statement, when that statement is a DROP
     * or a DESCRIBE of a table or a view and a '(' stands straight after the object's name; -1 otherwise.
     */
    private static int signatureOpen(final FrostlakeParser.SqlScriptContext script, final String sql) {
        if (script == null || sql == null || script.flowChain().isEmpty()
                || script.flowChain().get(0).statement().isEmpty()) {
            return -1;
        }
        final FrostlakeParser.StatementContext statement = script.flowChain().get(0).statement().get(0);
        final ParserRuleContext name = objectName(statement);
        if (name == null || name.getStop() == null) {
            return -1;
        }
        final ParseTree after = nextSibling(name);
        if (after instanceof FrostlakeParser.ObjectSignatureContext) {
            return ((ParserRuleContext) after).getStart().getTokenIndex();
        }
        if (after != null) {
            return -1;
        }
        // Nothing follows the name inside the statement: a '(' may still stand after it when the parse split the text
        // into further statements, so only a script that is this statement alone is settled here.
        for (int i = 0; i < script.getChildCount(); i++) {
            final ParseTree child = script.getChild(i);
            if (child != script.flowChain().get(0)
                    && !(child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == Token.EOF)) {
                return name.getStop().getTokenIndex() + 1;
            }
        }
        return script.flowChain().get(0).statement().size() > 1 ? name.getStop().getTokenIndex() + 1 : -1;
    }

    /** The object name a DROP or a DESCRIBE of a table or a view reads, or null for any other statement. */
    private static ParserRuleContext objectName(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DescribeStatementContext describe = statement.describeStatement();
        if (describe != null && describe.objectName() != null
                && (describe.TABLE() != null && describe.DYNAMIC() == null
                    || describe.VIEW() != null && describe.MATERIALIZED() == null)) {
            return describe.objectName();
        }
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        final FrostlakeParser.DropStatementContext drop = ddl == null ? null : ddl.dropStatement();
        if (drop != null && drop.objectName() != null
                && (drop.TABLE() != null && drop.ICEBERG() == null && drop.DYNAMIC() == null
                    || drop.VIEW() != null && drop.MATERIALIZED() == null)) {
            return drop.objectName();
        }
        return null;
    }

    private static ParseTree nextSibling(final ParserRuleContext node) {
        final ParserRuleContext parent = node.getParent();
        final int index = parent.children.indexOf(node);
        return index + 1 < parent.getChildCount() ? parent.getChild(index + 1) : null;
    }

    private static boolean isDescribe(final FrostlakeParser.SqlScriptContext script) {
        return script.flowChain().get(0).statement().get(0).describeStatement() != null;
    }

    /**
     * Whether every token of the region is one this reading classifies and the region holds one statement: a ';'
     * only as its last token, none of the spellings the account reads as one word where this lexer reads two, and no
     * block comment left open, which the account refuses where this lexer reads a '/' and a '*'.
     */
    private static boolean modelled(final List<Token> region) {
        for (int i = 0; i < region.size(); i++) {
            final Token token = region.get(i);
            final int type = token.getType();
            if (classOf(token) == UNMODELLED) {
                return false;
            }
            if (type == FrostlakeLexer.SEMI && i != region.size() - 2) {
                return false;
            }
            final Token next = i + 1 < region.size() ? region.get(i + 1) : null;
            final int nextType = next == null ? Token.EOF : next.getType();
            if (type == FrostlakeLexer.DOUBLE && nextType == FrostlakeLexer.PRECISION
                    || (type == FrostlakeLexer.CHAR || type == FrostlakeLexer.CHARACTER || type == FrostlakeLexer.NCHAR)
                        && nextType == FrostlakeLexer.VARYING
                    || type == FrostlakeLexer.TIMESTAMP && nextType != FrostlakeLexer.LPAREN
                        && nextType != FrostlakeLexer.COMMA && nextType != FrostlakeLexer.RPAREN
                    || type == FrostlakeLexer.FLOAT_LITERAL && next != null
                        && (nextType == FrostlakeLexer.FLOAT_LITERAL || nextType == FrostlakeLexer.DOT)
                        && next.getStartIndex() == token.getStopIndex() + 1
                    || type == FrostlakeLexer.SLASH && next != null
                        && (nextType == FrostlakeLexer.STAR || nextType == FrostlakeLexer.DOUBLE_STAR)
                        && next.getStartIndex() == token.getStopIndex() + 1) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The statement: the signature, then a DESCRIBE's properties or a DROP's option, then the end of the text.
    // ---------------------------------------------------------------------------------------------------------------

    private void statement() {
        following.add(bit(SEMI));
        following.add(EOR);
        if (identifierName && la(2) != RPAREN && la(2) != TABLE && !in(la(2), ITEM_START)) {
            // After the bare word IDENTIFIER the statement itself decides between a reference and a signature, so a
            // token that opens neither is named alone and the rest of the statement is skipped.
            report(lt(2));
            while (la(1) != EOF_CLASS && la(1) != SEMI) {
                consume();
            }
        } else if (describe) {
            following.add(PROPERTY | EOR);
            signature();
            following.remove(following.size() - 1);
            properties();
        } else {
            following.add(bit(DROP_OPTION) | EOR);
            signature();
            following.remove(following.size() - 1);
            if (la(1) == DROP_OPTION) {
                matched();
            }
        }
        following.clear();
        end();
    }

    /** The script's end: an optional ';' and the end of the input, anything else named and skipped. */
    private void end() {
        try {
            if (la(1) == SEMI) {
                matched();
            }
            match(EOF_CLASS, 0L);
        } catch (final SignatureFault fault) {
            handle(fault);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The signature: '(' [ item { ',' item } ] ')'.
    // ---------------------------------------------------------------------------------------------------------------

    private void signature() {
        try {
            matched();
            final int first = la(1);
            if (first == RPAREN) {
                matched();
                return;
            }
            if (first == TABLE) {
                throw unmodelled();
            }
            if (!in(first, ITEM_START)) {
                throw fault(1);
            }
            item();
            while (la(1) == COMMA) {
                matched();
                item();
            }
            match(RPAREN, EOR);
        } catch (final SignatureFault fault) {
            handle(fault);
        }
    }

    private void item() {
        following.add(LIST_END);
        try {
            dataType();
        } catch (final SignatureFault fault) {
            handle(fault);
        }
        following.remove(following.size() - 1);
    }

    /**
     * One data type: a type keyword with the parameters its kind takes, a plain word with parameters or a dotted
     * path, or a quoted word with a dotted path.
     */
    private void dataType() {
        final int kind = la(1);
        if (kind == STRUCTURED) {
            throw unmodelled();
        }
        if (kind == BARE_TYPE) {
            matched();
            return;
        }
        if (kind == SCALE_TYPE || kind == LENGTH_TYPE || kind == PRECISION_TYPE) {
            matched();
            if (la(1) == LPAREN && (la(2) == INT || la(2) == MINUS || kind == LENGTH_TYPE && la(2) == RPAREN)) {
                typeParameters(kind);
            }
            return;
        }
        if (kind == WORD) {
            matched();
            if (la(1) == LPAREN) {
                parameters();
            } else {
                path();
            }
            return;
        }
        if (kind == PATH_WORD || kind == QUOTED) {
            matched();
            path();
            return;
        }
        throw fault(1);
    }

    /** A type's parameters: NUMBER(p [, s]), VARCHAR([n]) or TIME(p), each number signed. */
    private void typeParameters(final int kind) {
        matched();
        if (kind != LENGTH_TYPE || la(1) == INT || la(1) == MINUS) {
            signedNumber(kind == SCALE_TYPE ? LIST_END : bit(RPAREN));
            if (kind == SCALE_TYPE && la(1) == COMMA) {
                matched();
                signedNumber(bit(RPAREN));
            }
        }
        match(RPAREN, EOR);
    }

    private void signedNumber(final long follow) {
        if (la(1) == MINUS) {
            matched();
        }
        match(INT, follow);
    }

    /** A dotted path after a word: each '.' followed by a plain or quoted word, read by a rule of its own. */
    private void path() {
        while (la(1) == DOT) {
            matched();
            pathPart(PART);
        }
    }

    private void pathPart(final long words) {
        following.add(bit(DOT) | EOR);
        try {
            if (!in(la(1), words)) {
                throw fault(1);
            }
            matched();
        } catch (final SignatureFault fault) {
            handle(fault);
        }
        following.remove(following.size() - 1);
    }

    /** A plain word's parameters: '(' parameter { ',' parameter } ')'. */
    private void parameters() {
        matched();
        parameter();
        while (la(1) == COMMA) {
            matched();
            parameter();
        }
        match(RPAREN, EOR);
    }

    private void parameter() {
        following.add(LIST_END);
        try {
            if (la(1) == MINUS) {
                matched();
                match(INT, EOR);
            } else if (la(1) == INT) {
                matched();
            } else {
                dataType();
            }
        } catch (final SignatureFault fault) {
            handle(fault);
        }
        following.remove(following.size() - 1);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // A DESCRIBE's properties: property { [','] property }, each name '=' value.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The properties: the first may be any property word; a later one follows a ',' or stands on its own, except that
     * a clause word (IF, COMMENT, TAG, …) continues the list only when an '=' follows it.
     */
    private void properties() {
        if (!in(la(1), PROPERTY)) {
            return;
        }
        property();
        while (true) {
            final int next = la(1);
            if (next == COMMA) {
                matched();
                if (!in(la(1), PROPERTY) && la(1) != EOF_CLASS && !(la(1) == SEMI && la(2) == EOF_CLASS)) {
                    throw unmodelled();
                }
            } else if (next == CLAUSE_WORD || next == WHEN) {
                if (la(2) != EQ) {
                    return;
                }
            } else if (!in(next, PROPERTY)) {
                return;
            }
            property();
        }
    }

    private void property() {
        following.add(bit(COMMA) | PROPERTY | EOR);
        try {
            if (!in(la(1), PROPERTY)) {
                throw fault(1);
            }
            matched();
            match(EQ, EQ_FOLLOW);
            value();
        } catch (final SignatureFault fault) {
            handle(fault);
        }
        following.remove(following.size() - 1);
    }

    /**
     * A property's value: a literal, a stage reference, a signed number, a word with its dotted path, or a
     * parenthesized list.
     */
    private void value() {
        final int kind = la(1);
        if (kind == EXPRESSION || kind == WHEN || kind == COLON) {
            throw unmodelled();
        }
        if (kind == LPAREN) {
            valueList();
        } else if (kind == MINUS) {
            signedNumber();
        } else if (in(kind, PATH_START)) {
            wordValue();
        } else if (in(kind, LITERAL) || kind == STAGE) {
            matched();
        } else {
            throw fault(1);
        }
    }

    /**
     * A word and its dotted path, predicted whole: a '.' followed by no word, or a number, a '-' or a '?' after the
     * path, is refused before anything is read, and the word is read again as a property.
     */
    private void wordValue() {
        int length = 1;
        while (la(length + 1) == DOT) {
            if (!in(la(length + 2), PATH_START)) {
                throw fault(length + 2);
            }
            length += 2;
        }
        if (in(la(length + 1), REREAD_AFTER_WORD)) {
            throw fault(length + 1);
        }
        for (int i = 0; i < length; i++) {
            matched();
        }
    }

    /**
     * A parenthesized list, predicted from its first item: a number, NULL, '?' or '-' opens it at once; a word, a
     * string or TRUE opens it only when a ',', ')' or (after a word) '.' follows, and is otherwise refused at that
     * token before the '(' is read.
     */
    private void valueList() {
        final int first = la(2);
        if (first == RPAREN) {
            matched();
            matched();
            return;
        }
        if (first == LPAREN || first == EXPRESSION || first == WHEN || first == COLON) {
            throw unmodelled();
        }
        if (in(first, PATH_START) || first == STR) {
            final int after = la(3);
            if (after == DOT && first == STR) {
                throw unmodelled();
            }
            if (after != COMMA && after != RPAREN && after != DOT) {
                throw fault(3);
            }
        } else if (!in(first, FIRST_ITEM_READ_ON)) {
            throw fault(2);
        }
        matched();
        listItem(true);
        while (la(1) == COMMA) {
            matched();
            listItem(false);
        }
        match(RPAREN, EOR);
    }

    /**
     * One item of a value list: a number, a string, NULL or '?' read at once, a signed number, or a word — a later
     * one read only when a ',', ')' or '.' follows it, and a dotted one read to its end.
     */
    private void listItem(final boolean first) {
        following.add(LIST_END);
        try {
            final int kind = la(1);
            if (kind == ALL_WORD || kind == EXPRESSION || kind == WHEN || kind == COLON) {
                throw unmodelled();
            }
            if (kind == MINUS) {
                signedNumber();
            } else if (in(kind, PATH_START)) {
                if (la(2) == DOT) {
                    listPath();
                } else if (!first && !in(la(2), LIST_END)) {
                    throw fault(2);
                } else {
                    matched();
                }
            } else if (in(kind, LITERAL)) {
                matched();
            } else {
                throw fault(1);
            }
        } catch (final SignatureFault fault) {
            handle(fault);
        }
        following.remove(following.size() - 1);
    }

    private void listPath() {
        matched();
        while (la(1) == DOT) {
            matched();
            pathPart(PATH_START);
        }
    }

    /** A '-' and the number after it, predicted together: a '-' that no number follows is refused at what does. */
    private void signedNumber() {
        if (!in(la(2), NUMBER)) {
            throw fault(2);
        }
        matched();
        matched();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The recovery.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Match one token: on a mismatch, drop the token at hand when the next one is the expected one, supply the expected
     * one when the token at hand may follow it, and otherwise fail at the token at hand.
     */
    private void match(final int kind, final long follow) {
        if (la(1) == kind) {
            matched();
            return;
        }
        if (la(2) == kind) {
            final Token extra = lt(1);
            consume();
            report(extra);
            consume();
            return;
        }
        if (missingTokenFits(follow)) {
            report(lt(1));
            return;
        }
        throw fault(1);
    }

    private boolean missingTokenFits(final long follow) {
        long viable = follow;
        if ((viable & EOR) != 0) {
            viable |= contextFollow();
            viable &= ~EOR;
        }
        return la(1) != EOF_CLASS && (viable & bit(la(1))) != 0;
    }

    /** What may follow the rules being read, from the innermost out to the first that cannot end there. */
    private long contextFollow() {
        long set = 0;
        for (int i = following.size() - 1; i >= 0; i--) {
            final long local = following.get(i);
            set |= local;
            if ((local & EOR) == 0) {
                break;
            }
            if (i > 0) {
                set &= ~EOR;
            }
        }
        return set;
    }

    private void handle(final SignatureFault fault) {
        if (fault.unmodelled()) {
            throw fault;
        }
        report(fault.token());
        recover();
    }

    private void report(final Token token) {
        if (recovering) {
            return;
        }
        recovering = true;
        reported.add(token);
    }

    /** Skip to the first token that may follow any rule being read; never stay twice where a fault was handled. */
    private void recover() {
        if (lastErrorIndex == at) {
            consume();
        }
        lastErrorIndex = at;
        long set = 0;
        for (final Long local : following) {
            set |= local;
        }
        while (la(1) != EOF_CLASS && (set & bit(la(1))) == 0) {
            consume();
        }
    }

    private void matched() {
        consume();
        recovering = false;
    }

    private void consume() {
        if (at < tokens.size() - 1) {
            at++;
        }
    }

    private SignatureFault fault(final int ahead) {
        return new SignatureFault(lt(ahead), false);
    }

    private static SignatureFault unmodelled() {
        return new SignatureFault(null, true);
    }

    private Token lt(final int ahead) {
        final int index = at + ahead - 1;
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    private int la(final int ahead) {
        return classOf(lt(ahead));
    }

    private static boolean in(final int kind, final long set) {
        return (set & bit(kind)) != 0;
    }

    private static long bit(final int kind) {
        return 1L << kind;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The account's view of this lexer's tokens.
    // ---------------------------------------------------------------------------------------------------------------

    private static int classOf(final Token token) {
        final int type = token.getType();
        if (type == Token.EOF) {
            return EOF_CLASS;
        }
        if (type == FrostlakeLexer.IDENTIFIER) {
            final String word = token.getText().toLowerCase(Locale.ROOT);
            if ("trigger".equals(word) || "whenever".equals(word)) {
                return UNMODELLED;
            }
            return "d".equals(word) || "t".equals(word) || "ts".equals(word) || "fn".equals(word) || "oj".equals(word)
                || "connection".equals(word) || "gscluster".equals(word) || "issue".equals(word) ? KEYWORD : WORD;
        }
        return type >= 0 && type < CLASSES.length ? CLASSES[type] : UNMODELLED;
    }

    /**
     * Each token type's class: a word is a keyword unless it is assigned below, and a symbol that is not assigned
     * below is one this reading does not model.
     */
    private static int[] classes() {
        final int[] table = new int[FrostlakeLexer.VOCABULARY.getMaxTokenType() + 1];
        for (int i = 0; i < table.length; i++) {
            table[i] = FrostlakeLexer.VOCABULARY.getLiteralName(i) == null ? KEYWORD : UNMODELLED;
        }
        assign(table, OTHER, FrostlakeLexer.LBRACKET, FrostlakeLexer.RBRACKET, FrostlakeLexer.LBRACE,
            FrostlakeLexer.RBRACE, FrostlakeLexer.COLON_EQ, FrostlakeLexer.ARROW, FrostlakeLexer.FLOW_ARROW,
            FrostlakeLexer.THIN_ARROW, FrostlakeLexer.NEQ, FrostlakeLexer.LT, FrostlakeLexer.LTE, FrostlakeLexer.GT,
            FrostlakeLexer.GTE, FrostlakeLexer.PLUS, FrostlakeLexer.STAR, FrostlakeLexer.DOUBLE_STAR,
            FrostlakeLexer.SLASH, FrostlakeLexer.PERCENT, FrostlakeLexer.TILDE, FrostlakeLexer.PIPE_PIPE,
            FrostlakeLexer.DOUBLE_COLON);
        assign(table, UNMODELLED, FrostlakeLexer.SESSION_VAR_REF, FrostlakeLexer.SYSTEM_STREAM_HAS_DATA,
            FrostlakeLexer.SYSTEM_USER_TASK_CANCEL, FrostlakeLexer.SYSTEM_FUNC, FrostlakeLexer.MALFORMED_EXPONENT,
            FrostlakeLexer.HEX_LITERAL, FrostlakeLexer.FILE_URL, FrostlakeLexer.LINE_COMMENT,
            FrostlakeLexer.BLOCK_COMMENT, FrostlakeLexer.WS, FrostlakeLexer.REVOKE_CURRENT_GRANTS,
            FrostlakeLexer.RESUME_IF_SUSPENDED, FrostlakeLexer.ABORT_ALL_QUERIES);
        assign(table, SEMI, FrostlakeLexer.SEMI);
        assign(table, LPAREN, FrostlakeLexer.LPAREN);
        assign(table, RPAREN, FrostlakeLexer.RPAREN);
        assign(table, COMMA, FrostlakeLexer.COMMA);
        assign(table, DOT, FrostlakeLexer.DOT);
        assign(table, EQ, FrostlakeLexer.EQ);
        assign(table, MINUS, FrostlakeLexer.MINUS);
        assign(table, INT, FrostlakeLexer.INTEGER_LITERAL);
        assign(table, NUM, FrostlakeLexer.FLOAT_LITERAL);
        assign(table, STR, FrostlakeLexer.STRING_LITERAL, FrostlakeLexer.DOLLAR_QUOTED_STRING);
        assign(table, QUOTED, FrostlakeLexer.QUOTED_IDENTIFIER);
        assign(table, QUESTION, FrostlakeLexer.QUESTION);
        assign(table, COLON, FrostlakeLexer.COLON);
        assign(table, STAGE, FrostlakeLexer.AT);
        assign(table, BOOLEAN, FrostlakeLexer.TRUE, FrostlakeLexer.FALSE);
        assign(table, NULL_WORD, FrostlakeLexer.NULL);
        assign(table, ALL_WORD, FrostlakeLexer.ALL);
        assign(table, UNMODELLED, FrostlakeLexer.WITH);
        assign(table, EXPRESSION, FrostlakeLexer.CASE, FrostlakeLexer.CAST, FrostlakeLexer.TRY_CAST);
        assign(table, WHEN, FrostlakeLexer.WHEN);
        assign(table, CLAUSE_WORD, FrostlakeLexer.CLONE, FrostlakeLexer.COPY, FrostlakeLexer.CONTACT,
            FrostlakeLexer.AFTER, FrostlakeLexer.FORCE, FrostlakeLexer.COMMENT, FrostlakeLexer.APPLY,
            FrostlakeLexer.TAG, FrostlakeLexer.CONDITION, FrostlakeLexer.IF, FrostlakeLexer.EXECUTE,
            FrostlakeLexer.IMMUTABLE);
        assign(table, TABLE, FrostlakeLexer.TABLE);
        assign(table, DROP_OPTION, FrostlakeLexer.CASCADE, FrostlakeLexer.RESTRICT, FrostlakeLexer.PURGE);
        assign(table, STRUCTURED, FrostlakeLexer.ARRAY, FrostlakeLexer.OBJECT, FrostlakeLexer.MAP,
            FrostlakeLexer.VECTOR, FrostlakeLexer.DEFAULT, FrostlakeLexer.INTERVAL);
        assign(table, BARE_TYPE, FrostlakeLexer.FILE, FrostlakeLexer.INTEGER, FrostlakeLexer.INT,
            FrostlakeLexer.BIGINT, FrostlakeLexer.SMALLINT, FrostlakeLexer.TINYINT, FrostlakeLexer.BYTEINT,
            FrostlakeLexer.DOUBLE, FrostlakeLexer.UUID, FrostlakeLexer.GEOGRAPHY, FrostlakeLexer.BOOLEAN,
            FrostlakeLexer.DATE, FrostlakeLexer.VARIANT);
        assign(table, SCALE_TYPE, FrostlakeLexer.NUMBER, FrostlakeLexer.DECIMAL, FrostlakeLexer.NUMERIC,
            FrostlakeLexer.DEC);
        assign(table, LENGTH_TYPE, FrostlakeLexer.FLOAT, FrostlakeLexer.FLOAT4, FrostlakeLexer.FLOAT8,
            FrostlakeLexer.REAL, FrostlakeLexer.VARCHAR, FrostlakeLexer.STRING, FrostlakeLexer.TEXT,
            FrostlakeLexer.CHAR, FrostlakeLexer.NVARCHAR2, FrostlakeLexer.VARCHAR2, FrostlakeLexer.NVARCHAR,
            FrostlakeLexer.NCHAR, FrostlakeLexer.CHARACTER);
        assign(table, PRECISION_TYPE, FrostlakeLexer.GEOMETRY, FrostlakeLexer.DATETIME, FrostlakeLexer.TIME,
            FrostlakeLexer.TIMESTAMP, FrostlakeLexer.TIMESTAMP_NTZ, FrostlakeLexer.TIMESTAMPNTZ,
            FrostlakeLexer.TIMESTAMP_LTZ, FrostlakeLexer.TIMESTAMP_TZ, FrostlakeLexer.BINARY, FrostlakeLexer.VARBINARY,
            FrostlakeLexer.TIMESTAMPLTZ, FrostlakeLexer.TIMESTAMPTZ, FrostlakeLexer.DECFLOAT);
        assign(table, PATH_WORD, FrostlakeLexer.DATA, FrostlakeLexer.INFORMATION);
        assign(table, WORD, FrostlakeLexer.POSITIONAL_PARAMETER, FrostlakeLexer.TARGET_LAG,
            FrostlakeLexer.EMBEDDING_MODEL, FrostlakeLexer.DOWNSTREAM, FrostlakeLexer.INCREMENTAL,
            FrostlakeLexer.INITIALIZE, FrostlakeLexer.ON_CREATE, FrostlakeLexer.ON_SCHEDULE,
            FrostlakeLexer.REFRESH_MODE, FrostlakeLexer.DATA_RETENTION_TIME_IN_DAYS, FrostlakeLexer.READ_ONLY,
            FrostlakeLexer.MIN_NODES, FrostlakeLexer.MAX_NODES, FrostlakeLexer.INSTANCE_FAMILY,
            FrostlakeLexer.AUTO_SUSPEND_SECS, FrostlakeLexer.PLACEMENT_GROUP, FrostlakeLexer.BACKUP_INSTANCE_FAMILIES,
            FrostlakeLexer.SESSIONS, FrostlakeLexer.PAUSE, FrostlakeLexer.SCHEDULE,
            FrostlakeLexer.ALLOW_OVERLAPPING_EXECUTION, FrostlakeLexer.USER_TASK_TIMEOUT_MS,
            FrostlakeLexer.SUSPEND_TASK_AFTER_NUM_FAILURES, FrostlakeLexer.TASK_AUTO_RETRY_ATTEMPTS,
            FrostlakeLexer.USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE, FrostlakeLexer.SERVERLESS_TASK_MAX_STATEMENT_SIZE,
            FrostlakeLexer.TARGET_COMPLETION_INTERVAL, FrostlakeLexer.USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS,
            FrostlakeLexer.NEXTVAL, FrostlakeLexer.CURRVAL, FrostlakeLexer.CURRENT_TIMESTAMP,
            FrostlakeLexer.LOCALTIMESTAMP, FrostlakeLexer.LOCALTIME, FrostlakeLexer.CURRENT_DATE,
            FrostlakeLexer.CURRENT_TIME, FrostlakeLexer.CURRENT_USER, FrostlakeLexer.SHOW_INITIAL_ROWS,
            FrostlakeLexer.AUTO_INGEST, FrostlakeLexer.AWS_SNS_TOPIC, FrostlakeLexer.ON_ERROR,
            FrostlakeLexer.FIELD_DELIMITER, FrostlakeLexer.SKIP_HEADER, FrostlakeLexer.DATE_FORMAT,
            FrostlakeLexer.WAREHOUSE_SIZE, FrostlakeLexer.WAREHOUSE_TYPE, FrostlakeLexer.MAX_BATCH_ROWS,
            FrostlakeLexer.AUTO_SUSPEND, FrostlakeLexer.AUTO_RESUME, FrostlakeLexer.MIN_CLUSTER_COUNT,
            FrostlakeLexer.MAX_CLUSTER_COUNT, FrostlakeLexer.SCALING_POLICY, FrostlakeLexer.INITIALLY_SUSPENDED,
            FrostlakeLexer.MAX_CONCURRENCY_LEVEL, FrostlakeLexer.STATEMENT_QUEUED_TIMEOUT_IN_SECONDS,
            FrostlakeLexer.STATEMENT_TIMEOUT_IN_SECONDS, FrostlakeLexer.ENABLE_QUERY_ACCELERATION,
            FrostlakeLexer.QUERY_ACCELERATION_MAX_SCALE_FACTOR, FrostlakeLexer.GENERATION,
            FrostlakeLexer.RESOURCE_CONSTRAINT, FrostlakeLexer.WAIT_FOR_COMPLETION, FrostlakeLexer.COMPUTE_POOL,
            FrostlakeLexer.EXTERNAL_ACCESS_INTEGRATIONS, FrostlakeLexer.IDLE_AUTO_SHUTDOWN_TIME_SECONDS,
            FrostlakeLexer.MAIN_FILE, FrostlakeLexer.QUERY_WAREHOUSE, FrostlakeLexer.RUNTIME_NAME,
            FrostlakeLexer.TITLE, FrostlakeLexer.STANDARD, FrostlakeLexer.ECONOMY,
            FrostlakeLexer.MULTI_STATEMENT_COUNT, FrostlakeLexer.FILE_FORMAT, FrostlakeLexer.ENCRYPTION,
            FrostlakeLexer.VALIDATION_MODE, FrostlakeLexer.SIZE_LIMIT, FrostlakeLexer.MATCH_BY_COLUMN_NAME,
            FrostlakeLexer.HEADER, FrostlakeLexer.SINGLE, FrostlakeLexer.MAX_FILE_SIZE, FrostlakeLexer.COMPRESSION,
            FrostlakeLexer.RECORD_DELIMITER, FrostlakeLexer.JAVASCRIPT, FrostlakeLexer.JAVA, FrostlakeLexer.SCALA,
            FrostlakeLexer.PYTHON, FrostlakeLexer.RUNTIME_VERSION, FrostlakeLexer.HANDLER, FrostlakeLexer.SQL,
            FrostlakeLexer.ADMIN_NAME, FrostlakeLexer.ADMIN_PASSWORD, FrostlakeLexer.ADMIN_RSA_PUBLIC_KEY,
            FrostlakeLexer.ADMIN_USER_TYPE, FrostlakeLexer.DEFAULT_ROLE, FrostlakeLexer.DEFAULT_WAREHOUSE,
            FrostlakeLexer.DEFAULT_NAMESPACE, FrostlakeLexer.DEFAULT_SECONDARY_ROLES, FrostlakeLexer.LOGIN_NAME,
            FrostlakeLexer.DISPLAY_NAME, FrostlakeLexer.FIRST_NAME, FrostlakeLexer.MIDDLE_NAME,
            FrostlakeLexer.LAST_NAME, FrostlakeLexer.MUST_CHANGE_PASSWORD, FrostlakeLexer.EMAIL,
            FrostlakeLexer.DISABLED, FrostlakeLexer.GENERATOR, FrostlakeLexer.ROWCOUNT, FrostlakeLexer.TIMELIMIT,
            FrostlakeLexer.SPLIT_TO_TABLE, FrostlakeLexer.DELIMITER, FrostlakeLexer.FLATTEN, FrostlakeLexer.PATH,
            FrostlakeLexer.NETWORK_POLICY, FrostlakeLexer.ALLOWED_VALUES_SEQUENCE, FrostlakeLexer.ON_CONFLICT,
            FrostlakeLexer.PROPAGATE, FrostlakeLexer.RUNBOOK, FrostlakeLexer.SUSPEND_ALERT_AFTER_NUM_FAILURES,
            FrostlakeLexer.RESOURCE_MONITOR, FrostlakeLexer.MASKING_POLICY, FrostlakeLexer.ROW_ACCESS_POLICY,
            FrostlakeLexer.SESSION_POLICY, FrostlakeLexer.OTHER);
        assign(table, RESERVED, FrostlakeLexer.SELECT, FrostlakeLexer.DISTINCT, FrostlakeLexer.FROM,
            FrostlakeLexer.WHERE, FrostlakeLexer.AND, FrostlakeLexer.OR, FrostlakeLexer.NOT, FrostlakeLexer.IN,
            FrostlakeLexer.TO, FrostlakeLexer.IS, FrostlakeLexer.AS, FrostlakeLexer.GROUP, FrostlakeLexer.BY,
            FrostlakeLexer.BETWEEN, FrostlakeLexer.LIKE, FrostlakeLexer.ILIKE, FrostlakeLexer.RLIKE,
            FrostlakeLexer.REGEXP, FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY, FrostlakeLexer.ORDER,
            FrostlakeLexer.ROWS, FrostlakeLexer.FOLLOWING, FrostlakeLexer.CURRENT, FrostlakeLexer.CREATE,
            FrostlakeLexer.DROP, FrostlakeLexer.ALTER, FrostlakeLexer.COLUMN, FrostlakeLexer.INSERT,
            FrostlakeLexer.INTO, FrostlakeLexer.VALUES, FrostlakeLexer.UPDATE, FrostlakeLexer.DELETE,
            FrostlakeLexer.SET, FrostlakeLexer.UNIQUE, FrostlakeLexer.UNION, FrostlakeLexer.INTERSECT,
            FrostlakeLexer.ON, FrostlakeLexer.OF, FrostlakeLexer.CONNECT, FrostlakeLexer.CHECK, FrostlakeLexer.START,
            FrostlakeLexer.INCREMENT, FrostlakeLexer.THEN, FrostlakeLexer.GRANT, FrostlakeLexer.REVOKE,
            FrostlakeLexer.ANY, FrostlakeLexer.SOME, FrostlakeLexer.ROW, FrostlakeLexer.SAMPLE,
            FrostlakeLexer.TABLESAMPLE, FrostlakeLexer.EXISTS, FrostlakeLexer.ELSE, FrostlakeLexer.FOR,
            FrostlakeLexer.MINUS_KW);
        return table;
    }

    private static void assign(final int[] table, final int kind, final int... types) {
        for (final int type : types) {
            table[type] = kind;
        }
    }
}
