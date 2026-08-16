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
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

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
        if (script == null || tokens == null) {
            return;
        }
        final List<FrostlakeParser.FlowChainContext> chains = script.flowChain();
        for (int i = 0; i + 1 < chains.size(); i++) {
            final FrostlakeParser.FlowChainContext chain = chains.get(i);
            final FrostlakeParser.FlowChainContext next = chains.get(i + 1);
            if (separated(chain, next, tokens)) {
                continue;
            }
            if (opensABlock(chain)) {
                refuse("<EOF>", EndOfInput.line(sql), EndOfInput.position(sql), sql);
            }
            refuse(next.getStart().getText(), next.getStart().getLine(),
                next.getStart().getCharPositionInLine(), sql);
        }
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

    private static void refuse(final String token, final int line, final int position,
                               final String sql) {
        final int[] shown = LeadingCommentOffset.rebase(line, position);
        final String detail = "syntax error line " + shown[0] + " at position " + shown[1]
            + " unexpected '" + token + "'.";
        final List<String> errors = new ArrayList<>();
        errors.add(detail);
        throw new SqlSyntaxException(SqlCompilationError.of(detail), errors, sql);
    }
}
