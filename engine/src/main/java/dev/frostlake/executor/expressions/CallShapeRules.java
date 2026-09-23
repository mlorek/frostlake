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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StarArgument;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.window.WindowFunctionNames;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a call's shape is refused for while its clause's names are walked, at the call's written place — after the
 * names inside the call and ahead of every name written after it, of every unknown function name, and of every
 * argument count and type (all live-verified, in the select list, WHERE, GROUP BY, HAVING, QUALIFY and ORDER BY):
 *
 * <pre>
 *   UPPER(DISTINCT 'a')                          invalid use of 'distinct' for function 'UPPER(DISTINCT 'a')'
 *   UPPER(ALL g)                                 invalid use of 'all' for function 'UPPER(ALL RT.G)'
 *   NTILE(ALL 2) OVER (ORDER BY n)               invalid use of 'all' for function 'NTILE(ALL 2)'
 *   PUBLIC.f(ALL 1)                              invalid use of 'all' for function 'F(ALL 1)'
 *   UPPER(DISTINCT), NOSUCHFN(ALL)               invalid function 'UPPER', invalid function 'NOSUCHFN'
 *   MEDIAN(a) WITHIN GROUP (ORDER BY a)          Function MEDIAN does not support WITHIN GROUP clause.
 *   ABS(1) OVER ()                               Invalid function type [ABS] for window function.
 *   ROW_NUMBER() OVER ()                         Window function type [ROW_NUMBER] requires ORDER BY in window
 *                                                specification.
 * </pre>
 *
 * <p>A built-in is named in any case, quoted or not: {@code "ntile"(ALL 2)} is NTILE. A scalar and a window-only
 * function take no quantifier; an aggregate takes ALL as the no-op it is, and DISTINCT is judged with its
 * arguments' types (see {@code AggregateDistinctRefusals}); FIRST_VALUE and LAST_VALUE take both, and so does
 * RATIO_TO_REPORT under OVER, where it is planned as SUM. CONDITIONAL_CHANGE_EVENT under OVER is planned as LAG and
 * echoed by that name. A user's function named with its schema takes no quantifier and no WITHIN GROUP either,
 * while an unqualified name written with either resolves to no user's function at all and is left to the
 * unknown-function refusal, which comes later. Only LISTAGG, ARRAY_AGG and the two ordered percentiles take a
 * WITHIN GROUP.
 *
 * <p>A window call is judged, in this order, for its quantifier, its WITHIN GROUP, its kind — a name that is
 * neither a window function nor an aggregate — and the ORDER BY its window function needs, each once the names
 * written ahead of that part of the call are walked: {@code UPPER(DISTINCT 1) OVER ()} is the DISTINCT sentence,
 * {@code RANK() WITHIN GROUP (ORDER BY n) OVER ()} the WITHIN GROUP one, and {@code ROW_NUMBER() OVER (PARTITION
 * BY nosuch)} the invalid identifier.
 */
final class CallShapeRules {

    private static final Set<String> WITHIN_GROUP_FUNCTIONS =
        Set.of("LISTAGG", "ARRAY_AGG", "ARRAYAGG", "PERCENTILE_CONT", "PERCENTILE_DISC");

    /** The window-only functions that take a quantifier as the aggregates do: the value functions. */
    private static final Set<String> QUANTIFIED_VALUE_FUNCTIONS = Set.of("FIRST_VALUE", "LAST_VALUE");

    private static final String RATIO_TO_REPORT = "RATIO_TO_REPORT";

    private static final String CONDITIONAL_CHANGE_EVENT = "CONDITIONAL_CHANGE_EVENT";

    private final ExpressionEvaluatorVisitor context;

    CallShapeRules(final ExpressionEvaluatorVisitor context) {
        this.context = context;
    }

