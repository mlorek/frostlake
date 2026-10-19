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

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A number or a string run in after a complete statement, met as a script's first fault: whether live reads the
 * statements after it, or reports nothing more. It depends on what the literal follows (all live-verified):
 *
 * <ul>
 *   <li>after a VALUE — an operand of a WHERE, a HAVING, a QUALIFY, a GROUP BY or ORDER BY item, a select item, an
 *       assigned value — the later statements are read: {@code SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y} names
 *       the 2 and the 'y'. An EXISTS is no value, and a literal after a join's ON condition is refused at the
 *       ON, the report ending there;</li>
 *   <li>after a FROM list's table, its alias, a derived table or a table function they are read too, but not after
 *       a SAMPLE or a USING list;</li>
 *   <li>after a DROP's name, a SHOW's one-word kind or the name its scope ends in, a DESCRIBE's name, an UNSET's
 *       name, BEGIN WORK or TRANSACTION and a plain CREATE DATABASE or SCHEMA's name they are read;</li>
 *   <li>a NUMBER alone is read past after a single unqualified name that ends a USE, a TRUNCATE, an UNDROP, a
 *       GRANT's or REVOKE's role or user, a transaction's NAME, an EXECUTE TASK and a DROP STAGE — {@code USE SCHEMA
 *       s 1} reads on where {@code USE SCHEMA s 'q'} and {@code USE SCHEMA s.t 1} do not — and, its line reported
 *       twice, after the name a CREATE ROLE, SEQUENCE or STAGE ends in, a CREATE TABLE's LIKE or CLONE source and
 *       an ALTER SESSION SET's value;</li>
 *   <li>everywhere else — after a CASCADE, a signature, a call's or a VALUES row's parenthesis, a LIMIT's count, a
 *       sort direction, COMMIT, an alias in a select list, a DELETE's target — the report ends at the literal. A
 *       number after a DELETE's target is reported twice.</li>
 * </ul>
 */
final class LiteralRunIn {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private static final LiteralRunIn READS_ON = new LiteralRunIn(true, false, null);

    private static final LiteralRunIn ENDS = new LiteralRunIn(false, false, null);

    private static final LiteralRunIn TWICE_READS_ON = new LiteralRunIn(true, true, null);

    private static final LiteralRunIn TWICE_ENDS = new LiteralRunIn(false, true, null);

    private final boolean readsOn;

    private final boolean twice;

    private final Token refusedInstead;

    private LiteralRunIn(final boolean readsOn, final boolean twice, final Token refusedInstead) {
        this.readsOn = readsOn;
        this.twice = twice;
        this.refusedInstead = refusedInstead;
    }

    /**
     * Whether a token is a literal judged here: a number or a string.
     *
     * @param token the token
     * @return whether it is one
     */
    static boolean isLiteral(final Token token) {
        return token.getType() == FrostlakeLexer.INTEGER_LITERAL || token.getType() == FrostlakeLexer.FLOAT_LITERAL
            || token.getType() == FrostlakeLexer.STRING_LITERAL
            || token.getType() == FrostlakeLexer.DOLLAR_QUOTED_STRING;
    }

    /**
     * How live reads on from a literal run in after a complete statement.
     *
     * @param parser  the parser that met the fault
     * @param literal the literal
     * @return the reading
     */
    static LiteralRunIn of(final Parser parser, final Token literal) {
        final FrostlakeParser.StatementContext run = UnseparatedStatementOpener.runOnStatement(parser, literal);
        final FrostlakeParser.StatementContext last = run != null ? run : statementBefore(parser, literal);
        if (last == null || last.getStart() == null) {
            return READS_ON;
        }
        return after(last, parser.getInputStream(), literal, literal.getType() == FrostlakeLexer.INTEGER_LITERAL
            || literal.getType() == FrostlakeLexer.FLOAT_LITERAL);
    }

    /**
     * Whether the later statements are read after a statement keyword run in straight after a complete statement:
     * where a string run in there would let the report go on.
     *
     * @param last    the complete statement
     * @param stream  the parsed tokens
     * @param keyword the keyword run in
     * @return whether live reads on
     */
    static boolean readsOnAfter(final FrostlakeParser.StatementContext last, final TokenStream stream,
                                final Token keyword) {
        return last.getStart() != null && after(last, stream, keyword, false).readsOn;
    }

