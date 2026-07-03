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

import java.util.List;

/**
 * Represents a function call (e.g., UPPER(name), COUNT(*), SUM(salary))
 */
public class FunctionCallExpression implements Expression {
    private final String functionName;
    private final List<Expression> arguments;
    private final boolean distinct;
    private final boolean star;  // true for COUNT(*)
    // Parallel to arguments: the caller-supplied name for each argument (f(x => 1)), or null for a
    // positional argument. The whole list is null when the call uses only positional arguments.
    private final List<String> argumentNames;

    public FunctionCallExpression(final String functionName, final List<Expression> arguments) {
        this(functionName, arguments, false, false);
    }

    public FunctionCallExpression(final String functionName, final List<Expression> arguments, final boolean distinct, final boolean star) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.distinct = distinct;
        this.star = star;
        this.argumentNames = null;
    }

    public FunctionCallExpression(final String functionName, final List<Expression> arguments,
            final List<String> argumentNames) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.distinct = false;
        this.star = false;
        this.argumentNames = argumentNames;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    /** The per-argument names (parallel to {@link #getArguments()}; null entry = positional), or null if none are named. */
    public List<String> getArgumentNames() {
        return argumentNames;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public boolean isStar() {
        return star;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitFunctionCall(this);
    }

    @Override
    public String toString() {
        if (star) {
            return functionName + "(*)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(functionName).append("(");
        if (distinct) {
            sb.append("DISTINCT ");
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            if (argumentNames != null && argumentNames.get(i) != null) {
                sb.append(argumentNames.get(i)).append(" => ");
            }
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
