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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.RoutineReturnTypeOracle;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.executor.expressions.UntypedNullFold;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declared-vs-actual return-type check a real account runs on a scalar {@code LANGUAGE SQL} UDF
 * at CREATE time (live-verified): the body's statically-known type must share the declared type's
 * {@link ReturnTypeFamily}; an explicit cast in the body changes the verdict because the check reads
 * the body's static type. Everything else is left alone — an undetermined body type, a scripting
 * block body (which answers per executed RETURN), a table function, a non-SQL language, and every
 * procedure. Failing open on the undetermined keeps the audited-only principle: null never guesses.
 */
final class RoutineReturnTypeChecker {

    /** The compile's refusal of set-operator branches whose column types do not meet. */
    private static final String INCONSISTENT_BRANCHES = "inconsistent data type for result columns";

    /** The compile's refusal of an operand that cannot become the type its comparison expects. */
    private static final String UNCONVERTIBLE_OPERAND = "Can not convert parameter";

    /** Where a positioned refusal points. */
    private static final Pattern ERROR_POSITION = Pattern.compile("error line (\\d+) at position (\\d+)");

    /** How the refusal spells a value of no type at all. */
    private static final String UNTYPED_NULL = "NULL";

    /** What a value the query compile types is written after, for a value that is no query itself. */
    private static final String SELECTED = "SELECT ";

    private RoutineReturnTypeChecker() {
    }

    static void checkScalarSqlUdf(final QueryExecutor queryExecutor, final Catalog catalog,
                                  final String language, final boolean tableFunction,
                                  final DataType declared, final List<Parameter> parameters,
                                  final String written, final Schema home) {
        if (queryExecutor == null || tableFunction || written == null || written.trim().isEmpty()
                || declared == null || !"SQL".equals(language)) {
            return;
        }
        // A body that closes its own frame before a semicolon is typed by what the frame holds.
        final String body = SqlUdfBodyFrame.executableBody(written);
        final FrostlakeParser.ExprTupleContext row = SqlUdfBodyFrame.rowOf(body);
        if (row != null) {
            throw rowRefusal(queryExecutor, catalog, declared, parameters, row, body, home);
        }
        final ReturnTypeFamily declaredFamily = ReturnTypeFamily.of(declared);
        if (declaredFamily == null) {
            return;
        }
        final String expressionText = bodyExpressionText(queryExecutor, body.trim());
        if (expressionText == null) {
            if (home != null && queryExecutor.queryStatementOf(body.trim()) != null) {
                judgeQueryShape(queryExecutor, catalog, declared, declaredFamily, body.trim(), home, parameters);
            }
            return;
        }
        if (isUntypedNull(expressionText)) {
            // The bare word NULL, or a call that folds to it, has no type, and every declared type takes it.
            return;
        }
        final DataType actual;
        try {
            actual = RoutineReturnTypeOracle.bodyType(expressionText,
                parametersTable(parameters), queryExecutor.getFunctionRegistry(), catalog);
        } catch (final RuntimeException untyped) {
            // A call the engine cannot type — a function the account builds in and this engine lacks — leaves
            // the body's type undetermined; a name nothing declares was refused when the body compiled.
            if (SqlUdfBodyCalls.isUnknownFunctionRefusal(untyped)) {
                return;
            }
            throw untyped;
        }
        if (actual == null) {
            // A lone item the expression typing cannot settle — a scalar subquery — is typed by the query.
            if (home != null && queryExecutor.queryStatementOf(body.trim()) != null) {
                judgeQueryShape(queryExecutor, catalog, declared, declaredFamily, body.trim(), home, parameters);
            }
            return;
        }
        final ReturnTypeFamily actualFamily = ReturnTypeFamily.of(actual);
        if (actualFamily == null
                || actualFamily == declaredFamily && !ReturnTypeFamily.precisionDiffers(declared, actual)) {
            return;
        }
        throw new RuntimeException("Declared return type '" + spell(declared)
            + "' is incompatible with actual return type '" + spell(actual) + "'");
    }

