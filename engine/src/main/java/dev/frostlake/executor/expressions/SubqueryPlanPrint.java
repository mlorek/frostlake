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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The re-print a refusal's echo gives a membership test over a SUBQUERY. The account names
 * {@code x IN (SELECT …)} as the comparison it plans, {@code x = ANY(SELECT …)}, and
 * {@code x NOT IN (SELECT …)} as {@code x != ALL(SELECT …)}, and re-prints the SELECT from its plan:
 *
 * <pre>
 *   'b' IN (SELECT 'abc')              'b' = ANY(SELECT 'abc' AS "'ABC'" FROM (VALUES (null)) DUAL)
 *   'b' IN (SELECT g FROM rt r)        'b' = ANY(SELECT R.G AS "G" FROM RT AS R)
 *   … FROM rt, re                      … FROM RT AS RT INNER JOIN RE AS RE
 *   … FROM rt JOIN re ON rt.n = re.i   … FROM RT AS RT INNER JOIN RE AS RE ON (RT.N = RE.I)
 *   … WHERE b AND n > 1                … WHERE (CAST(RT.B AS BOOLEAN)) AND (RT.N > 1)
 *   … GROUP BY g HAVING g = 'x'        … GROUP BY RT.G  HAVING RT.G = 'x'
 *   … ORDER BY g LIMIT 1               …  ORDER BY G ASC NULLS LAST LIMIT 1 OFFSET 0
 *   1 IN (SELECT 1.5)                  (CAST(1 AS NUMBER(2,1))) = ANY(SELECT 1.5 AS "1.5" FROM …)
 *   … FROM VALUES ('a'), ('b')         … FROM (VALUES ('a'), ('b')) "VALUES", read as VALUES.COLUMN1
 *   … FROM TABLE(FLATTEN(x)) f         … FROM TABLE (FLATTEN(1 => X)), read as F.VALUE
 * </pre>
 *
 * <p>Every item is named: by its alias ({@code AS x} is {@code "X"}, {@code AS "q"} keeps its case), a
 * column by its own name, anything else by its source text upper-cased. The clauses come in the plan's
 * order — WHERE, GROUP BY, ORDER BY, HAVING, QUALIFY, LIMIT — with two spaces before ORDER BY and before
 * HAVING. An ORDER BY key naming an output column prints that column's bare name, where a GROUP BY key
 * prints the input column it reads; an ordinal is the output column it counts to. TOP, FETCH and a
 * LIMIT without an OFFSET all print {@code LIMIT n OFFSET m}. The subject and the one item meet in a
 * single type: an untyped NULL item is the subject family's typed null, and of two exact numbers with
 * different scales the lower-scale side is rescaled to the higher scale. In an invalid-type sentence
 * the same re-print speaks that sentence's vocabulary: {@code (VALUES (NULL))},
 * {@code BOOLEAN_TO_ROWINDEX(RT.B)}, {@code FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN])}.
 *
 * <p>Only what was measured is modelled. A set operation, a WITH, a derived table, a NATURAL, USING or
 * ASOF join, a correlated reference, a pair of families that meet through any other conversion and
 * every other shape answer null, and the membership test then prints as written.
 */
final class SubqueryPlanPrint {

    private static final int MAX_PRECISION = 38;
    /** The name the plan gives the relation a NATURAL or USING join makes. */
    private static final String JOIN_RELATION = "SYS_VW";
    /** The name an unaliased derived table goes by in the plan: {@code values}, quoted where a column names it. */
    private static final String DERIVED_RELATION = "values";
    /** The name the FROM-less relation goes by, which a refusal's echo never prints. */
    private static final String FROMLESS_RELATION = "DUMMY";
    /** The name an unaliased VALUES list of the FROM clause goes by, printed quoted after the list. */
    private static final String VALUES_RELATION = "VALUES";

    private final ExpressionEvaluatorVisitor outer;
    private final StrictPrintMode mode;

    /** The relations the FROM clause names, keyed as a refusal's echo qualifies their columns. */
    private final Map<String, Table> aliasToTable = new LinkedHashMap<>();
    private final List<Table> tables = new ArrayList<>();
    /** Each relation's name as a written qualifier carries it: its alias, else its table's name. */
    private final Set<String> qualifiers = new HashSet<>();
    /** A qualifier a reference was written with, upper-cased, and the relation the plan reads it through. */
    private final Map<String, String> qualifierRenames = new HashMap<>();
    /** Each FROM segment's ASOF MATCH_CONDITION, or null, beside {@code segments}. */
    private final List<FrostlakeParser.BooleanExprContext> matches = new ArrayList<>();
    /** Whether the FROM clause joins a relation that may be NULL-extended, so no column is known to hold a value. */
    private boolean outerJoined;
    /** The CTEs the subquery's WITH defines, by name, which a FROM clause reads as derived tables. */
    private final Map<String, FrostlakeParser.SelectStatementContext> ctes = new LinkedHashMap<>();

    SubqueryPlanPrint(final ExpressionEvaluatorVisitor outer, final StrictPrintMode mode) {
        this.outer = outer;
        this.mode = mode;
    }

    /**
     * The membership test as the plan re-prints it, or null when its subquery is a shape this does not
     * model.
     *
     * @param in the membership test, over a subquery
     * @param outerPrinter the printer the test's subject is printed by
     * @return {@code x = ANY(SELECT …)}, {@code x != ALL(SELECT …)}, or null
     */
    String membership(final InExpression in, final StrictMessagePrinter outerPrinter) {
        final QueryExecutor queryExecutor = outer.getQueryExecutor();
        if (queryExecutor == null) {
            return null;
        }
        try {
            final FrostlakeParser.SelectStatementContext written = parsed(in.getSubquery().getSubquery());
            if (written == null || !collectCtes(written)) {
                return null;
            }
            final FrostlakeParser.SelectStatementContext statement = soleStatement(written);
            if (statement == null) {
                return setOperation(written, in, outerPrinter, queryExecutor);
            }
            return planned(statement.selectOperand(0).selectClause(), statement, in, outerPrinter, queryExecutor,
                null, null, true, null);
        } catch (final RuntimeException notModelled) {
            return null;
        }
    }

    /**
     * A subquery re-printed from its plan as a whole SELECT, every item as planned, or null when its shape is
     * not modelled — what a conversion sentence quotes inside {@code ANY(…)}.
     *
     * @param subquery the subquery
     * @return {@code SELECT … AS "…", … FROM …}, or null
     */
    String select(final SubqueryExpression subquery) {
        final QueryExecutor queryExecutor = outer.getQueryExecutor();
        if (queryExecutor == null) {
            return null;
        }
        try {
            final FrostlakeParser.SelectStatementContext written = parsed(subquery.getSubquery());
            if (written == null || !collectCtes(written)) {
                return null;
            }
            final FrostlakeParser.SelectStatementContext statement = soleStatement(written);
            if (statement == null) {
                final List<String> combined = new ArrayList<>();
                final List<String> names = new ArrayList<>();
                final String from = setOperation(written, queryExecutor, names, new ArrayList<DataType>(),
                    new ArrayList<Boolean>(), combined, true);
                if (from == null) {
                    return null;
                }
                final String order = orderText(written.orderByClause(), outputScope(queryExecutor), names);
                final String limit = limitText(written, null);
                return order == null || limit == null ? null : combinedSelect(combined, names, from) + order + limit;
            }
            return planned(statement.selectOperand(0).selectClause(), statement, null, null, queryExecutor, null, null,
                true, null);
        } catch (final RuntimeException notModelled) {
            return null;
        }
    }

    /**
     * A membership test over a set operation of one-column selects, as the plan re-prints it (see
     * {@link #setOperation}): {@code x = ANY(SELECT SET_COMBINE(WB$0.V, WB$1."1") AS "V" FROM …)}, the subject and
     * the combined column meeting in one type as they do over a single select.
     */
    private String setOperation(final FrostlakeParser.SelectStatementContext statement, final InExpression in,
                                final StrictMessagePrinter outerPrinter, final QueryExecutor queryExecutor) {
        final List<String> combined = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final List<DataType> types = new ArrayList<>();
        final String from = setOperation(statement, queryExecutor, names, types, new ArrayList<Boolean>(), combined,
            false);
        if (from == null || names.size() != 1) {
            return null;
        }
        final String[] meeting = meeting(in.getValue(), combined.get(0), types.get(0), false, outerPrinter, outerPrinter);
        if (meeting == null) {
            return null;
        }
        final String text = "SELECT " + meeting[1] + " AS \"" + names.get(0) + "\" FROM " + from;
        return meeting[0] + (in.isNot() ? " != ALL(" : " = ANY(") + text + ")";
    }

