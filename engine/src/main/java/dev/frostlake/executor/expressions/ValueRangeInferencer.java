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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.ValueRange;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The interval an exact-numeric expression's values lie in, propagated the way the account's planner
 * propagates column statistics — the channel that decides the storage tag {@code SYSTEM$TYPEOF} prints.
 * Live-verified shape by shape:
 *
 * <ul>
 *   <li>a stored column is its statistics, the least and greatest value in the whole table; a literal is
 *       itself; a NULL is the empty interval;</li>
 *   <li>{@code +}, {@code -}, {@code *} and unary minus follow interval arithmetic; {@code ABS} folds
 *       about zero; {@code SIGN} and {@code MOD} keep their first argument's interval; a cast between
 *       NUMBERs keeps the interval and only the scale changes at tag time;</li>
 *   <li>a conditional (IFF, COALESCE, NVL, NVL2, NULLIF, ZEROIFNULL, CASE, DECODE) is the union of the
 *       value branches its statistics cannot rule out — a WHEN the intervals settle ends the walk, and so
 *       does a COALESCE argument they prove never NULL — and LEAST / GREATEST drop an argument the
 *       intervals prove never chosen;</li>
 *   <li>SUM and AVG multiply the interval by a trillion — the account's bound on the rows a sum may
 *       accumulate, so SUM over a NUMBER(38,0) holding 9,200,000 is SB8 and holding 9,300,000 is SB16 —
 *       MAX and MIN narrow it to one end, COUNT runs from zero to the table's row count, MEDIAN and the
 *       percentiles keep it, and a window AVG or a VARIANCE always accumulates sixteen bytes wide;</li>
 *   <li>a division is tagged by the wider of its dividend at the result scale plus the divisor's scale
 *       (the integer intermediate it is computed in) and the divisor itself, which is what makes
 *       RATIO_TO_REPORT as wide as the SUM it divides by.</li>
 * </ul>
 *
 * <p>SIGN keeps its argument's physical width, and HASH, RANDOM and the SEQ generators are as wide as
 * the integer they are computed in. Everything else — ROUND, CEIL, TRUNC, LENGTH, a cast from text or
 * from a FLOAT, ROW_NUMBER, a DISTINCT count, a scalar subquery — answers null, and the tag falls back
 * to the declared width — FLOOR too, unless its argument is a constant the planner folds.
 *
 * <p>An interval also says whether the expression may answer NULL (see {@link ValueRange#isNullable}).
 * A literal, a column holding no NULL and arithmetic over those never do; a CASE or DECODE falling
 * through to a missing ELSE, NULLIF, NULLIFZERO, LAG, LEAD and a scalar subquery may; and an aggregate
 * never does while the plan answers it from the whole table's statistics, but may once a grouping key or
 * a filter the statistics cannot prove takes the query past them.
 *
 * <p>A condition prunes a branch only where it holds the same way of every row: over operands that
 * cannot be NULL and did not come through a COALESCE the planner kept as a call (see
 * {@link ValueRange#isOpaqueToConditions}), combined through NOT, AND and OR as three-valued logic. A
 * DECODE search and a simple CASE value are matched rather than decided: no row matches where the
 * intervals are disjoint, whatever NULLs the subject holds, and every row only where neither side can be
 * NULL.
 */
final class ValueRangeInferencer {

    /** The account's bound on the rows a SUM may accumulate: ten to the twelfth, measured at the flip. */
    private static final BigDecimal ACCUMULATED_ROWS = BigDecimal.TEN.pow(12);

    /** The calls a constant passes through and stays one — see {@link #foldsAsConstant}. */
    private static final Set<String> CONSTANT_FOLDING_CALLS = new HashSet<>(Arrays.asList(
        "CONCAT", "UPPER", "TO_VARIANT"));

    /** The conversions into an exact number, whose interval {@link #convertedRange} reads. */
    private static final Set<String> NUMERIC_CONVERSIONS = new HashSet<>(Arrays.asList(
        "TO_NUMBER", "TO_NUMERIC", "TO_DECIMAL", "TRY_TO_NUMBER", "TRY_TO_NUMERIC", "TRY_TO_DECIMAL"));

    private final ExpressionEvaluatorVisitor visitor;
    /** How many aggregate calls the expression being read stands in. */
    private int aggregateDepth;

    ValueRangeInferencer(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    /**
     * The functions whose result is tagged as the empty interval when an argument is always NULL
     * (live-verified, each one): the date parts, MONTHS_BETWEEN, the character codes and the byte and bit lengths,
     * the two string distances, MOD, TRUNC and the bitwise functions. DATEDIFF, POSITION, CHARINDEX, REGEXP_COUNT,
     * REGEXP_INSTR, ARRAY_SIZE, ARRAY_POSITION, FACTORIAL and HASH keep their declared width there.
     */
    private static final Set<String> EMPTY_OVER_NULL = new HashSet<>(Arrays.asList(
        "YEAR", "QUARTER", "MONTH", "WEEK", "WEEKOFYEAR", "WEEKISO", "DAY", "DAYOFMONTH", "DAYOFWEEK", "DAYOFWEEKISO",
        "DAYOFYEAR", "HOUR", "MINUTE", "SECOND", "YEAROFWEEK", "YEAROFWEEKISO", "EXTRACT", "DATE_PART",
        "MONTHS_BETWEEN", "ASCII", "UNICODE", "OCTET_LENGTH", "BIT_LENGTH", "LENGTH", "LEN", "EDITDISTANCE",
        "JAROWINKLER_SIMILARITY", "MOD", "TRUNC", "TRUNCATE", "BITAND", "BITOR", "BITXOR", "BITNOT", "BITSHIFTLEFT",
        "BITSHIFTRIGHT"));

    /**
     * The calls that answer NULL over a NULL argument and are read through when a value is judged always NULL
     * (live-verified, each one). REPLACE, REGEXP_SUBSTR, REGEXP_REPLACE and DATE_FROM_PARTS are not read through:
     * a date part or a character code over one of them keeps its declared width.
     */
    private static final Set<String> PASSES_NULL_ON = new HashSet<>(Arrays.asList(
        "UPPER", "LOWER", "DATEADD", "TIMEADD", "TIMESTAMPADD", "LTRIM", "RTRIM", "TRIM", "LEFT", "RIGHT", "LPAD",
        "REVERSE", "INITCAP", "SUBSTR", "SUBSTRING", "CONCAT", "CONCAT_WS", "SPLIT_PART", "TRANSLATE", "REPEAT",
        "TO_CHAR", "TO_VARCHAR", "TO_DATE", "TRY_TO_DATE", "TO_TIMESTAMP", "TO_TIMESTAMP_NTZ", "ADD_MONTHS",
        "DATE_TRUNC", "TIME_SLICE", "NEXT_DAY", "PREVIOUS_DAY", "CONVERT_TIMEZONE", "LAST_DAY", "GREATEST"));

    /** The fewest values KURTOSIS answers a number over. */
    private static final long KURTOSIS_LEAST_ROWS = 4;

    /**
     * The bitwise function that is tagged by its value over literals: BITOR, which declares one digit more than
     * its widest argument — BITOR(100, 27) is NUMBER(4,0)[SB1]. The others keep their declared width there:
     * BITAND(123, 1) is NUMBER(3,0)[SB2], BITNOT(127) is NUMBER(3,0)[SB2] and BITSHIFTLEFT(1, 4) is [SB16]
     * (live-verified).
     */
    private static final Set<String> TAGGED_BY_VALUE = new HashSet<String>(Arrays.asList("BITOR", "BIT_OR"));

    /** A day whose year and whose day of the year no other date part answers: the fifth of March, 2023. */
    private static final LocalDate TELLING_DAY = LocalDate.of(2023, 3, 5);

    /** The conversions that keep the days of the date or the timestamp they are given. */
    private static final Set<String> KEEPS_DAYS = new HashSet<String>(Arrays.asList("TO_DATE", "DATE",
        "TO_TIMESTAMP", "TO_TIMESTAMP_NTZ"));

    /** The aggregates that answer NULL over nothing but NULLs, read through the same way (live-verified). */
    private static final Set<String> NULL_OVER_NULLS = new HashSet<>(Arrays.asList("MIN", "MAX", "ANY_VALUE"));

    /**
     * Whether an aggregate's one argument is a NULL of a text type written out, {@code MAX(NULL::VARCHAR(10))}:
     * the aggregate over it is tagged by its type alone, where one over a column of NULLs is the empty interval
     * (live-verified).
     */
    private boolean writtenNullText(final List<Expression> args) {
        return args.size() == 1 && visitor.inferStaticType(args.get(0)) instanceof StringType
            && ConstantRootFold.isOverLiterals(args.get(0), visitor.getFunctionRegistry());
    }

    /**
     * The interval a projected item hands on to the relation it is a column of: {@link #infer}'s, or the empty
     * one for an item of any type — a date, a text — that is NULL on every row, which a derived table, a common
     * table expression, a view and a set operation carry as they carry a number's (live-verified).
     */
    ValueRange inferProjected(final Expression expr) {
        final ValueRange range = infer(expr);
        if (range != null) {
            return range;
        }
        try {
            // A date column hands on the days its table holds, for a part read from it further out.
            return alwaysNull(expr) ? ValueRange.EMPTY : dayRange(expr);
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /** The interval of {@code expr}, or null when none can be known. Never throws. */
    ValueRange infer(final Expression expr) {
        try {
            return inferUnchecked(expr);
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /**
     * The interval of an operand something is COMPUTED from — arithmetic, a cast, a numeric function —
     * or null where it has none or none may be computed from it (see {@link ValueRange#isOpaqueToComputation}).
     */
    private ValueRange computedFrom(final Expression operand) {
        final ValueRange range = infer(operand);
        return range != null && range.isOpaqueToComputation() ? null : range;
    }

    private ValueRange inferUnchecked(final Expression expr) {
        if (expr == null) {
            return null;
        }
        final BigDecimal folded = foldedTextNumber(expr);
        if (folded != null) {
            return ValueRange.of(folded);
        }
        if (expr instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expr;
            switch (literal.getType()) {
                case INTEGER:
                case DECIMAL:
                    final BigDecimal exact = ValueRange.exactOf(literal.getValue());
                    return exact != null ? ValueRange.of(exact) : null;
                case STRING:
                    // A text literal in arithmetic folds to the number it spells, and the tag follows
                    // that value: '5' + 1 is NUMBER(19,0)[SB1] and '5' / 2 NUMBER(24,6)[SB4] on the
                    // account, where a text COLUMN read as a number carries no interval at all.
                    final BigDecimal read = PlanTextNumber.value(String.valueOf(literal.getValue()));
                    return read == null ? null : ValueRange.of(read);
                case NULL:
                    return ValueRange.EMPTY;
                default:
                    return null;
            }
        }
        if (expr instanceof SubqueryExpression) {
            // The item's own interval, as the subquery's planned shape reported it (a column's table
            // statistics, a literal's value): live tags (SELECT n FROM t) + 1 over a 1.5 as [SB2]. A
            // subquery that finds no row answers NULL, so the interval may.
            return mayBeNull(visitor.subqueryValueRange((SubqueryExpression) expr));
        }
        if (expr instanceof BindVariableExpression) {
            // A scripting variable bound into the statement is ONE value, and the tag follows it
            // (live: a NUMBER(10,4) holding 1.7777 reads [SB2], holding 12345.6789 [SB4]). Read as a
            // parameter, in a block's own expression, it is no value the plan can bound.
            if (visitor.bindsAsParameters()) {
                return null;
            }
            final BindVariableExpression bind = (BindVariableExpression) expr;
            if (visitor.boundVariableHoldsNull(bind)) {
                // A NULL is no value at all, the empty interval: a NUMBER(10,2) holding NULL reads
                // NUMBER(10,2)[SB1], :n + 1 NUMBER(11,2)[SB1] and LENGTH(:s) over a NULL text [SB1]
                // (live-verified).
                return ValueRange.EMPTY;
            }
            final BigDecimal bound = ValueRange.exactOf(visitor.boundVariableValue(bind));
            return bound != null ? ValueRange.of(bound) : null;
        }
        if (expr instanceof SessionVarExpression) {
            // A session variable holds ONE value, and the tag follows it as a literal's does: set to 12345 it
            // reads NUMBER(5,0)[SB2] and set to 127 NUMBER(3,0)[SB1] (live-verified).
            final BigDecimal held = ValueRange.exactOf(
                visitor.sessionVariableValue(((SessionVarExpression) expr).getVarName()));
            return held != null ? ValueRange.of(held) : null;
        }
        if (expr instanceof ColumnReferenceExpression) {
            if (visitor.readsUntypedNull((ColumnReferenceExpression) expr)) {
                // Nothing but an untyped NULL: the empty interval, as the bare word is.
                return ValueRange.EMPTY;
            }
            final ValueRange read = visitor.declaredColumnRange((ColumnReferenceExpression) expr);
            // A view's column is told at its declared width, except to an aggregate over it.
            final ValueRange declared = aggregateDepth > 0 ? read
                : visitor.heldToDeclaredWidth((ColumnReferenceExpression) expr, read);
            if (declared == null && visitor.readsOnlyNullTexts((ColumnReferenceExpression) expr)) {
                // A text column that holds nothing but NULLs is no value to the number read from it: the empty
                // interval, s + 1 is NUMBER(19,5)[SB1] (live-verified).
                return ValueRange.EMPTY;
            }
            return declared == null && visitor.readsSequence((ColumnReferenceExpression) expr)
                ? SequenceRead.range() : declared;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            if (unary.getOperator() == UnaryOperator.PLUS) {
                return heldAsValue(unary.getOperand()) ? computedFrom(unary.getOperand()) : null;
            }
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                return null;
            }
            final ValueRange operand = infer(unary.getOperand());
            return operand != null ? operand.negate() : null;
        }
        if (expr instanceof BinaryOperationExpression) {
            return binaryRange((BinaryOperationExpression) expr);
        }
        if (expr instanceof CastExpression) {
            final Expression converted = ((CastExpression) expr).getExpression();
            final ValueRange exact = exactOperandRange(converted);
            final ValueRange kept = exact != null ? exact
                : foldedConversion(expr, Collections.singletonList(converted));
            // A conversion of what is always NULL is always NULL: a NULL cast to a text and on to another.
            return kept == null && alwaysNull(converted) ? ValueRange.EMPTY : kept;
        }
        if (expr instanceof CaseExpression) {
            // The planner prunes a branch its statistics decide: over a table whose column holds only
            // 1.5, WHEN c > 1 is always taken and the ELSE never contributes its width.
            final CaseExpression conditional = (CaseExpression) expr;
            final List<Expression> branches = new ArrayList<>();
            boolean settled = false;
            for (final WhenClause when : conditional.getWhenClauses()) {
                final Boolean decided = decideWhen(when);
                if (decided != null && !decided.booleanValue()) {
                    continue;
                }
                branches.add(when.getResult());
                if (decided != null) {
                    settled = true;
                    break;
                }
            }
            if (!settled && conditional.getElseExpression() != null) {
                branches.add(conditional.getElseExpression());
            }
            final ValueRange chosen = unionOf(branches);
            // A walk no WHEN settles falls through to the ELSE — to a NULL where none is written.
            return !settled && conditional.getElseExpression() == null ? mayBeNull(chosen) : chosen;
        }
        if (expr instanceof WindowFunctionExpression) {
            return windowRange((WindowFunctionExpression) expr);
        }
        if (expr instanceof FunctionCallExpression) {
            return functionRange((FunctionCallExpression) expr);
        }
        return null;
    }

    /** Whether an operand is the bare word NULL, or a column that holds nothing but one. */
    private boolean isBareNull(final Expression operand) {
        return operand instanceof LiteralExpression
            && ((LiteralExpression) operand).getType() == LiteralType.NULL
            || operand instanceof ColumnReferenceExpression
                && visitor.readsUntypedNull((ColumnReferenceExpression) operand);
    }

    /** The interval as one that may also answer NULL, or null when unknown. */
    private static ValueRange mayBeNull(final ValueRange range) {
        return range == null ? null : range.withNullable(true);
    }

    private ValueRange binaryRange(final BinaryOperationExpression binary) {
        // A bare NULL operand makes every row NULL whatever the operator, and the account tags that
        // as an empty interval: n10_2 % NULL and NULL / n10_2 are [SB1], not the dividend's width.
        if (isBareNull(binary.getLeft()) || isBareNull(binary.getRight())) {
            return ValueRange.EMPTY;
        }
        final ValueRange left = infer(binary.getLeft());
        final ValueRange right = infer(binary.getRight());
        if (left != null && left.isEmpty() || right != null && right.isEmpty()
                || left == null && alwaysNull(binary.getLeft(), left)
                || right == null && alwaysNull(binary.getRight(), right)) {
            // A NULL-valued operand (a cast NULL, TO_VARCHAR(NULL)) empties the result whatever the other side
            // holds.
            return ValueRange.EMPTY;
        }
        if (left != null && left.isOpaqueToComputation() || right != null && right.isOpaqueToComputation()) {
            return null;
        }
        if (readsWithoutInterval(binary.getLeft()) || readsWithoutInterval(binary.getRight())) {
            // A text column or a VARIANT read as a number carries no interval: the account tags t + 0
            // by its declared NUMBER(19,5) alone ([SB16]) whatever the column holds, where a text
            // LITERAL folds to its value and tags as that value ('5' + 1 is [SB1]).
            return null;
        }
        switch (binary.getOperator()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
                if (left == null || right == null) {
                    return null;
                }
                if (binary.getOperator() == BinaryOperator.ADD) {
                    return left.add(right);
                }
                return binary.getOperator() == BinaryOperator.SUBTRACT
                    ? left.subtract(right) : left.multiply(right);
            case DIVIDE:
                return quotientRange(binary.getLeft(), binary.getRight(), visitor.inferStaticType(binary));
            case MODULO:
                return remainderRange(binary.getLeft(), binary.getRight());
            default:
                return null;
        }
    }

    /**
     * The interval a remainder is tagged by: the wider of the dividend's and the divisor's. Over a
     * NUMBER(10,2) holding 1.50 and 2.50, {@code MOD(n, 1000)} is [SB4], the divisor's 1000.00, and
     * {@code MOD(1000, n)} is [SB4] as well; {@code n % 3} over 1.5 to 99999999.99 is [SB8], the dividend's.
     * Two single values fold to the remainder itself. That covers two constants ({@code MOD(12345, 2)} is
     * NUMBER(5,0)[SB1], {@code '5' % 12345} [SB1]), and in the same way a column holding one value, which
     * the account's statistics serve as a constant: {@code MOD(b, n)} over one row of 5 and 2.50 is [SB1],
     * the folded 0 (all live-verified).
     */
    private ValueRange remainderRange(final Expression dividend, final Expression divisor) {
        if (readsWithoutInterval(dividend) || readsWithoutInterval(divisor)) {
            // A text or VARIANT on either side leaves the remainder interval-less: MOD(n, t) over a
            // NUMBER(10,2) and a VARCHAR is NUMBER(18,5)[SB8] on the account, the declared width's.
            return null;
        }
        final ValueRange over = computedFrom(dividend);
        final ValueRange under = computedFrom(divisor);
        if (over == null || under == null) {
            return over;
        }
        if (!over.isEmpty() && !under.isEmpty()
                && over.getMin().compareTo(over.getMax()) == 0 && under.getMin().compareTo(under.getMax()) == 0
                && under.getMin().signum() != 0) {
            return ValueRange.of(over.getMin().remainder(under.getMin()))
                .withNullable(over.isNullable() || under.isNullable())
                .withConditionOpacityOf(over).withConditionOpacityOf(under);
        }
        return over.union(under);
    }

    /**
     * The number a text the plan folds while compiling reads as — a join or a case mapping of text literals,
     * {@code ('1.' || '50')}, {@code UPPER('1.50')} — which tags what is computed from it as the literal's does:
     * {@code ('1.' || '50') * 1} is NUMBER(19,5)[SB4]. Null for any other operand and for a text with no number.
     */
    private BigDecimal foldedTextNumber(final Expression operand) {
        if (operand instanceof LiteralExpression || operand instanceof SessionVarExpression
                || !ConstantTextArgument.isConstant(operand, visitor)) {
            return null;
        }
        try {
            final Object folded = operand.accept(visitor);
            return folded instanceof CharSequence ? ImpliedTextNumber.read((CharSequence) folded) : null;
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** Whether an operand is a text (not a literal) or a VARIANT, whose numeric reading has no interval. */
    private boolean readsWithoutInterval(final Expression operand) {
        if (ImpliedTextNumber.isTextLiteral(operand) || foldedTextNumber(operand) != null) {
            return false;
        }
        final DataType type = visitor.inferStaticType(operand);
        return type instanceof StringType || type instanceof VariantType;
    }

    /**
     * The interval a quotient is tagged by. The account divides in an integer intermediate — the dividend
     * shifted by the result's scale AND the divisor's — and tags the wider of that intermediate and the
     * divisor: 10 / c over a NUMBER(38,12) is SB16 whatever c holds, because ten shifted eighteen places
     * is. Expressed here as the union of both, each shifted so that the result's own scale, applied at
     * tag time, lands on the intermediate's width.
     */
    private ValueRange quotientRange(final Expression dividend, final Expression divisor,
                                     final DataType resultType) {
        final ValueRange over = computedFrom(dividend);
        final ValueRange under = computedFrom(divisor);
        final Integer divisorScale = exactScale(visitor.inferStaticType(divisor));
        final Integer resultScale = exactScale(resultType);
        if (over == null || under == null || divisorScale == null || resultScale == null) {
            return null;
        }
        return intermediateOf(over, under, divisorScale, resultScale);
    }

    private static ValueRange intermediateOf(final ValueRange over, final ValueRange under,
                                             final int divisorScale, final int resultScale) {
        final ValueRange shiftedDividend = over.symmetric().scaledBy(BigDecimal.ONE.scaleByPowerOfTen(divisorScale));
        final ValueRange divisorAtResultScale = under.symmetric()
            .scaledBy(BigDecimal.ONE.scaleByPowerOfTen(divisorScale - resultScale));
        return shiftedDividend.union(divisorAtResultScale);
    }

    private static Integer exactScale(final DataType type) {
        if (!(type instanceof NumericType) || NumericType.isApproximate(type)) {
            return null;
        }
        return ((NumericType) type).getScale();
    }

    /** The operand's interval when it is an exact number; a text, a FLOAT or a VARIANT has none. */
    private ValueRange exactOperandRange(final Expression operand) {
        if (UntypedNullFold.isUntypedNull(operand)) {
            return ValueRange.EMPTY;
        }
        final DataType type = visitor.inferStaticType(operand);
        if (!(type instanceof NumericType) || NumericType.isApproximate(type)) {
            return null;
        }
        return computedFrom(operand);
    }

    /** The union of the branches' intervals, null when any is unknown; no branch at all is a NULL. */
    private ValueRange unionOf(final List<Expression> branches) {
        ValueRange union = null;
        for (final Expression branch : branches) {
            final ValueRange one = infer(branch);
            if (one == null) {
                return null;
            }
            union = union == null ? one : union.union(one);
        }
        return union == null ? ValueRange.EMPTY : union;
    }

    /**
     * COALESCE, NVL and IFNULL as the planner prunes them: an argument the statistics prove never NULL
     * always answers, so the ones after it never contribute. Over a NUMBER(10,2) holding 0.50 and 1.00
     * and no NULL, live tags {@code COALESCE(n, 3000.5)} [SB1], n's width alone, and {@code
     * COALESCE(1.5, n)} [SB2], the literal's at the call's scale; once a NULL row lets the 3000.5 through
     * the tag is the union's [SB4]. A bare NULL argument contributes nothing.
     *
     * <p>Where its first argument cannot be NULL the call IS that argument. Any other call the planner
     * keeps as a call, which may answer NULL unless its first or its LAST argument cannot — live reads
     * {@code COALESCE(n, 0)} as never NULL but {@code COALESCE(n, 0.5, z)} as possibly NULL over columns
     * holding one, though the 0.5 is reached first — and over which no condition is settled (see
     * {@link ValueRange#isOpaqueToConditions}). An aggregate other than COUNT never reduces the call,
     * whatever its statistics prove: live leaves {@code COALESCE(MAX(n), 0) > 5} open where it settles
     * {@code COALESCE(COUNT(n), 0) > 5}.
     */
    private ValueRange firstNeverNull(final List<Expression> args) {
        ValueRange union = null;
        Expression first = null;
        ValueRange firstRange = null;
        for (final Expression arg : args) {
            if (isBareNull(arg)) {
                continue;
            }
            final ValueRange one = infer(arg);
            if (one == null) {
                return null;
            }
            if (first == null) {
                first = arg;
                firstRange = one;
            }
            union = union == null ? one : union.union(one);
            if (!one.isNullable()) {
                break;
            }
        }
        if (union == null) {
            return ValueRange.EMPTY;
        }
        if (!firstRange.isNullable() && !visitor.aggregatesBeyondCount(first)) {
            return firstRange;
        }
        final boolean neverNull = !firstRange.isNullable() || provedNeverNull(args.get(args.size() - 1));
        return union.withNullable(!neverNull).opaqueToConditions();
    }

    /** Whether the statistics prove an argument never NULL: a known interval holding a value and no NULL. */
    private boolean provedNeverNull(final Expression arg) {
        if (isBareNull(arg)) {
            return false;
        }
        final ValueRange range = infer(arg);
        return range != null && !range.isEmpty() && !range.isNullable();
    }

    /**
     * NVL2(subject, a, b): {@code a} alone where the statistics settle the subject as never NULL,
     * {@code b} alone where it is the bare NULL, and both otherwise — live tags {@code NVL2(n, n, 3000.5)}
     * over a column holding no NULL as n's width and {@code NVL2(n, 3000.5, n)} as the literal's, and
     * leaves {@code NVL2(COALESCE(n, 0), 1.5, 3000.5)} open over a column holding one.
     */
    private ValueRange nvl2Range(final List<Expression> args) {
        if (args.size() != 3) {
            return null;
        }
        final ValueRange tested = infer(args.get(0));
        if (settles(tested)) {
            return infer(args.get(1));
        }
        if (isBareNull(args.get(0)) || tested != null && tested.isEmpty()) {
            // What is always NULL — the bare word, a column of NULLs — leaves the third argument alone.
            return infer(args.get(2));
        }
        return unionOf(args.subList(1, 3));
    }

    /**
     * Whether an interval settles a condition over it: it is known and holds a value but no NULL — a
     * NULL operand makes a comparison NULL rather than FALSE, so the condition does not hold the same
     * way of every row — and it did not come through a COALESCE the planner kept as a call (see
     * {@link ValueRange#isOpaqueToConditions}).
     */
    private static boolean settles(final ValueRange range) {
        return range != null && !range.isEmpty() && !range.isNullable() && !range.isOpaqueToConditions();
    }

    /**
     * A bitwise function is tagged by its declared width, with two exceptions (live-verified): a bare NULL
     * argument empties the interval — BITAND(n, NULL) is [SB1] — and a FLOAT or text argument, converted
     * through a 64-bit integer, widens it to at least eight bytes — BITAND(f, 1) is NUMBER(2,0)[SB8] — while
     * a wider width keeps its own tag (BITOR(f, 1) is NUMBER(19,0)[SB16]).
     */
    private ValueRange bitwiseRange(final FunctionCallExpression call, final List<Expression> args) {
        for (final Expression arg : args) {
            if (isBareNull(arg)) {
                return ValueRange.EMPTY;
            }
        }
        final ValueRange folded = TAGGED_BY_VALUE.contains(call.getFunctionName().toUpperCase(Locale.ROOT))
            ? foldedOverLiterals(call, args) : null;
        if (folded != null) {
            return folded;
        }
        boolean throughSixtyFourBits = false;
        for (final Expression arg : args) {
            final DataType type = visitor.inferStaticType(arg);
            throughSixtyFourBits = throughSixtyFourBits || type instanceof StringType || NumericType.isApproximate(type);
        }
        final DataType declared = visitor.inferStaticType(call);
        if (!throughSixtyFourBits || !(declared instanceof NumericType)) {
            return null;
        }
        final BigDecimal bound = BigDecimal.TEN.pow(((NumericType) declared).getPrecision()).subtract(BigDecimal.ONE);
        return ValueRange.between(bound.negate(), bound)
            .union(ValueRange.between(BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE)))
            .withNullable(true);
    }

    /**
     * The value of a call whose arguments are all whole numbers or texts written out, which the plan holds before
     * a row is read: {@code BITOR(12, 10)} declares NUMBER(3,0) and is tagged by its 14, [SB1], and
     * {@code BITOR('12', 10)} is too, where {@code BITOR(1.5, 2)} keeps its declared width (live-verified). Null
     * when an argument is no such literal or the call does not answer a number.
     */
    private ValueRange foldedOverLiterals(final FunctionCallExpression call, final List<Expression> args) {
        if (args.isEmpty()) {
            return null;
        }
        for (final Expression arg : args) {
            final Expression written = arg instanceof UnaryOperationExpression
                ? ((UnaryOperationExpression) arg).getOperand() : arg;
            if (!(written instanceof LiteralExpression)
                    || ((LiteralExpression) written).getType() == LiteralType.NULL) {
                return null;
            }
            final DataType type = visitor.inferStaticType(arg);
            if (!(type instanceof StringType) && !Integer.valueOf(0).equals(exactScale(type))) {
                return null;
            }
        }
        final Object value;
        try {
            value = call.accept(visitor);
        } catch (final RuntimeException refused) {
            return null;
        }
        final BigDecimal exact = ValueRange.exactOf(value);
        return exact == null ? null : ValueRange.of(exact);
    }

    /**
     * A bitwise aggregate's interval, the bits its argument's values may set: over whole numbers from 0 to 15 it
     * is at most 15, [SB1] whatever width it declares, and over an argument that is always NULL it is the empty
     * interval. A FLOAT is read through a 64-bit integer, [SB8], and an argument that holds a negative number
     * keeps the declared width (all live-verified).
     */
    private ValueRange bitAggregateRange(final List<Expression> args) {
        if (args.size() != 1) {
            return null;
        }
        if (NumericType.isApproximate(visitor.inferStaticType(args.get(0)))) {
            return ValueRange.between(BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE))
                .withNullable(true);
        }
        final ValueRange read = infer(args.get(0));
        if (read == null) {
            return null;
        }
        if (read.isEmpty()) {
            return ValueRange.EMPTY;
        }
        if (read.getMin() == null || read.getMax() == null || read.getMin().signum() < 0) {
            return null;
        }
        final BigInteger high = read.getMax().setScale(0, RoundingMode.HALF_UP).toBigInteger();
        final BigInteger top = BigInteger.ONE.shiftLeft(high.bitLength()).subtract(BigInteger.ONE);
        return ValueRange.between(BigDecimal.ZERO, new BigDecimal(top)).withNullable(true);
    }

    private ValueRange functionRange(final FunctionCallExpression call) {
        if (call.getNameExpression() != null) {
            return null;
        }
        final boolean aggregate = visitor.getFunctionRegistry() != null
            && visitor.getFunctionRegistry().hasAggregateFunction(call.getFunctionName().toUpperCase(Locale.ROOT));
        if (!aggregate) {
            return rangeOfCall(call);
        }
        aggregateDepth++;
        try {
            return rangeOfCall(call);
        } finally {
            aggregateDepth--;
        }
    }

    private ValueRange rangeOfCall(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if ((name.equals("MOD") || name.equals("DIV0") || name.equals("DIV0NULL"))
                && !args.isEmpty() && (isBareNull(args.get(0)) || (args.size() > 1 && isBareNull(args.get(1))))) {
            // The function forms of the operators tag a bare NULL argument as the operators do — an
            // empty interval: MOD(n10_2, NULL) is [SB1] on the account.
            return ValueRange.EMPTY;
        }
        if (EMPTY_OVER_NULL.contains(name) && anyAlwaysNull(args)) {
            // A value read from an argument that is always NULL is always NULL, the empty interval: YEAR(NULL::DATE)
            // is NUMBER(4,0)[SB1] where YEAR over a date is [SB2] — see EMPTY_OVER_NULL.
            return ValueRange.EMPTY;
        }
        final ValueRange foldedPart = foldedDatePart(call, name, args);
        if (foldedPart != null) {
            return foldedPart;
        }
        final ValueRange columnPart = columnDatePart(call, name, args);
        if (columnPart != null) {
            return columnPart;
        }
        final Expression componentRead = IntervalFunctions.componentSource(name, args);
        if (componentRead != null && IntervalCasts.isIntervalType(visitor.inferStaticType(componentRead))) {
            // A component of an interval that is always NULL is NULL, the empty interval: EXTRACT(HOUR FROM
            // NULL::INTERVAL DAY TO SECOND) is NUMBER(9,0)[SB1], where one read from a constant or a column keeps
            // the declared width, [SB4] (live-verified).
            final ValueRange read = infer(componentRead);
            return read != null && read.isEmpty() ? ValueRange.EMPTY : null;
        }
        switch (name) {
            case "IFF":
                if (args.size() != 3) {
                    return null;
                }
                final Boolean chosen = decide(args.get(0));
                if (chosen != null) {
                    return infer(args.get(chosen.booleanValue() ? 1 : 2));
                }
                return unionOf(args.subList(1, 3));
            case "DECODE":
                return decodeRange(args);
            case "LEAST":
            case "GREATEST":
                return extremeRange(args, name.equals("LEAST"));
            case "COALESCE":
            case "NVL":
            case "IFNULL":
                return firstNeverNull(args);
            case "NVL2":
                return nvl2Range(args);
            case "NULLIF":
                // Its first argument, or NULL where that equals the second.
                return mayBeNull(unionOf(TypeInferencer.conditionalBranches(name, args)));
            case "GREATEST_IGNORE_NULLS":
            case "LEAST_IGNORE_NULLS":
                return unionOf(TypeInferencer.conditionalBranches(name, args));
            case "CEIL":
            case "ROUND":
                // Over a NULL they are tagged as the NULL is, [SB1] (live-verified for CEIL(NULL),
                // ROUND(NULL, 2) and CEIL(NULL::INT)); any other argument is left to the declared width.
                final ValueRange rounded = args.isEmpty() ? null : computedFrom(args.get(0));
                return rounded != null && rounded.isEmpty() ? rounded : null;
            case "FLOOR":
                // Folded for a constant — a column whose statistics hold one value — and otherwise
                // left to the declared width: FLOOR over a NUMBER(10,2) holding 1.5 and 99999999.99 is
                // SB8, its NUMBER(11,0)'s own, though the floors themselves would fit four bytes. Over a
                // NULL it is tagged as the NULL is, [SB1].
                final ValueRange floored = args.isEmpty() ? null : computedFrom(args.get(0));
                if (floored != null && floored.isEmpty()) {
                    return floored;
                }
                if (floored == null || floored.getMin().compareTo(floored.getMax()) != 0) {
                    return null;
                }
                return ValueRange.of(floored.getMin().setScale(0, RoundingMode.FLOOR))
                    .withNullable(floored.isNullable()).withConditionOpacityOf(floored);
            case "LENGTH":
            case "LEN":
                // The length of an argument that is always NULL is NULL, the empty interval:
                // LENGTH(NULL::VARCHAR(10)) is NUMBER(18,0)[SB1] (live-verified).
                final ValueRange measured = firstArgument(args);
                return measured != null && measured.isEmpty() ? ValueRange.EMPTY : null;
            case "ZEROIFNULL":
                final ValueRange zeroed = args.isEmpty() ? null : computedFrom(args.get(0));
                return zeroed != null ? zeroed.union(ValueRange.of(BigDecimal.ZERO)).withNullable(false) : null;
            case "ABS":
                final ValueRange signed = args.isEmpty() ? null : computedFrom(args.get(0));
                return signed != null ? signed.abs() : null;
            case "SIGN":
                // The argument's own physical width passes through untouched — SIGN over a
                // NUMBER(10,2) holding 99999999.99 is SB8 though it declares NUMBER(2,0) — so the
                // interval is the argument's UNSCALED one, which the scale-0 result reads as is.
                final ValueRange signedOf = args.isEmpty() ? null : computedFrom(args.get(0));
                final Integer signedScale = args.isEmpty() ? null
                    : exactScale(visitor.inferStaticType(args.get(0)));
                return signedOf == null || signedScale == null ? null
                    : signedOf.scaledBy(BigDecimal.ONE.scaleByPowerOfTen(signedScale));
            case "MOD":
                return args.size() == 2 ? remainderRange(args.get(0), args.get(1)) : null;
            case "NULLIFZERO":
                return mayBeNull(firstArgument(args));
            case "ANY_VALUE":
                return writtenNullText(args) ? null : aggregated(firstArgument(args), true);
            case "DIV0":
            case "DIV0NULL":
                return args.size() == 2
                    ? quotientRange(args.get(0), args.get(1), visitor.inferStaticType(call)) : null;
            case "TO_NUMBER":
            case "TO_NUMERIC":
            case "TO_DECIMAL":
                return args.isEmpty() ? null : convertedRange(call, args);
            case "TRY_TO_NUMBER":
            case "TRY_TO_NUMERIC":
            case "TRY_TO_DECIMAL":
                return args.isEmpty() ? null : mayBeNull(convertedRange(call, args));
            case "SUM":
                final SumShift shift = visitor.rewrittenSumShift(call);
                if (shift != null) {
                    return rewrittenSumRange(call, shift);
                }
                // A SUM over a value nothing may be computed from keeps its interval as it is: live tags
                // SUM over a derived REDUCE column by the accumulator's width, and AVG by its declared one.
                final ValueRange summed = firstArgument(args);
                return aggregated(summed != null && summed.isOpaqueToComputation() ? summed : accumulated(summed),
                    false);
            case "AVG":
                return aggregated(accumulated(args.isEmpty() ? null : computedFrom(args.get(0))), false);
            case "MIN":
                // One end of the argument's interval is the answer only where the statistics give it: under a
                // grouping key or a filter each MIN may be any value the interval holds.
                final ValueRange least = writtenNullText(args) ? null : firstArgument(args);
                return least == null ? null : least.isEmpty() ? least
                    : aggregated(visitor.readsWithinStatistics()
                        ? ValueRange.of(least.getMin()).withConditionOpacityOf(least) : least, false);
            case "MAX":
                final ValueRange greatest = writtenNullText(args) ? null : firstArgument(args);
                return greatest == null ? null : greatest.isEmpty() ? greatest
                    : aggregated(visitor.readsWithinStatistics()
                        ? ValueRange.of(greatest.getMax()).withConditionOpacityOf(greatest) : greatest, false);
            case "COUNT":
                // Bounded by the row count only where the statistics answer it — over a star, a constant or
                // a stored column: COUNT(c + 1) is tagged by its declared width on the account.
                if (call.isDistinct() || !call.isStar()
                        && (args.size() != 1 || !visitor.countsStoredColumn(args.get(0)))) {
                    return null;
                }
                final Long rows = visitor.countableRows();
                return rows == null ? null : ValueRange.between(BigDecimal.ZERO, BigDecimal.valueOf(rows));
            case "MEDIAN":
            case "PERCENTILE_CONT":
            case "PERCENTILE_DISC":
                return aggregated(percentileRange(call.getWithinGroupOrdered() != null
                    ? call.getWithinGroupOrdered() : args.isEmpty() ? null : args.get(0)), false);
            case "VARIANCE":
            case "VAR_POP":
            case "VAR_SAMP":
            case "VARIANCE_POP":
            case "VARIANCE_SAMP":
                return ValueRange.WIDEST;
            case "BITAND":
            case "BITOR":
            case "BITXOR":
            case "BITNOT":
            case "BIT_AND":
            case "BIT_OR":
            case "BIT_XOR":
            case "BIT_NOT":
            case "BITSHIFTLEFT":
            case "BITSHIFTRIGHT":
                return bitwiseRange(call, args);
            case "FACTORIAL":
                // Over a whole number written out the plan holds the value: FACTORIAL(5) is [SB1], where
                // FACTORIAL(2.5) keeps its declared width (live-verified).
                return args.size() == 1 && Integer.valueOf(0).equals(exactScale(visitor.inferStaticType(args.get(0))))
                    ? foldedOverLiterals(call, args) : null;
            case "BITAND_AGG":
            case "BITOR_AGG":
            case "BITXOR_AGG":
            case "BITANDAGG":
            case "BITORAGG":
            case "BITXORAGG":
            case "BIT_AND_AGG":
            case "BIT_OR_AGG":
            case "BIT_XOR_AGG":
            case "BIT_ANDAGG":
            case "BIT_ORAGG":
            case "BIT_XORAGG":
                return bitAggregateRange(args);
            case "REGR_COUNT":
                // Nothing is counted over an argument that is always NULL: [SB1] over an empty table.
                return anyAlwaysNull(args) ? ValueRange.of(BigDecimal.ZERO) : null;
            case "KURTOSIS":
                // NULL over fewer than four values, which a table of fewer rows cannot hold.
                final Long held = visitor.countableRows();
                return anyAlwaysNull(args) || held != null && held < KURTOSIS_LEAST_ROWS ? ValueRange.EMPTY : null;
            case "HASH":
            case "SEQ8":
            case "RANDOM":
                // A signed 64-bit integer, whatever the declared digits say: RANDOM() declares
                // NUMBER(19,0), whose nineteen digits would need sixteen bytes, and live tags it [SB8].
                return ValueRange.between(BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE));
            case "SEQ4":
                return ValueRange.between(BigDecimal.valueOf(Integer.MIN_VALUE), BigDecimal.valueOf(Integer.MAX_VALUE));
            case "SEQ2":
                return ValueRange.between(BigDecimal.valueOf(Short.MIN_VALUE), BigDecimal.valueOf(Short.MAX_VALUE));
            case "SEQ1":
                return ValueRange.between(BigDecimal.valueOf(Byte.MIN_VALUE), BigDecimal.valueOf(Byte.MAX_VALUE));
            case "REDUCE":
                return ReduceAccumulatorRange.of(this, call);
            default:
                // A SQL function's body is read as if the call were inlined — see udfCallRange.
                return visitor.getFunctionRegistry().getFunction(name) == null ? visitor.udfCallRange(call) : null;
        }
    }

    /** Whether an argument of a call is always NULL ({@link #alwaysNull}). */
    private boolean anyAlwaysNull(final List<Expression> args) {
        for (final Expression arg : args) {
            if (alwaysNull(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a value is NULL on every row the statement can read: the bare word, a cast of one, a variable
     * holding NULL, a column whose statistics hold nothing but NULLs — a text or a date column as much as a number
     * — a call that passes its NULL argument on (see {@link #PASSES_NULL_ON}), and a COALESCE of nothing else.
     */
    private boolean alwaysNull(final Expression value) {
        final Boolean byShape = alwaysNullByShape(value);
        if (byShape != null) {
            return byShape;
        }
        final ValueRange range = infer(value);
        return range != null && range.isEmpty();
    }

    /**
     * {@link #alwaysNull} of a value whose interval is already in hand, which stands in for inferring it again:
     * each operand of a chain of ANDs, ORs or comparisons would otherwise be inferred once more for every
     * operator above it, and the chain would take time exponential in its length.
     */
    private boolean alwaysNull(final Expression value, final ValueRange inferred) {
        final Boolean byShape = alwaysNullByShape(value);
        if (byShape != null) {
            return byShape;
        }
        return inferred != null && inferred.isEmpty();
    }

    /** Whether a value's shape alone settles {@link #alwaysNull}: TRUE or FALSE, or null when only its interval can. */
    private Boolean alwaysNullByShape(final Expression value) {
        if (value instanceof CastExpression) {
            return alwaysNull(((CastExpression) value).getExpression());
        }
        if (value instanceof ColumnReferenceExpression) {
            final ValueRange read = infer(value);
            return read != null ? read.isEmpty() : visitor.carriesOnlyNulls((ColumnReferenceExpression) value)
                || visitor.readsOnlyNulls((ColumnReferenceExpression) value);
        }
        if (value instanceof BinaryOperationExpression) {
            // Arithmetic and a join of texts pass a NULL operand on: a date that is always NULL plus one is.
            final BinaryOperationExpression operation = (BinaryOperationExpression) value;
            final BinaryOperator operator = operation.getOperator();
            if (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                    || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
                    || operator == BinaryOperator.MODULO || operator == BinaryOperator.CONCAT) {
                return alwaysNull(operation.getLeft()) || alwaysNull(operation.getRight());
            }
        }
        if (value instanceof CaseExpression) {
            return caseIsAlwaysNull((CaseExpression) value);
        }
        if (value instanceof FunctionCallExpression && ((FunctionCallExpression) value).getNameExpression() == null) {
            final FunctionCallExpression call = (FunctionCallExpression) value;
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if (name.equals("COALESCE") || name.equals("NVL") || name.equals("IFNULL")) {
                for (final Expression branch : call.getArguments()) {
                    if (!alwaysNull(branch)) {
                        return false;
                    }
                }
                return !call.getArguments().isEmpty();
            }
            if (name.equals("NULLIF")) {
                return !call.getArguments().isEmpty() && alwaysNull(call.getArguments().get(0));
            }
            if (NULL_OVER_NULLS.contains(name)) {
                // Over a column of NULLs; an aggregate over a NULL written out is tagged by its type alone.
                return call.getArguments().size() == 1 && alwaysNull(call.getArguments().get(0))
                    && !ConstantRootFold.isOverLiterals(call.getArguments().get(0), visitor.getFunctionRegistry());
            }
            if (PASSES_NULL_ON.contains(name)) {
                // A unit slot holds a word, never a value: DATE_TRUNC(month, d) reads no column named month.
                final int unitSlot = DateTimeUnitSlot.positionIn(name);
                for (int i = 0; i < call.getArguments().size(); i++) {
                    if (i != unitSlot && alwaysNull(call.getArguments().get(i))) {
                        return true;
                    }
                }
                return false;
            }
        }
        return null;
    }

    /**
     * Whether a CASE answers NULL on every row: every branch the statistics leave reachable does, a missing ELSE
     * among them — {@code CASE WHEN FALSE THEN d END} is always NULL.
     */
    private boolean caseIsAlwaysNull(final CaseExpression conditional) {
        for (final WhenClause when : conditional.getWhenClauses()) {
            final Boolean settled = settledCondition(when.getCondition());
            if (Boolean.FALSE.equals(settled)) {
                continue;
            }
            if (!alwaysNull(when.getResult())) {
                return false;
            }
            if (Boolean.TRUE.equals(settled)) {
                return true;
            }
        }
        return conditional.getElseExpression() == null || alwaysNull(conditional.getElseExpression());
    }

    /**
     * The interval of a calendar part read from a constant date, which the plan folds to the one value it is:
     * DAYOFYEAR over the 6th of May is 127 and tagged [SB1], where over a column it keeps its four digits' [SB2].
     * The part is one a function names — DAYOFYEAR, MONTH — or one EXTRACT or DATE_PART is asked for, and the
     * constant a cast of a literal, a TO_DATE over literals or a DATE_TRUNC of one:
     * {@code EXTRACT(dayofyear FROM TO_DATE('2024-05-06'))} and {@code DAYOFYEAR(DATE_TRUNC(month,
     * '2024-05-06'::DATE))} are [SB1]. An epoch part is not folded: {@code EXTRACT(epoch_second FROM
     * '2024-05-06'::DATE)} keeps its [SB8] (all live-verified). Null for any other call.
     */
    private ValueRange foldedDatePart(final FunctionCallExpression call, final String name,
                                      final List<Expression> args) {
        final Expression read;
        if (DatePartOperands.isOnePart(name) && args.size() == 1) {
            read = args.get(0);
        } else if ((name.equals("EXTRACT") || name.equals("DATE_PART")) && args.size() == 2) {
            read = args.get(1);
            final DataType declared = visitor.inferStaticType(call);
            // Only a calendar part folds, which declares two digits or four.
            if (!(declared instanceof NumericType) || ((NumericType) declared).getPrecision() > 4) {
                return null;
            }
        } else {
            return null;
        }
        if (!isConstantDate(read)) {
            return null;
        }
        final Object part;
        try {
            part = call.accept(visitor);
        } catch (final RuntimeException unread) {
            return null;
        }
        final BigDecimal exact = ValueRange.exactOf(part);
        return exact == null ? null : ValueRange.of(exact);
    }

    /**
     * The interval of a year or of a day of the year read from a DATE or TIMESTAMP_NTZ column, which the plan
     * bounds by the least and the greatest date the column's table holds — the table's statistics, whatever the
     * statement filters: {@code DAYOFYEAR(d)} over dates from the 10th of January to the 1st of March is [SB1], and
     * so is {@code YEAR(d)} over years 100 to 120. A day of the year is bounded only while both dates stand in one
     * year; a column that spans two keeps the part's whole range. MAX, MIN and ANY_VALUE of the column are read
     * alike, and a derived relation hands the column's days on (all live-verified). Null for any other call.
     */
    private ValueRange columnDatePart(final FunctionCallExpression call, final String name,
                                      final List<Expression> args) {
        final int operandAt;
        if (DatePartOperands.isOnePart(name) && args.size() == 1) {
            operandAt = 0;
        } else if ((name.equals("EXTRACT") || name.equals("DATE_PART")) && args.size() == 2) {
            operandAt = 1;
        } else {
            return null;
        }
        final ValueRange days = dayRange(args.get(operandAt));
        if (days == null || days.isEmpty() || days.getMin() == null || days.getMax() == null) {
            return null;
        }
        final DataType declared = visitor.inferStaticType(call);
        if (!(declared instanceof NumericType) || ((NumericType) declared).getPrecision() != 4) {
            // Only a year and a day of the year are wide enough for the statistics to narrow.
            return null;
        }
        final LocalDate least = LocalDate.ofEpochDay(days.getMin().longValue());
        final LocalDate greatest = LocalDate.ofEpochDay(days.getMax().longValue());
        final BigDecimal low = partOf(call, args, operandAt, least);
        final BigDecimal high = partOf(call, args, operandAt, greatest);
        if (low == null || high == null) {
            return null;
        }
        // What the part tells is read from a day whose year and day of the year no other part answers: a year —
        // the calendar's or a week's — grows with the date, a day of the year only within one year.
        final BigDecimal told = partOf(call, args, operandAt, TELLING_DAY);
        final boolean years = told != null && told.intValue() == TELLING_DAY.getYear();
        final boolean daysOfOneYear = told != null && told.intValue() == TELLING_DAY.getDayOfYear()
            && least.getYear() == greatest.getYear();
        return years || daysOfOneYear ? ValueRange.between(low, high).withNullable(days.isNullable()) : null;
    }

    /**
     * The days a date operand's values lie in: a column's; those of a MAX, MIN or ANY_VALUE over one; of a cast of
     * one to a DATE or to a timestamp that keeps no offset of its own, and of TO_DATE and TO_TIMESTAMP of one; of
     * a DATE moved by a whole number of days written out, by DATEADD, DATE_TRUNC or LAST_DAY; and of what
     * COALESCE, IFF, GREATEST and LEAST choose among such operands (all live-verified).
     */
    private ValueRange dayRange(final Expression operand) {
        if (operand instanceof ColumnReferenceExpression) {
            return visitor.dayRangeOf((ColumnReferenceExpression) operand);
        }
        if (operand instanceof CastExpression) {
            final CastExpression cast = (CastExpression) operand;
            if (cast.isTryMode() || !keepsDays(visitor.inferStaticType(cast))) {
                return null;
            }
            // A TIMESTAMP_TZ column's days are known once it is cast to a DATE.
            return cast.getExpression() instanceof ColumnReferenceExpression && isDate(visitor.inferStaticType(cast))
                ? visitor.dayRangeOf((ColumnReferenceExpression) cast.getExpression(), true)
                : dayRange(cast.getExpression());
        }
        if (operand instanceof FunctionCallExpression
                && ((FunctionCallExpression) operand).getNameExpression() == null) {
            return dayRangeOfCall((FunctionCallExpression) operand);
        }
        if (operand instanceof BinaryOperationExpression) {
            final BinaryOperationExpression moved = (BinaryOperationExpression) operand;
            final boolean added = moved.getOperator() == BinaryOperator.ADD;
            if (added || moved.getOperator() == BinaryOperator.SUBTRACT) {
                final ValueRange later = movedBy(moved.getLeft(), moved.getRight(), added);
                return later != null || !added ? later : movedBy(moved.getRight(), moved.getLeft(), true);
            }
        }
        return null;
    }

    /** The days a call over date operands answers within, or null for a call that is not read through. */
    private ValueRange dayRangeOfCall(final FunctionCallExpression read) {
        final String name = read.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = read.getArguments();
        if (NULL_OVER_NULLS.contains(name) && args.size() == 1) {
            return dayRange(args.get(0));
        }
        if (KEEPS_DAYS.contains(name) && args.size() == 1) {
            return keepsDays(visitor.inferStaticType(read)) ? dayRange(args.get(0)) : null;
        }
        if (name.equals("COALESCE") || name.equals("NVL") || name.equals("IFNULL")) {
            // The first operand that is never NULL ends the choice.
            ValueRange chosen = null;
            for (final Expression arg : args) {
                final ValueRange days = dayRange(arg);
                if (days == null) {
                    return null;
                }
                chosen = chosen == null ? days : chosen.union(days);
                if (!days.isEmpty() && !days.isNullable()) {
                    return chosen.withNullable(false);
                }
            }
            return chosen;
        }
        if (name.equals("IFF") && args.size() == 3) {
            final Boolean settled = settledCondition(args.get(0));
            final ValueRange then = Boolean.FALSE.equals(settled) ? null : dayRange(args.get(1));
            final ValueRange otherwise = Boolean.TRUE.equals(settled) ? null : dayRange(args.get(2));
            return settled != null ? settled.booleanValue() ? then : otherwise
                : then == null || otherwise == null ? null : then.union(otherwise);
        }
        if ((name.equals("GREATEST") || name.equals("LEAST")) && !args.isEmpty()) {
            return extremeDays(args, name.equals("GREATEST"));
        }
        final int moved = name.equals("DATEADD") && args.size() == 3 ? 2 : name.equals("DATE_TRUNC") && args.size() == 2
            ? 1 : name.equals("LAST_DAY") && !args.isEmpty() && args.size() <= 2 ? 0 : -1;
        if (moved < 0 || name.equals("DATEADD") && !(args.get(1) instanceof LiteralExpression)
                || !isDate(visitor.inferStaticType(args.get(moved)))) {
            return null;
        }
        // Each grows with the date it is given, so the ends of its days are the days of the column's ends.
        final ValueRange base = dayRange(args.get(moved));
        if (base == null || base.isEmpty() || base.getMin() == null || base.getMax() == null) {
            return base;
        }
        final BigDecimal least = dayOf(read, args, moved, LocalDate.ofEpochDay(base.getMin().longValue()));
        final BigDecimal greatest = dayOf(read, args, moved, LocalDate.ofEpochDay(base.getMax().longValue()));
        return least == null || greatest == null ? null
            : ValueRange.between(least, greatest).withNullable(base.isNullable());
    }

    /** The days GREATEST or LEAST answers within: the greatest, or the least, of its operands' ends. */
    private ValueRange extremeDays(final List<Expression> args, final boolean greatest) {
        BigDecimal low = null;
        BigDecimal high = null;
        boolean nullable = false;
        for (final Expression arg : args) {
            final ValueRange days = dayRange(arg);
            if (days == null || days.isEmpty() || days.getMin() == null || days.getMax() == null) {
                return null;
            }
            nullable |= days.isNullable();
            low = low == null ? days.getMin() : greatest ? low.max(days.getMin()) : low.min(days.getMin());
            high = high == null ? days.getMax() : greatest ? high.max(days.getMax()) : high.min(days.getMax());
        }
        return ValueRange.between(low, high).withNullable(nullable);
    }

    /** The day a call answers for one date in place of its date operand, or null when it answers no date. */
    private BigDecimal dayOf(final FunctionCallExpression call, final List<Expression> args, final int operandAt,
                             final LocalDate date) {
        final List<Expression> probed = new ArrayList<>(args);
        probed.set(operandAt, new CastExpression(new LiteralExpression(date.toString(), LiteralType.STRING), "DATE"));
        try {
            final Object answered = new FunctionCallExpression(call.getFunctionName(), probed).accept(visitor);
            return answered instanceof LocalDate ? BigDecimal.valueOf(((LocalDate) answered).toEpochDay())
                : answered instanceof LocalDateTime
                    ? BigDecimal.valueOf(((LocalDateTime) answered).toLocalDate().toEpochDay()) : null;
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** Whether a type is a DATE or a timestamp with no offset of its own, whose days a conversion keeps. */
    private static boolean keepsDays(final DataType type) {
        final String kind = type instanceof DateTimeType ? SqlTypeNames.canonical(type) : null;
        return kind != null
            && ("DATE".equals(kind) || kind.startsWith("TIMESTAMP_NTZ") || kind.startsWith("TIMESTAMP_LTZ"));
    }

    private static boolean isDate(final DataType type) {
        return type instanceof DateTimeType && "DATE".equals(SqlTypeNames.canonical(type));
    }

    /** The days of a DATE operand moved by the whole number of days {@code count} writes out, or null. */
    private ValueRange movedBy(final Expression date, final Expression count, final boolean added) {
        if (!(count instanceof LiteralExpression) || !(((LiteralExpression) count).getValue() instanceof Number)) {
            return null;
        }
        final BigDecimal days = ValueRange.exactOf(((LiteralExpression) count).getValue());
        if (days == null || days.stripTrailingZeros().scale() > 0) {
            return null;
        }
        final ValueRange base = isDate(visitor.inferStaticType(date)) ? dayRange(date) : null;
        if (base == null || base.isEmpty() || base.getMin() == null || base.getMax() == null) {
            return base;
        }
        final BigDecimal by = added ? days : days.negate();
        return ValueRange.between(base.getMin().add(by), base.getMax().add(by)).withNullable(base.isNullable());
    }

    /** What a date-part call answers for one date, the part and its spelling the call's own. */
    private BigDecimal partOf(final FunctionCallExpression call, final List<Expression> args, final int operandAt,
                              final LocalDate date) {
        final List<Expression> probed = new ArrayList<>(args);
        probed.set(operandAt, new CastExpression(new LiteralExpression(date.toString(), LiteralType.STRING), "DATE"));
        try {
            return ValueRange.exactOf(new FunctionCallExpression(call.getFunctionName(), probed).accept(visitor));
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** Whether a value is a date the plan holds as a constant: see {@link #foldedDatePart}. */
    private boolean isConstantDate(final Expression value) {
        if (value instanceof CastExpression) {
            final Expression converted = ((CastExpression) value).getExpression();
            return converted instanceof LiteralExpression && !isBareNull(converted) || isConstantDate(converted);
        }
        if (!(value instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) value;
        if (call.getNameExpression() != null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (name.equals("DATE_TRUNC")) {
            return call.getArguments().size() == 2 && isConstantDate(call.getArguments().get(1));
        }
        if (!name.equals("TO_DATE") || call.getArguments().isEmpty()) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!(argument instanceof LiteralExpression) || isBareNull(argument)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The interval of a SUM the plan rewrites over a shifted column, which it computes as the column's SUM
     * and the constant times the column's COUNT: {@code SUM(c + 200000)} over a column whose SUM reaches
     * 9.1 x 10^18 is [SB8] where the shifted values' own sum would not fit, and {@code SUM(s + 10^14)}
     * over a NUMBER(7,2) [SB8] too. A shift by exactly ONE is read at the rewrite's whole intermediate
     * width instead — {@code SUM(n + 1)}, {@code SUM(1 + n)} and {@code SUM(n - 1)} are [SB16] over any
     * column, and so is {@code COALESCE(SUM(n + 1), 0)} — while {@code SUM(n + -1)} is not (live-verified).
     */
    private ValueRange rewrittenSumRange(final FunctionCallExpression call, final SumShift shift) {
        final boolean columnLeft = shift.isColumnLeft();
        final ValueRange by = infer(shift.getConstant());
        if (by == null || by.isEmpty()) {
            return null;
        }
        if (by.getMin().compareTo(BigDecimal.ONE) == 0 && by.getMax().compareTo(BigDecimal.ONE) == 0) {
            final NumericType rewritten = visitor.shiftedColumnSum(call, null);
            if (rewritten == null) {
                return null;
            }
            final BigDecimal bound = BigDecimal.ONE.scaleByPowerOfTen(
                rewritten.getPrecision() - Math.max(0, rewritten.getScale()));
            return aggregated(ValueRange.between(bound.negate(), bound), false);
        }
        // The column is read where it lives — beneath a merged derived relation, in the relation it reads.
        final ValueRange summed = accumulated(shift.getColumnRange());
        final Long rows = visitor.countableRows();
        if (summed == null || rows == null) {
            return null;
        }
        final ValueRange moved = ValueRange.between(BigDecimal.ZERO, BigDecimal.valueOf(rows)).multiply(by);
        final ValueRange left = columnLeft ? summed : moved;
        final ValueRange right = columnLeft ? moved : summed;
        return aggregated(shift.getOperator() == BinaryOperator.SUBTRACT ? left.subtract(right) : left.add(right),
            false);
    }

    /** A TO_NUMBER-family call's interval: its exact operand's, or the one value a constant folds to. */
    private ValueRange convertedRange(final FunctionCallExpression call, final List<Expression> args) {
        final ValueRange exact = exactOperandRange(args.get(0));
        final ValueRange kept = exact != null ? exact : foldedConversion(call, args);
        // What is always NULL converts to no number at all: TO_NUMBER over a text column of NULLs.
        return kept == null && alwaysNull(args.get(0)) ? ValueRange.EMPTY : kept;
    }

    /**
     * The one value a conversion into an exact NUMBER folds to while the statement compiles, or null
     * where it does not fold. The account converts a text, FLOAT or VARIANT CONSTANT and tags the result
     * by that value — {@code '5'::NUMBER} is [SB1], {@code '127.5'::NUMBER} and
     * {@code 127.5::FLOAT::NUMBER} [SB2], {@code TRY_TO_DECIMAL('x', 10, 2)} a NULL's [SB1] — where the
     * same conversion over a column keeps the declared width. A conversion that fails does not fold:
     * {@code ''::NUMBER} is [SB16]. Which operands count as constants is {@link #foldsAsConstant}.
     */
    private ValueRange foldedConversion(final Expression conversion, final List<Expression> operands) {
        for (final Expression operand : operands) {
            if (!foldsAsConstant(operand)) {
                return null;
            }
        }
        if (exactScale(visitor.inferStaticType(conversion)) == null) {
            return null;
        }
        final Object folded;
        try {
            folded = conversion.accept(visitor);
        } catch (final RuntimeException unconverted) {
            return null;
        }
        if (folded == null) {
            return ValueRange.EMPTY;
        }
        final BigDecimal value = ValueRange.exactOf(folded);
        return value != null ? ValueRange.of(value) : null;
    }

    /**
     * Whether the plan holds an operand as a constant it converts while compiling: a literal, a cast of
     * one to anything but text (of a NULL, to anything), and a {@code ||}, CONCAT, UPPER or TO_VARIANT
     * over such constants. The edges are measured, not derived — {@code CAST(CAST('5' AS VARCHAR) AS
     * NUMBER)}, {@code TRIM(' 5 ')::NUMBER}, {@code TO_CHAR(5)::NUMBER}, {@code (1.5::FLOAT + 1)::NUMBER},
     * {@code IFF(TRUE, '5', '6')::NUMBER}, {@code COALESCE('5', '6')::NUMBER} and
     * {@code PARSE_JSON('5')::NUMBER} all keep the declared width on the account. A scalar subquery with no
     * FROM over one such constant is one too — {@code (SELECT '5')::NUMBER}, {@code (SELECT UPPER('5'))::NUMBER}
     * and {@code (SELECT TO_VARIANT(5))::NUMBER} are [SB1] — where {@code (SELECT '5' FROM t LIMIT 1)} is not.
     */
    boolean foldsAsConstant(final Expression operand) {
        if (operand instanceof LiteralExpression) {
            return true;
        }
        if (operand instanceof SubqueryExpression) {
            final Expression item = fromlessItem((SubqueryExpression) operand);
            return item != null && foldsAsConstant(item);
        }
        if (operand instanceof CastExpression) {
            final Expression converted = ((CastExpression) operand).getExpression();
            return isBareNull(converted)
                || !(visitor.inferStaticType(operand) instanceof StringType) && foldsAsConstant(converted);
        }
        if (operand instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) operand;
            return binary.getOperator() == BinaryOperator.CONCAT
                && foldsAsConstant(binary.getLeft()) && foldsAsConstant(binary.getRight());
        }
        if (!(operand instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) operand;
        if (call.getNameExpression() != null || call.getArguments().isEmpty()
                || !CONSTANT_FOLDING_CALLS.contains(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!foldsAsConstant(argument)) {
                return false;
            }
        }
        return true;
    }

    /** The one item of a scalar subquery that is nothing but a FROM-less select of it, or null. */
    private Expression fromlessItem(final SubqueryExpression subquery) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor == null ? null
            : queryExecutor.subqueryStatement(subquery.getSubquery());
        if (parsed == null || parsed.withClause() != null || !parsed.setOperator().isEmpty()
                || parsed.orderByClause() != null || parsed.limitClause() != null || parsed.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = parsed.selectOperand(0).selectClause();
        if (select == null || select.tableExpression() != null || select.whereClause() != null
                || select.topClause() != null || select.connectByClause() != null || select.groupByClause() != null
                || select.havingClause() != null || select.qualifyClause() != null
                || select.selectList().selectItem().size() != 1) {
            return null;
        }
        final FrostlakeParser.ExpressionContext value =
            SelectItemAccessors.getItemValueExpr(select.selectList().selectItem(0));
        return value == null ? null : ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value));
    }

    /**
     * Whether the plan holds {@code operand} as the one value it stands for, so that a unary plus over it keeps
     * that value's interval: a literal (NULL included), its negation or unary plus, a cast of a bare NULL or
     * arithmetic beside one, a FROM-less scalar subquery of such a value, a conversion into an exact number
     * that folds from a text, FLOAT or VARIANT constant, and ZEROIFNULL of such a value. Anything computed —
     * arithmetic, a cast between exact numbers, a function, a column — leaves the unary plus its declared
     * width. Live tags {@code +1.5}, {@code +(-123.45)}, {@code +(SELECT 123.45)}, {@code +'123.45'::NUMBER(5,2)}
     * and {@code +ZEROIFNULL(123.45)} by the value, and {@code +n}, {@code +(1.5 + 1.5)},
     * {@code +(123.45::NUMBER(5,2))}, {@code +CAST(1 AS INT)}, {@code +ABS(123.45)} and
     * {@code +IFF(TRUE, 123.45, 1)} by the width.
     */
    private boolean heldAsValue(final Expression operand) {
        if (operand instanceof LiteralExpression) {
            return true;
        }
        if (operand instanceof UnaryOperationExpression) {
            final UnaryOperator sign = ((UnaryOperationExpression) operand).getOperator();
            return (sign == UnaryOperator.NEGATE || sign == UnaryOperator.PLUS)
                && heldAsValue(((UnaryOperationExpression) operand).getOperand());
        }
        if (operand instanceof BinaryOperationExpression) {
            return isBareNull(((BinaryOperationExpression) operand).getLeft())
                || isBareNull(((BinaryOperationExpression) operand).getRight());
        }
        if (operand instanceof SubqueryExpression) {
            final Expression item = fromlessItem((SubqueryExpression) operand);
            return item != null && heldAsValue(item);
        }
        if (operand instanceof CastExpression) {
            final Expression converted = ((CastExpression) operand).getExpression();
            return isBareNull(converted) || foldsFromInexactConstant(converted);
        }
        if (!(operand instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) operand;
        if (call.getNameExpression() != null || call.getArguments().isEmpty()) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (name.equals("ZEROIFNULL")) {
            return call.getArguments().size() == 1 && heldAsValue(call.getArguments().get(0));
        }
        return NUMERIC_CONVERSIONS.contains(name) && foldsFromInexactConstant(call.getArguments().get(0));
    }

    /** Whether a conversion's operand is a constant it folds while compiling, and no exact number already. */
    private boolean foldsFromInexactConstant(final Expression converted) {
        final DataType type = visitor.inferStaticType(converted);
        return !(type instanceof NumericType && !NumericType.isApproximate(type)) && foldsAsConstant(converted);
    }

    /**
     * An aggregate's interval, and whether it may answer NULL: never while the plan answers it from the
     * whole table's statistics over a column holding a value — live tags {@code COALESCE(MAX(n), 3000.5)}
     * by MAX's width alone — and it may once a grouping key or a filter the statistics cannot prove takes
     * the query past them (the same call under GROUP BY is tagged by the union). An aggregate that can
     * hand back one of its NULL inputs ({@code keepsNulls}, ANY_VALUE) may whenever its argument may.
     */
    private ValueRange aggregated(final ValueRange range, final boolean keepsNulls) {
        if (range == null || range.isEmpty()) {
            return range;
        }
        return range.withNullable(keepsNulls && range.isNullable() || !visitor.readsWithinStatistics());
    }

    /**
     * The range MEDIAN and the two ordered percentiles carry: their key's own — except over a VARCHAR
     * or VARIANT key, whose values are converted one by one to NUMBER(9,0) before the percentile is
     * taken. Nothing is known of the converted values' spread, and live tags such a result by its
     * declared width alone — NUMBER(12,3) [SB8], NUMBER(9,0) [SB4] — where the key's statistics would
     * have narrowed it to [SB2] (live-verified over a VARIANT of 2 and 4 and a VARCHAR of '3' and '5').
     */
    private ValueRange percentileRange(final Expression key) {
        if (key == null) {
            return null;
        }
        final DataType keyType = visitor.inferStaticType(key);
        if (keyType instanceof StringType || keyType instanceof VariantType) {
            return null;
        }
        return computedFrom(key);
    }

    private ValueRange windowRange(final WindowFunctionExpression window) {
        final String name = window.getFunctionName() == null ? "" : window.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = window.getArguments();
        switch (name) {
            case "SUM":
                return accumulated(firstArgument(args));
            case "AVG":
            case "VARIANCE":
            case "VAR_POP":
            case "VAR_SAMP":
            case "VARIANCE_POP":
            case "VARIANCE_SAMP":
                return ValueRange.WIDEST;
            case "MIN":
            case "MAX":
            case "FIRST_VALUE":
            case "LAST_VALUE":
            case "ANY_VALUE":
                return firstArgument(args);
            case "LAG":
            case "LEAD":
            case "NTH_VALUE":
                // A row whose offset reaches past its partition answers NULL.
                return mayBeNull(firstArgument(args));
            case "MEDIAN":
            case "PERCENTILE_CONT":
            case "PERCENTILE_DISC":
                return percentileRange(window.getWithinGroupOrdered() != null
                    ? window.getWithinGroupOrdered() : args.isEmpty() ? null : args.get(0));
            case "RATIO_TO_REPORT":
                // The account rewrites it into a division by SUM over the partition, and the tag is
                // the division's: the argument shifted by its scale and the result's against the
                // trillion-fold sum, whichever is wider.
                final ValueRange share = firstArgument(args);
                final Integer argumentScale = args.isEmpty() ? null
                    : exactScale(visitor.inferStaticType(args.get(0)));
                final Integer resultScale = exactScale(visitor.inferStaticType(window));
                if (share == null || argumentScale == null || resultScale == null) {
                    return null;
                }
                return mayBeNull(intermediateOf(share, accumulated(share), argumentScale, resultScale));
            default:
                return null;
        }
    }

    /**
     * DECODE as the planner prunes it: a search the statistics rule out contributes nothing, a search
     * they settle ends the walk, and the default counts only when the walk reaches it — a NULL where
     * none is written. Each search is matched as {@link #decideMatch} says.
     */
    private ValueRange decodeRange(final List<Expression> args) {
        if (args.size() < 3) {
            return null;
        }
        final Expression subject = args.get(0);
        final List<Expression> branches = new ArrayList<>();
        boolean settled = false;
        int i = 1;
        for (; i + 1 < args.size(); i += 2) {
            final Boolean decided = decideMatch(subject, args.get(i), true);
            if (decided != null && !decided.booleanValue()) {
                continue;
            }
            branches.add(args.get(i + 1));
            if (decided != null) {
                settled = true;
                break;
            }
        }
        final boolean defaulted = !settled && i < args.size();
        if (defaulted) {
            branches.add(args.get(i));
        }
        final ValueRange range = unionOf(branches);
        return settled || defaulted ? range : mayBeNull(range);
    }

    /**
     * LEAST or GREATEST as the planner bounds it: an argument the statistics prove never chosen drops
     * out — LEAST(c, 1) over a column that never falls below 1.5 is the literal alone — and arguments
     * that may each win are united. Any argument that may be NULL makes the result NULL-able.
     */
    private ValueRange extremeRange(final List<Expression> args, final boolean least) {
        if (args.isEmpty()) {
            return null;
        }
        ValueRange chosen = infer(args.get(0));
        boolean anyNullable = chosen != null && chosen.isNullable();
        for (int i = 1; i < args.size() && chosen != null; i++) {
            final ValueRange next = infer(args.get(i));
            if (next == null) {
                return null;
            }
            anyNullable |= next.isNullable();
            if (chosen.isEmpty() || next.isEmpty()) {
                chosen = chosen.union(next);
                continue;
            }
            // An argument drops out when the ones before it can never lose to it, and takes over only
            // when it beats them OUTRIGHT: a tie at the boundary keeps both (live: LEAST(c, 1) over a
            // column running from 1 to 9999 is the column's width, not the literal's).
            if (least) {
                if (chosen.getMax().compareTo(next.getMin()) <= 0) {
                    continue;
                }
                chosen = next.getMax().compareTo(chosen.getMin()) < 0 ? next : chosen.union(next);
            } else {
                if (chosen.getMin().compareTo(next.getMax()) >= 0) {
                    continue;
                }
                chosen = next.getMin().compareTo(chosen.getMax()) > 0 ? next : chosen.union(next);
            }
        }
        return chosen == null ? null : chosen.withNullable(anyNullable);
    }

    /**
     * Whether a predicate is settled by the statistics: TRUE or FALSE when it holds that way of every
     * row, null when it does not or the predicate is no test this knows. A comparison is settled only
     * over operands that settle (see {@link #settles}) — live leaves {@code IFF(n > 5, 3000.5, 1.5)} open
     * over a column holding 0.50, 1.00 and a NULL, and settles it over one holding no NULL — and so is the
     * operand of an IS [NOT] NULL. NOT, AND and OR combine settled parts as three-valued logic, so one
     * FALSE settles an AND however open the rest is; BETWEEN is the AND of its two bounds and an IN list
     * the OR of its equalities.
     */
    /**
     * Whether the statistics settle a condition: TRUE where it holds on every row, FALSE where it holds on
     * none, null where they leave it open.
     *
     * @param predicate the condition
     * @return the settled value, or null
     */
    public Boolean settledCondition(final Expression predicate) {
        return decide(predicate);
    }

    private Boolean decide(final Expression predicate) {
        if (predicate instanceof LiteralExpression) {
            // A constant condition prunes the other branch outright: IFF(FALSE, s, 1.5) over a text
            // column is tagged by the 1.5 alone (live-verified), a NULL condition takes the else.
            final LiteralExpression constant = (LiteralExpression) predicate;
            if (constant.getType() == LiteralType.BOOLEAN && constant.getValue() instanceof Boolean) {
                return (Boolean) constant.getValue();
            }
            return constant.getType() == LiteralType.NULL ? Boolean.FALSE : null;
        }
        if (predicate instanceof UnaryOperationExpression) {
            final UnaryOperationExpression negation = (UnaryOperationExpression) predicate;
            return negation.getOperator() == UnaryOperator.NOT ? not(decide(negation.getOperand())) : null;
        }
        if (predicate instanceof IsNullExpression) {
            // IFF(n IS NULL, 3000.5, n) over a column holding no NULL is tagged by n alone on the account.
            final IsNullExpression test = (IsNullExpression) predicate;
            final ValueRange tested = infer(test.getOperand());
            if (tested != null && tested.isEmpty()) {
                // What is always NULL settles the test the other way: IFF(s IS NULL, s + 1, 2) over a text
                // column of NULLs is tagged by its first branch.
                return Boolean.valueOf(!test.isNot());
            }
            return settles(tested) ? Boolean.valueOf(test.isNot()) : null;
        }
        if (predicate instanceof BetweenExpression) {
            final BetweenExpression bounds = (BetweenExpression) predicate;
            final Boolean within = and(decideLess(bounds.getLower(), bounds.getValue(), true),
                decideLess(bounds.getValue(), bounds.getUpper(), true));
            return bounds.isNot() ? not(within) : within;
        }
        if (predicate instanceof InExpression) {
            final InExpression list = (InExpression) predicate;
            if (list.hasSubquery() || list.getValues() == null) {
                return null;
            }
            Boolean member = Boolean.FALSE;
            for (final Expression candidate : list.getValues()) {
                member = or(member, decideEqual(list.getValue(), candidate));
            }
            return list.isNot() ? not(member) : member;
        }
        if (!(predicate instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression comparison = (BinaryOperationExpression) predicate;
        switch (comparison.getOperator()) {
            case AND:
                return and(decide(comparison.getLeft()), decide(comparison.getRight()));
            case OR:
                return or(decide(comparison.getLeft()), decide(comparison.getRight()));
            case EQUAL:
                return decideEqual(comparison.getLeft(), comparison.getRight());
            case NOT_EQUAL:
                return not(decideEqual(comparison.getLeft(), comparison.getRight()));
            case LESS_THAN:
                return decideLess(comparison.getLeft(), comparison.getRight(), false);
            case LESS_THAN_OR_EQUAL:
                return decideLess(comparison.getLeft(), comparison.getRight(), true);
            case GREATER_THAN:
                return decideLess(comparison.getRight(), comparison.getLeft(), false);
            case GREATER_THAN_OR_EQUAL:
                return decideLess(comparison.getRight(), comparison.getLeft(), true);
            default:
                return null;
        }
    }

    /**
     * Whether a CASE's WHEN is taken on every row, on none, or on some: a simple CASE's value is matched
     * against the operand as a DECODE search is, a searched CASE's condition decided.
     */
    private Boolean decideWhen(final WhenClause when) {
        if (when.isOperandMatch() && when.getCondition() instanceof BinaryOperationExpression) {
            final BinaryOperationExpression match = (BinaryOperationExpression) when.getCondition();
            return decideMatch(match.getLeft(), match.getRight(), false);
        }
        return decide(when.getCondition());
    }

    /**
     * Whether a DECODE search or a simple CASE value matches the subject on every row (TRUE), on none
     * (FALSE) or on some (null). Disjoint intervals match on no row whatever NULLs the subject holds, since
     * a NULL subject matches no value — live settles {@code CASE n WHEN 7 THEN …} and
     * {@code DECODE(n, 7, …)} over a column holding 0.50, 1.00 and a NULL — unless DECODE may pair a NULL
     * with a NULL; a match on every row needs both sides free of NULLs — {@code DECODE(n, 1, 1.5, 3000.5)}
     * stays open over a column holding 1.00 and a NULL. DECODE matches a NULL search to a NULL subject
     * ({@code nullMatchesNull}), so its NULL search matches no row of a subject that cannot be NULL; a
     * simple CASE's NULL value is left open.
     */
    private Boolean decideMatch(final Expression subject, final Expression search, final boolean nullMatchesNull) {
        final ValueRange a = infer(subject);
        if (a == null) {
            return null;
        }
        if (isBareNull(search)) {
            return nullMatchesNull && !a.isEmpty() && !a.isNullable() ? Boolean.FALSE : null;
        }
        final ValueRange b = infer(search);
        final Boolean equal = a.equalTo(b);
        if (equal == null) {
            return null;
        }
        if (!equal.booleanValue()) {
            return nullMatchesNull && a.isNullable() && b.isNullable() ? null : Boolean.FALSE;
        }
        return a.isNullable() || b.isNullable() ? null : Boolean.TRUE;
    }

    /** Whether {@code left = right} is settled: both operands settle and their intervals decide it. */
    private Boolean decideEqual(final Expression left, final Expression right) {
        final ValueRange a = infer(left);
        final ValueRange b = infer(right);
        return settles(a) && settles(b) ? a.equalTo(b) : null;
    }

    /** Whether {@code left < right} (or {@code <=} when {@code orEqual}) is settled by the intervals. */
    private Boolean decideLess(final Expression left, final Expression right, final boolean orEqual) {
        final ValueRange a = infer(left);
        final ValueRange b = infer(right);
        return settles(a) && settles(b) ? a.below(b, orEqual) : null;
    }

    /** Three-valued NOT over a settled value; an open one stays open. */
    private static Boolean not(final Boolean settled) {
        return settled == null ? null : Boolean.valueOf(!settled.booleanValue());
    }

    /** Three-valued AND: FALSE once either side is, TRUE when both are, open otherwise. */
    private static Boolean and(final Boolean left, final Boolean right) {
        if (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right) ? Boolean.TRUE : null;
    }

    /** Three-valued OR: TRUE once either side is, FALSE when both are, open otherwise. */
    private static Boolean or(final Boolean left, final Boolean right) {
        if (Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right) ? Boolean.FALSE : null;
    }

    private ValueRange firstArgument(final List<Expression> args) {
        return args == null || args.isEmpty() ? null : infer(args.get(0));
    }

    private static ValueRange accumulated(final ValueRange addend) {
        return addend == null ? null : addend.scaledBy(ACCUMULATED_ROWS);
    }
}
