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
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A node of the plan the account holds for a set operation, as a refusal echoes it (all live-verified; see
 * SetOperationPlanText): a branch - a FROM-less select of literals, or a select of one table's columns, a table of
 * one row read as its values - or a UNION ALL, a UNION, an INTERSECT or an EXCEPT over the nodes beneath it.
 *
 * <p>Branches are numbered from 0, the nodes beneath an operation before its own branches, each branch a {@code WB$n}
 * and an operation standing as a branch an {@code OB$n}. A UNION ALL whose rows alone are read is
 * {@code null AS "CNULL"} over each branch; one whose column is read, and a UNION, combines the branches' values by
 * SET_COMBINE, one value they all hold standing for it, a UNION grouped by it. An INTERSECT is its left side SEMI
 * JOINed to its right on each column's equality and grouped by its columns, a side of another type than the other's
 * cast to the type both take; an EXCEPT is its left side grouped by its columns, the right one left out. A column that
 * holds one value is read as that value: {@code (SELECT MAX(x) FROM (SELECT 1 AS x INTERSECT SELECT 1))} is
 * {@code MAX(1)}.
 */
final class SetOperationPlanNode {

    private final SetOperationKind kind;
    private final FrostlakeParser.SelectOperandContext operand;
    private final List<SetOperationPlanNode> children = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> constants = new ArrayList<>();
    private final List<DataType> types = new ArrayList<>();
    private final List<String> items = new ArrayList<>();
    private final List<String> casts = new ArrayList<>();
    private String source;
    private int index = -1;

    private SetOperationPlanNode(final SetOperationKind kind, final FrostlakeParser.SelectOperandContext operand) {
        this.kind = kind;
        this.operand = operand;
    }

    /**
     * The plan of a set operation, its nodes numbered.
     *
     * @param executor the executor whose catalog and statistics the branches read
     * @param query    the set operation
     * @return the plan, or null where a node of it is not one of these shapes
     */
    static SetOperationPlanNode of(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query) {
        if (query.selectOperand().size() < 2 || query.orderByClause() != null || query.limitClause() != null
                || query.fetchClause() != null || query.withClause() != null) {
            return null;
        }
        final SetOperationPlanNode root = node(executor, SetOperationTree.of(query));
        if (root == null) {
            return null;
        }
        number(root, new int[] {0});
        return root;
    }

    private static SetOperationPlanNode node(final QueryExecutor executor, final SetOperationTree tree) {
        if (tree.isLeaf()) {
            final FrostlakeParser.SelectOperandContext operand = tree.operand();
            if (operand.selectStatement() != null) {
                final FrostlakeParser.SelectStatementContext inner = operand.selectStatement();
                if (inner.selectOperand().size() > 1) {
                    return of(executor, inner);
                }
                return inner.withClause() != null || inner.orderByClause() != null || inner.limitClause() != null
                    || inner.fetchClause() != null ? null : branch(executor, inner.selectOperand(0));
            }
            return branch(executor, operand);
        }
        final FrostlakeParser.SetOperatorContext operator = tree.operator();
        if (operator.ALL() != null && operator.UNION() == null || operator.BY() != null) {
            return null;
        }
        final SetOperationKind kind = operator.UNION() != null
            ? operator.ALL() != null ? SetOperationKind.UNION_ALL : SetOperationKind.UNION
            : operator.INTERSECT() != null ? SetOperationKind.INTERSECT : SetOperationKind.EXCEPT;
        final SetOperationPlanNode left = node(executor, tree.left());
        final SetOperationPlanNode right = node(executor, tree.right());
        if (left == null || right == null || left.width() != right.width()) {
            return null;
        }
        final SetOperationPlanNode node = new SetOperationPlanNode(kind, null);
        if (left.kind == kind && (kind == SetOperationKind.UNION_ALL || kind == SetOperationKind.UNION)
                && !tree.left().isLeaf()) {
            node.children.addAll(left.children);
        } else {
            node.children.add(left);
        }
        node.children.add(right);
        return node.columns() ? node : null;
    }

