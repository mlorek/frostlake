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
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A subquery over a set operation, a FROM-less select or a VALUES list, and a set operation standing alone, as a
 * refusal echoes the plan the account holds for it (live-verified):
 *
 * <pre>
 *   (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT 2))
 *       (SELECT COUNT(*) AS "COUNT(*)" FROM (SELECT null AS "CNULL" FROM (SELECT null AS "CNULL" FROM TABLE
 *       (GENERATOR(ROWCOUNT =&gt; 1, rowCount =&gt; 1))) WB$0 UNION ALL (SELECT null AS "CNULL" FROM TABLE
 *       (GENERATOR(ROWCOUNT =&gt; 1, rowCount =&gt; 1))) WB$1) values)
 *   (SELECT MAX(x) FROM (SELECT 1 AS x UNION ALL SELECT 2))
 *       (SELECT MAX("values".X) AS "MAX(X)" FROM (SELECT SET_COMBINE(1, 2) AS "X" FROM (SELECT 1 AS "X" FROM
 *       TABLE (GENERATOR(…))) WB$0 UNION ALL (SELECT 2 AS "2" FROM TABLE (GENERATOR(…))) WB$1) values)
 *   (SELECT COUNT(*) FROM (SELECT 1))           (SELECT COUNT(*) AS "COUNT(*)" FROM (VALUES (null)) DUAL)
 *   (SELECT COUNT(*) FROM (VALUES (1), (2)))    (SELECT COUNT(*) AS "COUNT(*)" FROM (VALUES (1), (2)) "VALUES")
 * </pre>
 *
 * <p>The set operation goes by {@code values} unless the query names it, each branch by {@code WB$n}. A branch the
 * outer query reads no column of is {@code null AS "CNULL"} over its rows: a FROM-less branch's one row and a whole
 * table's rows a GENERATOR of their count, a filtered table as it stands. A column read is the branches' constants
 * combined by SET_COMBINE, each branch naming its own; a UNION without ALL is that union grouped, GROUP BY 1, whatever
 * the outer query reads. COUNT, MIN and MAX read the column by the set operation's name, SUM adds it as its NUMBER.
 * An INTERSECT, an EXCEPT, a branch beyond these shapes and anything but these aggregates are not printed here.
 */
final class SetOperationPlanText {

    /** One row, as the plan reads a FROM-less branch. */
    static final String ONE_ROW = "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))";

    /** The name a set operation goes by where its query gives it none. */
    private static final String SET_OPERATION = "values";

    private SetOperationPlanText() {
    }

    /**
     * A set operation of FROM-less constants standing as the subquery itself:
     * {@code (SELECT SET_COMBINE(1, 2) AS "1" FROM (SELECT 1 AS "1" FROM TABLE (GENERATOR(…))) WB$0 UNION ALL …)}.
     *
     * @param query the subquery
     * @return the text in parentheses, or null where it is no such set operation
     */
    static String combined(final FrostlakeParser.SelectStatementContext query) {
        if (query.withClause() != null || query.orderByClause() != null || query.limitClause() != null
                || query.fetchClause() != null) {
            return null;
        }
        final String combined = combinedConstants(query, null);
        return combined == null ? null : "(" + combined + ")";
    }

