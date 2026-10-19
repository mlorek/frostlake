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

import dev.frostlake.parser.FrostlakeParser;

import java.util.Locale;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Where a GROUPING or GROUPING_ID call may stand. It tells which of a GROUP BY's keys a row was grouped by, so a
 * query that groups by nothing has no use for it, and the account refuses it while the statement compiles, once
 * every name has resolved (live-verified):
 *
 * <pre>
 *   SELECT GROUPING(n) FROM t                    GROUPING function without a GROUP BY.
 *   SELECT n FROM t WHERE GROUPING(n) = 0        the same; in ORDER BY, QUALIFY and beside an aggregate too
 *   SELECT n FROM t HAVING GROUPING(n) = 0       the same, the HAVING being a filter where nothing aggregates
 *   SELECT COUNT(*) FROM t HAVING GROUPING(n) = 0   [T.N] is not a valid group by expression - the grouped rule's
 *   SELECT n, GROUPING(n) FROM t GROUP BY ALL    GROUPING function cannot appear outside of SELECT, HAVING, and
 *                                                ORDER BY clauses.
 * </pre>
 *
 * <p>GROUP BY ALL groups by every select item that is no aggregate, the GROUPING call among them, which puts
 * the call in the GROUP BY. A call inside a nested query is that query's own.
 */
final class GroupingCallRules {

    private static final String WITHOUT_GROUP_BY = "GROUPING function without a GROUP BY.";
    private static final String IN_GROUP_BY =
        "GROUPING function cannot appear outside of SELECT, HAVING, and ORDER BY clauses.";

    private GroupingCallRules() {
    }

    /**
     * Refuse a GROUPING call the query's grouping cannot answer.
     *
     * @param statement  the statement the select clause belongs to, for its ORDER BY
     * @param select     the select clause
     * @param aggregates whether the query computes an aggregate anywhere
     */
    static void validate(final FrostlakeParser.SelectStatementContext statement,
                         final FrostlakeParser.SelectClauseContext select, final boolean aggregates) {
        if (select.groupByClause() != null) {
            if (select.groupByClause().ALL() != null && calls(select.selectList())) {
                throw new RuntimeException(SqlCompilationError.of(IN_GROUP_BY));
            }
            return;
        }
        final boolean filterHaving = select.havingClause() != null && !aggregates;
        if (calls(select.selectList()) || calls(select.whereClause()) || calls(select.qualifyClause())
                || filterHaving && calls(select.havingClause())
                || statement != null && calls(statement.orderByClause())) {
            throw new RuntimeException(SqlCompilationError.of(WITHOUT_GROUP_BY));
        }
    }

    /** Whether the tree holds a GROUPING call outside its nested queries. */
    private static boolean calls(final ParseTree node) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).functionArgList() != null) {
            // A call written with no argument is refused for that, where its arguments are counted.
            final String name = SqlIdentifiers.canonicalText(
                ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()).toUpperCase(Locale.ROOT);
            if ("GROUPING".equals(name) || "GROUPING_ID".equals(name)) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (calls(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
