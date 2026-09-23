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
 *   IF (TRUE) THEN SHOW TABLES IN USER x y; END IF;    'x', then the token after the construct's END IF;
 * </pre>
 *
 * <p>Past the semicolon live reads the rest outside the block, as this parse does: a scripting statement is refused
 * at its first word there (see ScriptingStatementPlacement, which offers that word), a SQL statement reads on, and
 * the block's END is refused. A statement in the body of an IF, LOOP, CASE, FOR, WHILE or REPEAT resumes past the
 * construct's own END, as a statement there run into the next word without its semicolon does, unless an ELSE, an
 * ELSEIF or an UNTIL follows the statement's semicolon: then that is read outside the block as well. Outside a block
 * the parse's own line stands alone.
 */
final class KeywordScopeLines {

    private final Token refused;
    private final Token stacked;
    private final boolean takesNextFault;
    /** A word a check after the parse refuses in what the parse read on past the refused word, or null. */
    private Token laterRefusal;

    private KeywordScopeLines(final Token refused, final Token stacked, final boolean takesNextFault) {
        this.refused = refused;
        this.stacked = stacked;
        this.takesNextFault = takesNextFault;
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
        if (!(recognizer instanceof Parser) || !(e instanceof NoViableAltException)
                || !(e.getCtx() instanceof FrostlakeParser.ShowStatementContext) || refused.getType() == Token.EOF) {
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
            return new KeywordScopeLines(refused, null, false);
        }
        final ParserRuleContext owner = statementListOwner((ParserRuleContext) e.getCtx());
        final Token name = firstNameBeforeSemicolon(parser, refused);
        if (owner == null || name == null) {
            return null;
        }
        final Token after = nextSpoken(stream, name.getTokenIndex());
        if (after == null || after.getType() == Token.EOF || after.getType() == FrostlakeLexer.END) {
            return new KeywordScopeLines(refused, null, false);
        }
        final boolean block = owner instanceof FrostlakeParser.BeginEndBlockContext
            || owner instanceof FrostlakeParser.ExceptionHandlerContext;
        if (after.getType() == FrostlakeLexer.SEMI) {
            final Token resumed = nextSpoken(stream, after.getTokenIndex());
            final boolean branchFollows = resumed != null && (resumed.getType() == FrostlakeLexer.ELSE
                || resumed.getType() == FrostlakeLexer.ELSEIF || resumed.getType() == FrostlakeLexer.UNTIL);
            if (block || branchFollows) {
                return new KeywordScopeLines(refused, null, true);
            }
        } else if (block) {
            return new KeywordScopeLines(refused, after, false);
        }
        return new KeywordScopeLines(refused, SyntaxErrorListener.afterEnclosingConstruct(stream, owner.getStart()),
            false);
    }

    /**
     * Whether the second line is the one the parse reports for the fault it meets after the refused word.
     *
     * @return whether it is
     */
    boolean readsNextFault() {
        return takesNextFault;
    }

    /**
     * Takes a word a check after the parse refuses in the statements the parse read on past the refused word — a
     * scripting statement's first word, which the block's recovery reads outside the block — as the fault the second
     * line may name.
     *
     * @param word the refused word
     * @return whether the second line may name it
     */
    boolean offer(final Token word) {
        if (!takesNextFault || laterRefusal != null || word.getTokenIndex() <= refused.getTokenIndex()) {
            return false;
        }
        laterRefusal = word;
        return true;
    }

    /**
     * The lines to report: the refused word, then the stacked token, or else the earlier of the word a later check
     * refuses and the first line the parse reports once the refused word is set aside.
     *
     * @param laterLines the lines the parse reports once the refused word is set aside, or null
     * @return the lines
     */
    List<String> lines(final List<String> laterLines) {
        final List<String> lines = new ArrayList<>();
        final String first = SyntaxErrorListener.sentence(refused);
        lines.add(first);
        if (stacked != null) {
            lines.add(SyntaxErrorListener.sentence(stacked));
            return lines;
        }
        if (!takesNextFault) {
            return lines;
        }
        final String parsed = laterLines == null || laterLines.isEmpty() || first.equals(laterLines.get(0))
            ? null : laterLines.get(0);
        final String checked = laterRefusal == null ? null : SyntaxErrorListener.sentence(laterRefusal);
        final String second = parsed == null ? checked : checked == null ? parsed : earlier(checked, parsed);
        if (second != null) {
            lines.add(second);
        }
        return lines;
    }

    /** The one of two sentences whose place in the text comes first. */
    private static String earlier(final String one, final String other) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(one);
        final int[] otherAt = SyntaxErrorListener.sentenceCoordinates(other);
        if (at == null || otherAt == null) {
            return one;
        }
        return at[0] < otherAt[0] || at[0] == otherAt[0] && at[1] <= otherAt[1] ? one : other;
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
