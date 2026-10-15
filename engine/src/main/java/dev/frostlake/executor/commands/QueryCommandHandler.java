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

import dev.frostlake.executor.CommonTableExpressions;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for Query commands:
 * SELECT statements
 */
public class QueryCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(QueryCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    public QueryCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public ResultSet handleQueryStatement(final FrostlakeParser.QueryStatementContext ctx) {
        if (ctx.selectStatement() != null) {
            if (ctx.forUpdateClause() != null) {
                throw refusedLock(ctx.selectStatement());
            }
            return handleSelectStatement(ctx.selectStatement());
        }
        throw new RuntimeException("Unsupported query statement");
    }

    /**
     * The refusal of a query written with FOR UPDATE, which locks the rows of a hybrid table and of nothing else:
     * {@code Unsupported feature 'FOR UPDATE clause'.} once the query itself has compiled — a relation or a column
     * it misses is told first — and, for a query that reads no table, {@code A 'FOR UPDATE' clause is not
     * compatible with 'not selecting from a table'.} (live-verified).
     */
    private RuntimeException refusedLock(final FrostlakeParser.SelectStatementContext query) {
        if (!readsATable(query, new HashSet<FrostlakeParser.CteDefinitionContext>())) {
            return new RuntimeException(
                "A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'.");
        }
        // The query is compiled as an EXPLAIN compiles it: its names are resolved and nothing is run.
        queryExecutor.execute("EXPLAIN " + ParseTreeText.getOriginalText(query));
        return new RuntimeException("Unsupported feature 'FOR UPDATE clause'.");
    }

    /**
     * Whether a query reads a table, at any depth of its set operations: a FROM of its own that names a table or a
     * view, calls a table function or reads a stage — directly, through a derived table that reads one, or through
     * a common table expression of the statement that does. A FROM of VALUES, of a derived table that reads no
     * table or of a common table expression that reads none reads no table (live-verified: {@code SELECT * FROM
     * (VALUES (1)) v FOR UPDATE} and {@code WITH c AS (SELECT 1 AS x) SELECT * FROM c FOR UPDATE} are 'not
     * selecting from a table', {@code SELECT * FROM (SELECT id FROM t) FOR UPDATE} and {@code WITH c AS (SELECT id
     * FROM t) SELECT * FROM c FOR UPDATE} the unsupported feature).
     *
     * @param node    the query, or a part of it
     * @param entered the common table expressions already entered, so a recursive one is read once
     */
    private static boolean readsATable(final ParseTree node, final Set<FrostlakeParser.CteDefinitionContext> entered) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            final FrostlakeParser.SelectClauseContext clause = (FrostlakeParser.SelectClauseContext) node;
            if (clause.FROM() == null) {
                return false;
            }
            final List<FrostlakeParser.TableSourceContext> sources = new ArrayList<>();
            collectSources(clause.tableExpression(), sources);
            for (final FrostlakeParser.TableSourceContext source : sources) {
                if (sourceReadsATable(source, entered)) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if ((child instanceof FrostlakeParser.SelectOperandContext
                    || child instanceof FrostlakeParser.SelectStatementContext
                    || child instanceof FrostlakeParser.SelectClauseContext) && readsATable(child, entered)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one source of a FROM reads a table (see {@link #readsATable}). */
    private static boolean sourceReadsATable(final FrostlakeParser.TableSourceContext source,
                                             final Set<FrostlakeParser.CteDefinitionContext> entered) {
        if (source.VALUES() != null) {
            return false;
        }
        if (source.selectStatement() != null) {
            return readsATable(source.selectStatement(), entered);
        }
        if (source.tableQualifiedName() != null) {
            final FrostlakeParser.CteDefinitionContext cte = CommonTableExpressions.named(source);
            if (cte == null) {
                return true;
            }
            return entered.add(cte) && readsATable(cte.selectStatement(), entered);
        }
        return source.tableReference() == null;
    }

    /** The sources of a FROM, a parenthesized group's included, and none of a query nested in it. */
    private static void collectSources(final ParseTree node, final List<FrostlakeParser.TableSourceContext> into) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.TableSourceContext) {
            into.add((FrostlakeParser.TableSourceContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSources(node.getChild(i), into);
        }
    }

    public ResultSet handleSelectStatement(final FrostlakeParser.SelectStatementContext ctx) {
        // Delegate to QueryExecutor which has the full SELECT logic
        return queryExecutor.executeSelectFromContext(ctx);
    }
}
