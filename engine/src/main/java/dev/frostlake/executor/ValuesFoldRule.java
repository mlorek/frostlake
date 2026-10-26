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
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.UntypedNullFold;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The expressions a VALUES clause refuses because live's compiler does not fold them to a constant: a division,
 * and the math functions below, anywhere in the item. Live refuses
 * {@code INSERT INTO t VALUES (SQRT(4))} and {@code SELECT $1 FROM VALUES (1 / 3)} as
 * {@code Invalid expression [SQRT(4.0)] in VALUES clause}, while FLOOR, MOD, DIV0, GREATEST, COALESCE, NVL,
 * TO_DOUBLE, RADIANS, DEGREES, ABS, PI, BITOR and {@code + - * %} are folded and taken (live-verified, each).
 *
 * <p>The item's constants fold first, and what folds away is taken (live-verified):
 *
 * <pre>
 *   1 / NULL, SQRT(NULL), SQRT(4) + NULL     NULL: a NULL operand folds the operation, a refused one included
 *   1 / (SELECT NULL), MIN over no rows      NULL too
 *   4 / 2, 2.5 / 2.5, 3 / -3, 4 / (SELECT 2) a whole quotient folds; 0 / 2 and 1 / 2 do not
 *   2::FLOAT / 2::FLOAT, '2' / '2'           a FLOAT division never folds: [2.0 / 2.0]
 * </pre>
 *
 * <p>The echo is live's rewritten expression, not the text as written:
 *
 * <pre>
 *   SQRT(4)                     SQRT(4.0)             a float parameter prints its argument as a FLOAT
 *   ROUND(1.5), SIGN(-1)        ROUND(1.5), SIGN(-1)  an exact parameter keeps its spelling
 *   CEIL(1.5::FLOAT)            CEIL(1.5)             a FLOAT cast of a literal is that literal
 *   SQRT(4) + 1                 (SQRT(4.0)) + 1.0     a call inside an operator is parenthesised
 *   -SQRT(4)                    NEGATE(SQRT(4.0))
 *   1::FLOAT / 3                1.0 / 3.0             a FLOAT operation prints both operands as FLOATs
 *   TO_VARCHAR(1 / 3)           CAST(1 / 3 AS VARCHAR(134217728))
 *   SQRT(4)::NUMBER             CAST(SQRT(4.0) AS NUMBER(38,0))
 *   CONCAT('a', 1 / 2)          CONCAT('a', CAST(1 / 2 AS VARCHAR(134217728)))
 *   IFF(TRUE, SQRT(4), 1)       SQRT(4.0)             a constant condition folds to its branch
 *   CASE WHEN TRUE THEN SQRT(4) END   ENSURE_NULLABLE(SQRT(4.0))
 *   4 / 2 + 1 / 2               2 + (1 / 2)           a folded division is its quotient
 *   1 / '2.50', 1 / 'a'         1 / 2.5, 1 / (TO_NUMBER('a', 18, 5))   a text operand is read as a number
 *   1 / (SELECT 2 + 3)          1 / 5                 a constant subquery is its value
 *   1 / (SELECT MIN(id) FROM t) 1 / 0                 and so is one the statistics answer (see ConstantSubqueries)
 *   1 / (SELECT 1 / 3)          1 / (CAST(1 / 3 AS NUMBER(7,6)))   an item that does not fold, cast to its type
 *   (SELECT MIN(id) FROM t) / 2 SCALED_ROUND_INT_DIVIDE(0, 2)      a NUMBER(38,0) divided by a whole number
 *   1 / (SELECT MIN(id) FROM t WHERE id &gt; 0)
 *                               1 / (CAST((SELECT MIN(T.ID) AS "MIN(ID)" FROM T AS T WHERE T.ID &gt; 0)
 *                               AS NUMBER(38,0)))
 *                               a subquery that does not fold, as the plan holds it (see SubqueryPlanText), cast to its
 *                               type where its item carries a column's or a literal's type, bare where a SUM, a COUNT,
 *                               an AVG or another call types it
 *   1 / (SELECT MIN(id) FROM t) / 2   (CAST(1 / 0 AS NUMBER(13,12))) / 2   a quotient divided again is cast first
 * </pre>
 *
 * <p>An item that is a whole query of no constant item is refused, uncast, as the plan holds it when it does not fold
 * (see ValuesSubqueryShapes for the queries judged), a table of one row folded into the query (live-verified):
 *
 * <pre>
 *   (SELECT MAX(12345))           (SELECT MAX(12345) AS "MAX(12345)" FROM (VALUES (null)) DUAL)
 *   (SELECT v FROM one)           (SELECT 12345 AS "V" FROM TABLE (GENERATOR(ROWCOUNT =&gt; 1, rowCount =&gt; 1)))
 *   (SELECT v FROM two)           (SELECT TWO.V AS "V" FROM TWO AS TWO)
 *   (SELECT SUM(v) FROM one)      (SELECT SUM(12345) AS "SUM(V)" FROM ONE AS ONE)
 * </pre>
 *
 * <p>A shape outside these echoes as written.
 */