    /** How live reads on from a token run in straight after {@code last}, judged as a number or as a string. */
    private static LiteralRunIn after(final FrostlakeParser.StatementContext last, final TokenStream stream,
                                      final Token literal, final boolean number) {
        final int opener = last.getStart().getType();
        final Token before = previous(stream, literal);
        if (opener == FrostlakeLexer.ALTER) {
            final Token assigned = before == null ? null : previous(stream, before);
            final Token session = next(stream, last.getStart());
            return number && assigned != null && assigned.getType() == FrostlakeLexer.EQ && session != null
                && session.getType() == FrostlakeLexer.SESSION ? TWICE_READS_ON : ENDS;
        }
        if (opener == FrostlakeLexer.COMMENT) {
            return ENDS;
        }
        final boolean query = endsInQuery(last);
        if (opener == FrostlakeLexer.CREATE && !query) {
            return afterCreate(stream, last, before, number);
        }
        final ParseTree leaf = lastToken(last);
        if (leaf == null) {
            return READS_ON;
        }
        ParserRuleContext value = null;
        for (ParseTree up = leaf.getParent(); up != null && up != last; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExistsExprContext && up == leaf.getParent()
                    || up instanceof FrostlakeParser.SampleClauseContext) {
                return ENDS;
            }
            if (up instanceof FrostlakeParser.JoinClauseContext) {
                final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) up;
                return value == null || join.ON() == null ? ENDS
                    : new LiteralRunIn(false, false, join.ON().getSymbol());
            }
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                return READS_ON;
            }
            if (up instanceof FrostlakeParser.ExpressionContext || up instanceof FrostlakeParser.BooleanExprContext) {
                value = (ParserRuleContext) up;
            } else if (value != null) {
                return READS_ON;
            }
        }
        if (value != null) {
            return READS_ON;
        }
        return afterCommand(stream, last, leaf, before, number);
    }

    /**
     * Whether a literal the parse met between two statements stands straight after a statement it read whole.
     *
     * @param parser  the parser that met the fault
     * @param literal the literal
     * @return whether a complete statement ends at the token before it
     */
    static boolean followsCompleteStatement(final Parser parser, final Token literal) {
        return statementBefore(parser, literal) != null;
    }

    /** The statement read whole whose last token stands straight before {@code literal}, or null. */
    private static FrostlakeParser.StatementContext statementBefore(final Parser parser, final Token literal) {
        final Token before = previous(parser.getInputStream(), literal);
        ParserRuleContext root = parser.getContext();
        while (root != null && root.getParent() != null) {
            root = root.getParent();
        }
        return before == null || root == null ? null : statementEndingAt(root, before.getTokenIndex());
    }

    private static FrostlakeParser.StatementContext statementEndingAt(final ParseTree tree, final int stop) {
        if (tree instanceof FrostlakeParser.StatementContext) {
            final FrostlakeParser.StatementContext statement = (FrostlakeParser.StatementContext) tree;
            return statement.exception == null && statement.getStop() != null
                && statement.getStop().getTokenIndex() == stop && isClean(statement) ? statement : null;
        }
        for (int i = tree.getChildCount() - 1; i >= 0; i--) {
            final FrostlakeParser.StatementContext found = statementEndingAt(tree.getChild(i), stop);
            if (found != null) {
                return found;
            }
        }
        return null;
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
     * Whether the statements after the literal are read.
     *
     * @return whether live reads on
     */
    boolean readsOn() {
        return readsOn;
    }

    /**
     * Whether the literal's line is reported twice.
     *
     * @return whether the line repeats
     */
    boolean isTwice() {
        return twice;
    }

    /**
     * The token live refuses in the literal's place — the ON of the join condition it follows — or null.
     *
     * @return the token, or null
     */
    Token refusedInstead() {
        return refusedInstead;
    }

    /** A literal after a CREATE that ends in no query. */
    private static LiteralRunIn afterCreate(final TokenStream stream, final FrostlakeParser.StatementContext last,
                                            final Token before, final boolean number) {
        final Token keyword = beforeName(stream, before);
        if (keyword == null) {
            return ENDS;
        }
        final Token kind = next(stream, last.getStart());
        final int type = keyword.getType();
        if (type == FrostlakeLexer.DATABASE || type == FrostlakeLexer.SCHEMA) {
            return keyword == kind ? READS_ON : ENDS;
        }
        if (type == FrostlakeLexer.ROLE || type == FrostlakeLexer.SEQUENCE || type == FrostlakeLexer.STAGE
                || type == FrostlakeLexer.LIKE || type == FrostlakeLexer.CLONE) {
            return number ? TWICE_READS_ON : ENDS;
        }
        return ENDS;
    }

    /** A literal after a statement that ends in no value and no table reference, by the statement's opener. */
    private static LiteralRunIn afterCommand(final TokenStream stream, final FrostlakeParser.StatementContext last,
                                             final ParseTree leaf, final Token before, final boolean number) {
        final Token opener = last.getStart();
        final Token second = next(stream, opener);
        final Token keyword = beforeName(stream, before);
        final boolean named = isNamed(leaf, last);
        final Token qualifier = before == null ? null : previous(stream, before);
        final boolean single = named && qualifier != null && qualifier.getType() != FrostlakeLexer.DOT;
        final int key = keyword == null ? Token.EOF : keyword.getType();
        switch (opener.getType()) {
            case FrostlakeLexer.DROP:
                if (second != null && second.getType() == FrostlakeLexer.STAGE) {
                    return number && single ? READS_ON : ENDS;
                }
                return named ? READS_ON : ENDS;
            case FrostlakeLexer.SHOW: {
                final Token kind = second != null && second.getType() == FrostlakeLexer.TERSE
                    ? next(stream, second) : second;
                if (before == kind) {
                    return kind.getType() != FrostlakeLexer.IDENTIFIER
                        && kind.getType() != FrostlakeLexer.QUOTED_IDENTIFIER ? READS_ON : ENDS;
                }
                return named ? READS_ON : ENDS;
            }
            case FrostlakeLexer.DESCRIBE:
            case FrostlakeLexer.DESC:
                return named ? READS_ON : ENDS;
            case FrostlakeLexer.UNSET:
                return before != null && previous(stream, before) == opener ? READS_ON : ENDS;
            case FrostlakeLexer.BEGIN:
                if (before == second && (second.getType() == FrostlakeLexer.WORK
                        || second.getType() == FrostlakeLexer.TRANSACTION)) {
                    return READS_ON;
                }
                return number && single && key == FrostlakeLexer.NAME ? READS_ON : ENDS;
            case FrostlakeLexer.START:
                return number && single && key == FrostlakeLexer.NAME ? READS_ON : ENDS;
            case FrostlakeLexer.USE:
            case FrostlakeLexer.TRUNCATE:
            case FrostlakeLexer.UNDROP:
                return number && single ? READS_ON : ENDS;
            case FrostlakeLexer.GRANT:
            case FrostlakeLexer.REVOKE:
                return number && single && (key == FrostlakeLexer.ROLE || key == FrostlakeLexer.USER)
                    ? READS_ON : ENDS;
            case FrostlakeLexer.EXECUTE:
                return number && single && key == FrostlakeLexer.TASK ? READS_ON : ENDS;
            case FrostlakeLexer.DELETE:
                return number && before != null && key == FrostlakeLexer.FROM ? TWICE_ENDS : ENDS;
            default:
                return ENDS;
        }
    }

    /**
     * The token before the name {@code last} ends — every part of a dotted name and an IF [NOT] EXISTS before it
     * passed over — or null when {@code last} is no name's last word.
     */
    private static Token beforeName(final TokenStream stream, final Token last) {
        if (last == null || !isName(last)) {
            return null;
        }
        Token token = previous(stream, last);
        while (token != null && token.getType() == FrostlakeLexer.DOT) {
            final Token part = previous(stream, token);
            if (part == null || !isName(part)) {
                return null;
            }
            token = previous(stream, part);
        }
        if (token != null && token.getType() == FrostlakeLexer.EXISTS) {
            token = previous(stream, token);
            if (token != null && token.getType() == FrostlakeLexer.NOT) {
                token = previous(stream, token);
            }
            token = token != null && token.getType() == FrostlakeLexer.IF ? previous(stream, token) : null;
        }
        return token;
    }

    /** The node of a tree's last token, any rule that read nothing passed over, or null for a tree with none. */
    private static ParseTree lastToken(final ParseTree tree) {
        if (tree instanceof TerminalNode) {
            return tree;
        }
        for (int i = tree.getChildCount() - 1; i >= 0; i--) {
            final ParseTree found = lastToken(tree.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether a statement's last token was read as a name: an identifier, not a keyword of the statement. */
    private static boolean isNamed(final ParseTree leaf, final ParseTree statement) {
        for (ParseTree up = leaf.getParent(); up != null && up != statement; up = up.getParent()) {
            if (up instanceof FrostlakeParser.IdentifierContext || up instanceof FrostlakeParser.ObjectNameContext
                    || up instanceof FrostlakeParser.QualifiedNameContext
                    || up instanceof FrostlakeParser.ShowScopeNameContext) {
                return true;
            }
        }
        return false;
    }

    /** Whether a statement's last construct is a query, as a CREATE … AS query's is. */
    private static boolean endsInQuery(final ParseTree statement) {
        for (ParseTree node = statement; node != null && node.getChildCount() > 0;
                node = node.getChild(node.getChildCount() - 1)) {
            if (node instanceof FrostlakeParser.SelectStatementContext) {
                return true;
            }
        }
        return false;
    }

    private static boolean isName(final Token token) {
        return WORDS.contains(token.getType()) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    private static Token previous(final TokenStream stream, final Token token) {
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    private static Token next(final TokenStream stream, final Token token) {
        for (int i = token.getTokenIndex() + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
