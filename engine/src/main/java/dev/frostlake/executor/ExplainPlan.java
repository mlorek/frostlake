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

import dev.frostlake.executor.commands.ExplainedStatementChecks;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EXPLAIN in the account's shapes (live-verified). The statement compiles first, so a missing object is refused as the
 * statement itself would refuse it, and then the plan is answered in the format USING names, TABULAR by default:
 *
 * <ul>
 *   <li>TABULAR — ten columns ({@code step}, {@code id}, {@code parentOperators}, {@code operation}, {@code objects},
 *       {@code alias}, {@code expressions}, {@code partitionsTotal}, {@code partitionsAssigned},
 *       {@code bytesAssigned}), a GlobalStats row first and then one row per operator;</li>
 *   <li>TEXT — one {@code content} column holding the plan as text: the GlobalStats block, then an Operations list
 *       indented by depth;</li>
 *   <li>JSON — the same {@code content} column holding the plan as a JSON document.</li>
 * </ul>
 *
 * <p>Any other word after USING is refused. The operators are those {@link PlanBuilder} arranges for the statement,
 * numbered from the top; a scan reports one partition of 1024 bytes for a table that holds rows, and GlobalStats
 * their sums.
 */
final class ExplainPlan {

    private static final NumericType NUMBER_9 = new NumericType("NUMBER", 9, 0);

    /** The bytes a plan assigns for each partition it scans. */
    private static final long PARTITION_BYTES = 1024;

    /** What a value is written after to be compiled as the one item of a query. */
    private static final String VALUE_QUERY = "SELECT ";

    private static final ThreadLocal<int[]> PLANNING = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    private ExplainPlan() {
    }

    /**
     * Whether a statement is being compiled for its plan alone. A procedure called in the FROM clause, and one
     * a TO_QUERY text calls, does not run then: an EXPLAIN writes nothing (live-verified).
     *
     * @return true while an EXPLAIN compiles its statement
     */
    static boolean planning() {
        return PLANNING.get()[0] > 0;
    }

    /**
     * Compile a query for its shape alone, as an EXPLAIN compiles it: a name it cannot resolve is refused, and
     * nothing it reads is carried out.
     *
     * @param executor the executor that compiles the query
     * @param query    the query's text
     */
    static void compileOnly(final QueryExecutor executor, final String query) {
        PLANNING.get()[0]++;
        try {
            executor.resolveRelationShape(query, null);
        } finally {
            PLANNING.get()[0]--;
        }
    }

    /**
     * The plan of an EXPLAIN, in the format it names.
     *
     * @param executor the executor that compiles the statement
     * @param ctx      the EXPLAIN
     * @return the plan
     */
    static ResultSet answer(final QueryExecutor executor, final FrostlakeParser.ExplainStatementContext ctx) {
        final String format = formatOf(ctx);
        final List<PlanOperator> operators = new ArrayList<>();
        // A plan is asked for and nothing is carried out: what a relation calls to learn its shape runs no
        // procedure while this compiles (see planning).
        PLANNING.get()[0]++;
        try {
            final PlanBuilder builder = new PlanBuilder(executor);
            final PlanNode plan;
            if (ctx.explainedStatement() != null) {
                plan = explained(executor, ctx.explainedStatement(), builder);
            } else {
                compile(executor, ctx);
                plan = ctx.selectStatement() != null ? builder.query(ctx.selectStatement())
                    : builder.dml(ctx.dmlStatement());
            }
            number(plan, null, 0, new int[1], operators, new IdentityHashMap<PlanNode, PlanOperator>());
        } finally {
            PLANNING.get()[0]--;
        }
        if ("TEXT".equals(format)) {
            return content(text(operators));
        }
        if ("JSON".equals(format)) {
            return content(json(operators));
        }
        return tabular(operators);
    }