final class ValuesFoldRule {

    /** The functions refused in VALUES whose parameters are FLOATs, so a literal argument prints as one. */
    private static final Set<String> FLOAT_FUNCTIONS = Set.of(
        "SQRT", "EXP", "LN", "LOG", "POWER", "POW", "SIN", "COS", "TAN", "COT", "ASIN", "ACOS", "ATAN",
        "ATAN2", "SINH", "COSH", "TANH", "ASINH", "ACOSH", "ATANH", "SQUARE", "CBRT", "HAVERSINE");

    /** The functions refused in VALUES whose arguments keep their spelling. */
    private static final Set<String> EXACT_FUNCTIONS = Set.of(
        "ROUND", "CEIL", "TRUNC", "TRUNCATE", "SIGN", "FACTORIAL", "BITAND", "BITXOR", "BITNOT",
        "BITSHIFTLEFT", "HASH", "WIDTH_BUCKET");

    /** The operators a NULL operand folds to NULL. */
    private static final Set<BinaryOperator> ARITHMETIC = Set.of(
        BinaryOperator.ADD, BinaryOperator.SUBTRACT, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE,
        BinaryOperator.MODULO);

    private static final String VARCHAR_CAST = " AS VARCHAR(134217728))";

    /** The precision past which a whole dividend leaves no room for a quotient's fraction. */
    private static final int WIDE_WHOLE_PRECISION = 32;

    /** The scale a quotient is cast to before it is divided again. */
    private static final int DIVISION_SCALE = 12;

    /** A subquery's value while it is not known to fold. */
    private static final Object UNFOLDED = new Object();

    private final ExpressionEvaluator typer;

    private final QueryExecutor executor;

    /** Per subquery, its sole constant item, or null when it has none. */
    private final Map<SubqueryExpression, Expression> items = new IdentityHashMap<>();

    /** Per subquery that has no constant item, the value the statistics answer, or UNFOLDED. */
    private final Map<SubqueryExpression, Object> values = new IdentityHashMap<>();

    private ValuesFoldRule(final ExpressionEvaluator typer, final QueryExecutor executor) {
        this.typer = typer;
        this.executor = executor;
    }

