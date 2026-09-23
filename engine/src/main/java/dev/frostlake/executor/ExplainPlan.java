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
import org.antlr.v4.runtime.tree.ParseTree;

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
 * <p>Any other word after USING is refused. The operators are this engine's own reading of the statement — a query
 * with no FROM reads a Generator, as the account's does — since no optimizer here reproduces the account's.
 */
final class ExplainPlan {

    private static final NumericType NUMBER_9 = new NumericType("NUMBER", 9, 0);

    private ExplainPlan() {
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
        compile(executor, ctx);
        final List<PlanOperator> operators = ctx.selectStatement() != null
            ? queryOperators(executor, ctx.selectStatement(), 0, null, 0, new ArrayList<PlanOperator>())
            : dmlOperators(executor, ctx.dmlStatement());
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
     * A query's operators: a Result over what the query reads — a Generator with no FROM, else, from the top, a Limit,
     * a Sort, an Aggregate, a Filter and a Join where the query has each, over a scan of every table it names.
     */
    private static List<PlanOperator> queryOperators(final QueryExecutor executor,
                                                     final FrostlakeParser.SelectStatementContext query,
                                                     final int firstId, final Integer parent, final int depth,
                                                     final List<PlanOperator> into) {
        int id = firstId;
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        into.add(new PlanOperator(id, parent, depth, "Result", null, null,
            select == null ? Collections.<String>emptyList() : itemTexts(select)));
        final int resultId = id++;
        if (select == null || select.FROM() == null) {
            into.add(new PlanOperator(id, resultId, depth + 1, "Generator", null, null, Arrays.asList("1")));
            return into;
        }
        int scanParent = resultId;
        int scanDepth = depth + 1;
        final List<String> stages = new ArrayList<>();
        final List<List<String>> stageExpressions = new ArrayList<>();
        if (query.limitClause() != null || query.fetchClause() != null) {
            stages.add("Limit");
            stageExpressions.add(Collections.<String>emptyList());
        }
        if (query.orderByClause() != null) {
            stages.add("Sort");
            stageExpressions.add(Arrays.asList(ParseTreeText.getOriginalText(query.orderByClause())));
        }
        if (select.groupByClause() != null) {
            stages.add("Aggregate");
            stageExpressions.add(Arrays.asList(ParseTreeText.getOriginalText(select.groupByClause())));
        }
        if (select.whereClause() != null) {
            stages.add("Filter");
            stageExpressions.add(Arrays.asList(ParseTreeText.getOriginalText(select.whereClause().booleanExpr())));
        }
        if (!select.tableExpression().joinClause().isEmpty()) {
            stages.add("Join");
            stageExpressions.add(Collections.<String>emptyList());
        }
        for (int i = 0; i < stages.size(); i++) {
            into.add(new PlanOperator(id, scanParent, scanDepth, stages.get(i), null, null, stageExpressions.get(i)));
            scanParent = id++;
            scanDepth++;
        }
        for (final FrostlakeParser.TableSourceContext source : scannedTables(select.tableExpression())) {
            final String written = ParseTreeText.getQualifiedName(source.tableQualifiedName());
            final FrostlakeParser.TableReferenceContext reference =
                (FrostlakeParser.TableReferenceContext) source.getParent();
            final String alias = reference.aliasName() != null ? ParseTreeText.getIdentifier(reference.aliasName())
                : reference.nonJoinKeywordIdentifier() != null
                    ? reference.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT) : null;
            into.add(new PlanOperator(id++, scanParent, scanDepth, "TableScan", qualified(executor, written), alias,
                Collections.<String>emptyList()));
        }
        return into;
    }

    /** A DML statement's operators: a Result counting the rows, the write, and what it reads. */
    private static List<PlanOperator> dmlOperators(final QueryExecutor executor,
                                                   final FrostlakeParser.DmlStatementContext dml) {
        final List<PlanOperator> operators = new ArrayList<>();
        final FrostlakeParser.ObjectNameContext target = dmlTarget(dml);
        final String objects = target == null ? null : qualified(executor, executor.resolveObjectName(target));
        if (dml.insertStatement() != null) {
            operators.add(new PlanOperator(0, null, 0, "Result", null, null, Arrays.asList("number of rows inserted")));
            operators.add(new PlanOperator(1, 0, 1, "Insert", objects, null, Collections.<String>emptyList()));
            final FrostlakeParser.InsertStatementContext insert = dml.insertStatement();
            if (insert.valueTupleList() != null) {
                operators.add(new PlanOperator(2, 1, 2, "ValuesClause", null, null,
                    Arrays.asList(ParseTreeText.getOriginalText(insert.valueTupleList()))));
            } else {
                queryOperators(executor, insert.selectStatement(), 2, 1, 2, operators);
            }
            return operators;
        }
        final String counted = dml.updateStatement() != null ? "number of rows updated"
            : dml.deleteStatement() != null ? "number of rows deleted" : "number of rows loaded";
        operators.add(new PlanOperator(0, null, 0, "Result", null, null, Arrays.asList(counted)));
        final String write = dml.updateStatement() != null ? "Update" : dml.deleteStatement() != null ? "Delete"
            : dml.mergeStatement() != null ? "Merge" : "Copy";
        operators.add(new PlanOperator(1, 0, 1, write, objects, null, Collections.<String>emptyList()));
        if (objects != null) {
            operators.add(new PlanOperator(2, 1, 2, "TableScan", objects, null, Collections.<String>emptyList()));
        }
        return operators;
    }