    /** The format USING names, upper-cased; TABULAR without one. Any word but the three is refused. */
    private static String formatOf(final FrostlakeParser.ExplainStatementContext ctx) {
        if (ctx.identifier() == null) {
            return "TABULAR";
        }
        final String word = ParseTreeText.getIdentifier(ctx.identifier());
        final String upper = word.toUpperCase(Locale.ROOT);
        if ("TABULAR".equals(upper) || "TEXT".equals(upper) || "JSON".equals(upper)) {
            return upper;
        }
        final Token explain = ctx.EXPLAIN().getSymbol();
        throw new RuntimeException(SqlCompilationError.at(explain.getLine(), explain.getCharPositionInLine() + 4,
            " Invalid explain plan format '" + word + "'"));
    }

    /**
     * Compile the statement explained, so a name it cannot resolve is refused as the statement refuses it. A query
     * holding a dynamic PIVOT — IN (ANY) or IN (a subquery) — reads its values to know its columns, as the account's
     * planner does: its rows are read, and a fault only a row raises is no refusal of the plan (live-verified). A
     * refusal is placed in the EXPLAIN statement.
     */
    private static void compile(final QueryExecutor executor, final FrostlakeParser.ExplainStatementContext ctx) {
        if (ctx.selectStatement() != null) {
            compileStatementQuery(executor, ctx.selectStatement());
            return;
        }
        // An INSERT, an UPDATE, a DELETE and a MERGE compile as they do before they read a row — every refusal of
        // theirs is the plan's, at the statement's own place (live-verified); their executors stop there while a
        // plan is asked for (see planning).
        final FrostlakeParser.DmlStatementContext dml = ctx.dmlStatement();
        if (dml.insertStatement() != null) {
            executor.executeInsertFromContext(dml.insertStatement());
        } else if (dml.updateStatement() != null) {
            executor.executeUpdateFromContext(dml.updateStatement());
        } else if (dml.deleteStatement() != null) {
            executor.executeDeleteFromContext(dml.deleteStatement());
        } else if (dml.mergeStatement() != null) {
            executor.executeMergeFromContext(dml.mergeStatement());
        }
    }

    /**
     * The plan of a statement that is neither a query nor a DML statement, compiled as the account compiles it for its
     * plan and with nothing it would do done (live-verified). A DDL statement is judged as {@link
     * ExplainedStatementChecks} judges it and the query it is made from compiled; a session variable's values are
     * compiled; a SHOW, a DESCRIBE and a LIST, which change nothing, run and their answer is dropped. A CREATE TABLE …
     * AS SELECT and a materialized view are planned over their query (see {@link PlanBuilder#created}); every other
     * statement, and a create IF NOT EXISTS of a name that is taken, is a bare Result.
     */
    private static PlanNode explained(final QueryExecutor executor,
                                      final FrostlakeParser.ExplainedStatementContext explained,
                                      final PlanBuilder builder) {
        final FrostlakeParser.DdlStatementContext ddl = explained.ddlStatement();
        if (ddl != null) {
            if (!ExplainedStatementChecks.compile(executor, ddl)) {
                return PlanBuilder.bareResult();
            }
            final FrostlakeParser.CreateStatementContext create = ddl.createStatement();
            final FrostlakeParser.SelectStatementContext query = create == null ? null : createdQuery(create);
            if (query == null) {
                return PlanBuilder.bareResult();
            }
            compileStatementQuery(executor, query);
            return create.MATERIALIZED() == null && create.VIEW() != null ? PlanBuilder.bareResult()
                : builder.created(createdName(executor, create), query, create.MATERIALIZED() != null);
        }
        if (explained.sessionSetStatement() != null) {
            for (final FrostlakeParser.BooleanExprContext value : setValues(explained.sessionSetStatement())) {
                compileValue(executor, value);
            }
            return PlanBuilder.bareResult();
        }
        if (explained.showStatement() != null || explained.showClassStatement() != null
                || explained.describeStatement() != null || explained.listStatement() != null) {
            final Token start = explained.getStart();
            executor.executeFromUnrecorded(ParseTreeText.getOriginalText(explained),
                new SourcePosition(start.getLine(), start.getCharPositionInLine()));
        }
        return PlanBuilder.bareResult();
    }

