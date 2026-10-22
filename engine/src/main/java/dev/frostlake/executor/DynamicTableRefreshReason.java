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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Why a dynamic table created with REFRESH_MODE AUTO settles on FULL: the first construct of its query, in the order
 * the query is written, that its incremental refresh does not follow, through the views it reads (all
 * live-verified):
 *
 * <pre>
 *   EXISTS, a scalar subquery, IN (SELECT …)        Change tracking is not supported on queries with subquery
 *                                                   expressions.
 *   a view whose query reads no relation            Change tracking is not supported on queries with row generators.
 *   LIMIT                                           Change tracking is not supported on queries with LIMIT.
 *   a window over the whole result (no PARTITION)   This dynamic table contains a complex query. …
 *   CURRENT_TIMESTAMP(), RANDOM()                   Query contains the function 'RANDOM', but change tracking is not
 *                                                   supported on queries with non-deterministic functions. …
 * </pre>
 *
 * A plain projection, a GROUP BY, a join, UNION and UNION ALL, and FLATTEN are followed: the table stays INCREMENTAL.
 */
public final class DynamicTableRefreshReason {

    private static final String SUBQUERY = "Change tracking is not supported on queries with subquery expressions.";
    private static final String ROW_GENERATORS = "Change tracking is not supported on queries with row generators.";
    private static final String LIMIT = "Change tracking is not supported on queries with LIMIT.";
    private static final String COMPLEX = "This dynamic table contains a complex query. Refresh mode has been"
        + " automatically set to FULL for more predictable performance. To use INCREMENTAL, re-create the dynamic"
        + " table with REFRESH_MODE=INCREMENTAL. See"
        + " https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details.";
    /** How deep views reading views are followed. */
    private static final int MAX_DEPTH = 32;

    private final Catalog catalog;
    private final String database;
    private final String schema;
    private final int depth;
    private final Set<String> cteNames = new HashSet<>();

    private DynamicTableRefreshReason(final Catalog catalog, final String database, final String schema,
                                      final int depth, final ParseTree query) {
        this.catalog = catalog;
        this.database = database;
        this.schema = schema;
        this.depth = depth;
        QueryExecutor.collectCteNames(query, cteNames);
    }

    /**
     * Why a dynamic table's query settles an AUTO refresh mode on FULL.
     *
     * @param catalog  the catalog
     * @param database the dynamic table's database
     * @param schema   the dynamic table's schema
     * @param query    the dynamic table's query
     * @return the reason, or null when the query is refreshed incrementally
     */
    public static String of(final Catalog catalog, final String database, final String schema,
                     final FrostlakeParser.SelectStatementContext query) {
        return new DynamicTableRefreshReason(catalog, database, schema, 0, query).walk(query);
    }

    private String walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectClauseContext && depth > 0
                && ((FrostlakeParser.SelectClauseContext) node).tableExpression() == null) {
            return ROW_GENERATORS;
        }
        if (node instanceof FrostlakeParser.ExistsExprContext
                || node instanceof FrostlakeParser.ScalarSubqueryExprContext
                || node instanceof FrostlakeParser.InSubqueryExprContext
                || node instanceof FrostlakeParser.TupleInSubqueryExprContext
                || node instanceof FrostlakeParser.QuantifiedComparisonExprContext
                && ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement() != null) {
            return SUBQUERY;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String reason = callReason((FrostlakeParser.FunctionCallExprContext) node);
            if (reason != null) {
                return reason;
            }
        }
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            final String inside = viewReason(((FrostlakeParser.TableSourceContext) node).tableQualifiedName());
            if (inside != null) {
                return inside;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String reason = walk(node.getChild(i));
            if (reason != null) {
                return reason;
            }
        }
        if (node instanceof FrostlakeParser.SelectStatementContext
                && (((FrostlakeParser.SelectStatementContext) node).limitClause() != null
                    || ((FrostlakeParser.SelectStatementContext) node).fetchClause() != null)) {
            return LIMIT;
        }
        return null;
    }

    /** The reason a call gives: a window over the whole result, or a function whose value changes per run. */
    private static String callReason(final FrostlakeParser.FunctionCallExprContext call) {
        if (call.overClause() != null && call.overClause().partitionByClause() == null) {
            return COMPLEX;
        }
        final String[] parts = ParseTreeText.functionNameParts(call.functionName());
        final String name = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        if ("CURRENT_TIMESTAMP".equals(name) || "RANDOM".equals(name)) {
            return "Query contains the function '" + name + "', but change tracking is not supported on queries with"
                + " non-deterministic functions. For more information, see"
                + " https://docs.snowflake.com/user-guide/dynamic-tables-supported-queries"
                + "#supported-non-deterministic-functions.";
        }
        return null;
    }

    /** The reason a view the query reads gives, judged by its own query, or null. */
    private String viewReason(final FrostlakeParser.TableQualifiedNameContext name) {
        if (depth >= MAX_DEPTH || cteNames.contains(ParseTreeText.getQualifiedName(name).toUpperCase(Locale.ROOT))) {
            return null;
        }
        final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(name), 3);
        final String viewDatabase = parts.length == 3 ? parts[0] : database;
        final String viewSchema = parts.length >= 2 ? parts[parts.length - 2] : schema;
        final View view = viewNamed(viewDatabase, viewSchema, parts[parts.length - 1]);
        final FrostlakeParser.SelectStatementContext body = view == null ? null : ChangeTrackedQuery.body(view);
        return body == null ? null
            : new DynamicTableRefreshReason(catalog, viewDatabase, viewSchema, depth + 1, body).walk(body);
    }

    private View viewNamed(final String viewDatabase, final String viewSchema, final String viewName) {
        if (viewDatabase == null || viewSchema == null) {
            return null;
        }
        try {
            final Database holder = catalog.databaseExact(viewDatabase);
            final Schema container = holder == null ? null : holder.schemaExact(viewSchema);
            return container != null && container.hasViewExact(viewName) ? container.getView(viewName) : null;
        } catch (final RuntimeException missing) {
            return null;
        }
    }
}
