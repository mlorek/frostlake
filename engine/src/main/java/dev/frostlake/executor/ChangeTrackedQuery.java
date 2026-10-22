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
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a query reads, as a view tracking its changes and a dynamic table need it (live-verified).
 *
 * <p>A view created with CHANGE_TRACKING = TRUE is refused the first construct, in the order its query is written,
 * that change tracking cannot follow, at the place the refusal names:
 *
 * <pre>
 *   a query block without FROM            'VALUES', at the block's SELECT
 *   a VALUES list                         'VALUES', at no place: line 0, position -1
 *   a table function                      'TABLE_FUNCTION' inside views, saw '&lt;NAME&gt;', at no place
 *   a scalar subquery                     subquery expressions, at the subquery's SELECT
 *   EXISTS                                subquery expressions, at EXISTS
 *   IN or a quantified comparison         subquery expressions, at no place
 * </pre>
 *
 * A view the query reads is judged by its own query, at the places of its CREATE statement, and the refusal then
 * names it: {@code (inside view 'DB.SCHEMA.V')}.
 *
 * <p>A dynamic table's query must read at least one base table: a relation the account stores, wherever in the
 * query it stands. A common table expression is none, whatever it reads.
 */
public final class ChangeTrackedQuery {

    private static final String VALUES = "Change tracking is not supported on queries with 'VALUES'.";
    private static final String SUBQUERY = "Change tracking is not supported on queries with subquery expressions.";
    /** How deep views reading views are followed. */
    private static final int MAX_DEPTH = 32;

    private final Catalog catalog;
    private final String database;
    private final String schema;
    private final int depth;
    private final Set<String> cteNames = new HashSet<>();
    private String refusal;
    private Token at;
    private String insideView;

    private ChangeTrackedQuery(final Catalog catalog, final String database, final String schema, final int depth,
                               final ParseTree statement) {
        this.catalog = catalog;
        this.database = database;
        this.schema = schema;
        this.depth = depth;
        QueryExecutor.collectCteNames(statement, cteNames);
    }

    /**
     * The refusal of a view's query that cannot track its changes.
     *
     * @param catalog the catalog the query's names are found in, its session's database and schema in force
     * @param query   the view's query
     * @return the refusal, or null when the query can track its changes
     */
    public static String refusal(final Catalog catalog, final FrostlakeParser.SelectStatementContext query) {
        final ChangeTrackedQuery judged = new ChangeTrackedQuery(catalog, catalog.getCurrentDatabase(),
            catalog.getCurrentSchema(), 0, query);
        if (!judged.walk(query)) {
            return null;
        }
        return judged.sentence(judged.insideView);
    }

    /**
     * The refusal of a view whose query cannot track its changes, as an ALTER VIEW ... SET CHANGE_TRACKING = TRUE
     * gives it: the place counted in the view's own CREATE statement, the view named as the one the refusal is
     * inside (live-verified).
     *
     * @param catalog  the catalog
     * @param database the view's database
     * @param schema   the view's schema
     * @param view     the view
     * @return the refusal, or null when the view can track its changes
     */
    public static String viewRefusal(final Catalog catalog, final String database, final String schema,
                                     final View view) {
        final FrostlakeParser.SelectStatementContext body = body(view);
        if (body == null) {
            return null;
        }
        final ChangeTrackedQuery judged = new ChangeTrackedQuery(catalog, database, schema, 1, body);
        if (!judged.walk(body)) {
            return null;
        }
        return judged.sentence(judged.insideView != null ? judged.insideView
            : database + "." + schema + "." + view.getName());
    }

    /**
     * The tables a view reads, through the views it reads too: those whose change tracking turns on with the view's.
     *
     * @param catalog  the catalog
     * @param database the view's database
     * @param schema   the view's schema
     * @param view     the view
     * @return the tables, each once
     */
    public static List<Table> trackedTables(final Catalog catalog, final String database, final String schema,
                                            final View view) {
        final List<Table> tables = new ArrayList<>();
        collectTables(catalog, database, schema, body(view), 0, tables);
        return tables;
    }