    /**
     * Refuse a plain call's quantifier or WITHIN GROUP, once its arguments' names are walked.
     *
     * @param call the call
     * @param walk the name walk, which the WITHIN GROUP keys are walked with before the refusal
     */
    void rejectCall(final FunctionCallExpression call, final ExpressionVisitor<String> walk) {
        if (call.getNameExpression() != null) {
            return;
        }
        final List<String> parts = call.getNameParts() != null ? call.getNameParts()
            : call.getFunctionName() == null ? null : Collections.singletonList(call.getFunctionName());
        if (parts == null || parts.isEmpty()) {
            return;
        }
        final String builtin = builtinName(parts);
        if (bareStar(call) && (call.getQuantifier() != null || call.isDistinct() || call.getWithinGroupKeys() != null)
                && builtin != null) {
            throw new RuntimeException("Unsupported feature 'TOK_STAR'.");
        }
        final String quantifier = call.getQuantifier();
        if (quantifier != null && call.getArguments().isEmpty() && !call.isStar()) {
            throw invalidFunction(builtin, parts);
        }
        final String userFunction = builtin == null ? userFunctionName(parts) : null;
        if (quantifier != null && (takesNoQuantifier(builtin, false) || userFunction != null)) {
            throw quantifierRefusal(builtin != null ? builtin : userFunction, quantifier, call.getArguments());
        }
        if (call.getWithinGroupKeys() != null
                && (builtin != null && !WITHIN_GROUP_FUNCTIONS.contains(builtin) || userFunction != null)) {
            walkAll(call.getWithinGroupKeys(), walk);
            throw withinGroupRefusal(builtin != null ? builtin : userFunction);
        }
    }

    /**
     * Refuse a window call's quantifier, its WITHIN GROUP, its kind or its missing ORDER BY, once the names written
     * ahead of each — its arguments, its WITHIN GROUP keys and its window's keys — are walked. A call nothing
     * refuses is left alone.
     *
     * @param window the window call
     * @param walk   the name walk
     */
    void rejectWindowCall(final WindowFunctionExpression window, final ExpressionVisitor<String> walk) {
        final List<String> parts = window.getNameParts();
        if (parts == null || parts.isEmpty()) {
            return;
        }
        final String builtin = builtinName(parts);
        final String quantifier = window.isDistinct() ? "DISTINCT" : window.isAll() ? "ALL" : null;
        final List<Expression> arguments = window.getArguments();
        if (quantifier != null && window.getStarCall() == null && (arguments == null || arguments.isEmpty())) {
            throw invalidFunction(builtin, parts);
        }
        final String userFunction = builtin == null ? userFunctionName(parts) : null;
        final boolean refusedQuantifier = quantifier != null
            && (takesNoQuantifier(builtin, true) || userFunction != null);
        final boolean refusedWithinGroup = window.getWithinGroupKeys() != null && builtin != null
            && !WITHIN_GROUP_FUNCTIONS.contains(builtin)
            && (functionRegistry().hasAggregateFunction(builtin) || WindowFunctionNames.handles(builtin));
        final String kind = parts.size() == 1 && !windowCapable(builtin)
            ? (builtin != null ? builtin : SqlIdentifiers.spellCanonical(parts.get(0))) : null;
        final boolean orderRequired = builtin != null && !window.isOrdered()
            && WindowFunctionNames.requiresOrderBy(builtin);
        if (!refusedQuantifier && !refusedWithinGroup && kind == null && !orderRequired) {
            return;
        }
        walkArguments(builtin, arguments, walk);
        if (refusedQuantifier) {
            // Under OVER the account plans CONDITIONAL_CHANGE_EVENT as LAG, and echoes the call by that name.
            final String echoed = builtin == null ? userFunction
                : CONDITIONAL_CHANGE_EVENT.equals(builtin) ? "LAG" : builtin;
            throw quantifierRefusal(echoed, quantifier, arguments);
        }
        walkAll(window.getWithinGroupKeys(), walk);
        if (refusedWithinGroup) {
            throw withinGroupRefusal(builtin);
        }
        walkAll(window.getPartitionKeys(), walk);
        if (kind != null) {
            walkAll(window.getOrderKeys(), walk);
            throw new RuntimeException(SqlCompilationError.of("Invalid function type [" + kind + "] for window function."));
        }
        throw new RuntimeException(SqlCompilationError.of("Window function type [" + builtin
            + "] requires ORDER BY in window specification."));
    }

    /** The call's arguments walked as a call's are, a date/time unit slot passed over as a name. */
    private void walkArguments(final String name, final List<Expression> arguments,
                               final ExpressionVisitor<String> walk) {
        if (arguments == null) {
            return;
        }
        final int slot = name == null ? -1 : DateTimeUnitSlot.positionIn(name);
        final boolean enclosing = context.beginFunctionArgumentScope();
        try {
            for (int i = 0; i < arguments.size(); i++) {
                if (i != slot) {
                    arguments.get(i).accept(walk);
                }
            }
        } finally {
            context.endFunctionArgumentScope(enclosing);
        }
    }

