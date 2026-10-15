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
     * subquery that is compared or listed, a join condition, a CASE's condition inside a select item, or anywhere
     * else.
     *
     * <p>A clause is read through what stands one bracket deeper in it — ROLLUP, CUBE and GROUPING SETS, and the
     * keys of a window — and the clause of a CTE's query or the WHERE of an EXISTS in a statement's own WHERE is
     * read as a statement's is. A window's keys in a select item are read as the item is.
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
        ParserRuleContext windowed = null;
        ParserRuleContext below = null;
        boolean window = false;
        boolean condition = false;
        for (ParserRuleContext up = (ParserRuleContext) node.getParent(); up != null; below = up, up = up.getParent()) {
            if (up instanceof FrostlakeParser.ObjectStarItemContext) {
                return item(up, StarModifierPlaceKind.ITEM, null);
            }
            if (up instanceof FrostlakeParser.ExprItemContext) {
                if (own == null || condition) {
                    return null;
                }
                if (window) {
                    // A window's keys are read as the item is, in a statement's own query.
                    return enclosing == null && statementQuery(queryOf(up))
                        ? new StarModifierPlace(StarModifierPlaceKind.ITEM, up.getStart(), null, false, null) : null;
                }
                return item(up, enclosing == null ? StarModifierPlaceKind.ITEM : StarModifierPlaceKind.NESTED, enclosing);
            }
            if (up instanceof FrostlakeParser.SelectStatementContext
                    || up instanceof FrostlakeParser.WithinGroupClauseContext) {
                return null;
            }
            if (up instanceof FrostlakeParser.OverClauseContext) {
                if (own == null || enclosing != null || window) {
                    return null;
                }
                window = true;
                windowed = up.getParent();
                continue;
            }
            if (up == windowed || up.getParent() instanceof FrostlakeParser.OverClauseContext || isGrouping(up)) {
                // The windowed call's own brackets, a window's key lists and a super-group's are not around the star.
                continue;
            }
            if (isClause(up)) {
                if (own == null) {
                    return null;
                }
                if (statementClause(up)) {
                    return new StarModifierPlace(StarModifierPlaceKind.CLAUSE, null, null, false, null);
                }
                return window ? null : enclosedClause(up);
            }
            if (isMergeCondition(up)) {
                // A MERGE's ON is read as a clause is.
                return own == null || window || inBlock(up) ? null
                    : new StarModifierPlace(StarModifierPlaceKind.CLAUSE, null, null, false, null);
            }
            if (up instanceof FrostlakeParser.WhenClauseContext
                    && below == ((FrostlakeParser.WhenClauseContext) up).booleanExpr(0)
                    || up instanceof FrostlakeParser.SimpleCaseExprContext
                    && below == ((FrostlakeParser.SimpleCaseExprContext) up).expression()) {
                condition = true;
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

    /**
     * The place of a clause that is not a statement's own: a CTE's clause and the WHERE of an EXISTS that stands in a
     * statement's own WHERE are read as a statement's clause is, a derived table's clause has a reading of its own,
     * and any other query's has none known.
     */
    private static StarModifierPlace enclosedClause(final ParserRuleContext clause) {
        final FrostlakeParser.SelectStatementContext query = queryOf(clause);
        if (query == null) {
            return null;
        }
        if (cteQuery(query) || clause instanceof FrostlakeParser.WhereClauseContext && existsInStatementWhere(query)) {
            return new StarModifierPlace(StarModifierPlaceKind.CLAUSE, null, null, false, null);
        }
        return enclosedQueryOpener(query) == null ? null
            : new StarModifierPlace(StarModifierPlaceKind.DERIVED_CLAUSE, null, null, false, null);
    }

    /** Whether a query is the subquery of an EXISTS standing, under no bracket, in a statement's own WHERE. */
    private static boolean existsInStatementWhere(final FrostlakeParser.SelectStatementContext query) {
        if (!(query.getParent() instanceof FrostlakeParser.ExistsExprContext)) {
            return false;
        }
        for (ParserRuleContext up = query.getParent().getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.WhereClauseContext) {
                return statementClause(up);
            }
            if (up instanceof FrostlakeParser.SelectStatementContext || opensABracket(up)) {
                return false;
            }
        }
        return false;
    }

    /** Whether a context is a super-group of a GROUP BY, a list of one, or a grouping set. */
    private static boolean isGrouping(final ParserRuleContext context) {
        return context instanceof FrostlakeParser.GroupByElementContext
            || context instanceof FrostlakeParser.GroupByColumnListContext
            || context instanceof FrostlakeParser.GroupingSetListContext
            || context instanceof FrostlakeParser.GroupingSetContext;
    }

    /**
     * The ON of the join whose condition holds the star at {@code star}, when the join is one of a statement's own
     * query; else null. A star call's misplaced modifier or word there is refused at that ON, and nothing after it
     * is read (live-verified).
     *
     * @param tree the parse of the text before the fault, its brackets closed
     * @param star the star token of the original text
     * @return the join's ON, or null
     */
    static Token joinOn(final FrostlakeParser.SqlScriptContext tree, final Token star) {
        final TerminalNode node = starAt(tree, star.getStartIndex());
        for (ParserRuleContext up = node == null ? null : (ParserRuleContext) node.getParent(); up != null;
                up = up.getParent()) {
            if (up instanceof FrostlakeParser.SelectStatementContext) {
                return null;
            }
            if (up instanceof FrostlakeParser.JoinClauseContext) {
                final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) up;
                return join.ON() != null && statementQuery(queryOf(join)) ? join.ON().getSymbol() : null;
            }
        }
        return null;
    }

    /**
     * For a star whose call is the select item of a subquery that stands in a select item of a statement's own
     * query: the subquery's SELECT and the '(' of the star's call, the two places live refuses such a subquery at
     * (live-verified); else null.
     *
     * @param tree the parse of the text before the fault, its brackets closed
     * @param star the star token of the original text
     * @return the subquery's SELECT and the call's opening parenthesis, or null
     */
    static Token[] scalarSubqueryItem(final FrostlakeParser.SqlScriptContext tree, final Token star) {
        final TerminalNode node = starAt(tree, star.getStartIndex());
        ParserRuleContext own = null;
        FrostlakeParser.SelectStatementContext subquery = null;
        for (ParserRuleContext up = node == null ? null : (ParserRuleContext) node.getParent(); up != null;
                up = up.getParent()) {
            if (subquery == null) {
                if (up instanceof FrostlakeParser.SelectStatementContext) {
                    subquery = (FrostlakeParser.SelectStatementContext) up;
                } else if (up instanceof FrostlakeParser.ExprItemContext) {
                    if (own == null) {
                        return null;
                    }
                } else if (isClause(up) || up instanceof FrostlakeParser.OverClauseContext) {
                    return null;
                } else if (!(up instanceof FrostlakeParser.ParenExprContext) && opensABracket(up)) {
                    if (own != null) {
                        return null;
                    }
                    own = up;
                }
            } else if (up instanceof FrostlakeParser.SelectStatementContext || isClause(up)) {
                return null;
            } else if (up instanceof FrostlakeParser.ExprItemContext) {
                final Token select = subquery.getStart();
                return own != null && select.getType() == FrostlakeLexer.SELECT && statementQuery(queryOf(up))
                    ? new Token[] {select, openingParen(own)} : null;
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

    /** Whether a context is the condition a MERGE joins its source on. */
    private static boolean isMergeCondition(final ParserRuleContext context) {
        return context.getParent() instanceof FrostlakeParser.MergeStatementContext
            && context == ((FrostlakeParser.MergeStatementContext) context.getParent()).booleanExpr();
    }

    /** Whether a context stands in a block's statement list. */
    private static boolean inBlock(final ParserRuleContext context) {
        for (ParserRuleContext up = context; up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return true;
            }
        }
        return false;
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

    /**
     * Whether a context opens a bracket of its own: a call's parentheses, a CAST's, any other construct's, or the
     * braces of a star read as a value, which are that star's own call.
     */
    private static boolean opensABracket(final ParserRuleContext context) {
        return openingParen(context) != null || context instanceof FrostlakeParser.FunctionCallStarExprContext
            && ((FrostlakeParser.FunctionCallStarExprContext) context).LBRACE() != null;
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
