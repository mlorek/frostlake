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
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The lines live reports for a word refused after a keyword written alone as a SHOW listing's scope
 * ({@link ShowScopeWords#takesInstance}) when the statement stands in a block. Live names the refused word, and then
 * one token more, and nothing after it (live-verified):
 *
 * <pre>
 *   BEGIN SHOW TABLES IN USER x y; END;                'x', then 'y'     the token after the first name
 *   BEGIN SHOW TABLES IN DATASET y LIMIT 1; END;       'y', then 'LIMIT'
 *   BEGIN SHOW TABLES IN DATASET y; RETURN 1; END;     'y', then 'RETURN'  past the semicolon, the first thing the
 *   BEGIN SHOW TABLES IN DATASET y; SELECT 1; END;     'y', then 'END'     rest read outside the block cannot take
 *   BEGIN SHOW TABLES IN DATASET y END;                'y' alone          END straight after the name
 *   IF (TRUE) THEN SHOW TABLES IN USER x y; END IF;    'x', then what the rest refuses once the IF is given up
 * </pre>
 *
 * <p>Past the statement's semicolon the rest is read from the construct around the statement's own, which is given
 * up with it: see {@link BlockReadOn}. A statement of a control construct's body is read on that way even when a
 * word follows the first name. Outside a block the parse's own line stands alone.
 *
 * <p>A refused token that reads as no name is named twice: {@code BEGIN SHOW TABLES IN DATASET 1; END;} is '1' and
 * '1' again.
 */
final class KeywordScopeLines {

    private final Token refused;
    private final List<String> stacked;

    private KeywordScopeLines(final Token refused, final List<String> stacked) {
        this.refused = refused;
        this.stacked = stacked;
    }

    /**
     * The lines of the parse's first fault when it refused a word after a keyword SHOW scope inside a block, or null.
     *
     * @param recognizer the parser
     * @param e          the fault
     * @param refused    the token the fault names
     * @return the lines, or null for any other fault
     */
    static KeywordScopeLines of(final Recognizer<?, ?> recognizer, final RecognitionException e, final Token refused) {
        if (!(recognizer instanceof Parser) || refused.getType() == Token.EOF || e == null) {
            return null;
        }
        // A word is refused inside the listing; a token that is no name is met once the listing has been read.
        final boolean inListing = e instanceof NoViableAltException
            && e.getCtx() instanceof FrostlakeParser.ShowStatementContext;
        final boolean afterListing = e.getCtx() instanceof FrostlakeParser.StatementListContext
            && !SyntaxErrorListener.isNameLike(refused);
        if (!inListing && !afterListing) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final TokenStream stream = parser.getInputStream();
        if (!ShowScopeWords.refusedAfterScopeWord(stream, refused)) {
            return null;
        }
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        // A listing a RESULTSET is filled from is no statement of the list: LET r RESULTSET := (SHOW TABLES IN DATASET
        // y); is 'y' alone (live-verified).
        if (e.getCtx().getParent() instanceof FrostlakeParser.ResultSetStatementContext) {
            return new KeywordScopeLines(refused, new ArrayList<String>());
        }
        final ParserRuleContext owner = statementListOwner((ParserRuleContext) e.getCtx());
        if (owner != null && !SyntaxErrorListener.isNameLike(refused)) {
            // ★ A TOKEN THAT IS NO NAME — a number, a string, a bind's colon, a NULL — is named twice in a block's
            // own statement; in a control construct's body the second line is the rest's, read on.
            final List<String> again = new ArrayList<>();
            if (owner instanceof FrostlakeParser.BeginEndBlockContext
                    || owner instanceof FrostlakeParser.ExceptionHandlerContext) {
                again.add(SyntaxErrorListener.sentence(refused));
            } else {
                again.addAll(BlockReadOn.linesAfter(stream, refused));
            }
            return new KeywordScopeLines(refused, again);
        }
        final Token name = firstNameBeforeSemicolon(parser, refused);
        if (owner == null || name == null) {
            return null;
        }
        final Token after = nextSpoken(stream, name.getTokenIndex());
        if (after == null || after.getType() == Token.EOF || after.getType() == FrostlakeLexer.END) {
            return new KeywordScopeLines(refused, new ArrayList<String>());
        }
        final boolean block = owner instanceof FrostlakeParser.BeginEndBlockContext
            || owner instanceof FrostlakeParser.ExceptionHandlerContext;
        if (after.getType() != FrostlakeLexer.SEMI && block) {
            final List<String> pair = new ArrayList<>();
            pair.add(SyntaxErrorListener.sentence(after));
            return new KeywordScopeLines(refused, pair);
        }
        return new KeywordScopeLines(refused, BlockReadOn.linesAfter(stream, after));
    }

    /**
     * The lines to report: the refused word, then the stacked lines.
     *
     * @return the lines
     */
    List<String> lines() {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        lines.addAll(stacked);
        return lines;
    }

    /**
     * The construct whose statement list holds the statement: a block, an exception handler, or the body of an IF, a
     * LOOP, a CASE, a FOR, a WHILE or a REPEAT — or null outside every one of them.
     */
    private static ParserRuleContext statementListOwner(final ParserRuleContext statement) {
        for (ParserRuleContext up = statement; up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                final ParserRuleContext owner = up.getParent();
                return owner instanceof FrostlakeParser.BeginEndBlockContext
                    || owner instanceof FrostlakeParser.ExceptionHandlerContext
                    || owner instanceof FrostlakeParser.IfStatementContext
                    || owner instanceof FrostlakeParser.LoopStatementContext
                    || owner instanceof FrostlakeParser.CaseStatementContext
                    || owner instanceof FrostlakeParser.ForStatementContext
                    || owner instanceof FrostlakeParser.WhileStatementContext
                    || owner instanceof FrostlakeParser.RepeatStatementContext ? owner : null;
            }
        }
        return null;
    }

    /** The first token from {@code first} on that live's recovery reads as a name, or null past the semicolon. */
    private static Token firstNameBeforeSemicolon(final Parser parser, final Token first) {
        final TokenStream stream = parser.getInputStream();
        for (Token token = first; token != null && token.getType() != Token.EOF;
                token = nextSpoken(stream, token.getTokenIndex())) {
            if (token.getType() == FrostlakeLexer.SEMI) {
                return null;
            }
            if (SyntaxErrorListener.isNameLike(parser, token)) {
                return token;
            }
        }
        return null;
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
}
