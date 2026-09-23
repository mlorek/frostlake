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

import dev.frostlake.values.ValueRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The interval REDUCE's accumulator is tagged by in the storage-tag channel. The account plans one only
 * where the accumulator provably stays among values it already knows; everywhere else the result keeps its
 * declared width. Live-verified over {@code REDUCE(ARRAY_CONSTRUCT(1, 2), start, (acc, x) -> body)}:
 *
 * <ul>
 *   <li>the start must be a numeric constant — a literal, or arithmetic, a cast, ABS or TO_NUMBER over
 *       literals: {@code 1} is [SB1], {@code 100000::NUMBER(10,0)} and {@code 1 * 100000} [SB4],
 *       {@code 1.5::NUMBER(5,2)} [SB2]; a column, a scalar subquery, {@code '5'::NUMBER} and
 *       {@code NULL::NUMBER} leave the declared width, and so does a lambda that declares its parameter
 *       types;</li>
 *   <li>the body must hand the accumulator back as its leading value — the accumulator itself, its
 *       negation, or the first value branch of IFF, CASE, DECODE, NVL2, NULLIF, GREATEST, LEAST, or of
 *       COALESCE, NVL and IFNULL past a bare NULL: {@code acc}, {@code -acc}, {@code IFF(x > 5, acc, 0)}
 *       and {@code COALESCE(NULL, acc)} keep an interval, while {@code 5}, {@code IFF(x > 5, 5, 6)} and
 *       {@code IFF(x > 5, 100000, acc)} do not;</li>
 *   <li>the interval is the start's joined with every value branch — {@code IFF(x > 5, acc, 100000)},
 *       {@code LEAST(acc, 100000)} and {@code NVL2(x, acc, 100000)} are [SB4] and
 *       {@code COALESCE(acc, 100000000000)} [SB8] — except that DECODE, a simple CASE and an IFF or CASE on
 *       a constant condition answer from their first branch alone: {@code DECODE(x, 1, acc, 100000)} and
 *       {@code IFF(TRUE, acc, 100000)} are [SB1];</li>
 *   <li>a branch computed from the accumulator any other way has no interval, and neither does the whole:
 *       {@code acc + 0}, {@code +acc}, {@code acc::NUMBER(38,0)}, {@code ABS(acc)} and
 *       {@code ZEROIFNULL(acc)} are [SB16]. A branch reading neither parameter keeps its own interval:
 *       {@code IFF(x > 5, acc, (SELECT 100000))} is [SB4].</li>
 * </ul>
 */
final class ReduceAccumulatorRange {

    private final ValueRangeInferencer inferencer;
    private final List<String> parameters;
    private final String accumulator;
    private final ValueRange startRange;

    private ReduceAccumulatorRange(final ValueRangeInferencer inferencer, final LambdaExpression lambda,
                                   final ValueRange startRange) {
        this.inferencer = inferencer;
        this.parameters = lambda.getParameters();
        this.accumulator = lambda.getParameters().get(0);
        this.startRange = startRange;
    }

    /**
     * The interval of a REDUCE call, or null where the plan keeps its declared width.
     *
     * @param inferencer the inferencer reading the statement's own intervals
     * @param call       the REDUCE call
     * @return the accumulator's interval, or null
     */
    static ValueRange of(final ValueRangeInferencer inferencer, final FunctionCallExpression call) {
        final List<Expression> args = call.getArguments();
        if (args.size() != 3 || !(args.get(2) instanceof LambdaExpression)) {
            return null;
        }
        final LambdaExpression lambda = (LambdaExpression) args.get(2);
        if (lambda.getParameters() == null || lambda.getParameters().isEmpty() || declaresTypes(lambda)
                || !isNumericConstant(args.get(1))) {
            return null;
        }
        final ValueRange start = inferencer.infer(args.get(1));
        if (start == null || start.isEmpty()) {
            return null;
        }
        final ReduceAccumulatorRange reading = new ReduceAccumulatorRange(inferencer, lambda, start);
        if (!reading.leadsWithAccumulator(lambda.getBody())) {
            return null;
        }
        final ValueRange body = reading.bodyRange(lambda.getBody());
        return body == null ? null : start.union(body).opaqueToComputation();
    }

    /** Whether an expression is the accumulator, the lambda's first parameter, read by its bare name. */
    private boolean isAccumulator(final Expression expr) {
        return expr instanceof ColumnReferenceExpression && !((ColumnReferenceExpression) expr).isQualified()
            && accumulator.equalsIgnoreCase(((ColumnReferenceExpression) expr).getColumnName());
    }

