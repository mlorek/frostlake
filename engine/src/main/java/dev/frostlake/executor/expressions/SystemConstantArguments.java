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

import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The arguments a SYSTEM$ function answers from while the statement compiles, which must therefore be constant
 * (live-verified). Constant means read from no row: a literal, an expression over literals, a session variable and
 * a query that reads no table are; a column, anything over one and a query with a FROM are not, over a table with
 * no row and under a WHERE that keeps none alike.
 *
 * <pre>
 *   SYSTEM$CLUSTERING_DEPTH(n)              argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be constant,
 *                                           found 'T.N'
 *   SYSTEM$CLUSTERING_DEPTH('t', n)         argument 1 to function …, and a predicate that is one argument 2
 *   SYSTEM$CLUSTERING_DEPTH(1)   … (TRUE)   … found 'TO_CHAR(1)'   … found 'BOOLEAN_TO_TEXT(TRUE)'
 *   SYSTEM$CLUSTERING_INFORMATION(n)        argument 0 to function …; a number is taken there
 *   SYSTEM$LAST_CHANGE_COMMIT_TIME(n)       argument 0 to function …; (1) found '1', (1.5) found
 *                                           'CAST(1.5 AS NUMBER(38,0))'
 *   SYSTEM$STREAM_HAS_DATA(n)               argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be constant, …
 *   SYSTEM$STREAM_HAS_DATA(TRUE)            … found 'CAST(TRUE AS VARCHAR(134217728))', and (NULL) found 'null'
 *   SYSTEM$STREAM_GET_TABLE_TIMESTAMP(n)    argument 1 to function …; (1) found 'CAST(1 AS VARCHAR(134217728))'
 *   SYSTEM$WAIT(n)   SYSTEM$WAIT(0, n)      argument 0 / 1 to function SqlIdentifier{qualifierNames=[],
 *                                           identifierName=SYSTEM$WAIT} needs to be constant, found 'T.N'
 *   SYSTEM$WAIT(1.5)   SYSTEM$WAIT(1 + 0)   … found 'CAST(1.5 AS NUMBER(18,0))'   … found '1 + 0'
 * </pre>
 *
 * <p>The stream functions count their argument from one, every other function from zero.
 *
 * <p>SYSTEM$GET_TAG reads its arguments before a name in them is resolved, so a bare column is echoed as written,
 * a column inside an expression is no identifier at all, and a query — even one that reads no table — prints as
 * parsed:
 *
 * <pre>
 *   SYSTEM$GET_TAG(n, 'tt', 'TABLE')        Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0:
 *                                           constant arguments expected
 *   … (t.n, …)   … ("n", …)                 [T.N]   ["n"]
 *   … (UPPER(n), …)                         invalid identifier 'N', at the name
 *   … ((SELECT 'e1'), …)                    [(SELECT 'e1' AS "'E1'" FROM (VALUES (null)) DUAL)]
 * </pre>
 *
 * <p>SYSTEM$GET_TAG_ALLOWED_VALUES is judged when a statement that reads it compiles — a view over it is created
 * — and takes only what the plan folds before that: a literal, a session variable, GETVARIABLE, a context
 * function, {@code ||}, CONCAT, UPPER and LOWER over those, and a query of one such item and nothing else. A cast,
 * written or implied, and any other call is refused with the argument as the plan prints it:
 *
 * <pre>
 *   SYSTEM$GET_TAG_ALLOWED_VALUES(n)        Invalid value [T.N] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES',
 *                                           parameter 0: constant arguments expected
 *   … (UPPER(n))   … ('e' || n)             [UPPER(T.N)]   ['e' || T.N]
 *   … ((SELECT n FROM t LIMIT 1))           [(SELECT T.N AS "N" FROM T AS T LIMIT 1 OFFSET 0)]
 *   … ('e1'::VARCHAR(2))   … (TRIM(' e1 '))    ['e1']   [TRIM(' e1 ')]
 *   … ('e' || 1)                            ['e' || (CAST(1 AS VARCHAR(134217728)))]
 * </pre>
 */
final class SystemConstantArguments {

    /** The functions whose arguments name a table, its keys and a predicate. */
    private static final Set<String> CLUSTERING = Set.of(
        "SYSTEM$CLUSTERING_DEPTH", "SYSTEM$CLUSTERING_INFORMATION", "SYSTEM$CLUSTERING_RATIO");

    /** The functions that fold over constant text before SYSTEM$GET_TAG_ALLOWED_VALUES is judged. */
    private static final Set<String> FOLDED_EARLY = Set.of("UPPER", "LOWER", "CONCAT", "GETVARIABLE");

    /** How the account names SYSTEM$WAIT in this refusal. */
    private static final String WAIT_NAME = "SqlIdentifier{qualifierNames=[], identifierName=SYSTEM$WAIT}";

