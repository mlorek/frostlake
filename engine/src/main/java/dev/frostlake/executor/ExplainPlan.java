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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            compile(executor, ctx);
            final PlanBuilder builder = new PlanBuilder(executor);
            number(ctx.selectStatement() != null ? builder.query(ctx.selectStatement())
                : builder.dml(ctx.dmlStatement()), null, 0, new int[1], operators);
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

    /** Compile the statement explained, so a name it cannot resolve is refused as the statement refuses it. */
    private static void compile(final QueryExecutor executor, final FrostlakeParser.ExplainStatementContext ctx) {
        if (ctx.selectStatement() != null) {
            executor.resolveRelationShape(ParseTreeText.getOriginalText(ctx.selectStatement()), null);
            return;
        }
        final FrostlakeParser.ObjectNameContext target = dmlTarget(ctx.dmlStatement());
        if (target != null) {
            executor.getCatalog().resolveTableAsWritten(executor.resolveObjectName(target), "Table");
        }
    }

    /** The table a DML statement writes, or null for a statement this does not read a target from. */
    private static FrostlakeParser.ObjectNameContext dmlTarget(final FrostlakeParser.DmlStatementContext dml) {
        if (dml.insertStatement() != null) {
            return dml.insertStatement().objectName();
        }
        if (dml.updateStatement() != null) {
            return dml.updateStatement().objectName();
        }
        if (dml.deleteStatement() != null) {
            return dml.deleteStatement().objectName();
        }
        return null;
    }

    /**
     * Numbers a plan's operators from the top, each before the operators under it, as the account lists them. An
     * operator a plan does not list takes a number all the same, and the operators under it stand no deeper for it.
     */
    private static void number(final PlanNode node, final Integer parent, final int depth, final int[] next,
                               final List<PlanOperator> into) {
        final int id = next[0]++;
        if (!node.isHidden()) {
            final Long held = !node.isScan() ? null : Long.valueOf(node.getRows().longValue() > 0 ? 1 : 0);
            into.add(new PlanOperator(id, parent, depth, node.getOperation(), node.getObjects(), node.getAlias(),
                node.getExpressions(), held, held == null || !node.isUnread() ? held : Long.valueOf(0)));
        }
        for (final PlanNode child : node.getChildren()) {
            number(child, Integer.valueOf(id), node.isHidden() ? depth : depth + 1, next, into);
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
                operator.getParent() == null ? null : "[" + operator.getParent() + "]", operator.getOperation(),
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
                partitions[0] += operator.getPartitionsTotal().longValue();
                partitions[1] += operator.getPartitionsAssigned().longValue();
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
                plan.append(",\"parentOperators\":[").append(operator.getParent()).append(']');
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
                quoted.append(String.format("\\u%04x", (int) c));
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