    /**
     * The refusal of a VALUES item live does not fold, or null when the item is taken.
     *
     * @param item      the item, parsed
     * @param written   the item as written, the echo of a shape the rewrite does not cover
     * @param typer     an evaluator that types the item's operations
     * @param executor  the executor that reads the item's subqueries
     * @return the refusal to throw, or null
     */
    static RuntimeException refusalOf(final Expression item, final String written, final ExpressionEvaluator typer,
                                      final QueryExecutor executor) {
        // A name comes first: SQRT(n) is "invalid identifier 'N'", which the item's own reading reports.
        if (item == null || namesAColumn(item)) {
            return null;
        }
        final ValuesFoldRule rule = new ValuesFoldRule(typer, executor);
        if (item instanceof SubqueryExpression && rule.itemOf((SubqueryExpression) item) == null) {
            // A whole item that is a query the plan does not fold is refused as the plan holds it.
            final SubqueryExpression subquery = (SubqueryExpression) item;
            if (executor == null || !ValuesSubqueryShapes.judged(executor, subquery)
                    || ValuesSubqueryShapes.pinnedExtremes(executor, subquery) || rule.foldsToValue(subquery)) {
                return null;
            }
            final String echo = rule.bareSubquery(subquery);
            return new RuntimeException(SqlCompilationError.of(
                "Invalid expression [" + (echo != null ? echo : written.trim()) + "] in VALUES clause"));
        }
        // The constants fold, and the subqueries are read, only for an item that holds a refused shape at all.
        if (!rule.holdsUnfolded(item, false) || !rule.holdsUnfolded(item, true)) {
            return null;
        }
        final String echo = rule.print(item, false);
        return new RuntimeException(SqlCompilationError.of(
            "Invalid expression [" + (echo != null ? echo : written.trim()) + "] in VALUES clause"));
    }

    /**
     * An item that is a query of no constant item, as the plan holds it, uncast: {@code (SELECT 12345 AS "V" FROM TABLE
     * (GENERATOR(ROWCOUNT => 1, rowCount => 1)))} for a table of one row, {@code (SELECT MAX(12345) AS "MAX(12345)"
     * FROM (VALUES (null)) DUAL)} for an aggregate over no table (live-verified).
     */
    private String bareSubquery(final SubqueryExpression subquery) {
        if (executor == null) {
            return null;
        }
        try {
            final String planned = SubqueryPlanText.printedOrNull(executor,
                executor.parseSelectStatement(subquery.getSubquery()), new HashSet<String>());
            return planned != null ? planned : typer.plannedText(subquery);
        } catch (final RuntimeException unprintable) {
            return null;
        }
    }

    /**
     * Whether a refused function or a division appears under {@code node}, with its constants folded first when
     * {@code folding}: a NULL operand and a whole quotient fold away.
     */
    private boolean holdsUnfolded(final Expression node, final boolean folding) {
        if (folding && foldsToNull(node)) {
            return false;
        }
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            if (call.getFunctionName() != null && refused(call.getFunctionName())) {
                return true;
            }
            for (final Expression argument : call.getArguments()) {
                if (holdsUnfolded(argument, folding)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return holdsUnfolded(((CastExpression) node).getExpression(), folding);
        }
        if (node instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) node;
            if (binary.getOperator() == BinaryOperator.DIVIDE) {
                return !folding || quotient(binary) == null;
            }
            return holdsUnfolded(binary.getLeft(), folding) || holdsUnfolded(binary.getRight(), folding);
        }
        if (node instanceof UnaryOperationExpression) {
            return holdsUnfolded(((UnaryOperationExpression) node).getOperand(), folding);
        }
        if (node instanceof CaseExpression) {
            for (final WhenClause clause : ((CaseExpression) node).getWhenClauses()) {
                if (holdsUnfolded(clause.getCondition(), folding) || holdsUnfolded(clause.getResult(), folding)) {
                    return true;
                }
            }
            final Expression otherwise = ((CaseExpression) node).getElseExpression();
            return otherwise != null && holdsUnfolded(otherwise, folding);
        }
        return false;
    }