    /** The plan's name for the typed NULL a missing ELSE is, which this sentence prints bare. */
    private static final String TYPED_NULL = "SYSTEM$NULL_TO_TEXT(null)";

    private SystemConstantArguments() {
    }

    /**
     * Refuses a SYSTEM$ call one of whose constant arguments reads a row.
     *
     * @param funcName the call's name, upper-cased
     * @param args     its arguments
     * @param visitor  the evaluator of the query the call stands in
     */
    static void judge(final String funcName, final List<Expression> args, final ExpressionEvaluatorVisitor visitor) {
        judge(funcName, args, null, visitor);
    }

    /**
     * {@link #judge(String, List, ExpressionEvaluatorVisitor)}, with the call a refusal of its arguments' types is
     * placed at.
     *
     * @param funcName the call's name, upper-cased
     * @param args     its arguments
     * @param call     the call, or null where it is not at hand
     * @param visitor  the evaluator of the query the call stands in
     */
    static void judge(final String funcName, final List<Expression> args, final FunctionCallExpression call,
                      final ExpressionEvaluatorVisitor visitor) {
        if (args.isEmpty() || !funcName.startsWith("SYSTEM$")) {
            return;
        }
        if ("SYSTEM$GET_TAG".equals(funcName)) {
            judgeTagLookup(args, visitor);
        } else if (PolicyObjectTagCalls.names(funcName)) {
            PolicyObjectTagCalls.judge(funcName, args.get(0));
        } else if ("SYSTEM$GET_TAG_ALLOWED_VALUES".equals(funcName)) {
            if (!RelationShapeOnly.isActive() && !foldsEarly(args.get(0), visitor)) {
                throw new RuntimeException(SqlCompilationError.of("Invalid value ["
                    + visitor.strictPlanText(args.get(0)).replace(TYPED_NULL, "null")
                    + "] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected"));
            }
        } else if (CLUSTERING.contains(funcName)) {
            final boolean information = "SYSTEM$CLUSTERING_INFORMATION".equals(funcName);
            for (int i = 0; i < args.size() && i < (information ? 2 : 3); i++) {
                requireConstant(funcName, i, args.get(i), visitor);
                final DataType type = information ? null : visitor.inferStaticType(args.get(i));
                if (type instanceof NumericType || type instanceof BooleanType) {
                    // What is no text is read as the text it converts to, which is no constant.
                    throw notConstant(funcName, i, (type instanceof NumericType ? "TO_CHAR(" : "BOOLEAN_TO_TEXT(")
                        + visitor.strictPlanText(args.get(i)) + ")");
                }
            }
        } else if ("SYSTEM$LAST_CHANGE_COMMIT_TIME".equals(funcName)) {
            judgeChangedObject(funcName, args.get(0), visitor);
        } else if ("SYSTEM$STREAM_GET_TABLE_TIMESTAMP".equals(funcName)) {
            judgeStreamName(funcName, args.get(0), visitor);
        } else if ("SYSTEM$WAIT".equals(funcName)) {
            for (int i = 0; i < args.size() && i < 2; i++) {
                requireConstant(WAIT_NAME, i, args.get(i), visitor);
            }
            judgeWaitedAmount(args.get(0), call, visitor);
        }
    }

