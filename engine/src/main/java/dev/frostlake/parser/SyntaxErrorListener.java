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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom ANTLR error listener that collects syntax errors and throws them in live Snowflake's
 * wording (live-verified): every error renders on its own line under the one compilation-error
 * prefix — a parser error as {@code syntax error line L at position P unexpected 'TOK'.} (token
 * text verbatim, {@code '<EOF>'} at end of input), a lexer error (an unterminated literal or
 * quoted identifier — the lexer consumed to end of input) as
 * {@code parse error line L at position P near '<EOF>'.} positioned at the END of the input.
 * The raw ANTLR diagnostics ("mismatched input ... expecting ...") stay available through
 * {@link SqlSyntaxException#getSyntaxErrors()} and the logs.
 */
public class SyntaxErrorListener extends BaseErrorListener {

    private static final Logger logger = LoggerFactory.getLogger(SyntaxErrorListener.class);

    private final List<String> errors;
    private final List<String> messageLines;
    private final List<Boolean> selfDeadEnds;
    private final List<Boolean> atEndOfInput;
    private final String sql;
    private int lastMessageLine = -1;
    private int lastMessagePosition = -1;
    // Speculative parses (is this UDF body a query or a bare expression?) EXPECT to fail on one of
    // the alternatives; logging their syntax errors at ERROR flooded the log with noise for every
    // perfectly working expression-body UDF. Quiet mode demotes the logging to DEBUG — the collected
    // errors and throwIfErrors() behave identically.
    private final boolean quiet;

    public SyntaxErrorListener(final String sql) {
        this(sql, false);
    }

    public SyntaxErrorListener(final String sql, final boolean quiet) {
        this.errors = new ArrayList<>();
        this.messageLines = new ArrayList<>();
        this.selfDeadEnds = new ArrayList<>();
        this.atEndOfInput = new ArrayList<>();
        this.sql = sql;
        this.quiet = quiet;
    }

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer,
                           final Object offendingSymbol,
                           final int line,
                           final int charPositionInLine,
                           final String msg,
                           final RecognitionException e) {
        final String error = String.format("Syntax error at line %d:%d - %s", line, charPositionInLine, msg);
        errors.add(error);
        final int messageLine;
        final int messagePosition;
        final String messageText;
        if (offendingSymbol instanceof Token) {
            Token reported = (Token) offendingSymbol;
            // A no-viable-alternative that ran to end of input names '<EOF>' as its offending token,
            // and live names the dead end's start token instead in ONE situation: when that token is
            // TRAILING JUNK after a statement that was already complete. Measured across twenty-two
            // end-of-input shapes, live's rule is maximal munch — it names the first token that no
            // continuation can consume, and '<EOF>' (at the input's end) only when the input itself
            // ran out mid-statement:
            //
            //   SELECT a FROM kw GROUP BY                      '<EOF>' — GROUP BY is consumable, the
            //                                                  statement simply needs more input
            //   SELECT '{}'::OBJECT(x VARCHAR) RENAME FIELDS   'FIELDS' — RENAME reads as an alias, so
            //                                                  the statement ENDED and FIELDS is junk
            //   SELECT a FROM kw zz yy                         'yy' — same shape, ordinary words
            //
            // The parser's own context tells the two apart. A dead end inside a clause the parser had
            // entered leaves that clause as the current rule, and some ancestor of it began EARLIER
            // than the dead end. Trailing junk instead fails while the parser is opening a FRESH
            // statement, so the whole context chain begins exactly at the dead-end token.
            if (e instanceof NoViableAltException && reported.getType() == Token.EOF) {
                final Token startToken = ((NoViableAltException) e).getStartToken();
                if (startToken != null && startToken.getType() != Token.EOF
                        && startToken.getTokenIndex() > 0
                        && beginsAFreshStatement(recognizer, startToken)) {
                    reported = startToken;
                }
            }
            final int[] shown = LeadingCommentOffset.rebase(
                reported.getLine(), reported.getCharPositionInLine());
            messageLine = shown[0];
            messagePosition = shown[1];
            messageText = "syntax error line " + messageLine + " at position " + messagePosition
                + " unexpected '" + reported.getText() + "'.";
        } else {
            final int[] shown = LeadingCommentOffset.rebase(endOfInputLine(), endOfInputPosition());
            messageLine = shown[0];
            messagePosition = shown[1];
            messageText = "parse error line " + messageLine + " at position "
                + messagePosition + " near '<EOF>'.";
        }
        // Recovery can re-flag a token BEFORE the error already reported (resynchronization
        // artifacts), and those are dropped. Live is not strictly forward itself — for a call left
        // open with no arguments it adds a BACKWARDS line naming the '(' ("SELECT MAX(" is
        // '<EOF>' at 11 then '(' at 10) — but that line is a note about the unclosed opener, which
        // this parser does not produce at all, so nothing measured is lost by dropping backwards
        // lines here. Live's FORWARD stacking is reproduced: see reportedLines.
        final boolean backwards = messageLine < lastMessageLine
            || (messageLine == lastMessageLine && messagePosition < lastMessagePosition);
        if (!backwards) {
            messageLines.add(messageText);
            // A LEXER error counts as positioned, not as end-of-input, so it suppresses the parser
            // error that follows it at the same place. Live keeps both for an unterminated quoted
            // IDENTIFIER and only the lexer's line for an unterminated STRING, and nothing in the
            // error distinguishes those two here — one line is the answer that matches the commoner
            // of the pair.
            atEndOfInput.add(Boolean.valueOf(offendingSymbol instanceof Token
                && ((Token) offendingSymbol).getType() == Token.EOF));
            // A dead end that begins AND ends on the same token is a single-token rejection of one
            // alternative; the parser routinely goes on to consume that very token through another
            // one (`SELECT case …` rejects CASE as a select item, then reads it as a CASE
            // expression). Live never reports those, so they are dropped once a later error shows
            // the parse continued past them — see throwIfErrors.
            selfDeadEnds.add(Boolean.valueOf(e instanceof NoViableAltException
                && ((NoViableAltException) e).getStartToken() == offendingSymbol));
            lastMessageLine = messageLine;
            lastMessagePosition = messagePosition;
        }
        if (quiet) {
            logger.debug("=> SQL syntax error (speculative parse): {}", error);
        } else {
            logger.error("=> SQL syntax error: {}", error);
        }
    }

    /**
     * Whether the parser was opening a FRESH statement at {@code startToken} — the mark of trailing
     * junk after a complete statement, as opposed to a statement that merely ran out of input.
     *
     * <p>The test is the context chain: every enclosing rule except the script itself must begin at
     * the dead-end token. A clause the parser had already entered ({@code groupByClause} inside a
     * {@code selectStatement} that began at token 0) has an ancestor starting earlier, and is
     * therefore mid-statement.
     */
    private boolean beginsAFreshStatement(final Recognizer<?, ?> recognizer, final Token startToken) {
        if (!(recognizer instanceof Parser)) {
            return false;
        }
        int outermostStart = -1;
        ParserRuleContext context = ((Parser) recognizer).getContext();
        while (context != null) {
            if (context.getParent() != null && context.getStart() != null) {
                outermostStart = context.getStart().getTokenIndex();
            }
            context = context.getParent();
        }
        return outermostStart == startToken.getTokenIndex();
    }

    /**
     * The lines actually reported, live's stacking rule reproduced as measured.
     *
     * <p>Live reports SEVERAL errors when they are genuinely independent, each at its own place and in
     * text order — {@code GROUP BY , ORDER BY , LIMIT ,} is three lines, one per stray comma. What it
     * never reports is the wreckage of its own recovery, and Frostlake's parser produces plenty:
     *
     * <ul>
     *   <li>a single-token dead end the parse then consumed through another alternative
     *       ({@code SELECT case …} rejects CASE as a select item, then reads it as a CASE
     *       expression) — dropped once a later error shows the parse continued past it;</li>
     *   <li>an END-OF-INPUT error following an error already reported at a real token — that is
     *       recovery running off the end of the statement, not a second problem.</li>
     * </ul>
     */
    private List<String> reportedLines() {
        final List<String> reported = new ArrayList<>();
        boolean keptPositioned = false;
        for (int i = 0; i < messageLines.size(); i++) {
            if (i < selfDeadEnds.size() && Boolean.TRUE.equals(selfDeadEnds.get(i))
                    && i + 1 < messageLines.size()) {
                continue;
            }
            final boolean endOfInput = i < atEndOfInput.size()
                && Boolean.TRUE.equals(atEndOfInput.get(i));
            if (endOfInput && keptPositioned) {
                continue;
            }
            if (!reported.isEmpty() && reported.get(reported.size() - 1).equals(messageLines.get(i))) {
                // The same sentence at the same place twice is one problem reported twice — two
                // alternatives of the same decision running out of input together.
                continue;
            }
            reported.add(messageLines.get(i));
            if (!endOfInput) {
                keptPositioned = true;
            }
        }
        if (reported.isEmpty() && !messageLines.isEmpty()) {
            reported.add(messageLines.get(messageLines.size() - 1));
        }
        return reported;
    }

    /** 1-based line of the end of the input text. */
    private int endOfInputLine() {
        int lines = 1;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /** 0-based column just past the last character on the input's last line. */
    private int endOfInputPosition() {
        final int lastBreak = sql.lastIndexOf('\n');
        return sql.length() - (lastBreak + 1);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public void throwIfErrors() {
        if (hasErrors()) {
            final StringBuilder detail = new StringBuilder();
            for (final String line : reportedLines()) {
                if (detail.length() > 0) {
                    detail.append('\n');
                }
                detail.append(line);
            }

            if (quiet) {
                logger.debug("=> Failed speculative parse:\n{}", sql);
            } else {
                logger.error("=> Failed to parse SQL query:\n{}", sql);
            }
            throw new SqlSyntaxException(SqlCompilationError.of(detail.toString()), errors, sql);
        }
    }
}
