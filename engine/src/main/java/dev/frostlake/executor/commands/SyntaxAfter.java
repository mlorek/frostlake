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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The syntax error of a clause whose word was read and whose rest is missing: the account names the token after
 * the word, or the end of the statement, where a parser asked to read both at once would name the word itself. The
 * grammar therefore reads the word alone and the handler refuses here, at what follows it.
 */
final class SyntaxAfter {

    private SyntaxAfter() {
    }

    /**
     * The refusal naming what follows a parsed clause.
     *
     * @param clause the clause that is missing its rest
     * @return the syntax error at the next token, or at the end of the statement
     */
    static RuntimeException unexpected(final ParserRuleContext clause) {
        return unexpected(clause, clause.getStop());
    }

    /**
     * The refusal naming what follows a word of the statement.
     *
     * @param word the word that is missing what it introduces
     * @return the syntax error at the next token, or at the end of the statement
     */
    static RuntimeException unexpected(final TerminalNode word) {
        return unexpected(word, word.getSymbol());
    }

    private static RuntimeException unexpected(final ParseTree clause, final Token last) {
        final Token next = following(clause);
        if (next != null && next.getType() != Token.EOF) {
            final int[] shown = LeadingCommentOffset.rebase(next.getLine(), next.getCharPositionInLine());
            return new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
                + shown[1] + " unexpected '" + next.getText() + "'."));
        }
        final int[] shown = LeadingCommentOffset.rebase(last.getLine(),
            last.getCharPositionInLine() + last.getText().length());
        return new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
            + shown[1] + " unexpected '<EOF>'."));
    }

    /** The first token after a node, found among the later children of its ancestors; null at the end. */
    private static Token following(final ParseTree clause) {
        ParseTree node = clause;
        while (node.getParent() != null) {
            final ParseTree parent = node.getParent();
            boolean passed = false;
            for (int i = 0; i < parent.getChildCount(); i++) {
                final ParseTree child = parent.getChild(i);
                if (passed) {
                    final Token first = firstToken(child);
                    if (first != null) {
                        return first;
                    }
                } else if (child == node) {
                    passed = true;
                }
            }
            node = parent;
        }
        return null;
    }

    private static Token firstToken(final ParseTree node) {
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol();
        }
        return node instanceof ParserRuleContext ? ((ParserRuleContext) node).getStart() : null;
    }
}
