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

package dev.frostlake.executor.expressions;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A SQL UDF body as the account reads it: inside a frame of parentheses of its own, one character on either side,
 * read as a statement that a semicolon ends. A body that closes that frame itself and then writes a semicolon
 * therefore holds only what stands before the semicolon, and nothing after it is ever read: {@code 1);garbage here}
 * answers 1, {@code (1)) + 1;} answers 2 (the frame groups what it holds), {@code SELECT ABS(1));} answers 1 and a
 * table function's {@code SELECT 1 AS a);} answers its row. The statement is compiled at CREATE like any other
 * body, so {@code 'abc');} under RETURNS INT is refused for its type.
 */
public final class SqlUdfBodyFrame {

    /** How many bodies {@link #executableBody} remembers. */
    private static final int REMEMBERED_BODIES = 256;

    /** The longest body {@link #executableBody} remembers. */
    private static final int REMEMBERED_LENGTH = 4096;

    /** The text each body a call runs, so a call made once per row reads its body's frame once. */
    private static final Map<String, String> EXECUTABLE =
        new LinkedHashMap<String, String>(REMEMBERED_BODIES * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
                return size() > REMEMBERED_BODIES;
            }
        };

    /** The statement the semicolon ends. */
    private final String statement;

    /** Whether {@link #statement} opens with the frame's own parenthesis rather than with the body's first character. */
    private final boolean framed;

    private SqlUdfBodyFrame(final String statement, final boolean framed) {
        this.statement = statement;
        this.framed = framed;
    }

    /**
     * How {@code body} reads when it closes its own frame and a semicolon ends the statement there, or null when it
     * does not: the frame is still open at the body's first semicolon, a parenthesis closes more than the frame
     * opened, or the body has no semicolon at all.
     *
     * @param body the body as written
     * @return the statement the frame ends, or null
     */
    public static SqlUdfBodyFrame closedAtSemicolon(final String body) {
        if (body == null) {
            return null;
        }
        final String framedText = "(" + body + ")";
        int depth = 0;
        Token close = null;
        Token previous = null;
        for (final Token token : spokenTokens(framedText)) {
            final int type = token.getType();
            if (type == FrostlakeLexer.SEMI) {
                if (depth != 0 || close == null) {
                    return null;
                }
                if (previous == close) {
                    // Only the frame's closing parenthesis stands before the semicolon: the statement is what the
                    // frame holds, in the body's own positions.
                    return new SqlUdfBodyFrame(body.substring(0, close.getStartIndex() - 1), false);
                }
                return new SqlUdfBodyFrame(framedText.substring(0, token.getStartIndex()), true);
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth < 0) {
                    return null;
                }
                if (depth == 0 && close == null) {
                    close = token;
                }
            }
            previous = token;
        }
        return null;
    }

    /**
     * The text a call runs for {@code body}: the statement its frame ends at a semicolon, or the body itself.
     *
     * @param body the body as written
     * @return the body to run
     */
    public static String executableBody(final String body) {
        if (body == null) {
            return null;
        }
        synchronized (EXECUTABLE) {
            final String remembered = EXECUTABLE.get(body);
            if (remembered != null) {
                return remembered;
            }
        }
        final SqlUdfBodyFrame frame = closedAtSemicolon(body);
        final String executable = frame == null ? body : frame.statement;
        if (body.length() <= REMEMBERED_LENGTH) {
            synchronized (EXECUTABLE) {
                EXECUTABLE.put(body, executable);
            }
        }
        return executable;
    }

    /**
     * The row the framed body reads as — a parenthesised list of two or more values, {@code 1,2} or {@code x, 'a'} —
     * or null when it reads as anything else. The account compiles such a body and refuses it for its type, a ROW
     * no declared type takes (live-verified). The tree stands in the frame's positions.
     *
     * @param body the body as written
     * @return the framed body's row, or null
     */
    public static FrostlakeParser.ExprTupleContext rowOf(final String body) {
        if (body == null) {
            return null;
        }
        String text = "(" + body + ")";
        while (true) {
            final FrostlakeParser.ExprTupleContext row = rowTree(text);
            if (row != null) {
                return row;
            }
            // Parentheses around the whole row group it and nothing more: blank them out, keeping every position.
            final List<Token> spoken = spokenTokens(text);
            if (spoken.size() < 2 || closingIndex(spoken) != spoken.size() - 1) {
                return null;
            }
            final int open = spoken.get(0).getStartIndex();
            final int close = spoken.get(spoken.size() - 1).getStartIndex();
            text = text.substring(0, open) + " " + text.substring(open + 1, close) + " " + text.substring(close + 1);
        }
    }

    /** The text read as one parenthesised row, or null. */
    private static FrostlakeParser.ExprTupleContext rowTree(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            final FrostlakeParser.ExprTupleContext row = parser.exprTuple();
            return parser.getCurrentToken().getType() == Token.EOF ? row : null;
        } catch (final RuntimeException notARow) {
            return null;
        }
    }

    /** The index of the token closing the parenthesis that opens {@code spoken}, or -1. */
    private static int closingIndex(final List<Token> spoken) {
        if (spoken.get(0).getType() != FrostlakeLexer.LPAREN) {
            return -1;
        }
        int depth = 0;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The statement the semicolon ends: what the frame holds, in the body's own positions, or — when something
     * follows the frame's closing parenthesis before the semicolon — the whole framed statement, which opens with
     * the frame's own parenthesis.
     *
     * @return the statement's text
     */
    public String statement() {
        return statement;
    }

    /**
     * Whether {@link #statement()} opens with the frame's own parenthesis, so that its positions are the frame's
     * rather than the body's.
     *
     * @return whether the statement is the whole framed statement
     */
    public boolean isFramed() {
        return framed;
    }

    /** The text's spoken tokens: comments and whitespace dropped, end of input excluded. */
    private static List<Token> spokenTokens(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                spoken.add(token);
            }
        }
        return spoken;
    }
}