    /**
     * A select of COUNT, MIN, MAX and SUM over a set operation, a FROM-less select or a VALUES list.
     *
     * @param executor  the executor that reads the tables a branch names
     * @param query     the subquery
     * @param select    its select clause
     * @param reference its one FROM item
     * @return the text in parentheses, or null where it is not known
     */
    static String overDerived(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query,
                              final FrostlakeParser.SelectClauseContext select,
                              final FrostlakeParser.TableReferenceContext reference) {
        if (query.orderByClause() != null || query.limitClause() != null || ParseTreeText.getWhereClause(select) != null
                || select.groupByClause() != null || select.havingClause() != null || reference.LATERAL() != null) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null) {
            return null;
        }
        final FrostlakeParser.NonJoinKeywordIdentifierContext bare = reference.nonJoinKeywordIdentifier();
        final String written = reference.aliasName() != null
            ? ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT)
            : bare != null && bare.identifier() != null
                ? ParseTreeText.getIdentifier(bare.identifier()).toUpperCase(Locale.ROOT) : null;
        final String inner;
        final String name;
        final Set<String> read = new HashSet<>();
        if (source.selectStatement() != null && source.selectStatement().selectOperand().size() > 1) {
            name = written != null ? written : SET_OPERATION;
            final String qualifier = written != null ? written : "\"" + SET_OPERATION + "\"";
            final String planned = planned(executor, select, qualifier, source.selectStatement());
            if (planned != null) {
                return "(SELECT " + planned + ") " + name + ")";
            }
            final String items = items(select, qualifier, read, source.selectStatement());
            inner = items == null ? null : setOperation(executor, source.selectStatement(), read);
            return inner == null ? null : "(SELECT " + items + " FROM (" + inner + ") " + name + ")";
        }
        if (source.selectStatement() != null && fromlessAlone(source.selectStatement())) {
            inner = "VALUES (null)";
            name = written != null ? written : "DUAL";
        } else if (source.VALUES() != null && source.identifier() == null) {
            inner = valuesList(source.valueTupleList());
            name = written != null ? written : "\"VALUES\"";
        } else {
            return null;
        }
        final String items = items(select, name, read, null);
        // A column of the one row or the list would fold; COUNT(*) alone is echoed.
        return items == null || inner == null || !read.isEmpty() ? null
            : "(SELECT " + items + " FROM (" + inner + ") " + name + ")";
    }

    /**
     * A select over a set operation of the shapes SetOperationPlanNode prints: its items and {@code FROM (} the plan,
     * up to the name it goes by; null where the select or a node of the set operation is not one of them, or where the
     * set operation's rows alone are read and its branches are counted as the plan counts them (see setOperation).
     */
    private static String planned(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext select,
                                  final String qualifier, final FrostlakeParser.SelectStatementContext derived) {
        final Set<String> read = new HashSet<>();
        if (items(select, qualifier, read, null) == null && read.isEmpty() && !onlyCounts(select)) {
            return null;
        }
        if (read.isEmpty() && setOperation(executor, derived, read) != null) {
            return null;
        }
        final SetOperationPlanNode plan = SetOperationPlanNode.of(executor, derived);
        if (plan == null) {
            return null;
        }
        final StringBuilder text = new StringBuilder();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            final FrostlakeParser.ExprItemContext expr = (FrostlakeParser.ExprItemContext) item;
            final String alias = SelectItemAccessors.getItemAlias(expr);
            final String named = alias != null ? alias
                : ParseTreeText.getOriginalText(expr.booleanExpr()).toUpperCase(Locale.ROOT);
            final String computed = plannedItem(valueOf(expr.booleanExpr()), qualifier, plan);
            if (computed == null) {
                return null;
            }
            text.append(text.length() > 0 ? ", " : "").append(computed).append(" AS \"").append(named).append('"');
        }
        final String inner = plan.text(executor, read.isEmpty());
        return inner == null ? null : text + " FROM (" + inner;
    }

    /** Whether every item of a select is COUNT(*). */
    private static boolean onlyCounts(final FrostlakeParser.SelectClauseContext select) {
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final ParseTree value = valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr());
            if (!"COUNT(*)".equals(computed(value, "", new HashSet<String>(), null))) {
                return false;
            }
        }
        return true;
    }

    /**
     * An item over a planned set operation: COUNT(*), and COUNT, MIN, MAX and SUM of one of its columns, a column
     * holding one value read as that value - {@code MAX(1)}, {@code COUNT(1)} (live-verified); null for anything else.
     */
    private static String plannedItem(final ParseTree value, final String qualifier, final SetOperationPlanNode plan) {
        if (value instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return computed(value, qualifier, new HashSet<String>(), null);
        }
        final Set<String> read = new HashSet<>();
        final String computed = computed(value, qualifier, read, null);
        final String function = value instanceof FrostlakeParser.FunctionCallExprContext
            ? ((FrostlakeParser.FunctionCallExprContext) value).functionName().getText().toUpperCase(Locale.ROOT)
            : null;
        if (read.size() != 1 || function == null || computed == null && !"SUM".equals(function)) {
            return null;
        }
        final String column = read.iterator().next();
        for (int c = 0; c < plan.width(); c++) {
            if (!column.equalsIgnoreCase(plan.label(c))) {
                continue;
            }
            if (plan.constant(c) != null) {
                return "SUM".equals(function) ? null : function + "(" + plan.constant(c) + ")";
            }
            final String reading = qualifier + "." + SetOperationPlanNode.reference(plan.label(c));
            if (!"SUM".equals(function)) {
                return function + "(" + reading + ")";
            }
            return plan.type(c) == null ? null : "SUM(CAST(" + reading + " AS " + plan.type(c) + "))";
        }
        return null;
    }

    /**
     * The select's items as the plan computes them, each named by its text: COUNT(*), and COUNT, MIN, MAX and SUM of
     * one column read by {@code qualifier}, the columns read landing in {@code read}; null for anything else.
     */
    private static String items(final FrostlakeParser.SelectClauseContext select, final String qualifier,
                                final Set<String> read, final FrostlakeParser.SelectStatementContext derived) {
        final StringBuilder text = new StringBuilder();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext expr = (FrostlakeParser.ExprItemContext) item;
            final String alias = SelectItemAccessors.getItemAlias(expr);
            final String named = alias != null ? alias
                : ParseTreeText.getOriginalText(expr.booleanExpr()).toUpperCase(Locale.ROOT);
            final String computed = computed(valueOf(expr.booleanExpr()), qualifier, read, derived);
            if (computed == null) {
                return null;
            }
            text.append(text.length() > 0 ? ", " : "").append(computed).append(" AS \"").append(named).append('"');
        }
        return text.toString();
    }

    private static String computed(final ParseTree value, final String qualifier, final Set<String> read,
                                   final FrostlakeParser.SelectStatementContext derived) {
        if (value instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star =
                (FrostlakeParser.FunctionCallStarExprContext) value;
            return star.functionName() != null && "COUNT".equalsIgnoreCase(star.functionName().getText())
                && star.DISTINCT() == null && star.starQualifiedName() == null && star.starArgumentModifier() == null
                ? "COUNT(*)" : null;
        }
        if (!(value instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) value;
        final String function = call.functionName().getText().toUpperCase(Locale.ROOT);
        if (!"COUNT".equals(function) && !"MIN".equals(function) && !"MAX".equals(function)
                && !"SUM".equals(function) || call.DISTINCT() != null || call.overClause() != null
                || call.functionArgList() == null || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).booleanExpr() == null) {
            return null;
        }
        final ParseTree argument = valueOf(call.functionArgList().functionArg(0).booleanExpr());
        if (!(argument instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) argument).qualifiedName());
        if (parts.length != 1) {
            return null;
        }
        final String column = parts[0].toUpperCase(Locale.ROOT);
        read.add(column);
        final String reading = qualifier + "." + column;
        if (!"SUM".equals(function)) {
            return function + "(" + reading + ")";
        }
        final String type = derived == null ? null : combinedType(derived);
        return type == null ? null : "SUM(CAST(" + reading + " AS " + type + "))";
    }

    /** A set operation as the plan holds it beneath a select reading {@code read} of it; null where not known. */
    private static String setOperation(final QueryExecutor executor,
                                       final FrostlakeParser.SelectStatementContext derived, final Set<String> read) {
        if (derived.withClause() != null || derived.orderByClause() != null || derived.limitClause() != null
                || derived.fetchClause() != null) {
            return null;
        }
        final Boolean distinct = unionKind(derived);
        if (distinct == null) {
            return null;
        }
        if (!read.isEmpty() || distinct.booleanValue()) {
            return combinedConstants(derived, read);
        }
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < derived.selectOperand().size(); i++) {
            final String branch = countedBranch(executor, derived.selectOperand(i));
            if (branch == null) {
                return null;
            }
            text.append(i == 0 ? "SELECT null AS \"CNULL\" FROM (" : " UNION ALL (").append(branch).append(") WB$")
                .append(i);
        }
        return text.toString();
    }

    /**
     * Whether a set operation is a UNION without ALL (TRUE) or a UNION ALL (FALSE) throughout; null for any other, a
     * mix of the two or a UNION BY NAME.
     */
    private static Boolean unionKind(final FrostlakeParser.SelectStatementContext derived) {
        Boolean distinct = null;
        for (final FrostlakeParser.SetOperatorContext operator : derived.setOperator()) {
            if (operator.UNION() == null || operator.BY() != null) {
                return null;
            }
            final Boolean kind = Boolean.valueOf(operator.ALL() == null);
            if (distinct != null && !distinct.equals(kind)) {
                return null;
            }
            distinct = kind;
        }
        return distinct;
    }

    /**
     * A branch whose rows alone are read: {@code SELECT null AS "CNULL"} over them.
     *
     * @param executor the executor that reads the table a branch names
     * @param operand  the branch
     * @return the text, without parentheses, or null where not known
     */
    static String countedBranch(final QueryExecutor executor,
                                        final FrostlakeParser.SelectOperandContext operand) {
        final FrostlakeParser.SelectClauseContext branch = operand.selectClause();
        if (branch == null || branch.DISTINCT() != null || branch.topClause() != null
                || branch.groupByClause() != null || branch.havingClause() != null || branch.qualifyClause() != null
                || branch.connectByClause() != null) {
            return null;
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(branch);
        if (branch.tableExpression() == null) {
            return where == null ? "SELECT null AS \"CNULL\" FROM " + ONE_ROW : null;
        }
        if (branch.tableExpression().tableReference().size() != 1 || !branch.tableExpression().joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = branch.tableExpression().tableReference(0);
        if (reference.tableSource() == null || reference.tableSource().tableQualifiedName() == null
                || reference.tableSource().timeTravelClause() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.sampleClause() != null
                || reference.matchRecognizeClause() != null) {
            return null;
        }
        final String written = ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName());
        final Table table;
        try {
            table = executor.getCatalog().resolveTable(written);
        } catch (final RuntimeException noTable) {
            return null;
        }
        if (table == null || !table.isCatalogResident()) {
            return null;
        }
        if (where == null) {
            // A whole table's rows are as many rows of a GENERATOR, by its statistics.
            final long rows = executor.statisticsRowsFor(executor.getFullyQualifiedTableName(written)).size();
            return "SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => " + rows + ", rowCount => " + rows
                + "))";
        }
        final FrostlakeParser.NonJoinKeywordIdentifierContext bare = reference.nonJoinKeywordIdentifier();
        final String relation = reference.aliasName() != null
            ? ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT)
            : bare != null && bare.identifier() != null
                ? ParseTreeText.getIdentifier(bare.identifier()).toUpperCase(Locale.ROOT)
                : table.getName().toUpperCase(Locale.ROOT);
        final Map<String, Table> relations = new LinkedHashMap<>();
        relations.put(relation, table);
        final List<Table> tables = new ArrayList<>();
        tables.add(table);
        final ExpressionEvaluator printer =
            new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        printer.setMultiTableContext(relations, tables);
        printer.setEnclosingQualifiers(new HashSet<String>());
        return "SELECT null AS \"CNULL\" FROM " + table.getName().toUpperCase(Locale.ROOT) + " AS " + relation
            + " WHERE " + printer.boundPlannedText(ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(where.booleanExpr())));
    }

    /**
     * A UNION of FROM-less branches of one number each, combined: {@code SELECT SET_COMBINE(1, 2) AS "X" FROM
     * (SELECT 1 AS "X" FROM …) WB$0 UNION ALL (SELECT 2 AS "2" FROM …) WB$1}, grouped where the UNION has no ALL;
     * null for any other, or where the select around reads a column the first branch does not name.
     */
    private static String combinedConstants(final FrostlakeParser.SelectStatementContext derived,
                                            final Set<String> read) {
        final Boolean distinct = unionKind(derived);
        if (distinct == null) {
            return null;
        }
        final List<String> values = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : derived.selectOperand()) {
            final FrostlakeParser.ExprItemContext item = constantItem(operand);
            if (item == null) {
                return null;
            }
            values.add(ParseTreeText.getOriginalText(item.booleanExpr()));
            final String alias = SelectItemAccessors.getItemAlias(item);
            names.add(alias != null ? alias.toUpperCase(Locale.ROOT)
                : ParseTreeText.getOriginalText(item.booleanExpr()).toUpperCase(Locale.ROOT));
        }
        if (read != null) {
            for (final String column : read) {
                if (!column.equals(names.get(0))) {
                    return null;
                }
            }
        }
        // One value every branch holds stands for their combination: SELECT 1 UNION SELECT 1 is 1 (live-verified).
        boolean same = true;
        for (final String value : values) {
            same &= value.equals(values.get(0));
        }
        final StringBuilder text = new StringBuilder("SELECT ")
            .append(same ? values.get(0) : "SET_COMBINE(" + String.join(", ", values) + ")")
            .append(" AS \"").append(names.get(0)).append("\" FROM ");
        for (int i = 0; i < values.size(); i++) {
            text.append(i == 0 ? "(" : " UNION ALL (").append("SELECT ").append(values.get(i)).append(" AS \"")
                .append(names.get(i)).append("\" FROM ").append(ONE_ROW).append(") WB$").append(i);
        }
        return distinct.booleanValue() ? text.append(" GROUP BY 1").toString() : text.toString();
    }

    /** The one item of a FROM-less branch that selects a whole number written out, or null. */
    private static FrostlakeParser.ExprItemContext constantItem(final FrostlakeParser.SelectOperandContext operand) {
        final FrostlakeParser.SelectClauseContext branch = operand.selectClause();
        if (branch == null || branch.tableExpression() != null || ParseTreeText.getWhereClause(branch) != null
                || branch.DISTINCT() != null || branch.topClause() != null || branch.groupByClause() != null
                || branch.havingClause() != null || branch.qualifyClause() != null
                || branch.selectList().selectItem().size() != 1
                || !(branch.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.ExprItemContext item =
            (FrostlakeParser.ExprItemContext) branch.selectList().selectItem(0);
        final ParseTree value = valueOf(item.booleanExpr());
        return value instanceof FrostlakeParser.LiteralExprContext && wholeNumber(value.getText()) ? item : null;
    }

    /** The NUMBER a UNION of whole numbers written out holds: as many digits as its widest, or null for another. */
    private static String combinedType(final FrostlakeParser.SelectStatementContext derived) {
        int digits = 0;
        for (final FrostlakeParser.SelectOperandContext operand : derived.selectOperand()) {
            final FrostlakeParser.ExprItemContext item = constantItem(operand);
            if (item == null) {
                return null;
            }
            digits = Math.max(digits, ParseTreeText.getOriginalText(item.booleanExpr()).length());
        }
        return digits == 0 || digits > 38 ? null : "NUMBER(" + digits + ",0)";
    }

    /** Whether a single FROM-less select stands alone as the derived table. */
    private static boolean fromlessAlone(final FrostlakeParser.SelectStatementContext derived) {
        if (derived.withClause() != null || derived.orderByClause() != null || derived.limitClause() != null
                || derived.fetchClause() != null || derived.selectOperand().size() != 1) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = derived.selectOperand(0).selectClause();
        return select != null && select.tableExpression() == null && ParseTreeText.getWhereClause(select) == null
            && select.groupByClause() == null && select.havingClause() == null && select.qualifyClause() == null;
    }

    /** A VALUES list of numbers written out as the plan prints it, {@code VALUES (1), (2)}; null for any other. */
    private static String valuesList(final FrostlakeParser.ValueTupleListContext list) {
        if (list == null) {
            return null;
        }
        final StringBuilder text = new StringBuilder("VALUES ");
        for (int i = 0; i < list.valueTuple().size(); i++) {
            final StringBuilder tuple = new StringBuilder();
            for (final FrostlakeParser.BooleanExprContext cell : list.valueTuple(i).valueList().booleanExpr()) {
                final ParseTree value = valueOf(cell);
                if (!(value instanceof FrostlakeParser.LiteralExprContext) || !wholeNumber(value.getText())) {
                    return null;
                }
                tuple.append(tuple.length() > 0 ? ", " : "").append(value.getText());
            }
            text.append(i > 0 ? ", (" : "(").append(tuple).append(')');
        }
        return text.toString();
    }

    private static boolean wholeNumber(final String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
