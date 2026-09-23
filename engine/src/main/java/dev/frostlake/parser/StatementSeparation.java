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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The SEMICOLON between two statements, which live REQUIRES and this grammar leaves optional.
 *
 * <p>Every statement rule carries its own trailing {@code SEMI?} and the script rule adds another, so
 * two statements written with nothing between them parse happily as two — {@code SELECT 1 SELECT 2}
 * answered 1 here where live refuses it. Making the separator mandatory in the grammar is not
 * available: the statements consume it themselves, so the script rule never sees one to require. It is
 * checked here instead, over the parse tree and the token stream, once per parse.
 *
 * <p>★ TWO REFUSALS, AND WHICH ONE DEPENDS ON THE FIRST STATEMENT. A bare {@code BEGIN} — no WORK, no
 * TRANSACTION, no name — is BOTH a transaction start and the opening of a scripting block, and live
 * reads it as the block whenever anything follows it. So a block that loses an END is not "two
 * statements without a separator" to live; it is one block that ran out of input:
 *
 * <pre>
 *   BEGIN BEGIN RETURN 42; END;        unexpected '&lt;EOF&gt;'   the outer block never closes
 *   BEGIN SELECT 1;                    unexpected '&lt;EOF&gt;'   same shape, any body
 *   BEGIN TRANSACTION BEGIN … END;     unexpected 'BEGIN'   an explicit transaction cannot open one
 *   SELECT 1 SELECT 2                  unexpected 'SELECT'  the ordinary missing separator
 * </pre>
 *
 * <p>★ THE EOF ANCHOR IS THE INPUT'S OWN END — the line and column one PAST the last character, which
 * for text ending in a newline is column 0 of the following line. Live-verified across a one-line
 * block, four-line blocks with and without a trailing newline, and a DECLARE section.
 *
 * <p>★ WHAT STAYS LEGAL: a bare {@code BEGIN} that IS the whole statement (live accepts it, and
 * {@code BEGIN TRANSACTION} / {@code BEGIN WORK} / {@code BEGIN NAME t} with or without a semicolon),
 * every multi-statement script whose statements DO carry their semicolons, and a doubled {@code ;}.
 *
 * <p>★ A WORD THAT CAN BE A NAME IS SWALLOWED AS AN ALIAS. When the first statement is a query ending in
 * an unaliased select item or table reference, or a DELETE ending at its unaliased target, and the next
 * statement begins with a word that can be a name, live reads that word as the ALIAS and refuses the
 * token after it — one line, nothing stacked (all live-verified):
 *
 * <pre>
 *   SELECT 'foo' RETURN :v             unexpected ':' at 20       SELECT 'foo' RETURN 1 2   '1' alone
 *   SELECT a FROM t RETURN 1           unexpected '1' at 23       SELECT 'foo' SHOW TABLES  'TABLES'
 *   SELECT 'foo' AS a RETURN 1         unexpected 'RETURN' at 18  — already aliased, the word is the fault
 * </pre>
 *
 * <p>★ OTHERWISE THE RECOVERY RESUMES THE INTERRUPTED STATEMENT. Past the refused word live skips to the
 * first NAME the first statement can still take — as its alias — reads on from there, and reports the
 * next fault it meets as a second line: {@code SELECT 1 CREATE TABLE u (a INT)} resumes at {@code u} — an alias of
 * {@code 1} — and refuses the '(' after it; {@code SELECT 1 SELECT a FROM t} resumes at {@code a} and
 * reads {@code FROM t} without a fault, so it stays one line; {@code SELECT 1 AS x CALL p(a, b)} finds
 * no name the aliased item can take, and is one line too. The skip stops at the second statement's own
 * semicolon. Found by trying each name in turn, over a copy of the text with the skipped tokens blanked.
 *
 * <p>★ A '(' AFTER THE NAME A DROP OR A DESCRIBE ENDS IN is that name's signature to live, so the token after it is
 * the one refused: {@code DESCRIBE TABLE t1 (SELECT 1)} is 'SELECT' at 19, one line (live-verified).
 *
 * <p>★ THE EARLIER FAULT WINS. Where the parse itself also failed, further on, the missing separator is
 * reported instead when it stands first: {@code SELECT 1 SELECT 2 x y} is 'SELECT' at 9, then 'y' at 20.
 */