    /**
     * The tables a view's query reads, through the views it reads too, as a CREATE VIEW ... CHANGE_TRACKING = TRUE
     * turns their change tracking on with the view's.
     *
     * @param catalog the catalog, its session's database and schema in force
     * @param query   the view's query
     * @return the tables, each once
     */
    public static List<Table> trackedTables(final Catalog catalog, final FrostlakeParser.SelectStatementContext query) {
        final List<Table> tables = new ArrayList<>();
        collectTables(catalog, catalog.getCurrentDatabase(), catalog.getCurrentSchema(), query, 0, tables);
        return tables;
    }

    private static void collectTables(final Catalog catalog, final String database, final String schema,
                                      final FrostlakeParser.SelectStatementContext body, final int depth,
                                      final List<Table> out) {
        if (body == null || depth >= MAX_DEPTH) {
            return;
        }
        final Set<String> ctes = new HashSet<>();
        QueryExecutor.collectCteNames(body, ctes);
        final List<FrostlakeParser.TableQualifiedNameContext> names = new ArrayList<>();
        relationNames(body, names);
        for (final FrostlakeParser.TableQualifiedNameContext name : names) {
            if (ctes.contains(ParseTreeText.getQualifiedName(name).toUpperCase(Locale.ROOT))) {
                continue;
            }
            final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(name), 3);
            final String relationDatabase = parts.length == 3 ? parts[0] : database;
            final String relationSchema = parts.length >= 2 ? parts[parts.length - 2] : schema;
            final Schema container = schemaNamed(catalog, relationDatabase, relationSchema);
            final String relation = parts[parts.length - 1];
            if (container == null) {
                continue;
            }
            if (container.hasViewExact(relation)) {
                collectTables(catalog, relationDatabase, relationSchema, body(container.getView(relation)), depth + 1,
                    out);
            } else if (container.tableExact(relation) != null && !out.contains(container.tableExact(relation))) {
                out.add(container.tableExact(relation));
            }
        }
    }

    private static void relationNames(final ParseTree node, final List<FrostlakeParser.TableQualifiedNameContext> out) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            out.add(((FrostlakeParser.TableSourceContext) node).tableQualifiedName());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            relationNames(node.getChild(i), out);
        }
    }

    private static Schema schemaNamed(final Catalog catalog, final String database, final String schema) {
        if (database == null || schema == null) {
            return null;
        }
        try {
            final Database holder = catalog.databaseExact(database);
            return holder == null ? null : holder.schemaExact(schema);
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** The refusal's sentence: the place, the construct, and the view it is inside, if any. */
    private String sentence(final String inside) {
        final String place = at == null ? "line 0 at position -1"
            : "line " + at.getLine() + " at position " + at.getCharPositionInLine();
        return SqlCompilationError.PREFIX + " " + place + ":\n" + refusal
            + (inside == null ? "" : " (inside view '" + inside + "').");
    }

    /**
     * Whether a query reads a base table: a relation named, or found by IDENTIFIER(), anywhere in it, its
     * subqueries included, and not a common table expression's name.
     *
     * @param query the query
     * @return true when it reads one
     */
    public static boolean readsBaseTable(final ParseTree query) {
        final Set<String> ctes = new HashSet<>();
        QueryExecutor.collectCteNames(query, ctes);
        return readsBaseTable(query, ctes);
    }

    private static boolean readsBaseTable(final ParseTree node, final Set<String> ctes) {
        if (node instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) node;
            if (source.tableQualifiedName() != null && !ctes.contains(
                    ParseTreeText.getQualifiedName(source.tableQualifiedName()).toUpperCase(Locale.ROOT))
                    || source.identifierArgument() != null || source.tableIdentifierCall() != null) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsBaseTable(node.getChild(i), ctes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the first construct change tracking cannot follow is in {@code node}, kept when it is. */
    private boolean walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectClauseContext
                && ((FrostlakeParser.SelectClauseContext) node).tableExpression() == null) {
            return refuse(VALUES, ((FrostlakeParser.SelectClauseContext) node).getStart());
        }
        if (node instanceof FrostlakeParser.TableSourceContext
                && judgeSource((FrostlakeParser.TableSourceContext) node)) {
            return true;
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            return refuse(SUBQUERY, ((FrostlakeParser.ExistsExprContext) node).getStart());
        }
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return refuse(SUBQUERY, ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement().getStart());
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext
                || node instanceof FrostlakeParser.TupleInSubqueryExprContext
                || node instanceof FrostlakeParser.QuantifiedComparisonExprContext
                && ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement() != null) {
            return refuse(SUBQUERY, null);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (walk(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether one FROM source is a construct change tracking cannot follow, or a view whose query has one. */
    private boolean judgeSource(final FrostlakeParser.TableSourceContext source) {
        if (source.VALUES() != null) {
            return refuse(VALUES, null);
        }
        if (source.FLATTEN() != null) {
            return refuse(tableFunction("FLATTEN"), null);
        }
        if (source.tableFunctionExpr() != null) {
            return refuse(tableFunction(lastPart(source.tableFunctionExpr().functionName())), null);
        }
        if (source.TABLE() != null && source.expression() != null) {
            final FrostlakeParser.FunctionNameContext function = firstFunctionName(source.expression());
            return function != null && refuse(tableFunction(lastPart(function)), null);
        }
        if (source.tableQualifiedName() != null) {
            return judgeView(source.tableQualifiedName());
        }
        return false;
    }

    /** Whether a relation named in the query is a view whose own query has a construct tracking cannot follow. */
    private boolean judgeView(final FrostlakeParser.TableQualifiedNameContext name) {
        if (depth >= MAX_DEPTH
                || cteNames.contains(ParseTreeText.getQualifiedName(name).toUpperCase(Locale.ROOT))) {
            return false;
        }
        final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(name), 3);
        final String viewDatabase = parts.length == 3 ? parts[0] : database;
        final String viewSchema = parts.length >= 2 ? parts[parts.length - 2] : schema;
        final String viewName = parts[parts.length - 1];
        final View view = viewNamed(viewDatabase, viewSchema, viewName);
        if (view == null) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext body = body(view);
        if (body == null) {
            return false;
        }
        final ChangeTrackedQuery inner = new ChangeTrackedQuery(catalog, viewDatabase, viewSchema, depth + 1, body);
        if (!inner.walk(body)) {
            return false;
        }
        refusal = inner.refusal;
        at = inner.at;
        insideView = inner.insideView != null ? inner.insideView
            : viewDatabase + "." + viewSchema + "." + view.getName();
        return true;
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

    /** A view's query as its CREATE statement wrote it, so that its places count from that statement's start. */
    static FrostlakeParser.SelectStatementContext body(final View view) {
        if (view.getOriginalDdl() != null) {
            final ParseTree statement = parsed(view.getOriginalDdl(), false);
            final FrostlakeParser.SelectStatementContext written = statement == null ? null : firstQuery(statement);
            if (written != null) {
                return written;
            }
        }
        final ParseTree query = parsed(view.getDefinition(), true);
        return query == null ? null : (FrostlakeParser.SelectStatementContext) query;
    }

    private static ParseTree parsed(final String text, final boolean query) {
        if (text == null) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return query ? parser.selectStatement() : parser.sqlScript();
        } catch (final RuntimeException unparsable) {
            return null;
        }
    }

    private static FrostlakeParser.SelectStatementContext firstQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return (FrostlakeParser.SelectStatementContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectStatementContext found = firstQuery(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static FrostlakeParser.FunctionNameContext firstFunctionName(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionNameContext) {
            return (FrostlakeParser.FunctionNameContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.FunctionNameContext found = firstFunctionName(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String lastPart(final FrostlakeParser.FunctionNameContext function) {
        final String[] parts = ParseTreeText.functionNameParts(function);
        return parts[parts.length - 1];
    }

    private static String tableFunction(final String name) {
        return "Change tracking is not supported on queries with 'TABLE_FUNCTION' inside views, saw '" + name + "'.";
    }

    private boolean refuse(final String text, final Token place) {
        refusal = text;
        at = place;
        return true;
    }
}
