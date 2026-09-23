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

import dev.frostlake.functions.FunctionRegistry;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether an argument is a CONSTANT the way a generator's seed and UNIFORM's bounds must be: an argument
 * the plan can fold before the statement runs. Live-verified:
 *
 * <ul>
 *   <li>constant — literals, session variables and deterministic calls over them: {@code RANDOM(1 + 1)},
 *       {@code RANDOM('5')}, {@code RANDOM(NULL)}, {@code RANDOM(ABS(-5))}, {@code RANDOM(HASH(1))},
 *       {@code RANDOM(PARSE_JSON('5'))}, {@code RANDOM(5 + $v)}, {@code RANDOM(NULLIF(1, 1))},
 *       {@code RANDOM(IFF(NULL, 1, 2))}, {@code RANDOM(ARRAY_SIZE([NULL]))},
 *       {@code RANDOM(DATEDIFF(day, '2020-01-01', '2020-01-05'))}, and a generator over a constant,
 *       {@code RANDOM(UNIFORM(1, 10, 5))}; and a NULL the plan does not convert — a call that folds to
 *       one ({@link UntypedNullFold}), {@code RANDOM(YEAR(NULL))}, {@code RANDOM(DATEADD(day, n, NULL))},
 *       {@code RANDOM(COALESCE(NULL, NULL))}, {@code RANDOM(IFF(TRUE, NULL, NULL))} where what it tests is
 *       constant, the value NVL2 tests, {@code RANDOM(NVL2(NULL, 1, 2))}, and {@code RANDOM(HASH(NULL))};</li>
 *   <li>not constant — a column, a scalar subquery, a window or an aggregate; a call with no arguments,
 *       {@code PI()}, {@code CURRENT_DATE()}, {@code CURRENT_USER()} — but for the empty
 *       {@code ARRAY_CONSTRUCT()} and {@code OBJECT_CONSTRUCT()} — and anything around one,
 *       {@code ABS(PI())}; RANDOM and the SEQ functions whatever their arguments, {@code RANDOM(RANDOM(5))}; a call that reads the session, {@code RANDOM(GETVARIABLE('V'))};
 *       and an untyped NULL the plan converts, {@code ABS(NULL)}, {@code NULL + 1}, {@code NULL AND TRUE},
 *       {@code CAST(NULL AS INT)}, {@code COALESCE(NULL, 5)}, {@code COALESCE(NULL, NULL) + 1},
 *       {@code YEAR(NULL) * n}, or a CASE's missing ELSE.</li>
 * </ul>
 *
 * <p>It subclasses the printer for its TRAVERSAL only; the printed string is discarded.
 */
final class ConstantArgumentWalk extends AstPrinterVisitor {

    /** The functions a call of which draws a new value whatever its arguments. */
    private static final Set<String> VOLATILE = Set.of("RANDOM", "SEQ1", "SEQ2", "SEQ4", "SEQ8");

    /** The functions that read the SESSION rather than their arguments, so no plan can fold them. */
    private static final Set<String> SESSION_STATE = Set.of("GETVARIABLE");

    /** The calls that fold with no arguments at all, where every other argument-less call does not. */
    private static final Set<String> CONSTRUCTORS = Set.of(
        "ARRAY_CONSTRUCT", "ARRAY_CONSTRUCT_COMPACT", "OBJECT_CONSTRUCT", "OBJECT_CONSTRUCT_KEEP_NULL");

    private final Expression argument;
    private final FunctionRegistry functions;
    /** The NULL literals the plan keeps unconverted: a condition's own, or one a constructor holds. */
    private final Map<Expression, Boolean> unconverted = new IdentityHashMap<Expression, Boolean>();
    private boolean constant = true;

    private ConstantArgumentWalk(final Expression argument, final FunctionRegistry functions) {
        this.argument = argument;
        this.functions = functions;
    }

