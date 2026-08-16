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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;

import java.util.ArrayList;
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
        final List<TableColumn> columns = new ArrayList<TableColumn>();
        for (final Map.Entry<String, DataType> entry : typesInScope.entrySet()) {
            final TableColumn column = new TableColumn(entry.getKey(), entry.getValue(), true, null,
                false, false, false);
            // A declared type is authoritative, so the resolver trusts it.
            column.setStaticallyTyped(true);
            columns.add(column);
        }
        final ExpressionEvaluator typer = new ExpressionEvaluator(new Table("$SCRIPT_VARIABLES", columns, true),
            queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
        return typer.inferStaticType(expression);
    }

    /** The initialiser as a SQL expression, or null when it cannot be read as one. */
    public static Expression sqlExpression(final FrostlakeParser.ExpressionContext initialiser) {
        if (initialiser == null) {
            return null;
        }
        try {
            return new ExpressionAstBuilder().visit(initialiser);
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