    /**
     * SYSTEM$WAIT's amount: a whole number the plan need not compute. A number written with a fraction, and one
     * that an arithmetic operation or a cast makes, is no constant - found as the whole number it would be cast to -
     * and a BOOLEAN is no amount at all; a text, a FLOAT, a decimal that is whole and a query that reads no table
     * are taken.
     */
    private static void judgeWaitedAmount(final Expression amount, final FunctionCallExpression call,
                                          final ExpressionEvaluatorVisitor visitor) {
        final DataType type = visitor.inferStaticType(amount);
        if (type instanceof BooleanType || type instanceof ArrayType) {
            final SourcePosition at = call == null || call.getPosition() == null ? null
                : ExpressionSource.resolve(call.getPosition());
            final String refusal = "Invalid argument types for function 'SYSTEM$WAIT': ("
                + (type instanceof ArrayType ? "ARRAY" : "BOOLEAN") + ")";
            throw new RuntimeException(at == null ? SqlCompilationError.of(refusal)
                : SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), refusal));
        }
        if (!(type instanceof NumericType) || NumericType.isApproximate(type)) {
            return;
        }
        final Expression unsigned = amount instanceof UnaryOperationExpression
            ? ((UnaryOperationExpression) amount).getOperand() : amount;
        final String written = visitor.strictPlanText(amount);
        if (unsigned instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) unsigned).getValue();
            if (value instanceof Number && new BigDecimal(value.toString()).stripTrailingZeros().scale() > 0) {
                throw notConstant(WAIT_NAME, 0, "CAST(" + written + " AS NUMBER(18,0))");
            }
            return;
        }
        // What the plan would have to compute: an arithmetic operation, and a cast to a number. A query that
        // reads no table, a variable and a call are taken.
        if (unsigned instanceof BinaryOperationExpression || unsigned instanceof CastExpression) {
            throw notConstant(WAIT_NAME, 0, ((NumericType) type).getScale() > 0
                ? "CAST(" + written + " AS NUMBER(18,0))" : written);
        }
    }

    /**
     * Refuses a stream function whose name reads a row, is NULL, or is a BOOLEAN; SYSTEM$STREAM_GET_TABLE_TIMESTAMP
     * refuses a number so too, where SYSTEM$STREAM_HAS_DATA reads one as a name.
     *
     * @param funcName the function's name, upper-cased
     * @param name     the argument naming the stream
     * @param visitor  the evaluator of the query the call stands in
     */
    static void judgeStreamName(final String funcName, final Expression name,
                                final ExpressionEvaluatorVisitor visitor) {
        if (readsARow(name)) {
            throw notConstant(funcName, 1, visitor.strictPlanText(name));
        }
        if (isNull(name)) {
            throw notConstant(funcName, 1, "null");
        }
        final DataType type = visitor.inferStaticType(name);
        if (type instanceof BooleanType
                || type instanceof NumericType && "SYSTEM$STREAM_GET_TABLE_TIMESTAMP".equals(funcName)) {
            throw notConstant(funcName, 1, "CAST(" + visitor.strictPlanText(name) + " AS VARCHAR(134217728))");
        }
    }

    /**
     * Refuses a task named by a number: the name is a text, and a number converted to one is no constant the
     * function takes (live-verified).
     *
     * @param funcName the function's name, upper-cased
     * @param name     the argument naming the task
     * @param visitor  the evaluator of the query the call stands in
     */
    static void judgeTaskName(final String funcName, final Expression name, final ExpressionEvaluatorVisitor visitor) {
        if (visitor.inferStaticType(name) instanceof NumericType) {
            throw notConstant(funcName, 1, "CAST(" + visitor.strictPlanText(name) + " AS VARCHAR(134217728))");
        }
    }

    /**
     * Whether an argument reads a row: a column anywhere in it, or a query that reads a relation.
     *
     * @param argument the argument
     * @return whether the plan cannot fold it
     */
    static boolean readsARow(final Expression argument) {
        final RelationReferenceWalk columns = new RelationReferenceWalk();
        argument.accept(columns);
        if (columns.found()) {
            return true;
        }
        final SubqueryCollectWalk queries = new SubqueryCollectWalk();
        argument.accept(queries);
        for (final SubqueryExpression query : queries.subqueries()) {
            if (soleItemOf(query) == null) {
                return true;
            }
        }
        return false;
    }

    /** SYSTEM$LAST_CHANGE_COMMIT_TIME's argument: a name in text, which a number or a BOOLEAN is not. */
    private static void judgeChangedObject(final String funcName, final Expression argument,
                                           final ExpressionEvaluatorVisitor visitor) {
        requireConstant(funcName, 0, argument, visitor);
        final DataType type = visitor.inferStaticType(argument);
        if (type instanceof BooleanType) {
            throw new RuntimeException(SqlCompilationError.of("Can not convert parameter '"
                + visitor.strictPlanText(argument) + "' of type [BOOLEAN] into expected type [NUMBER(38,0)]"));
        }
        if (type instanceof NumericType) {
            final String written = visitor.strictPlanText(argument);
            throw notConstant(funcName, 0, ((NumericType) type).getScale() > 0
                ? "CAST(" + written + " AS NUMBER(38,0))" : written);
        }
    }

    private static void requireConstant(final String name, final int position, final Expression argument,
                                        final ExpressionEvaluatorVisitor visitor) {
        if (readsARow(argument)) {
            throw notConstant(name, position, visitor.strictPlanText(argument));
        }
    }

    private static RuntimeException notConstant(final String name, final int position, final String found) {
        return new RuntimeException(SqlCompilationError.of("argument " + position + " to function " + name
            + " needs to be constant, found '" + found + "'"));
    }

    private static boolean isNull(final Expression argument) {
        final Expression value = argument instanceof CastExpression
            ? ((CastExpression) argument).getExpression() : argument;
        return value instanceof LiteralExpression && ((LiteralExpression) value).getType() == LiteralType.NULL;
    }

    /**
     * The one item of a query that is nothing but {@code SELECT <item>}: no FROM, no other clause, no second
     * item. Null for every other query, and for one that does not parse as a query at all.
     */
    private static Expression soleItemOf(final SubqueryExpression query) {
        final FrostlakeParser.SelectStatementContext parsed;
        try {
            parsed = SubqueryPlanPrint.parsed(query.getSubquery());
        } catch (final RuntimeException notRead) {
            return null;
        }
        if (parsed == null || parsed.selectOperand().size() != 1 || parsed.withClause() != null
                || parsed.orderByClause() != null || parsed.limitClause() != null || parsed.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = parsed.selectOperand(0).selectClause();
        if (select == null || select.FROM() != null || select.whereClause() != null || select.DISTINCT() != null
                || select.topClause() != null || select.intoClause() != null || select.connectByClause() != null
                || select.groupByClause() != null || select.havingClause() != null
                || select.qualifyClause() != null || select.selectList().selectItem().size() != 1) {
            return null;
        }
        final FrostlakeParser.SelectItemContext item = select.selectList().selectItem(0);
        return item instanceof FrostlakeParser.ExprItemContext
            ? new ExpressionAstBuilder().build(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
    }

    /**
     * Whether SYSTEM$GET_TAG_ALLOWED_VALUES takes an argument as a constant: what the plan has folded by the
     * time the call is judged.
     */
    private static boolean foldsEarly(final Expression argument, final ExpressionEvaluatorVisitor visitor) {
        if (argument instanceof LiteralExpression || argument instanceof SessionVarExpression || isNull(argument)) {
            return true;
        }
        if (argument instanceof SubqueryExpression) {
            final Expression item = soleItemOf((SubqueryExpression) argument);
            return item != null && foldsEarly(item, visitor);
        }
        if (argument instanceof BinaryOperationExpression) {
            final BinaryOperationExpression joined = (BinaryOperationExpression) argument;
            return joined.getOperator() == BinaryOperator.CONCAT
                && foldsToText(joined.getLeft(), visitor) && foldsToText(joined.getRight(), visitor);
        }
        if (argument instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) argument;
            if (call.getNameExpression() != null) {
                return false;
            }
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if (call.getArguments().isEmpty()) {
                // A context function is a literal by the time the call is judged.
                return name.startsWith("CURRENT_");
            }
            if (!FOLDED_EARLY.contains(name)) {
                return false;
            }
            for (final Expression operand : call.getArguments()) {
                if (!foldsToText(operand, visitor)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** {@link #foldsEarly} for an operand, which must also be text or NULL: a number beside text is cast. */
    private static boolean foldsToText(final Expression operand, final ExpressionEvaluatorVisitor visitor) {
        if (!foldsEarly(operand, visitor)) {
            return false;
        }
        if (operand instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) operand).getType();
            return type == LiteralType.STRING || type == LiteralType.NULL;
        }
        return true;
    }

    /**
     * SYSTEM$GET_TAG's arguments, read before any name is resolved: a column is echoed as written, a query as
     * parsed, and a column inside anything else is no identifier. A block's variable written by its bare name is
     * no column: the call reads its value (live-verified).
     */
    private static void judgeTagLookup(final List<Expression> args, final ExpressionEvaluatorVisitor visitor) {
        for (int i = 0; i < args.size() && i < 3; i++) {
            final Expression argument = args.get(i);
            if (visitor != null && visitor.namesBlockVariable(argument)) {
                continue;
            }
            if (argument instanceof ColumnReferenceExpression) {
                throw new RuntimeException(invalidTagValue(writtenName((ColumnReferenceExpression) argument), i));
            }
            if (argument instanceof SubqueryExpression) {
                final String echo = new ParsedQueryEcho().echo(((SubqueryExpression) argument).getSubquery());
                if (echo != null) {
                    throw new RuntimeException(invalidTagValue("(" + echo + ")", i));
                }
                continue;
            }
            final FirstColumnWalk first = new FirstColumnWalk();
            argument.accept(first);
            if (first.column() != null && !(visitor != null && visitor.namesBlockVariable(first.column()))) {
                final SourcePosition at = first.column().getPosition() == null ? null
                    : ExpressionSource.resolve(first.column().getPosition());
                final String name = writtenName(first.column());
                throw new RuntimeException(at == null ? SqlCompilationError.invalidIdentifier(name)
                    : SqlCompilationError.invalidIdentifier(at.getLine(), at.getCharPositionInLine(), name));
            }
        }
    }

    private static String invalidTagValue(final String echo, final int parameter) {
        return SqlCompilationError.of("Invalid value [" + echo + "] for function 'SYSTEM$GET_TAG', parameter "
            + parameter + ": constant arguments expected");
    }

    /** A column as written, its name spelled as a refusal spells one: quoted only where it has to be. */
    static String writtenName(final ColumnReferenceExpression column) {
        final String name = SqlIdentifiers.spellCanonical(column.getColumnName());
        return column.isQualified() ? column.getTableName().toUpperCase(Locale.ROOT) + "." + name : name;
    }
}