    /** The select list's items as written. */
    private static List<String> itemTexts(final FrostlakeParser.SelectClauseContext select) {
        final List<String> texts = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            texts.add(ParseTreeText.getOriginalText(item));
        }
        return texts;
    }

    /** Every table a FROM clause reads by name, in the order written, outside nested queries. */
    private static List<FrostlakeParser.TableSourceContext> scannedTables(final ParseTree node) {
        final List<FrostlakeParser.TableSourceContext> found = new ArrayList<>();
        collectTables(node, found);
        return found;
    }

    private static void collectTables(final ParseTree node, final List<FrostlakeParser.TableSourceContext> found) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null
                && node.getParent() instanceof FrostlakeParser.TableReferenceContext) {
            found.add((FrostlakeParser.TableSourceContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectTables(node.getChild(i), found);
        }
    }

    /** A table's name in full, as a plan names the object it reads; the name as written when it cannot be placed. */
    private static String qualified(final QueryExecutor executor, final String written) {
        try {
            return executor.getCatalog().qualifiedObjectName(written);
        } catch (final RuntimeException unplaced) {
            return written.toUpperCase(Locale.ROOT);
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
        rows.add(new Row(Arrays.<Object>asList(null, null, null, "GlobalStats", null, null, null,
            Long.valueOf(0), Long.valueOf(0), Long.valueOf(0))));
        for (final PlanOperator operator : operators) {
            rows.add(new Row(Arrays.<Object>asList(Long.valueOf(1), Long.valueOf(operator.getId()),
                operator.getParent() == null ? null : "[" + operator.getParent() + "]", operator.getOperation(),
                operator.getObjects(), operator.getAlias(),
                operator.getExpressions().isEmpty() ? null : String.join(", ", operator.getExpressions()),
                null, null, null)));
        }
        return new ResultSet(columns, rows);
    }

    private static ResultSet content(final String plan) {
        return new ResultSet(Collections.singletonList(new ResultSetColumn("content", StringType.VARCHAR, null)),
            Collections.singletonList(new Row(Arrays.<Object>asList(plan))));
    }

    /** The TEXT plan: the GlobalStats block, then one line per operator, indented five places a level. */
    private static String text(final List<PlanOperator> operators) {
        final StringBuilder plan = new StringBuilder("GlobalStats:\n    partitionsTotal=0\n    partitionsAssigned=0\n"
            + "    bytesAssigned=0\nOperations:\n");
        for (final PlanOperator operator : operators) {
            plan.append("1:").append(operator.getId());
            for (int i = 0; i < 5 * (operator.getDepth() + 1); i++) {
                plan.append(' ');
            }
            plan.append("->").append(operator.getOperation());
            if (operator.getObjects() != null) {
                plan.append("  ").append(operator.getObjects());
            }
            if (operator.getAlias() != null) {
                plan.append("  ").append(operator.getAlias());
            }
            if (!operator.getExpressions().isEmpty()) {
                plan.append("  ").append(String.join(", ", operator.getExpressions()));
            }
            plan.append("  \n");
        }
        return plan.toString();
    }

    /** The JSON plan: the GlobalStats object, then the operators as one step's list. */
    private static String json(final List<PlanOperator> operators) {
        final StringBuilder plan = new StringBuilder(
            "{\"GlobalStats\":{\"partitionsTotal\":0,\"partitionsAssigned\":0,\"bytesAssigned\":0},\"Operations\":[[");
        for (int i = 0; i < operators.size(); i++) {
            final PlanOperator operator = operators.get(i);
            plan.append(i > 0 ? "," : "").append("{\"id\":").append(operator.getId())
                .append(",\"operation\":").append(quoted(operator.getOperation()));
            if (operator.getObjects() != null) {
                plan.append(",\"objects\":[").append(quoted(operator.getObjects())).append(']');
            }
            if (operator.getAlias() != null) {
                plan.append(",\"alias\":").append(quoted(operator.getAlias()));
            }
            if (!operator.getExpressions().isEmpty()) {
                plan.append(",\"expressions\":[");
                final List<String> expressions = operator.getExpressions();
                for (int e = 0; e < expressions.size(); e++) {
                    plan.append(e > 0 ? "," : "").append(quoted(expressions.get(e)));
                }
                plan.append(']');
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