    /**
     * A body that reads as a parenthesised list of values is a ROW, which no declared type takes: {@code 1,2} under
     * RETURNS INT and under RETURNS VARIANT alike is refused as {@code ROW(NUMBER(1,0), NUMBER(1,0))}, each value
     * typed as the body types it — the bare word NULL as {@code NULL}, a scalar subquery by its query, and a query
     * of several columns as a ROW of its own: {@code (SELECT n, d FROM t), 1} is
     * {@code ROW(ROW(NUMBER(38,0), DATE), NUMBER(1,0))} (live-verified). A value whose type this engine cannot tell
     * is still refused, where the frame reads no expression. A value's own compile refusal, a name it does not
     * resolve included, is raised instead.
     */
    private static RuntimeException rowRefusal(final QueryExecutor queryExecutor, final Catalog catalog,
                                               final DataType declared, final List<Parameter> parameters,
                                               final FrostlakeParser.ExprTupleContext row, final String body,
                                               final Schema home) {
        final StringBuilder actual = new StringBuilder();
        for (final FrostlakeParser.ExpressionContext value : row.expression()) {
            final String type = rowValueType(queryExecutor, catalog, parameters, value, home);
            if (type == null) {
                return new RuntimeException("Compilation of SQL UDF failed: "
                    + SqlCompilationError.of(RoutineBodyCompiler.untypedRowRefusal(queryExecutor, body)));
            }
            actual.append(actual.length() > 0 ? ", " : "").append(type);
        }
        return new RuntimeException("Declared return type '" + spell(declared)
            + "' is incompatible with actual return type 'ROW(" + actual + ")'");
    }