    private static boolean declaresTypes(final LambdaExpression lambda) {
        if (lambda.getParameterTypes() == null) {
            return false;
        }
        for (final String written : lambda.getParameterTypes()) {
            if (written != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether the start folds to a number before the call runs: literals and exact arithmetic over them. */
    private static boolean isNumericConstant(final Expression start) {
        if (start instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) start).getType();
            return type == LiteralType.INTEGER || type == LiteralType.DECIMAL;
        }
        if (start instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) start;
            return unary.getOperator() == UnaryOperator.NEGATE && isNumericConstant(unary.getOperand());
        }
        if (start instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) start;
            final BinaryOperator operator = binary.getOperator();
            return (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                    || operator == BinaryOperator.MULTIPLY)
                && isNumericConstant(binary.getLeft()) && isNumericConstant(binary.getRight());
        }
        if (start instanceof CastExpression) {
            return isNumericConstant(((CastExpression) start).getExpression());
        }
        if (start instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) start;
            if (call.getNameExpression() != null || call.getArguments().isEmpty()) {
                return false;
            }
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if (!name.equals("ABS") && !name.equals("TO_NUMBER") && !name.equals("TO_DECIMAL")
                    && !name.equals("TO_NUMERIC")) {
                return false;
            }
            for (final Expression argument : call.getArguments()) {
                if (!isNumericConstant(argument)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Whether the body's leading value — see the class comment — is the accumulator. */
    private boolean leadsWithAccumulator(final Expression body) {
        if (isAccumulator(body)) {
            return true;
        }
        if (isNegation(body)) {
            return leadsWithAccumulator(((UnaryOperationExpression) body).getOperand());
        }
        final List<Expression> branches = valueBranches(body);
        final Expression leading = branches == null ? null : firstValue(branches);
        return leading != null && leadsWithAccumulator(leading);
    }

    /** The interval the body's value lies in with the accumulator at the start's, or null when unknown. */
    private ValueRange bodyRange(final Expression body) {
        if (isAccumulator(body)) {
            return startRange;
        }
        if (isBareNull(body)) {
            return ValueRange.EMPTY;
        }
        if (isNegation(body)) {
            final ValueRange operand = bodyRange(((UnaryOperationExpression) body).getOperand());
            return operand == null ? null : operand.negate();
        }
        final List<Expression> branches = valueBranches(body);
        if (branches == null) {
            // A value of its own: computed from a parameter it has no interval, and read from nothing the
            // lambda binds it keeps the one the statement gives it.
            return LambdaParameterReferenceWalk.reads(body, parameters) ? null : inferencer.infer(body);
        }
        if (picksFirstBranch(body)) {
            final Expression first = firstValue(branches);
            return first == null ? ValueRange.EMPTY : bodyRange(first);
        }
        ValueRange union = ValueRange.EMPTY;
        for (final Expression branch : branches) {
            final ValueRange one = bodyRange(branch);
            if (one == null) {
                return null;
            }
            union = union.union(one);
        }
        return union;
    }

    /**
     * The value branches of a pick — the expressions whose value it may answer, in order, a missing ELSE as
     * a NULL — or null when the expression is no pick.
     */
    private static List<Expression> valueBranches(final Expression body) {
        final List<Expression> branches = new ArrayList<>();
        if (body instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) body;
            for (final WhenClause when : conditional.getWhenClauses()) {
                branches.add(when.getResult());
            }
            branches.add(conditional.getElseExpression() != null ? conditional.getElseExpression()
                : new LiteralExpression(null, LiteralType.NULL));
            return branches;
        }
        if (!(body instanceof FunctionCallExpression) || ((FunctionCallExpression) body).getNameExpression() != null) {
            return null;
        }
        final List<Expression> args = ((FunctionCallExpression) body).getArguments();
        switch (((FunctionCallExpression) body).getFunctionName().toUpperCase(Locale.ROOT)) {
            case "IFF":
            case "NVL2":
                return args.size() == 3 ? args.subList(1, 3) : null;
            case "DECODE":
                if (args.size() < 3) {
                    return null;
                }
                int i = 1;
                for (; i + 1 < args.size(); i += 2) {
                    branches.add(args.get(i + 1));
                }
                if (i < args.size()) {
                    branches.add(args.get(i));
                }
                return branches;
            case "NULLIF":
                return args.isEmpty() ? null : args.subList(0, 1);
            case "GREATEST":
            case "LEAST":
            case "COALESCE":
            case "NVL":
            case "IFNULL":
                return args.isEmpty() ? null : args;
            default:
                return null;
        }
    }

    /** Whether a pick answers from its first value branch alone: DECODE, a simple CASE, a constant condition. */
    private static boolean picksFirstBranch(final Expression pick) {
        if (pick instanceof CaseExpression) {
            final List<WhenClause> whens = ((CaseExpression) pick).getWhenClauses();
            return !whens.isEmpty()
                && (whens.get(0).isOperandMatch() || whens.get(0).getCondition() instanceof LiteralExpression);
        }
        final FunctionCallExpression call = (FunctionCallExpression) pick;
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        return name.equals("DECODE")
            || name.equals("IFF") && call.getArguments().get(0) instanceof LiteralExpression;
    }

    /** The first branch that is not the bare word NULL, or null when every one is. */
    private static Expression firstValue(final List<Expression> branches) {
        for (final Expression branch : branches) {
            if (!isBareNull(branch)) {
                return branch;
            }
        }
        return null;
    }

    private static boolean isNegation(final Expression expr) {
        return expr instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE;
    }

    private static boolean isBareNull(final Expression expr) {
        return expr instanceof LiteralExpression && ((LiteralExpression) expr).getType() == LiteralType.NULL;
    }
}