    /** The query a CREATE makes a table, a view or a materialized view from; null for any other CREATE. */
    private static FrostlakeParser.SelectStatementContext createdQuery(
            final FrostlakeParser.CreateStatementContext create) {
        final boolean table = create.TABLE() != null && create.DYNAMIC() == null && create.EVENT() == null
            && create.TEMPLATE() == null;
        return table || create.VIEW() != null ? create.selectStatement() : null;
    }

    /**
     * The object a CREATE TABLE … AS SELECT or a materialized view makes, named in full as its plan names it: placed
     * by the session where the statement names no database or schema, and named with the account's locator first
     * where the statement names its database (live-verified).
     */
    private static String createdName(final QueryExecutor executor,
                                      final FrostlakeParser.CreateStatementContext create) {
        final Catalog catalog = executor.getCatalog();
        final String full = SqlIdentifiers.spellAlreadyCanonicalPath(
            catalog.qualifiedExactName(executor.resolveObjectName(create.objectName())));
        return executor.resolveObjectNameParts(create.objectName()).length >= 3
            ? catalog.getAccountLocator() + "." + full : full;
    }

    /** The values a session variable's SET assigns. */
    private static List<FrostlakeParser.BooleanExprContext> setValues(
            final FrostlakeParser.SessionSetStatementContext set) {
        if (set.booleanExprList() != null) {
            return set.booleanExprList().booleanExpr();
        }
        return Collections.singletonList(set.booleanExpr());
    }

