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
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The syntax errors live raises in a SHOW listing's modifiers, which the grammar parses leniently so the
 * refusal can land where live's parser stops (all live-verified):
 *
 * <pre>
 *   SHOW TABLES LIMIT                          unexpected '&lt;EOF&gt;' at 17   the count is missing
 *   SHOW TABLES STARTS                         unexpected '&lt;EOF&gt;' at 18   so is the WITH, then the prefix
 *   SHOW TABLES WITH                           unexpected '&lt;EOF&gt;' at 16   the PRIVILEGES, then a privilege
 *   SHOW TABLES WITH x                         unexpected 'x' at 17
 *   SHOW PRIMARY KEYS LIMIT 1                  unexpected 'LIMIT' at 18   the listing reads no LIMIT
 *   SHOW LOCKS STARTS WITH 'A'                 unexpected 'STARTS' at 11, then ''A'' at 23
 *   SHOW TASKS WITH PRIVILEGES USAGE           unexpected 'PRIVILEGES' at 16
 * </pre>
 *
 * <p>A modifier the listing's grammar reads and leaves unfinished is refused at the token after the word written
 * last; one the grammar does not read is refused at its own first word — except a WITH, which live reads on as
 * the start of what follows and refuses at the word after it. Being syntax errors, these precede every other
 * refusal of the statement.
 *
 * <p>A WITH PRIVILEGES a listing reads but does not filter by is refused as an unsupported feature instead,
 * once the scope's shape has been judged and before the scope is looked up:
 * {@code Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.}, naming the listing as live does internally.
 */
final class ShowTailSyntax {

    private ShowTailSyntax() {
    }