public final class StatementSeparation {

    private StatementSeparation() {
    }

    /**
     * Refuse a script whose statements are not separated by semicolons.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     */
    public static void requireSeparators(final FrostlakeParser.SqlScriptContext script,
                                         final TokenStream tokens, final String sql) {
        rejectSplitSelectInto(script, tokens, sql);
        final MissingSeparator fault = firstFault(script, tokens, sql, true);
        if (fault != null) {
            throw fault.refusal(sql);
        }
    }

    /**
     * Refuse a script's missing separator ahead of the parse's own faults when it stands before them in
     * the text — the order live reports them in. Faults of the parse that come first are left for its
     * listener to report.
     *
     * @param script the parsed script
     * @param tokens the token stream it was parsed from
     * @param sql the script's source text
     * @param listener the parse's error listener
     */
    public static void requireSeparatorsBefore(final FrostlakeParser.SqlScriptContext script,
                                               final TokenStream tokens, final String sql,
                                               final SyntaxErrorListener listener) {
        if (!listener.hasErrors() || listener.firstLineSettled()) {
            return;
        }
        final boolean queryOrDml = opensWithQueryOrDml(script);
        if (!queryOrDml && !opensWithOtherStatement(script)) {
            return;
        }
        final String parseFault = listener.firstReportedLine();
        final int[] parseFaultAt = parseFault == null ? null : SyntaxErrorListener.sentenceCoordinates(parseFault);
        if (parseFaultAt == null) {
            return;
        }
        final MissingSeparator fault = firstFault(script, tokens, sql, true);
        if (fault != null && fault.precedes(parseFaultAt) && separatesRealStatements(script, fault, queryOrDml)
                && (queryOrDml || opensOnlyAStatement(script, fault))) {
            throw fault.refusal(sql);
        }
    }

    /**
     * Refuse a SELECT … INTO this grammar read as two statements: a query ending in an unaliased item, then a
     * statement whose first word that item takes as its alias. {@code SELECT 1 COPY INTO t FROM @s} is one
     * query on the account, and its INTO clause is not allowed outside a block (live-verified), where this
     * parser read a COPY that lacked its semicolon.
     */
    private static void rejectSplitSelectInto(final FrostlakeParser.SqlScriptContext script,
                                              final TokenStream tokens, final String sql) {
        if (script == null || tokens == null || sql == null || script.flowChain().size() < 2
                || separated(script.flowChain().get(0), script.flowChain().get(1), tokens)) {
            return;
        }
        final Token word = script.flowChain().get(1).getStart();
        if (word == null || !swallowsWord(script.flowChain().get(0), word)) {
            return;
        }
        final StringBuilder text = new StringBuilder(sql);
        for (int i = word.getStartIndex(); i <= word.getStopIndex() && i < text.length(); i++) {
            text.setCharAt(i, 'x');
        }
        final FrostlakeParser.SqlScriptContext aliased = SyntaxErrorListener.cleanParse(text.toString());
        if (aliased == null || aliased.flowChain().isEmpty() || aliased.flowChain().get(0).statement().isEmpty()) {
            return;
        }
        final FrostlakeParser.StatementContext first = aliased.flowChain().get(0).statement().get(0);
        if (first.proceduralStatement() == null || first.proceduralStatement().selectIntoStatement() == null
                || first.getStop() == null || first.getStop().getStopIndex() < word.getStopIndex()) {
            return;
        }
        final Token select = first.proceduralStatement().selectIntoStatement().SELECT().getSymbol();
        throw new RuntimeException(SqlCompilationError.intoClauseNotAllowed(select.getLine(),
            select.getCharPositionInLine()));
    }