    /**
     * Compile a value a statement holds, as an EXPLAIN compiles it: read as the one item of a query, its names
     * resolved and nothing it reads run, a refusal placed where the value stands in its statement.
     */
    private static void compileValue(final QueryExecutor executor, final ParserRuleContext value) {
        final Token start = value.getStart();
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(start.getLine(), start.getCharPositionInLine() - VALUE_QUERY.length()));
        try {
            executor.resolveRelationShape(VALUE_QUERY + ParseTreeText.getOriginalText(value), null);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Compile the query a statement holds, as an EXPLAIN compiles it — its names are resolved and nothing it reads
     * runs — a refusal placed where the query stands in its statement: after an EXPLAIN's first word, at the start
     * of a query refused for its FOR UPDATE.
     *
     * @param executor the executor that compiles the query
     * @param query    the query, as its statement holds it
     */
    static void compileQuery(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query) {
        PLANNING.get()[0]++;
        try {
            compileStatementQuery(executor, query);
        } finally {
            PLANNING.get()[0]--;
        }
    }

    private static void compileStatementQuery(final QueryExecutor executor,
                                              final FrostlakeParser.SelectStatementContext query) {
        final String text = ParseTreeText.getOriginalText(query);
        final Token start = query.getStart();
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(start.getLine(), start.getCharPositionInLine()));
        try {
            if (holdsDynamicPivot(query)) {
                executor.resolveRelationColumns(text, null);
            } else {
                executor.resolveRelationShape(text, null);
            }
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** Whether a tree holds a PIVOT whose values are read: IN (ANY) or IN (a subquery). */
    private static boolean holdsDynamicPivot(final ParseTree node) {
        if (node instanceof FrostlakeParser.PivotInListContext) {
            final FrostlakeParser.PivotInListContext values = (FrostlakeParser.PivotInListContext) node;
            return values.ANY() != null || values.selectStatement() != null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsDynamicPivot(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Numbers a plan's operators from the top, each before the operators under it, as the account lists them. An
     * operator a plan does not list takes a number all the same, and the operators under it stand no deeper for it.
     * Where a recursive term reads its own common table expression no operator is numbered: the operator over it is
     * listed among the RecursiveWithClause's parents instead.
     */
    private static void number(final PlanNode node, final Integer parent, final int depth, final int[] next,
                               final List<PlanOperator> into, final Map<PlanNode, PlanOperator> numbered) {
        if (node.isUntold()) {
            final PlanOperator recursion = node.getLoop() == null ? null : numbered.get(node.getLoop());
            if (recursion != null && parent != null) {
                recursion.feedsBack(parent.intValue());
            }
            return;
        }
        final int id = next[0]++;
        if (!node.isHidden()) {
            final Long held = !node.isScan() ? null : Long.valueOf(node.getRows().longValue() > 0 ? 1 : 0);
            final Long assigned = held == null || !node.isUnread() ? held : Long.valueOf(0);
            final PlanOperator operator = new PlanOperator(id, parent, depth, node.getOperation(), node.getObjects(),
                node.getAlias(), node.getExpressions(), held, assigned);
            if (node.isCountedTwice()) {
                operator.countedTwice();
            }
            into.add(operator);
            numbered.put(node, operator);
        }
        for (final PlanNode child : node.getChildren()) {
            number(child, Integer.valueOf(id), node.isHidden() ? depth : depth + 1, next, into, numbered);
        }
    }

    private static ResultSet tabular(final List<PlanOperator> operators) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("step", NUMBER_9, null),
            new ResultSetColumn("id", NUMBER_9, null),
            new ResultSetColumn("parentOperators", StringType.VARCHAR, null),
            new ResultSetColumn("operation", StringType.VARCHAR, null),
            new ResultSetColumn("objects", StringType.VARCHAR, null),
            new ResultSetColumn("alias", StringType.VARCHAR, null),
            new ResultSetColumn("expressions", StringType.VARCHAR, null),
            new ResultSetColumn("partitionsTotal", NumericType.NUMBER, null),
            new ResultSetColumn("partitionsAssigned", NumericType.NUMBER, null),
            new ResultSetColumn("bytesAssigned", NumericType.NUMBER, null));
        final List<Row> rows = new ArrayList<>();
        final long[] scanned = partitionsOf(operators);
        rows.add(new Row(Arrays.<Object>asList(null, null, null, "GlobalStats", null, null, null,
            Long.valueOf(scanned[0]), Long.valueOf(scanned[1]), Long.valueOf(scanned[1] * PARTITION_BYTES))));
        for (final PlanOperator operator : operators) {
            final Long assigned = operator.getPartitionsAssigned();
            rows.add(new Row(Arrays.<Object>asList(Long.valueOf(1), Long.valueOf(operator.getId()),
                operator.getParent() == null ? null : "[" + operator.getParent()
                    + (operator.getLoopParent() == null ? "" : ", " + operator.getLoopParent()) + "]",
                operator.getOperation(),
                operator.getObjects().isEmpty() ? null : String.join(", ", operator.getObjects()),
                operator.getAlias(),
                operator.getExpressions().isEmpty() ? null : String.join(", ", operator.getExpressions()),
                operator.getPartitionsTotal(), assigned,
                assigned == null ? null : Long.valueOf(assigned.longValue() * PARTITION_BYTES))));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * The partitions of the tables a plan's scans read and those the scans are assigned: one for each scan of a table
     * that holds rows, and none assigned to a scan that reads no column.
     */
    private static long[] partitionsOf(final List<PlanOperator> operators) {
        final long[] partitions = new long[2];
        for (final PlanOperator operator : operators) {
            if (operator.getPartitionsTotal() != null) {
                partitions[0] += operator.getPartitionsTotal().longValue() * operator.counted();
                partitions[1] += operator.getPartitionsAssigned().longValue() * operator.counted();
            }
        }
        return partitions;
    }

    private static ResultSet content(final String plan) {
        return new ResultSet(Collections.singletonList(new ResultSetColumn("content", StringType.VARCHAR, null)),
            Collections.singletonList(new Row(Arrays.<Object>asList(plan))));
    }

    /**
     * The TEXT plan: the GlobalStats block, then one line per operator, indented five places a level: its objects,
     * with the alias they are read {@code as}, its expressions, and a scan's partitions.
     */
    private static String text(final List<PlanOperator> operators) {
        final long[] scanned = partitionsOf(operators);
        final StringBuilder plan = new StringBuilder("GlobalStats:\n    partitionsTotal=" + scanned[0]
            + "\n    partitionsAssigned=" + scanned[1] + "\n    bytesAssigned=" + scanned[1] * PARTITION_BYTES
            + "\nOperations:\n");
        for (final PlanOperator operator : operators) {
            plan.append("1:").append(operator.getId());
            for (int i = 0; i < 5 * (operator.getDepth() + 1); i++) {
                plan.append(' ');
            }
            plan.append("->").append(operator.getOperation());
            if (!operator.getObjects().isEmpty()) {
                plan.append("  ").append(String.join(", ", operator.getObjects()));
            }
            if (operator.getAlias() != null) {
                plan.append(" as ").append(operator.getAlias());
            }
            if (!operator.getExpressions().isEmpty()) {
                plan.append("  ").append(String.join(", ", operator.getExpressions()));
            }
            if (operator.getPartitionsTotal() != null) {
                plan.append("  {partitionsTotal=").append(operator.getPartitionsTotal())
                    .append(", partitionsAssigned=").append(operator.getPartitionsAssigned())
                    .append(", bytesAssigned=")
                    .append(operator.getPartitionsAssigned().longValue() * PARTITION_BYTES).append("}\n");
            } else {
                plan.append("  \n");
            }
        }
        return plan.toString();
    }

    /** The JSON plan: the GlobalStats object, then the operators as one step's list. */
    private static String json(final List<PlanOperator> operators) {
        final long[] scanned = partitionsOf(operators);
        final StringBuilder plan = new StringBuilder("{\"GlobalStats\":{\"partitionsTotal\":" + scanned[0]
            + ",\"partitionsAssigned\":" + scanned[1] + ",\"bytesAssigned\":" + scanned[1] * PARTITION_BYTES
            + "},\"Operations\":[[");
        for (int i = 0; i < operators.size(); i++) {
            final PlanOperator operator = operators.get(i);
            plan.append(i > 0 ? "," : "").append("{\"id\":").append(operator.getId())
                .append(",\"operation\":").append(quoted(operator.getOperation()));
            if (!operator.getObjects().isEmpty()) {
                plan.append(",\"objects\":[");
                final List<String> objects = operator.getObjects();
                for (int o = 0; o < objects.size(); o++) {
                    plan.append(o > 0 ? "," : "").append(quoted(objects.get(o)));
                }
                plan.append(']');
            }
            if (!operator.getExpressions().isEmpty()) {
                plan.append(",\"expressions\":[");
                final List<String> expressions = operator.getExpressions();
                for (int e = 0; e < expressions.size(); e++) {
                    plan.append(e > 0 ? "," : "").append(quoted(expressions.get(e)));
                }
                plan.append(']');
            }
            if (operator.getAlias() != null) {
                plan.append(",\"alias\":").append(quoted(operator.getAlias()));
            }
            if (operator.getPartitionsTotal() != null) {
                plan.append(",\"partitionsAssigned\":").append(operator.getPartitionsAssigned())
                    .append(",\"partitionsTotal\":").append(operator.getPartitionsTotal())
                    .append(",\"bytesAssigned\":")
                    .append(operator.getPartitionsAssigned().longValue() * PARTITION_BYTES);
            }
            if (operator.getParent() != null) {
                plan.append(",\"parentOperators\":[").append(operator.getParent())
                    .append(operator.getLoopParent() == null ? "" : "," + operator.getLoopParent()).append(']');
            }
            plan.append('}');
        }
        return plan.append("]]}").toString();
    }

    /** A JSON string literal. */
    private static String quoted(final String text) {
        final StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                quoted.append('\\').append(c);
            } else if (c == '\n') {
                quoted.append("\\n");
            } else if (c == '\t') {
                quoted.append("\\t");
            } else if (c < 0x20) {
                quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
