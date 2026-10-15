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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * What live refuses next once a statement of a block has been refused at a word and the statement's semicolon
 * follows. Live gives the statement up together with the construct whose statement it is — the BEGIN … END block,
 * or the body of the IF, CASE, FOR, WHILE, LOOP or REPEAT — as if that construct had closed there, and reads on from
 * the construct around it (live-verified):
 *
 * <pre>
 *   BEGIN SHOW TABLES IN c i x; SELECT 1; END;                  the END: a SQL statement reads on outside a block
 *   BEGIN SHOW TABLES IN c i x; RETURN 1; END;                  the RETURN: no scripting statement stands there
 *   BEGIN BEGIN SHOW TABLES IN c i x; RETURN 1; END; END;       the last END: the RETURN is the outer block's
 *   BEGIN BEGIN SHOW TABLES IN c i x; END; RETURN 1; END;       the RETURN: the first END closed the outer block
 *   BEGIN IF (a) THEN SHOW TABLES IN c i x; END IF; END;        the last END: END IF closed the block
 *   BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN c i x; END FOR; …   the FOR
 *   BEGIN IF (a) THEN SHOW TABLES IN c i x; ELSE …              the ELSE: no IF is open any more
 *   BEGIN LOOP IF (a) THEN SHOW TABLES IN c i x; END IF; …      that IF: the loop's END wants LOOP after it
 * </pre>
 *
 * <p>An END followed by another construct's word gives that construct up as well, and what the rest then refuses
 * is one line more: {@code … END IF; BREAK; END LOOP; END;} after the IF above is the last END.
 *
 * <p>A block's END takes one word after it as a name — IF, CASE, WHILE, LOOP, REPEAT or any other name, but not the
 * reserved FOR — so an END IF met where a block is open closes the block. Outside every block a SQL statement and
 * a whole block read on, a SQL statement's own fault is the line, and a scripting statement, a VALUES, a stray END
 * and a branch keyword with no construct to continue (ELSE, ELSEIF, WHEN, UNTIL, EXCEPTION) are refused at their
 * first word. A statement of an exception handler gives up the handler's block.
 */
public final class BlockReadOn {

    private BlockReadOn() {
    }

