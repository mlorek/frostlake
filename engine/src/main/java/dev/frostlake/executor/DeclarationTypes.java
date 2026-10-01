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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

import org.antlr.v4.runtime.ParserRuleContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The type an UNTYPED declaration ({@code LET y := <expr>}, {@code DECLARE y DEFAULT <expr>}) takes from
 * an initialiser the direct rules do not cover: the initialiser is typed as a SQL expression over the
 * declared types in scope, then widened the way live declares the name. A text of any length becomes
 * VARCHAR(134217728), a whole number NUMBER(38,0) and a fraction a FLOAT; a date, a timestamp, a boolean
 * and a container keep their own type. So {@code LET y := 'abc'}, {@code UPPER('ab')} and {@code 'x' ||
 * 'yz'} all declare VARCHAR(134217728), {@code 1 + 1} and {@code ABS(-5)} NUMBER(38,0), {@code 1.5 * 2}
 * FLOAT, {@code CURRENT_DATE()} DATE and {@code CURRENT_TIMESTAMP()} TIMESTAMP_LTZ(9) (live-verified).
 * A bare name hands on its own type, except that a number with a scale becomes a FLOAT.
 */
public final class DeclarationTypes {

    /** The type a whole number gives an untyped declaration. */
    public static final DataType WHOLE_NUMBER = new NumericType("NUMBER", 38, 0);

    private DeclarationTypes() {
    }

    /**
     * @param initialiser  the initialiser as parsed
     * @param typesInScope the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the declared type, or null when the initialiser's type cannot be determined
     */
    public static DataType ofInitialiser(final FrostlakeParser.ExpressionContext initialiser,
                                         final Map<String, DataType> typesInScope,
                                         final QueryExecutor queryExecutor) {
        return ofExpression(sqlExpression(initialiser), typesInScope, queryExecutor);
    }

    /**
     * @param initialiser  the initialiser as parsed, a NOT, AND or OR included
     * @param typesInScope the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the declared type, or null when the initialiser's type cannot be determined
     */
    public static DataType ofInitialiser(final FrostlakeParser.BooleanExprContext initialiser,
                                         final Map<String, DataType> typesInScope,
                                         final QueryExecutor queryExecutor) {
        return ofExpression(sqlExpression(initialiser), typesInScope, queryExecutor);
    }

    /**
     * @param expression   the initialiser as a SQL expression, or null
     * @param typesInScope the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the declared type, or null when the expression's type cannot be determined
     */
    public static DataType ofExpression(final Expression expression, final Map<String, DataType> typesInScope,
                                        final QueryExecutor queryExecutor) {
        final DataType type = staticType(expression, typesInScope, queryExecutor);
        return expression instanceof ColumnReferenceExpression ? handedOn(type) : widened(type);
    }

    /**
     * The static type an expression carries over the declared types in scope, as SQL types it — before
     * any widening a declaration applies. A routine's RETURN reports this type: {@code 1.7777 + 1} is
     * NUMBER(7,4) there, where {@code LET y := 1.7777 + 1} declares a FLOAT.
     *
     * @param expression    the expression, or null
     * @param typesInScope  the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the expression's static type, or null when it cannot be determined
     */
    public static DataType staticType(final Expression expression, final Map<String, DataType> typesInScope,
                                      final QueryExecutor queryExecutor) {
        if (expression == null || queryExecutor == null) {
            return null;
        }
        return scriptTyper(typesInScope, queryExecutor).inferStaticType(expression);
    }

