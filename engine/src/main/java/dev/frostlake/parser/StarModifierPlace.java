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

import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Where a star argument (or an object star's star) followed by a misplaced RENAME or REPLACE stands, read off the tree
 * of the text before the keyword: which of live's readings applies ({@link StarModifierPlaceKind}), and the brackets
 * each reading closes.
 *
 * <p>The query holding the item or clause is a statement of its own — an INSERT's query, a set operation's operand, an
 * UPDATE's or DELETE's WHERE included — or, for an item, the query of a derived table or a CTE of such a statement.
 */
final class StarModifierPlace {

    private final StarModifierPlaceKind kind;
    private final Token itemStart;
    private final Token queryOpener;
    private final boolean cte;
    private final Token enclosingOpener;

    private StarModifierPlace(final StarModifierPlaceKind kind, final Token itemStart, final Token queryOpener,
                              final boolean cte, final Token enclosingOpener) {
        this.kind = kind;
        this.itemStart = itemStart;
        this.queryOpener = queryOpener;
        this.cte = cte;
        this.enclosingOpener = enclosingOpener;
    }

    /** Which reading applies. */
    StarModifierPlaceKind kind() {
        return kind;
    }

    /** The select item's first token, for an item and under another call; null for a clause. */
    Token itemStart() {
        return itemStart;
    }

    /** The '(' that opens the derived table's or the CTE's query holding the item, or null for a statement's own. */
    Token queryOpener() {
        return queryOpener;
    }

    /** Whether the query holding the item is a CTE's; false for a derived table's and a statement's own. */
    boolean inCte() {
        return cte;
    }

    /** Under another call: the '(' of the innermost call or CAST around the star's own call; null otherwise. */
    Token enclosingOpener() {
        return enclosingOpener;
    }

    /**
     * The place of the star at {@code star} in {@code tree}, or null when it stands where no reading is known: in a
     * subquery, a join condition, a window or anywhere else.
     *
     * @param tree the parse of the text before the keyword, its brackets closed
     * @param star the star token of the original text
     * @return the place, or null
     */
    static StarModifierPlace of(final FrostlakeParser.SqlScriptContext tree, final Token star) {
        final TerminalNode node = starAt(tree, star.getStartIndex());
        if (node == null) {
            return null;
        }
        ParserRuleContext own = null;
        ParserRuleContext enclosing = null;
        for (ParserRuleContext up = (ParserRuleContext) node.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ObjectStarItemContext) {
                return item(up, StarModifierPlaceKind.ITEM, null);
            }
            if (up instanceof FrostlakeParser.ExprItemContext) {
                return own == null ? null
                    : item(up, enclosing == null ? StarModifierPlaceKind.ITEM : StarModifierPlaceKind.NESTED, enclosing);
            }
            if (up instanceof FrostlakeParser.SelectStatementContext
                    || up instanceof FrostlakeParser.OverClauseContext
                    || up instanceof FrostlakeParser.WithinGroupClauseContext) {
                return null;
            }
            if (isClause(up)) {
                return own == null || !statementClause(up) ? null
                    : new StarModifierPlace(StarModifierPlaceKind.CLAUSE, null, null, false, null);
            }
            if (!(up instanceof FrostlakeParser.ParenExprContext) && opensABracket(up)) {
                if (own == null) {
                    own = up;
                } else if (isCallOrCast(up)) {
                    if (enclosing == null) {
                        enclosing = up;
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    /** The place of a select item, or null when its query is none this reading knows. */
    private static StarModifierPlace item(final ParserRuleContext item, final StarModifierPlaceKind kind,
                                          final ParserRuleContext enclosing) {
        final FrostlakeParser.SelectStatementContext query = queryOf(item);
        if (query == null) {
            return null;
        }
        final Token enclosingOpener = enclosing == null ? null : openingParen(enclosing);
        if (statementQuery(query)) {
            return new StarModifierPlace(kind, item.getStart(), null, false, enclosingOpener);
        }
        final Token queryOpener = enclosedQueryOpener(query);
        return queryOpener == null ? null
            : new StarModifierPlace(kind, item.getStart(), queryOpener, cteQuery(query), enclosingOpener);
    }

    /**
     * The '(' that opens a query which is a derived table's or a CTE's of a statement's own query, or null for any
     * other query.
     */
    static Token enclosedQueryOpener(final FrostlakeParser.SelectStatementContext query) {
        final ParserRuleContext holder = query.getParent();
        final boolean derived = holder instanceof FrostlakeParser.TableSourceContext
            && statementQuery(queryOf(holder));
        return derived || cteQuery(query) ? openingParen(holder) : null;
    }

    /** Whether a query is a CTE's of a statement's own query. */
    static boolean cteQuery(final FrostlakeParser.SelectStatementContext query) {
        final ParserRuleContext holder = query.getParent();
        return holder instanceof FrostlakeParser.CteDefinitionContext
            && holder.getParent() instanceof FrostlakeParser.WithClauseContext
            && holder.getParent().getParent() instanceof FrostlakeParser.SelectStatementContext
            && statementQuery((FrostlakeParser.SelectStatementContext) holder.getParent().getParent());
    }

    /** Whether a context is one of the query clauses a star call is read in. */
    private static boolean isClause(final ParserRuleContext context) {
        return context instanceof FrostlakeParser.WhereClauseContext
            || context instanceof FrostlakeParser.GroupByClauseContext
            || context instanceof FrostlakeParser.HavingClauseContext
            || context instanceof FrostlakeParser.QualifyClauseContext
            || context instanceof FrostlakeParser.OrderByClauseContext;
    }

    /** Whether a clause belongs to a query or an UPDATE or DELETE that is a statement of its own. */
    private static boolean statementClause(final ParserRuleContext clause) {
        final ParserRuleContext owner = clause.getParent();
        if (owner instanceof FrostlakeParser.UpdateStatementContext
                || owner instanceof FrostlakeParser.DeleteStatementContext) {
            for (ParserRuleContext up = owner; up != null; up = up.getParent()) {
                if (up instanceof FrostlakeParser.StatementListContext) {
                    return false;
                }
            }
            return true;
        }
        if (clause instanceof FrostlakeParser.OrderByClauseContext) {
            return owner instanceof FrostlakeParser.SelectStatementContext
                && statementQuery((FrostlakeParser.SelectStatementContext) owner);
        }
        return owner instanceof FrostlakeParser.SelectClauseContext && statementQuery(queryOf(owner));
    }

    /** Whether a query is nested in no other query and stands in no block. */
    static boolean statementQuery(final FrostlakeParser.SelectStatementContext query) {
        if (query == null) {
            return false;
        }
        for (ParserRuleContext up = query.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.SelectStatementContext
                    || up instanceof FrostlakeParser.StatementListContext) {
                return false;
            }
        }
        return true;
    }

    /** The nearest query enclosing {@code context}, or null. */
    static FrostlakeParser.SelectStatementContext queryOf(final ParserRuleContext context) {
        for (ParserRuleContext up = context.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.SelectStatementContext) {
                return (FrostlakeParser.SelectStatementContext) up;
            }
        }
        return null;
    }

    /** Whether a context is a call with an argument list or a CAST. */
    private static boolean isCallOrCast(final ParserRuleContext context) {
        return context instanceof FrostlakeParser.FunctionCallExprContext
            || context instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            || context instanceof FrostlakeParser.CastExprContext
            || context instanceof FrostlakeParser.TryCastExprContext;
    }

    /** Whether a context opens a bracket of its own: a call's parentheses, a CAST's, or any other construct's. */
    private static boolean opensABracket(final ParserRuleContext context) {
        return openingParen(context) != null;
    }

    /** The first '(' among a context's own tokens, or null. */
    private static Token openingParen(final ParserRuleContext context) {
        for (int i = 0; i < context.getChildCount(); i++) {
            final ParseTree child = context.getChild(i);
            if (child instanceof TerminalNode && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.LPAREN) {
                return ((TerminalNode) child).getSymbol();
            }
        }
        return null;
    }

    /** The STAR terminal of the tree that starts at character {@code start}, or null. */
    private static TerminalNode starAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof TerminalNode) {
                final Token symbol = ((TerminalNode) node).getSymbol();
                if (symbol.getType() == FrostlakeLexer.STAR && symbol.getStartIndex() == start) {
                    return (TerminalNode) node;
                }
                continue;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return null;
    }
}
