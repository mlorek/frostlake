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
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.VolatileCalls;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The constant parts of an expression a plan writes, folded to their values as the account computes them while the
 * statement compiles (live-verified): arithmetic and concatenation over constants, a deterministic built-in function
 * over constants ({@code ABS(-5)} as {@code 5}, {@code UPPER('a')} as {@code 'A'}, {@code CURRENT_DATE()} as the
 * day), a SQL function over constants, called by name or by its SYSTEM$REFERENCE token, and a FROM-less scalar
 * subquery of a constant ({@code (SELECT 'a')}). A number is written with its scale ({@code 1.5 * 2} as {@code 3.0}),
 * a text and a date in quotes. Anything reading a column, a function that answers anew each time it is computed,
 * any other context function, an aggregate or a window, a function of another language, and a value of another type
 * are left as written.
 */
final class PlanConstantFold {

    /** The context function the account folds; it reads the statement's clock, which compiling fixes. */
    private static final List<String> FIXED_CONTEXT = Arrays.asList("CURRENT_DATE");

    private PlanConstantFold() {
    }

    /**
     * @param executor   the executor that computes the value
     * @param expression a part of an expression the plan writes
     * @return the text the plan writes for its value, or null where it writes the part as written
     */
    static String folded(final QueryExecutor executor, final Expression expression) {
        if (expression instanceof LiteralExpression || !foldable(executor, expression)) {
            return null;
        }
        final Object value;
        try {
            value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .evaluate(expression, new Row(new ArrayList<Object>()));
        } catch (final RuntimeException unfolded) {
            return null;
        }
        return textOf(value);
    }

    /** The plan's text of a folded value, or null for a value of a type it does not fold. */
    private static String textOf(final Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        return value instanceof LocalDate ? "'" + value + "'" : null;
    }