    /** Whether {@code node} folds to NULL: a NULL, or an operation or a refused function over a NULL operand. */
    private boolean foldsToNull(final Expression node) {
        if (UntypedNullFold.isUntypedNull(node)) {
            return true;
        }
        if (node instanceof CastExpression) {
            return foldsToNull(((CastExpression) node).getExpression());
        }
        if (node instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) node;
            return (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS)
                && foldsToNull(unary.getOperand());
        }
        if (node instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) node;
            return ARITHMETIC.contains(binary.getOperator())
                && (foldsToNull(binary.getLeft()) || foldsToNull(binary.getRight()));
        }
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            // HASH answers a number for a NULL, so it does not fold to one.
            if (call.getFunctionName() == null || !refused(call.getFunctionName())
                    || "HASH".equalsIgnoreCase(call.getFunctionName())) {
                return false;
            }
            for (final Expression argument : call.getArguments()) {
                if (foldsToNull(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof SubqueryExpression) {
            final SubqueryExpression subquery = (SubqueryExpression) node;
            final Expression item = itemOf(subquery);
            return item != null ? foldsToNull(item) : foldsToValue(subquery) && values.get(subquery) == null;
        }
        return false;
    }

    /** The whole quotient a division folds to, or null where it does not fold: a zero, a fraction or a FLOAT. */
    private BigDecimal quotient(final BinaryOperationExpression division) {
        if (isFloat(division)) {
            return null;
        }
        final BigDecimal dividend = valueOf(division.getLeft());
        final BigDecimal divisor = valueOf(division.getRight());
        if (dividend == null || divisor == null || dividend.signum() == 0 || divisor.signum() == 0) {
            return null;
        }
        final BigDecimal[] parts = dividend.divideAndRemainder(divisor);
        return parts[1].signum() == 0 ? parts[0] : null;
    }

    /** The exact number a constant folds to, a text read as one; null for any other value. */
    private BigDecimal valueOf(final Expression node) {
        if (node instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) node;
            if (literal.getType() == LiteralType.STRING) {
                return numberIn(String.valueOf(literal.getValue()));
            }
            return literal.getType() == LiteralType.INTEGER || literal.getType() == LiteralType.DECIMAL
                ? exactNumber(literal.getValue()) : null;
        }
        if (node instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) node;
            final boolean signed = unary.getOperator() == UnaryOperator.NEGATE
                || unary.getOperator() == UnaryOperator.PLUS;
            final BigDecimal operand = signed ? valueOf(unary.getOperand()) : null;
            return operand == null || unary.getOperator() == UnaryOperator.PLUS ? operand : operand.negate();
        }
        if (node instanceof BinaryOperationExpression) {
            return arithmetic((BinaryOperationExpression) node);
        }
        if (node instanceof SubqueryExpression) {
            final SubqueryExpression subquery = (SubqueryExpression) node;
            final Expression item = itemOf(subquery);
            if (item != null) {
                return valueOf(item);
            }
            return foldsToValue(subquery) ? exactNumber(values.get(subquery)) : null;
        }
        return null;
    }

    private BigDecimal arithmetic(final BinaryOperationExpression binary) {
        if (binary.getOperator() == BinaryOperator.DIVIDE) {
            return quotient(binary);
        }
        final BigDecimal left = valueOf(binary.getLeft());
        final BigDecimal right = valueOf(binary.getRight());
        if (left == null || right == null) {
            return null;
        }
        switch (binary.getOperator()) {
            case ADD:
                return left.add(right);
            case SUBTRACT:
                return left.subtract(right);
            case MULTIPLY:
                return left.multiply(right);
            case MODULO:
                return right.signum() == 0 ? null : left.remainder(right);
            default:
                return null;
        }
    }

    /** The sole constant item of a FROM-less subquery, or null when it has none. */
    private Expression itemOf(final SubqueryExpression subquery) {
        if (!items.containsKey(subquery)) {
            items.put(subquery, executor == null ? null : ConstantSubqueries.soleConstantItem(executor, subquery));
        }
        return items.get(subquery);
    }

    /** Whether a subquery with no constant item folds to the value its statistics answer, read once. */
    private boolean foldsToValue(final SubqueryExpression subquery) {
        if (!values.containsKey(subquery)) {
            Object value = UNFOLDED;
            if (executor != null && itemOf(subquery) == null
                    && ConstantSubqueries.foldsInValues(executor, subquery)) {
                try {
                    value = typer.evaluate(subquery, new Row(new ArrayList<>()));
                } catch (final RuntimeException unread) {
                    value = UNFOLDED;
                }
            }
            values.put(subquery, value);
        }
        return values.get(subquery) != UNFOLDED;
    }

    /** Whether a column reference appears anywhere under {@code node}. */
    private static boolean namesAColumn(final Expression node) {
        if (node instanceof ColumnReferenceExpression) {
            return true;
        }
        if (node instanceof FunctionCallExpression) {
            for (final Expression argument : ((FunctionCallExpression) node).getArguments()) {
                if (namesAColumn(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return namesAColumn(((CastExpression) node).getExpression());
        }
        if (node instanceof BinaryOperationExpression) {
            return namesAColumn(((BinaryOperationExpression) node).getLeft())
                || namesAColumn(((BinaryOperationExpression) node).getRight());
        }
        if (node instanceof UnaryOperationExpression) {
            return namesAColumn(((UnaryOperationExpression) node).getOperand());
        }
        return false;
    }

    private static boolean refused(final String name) {
        final String upper = name.toUpperCase(Locale.ROOT);
        return FLOAT_FUNCTIONS.contains(upper) || EXACT_FUNCTIONS.contains(upper);
    }

    /** The echo of {@code node}, its literals printed as FLOATs when {@code asFloat}; null for an unknown shape. */
    private String print(final Expression node, final boolean asFloat) {
        if (node instanceof LiteralExpression) {
            return literal((LiteralExpression) node, asFloat);
        }
        if (node instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) node;
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                return null;
            }
            final String operand = print(unary.getOperand(), asFloat);
            if (operand == null) {
                return null;
            }
            return unary.getOperand() instanceof LiteralExpression ? "-" + operand : "NEGATE(" + operand + ")";
        }
        if (node instanceof CastExpression) {
            return cast((CastExpression) node, asFloat);
        }
        if (node instanceof FunctionCallExpression) {
            return call((FunctionCallExpression) node, asFloat);
        }
        if (node instanceof BinaryOperationExpression) {
            final BigDecimal folded = foldedQuotient(node);
            return folded != null ? number(folded, asFloat) : binary((BinaryOperationExpression) node);
        }
        if (node instanceof SubqueryExpression) {
            return subquery((SubqueryExpression) node, asFloat, false);
        }
        if (node instanceof CaseExpression) {
            final CaseExpression expression = (CaseExpression) node;
            if (expression.getWhenClauses().size() != 1 || expression.getElseExpression() != null
                    || expression.getWhenClauses().get(0).isOperandMatch()
                    || !isTrue(expression.getWhenClauses().get(0).getCondition())) {
                return null;
            }
            final String result = print(expression.getWhenClauses().get(0).getResult(), asFloat);
            return result == null ? null : "ENSURE_NULLABLE(" + result + ")";
        }
        return null;
    }

    /**
     * A folded subquery's echo: its value, or its item cast to the item's type where the item does not fold; null
     * for a subquery that does not fold. Inside an operator a conversion is parenthesised as a call is.
     */
    private String subquery(final SubqueryExpression subquery, final boolean asFloat, final boolean inOperator) {
        final Expression item = itemOf(subquery);
        if (item != null) {
            final BigDecimal value = valueOf(item);
            if (value != null) {
                return number(value, asFloat);
            }
            if (!holdsUnfolded(item, true)) {
                return inOperator ? operand(item, asFloat) : print(item, asFloat);
            }
            final String inner = print(item, asFloat);
            final DataType type = typeOf(item);
            if (inner == null || type == null) {
                return null;
            }
            final String cast = "CAST(" + inner + " AS " + SqlTypeNames.canonical(type) + ")";
            return inOperator ? "(" + cast + ")" : cast;
        }
        if (!foldsToValue(subquery)) {
            return plannedSubquery(subquery, inOperator);
        }
        final Object value = values.get(subquery);
        if (value instanceof Double || value instanceof Float) {
            return floatText(BigDecimal.valueOf(((Number) value).doubleValue()));
        }
        if (value instanceof String && !asFloat) {
            return inOperator ? textAsNumber((String) value) : "'" + ((String) value).replace("'", "\\'") + "'";
        }
        final BigDecimal number = exactNumber(value);
        return number == null ? null : number(number, asFloat);
    }

    /**
     * A subquery that does not fold, echoed as the plan holds it, cast to its type where its one item carries the type
     * of what it reads (see {@link #carriesReadType}); null where the plan's text is not known.
     */
    private String plannedSubquery(final SubqueryExpression subquery, final boolean inOperator) {
        if (executor == null) {
            return null;
        }
        final FrostlakeParser.SelectStatementContext statement;
        try {
            statement = executor.parseSelectStatement(subquery.getSubquery());
        } catch (final RuntimeException unreadable) {
            return null;
        }
        final String planned = SubqueryPlanText.printedOrNull(executor, statement, new HashSet<String>());
        if (planned == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext clause = statement.selectOperand(0).selectClause();
        final Expression item = clause.selectList().selectItem().size() == 1
            && clause.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext
            ? ExpressionEvaluator.parse(ParseTreeText.getOriginalText(
                ((FrostlakeParser.ExprItemContext) clause.selectList().selectItem(0)).booleanExpr())) : null;
        final DataType type = item != null && carriesReadType(item) ? typeOf(subquery) : null;
        if (type == null) {
            return planned;
        }
        final String cast = "CAST(" + planned + " AS " + SqlTypeNames.canonical(type) + ")";
        return inOperator ? "(" + cast + ")" : cast;
    }

    /**
     * Whether an item carries the type of the column or the literal it reads, which the plan casts to the item's type:
     * a column, a literal, their MIN, MAX or ANY_VALUE, and arithmetic over those (live-verified).
     */
    private static boolean carriesReadType(final Expression item) {
        if (item instanceof ColumnReferenceExpression) {
            return true;
        }
        if (item instanceof LiteralExpression) {
            return ((LiteralExpression) item).getType() == LiteralType.INTEGER
                || ((LiteralExpression) item).getType() == LiteralType.DECIMAL;
        }
        if (item instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) item;
            final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
            return ("MIN".equals(name) || "MAX".equals(name) || "ANY_VALUE".equals(name)) && !call.isDistinct()
                && call.getArguments().size() == 1 && carriesReadType(call.getArguments().get(0));
        }
        if (item instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) item;
            return ARITHMETIC.contains(binary.getOperator()) && carriesReadType(binary.getLeft())
                && carriesReadType(binary.getRight());
        }
        return false;
    }

    private static String literal(final LiteralExpression literal, final boolean asFloat) {
        final Object value = literal.getValue();
        if (literal.getType() == LiteralType.STRING) {
            if (!asFloat) {
                return "'" + String.valueOf(value).replace("'", "\\'") + "'";
            }
            try {
                return floatText(DecimalText.parse(String.valueOf(value).trim()));
            } catch (final NumberFormatException notNumeric) {
                return null;
            }
        }
        if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        final BigDecimal number = exactNumber(value);
        return asFloat ? floatText(number) : number.toPlainString();
    }

    /** A number as a FLOAT literal prints: a whole number gains {@code .0}, a fraction keeps its digits. */
    private static String floatText(final BigDecimal number) {
        return number.scale() <= 0 ? number.toBigInteger() + ".0" : number.toPlainString();
    }

    /** A folded value as the echo prints it: without trailing zeros, or as a FLOAT when {@code asFloat}. */
    private static String number(final BigDecimal value, final boolean asFloat) {
        return asFloat ? floatText(value) : value.stripTrailingZeros().toPlainString();
    }

    /** A text read as a number in arithmetic: the number it spells, or its conversion where it spells none. */
    private static String textAsNumber(final String text) {
        final BigDecimal number = numberIn(text);
        return number != null ? number.stripTrailingZeros().toPlainString()
            : "(TO_NUMBER('" + text.replace("'", "\\'") + "', 18, 5))";
    }

    /** The number a text spells, or null when it spells none. */
    private static BigDecimal numberIn(final String text) {
        try {
            return DecimalText.parse(text.trim());
        } catch (final NumberFormatException notNumeric) {
            return null;
        }
    }

    /** A value as an exact number, or null for a value of no exact kind. */
    private static BigDecimal exactNumber(final Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof String) {
            return numberIn((String) value);
        }
        return null;
    }

    private String cast(final CastExpression cast, final boolean asFloat) {
        final String target = String.valueOf(cast.getTargetType()).trim().toUpperCase(Locale.ROOT);
        final Expression operand = cast.getExpression();
        if (isFloatName(target)) {
            // A FLOAT cast of a literal folds to a FLOAT literal.
            return operand instanceof LiteralExpression || operand instanceof UnaryOperationExpression
                ? print(operand, true) : null;
        }
        final String inner = print(operand, asFloat);
        if (inner == null) {
            return null;
        }
        if ("VARCHAR".equals(target) || "STRING".equals(target) || "TEXT".equals(target)) {
            return "CAST(" + inner + VARCHAR_CAST;
        }
        return "NUMBER".equals(target) ? "CAST(" + inner + " AS NUMBER(38,0))" : null;
    }

    private String call(final FunctionCallExpression call, final boolean asFloat) {
        final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = call.getArguments();
        if ("TO_VARCHAR".equals(name) && arguments.size() == 1) {
            final String inner = print(arguments.get(0), false);
            return inner == null ? null : "CAST(" + inner + VARCHAR_CAST;
        }
        if ("IFF".equals(name) && arguments.size() == 3 && arguments.get(0) instanceof LiteralExpression
                && ((LiteralExpression) arguments.get(0)).getValue() instanceof Boolean) {
            return print(isTrue(arguments.get(0)) ? arguments.get(1) : arguments.get(2), asFloat);
        }
        if ("NVL".equals(name) && arguments.size() == 2 && arguments.get(0) instanceof FunctionCallExpression
                && holdsUnfolded(arguments.get(0), true)) {
            // A call that cannot answer NULL folds NVL away.
            return print(arguments.get(0), asFloat);
        }
        final boolean floatParameters = FLOAT_FUNCTIONS.contains(name);
        final StringBuilder out = new StringBuilder(name).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            final Expression argument = arguments.get(i);
            String printed = print(argument, floatParameters);
            if (printed == null) {
                return null;
            }
            if ("CONCAT".equals(name) && !(argument instanceof LiteralExpression
                    && ((LiteralExpression) argument).getType() == LiteralType.STRING)) {
                // CONCAT reads a value that is not text through a cast to VARCHAR.
                printed = "CAST(" + printed + VARCHAR_CAST;
            }
            out.append(i > 0 ? ", " : "").append(printed);
        }
        return out.append(')').toString();
    }

    private String binary(final BinaryOperationExpression binary) {
        final String operator;
        switch (binary.getOperator()) {
            case ADD:
                operator = " + ";
                break;
            case SUBTRACT:
                operator = " - ";
                break;
            case MULTIPLY:
                operator = " * ";
                break;
            case DIVIDE:
                operator = " / ";
                break;
            default:
                return null;
        }
        final boolean asFloat = isFloat(binary);
        if (binary.getOperator() == BinaryOperator.DIVIDE && !asFloat && binary.getLeft() instanceof SubqueryExpression
                && isWholeWider(binary.getLeft(), WIDE_WHOLE_PRECISION) && isWholeWider(binary.getRight(), 0)) {
            // A whole number too wide to take the quotient's fraction is divided as the plan scales it: over an INT
            // column, (SELECT MIN(id) FROM t) / 2 is SCALED_ROUND_INT_DIVIDE(0, 2) (live-verified).
            final String left = print(binary.getLeft(), false);
            final String right = print(binary.getRight(), false);
            return left == null || right == null ? null : "SCALED_ROUND_INT_DIVIDE(" + left + ", " + right + ")";
        }
        final String divided = redividedQuotient(binary, asFloat);
        final String left = divided != null ? divided : operand(binary.getLeft(), asFloat);
        final String right = operand(binary.getRight(), asFloat);
        return left == null || right == null ? null : left + operator + right;
    }

    /**
     * The dividend of a division that is itself a quotient of a literal, cast to the scale the second division
     * takes: {@code 1 / (SELECT MIN(id) FROM t) / 2} is {@code (CAST(1 / 0 AS NUMBER(13,12))) / 2}, the literal's
     * whole digits and twelve of fraction (live-verified); null for any other operation.
     */
    private String redividedQuotient(final BinaryOperationExpression binary, final boolean asFloat) {
        if (binary.getOperator() != BinaryOperator.DIVIDE || asFloat
                || !(binary.getLeft() instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression inner = (BinaryOperationExpression) binary.getLeft();
        if (inner.getOperator() != BinaryOperator.DIVIDE || !(inner.getLeft() instanceof LiteralExpression)
                || foldedQuotient(inner) != null || isFloat(inner)) {
            return null;
        }
        final BigDecimal dividend = exactNumber(((LiteralExpression) inner.getLeft()).getValue());
        final String printed = dividend == null ? null : print(inner, false);
        if (printed == null) {
            return null;
        }
        final int whole = Math.max(1, dividend.abs().setScale(0, RoundingMode.DOWN).toPlainString().length());
        return "(CAST(" + printed + " AS NUMBER(" + (whole + DIVISION_SCALE) + "," + DIVISION_SCALE + ")))";
    }

    /**
     * An operator's operand: a call or another operation parenthesised, a folded division bare as its quotient, and a
     * text read as a number unless the operation is a FLOAT's.
     */
    private String operand(final Expression operand, final boolean asFloat) {
        if (operand instanceof SubqueryExpression) {
            return subquery((SubqueryExpression) operand, asFloat, true);
        }
        if (!asFloat && operand instanceof LiteralExpression
                && ((LiteralExpression) operand).getType() == LiteralType.STRING) {
            return textAsNumber(String.valueOf(((LiteralExpression) operand).getValue()));
        }
        final String printed = print(operand, asFloat);
        if (printed == null) {
            return null;
        }
        return foldedQuotient(operand) == null
            && (operand instanceof FunctionCallExpression || operand instanceof BinaryOperationExpression)
            ? "(" + printed + ")" : printed;
    }

    /** The quotient {@code node} folds to when it is a division that folds, or null. */
    private BigDecimal foldedQuotient(final Expression node) {
        return node instanceof BinaryOperationExpression
            && ((BinaryOperationExpression) node).getOperator() == BinaryOperator.DIVIDE
            ? quotient((BinaryOperationExpression) node) : null;
    }

    private DataType typeOf(final Expression expression) {
        try {
            return typer.inferStaticType(expression);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    private boolean isFloat(final Expression expression) {
        final DataType type = typeOf(expression);
        return type instanceof NumericType && NumericType.isApproximate(type);
    }

    /** Whether {@code expression} is an exact whole number whose type has more than {@code digits} digits. */
    private boolean isWholeWider(final Expression expression, final int digits) {
        final DataType type = typeOf(expression);
        return type instanceof NumericType && !NumericType.isApproximate(type)
            && ((NumericType) type).getScale() == 0 && ((NumericType) type).getPrecision() > digits;
    }

    private static boolean isFloatName(final String target) {
        return "FLOAT".equals(target) || "DOUBLE".equals(target) || "REAL".equals(target)
            || "FLOAT4".equals(target) || "FLOAT8".equals(target) || "DOUBLE PRECISION".equals(target);
    }

    private static boolean isTrue(final Expression condition) {
        return condition instanceof LiteralExpression && Boolean.TRUE.equals(((LiteralExpression) condition).getValue());
    }
}