    private static void walkAll(final List<Expression> keys, final ExpressionVisitor<String> walk) {
        if (keys == null) {
            return;
        }
        for (final Expression key : keys) {
            key.accept(walk);
        }
    }

    /** The refusal of a quantifier written with no argument after it: the call's name alone. */
    private static RuntimeException invalidFunction(final String builtin, final List<String> parts) {
        final String name = builtin != null ? builtin : SqlIdentifiers.spellCanonical(parts.get(parts.size() - 1));
        return new RuntimeException(SqlCompilationError.of("invalid function '" + name + "'"));
    }

    /** The quantifier's refusal: the call echoed from the plan with the quantifier as written. */
    private RuntimeException quantifierRefusal(final String name, final String quantifier,
                                               final List<Expression> arguments) {
        final StringBuilder echo = new StringBuilder(name).append('(').append(quantifier).append(' ');
        final int unitSlot = DateTimeUnitSlot.positionIn(name);
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                echo.append(", ");
            }
            final String unit = i == unitSlot ? DateTimeUnitSlot.unitTextOf(arguments.get(i)) : null;
            // The plan holds a date/time unit as the text it names.
            echo.append(unit != null ? "'" + unit.toUpperCase(Locale.ROOT) + "'"
                : context.strictPlanText(arguments.get(i)));
        }
        echo.append(')');
        return new RuntimeException(SqlCompilationError.of("invalid use of '"
            + quantifier.toLowerCase(Locale.ROOT) + "' for function '" + echo + "'"));
    }

    private static RuntimeException withinGroupRefusal(final String name) {
        return new RuntimeException(SqlCompilationError.of("Function " + name + " does not support WITHIN GROUP clause."));
    }

    /**
     * Whether the built-in takes no quantifier: a scalar, or a window-only function other than the value functions
     * — and other than RATIO_TO_REPORT under OVER, which is planned as SUM.
     */
    private boolean takesNoQuantifier(final String builtin, final boolean windowed) {
        if (builtin == null || functionRegistry().hasAggregateFunction(builtin)) {
            return false;
        }
        if (WindowFunctionNames.handles(builtin)) {
            return !QUANTIFIED_VALUE_FUNCTIONS.contains(builtin) && !(windowed && RATIO_TO_REPORT.equals(builtin));
        }
        return functionRegistry().getFunction(builtin) != null;
    }

    /** Whether a built-in may stand before OVER: a window function or an aggregate. */
    private boolean windowCapable(final String builtin) {
        return builtin != null
            && (WindowFunctionNames.handles(builtin) || functionRegistry().hasAggregateFunction(builtin));
    }

    /** Whether the call's one argument is a bare star, {@code COUNT(*)}, however the call spells it. */
    private static boolean bareStar(final FunctionCallExpression call) {
        if (call.isStar()) {
            return StarArgument.of(call).isBare();
        }
        final List<Expression> arguments = call.getArguments();
        return arguments != null && arguments.size() == 1 && arguments.get(0) instanceof ColumnReferenceExpression
            && ((ColumnReferenceExpression) arguments.get(0)).getStarArgument() != null
            && ((ColumnReferenceExpression) arguments.get(0)).getStarArgument().isBare();
    }

    /**
     * The built-in a one-part name calls — upper-cased, since the account finds a built-in by any spelling, quoted
     * or not — or null for a qualified name or one no scalar, aggregate or window function answers to.
     */
    private String builtinName(final List<String> parts) {
        if (parts.size() != 1 || parts.get(0) == null) {
            return null;
        }
        final String name = parts.get(0).toUpperCase(Locale.ROOT);
        return functionRegistry().getFunction(name) != null || functionRegistry().hasAggregateFunction(name)
            || WindowFunctionNames.handles(name) ? name : null;
    }

    /** The user's function a name qualified by its schema calls, spelled as a refusal names it, or null. */
    private String userFunctionName(final List<String> parts) {
        return parts.size() > 1 && context.namesUserFunction(parts)
            ? SqlIdentifiers.spellCanonical(parts.get(parts.size() - 1)) : null;
    }

    private FunctionRegistry functionRegistry() {
        return context.getFunctionRegistry();
    }
}
