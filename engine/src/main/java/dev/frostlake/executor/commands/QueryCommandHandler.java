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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
        if (!readsARelation(query)) {
            return new RuntimeException(
                "A 'FOR UPDATE' clause is not compatible with 'not selecting from a table'.");
        }
        // The query is compiled as an EXPLAIN compiles it: its names are resolved and nothing is run.
        queryExecutor.execute("EXPLAIN " + ParseTreeText.getOriginalText(query));
        return new RuntimeException("Unsupported feature 'FOR UPDATE clause'.");
    }

    /**
     * Whether a query has a FROM clause of its own, at any depth of its set operations, that reads more than a
     * common table expression of the statement.
     */
    private static boolean readsARelation(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            final FrostlakeParser.SelectClauseContext clause = (FrostlakeParser.SelectClauseContext) node;
            return clause.FROM() != null && !readsOnlyACommonTableExpression(clause);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if ((child instanceof FrostlakeParser.SelectOperandContext
                    || child instanceof FrostlakeParser.SelectStatementContext
                    || child instanceof FrostlakeParser.SelectClauseContext) && readsARelation(child)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a query's FROM is one name, and that name one of its statement's common table expressions. */
    private static boolean readsOnlyACommonTableExpression(final FrostlakeParser.SelectClauseContext clause) {
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        if (relations.size() != 1 || relations.get(0).tableSource() == null
                || relations.get(0).tableSource().tableQualifiedName() == null) {
            return false;
        }
        final String name = relations.get(0).tableSource().tableQualifiedName().getText();
        ParseTree up = clause.getParent();
        while (up != null) {
            if (up instanceof FrostlakeParser.SelectStatementContext
                    && ((FrostlakeParser.SelectStatementContext) up).withClause() != null) {
                for (final FrostlakeParser.CteDefinitionContext cte
                        : ((FrostlakeParser.SelectStatementContext) up).withClause().cteDefinition()) {
                    if (cte.getStart().getText().equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
            up = up.getParent();
        }
        return false;
    }

    private static void collectRelations(final ParseTree node,
                                         final List<FrostlakeParser.TableReferenceContext> into) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.TableReferenceContext) {
            into.add((FrostlakeParser.TableReferenceContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRelations(node.getChild(i), into);
        }
    }

    public ResultSet handleSelectStatement(final FrostlakeParser.SelectStatementContext ctx) {
        // Delegate to QueryExecutor which has the full SELECT logic
        return queryExecutor.executeSelectFromContext(ctx);
    }
}