    /** {@code SELECT SET_COMBINE(…) AS "<name>", … FROM <from>}. */
    private static String combinedSelect(final List<String> combined, final List<String> names, final String from) {
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int i = 0; i < combined.size(); i++) {
            text.append(i > 0 ? ", " : "").append(combined.get(i)).append(" AS \"").append(names.get(i)).append('"');
        }
        return text.append(" FROM ").append(from).toString();
    }

    /** The numbers the next arm's inner and outer relations take, {@code OB$n} and {@code WB$n}, counted as the plan does. */
    private int innerRelations;
    private int outerRelations;

    /**
     * A set operation as the plan re-prints it — the FROM of its combining SELECT, with each column's
     * {@code SET_COMBINE(…)} in {@code combined}, or null where its shape is not modelled. Each arm is wrapped as
     * {@code (SELECT OB$i.<name> AS "<name>" FROM (<arm>) OB$i) WB$j}, its column cast to the type the arms meet in
     * where its own type differs, {@code CAST(OB$1."1" AS NUMBER(38,0))}, and under UNION wrapped in
     * {@code ENSURE_NULLABLE(…)} where the arm can answer no NULL but the combined column can; an untyped NULL item
     * is the met type's typed null over its read, {@code SYSTEM$NULL_TO_FIXED(OB$0.NULL)}. The chain nests as
     * {@link SetOperationNode} describes, a nested combination being one arm of the one around it, except that a
     * MINUS reads a combination on its left bare, {@code (…) OB$2 MINUS (…) WB$2}. The relations are numbered in
     * the order the plan completes them: every arm's inner relation as it is finished, the combination's own
     * after its arms, and a combination's outer wrappers once all its arms are (live-verified). EXCEPT is spelled
     * MINUS. A chain's own ORDER BY and LIMIT follow the combination when {@code tailed} (the caller prints them),
     * and are not modelled otherwise; UNION BY NAME, an ALL after INTERSECT or MINUS, and arms whose columns meet
     * in no one type are not modelled.
     */
    private String setOperation(final FrostlakeParser.SelectStatementContext statement, final QueryExecutor queryExecutor,
                                final List<String> names, final List<DataType> types, final List<Boolean> nullables,
                                final List<String> combined, final boolean tailed) {
        if (!tailed && (statement.orderByClause() != null || statement.limitClause() != null
                || statement.fetchClause() != null)) {
            return null;
        }
        final SetOperationNode root = setOperationTree(statement);
        if (root == null || root.isLeaf()) {
            return null;
        }
        innerRelations = 0;
        outerRelations = 0;
        return combination(root, queryExecutor, names, types, nullables, combined);
    }

    /** The tree a set-operation statement plans as, or null where an operator or a bracketed operand is not modelled. */
    private SetOperationNode setOperationTree(final FrostlakeParser.SelectStatementContext statement) {
        final List<SetOperationNode> operands = new ArrayList<>();
        for (final FrostlakeParser.SelectOperandContext operand : statement.selectOperand()) {
            final FrostlakeParser.SelectStatementContext bracketed = operand.selectStatement();
            if (bracketed != null && bracketed.selectOperand().size() > 1) {
                if (bracketed.withClause() != null || bracketed.orderByClause() != null
                        || bracketed.limitClause() != null || bracketed.fetchClause() != null) {
                    return null;
                }
                final SetOperationNode nested = setOperationTree(bracketed);
                if (nested == null) {
                    return null;
                }
                operands.add(nested);
            } else {
                operands.add(SetOperationNode.leaf(operand));
            }
        }
        final List<String> operators = new ArrayList<>();
        for (final FrostlakeParser.SetOperatorContext each : statement.setOperator()) {
            final String text = setOperatorText(each);
            if (text == null) {
                return null;
            }
            operators.add(text);
        }
        return SetOperationNode.of(operands, operators);
    }

    /** One combination's FROM, its columns described into the lists; null where not modelled (see {@link #setOperation}). */
    private String combination(final SetOperationNode node, final QueryExecutor queryExecutor, final List<String> names,
                               final List<DataType> types, final List<Boolean> nullables, final List<String> combined) {
        final String operator = node.operator();
        final boolean union = operator.startsWith("UNION");
        final List<String> texts = new ArrayList<>();
        final List<List<String>> armNames = new ArrayList<>();
        final List<List<DataType>> armTypes = new ArrayList<>();
        final List<List<Boolean>> armNullables = new ArrayList<>();
        final List<List<Boolean>> armNulls = new ArrayList<>();
        final List<Integer> inner = new ArrayList<>();
        for (final SetOperationNode arm : node.arms()) {
            final List<String> eachNames = new ArrayList<>();
            final List<DataType> eachTypes = new ArrayList<>();
            final List<Boolean> eachNullables = new ArrayList<>();
            final List<Boolean> eachNulls = new ArrayList<>();
            final String text;
            if (arm.isLeaf()) {
                text = leaf(arm.operand(), queryExecutor, eachNames, eachTypes, eachNullables, eachNulls);
            } else {
                final List<String> armCombined = new ArrayList<>();
                final String from = combination(arm, queryExecutor, eachNames, eachTypes, eachNullables, armCombined);
                text = from == null ? null : combinedSelect(armCombined, eachNames, from);
                for (int i = 0; i < eachNames.size(); i++) {
                    eachNulls.add(Boolean.FALSE);
                }
            }
            if (text == null || !armNames.isEmpty() && eachNames.size() != armNames.get(0).size()) {
                return null;
            }
            texts.add(text);
            armNames.add(eachNames);
            armTypes.add(eachTypes);
            armNullables.add(eachNullables);
            armNulls.add(eachNulls);
            inner.add(Integer.valueOf(innerRelations++));
        }
        final boolean leftCombined = !node.arms().get(0).isLeaf();
        if (leftCombined && SetOperationNode.INTERSECT.equals(operator)) {
            return null;
        }
        final boolean bareLeft = leftCombined && operator.equals("MINUS");
        final List<DataType> met = new ArrayList<>();
        final List<Boolean> metNullable = new ArrayList<>();
        for (int column = 0; column < armNames.get(0).size(); column++) {
            final List<DataType> columnTypes = new ArrayList<>();
            boolean anyNullable = false;
            boolean allNullable = true;
            for (int arm = 0; arm < texts.size(); arm++) {
                if (!armNulls.get(arm).get(column).booleanValue()) {
                    columnTypes.add(armTypes.get(arm).get(column));
                }
                final boolean nullable = armNullables.get(arm).get(column).booleanValue();
                anyNullable = anyNullable || nullable;
                allNullable = allNullable && nullable;
            }
            final DataType type = metType(columnTypes);
            if (type == null) {
                return null;
            }
            met.add(type);
            metNullable.add(Boolean.valueOf(union ? anyNullable
                : operator.equals("MINUS") ? armNullables.get(0).get(column).booleanValue() : allNullable));
        }
        final List<Integer> outers = new ArrayList<>();
        for (int arm = 0; arm < texts.size(); arm++) {
            outers.add(Integer.valueOf(arm == 0 && bareLeft ? -1 : outerRelations++));
        }
        for (int column = 0; column < met.size(); column++) {
            final StringBuilder combine = new StringBuilder("SET_COMBINE(");
            for (int arm = 0; arm < texts.size(); arm++) {
                combine.append(arm > 0 ? ", " : "").append(outers.get(arm).intValue() < 0 ? "OB$" + inner.get(arm)
                    : "WB$" + outers.get(arm)).append('.').append(columnText(armNames.get(arm).get(column)));
            }
            combined.add(combine.append(')').toString());
        }
        final StringBuilder from = new StringBuilder();
        for (int arm = 0; arm < texts.size(); arm++) {
            from.append(arm > 0 ? " " + operator + " " : "");
            if (outers.get(arm).intValue() < 0) {
                from.append('(').append(texts.get(arm)).append(") OB$").append(inner.get(arm));
                continue;
            }
            from.append("(SELECT ");
            for (int column = 0; column < met.size(); column++) {
                final String name = armNames.get(arm).get(column);
                final String read = "OB$" + inner.get(arm) + "." + columnText(name);
                final String metName = SqlTypeNames.canonical(met.get(column));
                String value;
                if (armNulls.get(arm).get(column).booleanValue()) {
                    final String family = StrictMessagePrinter.typedNullFamily(met.get(column));
                    if (family == null) {
                        return null;
                    }
                    value = "SYSTEM$NULL_TO_" + family + "(" + read + ")";
                } else {
                    value = metName.equals(SqlTypeNames.canonical(armTypes.get(arm).get(column))) ? read
                        : "CAST(" + read + " AS " + metName + ")";
                    if (union && metNullable.get(column).booleanValue()
                            && !armNullables.get(arm).get(column).booleanValue()) {
                        value = "ENSURE_NULLABLE(" + value + ")";
                    }
                }
                from.append(column > 0 ? ", " : "").append(value).append(" AS \"").append(name).append('"');
            }
            from.append(" FROM (").append(texts.get(arm)).append(") OB$").append(inner.get(arm)).append(") WB$")
                .append(outers.get(arm));
        }
        names.addAll(armNames.get(0));
        types.addAll(met);
        nullables.addAll(metNullable);
        return from.toString();
    }

    /** One written arm of a set operation, re-printed as its own select, its columns described; null where not modelled. */
    private String leaf(final FrostlakeParser.SelectOperandContext operand, final QueryExecutor queryExecutor,
                        final List<String> names, final List<DataType> types, final List<Boolean> nullables,
                        final List<Boolean> untypedNulls) {
        final FrostlakeParser.SelectStatementContext bracketed =
            operand.selectStatement() == null ? null : soleStatement(operand.selectStatement());
        final FrostlakeParser.SelectClauseContext select = operand.selectClause() != null ? operand.selectClause()
            : bracketed != null && bracketed.withClause() == null ? bracketed.selectOperand(0).selectClause() : null;
        if (select == null) {
            return null;
        }
        final SubqueryPlanPrint arm = new SubqueryPlanPrint(outer, mode);
        arm.ctes.putAll(ctes);
        final List<Expression> items = new ArrayList<>();
        final String text = arm.planned(select, bracketed, null, null, queryExecutor, names, types, true, items);
        if (text == null) {
            return null;
        }
        for (final Expression item : items) {
            nullables.add(Boolean.valueOf(arm.nullable(item)));
            untypedNulls.add(Boolean.valueOf(isUntypedNull(item)));
        }
        return text;
    }

    /**
     * Whether a select item can answer NULL as the plan reads it: a literal other than NULL, a column declared NOT
     * NULL, COUNT and an operator or cast over such operands cannot; everything else — and whatever this does not
     * model — can.
     */
    private boolean nullable(final Expression item) {
        if (item instanceof LiteralExpression) {
            return ((LiteralExpression) item).getType() == LiteralType.NULL;
        }
        if (item instanceof CastExpression) {
            return ((CastExpression) item).isTryMode() || nullable(((CastExpression) item).getExpression());
        }
        if (item instanceof ColumnReferenceExpression) {
            final TableColumn column = outerJoined ? null : columnOf((ColumnReferenceExpression) item);
            return column == null || column.isNullable();
        }
        if (item instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) item;
            switch (operation.getOperator()) {
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case MODULO:
                case CONCAT:
                    return nullable(operation.getLeft()) || nullable(operation.getRight());
                default:
                    return true;
            }
        }
        if (item instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) item).getOperator() == UnaryOperator.NEGATE) {
            return nullable(((UnaryOperationExpression) item).getOperand());
        }
        return !(item instanceof FunctionCallExpression && !((FunctionCallExpression) item).isDistinct()
            && ((FunctionCallExpression) item).getNameExpression() == null
            && "COUNT".equalsIgnoreCase(((FunctionCallExpression) item).getFunctionName()));
    }

    /** The column a reference reads among the select's own relations, or null where none carries it. */
    private TableColumn columnOf(final ColumnReferenceExpression reference) {
        final String qualifier = !reference.isQualified() ? null
            : qualifierRenames.containsKey(reference.getTableName().toUpperCase(Locale.ROOT))
                ? qualifierRenames.get(reference.getTableName().toUpperCase(Locale.ROOT)) : reference.getTableName();
        for (final Map.Entry<String, Table> relation : aliasToTable.entrySet()) {
            final Table table = relation.getValue();
            if ((qualifier == null || relation.getKey().equalsIgnoreCase(qualifier))
                    && table.hasColumn(reference.getColumnName())) {
                return table.getColumns().get(table.getColumnIndex(reference.getColumnName()));
            }
        }
        return null;
    }

    /** UNION, UNION ALL, INTERSECT or MINUS as the plan spells the operator, or null for any other. */
    private static String setOperatorText(final FrostlakeParser.SetOperatorContext operator) {
        if (operator.BY() != null) {
            return null;
        }
        if (operator.UNION() != null) {
            return operator.ALL() != null ? "UNION ALL" : "UNION";
        }
        if (operator.ALL() != null) {
            return null;
        }
        if (operator.INTERSECT() != null) {
            return SetOperationNode.INTERSECT;
        }
        return operator.EXCEPT() != null || operator.MINUS_KW() != null ? "MINUS" : null;
    }

    /**
     * The type set operation arms meet in: the widest string among strings; among numbers FLOAT beside a FLOAT,
     * else the widest integer part at the highest scale, to 38 digits; the one type they share otherwise.
     */
    private static DataType metType(final List<DataType> types) {
        DataType met = null;
        for (final DataType type : types) {
            if (type == null) {
                return null;
            }
            if (met == null) {
                met = type;
            } else if (met instanceof StringType && type instanceof StringType) {
                if (((StringType) type).getMaxLength() > ((StringType) met).getMaxLength()) {
                    met = type;
                }
            } else if (met instanceof NumericType && type instanceof NumericType) {
                if (NumericType.isApproximate(met) || NumericType.isApproximate(type)) {
                    met = NumericType.FLOAT;
                } else {
                    final NumericType a = (NumericType) met;
                    final NumericType b = (NumericType) type;
                    final int scale = Math.max(a.getScale(), b.getScale());
                    final int integerDigits = Math.max(a.getPrecision() - a.getScale(), b.getPrecision() - b.getScale());
                    met = new NumericType("NUMBER", Math.min(MAX_PRECISION, integerDigits + scale), scale);
                }
            } else if (!SqlTypeNames.canonical(met).equals(SqlTypeNames.canonical(type))) {
                return null;
            }
        }
        return met;
    }

    /** A column name as a reference prints it: bare when it prints the same quoted or not, else quoted. */
    private static String columnText(final String name) {
        return isPlainName(name) ? name : "\"" + name + "\"";
    }

    /**
     * The CTEs a statement's WITH defines, recorded by name; false for a RECURSIVE WITH or a CTE with a column
     * list, which are not modelled.
     */
    private boolean collectCtes(final FrostlakeParser.SelectStatementContext statement) {
        final FrostlakeParser.WithClauseContext with = statement.withClause();
        if (with == null) {
            return true;
        }
        if (with.RECURSIVE() != null) {
            return false;
        }
        for (final FrostlakeParser.CteDefinitionContext cte : with.cteDefinition()) {
            if (cte.columnListOptional() != null) {
                return false;
            }
            ctes.put(ParseTreeText.namePartText(cte.nameStartPart()), cte.selectStatement());
        }
        return true;
    }

    /**
     * The re-print of one select, as a membership test over {@code in} or, with no test, as the SELECT alone.
     *
     * @param statement  the statement whose ORDER BY and LIMIT the select carries, or null for a set operation's arm
     * @param names      filled with the items' names when not null
     * @param types      filled with the items' types when not null
     * @param correlates whether a name the select's own relations do not carry reads the query around the test
     * @param itemExpressions filled with the items themselves when not null
     */
    private String planned(final FrostlakeParser.SelectClauseContext select,
                           final FrostlakeParser.SelectStatementContext statement, final InExpression in,
                           final StrictMessagePrinter outerPrinter, final QueryExecutor queryExecutor,
                           final List<String> itemNames, final List<DataType> itemTypes, final boolean correlates,
                           final List<Expression> itemExpressions) {
        if (select.connectByClause() != null) {
            return null;
        }
        final Catalog catalog = queryExecutor.getCatalog();
        final List<String> segments = new ArrayList<>();
        final List<FrostlakeParser.BooleanExprContext> conditions = new ArrayList<>();
        if (!collectFrom(select.tableExpression(), catalog, segments, conditions)) {
            return null;
        }
        final String limit = limitText(statement, select);
        if (limit == null) {
            return null;
        }
        final ExpressionEvaluatorVisitor scope = scope(catalog, queryExecutor);
        final StrictMessagePrinter printer = new StrictMessagePrinter(scope, mode);
        final List<String> bareColumns = new ArrayList<>();
        final List<String> writtenQualifiers = new ArrayList<>();
        printer.watchColumns(bareColumns, writtenQualifiers);
        printer.renameQualifiers(qualifierRenames);
        if (correlates) {
            printer.watchCorrelations(outer, qualifiers);
        }

        final List<Expression> items = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!collectItem(item, items, names)) {
                return null;
            }
        }
        final List<String> printedItems = new ArrayList<>();
        final String[] meeting;
        if (in != null) {
            if (items.size() != 1) {
                return null;
            }
            meeting = meeting(in.getValue(), isUntypedNull(items.get(0)) ? null : items.get(0).accept(printer),
                isUntypedNull(items.get(0)) ? null : itemType(items.get(0), printer, outerPrinter),
                isUntypedNull(items.get(0)), outerPrinter, printer);
            if (meeting == null) {
                return null;
            }
            printedItems.add(meeting[1]);
        } else {
            meeting = null;
            for (final Expression item : items) {
                printedItems.add(item.accept(printer));
            }
        }
        if (itemNames != null) {
            itemNames.addAll(names);
        }
        if (itemExpressions != null) {
            itemExpressions.addAll(items);
        }
        if (itemTypes != null) {
            for (final Expression item : items) {
                itemTypes.add(itemType(item, printer, outerPrinter));
            }
        }

        final StringBuilder from = new StringBuilder();
        if (segments.isEmpty()) {
            from.append("(VALUES (").append(mode == StrictPrintMode.CONVERSION ? "NULL" : "null").append(")) DUAL");
        }
        for (int i = 0; i < segments.size(); i++) {
            from.append(segments.get(i));
            if (matches.get(i) != null) {
                from.append(" MATCH_CONDITION (")
                    .append(printer.conditionText(new ExpressionAstBuilder().build(matches.get(i)), false))
                    .append(')');
            }
            if (conditions.get(i) != null) {
                from.append(" ON (")
                    .append(printer.conditionText(new ExpressionAstBuilder().build(conditions.get(i)), false))
                    .append(')');
            }
        }
        final String where = select.whereClause() == null ? ""
            : " WHERE " + printer.conditionText(new ExpressionAstBuilder().build(select.whereClause().booleanExpr()), false);
        final String group = groupText(select.groupByClause(), items, names, printer);
        if (group == null) {
            return null;
        }
        final String having = select.havingClause() == null ? ""
            : "  HAVING " + printer.conditionText(new ExpressionAstBuilder().build(select.havingClause().booleanExpr()), false);
        final String qualify = select.qualifyClause() == null ? ""
            : " QUALIFY " + printer.conditionText(new ExpressionAstBuilder().build(select.qualifyClause().booleanExpr()), false);
        if (!bareColumns.isEmpty() || !allKnown(writtenQualifiers)) {
            return null;
        }
        final String order = orderText(statement == null ? null : statement.orderByClause(), scope, names);
        if (order == null) {
            return null;
        }

        final StringBuilder text = new StringBuilder("SELECT ");
        if (select.DISTINCT() != null) {
            text.append("DISTINCT ");
        }
        for (int i = 0; i < printedItems.size(); i++) {
            text.append(i > 0 ? ", " : "").append(printedItems.get(i)).append(" AS \"").append(names.get(i)).append('"');
        }
        text.append(" FROM ").append(from)
            .append(where).append(group).append(order).append(having).append(qualify).append(limit);
        return in == null ? text.toString() : meeting[0] + (in.isNot() ? " != ALL(" : " = ANY(") + text + ")";
    }

    /**
     * The subject and the item as they meet in one type, {@code [subject, item]}, or null for a pair of
     * families this does not model.
     */
    private String[] meeting(final Expression subject, final String printedItem, final DataType itemType,
                             final boolean untypedNull, final StrictMessagePrinter outerPrinter,
                             final StrictMessagePrinter printer) {
        final DataType subjectType = outerPrinter.typeOf(subject);
        if (untypedNull) {
            final String family = subjectType instanceof StringType ? "TEXT"
                : isExactNumber(subjectType) ? "FIXED" : null;
            return family == null ? null : new String[] {outerPrinter.operandText(subject),
                "SYSTEM$NULL_TO_" + family + (mode == StrictPrintMode.CONVERSION ? "(NULL)" : "(null)")};
        }
        if (isExactNumber(subjectType) && isExactNumber(itemType)) {
            final NumericType s = (NumericType) subjectType;
            final NumericType t = (NumericType) itemType;
            if (s.getScale() == t.getScale()) {
                return new String[] {outerPrinter.operandText(subject), printedItem};
            }
            final int scale = Math.max(s.getScale(), t.getScale());
            final int integerDigits = Math.max(s.getPrecision() - s.getScale(), t.getPrecision() - t.getScale());
            final String target = "NUMBER(" + Math.min(MAX_PRECISION, integerDigits + scale) + "," + scale + ")";
            return t.getScale() < s.getScale()
                ? new String[] {outerPrinter.operandText(subject), printer.rescaledText(printedItem, target)}
                : new String[] {"(" + outerPrinter.rescaledText(subject.accept(outerPrinter), target) + ")", printedItem};
        }
        if (sameFamily(subjectType, itemType)) {
            return new String[] {outerPrinter.operandText(subject), printedItem};
        }
        return mode == StrictPrintMode.PLAN ? textAndNumber(subject, subjectType, printedItem, itemType, outerPrinter)
            : null;
    }

    /**
     * Text meeting a number, as the plan converts them: with an exact number both sides meet in NUMBER(18,5), the
     * text through {@code TO_NUMBER(x, 18, 5)} and the number through {@code CAST(x AS NUMBER(18,5))}; with a
     * FLOAT the text is cast to FLOAT. A text literal subject that spells a number is typed by its own digits
     * instead ({@link #spelledNumber}), and the two meet in the widest integer part at the highest scale:
     * {@code (TO_NUMBER('5.5', 2, 1)) = ANY(SELECT CAST(5 AS NUMBER(2,1)) …)}, the width left out where it is
     * NUMBER(38,0), {@code TO_NUMBER('5')} (live-verified). No other pair is modelled.
     */
    private String[] textAndNumber(final Expression subject, final DataType subjectType, final String printedItem,
                                   final DataType itemType, final StrictMessagePrinter outerPrinter) {
        final boolean subjectText = subjectType instanceof StringType;
        final boolean itemText = itemType instanceof StringType;
        if (subjectText == itemText) {
            return null;
        }
        final NumericType spelled = subject instanceof LiteralExpression
            && ((LiteralExpression) subject).getType() == LiteralType.STRING
            ? spelledNumber(String.valueOf(((LiteralExpression) subject).getValue())) : null;
        if (spelled != null) {
            if (!isExactNumber(itemType)) {
                return NumericType.isApproximate(itemType)
                    ? new String[] {"(CAST(" + subject.accept(outerPrinter) + " AS FLOAT))", printedItem} : null;
            }
            final NumericType item = (NumericType) itemType;
            final int scale = Math.max(spelled.getScale(), item.getScale());
            final int precision = Math.min(MAX_PRECISION, scale
                + Math.max(spelled.getPrecision() - spelled.getScale(), item.getPrecision() - item.getScale()));
            final String width = precision == MAX_PRECISION && scale == 0 ? "" : ", " + precision + ", " + scale;
            return new String[] {"(TO_NUMBER(" + subject.accept(outerPrinter) + width + "))",
                item.getScale() == scale ? printedItem : "CAST(" + printedItem + " AS NUMBER(" + precision + "," + scale + "))"};
        }
        final String printedSubject = subject.accept(outerPrinter);
        if (subjectText && isExactNumber(itemType)) {
            return new String[] {"(TO_NUMBER(" + printedSubject + ", 18, 5))", "CAST(" + printedItem + " AS NUMBER(18,5))"};
        }
        if (itemText && isExactNumber(subjectType)) {
            return new String[] {"(CAST(" + printedSubject + " AS NUMBER(18,5)))", "TO_NUMBER(" + printedItem + ", 18, 5)"};
        }
        if (subjectText && NumericType.isApproximate(itemType)) {
            return new String[] {"(CAST(" + printedSubject + " AS FLOAT))", printedItem};
        }
        return null;
    }

    /**
     * The number a text literal spells, typed by its own digits as the plan reads it: its value's scale without
     * trailing zeros, at least one integer digit — {@code '5'} NUMBER(1,0), {@code '1.50'} NUMBER(2,1),
     * {@code '.5'} NUMBER(2,1), {@code '1e-3'} NUMBER(4,3), {@code '1e3'} NUMBER(4,0), and past 38 digits the
     * default NUMBER(38,0). Null for a text that spells no number exactly, spaces included, which reads as
     * NUMBER(18,5) like a column.
     */
    private static NumericType spelledNumber(final String text) {
        BigDecimal value;
        try {
            value = new BigDecimal(text).stripTrailingZeros();
        } catch (final NumberFormatException notANumber) {
            return null;
        }
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        final int scale = value.scale();
        final int precision = Math.max(1, value.precision() - scale) + scale;
        return precision > MAX_PRECISION ? new NumericType("NUMBER", MAX_PRECISION, 0)
            : new NumericType("NUMBER", precision, scale);
    }

    /**
     * An item's type: as the select's own scope types it, else — for a column — as the relation registered
     * for it declares it (a derived table's column), else as the query around the subquery types it (a
     * correlated reference).
     */
    private DataType itemType(final Expression item, final StrictMessagePrinter printer,
                              final StrictMessagePrinter outerPrinter) {
        final DataType typed = printer.typeOf(item);
        if (typed != null || !(item instanceof ColumnReferenceExpression)) {
            return typed;
        }
        final ColumnReferenceExpression column = (ColumnReferenceExpression) item;
        final String qualifier = !column.isQualified() ? null
            : qualifierRenames.containsKey(column.getTableName().toUpperCase(Locale.ROOT))
                ? qualifierRenames.get(column.getTableName().toUpperCase(Locale.ROOT)) : column.getTableName();
        for (final Map.Entry<String, Table> relation : aliasToTable.entrySet()) {
            final Table table = relation.getValue();
            if ((qualifier == null || relation.getKey().equalsIgnoreCase(qualifier))
                    && table.hasColumn(column.getColumnName())) {
                return table.getColumns().get(table.getColumnIndex(column.getColumnName())).getDataType();
            }
        }
        return outerPrinter != null ? outerPrinter.typeOf(item) : outer.getQueryExecutor() == null ? null
            : new StrictMessagePrinter(outer, mode).typeOf(item);
    }

    private static boolean sameFamily(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return false;
        }
        if (first instanceof StringType || first instanceof BooleanType) {
            return first.getClass() == second.getClass()
                || (first instanceof StringType && second instanceof StringType);
        }
        final String name = SqlTypeNames.canonical(first);
        return name != null && name.equals(SqlTypeNames.canonical(second));
    }

    /** The FROM clause's relations and joins into {@code segments}, each join's ON beside it; false when not modelled. */
    private boolean collectFrom(final FrostlakeParser.TableExpressionContext from, final Catalog catalog,
                                final List<String> segments,
                                final List<FrostlakeParser.BooleanExprContext> conditions) {
        matches.clear();
        if (from == null) {
            return true;
        }
        if (from.tableReference().size() == 1 && from.joinClause().size() == 1
                && (from.joinClause(0).NATURAL() != null || from.joinClause(0).USING() != null)) {
            return usingJoin(from.tableReference(0), from.joinClause(0), catalog, segments, conditions);
        }
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                final boolean lateral = ((FrostlakeParser.TableReferenceContext) child).LATERAL() != null;
                // A LATERAL table function that leads the FROM clause reads nothing beside it, and prints as a
                // table function does.
                final String relation = lateral && segments.isEmpty()
                        && tableFunctionCall(((FrostlakeParser.TableReferenceContext) child).tableSource()) == null
                    ? null
                    : relation((FrostlakeParser.TableReferenceContext) child, catalog);
                if (relation == null) {
                    return false;
                }
                // A comma join is planned as the inner join it is, with no condition of its own; a LATERAL
                // derived table beside it keeps its comma.
                segments.add(segments.isEmpty() ? relation : (lateral ? ", " : " INNER JOIN ") + relation);
                conditions.add(null);
                matches.add(null);
            } else if (child instanceof FrostlakeParser.JoinClauseContext
                    && !join((FrostlakeParser.JoinClauseContext) child, catalog, segments, conditions,
                        i == from.getChildCount() - 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One JOIN: INNER and CROSS as {@code INNER JOIN}, LEFT and FULL as their OUTER spelling, and a RIGHT
     * join of two relations as the LEFT join of the pair swapped.
     */
    private boolean join(final FrostlakeParser.JoinClauseContext join, final Catalog catalog,
                         final List<String> segments, final List<FrostlakeParser.BooleanExprContext> conditions,
                         final boolean last) {
        if (join.NATURAL() != null || join.DIRECTED() != null || join.LATERAL() != null || join.USING() != null) {
            return false;
        }
        final String relation = relation(join.tableReference(), catalog);
        if (relation == null) {
            return false;
        }
        if (join.ASOF() != null) {
            // MATCH_CONDITION sits between the relation and its ON, printed as the ON is.
            if (join.asofMatchCondition() == null || join.asofMatchCondition().LIMIT() != null) {
                return false;
            }
            segments.add(" ASOF JOIN " + relation);
            matches.add(join.asofMatchCondition().booleanExpr());
            conditions.add(join.booleanExpr());
            return true;
        }
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        // CROSS is a clause of its own now, because it takes no ON and no USING.
        final boolean crossJoin = join.CROSS() != null;
        final FrostlakeParser.BooleanExprContext on = join.booleanExpr();
        if (type != null && type.RIGHT() != null) {
            if (segments.size() != 1 || !last || on == null) {
                return false;
            }
            final String left = segments.get(0);
            segments.set(0, relation);
            segments.add(" LEFT OUTER JOIN " + left);
            outerJoined = true;
            conditions.add(on);
            matches.add(null);
            return true;
        }
        final String keyword;
        if (type == null || type.INNER() != null) {
            keyword = " INNER JOIN ";
        } else if (crossJoin && on == null) {
            keyword = " INNER JOIN ";
        } else if (type.LEFT() != null && on != null) {
            keyword = " LEFT OUTER JOIN ";
            outerJoined = true;
        } else if (type.FULL() != null && on != null) {
            keyword = " FULL OUTER JOIN ";
            outerJoined = true;
        } else {
            return false;
        }
        segments.add(keyword + relation);
        conditions.add(on);
        matches.add(null);
        return true;
    }

    /** One table of the FROM clause, {@code PUBLIC.RT AS R}, registered in the scope; null when not modelled. */
    private String relation(final FrostlakeParser.TableReferenceContext reference, final Catalog catalog) {
        if (reference.pivotClause() != null || reference.unpivotClause() != null || reference.sampleClause() != null) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.VALUES() != null) {
            return values(reference, source);
        }
        if (reference.identifierList() != null) {
            return null;
        }
        final FunctionCallExpression call = tableFunctionCall(source);
        if (call != null) {
            return tableFunction(call, aliasOf(reference));
        }
        if (source.selectStatement() != null) {
            return derived(source.selectStatement(), aliasOf(reference), catalog);
        }
        if (reference.LATERAL() != null) {
            return null;
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length == 1 && ctes.containsKey(parts[0])) {
            final String alias = aliasOf(reference);
            return derived(ctes.get(parts[0]), alias != null ? alias : parts[0], catalog);
        }
        for (final String part : parts) {
            if (!isPlainName(part)) {
                return null;
            }
        }
        final Table table = catalog.resolveTable(QualifiedName.of(parts));
        final String alias = aliasOf(reference);
        final String shown = alias != null ? alias : parts[parts.length - 1];
        final String key = isPlainName(shown) ? shown : "\"" + shown + "\"";
        if (table == null || aliasToTable.containsKey(key)) {
            return null;
        }
        aliasToTable.put(key, table);
        tables.add(table);
        qualifiers.add(shown);
        return String.join(".", parts) + " AS " + shown;
    }

    /**
     * A VALUES list of the FROM clause as the plan inlines it, each cell printed as the plan prints it:
     * {@code (VALUES (CAST('2024-01-01' AS DATE)), (null)) "VALUES"}. The relation goes by its alias — {@code V},
     * {@code "v"} — else by {@code "VALUES"}, whose columns print as {@code VALUES.COLUMN1}; its columns are named
     * by the alias's column list, else COLUMN1, COLUMN2, …, each typed as its cells meet: {@code VALUES (1), (2.5)}
     * is one NUMBER(2,1) column. Null where a cell reads a name, the rows differ in width, a column's cells meet
     * in no type modelled, or an alias is written both inside and outside the brackets.
     */
    private String values(final FrostlakeParser.TableReferenceContext reference,
                          final FrostlakeParser.TableSourceContext source) {
        final String outerAlias = aliasOf(reference);
        final boolean innerAlias = source.identifier() != null;
        if (innerAlias && (outerAlias != null || reference.identifierList() != null)) {
            return null;
        }
        final List<String> columnNames = new ArrayList<>();
        if (innerAlias && source.columnListOptional() != null) {
            for (final FrostlakeParser.NamePartContext part : source.columnListOptional().namePart()) {
                columnNames.add(ParseTreeText.namePartText(part));
            }
        } else if (reference.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext name : reference.identifierList().identifier()) {
                columnNames.add(ParseTreeText.getIdentifier(name));
            }
        }
        final StrictMessagePrinter printer = new StrictMessagePrinter(outputScope(outer.getQueryExecutor()), mode);
        final List<String> bareColumns = new ArrayList<>();
        final List<String> writtenQualifiers = new ArrayList<>();
        printer.watchColumns(bareColumns, writtenQualifiers);
        final List<List<DataType>> cellTypes = new ArrayList<>();
        final List<String> rows = new ArrayList<>();
        for (final FrostlakeParser.ValueTupleContext tuple : source.valueTupleList().valueTuple()) {
            final List<FrostlakeParser.BooleanExprContext> cells = tuple.valueList().booleanExpr();
            if (!rows.isEmpty() && cells.size() != cellTypes.size()) {
                return null;
            }
            final List<String> printed = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) {
                final Expression cell = new ExpressionAstBuilder().build(cells.get(i));
                printed.add(cell.accept(printer));
                if (cellTypes.size() <= i) {
                    cellTypes.add(new ArrayList<DataType>());
                }
                if (!isUntypedNull(cell)) {
                    cellTypes.get(i).add(printer.typeOf(cell));
                }
            }
            rows.add("(" + String.join(", ", printed) + ")");
        }
        if (!bareColumns.isEmpty() || !writtenQualifiers.isEmpty()
                || !columnNames.isEmpty() && columnNames.size() != cellTypes.size()) {
            return null;
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < cellTypes.size(); i++) {
            final DataType met = cellTypes.get(i).isEmpty() ? null : metType(cellTypes.get(i));
            final String name = columnNames.isEmpty() ? "COLUMN" + (i + 1) : columnNames.get(i);
            if (met == null || !isPlainName(name)) {
                return null;
            }
            final TableColumn column = new TableColumn(name, met, true, null, false, false, false);
            column.setStaticallyTyped(true);
            columns.add(column);
        }
        final String alias = innerAlias ? ParseTreeText.getIdentifier(source.identifier()) : outerAlias;
        final String key = alias == null ? VALUES_RELATION : isPlainName(alias) ? alias : "\"" + alias + "\"";
        if (aliasToTable.containsKey(key)) {
            return null;
        }
        final Table table = new Table(alias == null ? VALUES_RELATION : alias, columns, false);
        aliasToTable.put(key, table);
        tables.add(table);
        qualifiers.add(alias == null ? VALUES_RELATION : alias);
        return "(VALUES " + String.join(", ", rows) + ") " + (alias == null ? "\"" + VALUES_RELATION + "\"" : key);
    }

    /**
     * The call a FROM clause's table function makes — {@code TABLE(f(…))}, a bare {@code FLATTEN(…)} or
     * {@code f(…)} — or null for any other source.
     */
    private static FunctionCallExpression tableFunctionCall(final FrostlakeParser.TableSourceContext source) {
        final Expression call;
        if (source.TABLE() != null && source.expression() != null) {
            call = new ExpressionAstBuilder().visit(source.expression());
        } else if (source.FLATTEN() != null || source.tableFunctionExpr() != null) {
            call = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(source));
        } else {
            return null;
        }
        return call instanceof FunctionCallExpression ? (FunctionCallExpression) call : null;
    }

    /**
     * A table function of the FROM clause as the plan prints it, every argument named — a positional one by its
     * place — and no alias: {@code TABLE (FLATTEN(1 => ARRAY_CONSTRUCT('2024-01-01')))},
     * {@code TABLE (GENERATOR(ROWCOUNT => 3))}. It is registered under its alias, else its name, with the
     * columns the function answers, so {@code f.value} prints as {@code F.VALUE} and a bare {@code index} as
     * {@code FLATTEN.INDEX}. Null where an argument reads a name or the function's columns are not known.
     */
    private String tableFunction(final FunctionCallExpression call, final String alias) {
        if (call.getFunctionName() == null || call.getNameExpression() != null || call.isStar() || call.isDistinct()) {
            return null;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final TableFunction function = outer.getFunctionRegistry().getTableFunction(name);
        final List<ResultSetColumn> answered =
            function == null ? null : function.outputColumns(new HashMap<String, Object>());
        final String shown = alias != null ? alias : name;
        if (answered == null || !isPlainName(name) || !isPlainName(shown) || aliasToTable.containsKey(shown)) {
            return null;
        }
        final StrictMessagePrinter printer = new StrictMessagePrinter(outputScope(outer.getQueryExecutor()), mode);
        final List<String> bareColumns = new ArrayList<>();
        final List<String> writtenQualifiers = new ArrayList<>();
        printer.watchColumns(bareColumns, writtenQualifiers);
        final List<String> arguments = new ArrayList<>();
        for (int i = 0; i < call.getArguments().size(); i++) {
            final String written = call.getArgumentNames() == null ? null : call.getArgumentNames().get(i);
            arguments.add((written != null ? written.toUpperCase(Locale.ROOT) : String.valueOf(i + 1)) + " => "
                + call.getArguments().get(i).accept(printer));
        }
        if (!bareColumns.isEmpty() || !writtenQualifiers.isEmpty()) {
            return null;
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (final ResultSetColumn answer : answered) {
            final TableColumn column =
                new TableColumn(answer.getName(), answer.getStaticType(), true, null, false, false, false);
            column.setStaticallyTyped(answer.getStaticType() != null);
            columns.add(column);
        }
        final Table table = new Table(shown, columns, false);
        aliasToTable.put(shown, table);
        tables.add(table);
        qualifiers.add(shown);
        return "TABLE (" + name + "(" + String.join(", ", arguments) + "))";
    }

    /**
     * A NATURAL or USING join of two tables on one column, as the plan inlines it: a derived relation
     * {@code SYS_VW} projecting every column of the left table and then the right table's others — an outer
     * join keeping the right key too, renamed {@code <KEY>_0} — over the join on the key's equality, which the
     * query reads its columns through: {@code SELECT SYS_VW.G AS "G" FROM (SELECT RT.G AS "G", … FROM RT AS RT
     * INNER JOIN ONE AS ONE ON (RT.G = ONE.G)) SYS_VW}. A reference qualified by the left table reads SYS_VW; one
     * qualified by the right table, several keys, and every other join type are not modelled.
     */
    private boolean usingJoin(final FrostlakeParser.TableReferenceContext left, final FrostlakeParser.JoinClauseContext join,
                              final Catalog catalog, final List<String> segments,
                              final List<FrostlakeParser.BooleanExprContext> conditions) {
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        final boolean leftOuter = type != null && type.LEFT() != null;
        if (type != null && type.INNER() == null && !leftOuter || join.DIRECTED() != null || join.LATERAL() != null
                || left.LATERAL() != null || join.tableReference().LATERAL() != null) {
            return false;
        }
        final SubqueryPlanPrint joined = new SubqueryPlanPrint(outer, mode);
        final String leftText = joined.relation(left, catalog);
        final String rightText = leftText == null ? null : joined.relation(join.tableReference(), catalog);
        if (rightText == null || joined.tables.size() != 2) {
            return false;
        }
        final List<String> keysByName = new ArrayList<>(joined.aliasToTable.keySet());
        final Table leftTable = joined.tables.get(0);
        final Table rightTable = joined.tables.get(1);
        final List<String> keys = new ArrayList<>();
        if (join.USING() != null) {
            for (final FrostlakeParser.QualifiedNameContext name : join.usingColumnList().qualifiedName()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(name);
                if (parts.length != 1) {
                    return false;
                }
                keys.add(parts[0]);
            }
        } else {
            for (final TableColumn column : leftTable.getColumns()) {
                if (rightTable.hasColumn(column.getName())) {
                    keys.add(column.getName());
                }
            }
        }
        if (keys.size() != 1 || !leftTable.hasColumn(keys.get(0)) || !rightTable.hasColumn(keys.get(0))) {
            return false;
        }
        final String key = keys.get(0);
        final List<String> projected = new ArrayList<>();
        final List<TableColumn> columns = new ArrayList<>();
        for (final TableColumn column : leftTable.getColumns()) {
            if (!project(keysByName.get(0), column, column.getName(), projected, columns)) {
                return false;
            }
        }
        for (final TableColumn column : rightTable.getColumns()) {
            final boolean isKey = column.getName().equalsIgnoreCase(key);
            if (isKey && !leftOuter) {
                continue;
            }
            String name = column.getName();
            for (final TableColumn taken : columns) {
                if (taken.getName().equalsIgnoreCase(name)) {
                    if (!isKey) {
                        return false;
                    }
                    name = name + "_0";
                }
            }
            if (!project(keysByName.get(1), column, name, projected, columns)) {
                return false;
            }
        }
        final StrictMessagePrinter printer =
            new StrictMessagePrinter(joined.scope(catalog, outer.getQueryExecutor()), mode);
        final String on = printer.conditionText(new BinaryOperationExpression(
            new ColumnReferenceExpression(keysByName.get(0), key, null), BinaryOperator.EQUAL,
            new ColumnReferenceExpression(keysByName.get(1), key, null)), false);
        final Table view = new Table(JOIN_RELATION, columns, false);
        aliasToTable.put(JOIN_RELATION, view);
        tables.add(view);
        qualifiers.add(JOIN_RELATION);
        qualifierRenames.put(keysByName.get(0).toUpperCase(Locale.ROOT), JOIN_RELATION);
        outerJoined = outerJoined || leftOuter;
        segments.add("(SELECT " + String.join(", ", projected) + " FROM " + leftText
            + (leftOuter ? " LEFT OUTER JOIN " : " INNER JOIN ") + rightText + " ON (" + on + ")) " + JOIN_RELATION);
        conditions.add(null);
        matches.add(null);
        return true;
    }

    /** One column of a USING join's relation, {@code RT.G AS "G"}; false when a name would need quoting. */
    private static boolean project(final String relation, final TableColumn column, final String name,
                                   final List<String> projected, final List<TableColumn> columns) {
        if (!isPlainName(name) || !isPlainName(column.getName())) {
            return false;
        }
        projected.add(relation + "." + column.getName() + " AS \"" + name + "\"");
        columns.add(new TableColumn(name, column.getDataType(), true, null, false, false, false));
        return true;
    }

    /**
     * A derived table — or a CTE read in the FROM clause — as the plan inlines it: its own re-print in brackets
     * followed by its name, {@code (SELECT RT.G AS "G" FROM RT AS RT) D}, an unaliased one named {@code values},
     * registered in the scope with the columns its items name. Null when its shape is not modelled.
     */
    private String derived(final FrostlakeParser.SelectStatementContext query, final String alias, final Catalog catalog) {
        final FrostlakeParser.SelectStatementContext statement = soleStatement(query);
        if (statement == null || statement.withClause() != null || alias != null && !isPlainName(alias)) {
            return null;
        }
        final SubqueryPlanPrint inner = new SubqueryPlanPrint(outer, mode);
        inner.ctes.putAll(ctes);
        final List<String> names = new ArrayList<>();
        final List<DataType> types = new ArrayList<>();
        final List<Expression> items = new ArrayList<>();
        final String text = inner.planned(statement.selectOperand(0).selectClause(), statement, null, null,
            outer.getQueryExecutor(), names, types, false, items);
        if (text == null) {
            return null;
        }
        final String shown = alias != null ? alias : DERIVED_RELATION;
        if (aliasToTable.containsKey(shown)) {
            return null;
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (!isPlainName(names.get(i))) {
                return null;
            }
            columns.add(new TableColumn(names.get(i), types.get(i), inner.nullable(items.get(i)), null, false, false,
                false));
        }
        final Table table = new Table(shown, columns, false);
        aliasToTable.put(shown, table);
        tables.add(table);
        qualifiers.add(shown);
        return "(" + text + ") " + shown;
    }

    private static String aliasOf(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName());
        }
        final FrostlakeParser.NonJoinKeywordIdentifierContext bare = reference.nonJoinKeywordIdentifier();
        if (bare == null) {
            return null;
        }
        return bare.identifier() != null ? ParseTreeText.getIdentifier(bare.identifier())
            : bare.getText().toUpperCase(Locale.ROOT);
    }

    /** The evaluation scope the subquery's own FROM clause makes, which its columns are qualified and typed in. */
    /**
     * The scope a set operation's own ORDER BY is read in: its output columns alone, over no relation — a key
     * is an ordinal or a name the combination answers.
     */
    private ExpressionEvaluatorVisitor outputScope(final QueryExecutor queryExecutor) {
        final ExpressionEvaluatorVisitor scope = new ExpressionEvaluatorVisitor(
            new Table(FROMLESS_RELATION, new ArrayList<TableColumn>(), false), null, outer.getFunctionRegistry(),
            queryExecutor.getCatalog());
        scope.setQueryExecutor(queryExecutor);
        return scope;
    }

    private ExpressionEvaluatorVisitor scope(final Catalog catalog, final QueryExecutor queryExecutor) {
        final Table first = tables.isEmpty()
            ? new Table(FROMLESS_RELATION, new ArrayList<TableColumn>(), false) : tables.get(0);
        final ExpressionEvaluatorVisitor scope =
            new ExpressionEvaluatorVisitor(first, null, outer.getFunctionRegistry(), catalog);
        scope.setQueryExecutor(queryExecutor);
        if (!tables.isEmpty()) {
            scope.setMultiTableContext(aliasToTable, tables);
        }
        return scope;
    }

    /** One select item and its name, a star expanded over the one relation; false when not modelled. */
    private boolean collectItem(final FrostlakeParser.SelectItemContext item, final List<Expression> items,
                                final List<String> names) {
        if (SelectItemAccessors.isStarItem(item)) {
            if (tables.size() != 1 || !SelectItemAccessors.getStarModifiers(item).isEmpty()) {
                return false;
            }
            final String qualifier = qualifiers.iterator().next();
            if (!isPlainName(qualifier)) {
                return false;
            }
            for (final TableColumn column : tables.get(0).getColumns()) {
                items.add(new ColumnReferenceExpression(qualifier, column.getName(), null));
                names.add(column.getName());
            }
            return true;
        }
        if (!SelectItemAccessors.isExprItem(item)) {
            return false;
        }
        items.add(new ExpressionAstBuilder().build(((FrostlakeParser.ExprItemContext) item).booleanExpr()));
        final String alias = SelectItemAccessors.getItemAlias(item);
        final String name = alias != null ? alias : derivedName(item);
        if (name.indexOf('"') >= 0) {
            return false;
        }
        names.add(name);
        return true;
    }

    /** An unaliased item's name: a column's own name, anything else its source text upper-cased. */
    private static String derivedName(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (value == null) {
            return ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)).toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.ExpressionContext simple = SelectItemAccessors.unwrapParens(value);
        if (simple instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) simple).qualifiedName());
            return parts[parts.length - 1];
        }
        return ParseTreeText.getOriginalText(value).toUpperCase(Locale.ROOT);
    }

    /** The GROUP BY the plan prints, "" for none and null when not modelled. */
    private String groupText(final FrostlakeParser.GroupByClauseContext group, final List<Expression> items,
                             final List<String> names, final StrictMessagePrinter printer) {
        if (group == null) {
            return "";
        }
        final List<String> keys = new ArrayList<>();
        if (group.ALL() != null) {
            // GROUP BY ALL groups by the projected columns, and names them as projected.
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof ColumnReferenceExpression)) {
                    return null;
                }
                keys.add(names.get(i));
            }
            return " GROUP BY " + String.join(", ", keys);
        }
        for (final FrostlakeParser.GroupByElementContext element : group.groupByElement()) {
            if (element.expression() == null) {
                return null;
            }
            final Expression key = new ExpressionAstBuilder().visit(element.expression());
            final Integer ordinal = ordinalOf(key, names.size());
            if (ordinal != null) {
                keys.add(names.get(ordinal.intValue() - 1));
            } else if (key instanceof ColumnReferenceExpression && !((ColumnReferenceExpression) key).isQualified()
                    && !readByARelation(((ColumnReferenceExpression) key).getColumnName())
                    && names.contains(((ColumnReferenceExpression) key).getColumnName())) {
                // A name no relation carries is the output column it names, and prints bare.
                keys.add(((ColumnReferenceExpression) key).getColumnName());
            } else {
                keys.add(key.accept(printer));
            }
        }
        return " GROUP BY " + String.join(", ", keys);
    }

    /** The ORDER BY the plan prints, "" for none and null when not modelled. */
    private String orderText(final FrostlakeParser.OrderByClauseContext order, final ExpressionEvaluatorVisitor scope,
                             final List<String> names) {
        if (order == null) {
            return "";
        }
        final Set<String> outputNames = new HashSet<>(names);
        scope.setOutputScopeNames(outputNames);
        try {
            final StrictMessagePrinter printer = new StrictMessagePrinter(scope, mode);
            final List<String> bareColumns = new ArrayList<>();
            final List<String> writtenQualifiers = new ArrayList<>();
            printer.watchColumns(bareColumns, writtenQualifiers);
            final List<String> keys = new ArrayList<>();
            for (final FrostlakeParser.OrderItemContext item : order.orderItem()) {
                final Expression key = new ExpressionAstBuilder().visit(item.expression());
                final Integer ordinal = ordinalOf(key, names.size());
                final boolean descending = item.DESC() != null;
                final boolean nullsFirst = item.FIRST() != null || (item.LAST() == null && descending);
                keys.add((ordinal != null ? names.get(ordinal.intValue() - 1) : key.accept(printer))
                    + (descending ? " DESC" : " ASC") + " NULLS " + (nullsFirst ? "FIRST" : "LAST"));
            }
            for (final String bare : bareColumns) {
                if (!outputNames.contains(bare) || !isPlainName(bare)) {
                    return null;
                }
            }
            return allKnown(writtenQualifiers) ? "  ORDER BY " + String.join(", ", keys) : null;
        } finally {
            scope.setOutputScopeNames(null);
        }
    }

    /**
     * The LIMIT the plan prints, {@code LIMIT n OFFSET m} with the offset 0 when none was written — from a
     * LIMIT, a FETCH or a TOP alike — "" for none and null when not modelled (a NULL, an empty string or
     * a bind as the count).
     */
    private static String limitText(final FrostlakeParser.SelectStatementContext statement,
                                    final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.LimitClauseContext limit = statement == null ? null : statement.limitClause();
        final FrostlakeParser.FetchClauseContext fetch = statement == null ? null : statement.fetchClause();
        final FrostlakeParser.TopClauseContext top = select == null ? null : select.topClause();
        final int written = (limit != null ? 1 : 0) + (fetch != null ? 1 : 0) + (top != null ? 1 : 0);
        if (written == 0) {
            return "";
        }
        if (written > 1) {
            return null;
        }
        final TerminalNode count;
        final TerminalNode offset;
        if (limit != null) {
            if (!limit.NULL().isEmpty() || !limit.STRING_LITERAL().isEmpty() || !limit.COLON().isEmpty()) {
                return null;
            }
            final List<TerminalNode> numbers = limit.INTEGER_LITERAL();
            count = numbers.get(0);
            offset = numbers.size() > 1 ? numbers.get(1) : null;
        } else if (fetch != null) {
            if (!fetch.NULL().isEmpty() || !fetch.COLON().isEmpty()) {
                return null;
            }
            final List<TerminalNode> numbers = fetch.INTEGER_LITERAL();
            count = numbers.get(numbers.size() - 1);
            offset = fetch.OFFSET() != null ? numbers.get(0) : null;
        } else {
            count = top.INTEGER_LITERAL();
            offset = null;
        }
        return " LIMIT " + new BigInteger(count.getText()) + " OFFSET "
            + (offset != null ? new BigInteger(offset.getText()).toString() : "0");
    }

    /** The 1-based ordinal a written integer key counts to, or null for any other key or one out of range. */
    private static Integer ordinalOf(final Expression key, final int items) {
        if (!(key instanceof LiteralExpression) || ((LiteralExpression) key).getType() != LiteralType.INTEGER) {
            return null;
        }
        final long ordinal = ((Number) ((LiteralExpression) key).getValue()).longValue();
        if (ordinal < 1 || ordinal > items) {
            throw new IllegalStateException("An ordinal out of range is not modelled: " + ordinal);
        }
        return Integer.valueOf((int) ordinal);
    }

    private boolean readByARelation(final String column) {
        for (final Table table : tables) {
            if (table.hasColumn(column)) {
                return true;
            }
        }
        return false;
    }

    /** Whether every qualifier the subquery wrote names one of its own relations, as printed. */
    private boolean allKnown(final List<String> writtenQualifiers) {
        for (final String qualifier : writtenQualifiers) {
            if (!qualifiers.contains(qualifier) || !isPlainName(qualifier)) {
                return false;
            }
        }
        return true;
    }

    /** A name that prints the same quoted or not: upper-case letters, digits, '_' and '$', not led by a digit. */
    private static boolean isPlainName(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            final boolean leading = (c >= 'A' && c <= 'Z') || c == '_';
            final boolean following = (c >= '0' && c <= '9') || c == '$';
            if (!(leading || (i > 0 && following))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUntypedNull(final Expression expression) {
        return expression instanceof LiteralExpression
            && ((LiteralExpression) expression).getType() == LiteralType.NULL;
    }

    private static boolean isExactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }

    /** The subquery's text as a SELECT statement, or null when it is not one whole statement. */
    private static FrostlakeParser.SelectStatementContext parsed(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.SelectStatementContext statement = parser.selectStatement();
        NamedCallRewrite.apply(statement);
        return parser.getCurrentToken().getType() == Token.EOF ? statement : null;
    }

    /**
     * The statement whose one operand is a plain SELECT clause, through any brackets written around it,
     * or null for a set operation, a WITH inside brackets, or brackets that carry an ORDER BY or a LIMIT of
     * their own.
     */
    private static FrostlakeParser.SelectStatementContext soleStatement(
            final FrostlakeParser.SelectStatementContext written) {
        FrostlakeParser.SelectStatementContext statement = written;
        while (statement != null) {
            // Only the outermost statement may carry a WITH: its CTEs are read as derived tables.
            if (statement != written && statement.withClause() != null || statement.selectOperand().size() != 1) {
                return null;
            }
            final FrostlakeParser.SelectOperandContext operand = statement.selectOperand(0);
            if (operand.selectClause() != null) {
                return statement;
            }
            if (statement.orderByClause() != null || statement.limitClause() != null
                    || statement.fetchClause() != null) {
                return null;
            }
            statement = operand.selectStatement();
        }
        return null;
    }
}
