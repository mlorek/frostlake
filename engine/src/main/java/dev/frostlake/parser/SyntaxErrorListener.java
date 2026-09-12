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
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
    private boolean firstErrorMissingTerminator;
    private final List<String> messageLines;
    private final List<Boolean> selfDeadEnds;
    private final List<Boolean> atEndOfInput;
    /** Per message line: whether it names a SURPLUS END, after which live reports nothing more. */
    private final List<Boolean> surplusEnds;
    /** Per message line: the token index of the token it names, or -1 (a lexer error, end of input). */
    private final List<Integer> offendingIndexes;
    /** Per message line: the type of the token the reported statement OPENED at, or -1. */
    private final List<Integer> openerTypes;
    private final String sql;
    private int lastMessageLine = -1;
    private int firstMessageLine = -1;
    private int firstMessagePosition = -1;
    private int firstOffendingStartIndex = -1;
    private int lastMessagePosition = -1;
    /** Token index of the ')' closing a group whose '(' was already refused; see groupOpenedHere. */
    private int suppressUntilTokenIndex = -1;
    /** Per message line: its line number and position, so lines from two parses merge in text order. */
    private final List<int[]> coordinates;
    /** Per message line: the second line live stacks after it when it is the first line reported; or null. */
    private final List<String> stackedSentences;
    /** Per message line: where the statement holding its stacked line's token ends; lines up to it are its wreckage. */
    private final List<int[]> stackedEnds;
    /** Whether this listener watches the parse of a text whose bare scripting conditions were bracketed. */
    private boolean repairedParse;
    /** The '(' of a call left open at the end of a SELECT list, which live names in a backwards line; or null. */
    private Token unclosedCall;
    // Speculative parses (is this UDF body a query or a bare expression?) EXPECT to fail on one of
    // the alternatives; logging their syntax errors at ERROR flooded the log with noise for every
    // perfectly working expression-body UDF. Quiet mode demotes the logging to DEBUG — the collected
    // errors and throwIfErrors() behave identically.
    private final boolean quiet;
    // Only a WHOLE STATEMENT may be judged by its leading token — see leadingIdentifierRefusal. An
    // expression parse legitimately begins with an identifier, so the rule would blame the wrong word.
    private boolean statementParse;

    public SyntaxErrorListener(final String sql) {
        this(sql, false);
    }

    public SyntaxErrorListener(final String sql, final boolean quiet) {
        this.errors = new ArrayList<>();
        this.messageLines = new ArrayList<>();
        this.selfDeadEnds = new ArrayList<>();
        this.atEndOfInput = new ArrayList<>();
        this.surplusEnds = new ArrayList<>();
        this.offendingIndexes = new ArrayList<>();
        this.openerTypes = new ArrayList<>();
        this.coordinates = new ArrayList<>();
        this.stackedSentences = new ArrayList<>();
        this.stackedEnds = new ArrayList<>();
        this.sql = sql;
        this.quiet = quiet;
    }

    /**
     * The character that FOLLOWS a malformed number, spelled as live spells it: its DECIMAL CHARACTER
     * CODE, or the literal {@code <EOF>} past the end of the input. Measured across the whole set —
     * without the decoding the number reads like an internal token id, which is what it was first
     * taken for:
     *
     * <pre>
     *   SELECT 1e FROM ex     near '32'      32 is the SPACE after 1e
     *   SELECT 1ea FROM ex    near '97'      97 is the a
     *   SELECT (1e) FROM ex   near '41'      41 is the close paren
     *   SELECT 1e, i FROM ex  near '44'      44 is the comma
     *   SELECT 1e::INT …      near '58'      58 is the first colon
     *   SELECT 1e\tFROM ex    near '9'       a TAB is coded like any other character
     *   SELECT 1e             near '<EOF>'   the one case that is spelled rather than coded
     * </pre>
     *
     * @param malformed the malformed-number token
     * @return the code, or {@code <EOF>}
     */
    private String characterAfter(final Token malformed) {
        final CharStream input = malformed.getInputStream();
        final int next = malformed.getStopIndex() + 1;
        if (input == null || next >= input.size()) {
            return "<EOF>";
        }
        return String.valueOf((int) input.getText(Interval.of(next, next)).charAt(0));
    }

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer,
                           final Object offendingSymbol,
                           final int line,
                           final int charPositionInLine,
                           final String msg,
                           final RecognitionException e) {
        final String error = String.format("Syntax error at line %d:%d - %s", line, charPositionInLine, msg);
        if (errors.isEmpty() && isMissingTerminator(e)) {
            firstErrorMissingTerminator = true;
        }
        errors.add(error);
        final int messageLine;
        final int messagePosition;
        final String messageText;
        Token named = null;
        boolean endOpened = false;
        boolean parenAfterAlias = false;
        Token stacked = null;
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
            // ★ A MISSPELLED KEYWORD MID-STATEMENT is live's first-unusable-token shape. The parser
            // reads the misspelling as an ALIAS, ends the statement, opens a FRESH one at the next
            // bare word — the only statement an identifier can open is an assignment — and dies
            // further along ({@code … WHER i = 1} died on '='). Live names the identifier that
            // opened the doomed statement: the first token no continuation could use. Statements
            // legitimately opened by {@code x :=} are excluded by the token AFTER the identifier.
            if (reported.getType() != Token.EOF) {
                final Token doomedStart = freshIdentifierStatementStart(recognizer, reported);
                if (doomedStart != null) {
                    reported = doomedStart;
                }
            }
            // ★ AN ALIAS FOLLOWED BY '(' is refused AT THE PARENTHESIS, and nothing after it in the
            // statement is reported. This parser lets a fresh statement open at the '(' right after
            // the select item's alias, and names whatever inside the bracket cannot begin one.
            final Token aliasParen = parenAfterAliasStatementStart(recognizer, reported);
            if (aliasParen != null) {
                reported = aliasParen;
                parenAfterAlias = true;
            }
            // ★ A STATEMENT OPENED BY END IS A SURPLUS END, and live names the END itself — after a
            // block, after a SELECT, standing alone, with or without its semicolon — where this
            // parser reads END as a word that may open a statement and dies on what follows it.
            final Token surplusEnd = statementOpenedByEnd(recognizer, reported);
            if (surplusEnd != null) {
                reported = surplusEnd;
                endOpened = true;
            }
            if (isMissingTerminator(e)) {
                final Token follower = aliasSwallowedFollower(recognizer, reported);
                if (follower != null) {
                    reported = follower;
                }
                if (errors.size() == 1) {
                    stacked = stackedAfterMissingTerminator(recognizer, reported);
                }
            }
            if (stacked == null && !endOpened && recognizer instanceof Parser) {
                stacked = stackedAfterFirstFault((Parser) recognizer, reported, parenAfterAlias, e);
            }
            if (reported.getType() == Token.EOF && messageLines.isEmpty()) {
                unclosedCall = unclosedCallParen(recognizer, reported);
            }
            named = reported;
            if (reported.getType() == FrostlakeLexer.MALFORMED_EXPONENT) {
                final int[] shown = LeadingCommentOffset.rebase(reported.getLine(),
                    reported.getCharPositionInLine() + reported.getText().length());
                messageLine = shown[0];
                messagePosition = shown[1];
                messageText = "parse error line " + messageLine + " at position " + messagePosition
                    + " near '" + characterAfter(reported) + "'.";
            } else {
                final int[] shown = LeadingCommentOffset.rebase(
                    reported.getLine(), reported.getCharPositionInLine());
                messageLine = shown[0];
                messagePosition = shown[1];
                messageText = "syntax error line " + messageLine + " at position " + messagePosition
                    + " unexpected '" + reported.getText() + "'.";
            }
        } else {
            final int[] shown = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
            messageLine = shown[0];
            messagePosition = shown[1];
            messageText = "parse error line " + messageLine + " at position "
                + messagePosition + " near '<EOF>'.";
        }
        // ★ A GROUP WHOSE OPENING PAREN WAS ALREADY REFUSED SAYS NOTHING MORE. Live reports
        // `LIMIT (2)` as one line naming the '(' and stops — whatever sits inside the brackets, and
        // whatever follows them: a count, a NULL, a column, an empty pair, an unclosed one, a
        // trailing OFFSET or plain junk all give the same single sentence. Frostlake named the inner
        // token as well.
        //
        // It is the GROUP that goes quiet, not the statement. `SAMPLE ((10))` stacks two lines live —
        // the inner '(' and then the ')' left over AFTER that group — so a second fault outside the
        // brackets still speaks, and only positions within them are dropped.
        if (named != null && suppressUntilTokenIndex >= 0
                && named.getTokenIndex() <= suppressUntilTokenIndex) {
            if (quiet) {
                logger.debug("=> SQL syntax error inside an already-refused group: {}", error);
            }
            return;
        }
        // Recovery can re-flag a token BEFORE the error already reported (resynchronization
        // artifacts), and those are dropped. Live is not strictly forward itself — for a call left
        // open with no arguments it adds a BACKWARDS line naming the '(' ("SELECT MAX(" is
        // '<EOF>' at 11 then '(' at 10) — but that line is a note about the unclosed opener, which
        // this parser does not produce as an error at all (reportedLines adds it for a call standing
        // in a SELECT list, see unclosedCallParen), so nothing measured is lost by dropping backwards
        // lines here. Live's FORWARD stacking is reproduced: see reportedLines.
        final boolean backwards = messageLine < lastMessageLine
            || (messageLine == lastMessageLine && messagePosition < lastMessagePosition);
        if (!backwards) {
            messageLines.add(messageText);
            coordinates.add(new int[] {messageLine, messagePosition});
            if (stacked != null) {
                stackedSentences.add(sentence(stacked));
                Token end = recognizer instanceof Parser
                    ? nextSpoken(((Parser) recognizer).getInputStream(), stacked.getTokenIndex()) : null;
                while (end != null && end.getType() != FrostlakeLexer.SEMI && end.getType() != Token.EOF) {
                    end = nextSpoken(((Parser) recognizer).getInputStream(), end.getTokenIndex());
                }
                stackedEnds.add(end == null || end.getType() == Token.EOF
                    ? new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}
                    : LeadingCommentOffset.rebase(end.getLine(), end.getCharPositionInLine()));
            } else {
                stackedSentences.add(null);
                stackedEnds.add(null);
            }
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
            surplusEnds.add(Boolean.valueOf(endOpened));
            offendingIndexes.add(Integer.valueOf(named != null && named.getType() != Token.EOF
                ? named.getTokenIndex() : -1));
            openerTypes.add(Integer.valueOf(statementOpenerType(recognizer)));
            lastMessageLine = messageLine;
            lastMessagePosition = messagePosition;
            if (firstMessageLine < 0) {
                firstMessageLine = messageLine;
                firstMessagePosition = messagePosition;
                if (named != null) {
                    firstOffendingStartIndex = named.getStartIndex();
                }
            }
            if (named != null && named.getType() == FrostlakeLexer.LPAREN) {
                // ★ A REFUSED '(' IN THE FETCH COUNT SLOT ENDS THE STATEMENT'S REPORTING. Live resyncs
                // past the group to the clause's own keywords and reads the rest as written — ROWS
                // ONLY, ROW ONLY, nothing, a stray ')' or junk all give the one line — where this
                // parser, its count slot already failed, refuses whatever follows the group. Every
                // other refused group keeps to its own brackets: SAMPLE ((10)) still names the ')'
                // left over outside them. A '(' refused after an ALIAS ends the statement's
                // reporting as well: `SELECT 1 foo (2) FROM t WHERE …` is the one line at the '('.
                suppressUntilTokenIndex = inFetchCountSlot(recognizer, named) || parenAfterAlias
                    ? statementEndIndex(recognizer, named) : groupCloseIndex(recognizer, named);
            } else if (named != null && insideLimitSlotGroup(recognizer, named)) {
                // ★ AN ERROR INSIDE A BRACKETED LIMIT / OFFSET COUNT resyncs live at the next OFFSET —
                // `LIMIT (2) OFFSET (1)` still names the second '(' — and consumes anything else
                // silently: `LIMIT (2) ROWS` and `LIMIT (2) )` add no line for the ROWS or the ')'.
                suppressUntilTokenIndex = nextOffsetOrStatementEnd(recognizer, named);
            } else if (named != null && named.getType() == FrostlakeLexer.RPAREN
                    && closesTheLastOpenGroup(recognizer, named)) {
                // ★ A STRAY ')' THAT CLOSES THE LAST OPEN GROUP ENDS THE STATEMENT'S REPORTING TOO:
                // after SAMPLE ((10))'s second line live reads a LIMIT 1, a WHERE or plain junk
                // without another word, while this parser named the LIMIT's count.
                suppressUntilTokenIndex = statementEndIndex(recognizer, named);
            }
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
    /**
     * The bare IDENTIFIER a fresh, doomed statement opened at, when the current error sits strictly
     * after it — or null when the current statement opened any other way. The identifier must be
     * mid-input (a LEADING one has its own rule) and must not be followed by {@code :=}, which is
     * the one legitimate identifier-opened statement.
     */
    /**
     * The END token a fresh statement opened at, when the current error sits at or after it — or null
     * when the current statement opened any other way. Unlike the identifier rule this one applies to
     * a LEADING END too: {@code END} alone is "unexpected 'END'" at position 0 on the account, not
     * "unexpected '&lt;EOF&gt;'" three characters on.
     */
    private Token statementOpenedByEnd(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        Token outermostStart = null;
        ParserRuleContext context = ((Parser) recognizer).getContext();
        while (context != null) {
            if (context.getParent() != null && context.getStart() != null) {
                outermostStart = context.getStart();
            }
            context = context.getParent();
        }
        if (outermostStart == null || outermostStart.getType() != FrostlakeLexer.END
                || outermostStart.getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        return outermostStart;
    }

    /**
     * The '(' a fresh statement opened at when that bracket directly follows a statement whose last
     * select item ends in its ALIAS — bare, after AS, quoted, or a word such as COLLATE read as one.
     * Live refuses the bracket itself there ({@code SELECT 'a' foo ('x')} is "unexpected '('"), at the
     * top level, inside a CREATE … AS or INSERT … SELECT, and inside a block alike, while this parser,
     * free to open a new statement at a bracket, named the first token inside it that cannot begin one.
     * Null for every other shape: a bracket after a plain expression is not this case
     * ({@code SELECT 1 (2)} names the '2' on both engines), nor is one after a semicolon.
     */
    private static Token parenAfterAliasStatementStart(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        ParserRuleContext statement = null;
        ParserRuleContext root = ((Parser) recognizer).getContext();
        while (root != null && root.getParent() != null) {
            statement = root;
            root = root.getParent();
        }
        if (statement == null || root == null || statement.getStart() == null
                || statement.getStart().getType() != FrostlakeLexer.LPAREN
                || statement.getStart().getTokenIndex() > reported.getTokenIndex()) {
            return null;
        }
        ParseTree previous = null;
        for (int i = 0; i < root.getChildCount() && root.getChild(i) != statement; i++) {
            previous = root.getChild(i);
        }
        if (!(previous instanceof ParserRuleContext)) {
            return null;
        }
        final ParserRuleContext before = (ParserRuleContext) previous;
        if (before.getStop() == null || !endsInSelectItemAlias(before)) {
            return null;
        }
        final TokenStream stream = ((Parser) recognizer).getInputStream();
        for (int i = statement.getStart().getTokenIndex() - 1; i > before.getStop().getTokenIndex(); i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return null;
            }
        }
        return statement.getStart();
    }

    /** Whether a statement's last token closes the ALIAS of a select item. */
    private static boolean endsInSelectItemAlias(final ParserRuleContext statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        for (ParseTree up = leaf.getParent(); up != null && up != statement.getParent(); up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) up;
                final ParserRuleContext alias = item.AS() != null ? item.aliasName() : item.identifier();
                return alias != null && alias.getStop() != null
                    && alias.getStop().getTokenIndex() == statement.getStop().getTokenIndex();
            }
        }
        return false;
    }

    /** Whether {@code e} is a block's statement ending without its semicolon (the statement list's own check). */
    private static boolean isMissingTerminator(final RecognitionException e) {
        return e instanceof FailedPredicateException
            && ((FailedPredicateException) e).getRuleIndex() == FrostlakeParser.RULE_statementList;
    }

    /**
     * Where live points when a query inside a block runs into the next word without its semicolon. A word
     * the grammar can read as a NAME — RETURN, BREAK, CONTINUE, LET or a plain identifier — becomes the
     * query's bare ALIAS on the account whenever the query ends in an unaliased select item or table
     * reference (a DELETE ending at its unaliased target table too), so the error lands on the token
     * AFTER it: {@code SELECT 'foo'} then {@code RETURN :v;}
     * is "unexpected ':'", and {@code RETURN 1;} "unexpected '1'" (live-verified). After an alias, a
     * WHERE, an ORDER BY or a LIMIT, and after any other statement, the word itself stays the error.
     * Null when this is not that case.
     */
    private Token aliasSwallowedFollower(final Recognizer<?, ?> recognizer, final Token word) {
        if (!(recognizer instanceof Parser) || word.getType() == Token.EOF) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        if (!(parser.getContext() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final FrostlakeParser.StatementListContext list = (FrostlakeParser.StatementListContext) parser.getContext();
        FrostlakeParser.StatementContext finished = null;
        for (int i = list.getChildCount() - 1; i >= 0 && finished == null; i--) {
            if (list.getChild(i) instanceof FrostlakeParser.StatementContext) {
                finished = (FrostlakeParser.StatementContext) list.getChild(i);
            }
        }
        if (finished == null || !(finished.queryStatement() != null && endsTakingBareAlias(finished)
                || endsInUnaliasedDeleteTarget(finished))) {
            return null;
        }
        final ATN atn = parser.getATN();
        if (!atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(word.getType())) {
            return null;
        }
        final TokenStream stream = parser.getInputStream();
        for (int i = word.getTokenIndex() + 1; i < stream.size(); i++) {
            final Token next = stream.get(i);
            if (next.getChannel() == Token.DEFAULT_CHANNEL) {
                return next;
            }
        }
        return null;
    }

    /**
     * Whether a statement is a DELETE that ends at its unaliased target table — {@code DELETE FROM t}
     * followed by {@code RETURN 1;} reads RETURN as the target's alias on the account, as a query does.
     */
    private static boolean endsInUnaliasedDeleteTarget(final FrostlakeParser.StatementContext statement) {
        if (statement.dmlStatement() == null || statement.dmlStatement().deleteStatement() == null) {
            return false;
        }
        final FrostlakeParser.DeleteStatementContext delete = statement.dmlStatement().deleteStatement();
        return delete.objectName() != null && delete.objectName().getStop() != null && delete.getStop() != null
            && delete.objectName().getStop().getTokenIndex() == delete.getStop().getTokenIndex();
    }

    /**
     * The token live names in a SECOND line when a statement directly inside a BEGIN … END block or an
     * exception handler runs into the next word without its semicolon — or null when live stops at the
     * first line. Live's recovery resumes at the first word, from the refused token onwards, that the
     * grammar can read as a NAME, takes that word, and refuses the token after it. A semicolon there is
     * consumed and the token after it is refused instead; END straight after the name, or no name before
     * the statement's semicolon, ends the report:
     *
     * <pre>
     *   SELECT 'foo' AS a  then  RETURN 1;       'RETURN', then '1'
     *   LET a := 1         then  INSERT INTO t   'INSERT', then 'VALUES' — t is the first name
     *   LET a := 1         then  BREAK;          'BREAK', then the block's END
     *   SELECT 'foo'       then  RETURN :v + 1;  ':', then '+' — v is the first name
     *   SELECT 'foo'       then  RETURN 1 + 2;   '1' alone — no name before the semicolon
     *   LET a := 1         then  CALL p();       'CALL' alone
     * </pre>
     *
     * <p>A query whose FROM list ends in a table reference aliased with AS names the refused word TWICE,
     * at the same place. A statement inside an IF, LOOP, CASE, FOR, WHILE or REPEAT body resumes at the
     * construct's own END instead — see {@link #afterEnclosingConstruct}.
     */
    private static Token stackedAfterMissingTerminator(final Recognizer<?, ?> recognizer, final Token first) {
        // A statement run into its body's own END is the first line alone: the recovery already stands
        // where the body closes.
        if (!(recognizer instanceof Parser) || first.getType() == Token.EOF
                || first.getType() == FrostlakeLexer.END) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        if (!(parser.getContext() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final FrostlakeParser.StatementListContext list = (FrostlakeParser.StatementListContext) parser.getContext();
        final ParserRuleContext owner = list.getParent();
        if (owner instanceof FrostlakeParser.IfStatementContext || owner instanceof FrostlakeParser.LoopStatementContext
                || owner instanceof FrostlakeParser.CaseStatementContext
                || owner instanceof FrostlakeParser.ForStatementContext
                || owner instanceof FrostlakeParser.WhileStatementContext
                || owner instanceof FrostlakeParser.RepeatStatementContext) {
            // Only where the block-level reading would stack a line at all: with no name before the
            // statement's semicolon (RETURN 1 1;) the fault is one line in a body too.
            return owner.getStart() == null || firstNameBeforeSemicolon(parser, first) == null
                ? null : afterEnclosingConstruct(parser, owner.getStart());
        }
        if (!(list.getParent() instanceof FrostlakeParser.BeginEndBlockContext)
                && !(list.getParent() instanceof FrostlakeParser.ExceptionHandlerContext)) {
            return null;
        }
        FrostlakeParser.StatementContext finished = null;
        for (int i = list.getChildCount() - 1; i >= 0 && finished == null; i--) {
            if (list.getChild(i) instanceof FrostlakeParser.StatementContext) {
                finished = (FrostlakeParser.StatementContext) list.getChild(i);
            }
        }
        if (finished != null && finished.queryStatement() != null && endsInAsTableAlias(finished)) {
            return first;
        }
        if (first.getType() == FrostlakeLexer.CALL) {
            return null;
        }
        return afterFirstName(parser, first);
    }

    /**
     * The token live refuses in the second line once its recovery resumes at {@code first}: the first word
     * from there on that the grammar can read as a NAME is taken, and the token after it is refused. A
     * semicolon right after the name is consumed and the token after it is refused instead; END straight
     * after the name, or no name before the statement's semicolon, ends the report (null).
     */
    private static Token afterFirstName(final Parser parser, final Token first) {
        final TokenStream stream = parser.getInputStream();
        final Token name = firstNameBeforeSemicolon(parser, first);
        if (name == null) {
            return null;
        }
        Token next = nextSpoken(stream, name.getTokenIndex());
        if (next != null && next.getType() == FrostlakeLexer.END) {
            return null;
        }
        while (next != null && next.getType() == FrostlakeLexer.SEMI) {
            next = nextSpoken(stream, next.getTokenIndex());
        }
        return next == null || next.getType() == Token.EOF ? null : next;
    }

    /**
     * The second line live stacks after a first fault that is not a block statement's missing semicolon,
     * in the shapes whose recovery resumes the way {@link #afterFirstName} describes — or null:
     *
     * <pre>
     *   SELECT 1 foo ('x') FROM t;  RETURN 1;    '(' then 'RETURN'   an alias followed by '(' in a block
     *   SELECT 'foo'  then  CONTINUE :v;         ':' then 'END'      CONTINUE read as the query's alias
     *   WHILE (TRUE) OR x DO BREAK;              'OR' then 'DO'      a stray token after a loop condition
     *   WHILE (TRUE) x DO BREAK;                 'x' then 'BREAK'    the search starts past the stray token
     * </pre>
     *
     * <p>The first two hold for a statement of the BEGIN … END block itself or of an exception handler; the
     * loop condition's wherever the WHILE or UNTIL stands, and there the search for the name starts at the
     * token AFTER the refused one.
     */
    private static Token stackedAfterFirstFault(final Parser parser, final Token first, final boolean parenAfterAlias,
                                                final RecognitionException e) {
        if (first.getType() == Token.EOF || isMissingTerminator(e)) {
            return null;
        }
        if (strayAfterLoopCondition(parser, first)) {
            final Token next = nextSpoken(parser.getInputStream(), first.getTokenIndex());
            return next == null || next.getType() == Token.EOF ? null : afterFirstName(parser, next);
        }
        if ((parenAfterAlias || followsSwallowedWord(parser, first)) && directlyInBlock(parser, first)) {
            return afterFirstName(parser, first);
        }
        return null;
    }

    /**
     * Whether {@code token} stands among the statements of a BEGIN … END block itself — its body or one of
     * its exception handlers — and not inside an IF, LOOP, CASE, FOR, WHILE or REPEAT body, in a script
     * that is a block. Read off the tokens, because a parse that met a fault inside a block may have given
     * the block up and read its statements at the top level.
     */
    private static boolean directlyInBlock(final Parser parser, final Token token) {
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Integer> open = new ArrayList<>();
        Token previous = null;
        for (int i = 0; i < token.getTokenIndex() && i < stream.size(); i++) {
            final Token each = stream.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final Token next = nextSpoken(stream, i);
            final boolean opens = opensAConstruct(stream, each, previous)
                && !(each.getType() == FrostlakeLexer.BEGIN && next != null && next.getType() == FrostlakeLexer.SEMI);
            if (previous == null && !(each.getType() == FrostlakeLexer.DECLARE
                    || each.getType() == FrostlakeLexer.BEGIN && opens)) {
                return false;
            }
            if (opens) {
                open.add(Integer.valueOf(each.getType()));
            } else if (each.getType() == FrostlakeLexer.END && !open.isEmpty()) {
                open.remove(open.size() - 1);
            }
            previous = each;
        }
        return !open.isEmpty() && open.get(open.size() - 1).intValue() == FrostlakeLexer.BEGIN;
    }

    /**
     * Whether the word right before {@code token} ended the statement before it as a BARE ALIAS — of its
     * last select item or table reference. That is how a block statement left without its semicolon takes
     * the next statement's first word: {@code SELECT 'foo'} then {@code CONTINUE :v;} reads CONTINUE as the
     * query's alias, and the ':' after it is the refused token.
     */
    private static boolean followsSwallowedWord(final Parser parser, final Token token) {
        final Token word = previousSpoken(parser.getInputStream(), token.getTokenIndex());
        if (word == null || !isNameLike(parser, word)) {
            return false;
        }
        ParserRuleContext root = parser.getContext();
        while (root != null && root.getParent() != null) {
            root = root.getParent();
        }
        final FrostlakeParser.StatementContext statement = root == null ? null
            : statementEndingAt(root, word.getTokenIndex());
        return statement != null && endsInBareAlias(statement);
    }

    /** The statement in the tree under {@code context} whose last token has index {@code index}, or null. */
    private static FrostlakeParser.StatementContext statementEndingAt(final ParserRuleContext context, final int index) {
        for (int i = 0; i < context.getChildCount(); i++) {
            if (!(context.getChild(i) instanceof ParserRuleContext)) {
                continue;
            }
            final ParserRuleContext child = (ParserRuleContext) context.getChild(i);
            if (child.getStart() == null || child.getStop() == null
                    || child.getStart().getTokenIndex() > index || child.getStop().getTokenIndex() < index) {
                continue;
            }
            if (child instanceof FrostlakeParser.StatementContext && child.getStop().getTokenIndex() == index) {
                return (FrostlakeParser.StatementContext) child;
            }
            final FrostlakeParser.StatementContext inner = statementEndingAt(child, index);
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    /** Whether a statement's last token is the bare alias (no AS) of its last select item or table reference. */
    private static boolean endsInBareAlias(final ParserRuleContext statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        final int last = statement.getStop().getTokenIndex();
        for (ParseTree up = leaf.getParent(); up != null && up != statement.getParent(); up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) up;
                return item.AS() == null && item.identifier() != null && item.identifier().getStop() != null
                    && item.identifier().getStop().getTokenIndex() == last;
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                return reference.AS() == null && reference.nonJoinKeywordIdentifier() != null
                    && reference.nonJoinKeywordIdentifier().getStop() != null
                    && reference.nonJoinKeywordIdentifier().getStop().getTokenIndex() == last;
            }
        }
        return false;
    }

    /**
     * Whether {@code token} is a stray token right after the ')' that closes a WHILE or UNTIL condition —
     * {@code WHILE (TRUE) OR x DO}, {@code UNTIL (TRUE) x = 1 END} — other than the DO, LOOP or END that
     * belongs there.
     */
    private static boolean strayAfterLoopCondition(final Parser parser, final Token token) {
        final TokenStream stream = parser.getInputStream();
        final Token close = previousSpoken(stream, token.getTokenIndex());
        if (close == null || close.getType() != FrostlakeLexer.RPAREN) {
            return false;
        }
        Token open = null;
        int depth = 0;
        for (int i = close.getTokenIndex(); i >= 0 && open == null; i--) {
            final Token each = stream.get(i);
            if (each.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (each.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (each.getType() == FrostlakeLexer.LPAREN && --depth == 0) {
                open = each;
            }
        }
        final Token keyword = open == null ? null : previousSpoken(stream, open.getTokenIndex());
        if (keyword == null || keyword.getType() != FrostlakeLexer.WHILE && keyword.getType() != FrostlakeLexer.UNTIL) {
            return false;
        }
        final Token beforeKeyword = previousSpoken(stream, keyword.getTokenIndex());
        if (beforeKeyword != null && beforeKeyword.getType() == FrostlakeLexer.END) {
            return false;
        }
        return !closesLoopCondition(keyword, token);
    }

    /** Whether {@code token} is what belongs after the condition of {@code keyword}: DO or LOOP after WHILE, END after UNTIL. */
    private static boolean closesLoopCondition(final Token keyword, final Token token) {
        if (keyword.getType() == FrostlakeLexer.UNTIL) {
            return token.getType() == FrostlakeLexer.END;
        }
        return token.getType() == FrostlakeLexer.DO || token.getType() == FrostlakeLexer.LOOP;
    }

    /** The default-channel token before token index {@code before}, or null at the start. */
    private static Token previousSpoken(final TokenStream stream, final int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    /**
     * The '(' of a call left open, with nothing inside it, at the very end of a SELECT list —
     * {@code SELECT ABS(} — or null. Live follows that '&lt;EOF&gt;' line with a second, backwards one naming
     * the '(' ({@code SELECT ABS(} is '&lt;EOF&gt;' at 11, then '(' at 10), for a call standing in the list
     * itself: an operand or a whole item, in a query of its own or of an INSERT or CREATE … AS. Unable to
     * finish the call, this parser reads the name as the list's last item and opens a fresh statement at
     * the bracket, and that split is what is recognised here. A call inside another bracket, or in a later
     * clause, is not this case.
     */
    private static Token unclosedCallParen(final Recognizer<?, ?> recognizer, final Token eof) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        final TokenStream stream = ((Parser) recognizer).getInputStream();
        final Token paren = previousSpoken(stream, eof.getTokenIndex());
        if (paren == null || paren.getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        ParserRuleContext statement = null;
        ParserRuleContext root = ((Parser) recognizer).getContext();
        while (root != null && root.getParent() != null) {
            statement = root;
            root = root.getParent();
        }
        if (statement == null || root == null || statement.getStart() == null
                || statement.getStart().getTokenIndex() != paren.getTokenIndex()) {
            return null;
        }
        ParseTree previous = null;
        for (int i = 0; i < root.getChildCount() && root.getChild(i) != statement; i++) {
            previous = root.getChild(i);
        }
        if (!(previous instanceof ParserRuleContext)) {
            return null;
        }
        final ParserRuleContext before = (ParserRuleContext) previous;
        final Token name = previousSpoken(stream, paren.getTokenIndex());
        if (before.getStop() == null || name == null || before.getStop().getTokenIndex() != name.getTokenIndex()
                || !isNameLike((Parser) recognizer, name) || !endsInUnaliasedSelectItem(before)) {
            return null;
        }
        return paren;
    }

    /** Whether a statement's last token closes an unaliased select item of its own outermost select list. */
    private static boolean endsInUnaliasedSelectItem(final ParserRuleContext statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        for (ParseTree up = leaf.getParent(); up != null && up != statement.getParent(); up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) up;
                return item.AS() == null && item.identifier() == null;
            }
            if (up instanceof FrostlakeParser.TableReferenceContext
                    || up instanceof FrostlakeParser.ScalarSubqueryExprContext
                    || up instanceof FrostlakeParser.InSubqueryExprContext
                    || up instanceof FrostlakeParser.ExistsExprContext) {
                return false;
            }
        }
        return false;
    }

    /**
     * The second line's token for a statement inside a control construct's body — an IF (any branch),
     * LOOP, CASE, FOR, WHILE or REPEAT opened at {@code opener}. Live's recovery resumes at the
     * construct's own END and reads the keyword after it as a NAME: IF, LOOP and CASE are names, so
     * the construct's semicolon is consumed and the token after it is refused — the enclosing block's
     * END, or the next statement's first word — while FOR is reserved and is itself the second line
     * ({@code … RETURN 1; END FOR;} names that FOR).
     */
    private static Token afterEnclosingConstruct(final Parser parser, final Token opener) {
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        int depth = 0;
        Token previous = null;
        for (int i = opener.getTokenIndex(); i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == Token.EOF) {
                return null;
            }
            if (opensAConstruct(stream, token, previous)) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.END && --depth == 0) {
                Token next = nextSpoken(stream, i);
                if (next == null || next.getType() == Token.EOF || !isNameLike(parser, next)) {
                    return next == null || next.getType() == Token.EOF ? null : next;
                }
                next = nextSpoken(stream, next.getTokenIndex());
                while (next != null && next.getType() == FrostlakeLexer.SEMI) {
                    next = nextSpoken(stream, next.getTokenIndex());
                }
                return next == null || next.getType() == Token.EOF ? null : next;
            }
            previous = token;
        }
        return null;
    }

    /**
     * Whether {@code token} opens something one END will close: a bare BEGIN block, an IF statement,
     * a LOOP, WHILE, FOR or REPEAT, or a CASE statement or expression — none of them the keyword of
     * an END IF / END LOOP / … that closes one, an IF [NOT] EXISTS clause, or a cursor's FOR.
     */
    private static boolean opensAConstruct(final TokenStream stream, final Token token, final Token previous) {
        final int type = token.getType();
        final boolean closing = previous != null && previous.getType() == FrostlakeLexer.END;
        final Token next = nextSpoken(stream, token.getTokenIndex());
        final int nextType = next == null ? Token.EOF : next.getType();
        if (type == FrostlakeLexer.BEGIN) {
            return nextType != FrostlakeLexer.WORK && nextType != FrostlakeLexer.TRANSACTION
                && nextType != FrostlakeLexer.NAME;
        }
        if (type == FrostlakeLexer.IF) {
            if (closing || nextType == FrostlakeLexer.EXISTS) {
                return false;
            }
            final Token after = next == null ? null : nextSpoken(stream, next.getTokenIndex());
            return !(nextType == FrostlakeLexer.NOT && after != null && after.getType() == FrostlakeLexer.EXISTS);
        }
        if (type == FrostlakeLexer.FOR) {
            return !closing && (previous == null || previous.getType() != FrostlakeLexer.CURSOR);
        }
        return !closing && (type == FrostlakeLexer.LOOP || type == FrostlakeLexer.WHILE
            || type == FrostlakeLexer.REPEAT || type == FrostlakeLexer.CASE);
    }

    /**
     * The first token from {@code first} onwards that live's recovery reads as a NAME, or null when the
     * statement's semicolon (or the end of the input) comes first.
     */
    private static Token firstNameBeforeSemicolon(final Parser parser, final Token first) {
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        for (int i = first.getTokenIndex(); i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.SEMI || token.getType() == Token.EOF) {
                return null;
            }
            if (isNameLike(parser, token)) {
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

    /**
     * Whether live's recovery reads the token as a NAME: whatever may name a column here, CASE too (it
     * leads a name wherever one is the only thing possible), but not BEGIN, which live skips like a
     * reserved word.
     */
    private static boolean isNameLike(final Parser parser, final Token token) {
        if (token.getType() == FrostlakeLexer.CASE) {
            return true;
        }
        if (token.getType() == FrostlakeLexer.BEGIN) {
            return false;
        }
        final ATN atn = parser.getATN();
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    /** Whether a query's last token closes the AS-alias of a table reference in its FROM list. */
    private static boolean endsInAsTableAlias(final ParserRuleContext statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        for (ParseTree up = leaf.getParent(); up != null && up != statement; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                return false;
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                return reference.AS() != null && reference.aliasName() != null
                    && reference.aliasName().getStop() != null && statement.getStop() != null
                    && reference.aliasName().getStop().getTokenIndex() == statement.getStop().getTokenIndex();
            }
        }
        return false;
    }

    /** Whether a statement's last token closes an unaliased select item or table reference, which a bare alias may follow. */
    private static boolean endsTakingBareAlias(final ParseTree statement) {
        ParseTree leaf = statement;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        for (ParseTree up = leaf.getParent(); up != null && up != statement; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) up;
                return item.AS() == null && item.identifier() == null;
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                return reference.AS() == null && reference.nonJoinKeywordIdentifier() == null
                    && reference.pivotClause() == null && reference.unpivotClause() == null
                    && reference.sampleClause() == null;
            }
        }
        return false;
    }

    /**
     * Whether the FIRST error was a block's statement ending without its semicolon: the engine read that
     * statement whole, so no construct it does not know can explain the fault, and a CREATE may refuse the
     * body as the account does.
     */
    public boolean firstErrorIsMissingTerminator() {
        return firstErrorMissingTerminator;
    }

    private Token freshIdentifierStatementStart(final Recognizer<?, ?> recognizer, final Token reported) {
        if (!(recognizer instanceof Parser)) {
            return null;
        }
        Token outermostStart = null;
        ParserRuleContext context = ((Parser) recognizer).getContext();
        while (context != null) {
            if (context.getParent() != null && context.getStart() != null) {
                outermostStart = context.getStart();
            }
            context = context.getParent();
        }
        if (outermostStart == null || outermostStart.getType() != FrostlakeLexer.IDENTIFIER
                || outermostStart.getTokenIndex() <= 0
                || outermostStart.getTokenIndex() >= reported.getTokenIndex()) {
            return null;
        }
        final TokenStream stream = ((Parser) recognizer).getInputStream();
        for (int i = outermostStart.getTokenIndex() + 1; i < stream.size(); i++) {
            final Token next = stream.get(i);
            if (next.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            return next.getType() == FrostlakeLexer.COLON_EQ ? null : outermostStart;
        }
        return outermostStart;
    }

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
     *       recovery running off the end of the statement, not a second problem;</li>
     *   <li>inside a scripting BLOCK, the wreckage of an ABANDONED block — see
     *       {@link #abandonedBlockArtifacts}.</li>
     * </ul>
     */
    private List<String> reportedLines() {
        final String leading = leadingIdentifierRefusal();
        if (leading != null) {
            return Collections.singletonList(leading);
        }
        final String unsupportedType = unsupportedTypeRefusal();
        if (unsupportedType != null) {
            return Collections.singletonList(unsupportedType);
        }
        final String leadingSemicolon = leadingSemicolonRefusal();
        if (leadingSemicolon != null) {
            return Collections.singletonList(leadingSemicolon);
        }
        final String unbalancedBlock = unbalancedBlockRefusal();
        if (unbalancedBlock != null) {
            return Collections.singletonList(unbalancedBlock);
        }
        final List<String> reported = new ArrayList<>();
        final List<Integer> kept = new ArrayList<>();
        final boolean[] artifacts = abandonedBlockArtifacts();
        boolean keptPositioned = false;
        for (int i = 0; i < messageLines.size(); i++) {
            if (artifacts[i]) {
                continue;
            }
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
            kept.add(Integer.valueOf(i));
            if (i < surplusEnds.size() && Boolean.TRUE.equals(surplusEnds.get(i))) {
                // After a surplus END live reports nothing more: the statement list is over.
                break;
            }
            if (!endOfInput) {
                keptPositioned = true;
            }
        }
        if (reported.isEmpty() && !messageLines.isEmpty()) {
            reported.add(messageLines.get(messageLines.size() - 1));
            kept.add(Integer.valueOf(messageLines.size() - 1));
        }
        if (kept.isEmpty()) {
            return reported;
        }
        final List<String> bracketed = bareConditionRefusal(kept.get(0).intValue());
        if (bracketed != null) {
            return bracketed;
        }
        final List<String> nested = nestedAliasParenRefusal(kept);
        if (nested != null) {
            return nested;
        }
        final int firstLine = kept.get(0).intValue();
        if (firstLine < stackedSentences.size() && stackedSentences.get(firstLine) != null) {
            // The one line live stacks after the first fault — a block statement run into the next word, and
            // the shapes stackedAfterFirstFault names. Whatever this parser reported from there to the end of
            // the statement holding the stacked token is the wreckage of the same fault; a fault in a later
            // statement still speaks.
            final List<String> stackedLines = new ArrayList<>();
            stackedLines.add(reported.get(0));
            stackedLines.add(stackedSentences.get(firstLine));
            for (int k = 1; k < kept.size(); k++) {
                if (isAfter(coordinates.get(kept.get(k).intValue()), stackedEnds.get(firstLine))) {
                    stackedLines.add(reported.get(k));
                }
            }
            return stackedLines;
        }
        if (unclosedCall != null && reported.size() == 1 && kept.get(0).intValue() == 0
                && Boolean.TRUE.equals(atEndOfInput.get(0))) {
            // The backwards line naming the '(' of a call left open at the end of a SELECT list.
            final int[] at = LeadingCommentOffset.rebase(unclosedCall.getLine(), unclosedCall.getCharPositionInLine());
            final List<String> withOpener = new ArrayList<>(reported);
            withOpener.add("syntax error line " + at[0] + " at position " + at[1] + " unexpected '('.");
            return withOpener;
        }
        return reported;
    }

    /** Whether the first line-and-position pair lies strictly after the second. */
    private static boolean isAfter(final int[] candidate, final int[] reference) {
        return candidate[0] > reference[0] || (candidate[0] == reference[0] && candidate[1] > reference[1]);
    }

    /**
     * The lines live reports when the first fault is a Snowflake Scripting condition written WITHOUT its
     * parentheses — {@code IF 'x' THEN}, {@code ELSEIF}, {@code WHILE … DO}, {@code UNTIL … END} — or null
     * for any other first fault.
     *
     * <p>Live treats the missing brackets as missing tokens: it names the condition's first token (the
     * '(' should stand before it), then the THEN / DO / END where the ')' should stand, and reads on as
     * though both were there — so every other bare condition earns its own pair, and a genuine fault
     * anywhere else in the block still speaks: {@code IF 'x' THEN … ELSEIF 'y' THEN …} is four lines, and
     * {@code IF 'x' THEN RETURN 1; ELSE RETURN 1 1; END IF;} is 'x', THEN and the second '1'. A condition
     * whose own ')' closes it ({@code IF 1 = 1) THEN}) is its first token alone, and a word followed by
     * '(' ({@code IF UPPER('x') THEN}) is left to the parse, which already names it as live does.
     *
     * <p>This parser cannot follow the account there: unable to read the IF, it re-reads the block's
     * BEGIN as a transaction and reports the rest of the block as stray statements (an ELSE, an END).
     * So the pairs are built from the tokens, and the other faults come from a second parse of the
     * text with every bare condition bracketed — each bracket replacing the blank before its token, so
     * every position stays where it was.
     */
    private List<String> bareConditionRefusal(final int firstKept) {
        if (repairedParse || sql == null || firstKept >= offendingIndexes.size()
                || offendingIndexes.get(firstKept).intValue() < 0) {
            return null;
        }
        final List<Token> spoken = defaultChannelTokens();
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.DECLARE
                && !(spoken.get(0).getType() == FrostlakeLexer.BEGIN && isBareBegin(spoken, 0))) {
            return null;
        }
        int first = -1;
        for (int i = 0; i < spoken.size() && first < 0; i++) {
            if (spoken.get(i).getTokenIndex() == offendingIndexes.get(firstKept).intValue()) {
                first = i;
            }
        }
        if (first < 0 || !isBareCondition(spoken, first)) {
            return null;
        }
        final List<Token> named = new ArrayList<>();
        final char[] repaired = sql.toCharArray();
        // Character offsets and token start indexes agree only while no character needs two chars.
        boolean repairable = sql.length() == sql.codePointCount(0, sql.length());
        for (int i = 1; i < spoken.size(); i++) {
            if (!isBareCondition(spoken, i)) {
                continue;
            }
            final int closer = conditionCloser(spoken, i);
            if (closer == -2) {
                return null;
            }
            named.add(spoken.get(i));
            repairable = repairable && bracket(repaired, spoken.get(i), '(');
            if (closer >= 0) {
                named.add(spoken.get(closer));
                repairable = repairable && bracket(repaired, spoken.get(closer), ')');
            }
        }
        final List<String> lines = new ArrayList<>();
        final List<int[]> places = new ArrayList<>();
        for (final Token token : named) {
            final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            lines.add("syntax error line " + shown[0] + " at position " + shown[1]
                + " unexpected '" + token.getText() + "'.");
            places.add(shown);
        }
        if (!repairable) {
            return lines;
        }
        final String text = new String(repaired);
        final SyntaxErrorListener second = new SyntaxErrorListener(text, true);
        second.repairedParse = true;
        second.statementParse = statementParse;
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(second);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(second);
        parser.sqlScript();
        if (!second.hasErrors()) {
            return lines;
        }
        for (final String line : second.reportedLines()) {
            final int[] place = sentenceCoordinates(line);
            int at = lines.size();
            if (place != null) {
                while (at > 0 && isAfter(places.get(at - 1), place)) {
                    at--;
                }
            }
            lines.add(at, line);
            places.add(at, place == null ? new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE} : place);
        }
        return lines;
    }

    /**
     * Whether the token at {@code i} opens a condition written without its parentheses: it follows IF,
     * ELSEIF, WHILE or UNTIL (not the END IF / END WHILE that closes one) and is neither a '(' nor the
     * EXISTS of an {@code IF [NOT] EXISTS} clause. A word followed by '(' is excluded too — live drops
     * such a word as a single stray token and reads the bracket as the condition's own.
     */
    private static boolean isBareCondition(final List<Token> spoken, final int i) {
        if (i < 1 || i + 1 >= spoken.size()) {
            return false;
        }
        final int keyword = spoken.get(i - 1).getType();
        if (keyword != FrostlakeLexer.IF && keyword != FrostlakeLexer.ELSEIF
                && keyword != FrostlakeLexer.WHILE && keyword != FrostlakeLexer.UNTIL) {
            return false;
        }
        if (i >= 2 && spoken.get(i - 2).getType() == FrostlakeLexer.END) {
            return false;
        }
        final int type = spoken.get(i).getType();
        if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.SEMI || type == Token.EOF) {
            return false;
        }
        if (keyword == FrostlakeLexer.IF && (type == FrostlakeLexer.EXISTS
                || type == FrostlakeLexer.NOT && spoken.get(i + 1).getType() == FrostlakeLexer.EXISTS)) {
            return false;
        }
        return spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN;
    }

    /**
     * Where the ')' of a bare condition starting at {@code start} belongs: the index of the THEN (after
     * IF / ELSEIF), DO (after WHILE) or END (after UNTIL) that ends the condition, skipping brackets and
     * CASE … END expressions inside it; -1 when a ')' of the text's own closes the condition first; -2
     * when the statement ends before any of them.
     */
    private static int conditionCloser(final List<Token> spoken, final int start) {
        final int keyword = spoken.get(start - 1).getType();
        final int closerType = keyword == FrostlakeLexer.WHILE ? FrostlakeLexer.DO
            : keyword == FrostlakeLexer.UNTIL ? FrostlakeLexer.END : FrostlakeLexer.THEN;
        int depth = 0;
        int cases = 0;
        for (int j = start; j < spoken.size(); j++) {
            final int type = spoken.get(j).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -2;
            } else if (depth == 0 && type == FrostlakeLexer.CASE) {
                cases++;
            } else if (depth == 0 && cases > 0 && type == FrostlakeLexer.END) {
                cases--;
            } else if (depth == 0 && cases == 0 && type == closerType) {
                return j;
            }
        }
        return -2;
    }

    /** Replace the blank before {@code token} with {@code bracket}; false when no single blank is there. */
    private static boolean bracket(final char[] text, final Token token, final char bracket) {
        final int before = token.getStartIndex() - 1;
        if (before < 0 || before >= text.length || (text[before] != ' ' && text[before] != '\t')) {
            return false;
        }
        text[before] = bracket;
        return true;
    }

    /**
     * The line and position a sentence of this listener's own names ({@code … line L at position P …}),
     * or null for a sentence that carries none.
     */
    private static int[] sentenceCoordinates(final String sentence) {
        final int lineAt = sentence.indexOf(" line ");
        final int positionAt = sentence.indexOf(" at position ");
        if (lineAt < 0 || positionAt < lineAt) {
            return null;
        }
        int end = positionAt + " at position ".length();
        final int digits = end;
        while (end < sentence.length() && Character.isDigit(sentence.charAt(end))) {
            end++;
        }
        try {
            return new int[] {Integer.parseInt(sentence.substring(lineAt + " line ".length(), positionAt).trim()),
                Integer.parseInt(sentence.substring(digits, end))};
        } catch (final NumberFormatException notPositioned) {
            return null;
        }
    }

    /**
     * The lines live reports when the first fault is an ALIAS followed by '(' inside a bracketed query — a
     * scalar or IN subquery in a SELECT list, or a CTE's body — or null for any other first fault.
     *
     * <pre>
     *   SELECT (SELECT 1 foo (2))                'SELECT' at 8, '(' at 21
     *   SELECT (SELECT (SELECT 1 foo (2)))       'SELECT' at 8, 'SELECT' at 16, '(' at 29
     *   WITH c AS (SELECT 1 foo (2)) …           '(' at 24, '2' at 25, ')' at 27
     *   WITH c AS (SELECT 1 foo ()) …            '(' alone
     * </pre>
     *
     * <p>In a SELECT list live names the SELECT that opens every enclosing subquery, outermost first, and then
     * the '(' — for a subquery standing in the list itself: a whole item, an operand, an IN list or a CASE
     * branch, not one inside a call's brackets or in a later clause. In a CTE whose body ends with the
     * bracket, live names the '(', the first token inside it that is not another '(', and the CTE's own ')'
     * — an empty bracket is the '(' alone. Nothing after that is reported, in the statement or after it.
     * Both shapes hold for a query that is a statement of its own, not one inside a block.
     *
     * <p>This parser cannot follow the account there — its first line may even name an earlier token of the
     * same fault — so the shape is read from a second parse of the text with the bracket blanked out, every
     * character but a line break replaced by a blank so every position stays where it was. That parse must
     * read cleanly up to the bracketed query's own close; a fault after it does not matter.
     */
    private List<String> nestedAliasParenRefusal(final List<Integer> kept) {
        if (repairedParse || sql == null || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        final List<Token> spoken = defaultChannelTokens();
        int open = -1;
        for (int k = 0; k < kept.size() && open < 0; k++) {
            final int line = kept.get(k).intValue();
            final int index = line < offendingIndexes.size() ? offendingIndexes.get(line).intValue() : -1;
            for (int i = 1; i < spoken.size() && index >= 0 && open < 0; i++) {
                if (spoken.get(i).getTokenIndex() == index && spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                    open = i;
                }
            }
        }
        if (open < 1) {
            return null;
        }
        final int close = closingParen(spoken, open);
        if (close < 0) {
            return null;
        }
        final char[] blanked = sql.toCharArray();
        for (int c = spoken.get(open).getStartIndex(); c <= spoken.get(close).getStopIndex(); c++) {
            if (blanked[c] != '\n' && blanked[c] != '\r') {
                blanked[c] = ' ';
            }
        }
        final int[] firstError = new int[1];
        final FrostlakeParser.SqlScriptContext tree = parseNotingFirstError(new String(blanked), firstError);
        final FrostlakeParser.ExprItemContext item = itemAliasedAt(tree, spoken.get(open - 1).getStartIndex());
        final FrostlakeParser.SelectStatementContext query = item == null ? null : selectStatementOf(item);
        if (query == null) {
            return null;
        }
        final ParserRuleContext holder = query.getParent();
        if (holder instanceof FrostlakeParser.CteDefinitionContext) {
            final FrostlakeParser.CteDefinitionContext cte = (FrostlakeParser.CteDefinitionContext) holder;
            final ParserRuleContext with = holder.getParent();
            final ParserRuleContext outer = with == null ? null : with.getParent();
            return outer instanceof FrostlakeParser.SelectStatementContext
                && isTopLevelQuery((FrostlakeParser.SelectStatementContext) outer)
                && cte.RPAREN() != null && cte.RPAREN().getSymbol().getStartIndex() < firstError[0]
                ? cteBodyLines(spoken, open, close, cte) : null;
        }
        if (holder instanceof FrostlakeParser.ScalarSubqueryExprContext
                || holder instanceof FrostlakeParser.InSubqueryExprContext) {
            return selectListSubqueryLines(spoken, open, holder, firstError[0]);
        }
        return null;
    }

    /**
     * The parse tree of {@code text} as a script, however it ends; {@code firstError[0]} receives the character
     * where its first syntax error begins — a dead end's first token, not the one it died on — or
     * {@link Integer#MAX_VALUE} when it has none.
     */
    private static FrostlakeParser.SqlScriptContext parseNotingFirstError(final String text, final int[] firstError) {
        firstError[0] = Integer.MAX_VALUE;
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        final BaseErrorListener noter = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                    final int line, final int charPositionInLine, final String msg,
                    final RecognitionException e) {
                if (firstError[0] != Integer.MAX_VALUE) {
                    return;
                }
                Token at = offendingSymbol instanceof Token ? (Token) offendingSymbol : null;
                if (e instanceof NoViableAltException && ((NoViableAltException) e).getStartToken() != null) {
                    at = ((NoViableAltException) e).getStartToken();
                }
                firstError[0] = at == null || at.getStartIndex() < 0 ? 0 : at.getStartIndex();
            }
        };
        lexer.addErrorListener(noter);
        parser.addErrorListener(noter);
        return parser.sqlScript();
    }

    /** The index in {@code spoken} of the ')' that closes the '(' at {@code open}, or -1 when it never closes. */
    private static int closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The select item in the tree whose alias ends in the token starting at character {@code start}, or null. */
    private static FrostlakeParser.ExprItemContext itemAliasedAt(final ParserRuleContext context, final int start) {
        if (context instanceof FrostlakeParser.ExprItemContext) {
            final FrostlakeParser.ExprItemContext item = (FrostlakeParser.ExprItemContext) context;
            final ParserRuleContext alias = item.AS() != null ? item.aliasName() : item.identifier();
            if (alias != null && alias.getStop() != null && alias.getStop().getStartIndex() == start) {
                return item;
            }
        }
        for (int i = 0; i < context.getChildCount(); i++) {
            if (context.getChild(i) instanceof ParserRuleContext) {
                final FrostlakeParser.ExprItemContext found =
                    itemAliasedAt((ParserRuleContext) context.getChild(i), start);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The nearest query enclosing {@code context}, or null. */
    private static FrostlakeParser.SelectStatementContext selectStatementOf(final ParserRuleContext context) {
        for (ParserRuleContext up = context.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.SelectStatementContext) {
                return (FrostlakeParser.SelectStatementContext) up;
            }
        }
        return null;
    }

    /** Whether a query is a statement of the script's own — a plain query, not one inside a block. */
    private static boolean isTopLevelQuery(final FrostlakeParser.SelectStatementContext query) {
        if (!(query.getParent() instanceof FrostlakeParser.QueryStatementContext)) {
            return false;
        }
        for (ParserRuleContext up = query.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return false;
            }
        }
        return true;
    }

    /**
     * The SELECT that opens every subquery enclosing the one at {@code innermost}, outermost first, then the
     * refused '(' — or null when one of them does not stand in a SELECT list itself, at the depth of its
     * select item, or closes at or past {@code firstError}, or the outermost query is not a statement of its
     * own.
     */
    private static List<String> selectListSubqueryLines(final List<Token> spoken, final int open,
                                                        final ParserRuleContext innermost, final int firstError) {
        final List<Token> openers = new ArrayList<>();
        ParserRuleContext subquery = innermost;
        while (subquery != null) {
            if (subquery.getStop() == null || subquery.getStop().getStartIndex() >= firstError) {
                return null;
            }
            final Token paren = subquery instanceof FrostlakeParser.ScalarSubqueryExprContext
                ? ((FrostlakeParser.ScalarSubqueryExprContext) subquery).LPAREN().getSymbol()
                : ((FrostlakeParser.InSubqueryExprContext) subquery).LPAREN().getSymbol();
            int at = -1;
            for (int i = 0; i < spoken.size() && at < 0; i++) {
                if (spoken.get(i).getStartIndex() == paren.getStartIndex()) {
                    at = i;
                }
            }
            if (at < 0 || at + 1 >= spoken.size() || spoken.get(at + 1).getType() != FrostlakeLexer.SELECT) {
                return null;
            }
            openers.add(0, spoken.get(at + 1));
            ParserRuleContext up = subquery.getParent();
            while (up != null && !(up instanceof FrostlakeParser.ExprItemContext)) {
                if (up instanceof FrostlakeParser.SelectStatementContext
                        || up instanceof FrostlakeParser.SelectClauseContext) {
                    return null;
                }
                up = up.getParent();
            }
            if (up == null || !bracketsClosedBetween(spoken, ((ParserRuleContext) up).getStart().getStartIndex(), at)) {
                return null;
            }
            final FrostlakeParser.SelectStatementContext outer = selectStatementOf(up);
            if (outer == null) {
                return null;
            }
            if (outer.getParent() instanceof FrostlakeParser.ScalarSubqueryExprContext
                    || outer.getParent() instanceof FrostlakeParser.InSubqueryExprContext) {
                subquery = outer.getParent();
            } else if (isTopLevelQuery(outer)) {
                subquery = null;
            } else {
                return null;
            }
        }
        final List<String> lines = new ArrayList<>();
        for (final Token opener : openers) {
            lines.add(sentence(opener));
        }
        lines.add(sentence(spoken.get(open)));
        return lines;
    }

    /** Whether every bracket opened from character {@code from} up to the token at {@code before} is closed again. */
    private static boolean bracketsClosedBetween(final List<Token> spoken, final int from, final int before) {
        int depth = 0;
        for (int i = 0; i < before; i++) {
            if (spoken.get(i).getStartIndex() < from) {
                continue;
            }
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        return depth == 0;
    }

    /**
     * The '(', the first token inside it that is not another '(', and the CTE's own ')' — or the '(' alone
     * for an empty bracket; null when the bracket is not the last thing in the CTE's body.
     */
    private static List<String> cteBodyLines(final List<Token> spoken, final int open, final int close,
                                             final FrostlakeParser.CteDefinitionContext cte) {
        if (cte.RPAREN() == null || close + 1 >= spoken.size()
                || cte.RPAREN().getSymbol().getStartIndex() != spoken.get(close + 1).getStartIndex()) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(sentence(spoken.get(open)));
        if (close == open + 1) {
            return lines;
        }
        int inner = open + 1;
        while (inner < close && spoken.get(inner).getType() == FrostlakeLexer.LPAREN) {
            inner++;
        }
        if (inner >= close || spoken.get(inner).getType() == FrostlakeLexer.RPAREN) {
            return null;
        }
        lines.add(sentence(spoken.get(inner)));
        lines.add(sentence(spoken.get(close + 1)));
        return lines;
    }

    /** A syntax-error sentence naming {@code token} at its own place. */
    private static String sentence(final Token token) {
        final int[] at = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + at[0] + " at position " + at[1] + " unexpected '" + token.getText() + "'.";
    }



    /**
     * Where the group opened by {@code open} closes, as a token index — the matching ')' or, when it
     * is never closed, the end of the input. Errors falling at or before it belong to a group this
     * listener has already refused and are not reported again; the CLOSER counts as part of the group,
     * which is what keeps `LIMIT ()` to one line. A token PAST it is a separate fault and still
     * speaks — `SAMPLE ((10))` names the inner '(' and then the ')' left over outside it, live-verified.
     *
     * @param recognizer the parser that raised the error
     * @param open the '(' token already refused
     * @return the token index one past the group's contents
     */
    /**
     * Whether {@code open} sits where a FETCH count belongs — right after {@code FETCH}, {@code FETCH
     * FIRST} or {@code FETCH NEXT}. Read off the tokens: the clause is an optional tail of the
     * statement, so the prediction that refuses the '(' dies before the clause's own rule is entered
     * and the parser's context never names it.
     */
    private static boolean inFetchCountSlot(final Recognizer<?, ?> recognizer, final Token open) {
        if (!(recognizer instanceof Parser)) {
            return false;
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        Token previous = null;
        Token beforePrevious = null;
        for (int i = open.getTokenIndex() - 1; i >= 0 && beforePrevious == null; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (previous == null) {
                previous = token;
            } else {
                beforePrevious = token;
            }
        }
        if (previous == null) {
            return false;
        }
        if (previous.getType() == FrostlakeLexer.FETCH) {
            return true;
        }
        return (previous.getType() == FrostlakeLexer.FIRST || previous.getType() == FrostlakeLexer.NEXT)
            && beforePrevious != null && beforePrevious.getType() == FrostlakeLexer.FETCH;
    }

    /**
     * Whether {@code offending} sits inside a bracket that itself follows LIMIT or OFFSET — the count
     * slot's own group, whose opening '(' the parser read as a group rather than refusing.
     */
    private static boolean insideLimitSlotGroup(final Recognizer<?, ?> recognizer, final Token offending) {
        if (!(recognizer instanceof Parser)) {
            return false;
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        Token previous = null;
        Token beforePrevious = null;
        for (int i = offending.getTokenIndex() - 1; i >= 0 && beforePrevious == null; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (previous == null) {
                previous = token;
            } else {
                beforePrevious = token;
            }
        }
        return previous != null && previous.getType() == FrostlakeLexer.LPAREN && beforePrevious != null
            && (beforePrevious.getType() == FrostlakeLexer.LIMIT
                || beforePrevious.getType() == FrostlakeLexer.OFFSET);
    }

    /** The index just before the next OFFSET after {@code from}, or the statement's end without one. */
    private static int nextOffsetOrStatementEnd(final Recognizer<?, ?> recognizer, final Token from) {
        if (!(recognizer instanceof Parser)) {
            return from.getTokenIndex();
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        if (tokens instanceof BufferedTokenStream) {
            ((BufferedTokenStream) tokens).fill();
        }
        for (int i = from.getTokenIndex() + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.OFFSET) {
                return i - 1;
            }
            if (token.getType() == FrostlakeLexer.SEMI) {
                return i;
            }
        }
        return tokens.size();
    }

    /**
     * The index of the token that ends the statement {@code from} belongs to — its separating ';' —
     * or one past the input when there is none. Errors up to it are the wreckage of a statement live
     * has already given up on.
     */
    private static int statementEndIndex(final Recognizer<?, ?> recognizer, final Token from) {
        if (!(recognizer instanceof Parser)) {
            return from.getTokenIndex();
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        if (tokens instanceof BufferedTokenStream) {
            ((BufferedTokenStream) tokens).fill();
        }
        for (int i = from.getTokenIndex() + 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() == FrostlakeLexer.SEMI) {
                return i;
            }
        }
        return tokens.size();
    }

    /**
     * Whether {@code close} brings the statement's bracket depth back to zero — the ')' that shuts the
     * outermost group still open, counted from the statement's own start.
     */
    private static boolean closesTheLastOpenGroup(final Recognizer<?, ?> recognizer, final Token close) {
        if (!(recognizer instanceof Parser)) {
            return false;
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        int depth = 0;
        for (int i = close.getTokenIndex(); i >= 0; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.SEMI && i != close.getTokenIndex()) {
                break;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                depth--;
            }
        }
        return depth == 0;
    }

    private static int groupCloseIndex(final Recognizer<?, ?> recognizer, final Token open) {
        if (!(recognizer instanceof Parser)) {
            return open.getTokenIndex() + 1;
        }
        final TokenStream tokens = ((Parser) recognizer).getInputStream();
        if (tokens instanceof BufferedTokenStream) {
            // The stream is filled LAZILY, so mid-parse it holds only what has been read so far and
            // the closing bracket is usually not in it yet. Without this the span came out short and
            // the group's own errors were reported after all.
            ((BufferedTokenStream) tokens).fill();
        }
        int depth = 0;
        for (int i = open.getTokenIndex(); i < tokens.size(); i++) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return tokens.size();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Mark this listener as watching a whole STATEMENT, which enables the leading-token rule.
     */
    public void statementParse() {
        this.statementParse = true;
    }

    /**
     * The one line live reports for a statement that begins with a word no statement can begin with.
     *
     * <p>★ LIVE BLAMES THE FIRST TOKEN IT CANNOT USE, which is the rule the whole anchor family turns
     * on: {@code SELCT 1} is "unexpected 'SELCT'" at position 0, not "unexpected '1'" further along.
     * Frostlake's parser gets past the misspelling — a bare identifier opens the assignment statement,
     * so the alternative is viable until the token after it — and blames whatever it reached.
     *
     * <p>The test is deliberately narrow: a statement may open with a bare identifier ONLY as an
     * assignment, {@code x := 1}. So an identifier NOT followed by {@code :=} could never have been a
     * statement, and naming it costs nothing that parses. Every keyword-led statement is untouched,
     * which is why this needs no list of legal opening words — the lexer already separates them.
     *
     * @return the single reported line, or null when the rule does not apply
     */
    /**
     * The one line live reports for a script that OPENS with a semicolon: the first token after the
     * semicolons, at its own place — {@code ;SELECT 1} is "unexpected 'SELECT'" at position 1, and
     * {@code ;} followed by a newline names line 2 — where this parser refused the semicolon itself.
     * Semicolons alone are "Empty SQL statement." on both engines and never reach here; a doubled
     * separator BETWEEN statements is accepted by both.
     */
    private String leadingSemicolonRefusal() {
        if (!statementParse || sql == null) {
            return null;
        }
        final List<Token> spoken = defaultChannelTokens();
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.SEMI) {
            return null;
        }
        int i = 0;
        while (i < spoken.size() && spoken.get(i).getType() == FrostlakeLexer.SEMI) {
            i++;
        }
        if (i >= spoken.size() || spoken.get(i).getType() == Token.EOF) {
            return null;
        }
        final Token first = spoken.get(i);
        final int[] shown = LeadingCommentOffset.rebase(first.getLine(), first.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1]
            + " unexpected '" + first.getText() + "'.";
    }

    /**
     * The one line live reports for a scripting block that RAN OUT OF INPUT — more BEGINs than ENDs —
     * whatever this parser tripped over on the way: {@code DECLARE x INTEGER; BEGIN BEGIN x := 1;
     * RETURN x; END;} is "unexpected '&lt;EOF&gt;'" at the input's own end on the account, where the
     * parser here, unable to complete the nested block, read its BEGIN as a transaction and died on
     * the {@code x} after it. A block WITHOUT a declaration parses whole and is judged by
     * {@link StatementSeparation}; with one, the first BEGIN belongs to the block grammatically and
     * the failure lands inside the rule, so the judgement is made here instead.
     *
     * <p>Only the signature of that failure is overridden: the script opens with DECLARE or a bare
     * BEGIN, the block's openers outnumber its closers, and the first error sits on the token right
     * after a bare BEGIN or at the end of the input. A typo inside an unbalanced block keeps its own
     * line.
     */
    private String unbalancedBlockRefusal() {
        if (!statementParse || sql == null) {
            return null;
        }
        final List<Token> spoken = defaultChannelTokens();
        if (spoken.isEmpty()) {
            return null;
        }
        final Token opener = spoken.get(0);
        if (opener.getType() != FrostlakeLexer.DECLARE
                && !(opener.getType() == FrostlakeLexer.BEGIN && isBareBegin(spoken, 0))) {
            return null;
        }
        int depth = 0;
        boolean firstErrorAfterBegin = false;
        for (int i = 0; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getType() == FrostlakeLexer.BEGIN && isBareBegin(spoken, i)) {
                depth++;
                if (i + 1 < spoken.size() && spoken.get(i + 1).getStartIndex() == firstOffendingStartIndex) {
                    firstErrorAfterBegin = true;
                }
            } else if (token.getType() == FrostlakeLexer.CASE) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.END && !closesAControlStatement(spoken, i)) {
                depth--;
            }
        }
        if (depth <= 0) {
            return null;
        }
        final boolean firstErrorAtEnd = !atEndOfInput.isEmpty() && Boolean.TRUE.equals(atEndOfInput.get(0));
        if (!firstErrorAfterBegin && !firstErrorAtEnd) {
            return null;
        }
        final int[] shown = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.";
    }

    /**
     * The lines a scripting BLOCK's parse leaves behind once the parser has ABANDONED the block, which
     * live never reports. Live reads a block statement by statement: a fault inside one statement is
     * ONE line, the parse resumes at that statement's semicolon, and a second faulty statement adds its
     * own line — {@code LET r RESULTSET := 1; RETURN 'x';} is one line, two such LETs are two, a fault
     * inside a nested block or an EXCEPTION handler is one, and the block's own END is never named
     * (live-verified). This parser, unable to complete a block with a fault inside it, gives the block
     * alternative up and reads the block's statements at top level instead: the fault is reported at
     * the same token live names, but the statement's remaining tokens can be named again, the
     * EXCEPTION section is a dead end of its own, and the closing {@code END;} is a statement that
     * opens at END. Those are the artifacts, dropped here, and only for a script that IS a block —
     * one opening with DECLARE or a bare BEGIN — and only once a real line exists to stand:
     *
     * <ul>
     *   <li>a line whose statement opened at END, after an earlier line;</li>
     *   <li>a line whose statement opened at EXCEPTION or WHEN, beside any other line;</li>
     *   <li>a line inside the SAME statement as the last kept line — at or before the first
     *       semicolon that follows it.</li>
     * </ul>
     *
     * <p>A lone line is never an artifact: {@code RETURN 1 END;} is refused at the END itself, as live
     * refuses it. End-of-input lines keep their own rule.
     *
     * @return per message line, whether it is such an artifact
     */
    private boolean[] abandonedBlockArtifacts() {
        final boolean[] artifacts = new boolean[messageLines.size()];
        // Not gated on a STATEMENT parse: the shape gate below is what makes the rule safe, and the
        // dynamic and CALL-time parses of a block body arrive without the marker.
        if (sql == null || messageLines.size() < 2) {
            return artifacts;
        }
        final List<Token> spoken = defaultChannelTokens();
        if (spoken.isEmpty()) {
            return artifacts;
        }
        final Token opener = spoken.get(0);
        if (opener.getType() != FrostlakeLexer.DECLARE
                && !(opener.getType() == FrostlakeLexer.BEGIN && isBareBegin(spoken, 0))) {
            return artifacts;
        }
        int positioned = 0;
        for (int i = 0; i < messageLines.size(); i++) {
            if (i < offendingIndexes.size() && offendingIndexes.get(i).intValue() >= 0) {
                positioned++;
            }
        }
        if (positioned < 2) {
            return artifacts;
        }
        int lastKeptIndex = -1;
        for (int i = 0; i < messageLines.size(); i++) {
            final int index = i < offendingIndexes.size() ? offendingIndexes.get(i).intValue() : -1;
            if (index < 0) {
                continue;
            }
            final int openedAt = i < openerTypes.size() ? openerTypes.get(i).intValue() : -1;
            if (i < surplusEnds.size() && Boolean.TRUE.equals(surplusEnds.get(i)) && lastKeptIndex >= 0) {
                artifacts[i] = true;
                continue;
            }
            if (openedAt == FrostlakeLexer.EXCEPTION || openedAt == FrostlakeLexer.WHEN) {
                artifacts[i] = true;
                continue;
            }
            if (lastKeptIndex >= 0 && index <= firstSemicolonAfter(spoken, lastKeptIndex)) {
                artifacts[i] = true;
                continue;
            }
            lastKeptIndex = index;
        }
        return artifacts;
    }

    /** The token index of the first ';' after token index {@code after}, or the input's end. */
    private static int firstSemicolonAfter(final List<Token> spoken, final int after) {
        for (final Token token : spoken) {
            if (token.getTokenIndex() > after && token.getType() == FrostlakeLexer.SEMI) {
                return token.getTokenIndex();
            }
        }
        return Integer.MAX_VALUE;
    }

    /** The type of the token the parser's current outermost statement opened at, or -1. */
    private static int statementOpenerType(final Recognizer<?, ?> recognizer) {
        if (!(recognizer instanceof Parser)) {
            return -1;
        }
        Token outermostStart = null;
        ParserRuleContext context = ((Parser) recognizer).getContext();
        while (context != null) {
            if (context.getParent() != null && context.getStart() != null) {
                outermostStart = context.getStart();
            }
            context = context.getParent();
        }
        return outermostStart == null ? -1 : outermostStart.getType();
    }

    /** Whether the BEGIN at {@code i} opens a block — not WORK, TRANSACTION or a named transaction. */
    private static boolean isBareBegin(final List<Token> spoken, final int i) {
        if (i + 1 >= spoken.size()) {
            return true;
        }
        final int next = spoken.get(i + 1).getType();
        return next != FrostlakeLexer.WORK && next != FrostlakeLexer.TRANSACTION
            && next != FrostlakeLexer.NAME;
    }

    /** Whether the END at {@code i} closes an IF / LOOP / FOR / WHILE / REPEAT rather than a block or a CASE. */
    private static boolean closesAControlStatement(final List<Token> spoken, final int i) {
        if (i + 1 >= spoken.size()) {
            return false;
        }
        final int next = spoken.get(i + 1).getType();
        return next == FrostlakeLexer.IF || next == FrostlakeLexer.LOOP || next == FrostlakeLexer.FOR
            || next == FrostlakeLexer.WHILE || next == FrostlakeLexer.REPEAT;
    }

    /** The input re-lexed, default-channel tokens only, the EOF token included. */
    private List<Token> defaultChannelTokens() {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                spoken.add(token);
            }
        }
        return spoken;
    }

    private String leadingIdentifierRefusal() {
        if (!statementParse || sql == null) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> all = tokens.getTokens();
        if (all.isEmpty() || all.get(0).getType() != FrostlakeLexer.IDENTIFIER) {
            return null;
        }
        if (all.size() > 1 && all.get(1).getType() == FrostlakeLexer.COLON_EQ) {
            return null;
        }
        // A LEADING COMMENT does not move live's position, and the rebase is where that is already
        // settled for every other syntax error.
        final int[] shown = LeadingCommentOffset.rebase(
            all.get(0).getLine(), all.get(0).getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1]
            + " unexpected '" + all.get(0).getText() + "'.";
    }

    /**
     * The one line live reports when an unknown word sits where a data type belongs.
     *
     * <p>★ LIVE NAMES THE TYPE INSTEAD OF REFUSING THE SYNTAX. {@code CREATE TABLE t (c
     * NOSUCHTYPE(5,2))} is "Unsupported data type 'NOSUCHTYPE(5, 2)'." — unpositioned, the LAST
     * dotted part of the written name, unquoted and upper-cased, with the written parameters echoed
     * inside the quotes joined by ", ". The same sentence covers ALTER TABLE ADD COLUMN, CAST(x AS
     * t) and x::t, and quoting does not protect a REAL type name: {@code (c "VARCHAR")} is
     * unsupported 'VARCHAR', because the slot is resolved by NAME after the parse, not by keyword.
     * A KEYWORD in the slot stays a plain syntax error — {@code (c SELECT)} names 'SELECT' — and so
     * does a word after a COMPLETE type: {@code (c INT badword)} names 'badword' (live-verified).
     *
     * <p>The slot test is a substitution parse: the doubtful name chain and its bracket group are
     * replaced by a known type, and the sentence applies exactly when that substitution makes the
     * statement whole — which is what "where a data type belongs" means. The known type only reads
     * as a TYPE where one belongs (elsewhere it is one more identifier), so a word that fails for
     * any other reason keeps failing and keeps its syntax line. No grammar change is involved.
     *
     * @return the single reported line, or null when the rule does not apply
     */
    private String unsupportedTypeRefusal() {
        if (!statementParse || sql == null || firstOffendingStartIndex < 0) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> all = tokens.getTokens();
        int at = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getChannel() == Token.DEFAULT_CHANNEL
                    && all.get(i).getStartIndex() == firstOffendingStartIndex) {
                at = i;
                break;
            }
        }
        if (at < 0 || !isIdentifierToken(all.get(at))) {
            return null;
        }
        // The written name may be a dotted chain; live keeps only its LAST part.
        Token lastPart = all.get(at);
        int end = at;
        int next = nextDefaultChannel(all, at);
        while (next >= 0 && all.get(next).getType() == FrostlakeLexer.DOT) {
            final int part = nextDefaultChannel(all, next);
            if (part < 0 || !isIdentifierToken(all.get(part))) {
                return null;
            }
            lastPart = all.get(part);
            end = part;
            next = nextDefaultChannel(all, part);
        }
        // The bracket group after the name holds the type's written parameters.
        final List<String> params = new ArrayList<>();
        boolean parameterized = false;
        if (next >= 0 && all.get(next).getType() == FrostlakeLexer.LPAREN) {
            parameterized = true;
            int depth = 0;
            int close = -1;
            int paramFrom = -1;
            int paramTo = -1;
            for (int i = next; i < all.size() && close < 0; i++) {
                final Token t = all.get(i);
                if (t.getChannel() != Token.DEFAULT_CHANNEL || t.getType() == Token.EOF) {
                    if (t.getType() == Token.EOF) {
                        break;
                    }
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
                            params.add(sql.substring(all.get(paramFrom).getStartIndex(),
                                all.get(paramTo).getStopIndex() + 1));
                        }
                        close = i;
                        continue;
                    }
                } else if (depth == 1 && t.getType() == FrostlakeLexer.COMMA) {
                    if (paramFrom >= 0) {
                        params.add(sql.substring(all.get(paramFrom).getStartIndex(),
                            all.get(paramTo).getStopIndex() + 1));
                    }
                    paramFrom = -1;
                    continue;
                }
                if (paramFrom < 0) {
                    paramFrom = i;
                }
                paramTo = i;
            }
            if (close < 0) {
                return null;
            }
            end = close;
        }
        final String substituted = sql.substring(0, all.get(at).getStartIndex()) + "NUMBER"
            + sql.substring(all.get(end).getStopIndex() + 1);
        if (!parsesClean(substituted)) {
            return null;
        }
        String name = lastPart.getText();
        if (lastPart.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
        }
        final StringBuilder spelled = new StringBuilder(name.toUpperCase(Locale.ROOT));
        if (parameterized) {
            spelled.append('(');
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    spelled.append(", ");
                }
                spelled.append(params.get(i));
            }
            spelled.append(')');
        }
        return "Unsupported data type '" + spelled + "'.";
    }

    /**
     * Whether the token could spell a data type's name: a plain or a quoted identifier.
     */
    private static boolean isIdentifierToken(final Token token) {
        return token.getType() == FrostlakeLexer.IDENTIFIER
            || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /**
     * The index of the next default-channel token after {@code from}, or -1 at the end.
     */
    private static int nextDefaultChannel(final List<Token> all, final int from) {
        for (int i = from + 1; i < all.size(); i++) {
            if (all.get(i).getChannel() == Token.DEFAULT_CHANNEL
                    && all.get(i).getType() != Token.EOF) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether {@code candidate} parses as a script with no syntax errors at all — the substitution
     * check behind the unsupported-data-type sentence.
     */
    private static boolean parsesClean(final String candidate) {
        return cleanParse(candidate) != null;
    }

    /** The parse tree of {@code candidate} as a script, or null when it has any syntax error at all. */
    private static FrostlakeParser.SqlScriptContext cleanParse(final String candidate) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(candidate));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        final int[] faults = new int[1];
        final BaseErrorListener counter = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                    final int line, final int charPositionInLine, final String msg,
                    final RecognitionException e) {
                faults[0]++;
            }
        };
        lexer.addErrorListener(counter);
        parser.addErrorListener(counter);
        final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
        return faults[0] == 0 ? tree : null;
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
