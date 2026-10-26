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

import dev.frostlake.executor.expressions.ChangeTrackedAggregateText;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *   an aggregate over the whole block     the function as the account computes it, at the call
 * </pre>
 *
 * Ahead of what a query block holds, the block itself is judged, each construct in this order: DISTINCT at its
 * SELECT, GROUP BY (and GROUPING SETS, CUBE or ROLLUP) at its GROUP, a LIMIT, TOP or FETCH of the query it is the
 * whole of at no place, QUALIFY at its QUALIFY, a window function in its select list at its SELECT, CONNECT BY at no
 * place, then the last outer join of its FROM clause at no place. A set operation is judged ahead of its operands: a
 * LIMIT or FETCH over it, then its first operator that is no UNION ALL, both at no place. A window in an ORDER BY is
 * no window function of the select list, and is not refused.
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
    private static final String DISTINCT = "Change tracking is not supported on queries with DISTINCT. Remove the"
        + " DISTINCT keyword from the query or from the definition of the view that contains it.";
    private static final String GROUP_BY = "Change tracking is not supported on queries with GROUP BY. Remove the"
        + " GROUP BY clause from the query or from the definition of the view that contains it.";
    private static final String SUPER_GROUPS = "Change tracking is not supported on queries with GROUP BY GROUPING"
        + " SETS/CUBE/ROLLUP. Remove the GROUP BY GROUPING SETS/CUBE/ROLLUP clause from the query or from the"
        + " definition of the view that contains it.";
    private static final String LIMIT = "Change tracking is not supported on queries with LIMIT.";
    private static final String QUALIFY = "Change tracking is not supported on queries with QUALIFY.";
    private static final String WINDOW = "Change tracking is not supported on queries with window functions.";
    private static final String RECURSIVE = "Change tracking is not supported on recursive queries (e.g., with"
        + " CONNECT BY or recursive CTE).";
    private static final String TABLE_FUNCTION = "Change tracking is not supported on queries with 'Table Function'.";
    /** How deep views reading views are followed. */
    private static final int MAX_DEPTH = 32;

    private final Catalog catalog;
    /** The functions, which tell an aggregate; null to judge no call as one. */
    private final FunctionRegistry functions;
    private final String database;
    private final String schema;
    private final int depth;
    private final Set<String> cteNames = new HashSet<>();
    private String refusal;
    private Token at;
    private String insideView;

    private ChangeTrackedQuery(final Catalog catalog, final FunctionRegistry functions, final String database,
                               final String schema, final int depth, final ParseTree statement) {
        this.catalog = catalog;
        this.functions = functions;
        this.database = database;
        this.schema = schema;
        this.depth = depth;
        QueryExecutor.collectCteNames(statement, cteNames);
    }

    /**
     * The refusal of a view's query that cannot track its changes.
     *
     * @param catalog   the catalog the query's names are found in, its session's database and schema in force
     * @param functions the functions, which tell an aggregate
     * @param query     the view's query
     * @return the refusal, or null when the query can track its changes
     */
    public static String refusal(final Catalog catalog, final FunctionRegistry functions,
                                 final FrostlakeParser.SelectStatementContext query) {
        final ChangeTrackedQuery judged = new ChangeTrackedQuery(catalog, functions, catalog.getCurrentDatabase(),
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
     * @param catalog   the catalog
     * @param functions the functions, which tell an aggregate
     * @param database  the view's database
     * @param schema    the view's schema
     * @param view      the view
     * @return the refusal, or null when the view can track its changes
     */
    public static String viewRefusal(final Catalog catalog, final FunctionRegistry functions, final String database,
                                     final String schema, final View view) {
        final FrostlakeParser.SelectStatementContext body = body(view);
        if (body == null) {
            return null;
        }
        final ChangeTrackedQuery judged = new ChangeTrackedQuery(catalog, functions, database, schema, 1, body);
        if (!judged.walk(body)) {
            return null;
        }
        return judged.sentence(judged.insideView != null ? judged.insideView
            : database + "." + schema + "." + view.getName());
    }

    /**
     * The refusal of a stream on a view whose query change tracking cannot follow, as CREATE STREAM ... ON VIEW gives
     * it: a table function anywhere in the view's own query first, at the function's name and with no view named, then
     * the view's refusal as an ALTER VIEW ... SET CHANGE_TRACKING = TRUE gives it (live-verified).
     *
     * @param catalog   the catalog
     * @param functions the functions, which tell an aggregate
     * @param database  the view's database
     * @param schema    the view's schema
     * @param view      the view
     * @return the refusal, or null when a stream can follow the view's changes
     */
    public static String streamRefusal(final Catalog catalog, final FunctionRegistry functions, final String database,
                                       final String schema, final View view) {
        final FrostlakeParser.SelectStatementContext body = body(view);
        final Token function = body == null ? null : firstTableFunction(body);
        if (function == null) {
            return viewRefusal(catalog, functions, database, schema, view);
        }
        final ChangeTrackedQuery judged = new ChangeTrackedQuery(catalog, functions, database, schema, 1, body);
        judged.refuse(TABLE_FUNCTION, function);
        return judged.sentence(null);
    }

    /** The name of the first table function a query reads, in the order it is written, or null. */
    private static Token firstTableFunction(final ParseTree node) {
        if (node instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) node;
            if (source.FLATTEN() != null) {
                return source.FLATTEN().getSymbol();
            }
            if (source.tableFunctionExpr() != null) {
                return source.tableFunctionExpr().getStart();
            }
            final FrostlakeParser.FunctionNameContext called = source.TABLE() != null && source.expression() != null
                ? firstFunctionName(source.expression()) : null;
            if (called != null) {
                return called.getStart();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstTableFunction(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
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

    /**
     * The tables a dynamic table's query reads, through the views it reads too: its inputs that are tables.
     *
     * @param catalog      the catalog
     * @param database     the database its bare names are read in
     * @param schema       the schema its bare names are read in
     * @param dynamicTable the dynamic table
     * @return the tables, each once
     */
    public static List<Table> readTables(final Catalog catalog, final String database, final String schema,
                                         final DynamicTable dynamicTable) {
        final List<Table> tables = new ArrayList<>();
        final ParseTree query = parsed(dynamicTable.getQuery(), true);
        collectTables(catalog, database, schema, query == null ? null : (FrostlakeParser.SelectStatementContext) query,
            0, tables);
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
        if (node instanceof FrostlakeParser.SelectStatementContext
                && judgeSetOperation((FrostlakeParser.SelectStatementContext) node)) {
            return true;
        }
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            final FrostlakeParser.SelectClauseContext block = (FrostlakeParser.SelectClauseContext) node;
            if (judgeBlock(block)) {
                return true;
            }
            if (block.tableExpression() == null) {
                return refuse(VALUES, block.getStart());
            }
        }
        if (isScalarAggregate(node)) {
            return refuse(aggregateRefusal((ParserRuleContext) node), ((ParserRuleContext) node).getStart());
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

    /** Whether a set operation is refused ahead of its operands: a LIMIT over it, then its first operator's kind. */
    private boolean judgeSetOperation(final FrostlakeParser.SelectStatementContext statement) {
        if (statement.selectOperand().size() < 2) {
            return false;
        }
        if (statement.limitClause() != null || statement.fetchClause() != null) {
            return refuse(LIMIT, null);
        }
        for (final FrostlakeParser.SetOperatorContext operator : statement.setOperator()) {
            if (operator.UNION() != null && operator.ALL() == null) {
                return refuse(joins("UNION"), null);
            }
            if (operator.INTERSECT() != null) {
                return refuse(joins("INTERSECT"), null);
            }
            if (operator.EXCEPT() != null || operator.MINUS_KW() != null) {
                return refuse(joins("MINUS"), null);
            }
        }
        return false;
    }

    /** Whether a query block is refused ahead of what it holds (see the class's order). */
    private boolean judgeBlock(final FrostlakeParser.SelectClauseContext block) {
        if (block.DISTINCT() != null) {
            return refuse(DISTINCT, block.getStart());
        }
        final FrostlakeParser.GroupByClauseContext groupBy = block.groupByClause();
        if (groupBy != null) {
            return refuse(superGroups(groupBy) ? SUPER_GROUPS : GROUP_BY, groupBy.getStart());
        }
        if (block.topClause() != null || limitsWhole(block)) {
            return refuse(LIMIT, null);
        }
        if (block.qualifyClause() != null) {
            return refuse(QUALIFY, block.qualifyClause().getStart());
        }
        if (holdsWindow(block.selectList())) {
            return refuse(WINDOW, block.getStart());
        }
        if (block.connectByClause() != null) {
            return refuse(RECURSIVE, null);
        }
        final String outer = block.tableExpression() == null ? null : lastOuterJoin(block.tableExpression());
        return outer != null && refuse(joins(outer), null);
    }

    /** Whether a GROUP BY groups by GROUPING SETS, CUBE or ROLLUP. */
    private static boolean superGroups(final FrostlakeParser.GroupByClauseContext groupBy) {
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.ROLLUP() != null || element.CUBE() != null || element.GROUPING() != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block is the whole of a query that ends in LIMIT or FETCH. */
    private static boolean limitsWhole(final FrostlakeParser.SelectClauseContext block) {
        final ParseTree operand = block.getParent();
        final ParseTree statement = operand == null ? null : operand.getParent();
        if (!(operand instanceof FrostlakeParser.SelectOperandContext)
                || !(statement instanceof FrostlakeParser.SelectStatementContext)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query = (FrostlakeParser.SelectStatementContext) statement;
        return query.selectOperand().size() == 1 && (query.limitClause() != null || query.fetchClause() != null);
    }

    /** Whether a part of a block holds a window, outside the queries nested in it. */
    private static boolean holdsWindow(final ParseTree node) {
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsWindow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The kinds the account lists for the last outer join of a FROM clause, or null when it has none. */
    private static String lastOuterJoin(final FrostlakeParser.TableExpressionContext from) {
        String kinds = null;
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            final FrostlakeParser.JoinTypeContext type = join.joinType();
            if (type != null && type.LEFT() != null) {
                kinds = "LEFT_OUTER_JOIN";
            } else if (type != null && type.RIGHT() != null) {
                kinds = "RIGHT_OUTER_JOIN, LEFT_OUTER_JOIN";
            } else if (type != null && type.FULL() != null) {
                kinds = "FULL_OUTER_JOIN";
            }
        }
        return kinds;
    }

    private static String joins(final String kinds) {
        return "Change tracking is not supported on queries with joins of type '[" + kinds + "]'.";
    }

    /** Whether a node is a call of an aggregate with no window over it. */
    private boolean isScalarAggregate(final ParseTree node) {
        final FrostlakeParser.FunctionNameContext name;
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() == null) {
            name = ((FrostlakeParser.FunctionCallExprContext) node).functionName();
        } else if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            name = ((FrostlakeParser.FunctionCallStarExprContext) node).functionName();
        } else {
            return false;
        }
        return functions != null && name != null
            && !(node.getParent() instanceof FrostlakeParser.WindowedOperandExprContext)
            && functions.hasAggregateFunction(SqlIdentifiers.canonicalText(name.getText()).toUpperCase(Locale.ROOT));
    }

    /** The refusal of an aggregate over a whole block, the call named as the account computes it. */
    private String aggregateRefusal(final ParserRuleContext call) {
        final String written = ParseTreeText.getOriginalText(call);
        final Expression parsed = ExpressionEvaluator.parse(written);
        String text = written.toUpperCase(Locale.ROOT);
        if (parsed instanceof FunctionCallExpression) {
            final Map<String, String> qualifiers = new HashMap<>();
            final Set<String> textColumns = new HashSet<>();
            ParseTree block = call.getParent();
            while (block != null && !(block instanceof FrostlakeParser.SelectClauseContext)) {
                block = block.getParent();
            }
            if (block != null && ((FrostlakeParser.SelectClauseContext) block).tableExpression() != null) {
                collectColumns(((FrostlakeParser.SelectClauseContext) block).tableExpression(), qualifiers,
                    textColumns);
            }
            text = new ChangeTrackedAggregateText(qualifiers, textColumns).spell((FunctionCallExpression) parsed);
        }
        return "Query contains the function '" + text
            + "', but change tracking is not supported on queries with scalar aggregate functions.";
    }

    /** The relation each column a FROM clause's tables hold is read under, and which of them hold text. */
    private void collectColumns(final FrostlakeParser.TableExpressionContext from, final Map<String, String> qualifiers,
                                final Set<String> textColumns) {
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>(from.tableReference());
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            references.add(join.tableReference());
        }
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            final FrostlakeParser.TableQualifiedNameContext name = reference.tableSource().tableQualifiedName();
            final Table table = name == null ? null : tableNamed(name);
            if (table == null) {
                continue;
            }
            final ParserRuleContext alias = reference.aliasName() != null ? reference.aliasName()
                : reference.nonJoinKeywordIdentifier();
            final String qualifier = alias != null ? SqlIdentifiers.canonicalText(alias.getText()) : table.getName();
            for (final TableColumn column : table.getColumns()) {
                if (!qualifiers.containsKey(column.getName())) {
                    qualifiers.put(column.getName(), qualifier);
                }
                if (column.getDataType() instanceof StringType) {
                    textColumns.add(qualifier + "." + column.getName());
                }
            }
        }
    }

    /** The table a relation named in the query is, read where the query is judged, or null. */
    private Table tableNamed(final FrostlakeParser.TableQualifiedNameContext name) {
        final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(name), 3);
        final Schema holder = schemaNamed(catalog, parts.length == 3 ? parts[0] : database,
            parts.length >= 2 ? parts[parts.length - 2] : schema);
        return holder == null ? null : holder.tableExact(parts[parts.length - 1]);
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
        final ChangeTrackedQuery inner = new ChangeTrackedQuery(catalog, functions, viewDatabase, viewSchema, depth + 1,
            body);
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
