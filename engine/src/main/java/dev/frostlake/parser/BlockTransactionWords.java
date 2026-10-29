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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A statement of a block opens a transaction with BEGIN TRANSACTION or START TRANSACTION alone: there any other BEGIN
 * opens a nested block, so BEGIN WORK, BEGIN NAME and a bare BEGIN are refused at the token after the BEGIN, where a
 * request outside every block starts a transaction with each (all live-verified):
 *
 * <pre>
 *   BEGIN BEGIN WORK; ROLLBACK; END       'WORK' at 12
 *   BEGIN BEGIN NAME t1; ROLLBACK; END    'NAME' at 12
 *   BEGIN BEGIN; END                      ';' at 11
 * </pre>
 *
 * <p>The account stacks further lines for some of these, its recovery reading on in the nested block; they are not
 * given here.
 */
public final class BlockTransactionWords {

    private BlockTransactionWords() {
    }

    /**
     * Refuse the first such BEGIN, in the order written.
     *
     * @param script the parsed script
     * @param sql    the script's source text
     */
    public static void require(final FrostlakeParser.SqlScriptContext script, final String sql) {
        if (script == null) {
            return;
        }
        Token first = null;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(script);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.TransactionStatementContext) {
                final Token refused = refusedWord((FrostlakeParser.TransactionStatementContext) node);
                if (refused != null && (first == null || refused.getTokenIndex() < first.getTokenIndex())) {
                    first = refused;
                }
                continue;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        if (first != null) {
            final List<String> lines = new ArrayList<>();
            lines.add(sentence(first));
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
    }

    /** The token after a BEGIN that opens no transaction where the statement stands, or null. */
    private static Token refusedWord(final FrostlakeParser.TransactionStatementContext statement) {
        if (statement.getStart().getType() != FrostlakeParser.BEGIN || statement.TRANSACTION() != null
                || statement.getChildCount() < 2) {
            return null;
        }
        // A statement of a block's own list: a RESULTSET's parenthesized statement is read as a request's is.
        final ParserRuleContext standing = statement.getParent();
        if (!(standing instanceof FrostlakeParser.StatementContext)
                || !(standing.getParent() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final ParseTree after = statement.getChild(1);
        if (after instanceof TerminalNode) {
            final Token word = ((TerminalNode) after).getSymbol();
            return word.getType() == Token.EOF ? null : word;
        }
        return after instanceof ParserRuleContext ? ((ParserRuleContext) after).getStart() : null;
    }

    private static String sentence(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token.getText() + "'.";
    }
}