    /**
     * An evaluator that types an expression over the declared names in scope: each name a column of a relation
     * holding no row, a cursor record's field, keyed REC.FIELD, a column of a relation named for the record.
     *
     * @param typesInScope  the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the evaluator
     */
    public static ExpressionEvaluator scriptTyper(final Map<String, DataType> typesInScope,
                                                  final QueryExecutor queryExecutor) {
        final List<TableColumn> columns = new ArrayList<TableColumn>();
        // A cursor record's field, keyed REC.FIELD, is a column of a relation named for the record.
        final Map<String, List<TableColumn>> recordFields = new LinkedHashMap<String, List<TableColumn>>();
        for (final Map.Entry<String, DataType> entry : typesInScope.entrySet()) {
            final int dot = entry.getKey().indexOf('.');
            final TableColumn column = new TableColumn(dot < 0 ? entry.getKey() : entry.getKey().substring(dot + 1),
                entry.getValue(), true, null, false, false, false);
            // A declared type is authoritative, so the resolver trusts it.
            column.setStaticallyTyped(true);
            if (dot < 0) {
                columns.add(column);
            } else {
                final String record = entry.getKey().substring(0, dot);
                if (!recordFields.containsKey(record)) {
                    recordFields.put(record, new ArrayList<TableColumn>());
                }
                recordFields.get(record).add(column);
            }
        }
        final Table variables = new Table("$SCRIPT_VARIABLES", columns, true);
        final ExpressionEvaluator typer = new ExpressionEvaluator(variables,
            queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
        if (!recordFields.isEmpty()) {
            final Map<String, Table> byName = new LinkedHashMap<String, Table>();
            final List<Table> all = new ArrayList<Table>();
            byName.put(variables.getName(), variables);
            all.add(variables);
            for (final Map.Entry<String, List<TableColumn>> record : recordFields.entrySet()) {
                final Table fields = new Table(record.getKey(), record.getValue(), true);
                byName.put(record.getKey(), fields);
                all.add(fields);
            }
            typer.setMultiTableContext(byName, all);
        }
        return typer;
    }

    /**
     * {@link #staticType} as a block's own expression is typed: a product whose one operand is the literal
     * 1 (or 1.0) and whose other is no literal is that operand's type, a text read as a NUMBER(18,5) — {@code
     * i * 1} over a FOR counter is NUMBER(9,0), {@code SQLROWCOUNT * 1} NUMBER(18,5) — where SQL types a column
     * times 1 one digit wider. Only the whole expression folds: {@code i * 1 + 0} is NUMBER(11,0)
     * (live-verified).
     *
     * @param expression    the expression, or null
     * @param typesInScope  the declared types of the names in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the expression
     * @return the expression's static type, or null when it cannot be determined
     */
    public static DataType scriptStaticType(final Expression expression, final Map<String, DataType> typesInScope,
                                            final QueryExecutor queryExecutor) {
        Expression folded = expression;
        while (folded instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) folded).getOperator() == BinaryOperator.MULTIPLY) {
            final BinaryOperationExpression product = (BinaryOperationExpression) folded;
            if (isLiteralOne(product.getRight()) && !(product.getLeft() instanceof LiteralExpression)) {
                folded = product.getLeft();
            } else if (isLiteralOne(product.getLeft()) && !(product.getRight() instanceof LiteralExpression)) {
                folded = product.getRight();
            } else {
                break;
            }
        }
        final DataType type = staticType(folded, typesInScope, queryExecutor);
        return folded != expression && type instanceof StringType ? TEXT_IN_ARITHMETIC : type;
    }

    /** The NUMBER a text operand of a block's arithmetic is read as. */
    private static final DataType TEXT_IN_ARITHMETIC = new NumericType("NUMBER", 18, 5);

    private static boolean isLiteralOne(final Expression expression) {
        if (!(expression instanceof LiteralExpression)
                || !(((LiteralExpression) expression).getValue() instanceof Number)) {
            return false;
        }
        return new BigDecimal(((LiteralExpression) expression).getValue().toString()).compareTo(BigDecimal.ONE) == 0;
    }

    /** The initialiser as a SQL expression, or null when it cannot be read as one. */
    public static Expression sqlExpression(final FrostlakeParser.ExpressionContext initialiser) {
        return astOf(initialiser);
    }

    /** A scripting value — a NOT, AND or OR included — as a SQL expression, or null when it cannot be read as one. */
    public static Expression sqlExpression(final FrostlakeParser.BooleanExprContext value) {
        return astOf(value);
    }

    private static Expression astOf(final ParserRuleContext parsed) {
        if (parsed == null) {
            return null;
        }
        try {
            return new ExpressionAstBuilder().visit(parsed);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /** How live widens an expression's type for an untyped declaration. */
    private static DataType widened(final DataType type) {
        if (type == null || type instanceof UuidType) {
            return type;
        }
        if (type instanceof StringType) {
            return new StringType("VARCHAR", StringResultWidths.UNBOUNDED);
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) || ((NumericType) type).getScale() > 0
                ? NumericType.FLOAT : WHOLE_NUMBER;
        }
        return type;
    }

    /** A bare name's type as it passes to the declaration: its own, a number with a scale a FLOAT. */
    private static DataType handedOn(final DataType type) {
        if (type instanceof NumericType
                && (NumericType.isApproximate(type) || ((NumericType) type).getScale() > 0)) {
            return NumericType.FLOAT;
        }
        return type;
    }
}