    /** Whether an expression is computed from constants alone, by the parts the account folds. */
    private static boolean foldable(final QueryExecutor executor, final Expression expression) {
        if (expression instanceof LiteralExpression) {
            return true;
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS)
                && foldable(executor, unary.getOperand());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            switch (binary.getOperator()) {
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case MODULO:
                case CONCAT:
                    return foldable(executor, binary.getLeft()) && foldable(executor, binary.getRight());
                default:
                    return false;
            }
        }
        if (expression instanceof CastExpression) {
            return foldable(executor, ((CastExpression) expression).getExpression());
        }
        if (expression instanceof FunctionCallExpression) {
            return foldableCall(executor, (FunctionCallExpression) expression);
        }
        if (expression instanceof SubqueryExpression) {
            final Expression item = constantItem(executor, (SubqueryExpression) expression);
            return item != null && foldable(executor, item);
        }
        return false;
    }

    /** Whether a call is one the account computes while compiling, its arguments constants. */
    private static boolean foldableCall(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.isStar() || VolatileCalls.writes(call)) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!foldable(executor, argument)) {
                return false;
            }
        }
        final List<String> parts;
        if (call.getNameExpression() != null) {
            final Object resolved;
            try {
                resolved = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                    executor).evaluate(call.getNameExpression(), null);
            } catch (final RuntimeException unresolved) {
                return false;
            }
            if (resolved == null || ProcedureTokenCalls.namesProcedure(resolved.toString())) {
                return false;
            }
            parts = Arrays.asList(SqlIdentifiers.identifierReferenceParts(resolved.toString()));
            if (!SqlIdentifiers.isIssuedReference(resolved.toString()) && parts.size() == 1
                    && executor.getFunctionRegistry().hasFunction(parts.get(0).toUpperCase(Locale.ROOT))) {
                return false;
            }
        } else {
            if (call.getFunctionName() == null) {
                return false;
            }
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            final FunctionRegistry functions = executor.getFunctionRegistry();
            if (functions.hasAggregateFunction(name)) {
                return false;
            }
            final List<String> written = call.getNameParts();
            if (functions.hasFunction(name) && (written == null || written.size() == 1)) {
                final BuiltInFunction builtIn = functions.getFunction(name);
                return builtIn != null && builtIn.getClass().getPackage() != null
                    && (!builtIn.getClass().getPackage().getName().endsWith(".context")
                        || FIXED_CONTEXT.contains(name))
                    && !builtIn.getClass().getPackage().getName().endsWith(".crypto");
            }
            parts = written;
        }
        return sqlFunctions(executor, parts);
    }

    /**
     * The body a plan writes for a call of a SQL function over columns or constants, as the account inlines it
     * (live-verified): {@code fn(n)} of {@code x + 1} as {@code E.N + 1}. Only a function of one overload, not secure,
     * whose body is an expression rather than a query, called with one argument for each parameter, each a column or
     * a constant; null for any other call.
     *
     * @param executor the executor whose catalog holds the function
     * @param call     the call
     * @return the body and its parameters, or null
     */
    static InlinedFunctionBody inlined(final QueryExecutor executor, final FunctionCallExpression call) {
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isStar()
                || call.getArgumentNames() != null) {
            return null;
        }
        final List<String> parts = call.getNameParts();
        if (parts == null || parts.size() == 1
                && executor.getFunctionRegistry().hasFunction(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return null;
        }
        for (final Expression argument : call.getArguments()) {
            if (!(argument instanceof LiteralExpression || argument instanceof ColumnReferenceExpression)) {
                return null;
            }
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts);
            if (overloads == null || overloads.size() != 1) {
                return null;
            }
            final Function function = overloads.get(0);
            final String body = function.getBody() == null ? null : function.getBody().trim();
            if (!"SQL".equals(function.getLanguage()) || function.isTableFunction() || function.isSecure()
                    || body == null || body.regionMatches(true, 0, "SELECT", 0, 6)
                    || function.getParameters().size() != call.getArguments().size()) {
                return null;
            }
            final List<String> names = new ArrayList<>();
            for (final Parameter parameter : function.getParameters()) {
                names.add(parameter.getName().toUpperCase(Locale.ROOT));
            }
            return new InlinedFunctionBody(ExpressionEvaluator.parse(body), names);
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** The user-defined functions a name reaches, or null where it reaches none. */
    private static List<Function> overloadsOf(final QueryExecutor executor, final List<String> parts) {
        if (parts == null || parts.isEmpty() || parts.size() > 3) {
            return null;
        }
        final String databaseName = parts.size() == 3 ? parts.get(0) : executor.getCatalog().getCurrentDatabase();
        final String schemaName = parts.size() >= 2 ? parts.get(parts.size() - 2)
            : executor.getCatalog().getCurrentSchema();
        final Database database = executor.getCatalog().databaseExact(databaseName);
        final Schema schema = database.schemaExact(schemaName);
        final List<Function> overloads = schema.getFunctionOverloads(parts.get(parts.size() - 1));
        return overloads == null || overloads.isEmpty() ? null : overloads;
    }

    /** Whether a name reaches user-defined functions, every overload of them written in SQL. */
    private static boolean sqlFunctions(final QueryExecutor executor, final List<String> parts) {
        if (parts == null || parts.isEmpty() || parts.size() > 3) {
            return false;
        }
        try {
            final List<Function> overloads = overloadsOf(executor, parts);
            if (overloads == null) {
                return false;
            }
            for (final Function function : overloads) {
                if (!"SQL".equals(function.getLanguage()) || function.isTableFunction()
                        || answersAnew(function.getBody())) {
                    return false;
                }
            }
            return true;
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /** Whether a SQL function's body calls a function that answers anew each time, or cannot be read. */
    private static boolean answersAnew(final String body) {
        if (body == null) {
            return true;
        }
        try {
            final String text = body.trim();
            return VolatileCalls.writes(ExpressionEvaluator.parse(text.regionMatches(true, 0, "SELECT", 0, 6)
                ? text.substring(6) : text));
        } catch (final RuntimeException unread) {
            return true;
        }
    }

    /**
     * The item a plan writes in place of a FROM-less scalar subquery of one SELECT whose item calls a procedure's
     * token, as the account inlines it (live-verified): {@code (SELECT IDENTIFIER($tok)(4))} as {@code PR(X)}.
     *
     * @param executor the executor that resolves the call
     * @param subquery the subquery
     * @return the item, or null for any other subquery
     */
    static Expression inlinedItem(final QueryExecutor executor, final SubqueryExpression subquery) {
        final Expression item = constantItem(executor, subquery);
        if (item == null) {
            return null;
        }
        try {
            final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(
                subquery.getSubquery());
            return ProcedureTokenCalls.holdsPlannedCall(executor, statement) ? item : null;
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** The one item of a FROM-less scalar subquery of one SELECT, or null for any other subquery. */
    private static Expression constantItem(final QueryExecutor executor, final SubqueryExpression subquery) {
        if (subquery.isExistence() || subquery.isLateral()) {
            return null;
        }
        try {
            final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(
                subquery.getSubquery());
            if (statement == null || !statement.setOperator().isEmpty() || statement.selectOperand().size() != 1
                    || statement.orderByClause() != null || statement.limitClause() != null) {
                return null;
            }
            final FrostlakeParser.SelectClauseContext clause = statement.selectOperand(0).selectClause();
            if (clause == null || clause.tableExpression() != null || clause.whereClause() != null
                    || clause.selectList().selectItem().size() != 1
                    || !(clause.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final ParseTree value = SelectItemAccessors.getItemValueExpr(clause.selectList().selectItem(0));
            return value == null ? null : ExpressionEvaluator.parse(ParseTreeText.getOriginalText(
                SelectItemAccessors.getItemExpression(clause.selectList().selectItem(0))));
        } catch (final RuntimeException unread) {
            return null;
        }
    }
}
