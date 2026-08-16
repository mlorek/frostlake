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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.RoutineReturnTypeOracle;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * The declared-vs-actual return-type check a real account runs on a scalar {@code LANGUAGE SQL} UDF
 * at CREATE time (live-verified): the body's statically-known type must share the declared type's
 * {@link ReturnTypeFamily}; an explicit cast in the body changes the verdict because the check reads
 * the body's static type. Everything else is left alone — an undetermined body type, a scripting
 * block body (which answers per executed RETURN), a table function, a non-SQL language, and every
 * procedure. Failing open on the undetermined keeps the audited-only principle: null never guesses.
 */
final class RoutineReturnTypeChecker {

    private RoutineReturnTypeChecker() {
    }

    static void checkScalarSqlUdf(final QueryExecutor queryExecutor, final Catalog catalog,
                                  final String language, final boolean tableFunction,
                                  final DataType declared, final List<Parameter> parameters,
                                  final String body) {
        if (queryExecutor == null || tableFunction || body == null || body.trim().isEmpty()
                || declared == null || !"SQL".equals(language)) {
            return;
        }
        final ReturnTypeFamily declaredFamily = ReturnTypeFamily.of(declared);
        if (declaredFamily == null) {
            return;
        }
        final String expressionText = bodyExpressionText(queryExecutor, body.trim());
        if (expressionText == null) {
            return;
        }
        final DataType actual = RoutineReturnTypeOracle.bodyType(expressionText,
            parametersTable(parameters), queryExecutor.getFunctionRegistry(), catalog);
        if (actual == null) {
            return;
        }
        final ReturnTypeFamily actualFamily = ReturnTypeFamily.of(actual);
        if (actualFamily == null || actualFamily == declaredFamily) {
            return;
        }
        throw new RuntimeException("Declared return type '" + spell(declared)
            + "' is incompatible with actual return type '" + spell(actual) + "'");
    }

    /**
     * The expression whose static type stands for the body's: the body itself when it is a scalar
     * expression, the lone select item of a single-SELECT query body with no FROM, and null for
     * every shape the check leaves alone (scripting blocks, multi-item or FROM-carrying queries).
     */
    private static String bodyExpressionText(final QueryExecutor queryExecutor, final String body) {
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(body);
        if (query != null) {
            return singleItemText(query, body);
        }
        if (queryExecutor.proceduralBlockOf(body) != null) {
            return null;
        }
        return body;
    }

    private static String singleItemText(final FrostlakeParser.SqlScriptContext script,
                                         final String body) {
        final FrostlakeParser.SelectStatementContext select = firstSelect(script);
        if (select == null || !select.setOperator().isEmpty() || select.selectOperand().size() != 1) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext clause = select.selectOperand(0).selectClause();
        if (clause == null || clause.FROM() != null || clause.selectList() == null) {
            return null;
        }
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        if (items.size() != 1) {
            return null;
        }
        final FrostlakeParser.SelectItemContext item = items.get(0);
        if (item.getStart() == null || item.getStop() == null) {
            return null;
        }
        return body.substring(item.getStart().getStartIndex(), item.getStop().getStopIndex() + 1);
    }

    private static FrostlakeParser.SelectStatementContext firstSelect(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return (FrostlakeParser.SelectStatementContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectStatementContext found = firstSelect(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The declared parameters as a single-table context, so the oracle types references to them. */
    private static Table parametersTable(final List<Parameter> parameters) {
        final List<TableColumn> columns = new ArrayList<TableColumn>();
        if (parameters != null) {
            for (final Parameter parameter : parameters) {
                final TableColumn column = new TableColumn(parameter.getName(),
                    parameter.getDataType(), true, null, false, false, false);
                // A declared parameter type is authoritative — mark it so the resolver trusts it
                // (a synthetic table is not a base catalog table).
                column.setStaticallyTyped(true);
                columns.add(column);
            }
        }
        return new Table("$ROUTINE_PARAMETERS", columns, true);
    }

    /**
     * The account's spelling of a type in this refusal: every parameterized type carries its
     * parameters, an undeclared length means the type's maximum and is spelled as such, the
     * floating-point names collapse to FLOAT, and TIMESTAMP/DATETIME spell their NTZ flavor.
     */
    private static String spell(final DataType type) {
        final ReturnTypeFamily family = ReturnTypeFamily.of(type);
        if (family == ReturnTypeFamily.REAL) {
            return "FLOAT";
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            final int length = ((StringType) type).getMaxLength();
            return type.getName().toUpperCase() + "(" + (length == 16777216 ? 134217728 : length) + ")";
        }
        if (type instanceof BinaryType) {
            final int length = ((BinaryType) type).getMaxLength();
            return "BINARY(" + (length == 8388608 ? 67108864 : length) + ")";
        }
        if (type instanceof DateTimeType) {
            final DateTimeType dateTime = (DateTimeType) type;
            if (family == ReturnTypeFamily.DATE) {
                return "DATE";
            }
            if (family == ReturnTypeFamily.TIME) {
                return "TIME(" + dateTime.getPrecision() + ")";
            }
            if (family == ReturnTypeFamily.TIMESTAMP_LTZ) {
                return "TIMESTAMP_LTZ(" + dateTime.getPrecision() + ")";
            }
            if (family == ReturnTypeFamily.TIMESTAMP_TZ) {
                return "TIMESTAMP_TZ(" + dateTime.getPrecision() + ")";
            }
            return "TIMESTAMP_NTZ(" + dateTime.getPrecision() + ")";
        }
        if (family == ReturnTypeFamily.BOOLEAN || family == ReturnTypeFamily.VARIANT
                || family == ReturnTypeFamily.ARRAY || family == ReturnTypeFamily.OBJECT) {
            return family.name();
        }
        return type.getName().toUpperCase();
    }
}