    /**
     * The default-channel tokens of a parse, its end of input last.
     *
     * @param stream the parse's token stream
     * @return the tokens
     */
    public static List<Token> spoken(final TokenStream stream) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                tokens.add(stream.get(i));
            }
        }
        return tokens;
    }

    /**
     * The default-channel tokens of a text, its end of input last.
     *
     * @param sql the text
     * @return the tokens
     */
    public static List<Token> spoken(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        return spoken(new CommonTokenStream(lexer));
    }

    /**
     * The index of the token that starts at a character of the text.
     *
     * @param tokens the text's tokens
     * @param start  the character index
     * @return the token's index, or -1
     */
    public static int indexAt(final List<Token> tokens, final int start) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getStartIndex() == start && tokens.get(i).getType() != Token.EOF) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The index of the token a syntax-error line names.
     *
     * @param tokens the text's tokens
     * @param line   the line, {@code syntax error line 1 at position 9 unexpected 'x'.}
     * @return the token's index, or -1 when the line names none of them
     */
    public static int indexOfLine(final List<Token> tokens, final String line) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getType() != Token.EOF && SyntaxErrorListener.sentence(tokens.get(i)).equals(line)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The syntax-error line that names a token.
     *
     * @param token the refused token
     * @return its line
     */
    public static String lineOf(final Token token) {
        return SyntaxErrorListener.sentence(token);
    }

    /**
     * The index of the semicolon that ends the statement a token stands in, the token itself when it is one.
     *
     * @param tokens the text's tokens
     * @param from   the index of a token of the statement
     * @return the semicolon's index, or -1 when the text ends first
     */
    public static int semicolonFrom(final List<Token> tokens, final int from) {
        final int end = statementEnd(tokens, from);
        return end < tokens.size() && tokens.get(end).getType() == FrostlakeLexer.SEMI ? end : -1;
    }

    /**
     * Whether the statement that ends at a semicolon stands in a block or in a control construct's body.
     *
     * @param tokens    the text's tokens ({@link #spoken})
     * @param semicolon the index of the statement's semicolon
     * @return whether a construct is open there
     */
    public static boolean standsInBlock(final List<Token> tokens, final int semicolon) {
        final Deque<OpenConstruct> open = new ArrayDeque<>();
        read(tokens, 0, semicolon, open, false);
        return !open.isEmpty();
    }

    /**
     * Whether the statement that ends at a token is a statement of a BEGIN … END block itself — its body or one of
     * its handlers — and not of a control construct's body.
     *
     * @param tokens the text's tokens ({@link #spoken})
     * @param end    the index of the statement's semicolon, or of the token it runs into
     * @return whether the innermost construct open there is a block
     */
    public static boolean standsDirectlyInBlock(final List<Token> tokens, final int end) {
        final Deque<OpenConstruct> open = new ArrayDeque<>();
        read(tokens, 0, end, open, false);
        return !open.isEmpty() && open.peek().closingWord() < 0;
    }

    /**
     * The token refused once the block statement that ends at a semicolon is given up.
     *
     * @param tokens    the text's tokens ({@link #spoken})
     * @param semicolon the index of the given-up statement's semicolon
     * @return the token, or null when the rest reads on to the end or its fault names no token
     */
    public static Token refusedAfter(final List<Token> tokens, final int semicolon) {
        final ReadOnFault fault = faultAfter(tokens, semicolon);
        return fault == null ? null : fault.token();
    }

    /**
     * As {@link #refusedAfter(List, int)}, for the statement a token of a parse stands in.
     *
     * @param stream the parse's token stream
     * @param inside a token of the given-up statement, at or before its semicolon
     * @return the token, or null when the statement has no semicolon or the rest reads on to the end
     */
    public static Token refusedAfter(final TokenStream stream, final Token inside) {
        final List<Token> tokens = spoken(stream);
        final int at = indexAt(tokens, inside.getStartIndex());
        final int semicolon = at < 0 ? -1 : semicolonFrom(tokens, at);
        return semicolon < 0 ? null : refusedAfter(tokens, semicolon);
    }

    /**
     * The line of what is refused once the block statement that ends at a semicolon is given up.
     *
     * @param tokens    the text's tokens ({@link #spoken})
     * @param semicolon the index of the given-up statement's semicolon
     * @return the line, or null when the rest reads on to the end
     */
    public static String lineAfter(final List<Token> tokens, final int semicolon) {
        final ReadOnFault fault = faultAfter(tokens, semicolon);
        return fault == null ? null : fault.line();
    }

    /**
     * As {@link #lineAfter(List, int)}, for the statement a token of a parse stands in.
     *
     * @param stream the parse's token stream
     * @param inside a token of the given-up statement, at or before its semicolon
     * @return the line, or null when the statement has no semicolon or the rest reads on to the end
     */
    public static String lineAfter(final TokenStream stream, final Token inside) {
        final List<Token> tokens = spoken(stream);
        final int at = indexAt(tokens, inside.getStartIndex());
        final int semicolon = at < 0 ? -1 : semicolonFrom(tokens, at);
        return semicolon < 0 ? null : lineAfter(tokens, semicolon);
    }

    /**
     * The lines of what is refused once the block statement that ends at a semicolon is given up: the line
     * {@link #lineAfter(List, int)} answers and, when it names a branch keyword, the one after it
     * ({@link #afterBranchKeyword}).
     *
     * @param tokens    the text's tokens ({@link #spoken})
     * @param semicolon the index of the given-up statement's semicolon
     * @return the lines, none when the rest reads on to the end
     */
    public static List<String> linesAfter(final List<Token> tokens, final int semicolon) {
        final List<String> lines = new ArrayList<>();
        final List<ReadOnFault> faults = faultsAfter(tokens, semicolon);
        if (faults.isEmpty()) {
            return lines;
        }
        final ReadOnFault fault = faults.get(0);
        lines.add(fault.line());
        if (faults.size() > 1) {
            lines.add(faults.get(1).line());
            return lines;
        }
        final Token again = fault.token() == null || !fault.inConstruct() ? null
            : afterBranchKeyword(tokens, fault.token());
        if (again != null) {
            lines.add(SyntaxErrorListener.sentence(again));
        }
        return lines;
    }

    /**
     * As {@link #linesAfter(List, int)}, for the statement a token of a parse stands in.
     *
     * @param stream the parse's token stream
     * @param inside a token of the given-up statement, at or before its semicolon
     * @return the lines, none when the statement has no semicolon or the rest reads on to the end
     */
    public static List<String> linesAfter(final TokenStream stream, final Token inside) {
        final List<Token> tokens = spoken(stream);
        final int at = indexAt(tokens, inside.getStartIndex());
        final int semicolon = at < 0 ? -1 : semicolonFrom(tokens, at);
        return semicolon < 0 ? new ArrayList<String>() : linesAfter(tokens, semicolon);
    }

    /**
     * The token stacked after the one {@link #refusedAfter(TokenStream, Token)} answered: the one after a branch
     * keyword refused while a construct was still open ({@link #afterBranchKeyword}), or what the rest refuses once
     * an END's wrong word has given its construct up.
     *
     * @param stream    the parse's token stream
     * @param semicolon the given-up statement's semicolon
     * @param refused   the token refused after it
     * @return the token stacked after it, or null
     */
    public static Token followerAfter(final TokenStream stream, final Token semicolon, final Token refused) {
        final List<Token> tokens = spoken(stream);
        final int at = indexAt(tokens, semicolon.getStartIndex());
        final List<ReadOnFault> faults = at < 0 ? new ArrayList<ReadOnFault>() : faultsAfter(tokens, at);
        if (faults.isEmpty() || faults.get(0).token() == null
                || faults.get(0).token().getStartIndex() != refused.getStartIndex()) {
            return null;
        }
        if (faults.size() > 1) {
            return faults.get(1).token();
        }
        return faults.get(0).inConstruct() ? afterBranchKeyword(tokens, faults.get(0).token()) : null;
    }

    /**
     * The token refused once the block statement that ends at a semicolon is given up, when a statement read on
     * between the two; null when the rest is refused straight away, at its first word or at a construct's END.
     *
     * @param stream    the parse's token stream
     * @param semicolon the given-up statement's semicolon
     * @return the token, or null
     */
    public static Token refusedPastStatement(final TokenStream stream, final Token semicolon) {
        final List<Token> tokens = spoken(stream);
        final int at = indexAt(tokens, semicolon.getStartIndex());
        final ReadOnFault fault = at < 0 ? null : faultAfter(tokens, at);
        return fault == null || fault.statementsRead() == 0 ? null : fault.token();
    }

    /**
     * The token live refuses after a branch keyword it refused for want of a construct to continue — an ELSE, an
     * ELSEIF, a WHEN or an UNTIL that follows a given-up statement's semicolon — while a block or a construct is
     * still open around it; outside every block the keyword ends the report. The first word from the keyword on
     * that reads as a name is taken and the token after it is refused; a semicolon there is read past and the token
     * after it refused instead. END straight after the name, no name before the statement's semicolon, and a CALL
     * after the keyword end the report (live-verified):
     *
     * <pre>
     *   … ; ELSE RETURN 1; END IF; END;            'ELSE', then '1'
     *   … ; ELSE RETURN; END IF; END;              'ELSE', then the 'END' of END IF
     *   … ; ELSEIF (TRUE) THEN RETURN 1; …         'ELSEIF', then '(': ELSEIF itself reads as the name
     *   … ; WHEN FALSE THEN RETURN 1; …            'WHEN', then 'FALSE'
     *   … ; ELSE SELECT 1; END IF; END;            'ELSE' alone
     * </pre>
     *
     * @param tokens  the text's tokens ({@link #spoken})
     * @param keyword the refused token
     * @return the token refused after it, or null when the report ends or the token is no such keyword
     */
    static Token afterBranchKeyword(final List<Token> tokens, final Token keyword) {
        final int type = keyword.getType();
        final int at = indexAt(tokens, keyword.getStartIndex());
        if (at < 1 || tokens.get(at - 1).getType() != FrostlakeLexer.SEMI
                || type != FrostlakeLexer.ELSE && type != FrostlakeLexer.ELSEIF && type != FrostlakeLexer.WHEN
                    && type != FrostlakeLexer.UNTIL
                || tokens.get(at + 1).getType() == FrostlakeLexer.CALL) {
            return null;
        }
        int name = -1;
        for (int k = at; k < tokens.size() && name < 0; k++) {
            final int read = tokens.get(k).getType();
            if (read == FrostlakeLexer.SEMI || read == Token.EOF) {
                return null;
            }
            if (SyntaxErrorListener.isNameLike(tokens.get(k))) {
                name = k;
            }
        }
        int next = name + 1;
        if (name < 0 || tokens.get(next).getType() == FrostlakeLexer.END) {
            return null;
        }
        while (tokens.get(next).getType() == FrostlakeLexer.SEMI) {
            next++;
        }
        return tokens.get(next).getType() == Token.EOF ? null : tokens.get(next);
    }

    /** The fault met after the given-up statement and, after an END's wrong word, the one met next. */
    private static List<ReadOnFault> faultsAfter(final List<Token> tokens, final int semicolon) {
        final List<ReadOnFault> faults = new ArrayList<>();
        final Deque<OpenConstruct> open = new ArrayDeque<>();
        read(tokens, 0, semicolon, open, false);
        if (!open.isEmpty()) {
            open.pop();
        }
        final ReadOnFault fault = read(tokens, semicolon + 1, tokens.size() - 1, open, true);
        if (fault != null) {
            faults.add(fault);
            if (fault.resumesAt() >= 0) {
                final ReadOnFault next = read(tokens, fault.resumesAt(), tokens.size() - 1, open, true);
                if (next != null) {
                    faults.add(next);
                }
            }
        }
        return faults;
    }

    private static ReadOnFault faultAfter(final List<Token> tokens, final int semicolon) {
        final List<ReadOnFault> faults = faultsAfter(tokens, semicolon);
        return faults.isEmpty() ? null : faults.get(0);
    }

    /**
     * Reads statements from {@code from} up to {@code to}, keeping {@code open} as the constructs open at each. When
     * {@code refuses}, the first token refused is returned; otherwise nothing is refused and the reading only tracks
     * what is open.
     */
    private static ReadOnFault read(final List<Token> tokens, final int from, final int to,
                                    final Deque<OpenConstruct> open, final boolean refuses) {
        int statements = 0;
        int i = from;
        while (i <= to && i < tokens.size()) {
            final Token token = tokens.get(i);
            final int type = token.getType();
            final OpenConstruct top = open.peek();
            if (type == Token.EOF) {
                return null;
            }
            if (type == FrostlakeLexer.SEMI) {
                i++;
            } else if (type == FrostlakeLexer.END) {
                if (top == null) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                final Token next = tokens.get(i + 1);
                if (top.kind() == BlockConstruct.BLOCK) {
                    open.pop();
                    i++;
                    if (next.getType() != FrostlakeLexer.SEMI && next.getType() != Token.EOF) {
                        if (!SyntaxErrorListener.isNameLike(next) || next.getType() == FrostlakeLexer.FOR
                                || next.getType() == FrostlakeLexer.END) {
                            return refuses ? new ReadOnFault(next).within(!open.isEmpty(), statements) : null;
                        }
                        i++;
                        final Token after = tokens.get(i);
                        if (after.getType() != FrostlakeLexer.SEMI && after.getType() != Token.EOF) {
                            return refuses ? new ReadOnFault(after).within(!open.isEmpty(), statements) : null;
                        }
                    }
                } else {
                    if (next.getType() != top.closingWord()) {
                        open.pop();
                        return refuses ? new ReadOnFault(next).within(!open.isEmpty(), statements).resumingAt(i + 2)
                            : null;
                    }
                    open.pop();
                    i += 2;
                    // A loop's END may carry the loop's label.
                    if (tokens.get(i).getType() != FrostlakeLexer.SEMI && tokens.get(i).getType() != Token.EOF) {
                        i++;
                    }
                }
            } else if (type == FrostlakeLexer.ELSE) {
                if (top == null || top.kind() != BlockConstruct.IF && top.kind() != BlockConstruct.CASE) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                i++;
            } else if (type == FrostlakeLexer.ELSEIF) {
                if (top == null || top.kind() != BlockConstruct.IF) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                i = after(tokens, i, FrostlakeLexer.THEN, FrostlakeLexer.THEN);
            } else if (type == FrostlakeLexer.WHEN) {
                if (top == null || top.kind() != BlockConstruct.CASE
                        && !(top.kind() == BlockConstruct.BLOCK && top.inHandlers())) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                i = after(tokens, i, FrostlakeLexer.THEN, FrostlakeLexer.THEN);
            } else if (type == FrostlakeLexer.UNTIL) {
                if (top == null || top.kind() != BlockConstruct.REPEAT) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                i = after(tokens, i, FrostlakeLexer.END, FrostlakeLexer.END) - 1;
            } else if (type == FrostlakeLexer.EXCEPTION) {
                if (top == null || top.kind() != BlockConstruct.BLOCK) {
                    return refuses ? new ReadOnFault(token).within(top != null, statements) : null;
                }
                top.enterHandlers();
                i++;
            } else if (type == FrostlakeLexer.BEGIN && opensBlock(tokens.get(i + 1))) {
                open.push(new OpenConstruct(BlockConstruct.BLOCK, -1));
                i++;
            } else if (type == FrostlakeLexer.DECLARE) {
                i = afterDeclarations(tokens, i);
                open.push(new OpenConstruct(BlockConstruct.BLOCK, -1));
            } else if (type == FrostlakeLexer.IF || type == FrostlakeLexer.CASE || type == FrostlakeLexer.FOR
                    || type == FrostlakeLexer.WHILE || type == FrostlakeLexer.LOOP || type == FrostlakeLexer.REPEAT) {
                if (top == null && refuses) {
                    return new ReadOnFault(token).within(false, statements);
                }
                i = opened(tokens, i, open);
            } else {
                final int end = statementEnd(tokens, i);
                if (top == null && refuses) {
                    final ReadOnFault fault = type == FrostlakeLexer.VALUES ? new ReadOnFault(token)
                        : StatementAlone.firstFault(tokens, i, end);
                    if (fault != null) {
                        return fault.within(false, statements);
                    }
                }
                statements++;
                i = end;
            }
        }
        return null;
    }

    /** Opens the control construct whose keyword stands at {@code i}; the index of its body's first token. */
    private static int opened(final List<Token> tokens, final int i, final Deque<OpenConstruct> open) {
        final int type = tokens.get(i).getType();
        if (type == FrostlakeLexer.IF) {
            open.push(new OpenConstruct(BlockConstruct.IF, FrostlakeLexer.IF));
            return after(tokens, i, FrostlakeLexer.THEN, FrostlakeLexer.THEN);
        }
        if (type == FrostlakeLexer.CASE) {
            open.push(new OpenConstruct(BlockConstruct.CASE, FrostlakeLexer.CASE));
            return after(tokens, i, FrostlakeLexer.WHEN, FrostlakeLexer.WHEN) - 1;
        }
        if (type == FrostlakeLexer.LOOP) {
            open.push(new OpenConstruct(BlockConstruct.LOOP, FrostlakeLexer.LOOP));
            return i + 1;
        }
        if (type == FrostlakeLexer.REPEAT) {
            open.push(new OpenConstruct(BlockConstruct.REPEAT, FrostlakeLexer.REPEAT));
            return i + 1;
        }
        // A FOR or a WHILE whose body opens with LOOP closes with END LOOP.
        final int body = after(tokens, i, FrostlakeLexer.DO, FrostlakeLexer.LOOP);
        final boolean loop = tokens.get(body - 1).getType() == FrostlakeLexer.LOOP;
        open.push(new OpenConstruct(type == FrostlakeLexer.FOR ? BlockConstruct.FOR : BlockConstruct.WHILE,
            loop ? FrostlakeLexer.LOOP : type));
        return body;
    }

    /** Whether a BEGIN followed by {@code next} opens a block: BEGIN alone, or with a name, starts a transaction. */
    private static boolean opensBlock(final Token next) {
        final int type = next.getType();
        return type != FrostlakeLexer.SEMI && type != Token.EOF && type != FrostlakeLexer.TRANSACTION
            && type != FrostlakeLexer.WORK && type != FrostlakeLexer.NAME;
    }

    /** The index after the BEGIN that follows a DECLARE section opened at {@code i}. */
    private static int afterDeclarations(final List<Token> tokens, final int i) {
        for (int k = i + 1; k < tokens.size(); k++) {
            final int type = tokens.get(k).getType();
            if (type == Token.EOF) {
                return k;
            }
            if (type == FrostlakeLexer.BEGIN && tokens.get(k - 1).getType() == FrostlakeLexer.SEMI) {
                return k + 1;
            }
        }
        return tokens.size() - 1;
    }

    /**
     * The index after the first token of either type that follows {@code i} outside every bracket; the end of the
     * input when there is none.
     */
    private static int after(final List<Token> tokens, final int i, final int one, final int other) {
        int depth = 0;
        for (int k = i + 1; k < tokens.size(); k++) {
            final int type = tokens.get(k).getType();
            if (type == Token.EOF) {
                return k;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && depth > 0) {
                depth--;
            } else if (depth == 0 && (type == one || type == other)) {
                return k + 1;
            }
        }
        return tokens.size() - 1;
    }

    /** The index of the semicolon that ends the statement {@code i} stands in, outside every bracket, or of the end. */
    private static int statementEnd(final List<Token> tokens, final int i) {
        int depth = 0;
        for (int k = i; k < tokens.size(); k++) {
            final int type = tokens.get(k).getType();
            if (type == Token.EOF) {
                return k;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && depth > 0) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.SEMI) {
                return k;
            }
        }
        return tokens.size() - 1;
    }
}
