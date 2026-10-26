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
import dev.frostlake.functions.FunctionRegistry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The walk {@link VirtualColumnUsage} takes: the printer's traversal, noting the first part of an expression that a
 * virtual column's definition cannot hold, in the words its refusal gives it.
 */
final class VirtualColumnUsageWalk extends AstPrinterVisitor {

    /**
     * The functions whose answer moves between two calls, each under the name the refusal gives it: a call written
     * through SYSDATE, GETDATE or SYSTIMESTAMP is planned as CURRENT_TIMESTAMP.
     */
    private static final Map<String, String> MOVING = new HashMap<>();

    static {
        for (final String name : new String[] {"CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "LOCALTIMESTAMP", "LOCALTIME", "RANDOM", "LAST_QUERY_ID", "SEQ1", "SEQ2", "SEQ4", "SEQ8"}) {
            MOVING.put(name, name);
        }
        MOVING.put("SYSDATE", "CURRENT_TIMESTAMP");
        MOVING.put("GETDATE", "CURRENT_TIMESTAMP");
        MOVING.put("SYSTIMESTAMP", "CURRENT_TIMESTAMP");
    }

    private final FunctionRegistry functions;
    private final ExpressionEvaluator scope;
    private final Map<String, String> spellings;
    private String refused;

    /**
     * @param functions the built-in functions
     * @param scope     the evaluator over the columns the definition reads, which resolves a user's function
     * @param spellings each function name the expression writes, by its canonical name, as written
     */
    VirtualColumnUsageWalk(final FunctionRegistry functions, final ExpressionEvaluator scope,
                           final Map<String, String> spellings) {
        this.functions = functions;
        this.scope = scope;
        this.spellings = spellings;
    }

    /** A function's name as the expression wrote it, which the refusal gives: {@code f1} for a user's F1. */
    private String spelled(final String name) {
        final String canonical = name.toUpperCase(Locale.ROOT);
        return spellings.containsKey(canonical) ? spellings.get(canonical) : name;
    }

    /** What the refusal names, or null when the expression holds nothing a definition cannot. */
    String refused() {
        return refused;
    }

    private void note(final String usage) {
        if (refused == null) {
            refused = usage;
        }
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        note("subquery");
        return "";
    }

    @Override
    public String visitBindVariable(final BindVariableExpression expr) {
        note("bind variable");
        return super.visitBindVariable(expr);
    }

    @Override
    public String visitSessionVar(final SessionVarExpression expr) {
        note("session variable");
        return super.visitSessionVar(expr);
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (expr.getPositionalOrdinal() > 0) {
            note("column positional reference");
        }
        return super.visitColumnReference(expr);
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        note(spelled(expr.getFunctionName()) + " window function");
        return super.visitWindowFunction(expr);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String written = expr.getFunctionName() == null ? "" : expr.getFunctionName();
        final String name = written.toUpperCase(Locale.ROOT);
        final boolean builtIn = functions.hasFunction(name) || functions.hasBuiltInAggregateFunction(name)
            || functions.hasTableFunction(name);
        if (MOVING.containsKey(name) || "UUID_STRING".equals(name) && expr.getArguments().isEmpty()) {
            note("non-deterministic " + (MOVING.containsKey(name) && !MOVING.get(name).equals(name) ? MOVING.get(name)
                : spelled(written)) + " function");
        } else if (!builtIn && scope.resolvedUserFunction(expr) != null) {
            note(spelled(written) + " user defined function");
        } else if (functions.hasBuiltInAggregateFunction(name) && !functions.hasFunction(name)) {
            note(spelled(written) + " aggregate function");
        }
        return super.visitFunctionCall(expr);
    }
}
