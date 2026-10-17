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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which projection policies a projected expression's values come under, so that the restriction travels with the
 * values: every output column of a select carries the policy of the column it projects (see
 * {@code ResultSetColumn#getProjectionPolicyName}), and a derived table, a CTE, a view body, a set operation or a
 * lateral relation built from it carries the policy on to the query that reads it. The query that finally projects
 * the values, however many relations they passed through, is the one the policy refuses.
 *
 * <p>An expression comes under a policy when it names a column carrying one (a stored column, or a derived one that
 * inherited it), when it hands every column of its relation to a function ({@code OBJECT_CONSTRUCT(*)},
 * {@code HASH(*)}; COUNT(*) reads no value), or when it holds a subquery whose own select list hands out such a value.
 * The subqueries are judged by the shapes their plan-time compile produced, which {@link #recordSubquery} keeps.
 *
 * <p>A column a counting aggregate reads (COUNT, COUNT_IF, APPROX_COUNT_DISTINCT, HLL) or a window's or WITHIN GROUP's
 * ordering names hands out no value of it, so it is used there, as in a WHERE or an ORDER BY (live-verified for
 * COUNT). Every other function over the column hands its values out: MAX(p) is one of them, and so is SUM(p) over a
 * group of one.
 */
public final class ProjectionLineage {

    /** The policy each subquery compiled on this thread hands its values out under, by the subquery's text. */
    private static final ThreadLocal<Map<String, String>> SUBQUERIES = new ThreadLocal<Map<String, String>>() {
        @Override
        protected Map<String, String> initialValue() {
            return new HashMap<>();
        }
    };

    private ProjectionLineage() {
    }

    /**
     * Records the policy a compiled subquery's select list hands its values out under, if any.
     *
     * @param text   the subquery as written
     * @param shapes what its compile produced, the last being its result's shape
     */
    public static void recordSubquery(final String text, final List<ResultSet> shapes) {
        if (text == null || shapes == null || shapes.isEmpty() || shapes.get(shapes.size() - 1) == null) {
            return;
        }
        for (final ResultSetColumn column : shapes.get(shapes.size() - 1).getColumns()) {
            if (column.getProjectionPolicyName() != null) {
                SUBQUERIES.get().put(text, column.getProjectionPolicyName());
                return;
            }
        }
    }

    /** Forgets the subqueries recorded on this thread, as a statement begins. */
    static void forgetSubqueries() {
        SUBQUERIES.get().clear();
    }

    /** Whether any subquery compiled on this thread handed out a restricted value. */
    static boolean hasRecordedSubqueries() {
        return !SUBQUERIES.get().isEmpty();
    }

    /**
     * The projection policies whose values an expression hands out, in the order it names them.
     *
     * @param expression the expression as written, or null
     * @param sources    the relations it is written over
     * @param subqueries the texts of the subqueries it holds
     * @return each policy once, fully qualified; empty when none
     */
    static List<String> policiesOf(final String expression, final List<Table> sources, final List<String> subqueries) {
        final List<String> policies = new ArrayList<>();
        if (expression == null) {
            return policies;
        }
        final String handed = withoutUses(expression);
        // Read only once a column carries a policy: the common relation carries none.
        Boolean everyColumn = null;
        for (final Table source : sources) {
            if (source == null) {
                continue;
            }
            for (final TableColumn column : source.columnsView()) {
                if (!column.hasProjectionPolicy() || policies.contains(column.getProjectionPolicyName())) {
                    continue;
                }
                if (everyColumn == null) {
                    everyColumn = handsOverEveryColumn(handed);
                }
                if (everyColumn || ExpressionColumns.references(handed, column.getName())) {
                    policies.add(column.getProjectionPolicyName());
                }
            }
        }
        for (final String subquery : subqueries) {
            final String policy = SUBQUERIES.get().get(subquery);
            if (policy != null && !policies.contains(policy)) {
                policies.add(policy);
            }
        }
        return policies;
    }

    /**
     * The policed columns an expression hands out, each with the relation holding it: the columns
     * {@link #policiesOf} reads its policies from, one entry a column, in the same order.
     *
     * @param expression the expression as written, or null
     * @param sources    the relations it is written over
     * @return each column with its relation, as {@code {relation, column}}
     */
    static List<Object[]> policedColumnsOf(final String expression, final List<Table> sources) {
        final List<Object[]> columns = new ArrayList<>();
        if (expression == null) {
            return columns;
        }
        final String handed = withoutUses(expression);
        Boolean everyColumn = null;
        for (final Table source : sources) {
            if (source == null) {
                continue;
            }
            for (final TableColumn column : source.columnsView()) {
                if (!column.hasProjectionPolicy()) {
                    continue;
                }
                if (everyColumn == null) {
                    everyColumn = handsOverEveryColumn(handed);
                }
                if (everyColumn || ExpressionColumns.references(handed, column.getName())) {
                    columns.add(new Object[] {source, column});
                }
            }
        }
        return columns;
    }

    /** The aggregates whose answer is a count, which holds no value of what they read. */
    private static final List<String> COUNTING = List.of("COUNT", "COUNT_IF", "APPROX_COUNT_DISTINCT", "HLL");

    /**
     * The expression with what only uses a column taken out: a counting aggregate's arguments, and the ordering an
     * OVER or a WITHIN GROUP clause reads. What is left hands its values out. The parentheses stay, so
     * {@code COUNT(p)} reads {@code COUNT()}.
     */
    static String withoutUses(final String expression) {
        final String upper = expression.toUpperCase(Locale.ROOT);
        if (!upper.contains("COUNT") && !upper.contains("HLL") && !upper.contains("OVER") && !upper.contains("WITHIN")) {
            return expression;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        final StringBuilder out = new StringBuilder(expression.length());
        int cursor = 0;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            final String word = tokens.get(i).getText().toUpperCase(Locale.ROOT);
            int open = -1;
            if ((COUNTING.contains(word) || "OVER".equals(word))
                    && tokens.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                open = i + 1;
            } else if ("WITHIN".equals(word) && i + 2 < tokens.size()
                    && "GROUP".equalsIgnoreCase(tokens.get(i + 1).getText())
                    && tokens.get(i + 2).getType() == FrostlakeLexer.LPAREN) {
                open = i + 2;
            }
            final int close = open < 0 ? -1 : closing(tokens, open);
            if (close < 0) {
                continue;
            }
            out.append(expression, cursor, tokens.get(open).getStopIndex() + 1);
            cursor = tokens.get(close).getStartIndex();
            i = close;
        }
        out.append(expression, cursor, expression.length());
        return out.toString();
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 when the text leaves it open. */
    private static int closing(final List<Token> tokens, final int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
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
        return -1;
    }

    /**
     * Whether an expression hands every column of its relation to a function: a call whose argument is {@code *}
     * or {@code t.*}. COUNT(*) counts rows and reads no value.
     */
    private static boolean handsOverEveryColumn(final String expression) {
        if (expression.indexOf('*') < 0) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        for (int i = 1; i < tokens.size(); i++) {
            if (!"*".equals(tokens.get(i).getText())) {
                continue;
            }
            // The token before the star: an opening parenthesis, or the dot of a qualified star whose opening
            // parenthesis stands two tokens further back.
            int open = i - 1;
            if (tokens.get(open).getType() == FrostlakeLexer.DOT && open >= 2) {
                open -= 2;
            }
            if (tokens.get(open).getType() == FrostlakeLexer.LPAREN && open >= 1
                    && !"COUNT".equalsIgnoreCase(tokens.get(open - 1).getText())) {
                return true;
            }
        }
        return false;
    }
}