    /**
     * Refuse the listing's modifiers where live's parser would, or do nothing.
     *
     * @param ctx     the statement
     * @param listing its listing
     */
    static void requireParsed(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail == null) {
            return;
        }
        final ShowTailGrammar grammar = listing.tail();
        if (listing.isAccountLevel() && grammar == ShowTailGrammar.NONE && ctx.ACCOUNT() != null
                && ctx.objectName() == null && tail.getChildCount() > 1) {
            // LOCKS and TRANSACTIONS name an account after IN ACCOUNT, and live reads a LIMIT or STARTS written
            // there as that name: the fault is the word after it, or the one after a WITH that follows it.
            final Token named = terminal(tail, 0);
            if (isToken(named, FrostlakeLexer.LIMIT) || isToken(named, FrostlakeLexer.STARTS)) {
                throw refused(lineAfter(ctx, tail, isToken(terminal(tail, 1), FrostlakeLexer.WITH) ? 1 : 0));
            }
        }
        for (int i = 0; i < tail.getChildCount(); i++) {
            final Token word = terminal(tail, i);
            if (word == null) {
                continue;
            }
            if (word.getType() == FrostlakeLexer.STARTS) {
                if (!grammar.readsStartsWith()) {
                    throw refused(startsWithRefusal(tail, i, listing));
                }
                if (!isToken(terminal(tail, i + 1), FrostlakeLexer.WITH)) {
                    throw refused(lineAfter(ctx, tail, i));
                }
                if (!isToken(terminal(tail, i + 2), FrostlakeLexer.STRING_LITERAL)) {
                    throw refused(lineAfter(ctx, tail, i + 1));
                }
                i += 2;
            } else if (word.getType() == FrostlakeLexer.ROOT) {
                // ROOT ONLY narrows a task listing to its root tasks; no other listing reads it.
                if (ctx.TASKS() == null) {
                    throw refused(lineAt(word));
                }
                i++;
            } else if (word.getType() == FrostlakeLexer.LIMIT) {
                if (!grammar.readsLimit()) {
                    throw refused(lineAt(word));
                }
                if (!isToken(terminal(tail, i + 1), FrostlakeLexer.INTEGER_LITERAL)) {
                    throw refused(lineAfter(ctx, tail, i));
                }
            } else if (word.getType() == FrostlakeLexer.WITH) {
                if (!grammar.readsPrivileges() || !isToken(terminal(tail, i + 1), FrostlakeLexer.PRIVILEGES)) {
                    throw refused(lineAfter(ctx, tail, i));
                }
                if (i + 2 >= tail.getChildCount()) {
                    throw refused(lineAfter(ctx, tail, i + 1));
                }
                return;
            }
        }
    }

    /**
     * Refuse the modifiers of {@code SHOW VERSIONS IN NOTEBOOK | STREAMLIT}, which reads a LIMIT and its count and
     * nothing else: {@code … LIMIT 1 FROM 'x'} is refused at the FROM, {@code … STARTS WITH 'x'} at the STARTS and a
     * LIMIT without its count at the token after it (live-verified).
     *
     * @param ctx the statement
     */
    static void requireCountOnly(final FrostlakeParser.ShowStatementContext ctx) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail == null) {
            return;
        }
        for (int i = 0; i < tail.getChildCount(); i++) {
            final Token word = startOf(tail.getChild(i));
            if (word.getType() != FrostlakeLexer.LIMIT) {
                throw refused(lineAt(word));
            }
            if (!isToken(terminal(tail, i + 1), FrostlakeLexer.INTEGER_LITERAL)) {
                throw refused(lineAfter(ctx, tail, i));
            }
            i++;
        }
    }

    /**
     * Refuse a WITH PRIVILEGES the listing reads but does not filter by, or do nothing.
     *
     * @param ctx     the statement
     * @param listing its listing
     */
    static void refuseUnfilteredPrivileges(final FrostlakeParser.ShowStatementContext ctx,
                                           final ShowListing listing) {
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail == null || tail.PRIVILEGES() == null || listing.featureName() == null) {
            return;
        }
        if (listing.filtersByPrivileges() && ctx.HISTORY() == null) {
            return;
        }
        final String named = ctx.HISTORY() != null ? listing.featureName() + " HISTORY" : listing.featureName();
        throw new RuntimeException("Unsupported feature 'SHOW " + named + " ... WITH PRIVILEGES <>'.");
    }

    /** A STARTS the listing does not read: its own line, and a second at the prefix where live stacks one. */
    private static String startsWithRefusal(final FrostlakeParser.ShowTailContext tail, final int index,
                                            final ShowListing listing) {
        final String first = lineAt(terminal(tail, index));
        final Token prefix = terminal(tail, index + 2);
        if (listing.startsWithStacksItsPrefix() && isToken(terminal(tail, index + 1), FrostlakeLexer.WITH)
                && isToken(prefix, FrostlakeLexer.STRING_LITERAL)) {
            return first + "\n" + lineAt(prefix);
        }
        return first;
    }

    /** The line at the token after the tail's child at {@code index}: its next child, the semicolon, or the end. */
    private static String lineAfter(final FrostlakeParser.ShowStatementContext ctx,
                                    final FrostlakeParser.ShowTailContext tail, final int index) {
        if (index + 1 < tail.getChildCount()) {
            return lineAt(startOf(tail.getChild(index + 1)));
        }
        final int at = ctx.children.indexOf(tail);
        if (at >= 0 && at + 1 < ctx.getChildCount()) {
            return lineAt(startOf(ctx.getChild(at + 1)));
        }
        final Token last = ctx.getStop();
        final int[] shown = LeadingCommentOffset.rebase(last.getLine(),
            last.getCharPositionInLine() + last.getText().length());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.";
    }

    private static String lineAt(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token.getText() + "'.";
    }

    private static RuntimeException refused(final String lines) {
        return new RuntimeException(SqlCompilationError.of(lines));
    }

    /** The tail's child at {@code index} when it is a token, or null. */
    private static Token terminal(final FrostlakeParser.ShowTailContext tail, final int index) {
        if (index >= tail.getChildCount() || !(tail.getChild(index) instanceof TerminalNode)) {
            return null;
        }
        return ((TerminalNode) tail.getChild(index)).getSymbol();
    }

    private static boolean isToken(final Token token, final int type) {
        return token != null && token.getType() == type;
    }

    private static Token startOf(final ParseTree tree) {
        return tree instanceof TerminalNode ? ((TerminalNode) tree).getSymbol() : ((ParserRuleContext) tree).getStart();
    }
}