    /** How the refusal spells one value of a ROW body's, or null when this engine cannot type it. */
    private static String rowValueType(final QueryExecutor queryExecutor, final Catalog catalog,
                                       final List<Parameter> parameters, final FrostlakeParser.ExpressionContext value,
                                       final Schema home) {
        final String text = value.getStart().getInputStream().getText(
            Interval.of(value.getStart().getStartIndex(), value.getStop().getStopIndex()));
        if (isUntypedNull(text)) {
            return UNTYPED_NULL;
        }
        final DataType type;
        try {
            type = RoutineReturnTypeOracle.bodyType(text, parametersTable(parameters),
                queryExecutor.getFunctionRegistry(), catalog);
        } catch (final RuntimeException untyped) {
            if (SqlUdfBodyCalls.isUnknownFunctionRefusal(untyped)) {
                return null;
            }
            throw untyped;
        }
        if (type != null) {
            return spellColumn(type);
        }
        if (home == null) {
            return null;
        }
        // A value the expression typing cannot settle — a scalar subquery, or a value holding one — is typed by the
        // query, with the routine's parameters in scope as the body reads them.
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(text);
        final String compiled = query != null ? text : SELECTED + text;
        final ParameterSubstitutedBody typed = ParameterSubstitutedBody.typed(compiled, parameters);
        final List<TableColumn> columns;
        try {
            columns = queryExecutor.resolveQueryResultColumnsInScope(home.getDatabaseName(), home.getName(),
                typed.text());
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage());
            if (SqlUdfBodyCalls.isUnknownFunctionRefusal(refused) || !SqlCompilationError.isCompilationError(message)) {
                return null;
            }
            // The value's own compile refusal, a name it does not resolve included, stands where the value does.
            throw new RuntimeException(atValue(typed.inAccountWords(typed.inWrittenPositions(message),
                queryExecutor.getFunctionRegistry(), catalog), value.getStart(), query != null ? 0 : SELECTED.length()));
        }
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        final StringBuilder spelled = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getDataType() == null) {
                return null;
            }
            spelled.append(i > 0 ? ", " : "").append(query != null && isNullColumn(query, text, i) ? UNTYPED_NULL
                : spellColumn(columns.get(i).getDataType()));
        }
        return columns.size() == 1 ? spelled.toString() : "ROW(" + spelled + ")";
    }

    /**
     * A refusal from compiling a ROW value's text, moved to where the value stands in the body's frame: a value on
     * the frame's first line moves its first line's positions by where it starts, less the {@code prefix} the text
     * was compiled after.
     */
    private static String atValue(final String message, final Token valueStart, final int prefix) {
        final Matcher at = ERROR_POSITION.matcher(message);
        if (!at.find()) {
            return message;
        }
        final int line = Integer.parseInt(at.group(1));
        final int position = Integer.parseInt(at.group(2));
        final int framedLine = valueStart.getLine() + line - 1;
        final int framedPosition = line == 1 ? valueStart.getCharPositionInLine() + position - prefix : position;
        return message.substring(0, at.start()) + "error line " + framedLine + " at position " + framedPosition
            + message.substring(at.end());
    }

    /** Whether an expression text is the bare word NULL, or a call that folds to it — a value of no type at all. */
    private static boolean isUntypedNull(final String expressionText) {
        try {
            return UntypedNullFold.isUntypedNull(AntlrExpressionParser.parse(expressionText));
        } catch (final RuntimeException notAnExpression) {
            return false;
        }
    }

    /**
     * Whether a query's result column is a value of no type in every branch of its set operation: the bare word NULL,
     * or a call folding to it. Such a column meets every declared type, and a ROW spells it {@code NULL}:
     * {@code SELECT NULL FROM t} is created under RETURNS INT, and {@code SELECT 1, NULL} refused as
     * {@code ROW(NUMBER(1,0), NULL)} (live-verified).
     */
    private static boolean isNullColumn(final FrostlakeParser.SqlScriptContext query, final String text,
                                        final int column) {
        final FrostlakeParser.SelectStatementContext select = firstSelect(query);
        return select != null && isNullColumn(select, text, column);
    }

    private static boolean isNullColumn(final FrostlakeParser.SelectStatementContext select, final String text,
                                        final int column) {
        for (final FrostlakeParser.SelectOperandContext operand : select.selectOperand()) {
            if (operand.selectStatement() != null) {
                if (!isNullColumn(operand.selectStatement(), text, column)) {
                    return false;
                }
                continue;
            }
            final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
            if (clause == null || clause.selectList() == null || column >= clause.selectList().selectItem().size()
                    || !(clause.selectList().selectItem(column) instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final FrostlakeParser.BooleanExprContext item =
                ((FrostlakeParser.ExprItemContext) clause.selectList().selectItem(column)).booleanExpr();
            if (item == null || !isUntypedNull(text.substring(item.getStart().getStartIndex(),
                    item.getStop().getStopIndex() + 1))) {
                return false;
            }
        }
        return !select.selectOperand().isEmpty();
    }

    /**
     * A query body the single-item rule does not cover — one reading a relation, a set operation, several
     * columns — judged by the shape it compiles to, where the routine lives: its one column must share the
     * declared type's family, and several columns are a ROW no declared type takes, each spelled as the column
     * carries it (live-verified). The body compiles with the routine's parameters in scope, each typed as declared
     * and winning over a same-named column, as a call runs it (see {@link ParameterSubstitutedBody}); a refusal that
     * quotes a parameter names it as the account does. A body that does not compile even so is left to the run.
     */
    private static void judgeQueryShape(final QueryExecutor queryExecutor, final Catalog catalog,
                                        final DataType declared, final ReturnTypeFamily declaredFamily,
                                        final String body, final Schema home, final List<Parameter> parameters) {
        final ParameterSubstitutedBody typed = ParameterSubstitutedBody.typed(body, parameters);
        final List<TableColumn> shape;
        try {
            shape = queryExecutor.resolveQueryResultColumnsInScope(home.getDatabaseName(), home.getName(),
                typed.text());
        } catch (final RuntimeException unshaped) {
            // A parameter's type decides these compile refusals as the account makes them: set-operator branches
            // it types apart, and a comparison it cannot meet.
            final String message = String.valueOf(unshaped.getMessage());
            if (typed.substitutedAny() && (message.contains(INCONSISTENT_BRANCHES)
                    || message.contains(UNCONVERTIBLE_OPERAND))) {
                throw new RuntimeException(typed.inAccountWords(message, queryExecutor.getFunctionRegistry(), catalog),
                    unshaped);
            }
            return;
        }
        if (shape == null) {
            return;
        }
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(body);
        if (shape.size() > 1) {
            final StringBuilder row = new StringBuilder("ROW(");
            for (int i = 0; i < shape.size(); i++) {
                row.append(i > 0 ? ", " : "").append(query != null && isNullColumn(query, body, i) ? UNTYPED_NULL
                    : spellColumn(shape.get(i).getDataType()));
            }
            throw new RuntimeException("Declared return type '" + spell(declared)
                + "' is incompatible with actual return type '" + row.append(')') + "'");
        }
        if (shape.size() != 1 || shape.get(0).getDataType() == null
                || query != null && isNullColumn(query, body, 0)) {
            return;
        }
        final DataType actual = shape.get(0).getDataType();
        final ReturnTypeFamily actualFamily = ReturnTypeFamily.of(actual);
        if (actualFamily == null
                || actualFamily == declaredFamily && !ReturnTypeFamily.precisionDiffers(declared, actual)) {
            return;
        }
        throw new RuntimeException("Declared return type '" + spell(declared)
            + "' is incompatible with actual return type '" + spellColumn(actual) + "'");
    }

    /**
     * A compiled column's type as the refusal spells it: as the column carries it, so a table's VARCHAR is
     * {@code VARCHAR(16777216)} and its BINARY {@code BINARY(8388608)}, where a cast to bare VARCHAR already
     * carries its own 134217728.
     */
    static String spellColumn(final DataType type) {
        if (type instanceof StringType) {
            return type.getName().toUpperCase() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return "BINARY(" + ((BinaryType) type).getMaxLength() + ")";
        }
        return spell(type);
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
    static String spell(final DataType type) {
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