    /**
     * Whether an expression is a constant argument.
     *
     * @param expression the argument
     * @param functions  the registry that tells an aggregate call from a scalar one, or null
     * @return true when the plan can fold it before the statement runs
     */
    static boolean isConstant(final Expression expression, final FunctionRegistry functions) {
        final ConstantArgumentWalk walk = new ConstantArgumentWalk(expression, functions);
        expression.accept(walk);
        return walk.constant;
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        if (expr.getType() == LiteralType.NULL && expr != argument && !unconverted.containsKey(expr)) {
            constant = false;
        }
        return "";
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        constant = false;
        return "";
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        constant = false;
        return "";
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        constant = false;
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = expr.getFunctionName() == null ? null : expr.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = expr.getArguments();
        if (name == null || expr.getNameExpression() != null || expr.isStar()
                || args.isEmpty() && !CONSTRUCTORS.contains(name)
                || VOLATILE.contains(name) || SESSION_STATE.contains(name)
                || functions != null && functions.hasAggregateFunction(name)) {
            constant = false;
            return "";
        }
        if (UntypedNullFold.foldsToUntypedNull(expr) && !("NULLIF".equals(name) && !allNull(args))) {
            // A call that folds to an untyped NULL is one: converted by what holds it, it is no constant.
            if (expr != argument && !unconverted.containsKey(expr)) {
                constant = false;
                return "";
            }
            if (!UntypedNullFold.picksABranch(name)) {
                return "";
            }
            // A conditional's NULL branches meet in no type; what it tests must still be constant.
            for (final Expression branch : "NULLIF".equals(name) ? args : TypeInferencer.conditionalBranches(name, args)) {
                markUnconverted(branch);
            }
        }
        if ("IFF".equals(name) || "NVL2".equals(name)) {
            // IFF's condition and NVL2's first argument are tested, not converted: NVL2(NULL, 1, 2) is 2.
            markUnconverted(args.get(0));
        }
        if (CONSTRUCTORS.contains(name) || "HASH".equals(name)) {
            for (final Expression element : args) {
                markUnconverted(element);
            }
        }
        // A date/time unit slot holds a word, never a reference: DATEDIFF(day, '2020-01-01', '2020-01-05').
        final int slot = DateTimeUnitSlot.positionIn(name);
        for (int i = 0; i < args.size(); i++) {
            if (i != slot) {
                args.get(i).accept(this);
            }
        }
        return "";
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        if (expr.getElseExpression() == null) {
            constant = false;
            return "";
        }
        if (UntypedNullFold.foldsToUntypedNull(expr)) {
            // Results that are all NULL meet in no type; converted by what holds it, the CASE is no constant.
            if (expr != argument && !unconverted.containsKey(expr)) {
                constant = false;
                return "";
            }
            markUnconverted(expr.getElseExpression());
            for (final WhenClause when : expr.getWhenClauses()) {
                markUnconverted(when.getResult());
            }
        }
        for (final WhenClause when : expr.getWhenClauses()) {
            markUnconverted(when.getCondition());
        }
        return super.visitCaseExpression(expr);
    }

    /** NULL OR NULL keeps its NULLs, where NULL AND TRUE converts its NULL to a BOOLEAN. */
    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        if ((expr.getOperator() == BinaryOperator.AND || expr.getOperator() == BinaryOperator.OR)
                && isNull(expr.getLeft()) && isNull(expr.getRight())) {
            markUnconverted(expr.getLeft());
            markUnconverted(expr.getRight());
        }
        return super.visitBinaryOperation(expr);
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        if (expr.getOperator() == UnaryOperator.NOT) {
            markUnconverted(expr.getOperand());
        }
        return super.visitUnaryOperation(expr);
    }

    @Override
    public String visitJsonArray(final JsonArrayExpression expr) {
        for (final Expression element : expr.getElements()) {
            markUnconverted(element);
        }
        return super.visitJsonArray(expr);
    }

    @Override
    public String visitJsonObject(final JsonObjectExpression expr) {
        for (final Expression value : expr.getProperties().values()) {
            markUnconverted(value);
        }
        return super.visitJsonObject(expr);
    }

    private static boolean allNull(final List<Expression> expressions) {
        for (final Expression expression : expressions) {
            if (!isNull(expression)) {
                return false;
            }
        }
        return !expressions.isEmpty();
    }

    private static boolean isNull(final Expression expression) {
        return expression instanceof LiteralExpression && ((LiteralExpression) expression).getType() == LiteralType.NULL;
    }

    private void markUnconverted(final Expression expression) {
        if (UntypedNullFold.isUntypedNull(expression)) {
            unconverted.put(expression, Boolean.TRUE);
        }
    }
}