    /** A select standing as a branch, or null where it is no shape a branch is printed in. */
    private static SetOperationPlanNode branch(final QueryExecutor executor,
                                               final FrostlakeParser.SelectOperandContext operand) {
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select == null || select.DISTINCT() != null || select.topClause() != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.connectByClause() != null || select.intoClause() != null) {
            return null;
        }
        final SetOperationPlanNode branch = new SetOperationPlanNode(SetOperationKind.BRANCH, operand);
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (select.tableExpression() == null) {
            if (where != null) {
                return null;
            }
            for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
                final ParseTree value = item instanceof FrostlakeParser.ExprItemContext
                    ? valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
                final DataType type = value instanceof FrostlakeParser.LiteralExprContext
                    ? literalType(executor, (FrostlakeParser.LiteralExprContext) value) : null;
                if (type == null) {
                    return null;
                }
                final String written = ParseTreeText.getOriginalText((FrostlakeParser.LiteralExprContext) value);
                final String alias = SelectItemAccessors.getItemAlias(item);
                branch.add(alias != null ? alias : written.toUpperCase(Locale.ROOT), written, type, written);
            }
            branch.source = SetOperationPlanText.ONE_ROW;
            return branch;
        }
        return tableBranch(executor, select, where, branch);
    }

    /** A select of one table's columns: by its name and alias, or as one row of its values where it holds one. */
    private static SetOperationPlanNode tableBranch(final QueryExecutor executor,
                                                    final FrostlakeParser.SelectClauseContext select,
                                                    final FrostlakeParser.WhereClauseContext where,
                                                    final SetOperationPlanNode branch) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null
                || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.sampleClause() != null || reference.matchRecognizeClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length == 1 && DerivedRelationPins.cteNamed(reference, parts[0]) != null) {
            return null;
        }
        final Table table;
        try {
            table = executor.getCatalog().resolveTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        } catch (final RuntimeException noTable) {
            return null;
        }
        if (table == null || !table.isCatalogResident()) {
            return null;
        }
        final String relation = GeneratorBounds.qualifierOf(reference);
        final List<Row> rows = executor.statisticsRowsFor(executor.getFullyQualifiedTableName(
            ParseTreeText.getQualifiedName(source.tableQualifiedName())));
        final boolean oneRow = rows.size() == 1;
        if (oneRow && where != null) {
            return null;
        }
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            final ParseTree value = item instanceof FrostlakeParser.ExprItemContext
                ? valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
            if (!(value instanceof FrostlakeParser.QualifiedNameExprContext)) {
                return null;
            }
            final String[] named = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
            final String column = named[named.length - 1];
            if (named.length > 2 || named.length == 2 && !named[0].toUpperCase(Locale.ROOT).equals(relation)
                    || !table.hasColumn(column)) {
                return null;
            }
            final String declared = table.getColumn(column).getName();
            final String alias = SelectItemAccessors.getItemAlias(item);
            final String label = alias != null ? alias : declared.toUpperCase(Locale.ROOT);
            final DataType type = table.getColumn(column).getDataType();
            if (oneRow) {
                final Object held = rows.get(0).getValue(table.getColumnIndex(declared));
                if (!(held instanceof Number)) {
                    return null;
                }
                final String constant = DecimalText.parse(held.toString()).toPlainString();
                branch.add(label, constant, type, constant);
            } else {
                branch.add(label, null, type, relation + "." + declared.toUpperCase(Locale.ROOT));
            }
        }
        if (oneRow) {
            branch.source = SetOperationPlanText.ONE_ROW;
            return branch;
        }
        branch.source = table.getName().toUpperCase(Locale.ROOT) + " AS " + relation;
        if (where != null) {
            final Map<String, Table> relations = new LinkedHashMap<>();
            relations.put(relation, table);
            final List<Table> tables = new ArrayList<>();
            tables.add(table);
            final ExpressionEvaluator printer =
                new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            printer.setMultiTableContext(relations, tables);
            printer.setEnclosingQualifiers(new HashSet<String>());
            branch.source += " WHERE " + printer.boundPlannedText(ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(where.booleanExpr())));
        }
        return branch;
    }

    private void add(final String label, final String constant, final DataType type, final String item) {
        labels.add(label);
        constants.add(constant);
        types.add(type);
        items.add(item);
        casts.add(null);
    }

    /** An operation's columns from its children's: false where their types do not combine. */
    private boolean columns() {
        final SetOperationPlanNode first = children.get(0);
        for (int c = 0; c < first.width(); c++) {
            String constant = first.constants.get(c);
            DataType type = first.types.get(c);
            if (kind == SetOperationKind.UNION_ALL || kind == SetOperationKind.UNION) {
                for (int i = 1; i < children.size(); i++) {
                    final SetOperationPlanNode child = children.get(i);
                    constant = constant != null && constant.equals(child.constants.get(c)) ? constant : null;
                    type = combinedType(type, child.types.get(c));
                }
            } else if (kind == SetOperationKind.INTERSECT) {
                type = combinedType(type, children.get(1).types.get(c));
            }
            if (type == null) {
                return false;
            }
            labels.add(first.labels.get(c));
            constants.add(constant);
            types.add(type);
            items.add(null);
            casts.add(null);
        }
        return true;
    }

    /** Numbers the branches beneath a node: the operations beneath it first, then its own children in order. */
    private static void number(final SetOperationPlanNode node, final int[] next) {
        for (final SetOperationPlanNode child : node.children) {
            if (child.kind != SetOperationKind.BRANCH) {
                number(child, next);
            }
        }
        for (final SetOperationPlanNode child : node.children) {
            child.index = next[0]++;
        }
    }

    /** @return how many columns the node answers */
    int width() {
        return labels.size();
    }

    /** @return the name the node's column at {@code column} answers to */
    String label(final int column) {
        return labels.get(column);
    }

    /** @return the one value the column holds, as the plan writes it, or null */
    String constant(final int column) {
        return constants.get(column);
    }

    /** @return the column's type as the plan writes it, NUMBER(p,s) for a number, or null for another */
    String type(final int column) {
        return typeText(types.get(column));
    }

    /**
     * The node's text as the plan holds it beneath a select.
     *
     * @param executor the executor whose statistics a counted branch reads
     * @param counted  whether the select reads the node's rows alone, no column of it
     * @return the text, without its parentheses, or null where it is not printed here
     */
    String text(final QueryExecutor executor, final boolean counted) {
        switch (kind) {
            case BRANCH:
                return branchText();
            case UNION_ALL:
                return counted ? countedText(executor) : combinedText(false);
            case UNION:
                return combinedText(true);
            case INTERSECT:
                return intersectText(executor);
            default:
                return exceptText(executor);
        }
    }

    private String branchText() {
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int c = 0; c < width(); c++) {
            final String value = constants.get(c) != null ? constants.get(c)
                : casts.get(c) != null ? "CAST(" + items.get(c) + " AS " + casts.get(c) + ")" : items.get(c);
            text.append(c > 0 ? ", " : "").append(value).append(" AS \"").append(labels.get(c)).append('"');
        }
        return text.append(" FROM ").append(source).toString();
    }

    /** A UNION ALL whose rows alone are read: {@code null AS "CNULL"} over each branch. */
    private String countedText(final QueryExecutor executor) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            final SetOperationPlanNode child = children.get(i);
            final String branch;
            if (child.kind == SetOperationKind.BRANCH) {
                branch = SetOperationPlanText.countedBranch(executor, child.operand);
            } else {
                final String inner = child.text(executor, false);
                branch = inner == null ? null
                    : "SELECT null AS \"CNULL\" FROM (" + inner + ") OB$" + child.index;
            }
            if (branch == null) {
                return null;
            }
            text.append(i == 0 ? "SELECT null AS \"CNULL\" FROM (" : " UNION ALL (").append(branch).append(") WB$")
                .append(child.index);
        }
        return text.toString();
    }

    /** A UNION, with or without ALL, of branches' values combined: {@code SET_COMBINE(WB$0.X, 4) AS "X"}. */
    private String combinedText(final boolean grouped) {
        if (width() != 1) {
            return null;
        }
        final List<String> combined = new ArrayList<>();
        final StringBuilder branches = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            final SetOperationPlanNode child = children.get(i);
            if (child.kind != SetOperationKind.BRANCH) {
                return null;
            }
            combined.add(child.constants.get(0) != null ? child.constants.get(0)
                : "WB$" + child.index + "." + reference(child.labels.get(0)));
            branches.append(i == 0 ? "(" : " UNION ALL (").append(child.branchText()).append(") WB$")
                .append(child.index);
        }
        final String value = constants.get(0) != null ? constants.get(0)
            : "SET_COMBINE(" + String.join(", ", combined) + ")";
        final String text = "SELECT " + value + " AS \"" + labels.get(0) + "\" FROM " + branches;
        return grouped ? text + " GROUP BY " + key(0) : text;
    }

    /** An INTERSECT: the left side SEMI JOINed to the right on each column's equality, grouped by its columns. */
    private String intersectText(final QueryExecutor executor) {
        final SetOperationPlanNode left = children.get(0);
        final SetOperationPlanNode right = children.get(1);
        for (int c = 0; c < width(); c++) {
            if (!sameType(left.types.get(c), right.types.get(c))) {
                if (left.kind != SetOperationKind.BRANCH || right.kind != SetOperationKind.BRANCH
                        || typeText(types.get(c)) == null) {
                    return null;
                }
                left.casts.set(c, typeText(types.get(c)));
                right.casts.set(c, typeText(types.get(c)));
            }
        }
        final String leftAlias = "WB$" + left.index;
        final String leftText = inputText(executor, left, true);
        final String rightText = inputText(executor, right, false);
        if (leftText == null || rightText == null) {
            return null;
        }
        final String rightAlias = (right.kind == SetOperationKind.BRANCH ? "WB$" : "OB$") + right.index;
        final List<String> conditions = new ArrayList<>();
        for (int c = 0; c < width(); c++) {
            conditions.add("(" + leftAlias + "." + reference(left.labels.get(c)) + " = " + rightAlias + "."
                + reference(right.labels.get(c)) + ")");
        }
        final String on = conditions.size() == 1 ? conditions.get(0) : "(" + String.join(" AND ", conditions) + ")";
        return "SELECT " + outputItems(leftAlias) + " FROM " + leftText + " SEMI JOIN " + rightText + " ON " + on
            + " GROUP BY " + keys();
    }

    /**
     * A side of an INTERSECT: a branch as itself, an operation on the left under a select of its columns, an
     * operation on the right as itself.
     */
    private static String inputText(final QueryExecutor executor, final SetOperationPlanNode side, final boolean left) {
        if (side.kind == SetOperationKind.BRANCH) {
            return "(" + side.branchText() + ") WB$" + side.index;
        }
        final String inner = side.text(executor, false);
        if (inner == null) {
            return null;
        }
        return left ? "(SELECT " + side.outputItems("OB$" + side.index) + " FROM (" + inner + ") OB$" + side.index
            + ") WB$" + side.index : "(" + inner + ") OB$" + side.index;
    }

    /** An EXCEPT: its left side grouped by its columns, the right one left out. */
    private String exceptText(final QueryExecutor executor) {
        final SetOperationPlanNode left = children.get(0);
        if (left.kind == SetOperationKind.BRANCH) {
            final String alias = "WB$" + left.index;
            return "SELECT " + outputItems(alias) + " FROM (" + left.branchText() + ") " + alias + " GROUP BY "
                + keys();
        }
        final String inner = left.text(executor, false);
        if (inner == null) {
            return null;
        }
        final String nested = "OB$" + left.index;
        boolean passes = true;
        for (int c = 0; c < width(); c++) {
            passes &= left.constants.get(c) == null;
        }
        if (passes) {
            return "SELECT " + outputItems(nested) + " FROM (" + inner + ") " + nested + " GROUP BY " + keys();
        }
        final String alias = "WB$" + left.index;
        return "SELECT " + outputItems(alias) + " FROM (SELECT " + left.outputItems(nested) + " FROM (" + inner + ") "
            + nested + ") " + alias + " GROUP BY " + keys();
    }

    /** The node's columns read from a relation so named: each one value it holds, else its column. */
    private String outputItems(final String relation) {
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < width(); c++) {
            final String value = constants.get(c) != null ? constants.get(c)
                : relation + "." + reference(labels.get(c));
            text.append(c > 0 ? ", " : "").append(value).append(" AS \"").append(labels.get(c)).append('"');
        }
        return text.toString();
    }

    private String keys() {
        final List<String> keys = new ArrayList<>();
        for (int c = 0; c < width(); c++) {
            keys.add(key(c));
        }
        return String.join(", ", keys);
    }

    /** A grouping key: the one value a column holds, else its name as written bare. */
    private String key(final int column) {
        return constants.get(column) != null ? constants.get(column) : labels.get(column);
    }

    /**
     * A column's name as a qualified read writes it: bare where it could be written so, else between quotes.
     *
     * @param label the name
     * @return the text
     */
    static String reference(final String label) {
        return SqlIdentifiers.spellCanonical(label);
    }

    /** The type a number or a text written out is planned as, by the literal's own typing; null for another. */
    private static DataType literalType(final QueryExecutor executor,
                                        final FrostlakeParser.LiteralExprContext literal) {
        final FrostlakeParser.LiteralContext written = literal.literal();
        if (written.INTEGER_LITERAL() == null && written.FLOAT_LITERAL() == null
                && written.STRING_LITERAL() == null) {
            return null;
        }
        try {
            return new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .inferStaticType(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(literal)));
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    /** A type as the plan writes it: NUMBER(p,s) for an exact number; null for an approximate one or a DECFLOAT. */
    private static String typeText(final DataType type) {
        if (exactNumber(type)) {
            final NumericType number = (NumericType) type;
            return "NUMBER(" + number.getPrecision() + "," + number.getScale() + ")";
        }
        return type == null || type instanceof NumericType ? null : type.toString();
    }

    private static boolean exactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type);
    }

    private static boolean sameType(final DataType first, final DataType second) {
        final String one = typeText(first);
        return one != null && one.equals(typeText(second));
    }

    /** The type two columns take together: a NUMBER wide enough for both, or the one both hold; null for another. */
    private static DataType combinedType(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return null;
        }
        if (sameType(first, second)) {
            return first;
        }
        if (!exactNumber(first) || !exactNumber(second)) {
            return null;
        }
        final NumericType one = (NumericType) first;
        final NumericType other = (NumericType) second;
        final int scale = Math.max(one.getScale(), other.getScale());
        final int whole = Math.max(one.getPrecision() - one.getScale(), other.getPrecision() - other.getScale());
        return new NumericType("NUMBER", Math.min(38, whole + scale), scale);
    }

    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