    /**
     * Whether a missing separator stands between two REAL statements, and not between the pieces a failed
     * statement was broken into: every statement before it parsed without a fault of its own, and the
     * second one begins with a word — {@code SELECT MAX(} is one unclosed call, not {@code SELECT MAX}
     * followed by a statement opening at the '(' — or is a bracketed query that parsed whole: {@code SELECT a
     * x (SELECT 1))} is refused at the '(' after the alias, as it is without the stray ')' (live-verified).
     * After a query or a DML statement the second one must parse whole too ({@code wholeSecond}); after any other
     * statement its own later fault does not matter — {@code DROP TABLE t SELECT 1 x y} is 'SELECT' alone.
     */
    private static boolean separatesRealStatements(final FrostlakeParser.SqlScriptContext script,
                                                   final MissingSeparator fault, final boolean wholeSecond) {
        final List<FrostlakeParser.FlowChainContext> chains = script.flowChain();
        if (fault.secondChain() < 1 || fault.secondChain() >= chains.size()) {
            return false;
        }
        final Token second = chains.get(fault.secondChain()).getStart();
        if (second == null || second.getText() == null || second.getText().isEmpty()) {
            return false;
        }
        final boolean bracketedQuery = second.getType() == FrostlakeLexer.LPAREN
            && isClean(chains.get(fault.secondChain()));
        if (!Character.isLetter(second.getText().charAt(0)) && !bracketedQuery) {
            return false;
        }
        // A refused WORD needs the second statement whole as well: ORDER BY a OFFSET -1 is one statement
        // whose OFFSET the parse split off. A swallowed word is read on as the first statement's alias,
        // so what the second statement would have been does not matter — nor does it after an INSERT,
        // which opens nothing but a statement and resumes nothing: SELECT 1 INSERT t VALUES (1) is
        // 'INSERT' alone on the account.
        final boolean secondWhole = wholeSecond && !fault.swallows() && second.getType() != FrostlakeLexer.INSERT;
        final int clean = secondWhole ? fault.secondChain() + 1 : fault.secondChain();
        for (int i = 0; i < clean; i++) {
            if (!isClean(chains.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a parse subtree holds no recognition fault and no error node. */
    private static boolean isClean(final ParseTree tree) {
        if (tree instanceof ErrorNode) {
            return false;
        }
        if (tree instanceof ParserRuleContext && ((ParserRuleContext) tree).exception != null) {
            return false;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (!isClean(tree.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a script's first statement is one of the others whose missing separator comes first — any statement but
     * a query, a DML statement or a scripting one, a CALL and an EXECUTE IMMEDIATE included: {@code GRANT SELECT ON
     * TABLE t TO ROLE r REVOKE GRANTS} is 'REVOKE' alone on the account, as it is after a DROP, an ALTER, a USE, a SHOW,
     * a TRUNCATE, a COMMIT, a SET, a CALL, a CREATE or a DESCRIBE (live-verified).
     */
    private static boolean opensWithOtherStatement(final FrostlakeParser.SqlScriptContext script) {
        if (script == null || script.flowChain().isEmpty() || script.flowChain().get(0).statement().isEmpty()) {
            return false;
        }
        final FrostlakeParser.StatementContext first = script.flowChain().get(0).statement().get(0);
        if (first.queryStatement() != null || first.dmlStatement() != null) {
            return false;
        }
        final FrostlakeParser.ProceduralStatementContext procedural = first.proceduralStatement();
        return procedural == null || procedural.callStatement() != null || procedural.executeImmediateStatement() != null;
    }

    /**
     * Whether the second statement of a missing separator opens with a word that opens nothing but a statement. After a
     * statement other than a query, the word may be a clause the first statement takes, read differently here: {@code
     * CREATE TABLE tq (a INT) COMMENT 'x'} is refused at the 'x' on the account, which reads the COMMENT as the table's.
     */
    private static boolean opensOnlyAStatement(final FrostlakeParser.SqlScriptContext script,
                                               final MissingSeparator fault) {
        final List<FrostlakeParser.FlowChainContext> chains = script.flowChain();
        if (fault.secondChain() < 1 || fault.secondChain() >= chains.size()) {
            return false;
        }
        final Token word = chains.get(fault.secondChain()).getStart();
        final int type = word == null ? Token.INVALID_TYPE : word.getType();
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.INSERT || type == FrostlakeLexer.UPDATE
            || type == FrostlakeLexer.DELETE || type == FrostlakeLexer.MERGE || type == FrostlakeLexer.CREATE
            || type == FrostlakeLexer.ALTER || type == FrostlakeLexer.DROP || type == FrostlakeLexer.UNDROP
            || type == FrostlakeLexer.GRANT || type == FrostlakeLexer.REVOKE || type == FrostlakeLexer.SHOW
            || type == FrostlakeLexer.DESCRIBE || type == FrostlakeLexer.USE || type == FrostlakeLexer.TRUNCATE
            || type == FrostlakeLexer.COMMIT || type == FrostlakeLexer.ROLLBACK || type == FrostlakeLexer.LIST
            || type == FrostlakeLexer.LS || type == FrostlakeLexer.GET || type == FrostlakeLexer.PUT
            || type == FrostlakeLexer.REMOVE || type == FrostlakeLexer.RM || type == FrostlakeLexer.EXPLAIN;
    }

    /**
     * Whether a script's first statement is a query or a DML statement — the scripts whose missing
     * separator competes with the parse's own faults. A failed BLOCK can leave its pieces read as
     * top-level statements, and the block's own report stands there.
     */
    private static boolean opensWithQueryOrDml(final FrostlakeParser.SqlScriptContext script) {
        if (script == null || script.flowChain().isEmpty()) {
            return false;
        }
        final List<FrostlakeParser.StatementContext> first = script.flowChain().get(0).statement();
        return first != null && !first.isEmpty()
            && (first.get(0).queryStatement() != null || first.get(0).dmlStatement() != null);
    }

    /** The first missing separator of a script, with the lines live reports for it, or null. */
    private static MissingSeparator firstFault(final FrostlakeParser.SqlScriptContext script,
                                               final TokenStream tokens, final String sql,
                                               final boolean stackSecondLine) {
        if (script == null || tokens == null) {
            return null;
        }
        final List<FrostlakeParser.FlowChainContext> chains = script.flowChain();
        for (int i = 0; i + 1 < chains.size(); i++) {
            final FrostlakeParser.FlowChainContext chain = chains.get(i);
            final FrostlakeParser.FlowChainContext next = chains.get(i + 1);
            if (separated(chain, next, tokens)) {
                continue;
            }
            if (opensABlock(chain)) {
                return fault("<EOF>", EndOfInput.line(sql), EndOfInput.position(sql), null).between(i + 1);
            }
            final Token word = next.getStart();
            if (word.getType() == FrostlakeLexer.LPAREN && endsInSignableName(chain)) {
                // The '(' opens the name's signature, which no statement's first token can open.
                final Token inside = nextSpoken(tokens, word.getTokenIndex());
                return fault(inside.getText(), inside.getLine(), inside.getCharPositionInLine(), null).between(i + 1);
            }
            if (swallowsWord(chain, word)) {
                final Token follower = nextSpoken(tokens, word.getTokenIndex());
                if (follower != null && follower.getType() != Token.EOF
                        && follower.getType() != FrostlakeLexer.SEMI) {
                    final String asAlias = stackSecondLine ? aliasedFault(sql, word) : null;
                    if (asAlias != null) {
                        final int[] at = SyntaxErrorListener.sentenceCoordinates(asAlias);
                        return new MissingSeparator(at[0], at[1], Collections.singletonList(asAlias)).between(i + 1)
                            .swallowing();
                    }
                    return fault(follower.getText(), follower.getLine(), follower.getCharPositionInLine(), null)
                        .between(i + 1).swallowing();
                }
            }
            // A DROP or a DESCRIBE is not resumed: DESCRIBE TABLE t UPDATE t SET a = 1 x y is 'UPDATE' alone, though
            // the t after the word could be read as a property's name. Nor is a bracketed query run into the statement
            // before it, refused at its '(' alone whatever follows the bracket: SELECT a x (SELECT 1) FROM t) is '('
            // at 11 and nothing more (live-verified).
            final boolean resumes = stackSecondLine && !endsInSignableName(chain)
                && word.getType() != FrostlakeLexer.LPAREN;
            return fault(word.getText(), word.getLine(), word.getCharPositionInLine(),
                resumes ? resumedFault(tokens, sql, word, !endsInUnaliasedTable(chain)) : null).between(i + 1);
        }
        return null;
    }

    /** Whether a chain's last statement is a DROP or DESCRIBE ending with the object name it takes. */
    private static boolean endsInSignableName(final FrostlakeParser.FlowChainContext chain) {
        final List<FrostlakeParser.StatementContext> statements = chain.statement();
        return statements != null && !statements.isEmpty()
            && UnseparatedStatementOpener.endsInSignableName(statements.get(statements.size() - 1));
    }

    /**
     * Whether the statement before a missing separator takes the next word as its bare ALIAS: a query
     * ending in an unaliased select item or table reference, or a DELETE ending at its unaliased target,
     * followed by a word that can be a name.
     */
    private static boolean swallowsWord(final FrostlakeParser.FlowChainContext chain, final Token word) {
        final List<FrostlakeParser.StatementContext> statements = chain.statement();
        if (statements == null || statements.isEmpty()) {
            return false;
        }
        final FrostlakeParser.StatementContext last = statements.get(statements.size() - 1);
        final boolean takesAlias = last.queryStatement() != null && SyntaxErrorListener.endsTakingBareAlias(last)
            || SyntaxErrorListener.endsInUnaliasedDeleteTarget(last);
        return takesAlias && FrostlakeParser._ATN.nextTokens(
            FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(word.getType());
    }

    /**
     * The fault met when the swallowed word is read as the alias it is: the text again, with the word
     * written as a plain name of its own length, so that a token after it the query can still take —
     * {@code SELECT 1 MERGE INTO t USING …} reads INTO and refuses USING — is read on. Null when the
     * text then has no fault, or a fault that carries no position.
     */
    private static String aliasedFault(final String sql, final Token word) {
        final StringBuilder text = new StringBuilder(sql);
        for (int i = word.getStartIndex(); i <= word.getStopIndex() && i < text.length(); i++) {
            text.setCharAt(i, 'x');
        }
        final String firstLine = firstFaultOf(text.toString(), -1);
        return firstLine == null || SyntaxErrorListener.sentenceCoordinates(firstLine) == null ? null : firstLine;
    }

    /**
     * The second line live stacks after refusing {@code word}: the first fault met once the interrupted
     * statement resumes at the first token after the word that it can still take — or null when no token
     * before the next statement's semicolon resumes it, or the statement then reads on without a fault.
     */
    private static String resumedFault(final TokenStream tokens, final String sql, final Token word,
                                       final boolean namesResume) {
        final String resumed = resumedLine(tokens, sql, word, namesResume);
        return resumed == null || resumed.isEmpty() ? null : resumed;
    }

    /**
     * The first fault met once the statement interrupted by {@code word} resumes at the first token after
     * it that the statement can still take: that line, the empty string when the statement resumes and reads
     * on without a fault, or null when no token before the next statement's semicolon resumes it.
     *
     * @param tokens the token stream of the text
     * @param sql the text, blanked wherever it must not be read — only the interrupted statement and the
     *            statement {@code word} opens are
     * @param word the refused word
     * @return the resumed statement's first fault, the empty string, or null
     */
    static String resumedLine(final TokenStream tokens, final String sql, final Token word) {
        return resumedLine(tokens, sql, word, true);
    }

    /**
     * As {@link #resumedLine(TokenStream, String, Token)}, where a name resumes the statement only when
     * {@code namesResume} holds: after an unaliased table reference live resumes at no name and no bracket, only at a
     * clause's keyword or an operator the query can go on with.
     *
     * @param tokens the token stream of the text
     * @param sql the text, blanked wherever it must not be read
     * @param word the refused word
     * @param namesResume whether a name may resume the statement
     * @return the resumed statement's first fault, the empty string, or null
     */
    static String resumedLine(final TokenStream tokens, final String sql, final Token word, final boolean namesResume) {
        if (word.getType() == FrostlakeLexer.INSERT || word.getType() == FrostlakeLexer.WITH) {
            // INSERT and WITH are the refused words after which live resumes nothing in every form measured —
            // INTO … VALUES, INTO … SELECT, a column list, OVERWRITE, no INTO, a WITH before its query —
            // where UPDATE t SET and ALTER TABLE t ADD each resume at t and refuse the word after it.
            return null;
        }
        final Token end = statementEnd(tokens, word);
        int depth = 0;
        for (Token resume = nextSpoken(tokens, word.getTokenIndex());
                resume != null && resume.getType() != Token.EOF && resume.getTokenIndex() <= end.getTokenIndex()
                    && resume.getType() != FrostlakeLexer.SEMI;
                resume = nextSpoken(tokens, resume.getTokenIndex())) {
            // Inside a bracket the refused statement opened only a name resumes, as it always has.
            final boolean inside = depth > 0;
            if (resume.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (resume.getType() == FrostlakeLexer.RPAREN && depth > 0) {
                depth--;
            }
            if (isName(resume) ? !namesResume
                    : inside || resume.getType() == FrostlakeLexer.BEGIN
                        || resume.getType() == FrostlakeLexer.LPAREN && !namesResume) {
                continue;
            }
            final String candidate = blanked(sql, word.getStartIndex(), resume.getStartIndex(),
                end.getType() == Token.EOF ? sql.length() : end.getStopIndex() + 1);
            final String firstLine = firstFaultOf(candidate, resume.getStartIndex());
            if (firstLine == null) {
                continue;
            }
            if (firstLine.isEmpty()) {
                return firstLine;
            }
            // A fault AT the resumed token means the statement did not take it after all.
            final int[] at = SyntaxErrorListener.sentenceCoordinates(firstLine);
            final int[] resumed = LeadingCommentOffset.rebase(resume.getLine(), resume.getCharPositionInLine());
            if (at == null || at[0] != resumed[0] || at[1] != resumed[1]) {
                return firstLine;
            }
        }
        return null;
    }

    /** Whether a statement's last token closes a table reference with no alias, which no name resumes. */
    private static boolean endsInUnaliasedTable(final FrostlakeParser.FlowChainContext chain) {
        ParseTree leaf = chain;
        while (leaf.getChildCount() > 0) {
            leaf = leaf.getChild(leaf.getChildCount() - 1);
        }
        for (ParseTree up = leaf.getParent(); up != null && up != chain; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                return false;
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) up;
                return reference.AS() == null && reference.nonJoinKeywordIdentifier() == null;
            }
        }
        return false;
    }

    /** Whether live's recovery can resume at a token: a word that can be a name, CASE too, but not BEGIN. */
    private static boolean isName(final Token token) {
        if (token.getType() == FrostlakeLexer.CASE) {
            return true;
        }
        return token.getType() != FrostlakeLexer.BEGIN && FrostlakeParser._ATN.nextTokens(
            FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    /**
     * The first line a text is refused with — its parse's own, or its first missing separator's. With a
     * resumption offset, null when the text's first statement does not reach that offset (the token there
     * was not taken), and the empty string when it does but the text has no fault.
     */
    static String firstFaultOf(final String text, final int resumedAt) {
        final SyntaxErrorListener listener = new SyntaxErrorListener(text, true);
        listener.statementParse();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(stream);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        if (resumedAt >= 0) {
            final List<FrostlakeParser.FlowChainContext> chains = script.flowChain();
            if (chains.isEmpty() || chains.get(0).getStop() == null
                    || chains.get(0).getStop().getStopIndex() < resumedAt) {
                return null;
            }
        }
        final String parseFault = listener.firstReportedLine();
        final MissingSeparator separator = firstFault(script, stream, text, false);
        if (separator == null) {
            return parseFault != null ? parseFault : resumedAt >= 0 ? "" : null;
        }
        final int[] parseFaultAt = parseFault == null ? null : SyntaxErrorListener.sentenceCoordinates(parseFault);
        return parseFault != null && !separator.precedes(parseFaultAt) ? parseFault : separator.firstLine();
    }

    /** The text with every character from {@code from} up to {@code to} blanked, and nothing kept past {@code keep}. */
    private static String blanked(final String sql, final int from, final int to, final int keep) {
        final StringBuilder text = new StringBuilder(sql.substring(0, Math.min(keep, sql.length())));
        for (int i = from; i < to && i < text.length(); i++) {
            if (text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                text.setCharAt(i, ' ');
            }
        }
        return text.toString();
    }

    /** The semicolon that ends the statement {@code word} stands in, or the end of the input. */
    private static Token statementEnd(final TokenStream tokens, final Token word) {
        Token token = word;
        while (token.getType() != FrostlakeLexer.SEMI && token.getType() != Token.EOF) {
            final Token next = nextSpoken(tokens, token.getTokenIndex());
            if (next == null) {
                return token;
            }
            token = next;
        }
        return token;
    }

    /** The next default-channel token after token index {@code after}, or null past the end. */
    private static Token nextSpoken(final TokenStream tokens, final int after) {
        for (int i = after + 1; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return tokens.get(i);
            }
        }
        return null;
    }

    /** Whether a semicolon sits between two consecutive statements — inside the first, or between. */
    private static boolean separated(final FrostlakeParser.FlowChainContext chain,
                                     final FrostlakeParser.FlowChainContext next,
                                     final TokenStream tokens) {
        final Token stop = chain.getStop();
        final Token start = next.getStart();
        if (stop == null || start == null) {
            return true;
        }
        if (stop.getType() == FrostlakeLexer.SEMI) {
            return true;
        }
        for (int i = stop.getTokenIndex() + 1; i < start.getTokenIndex(); i++) {
            if (tokens.get(i).getType() == FrostlakeLexer.SEMI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this statement is a bare {@code BEGIN} — the one spelling that opens a scripting block
     * as readily as it starts a transaction, and which live resolves as the block once anything
     * follows it. {@code BEGIN WORK}, {@code BEGIN TRANSACTION} and {@code BEGIN NAME t} are
     * transactions only, and get the ordinary missing-separator refusal.
     *
     * @param chain the statement
     * @return true when it is a bare BEGIN
     */
    private static boolean opensABlock(final FrostlakeParser.FlowChainContext chain) {
        final List<FrostlakeParser.StatementContext> statements = chain.statement();
        if (statements == null || statements.size() != 1) {
            return false;
        }
        final FrostlakeParser.TransactionStatementContext transaction =
            statements.get(0).transactionStatement();
        return transaction != null && transaction.BEGIN() != null && transaction.WORK() == null
            && transaction.TRANSACTION() == null && transaction.transactionName() == null;
    }

    private static MissingSeparator fault(final String token, final int line, final int position,
                                          final String secondLine) {
        final int[] shown = LeadingCommentOffset.rebase(line, position);
        final List<String> lines = new ArrayList<>();
        lines.add("syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token + "'.");
        if (secondLine != null) {
            lines.add(secondLine);
        }
        return new MissingSeparator(shown[0], shown[1], lines);
    }
}
