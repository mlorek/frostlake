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

import java.util.ArrayList;
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
    // IDENTIFIER('fn')/IDENTIFIER($var) as the function name: resolved per evaluation (null otherwise).
    private final Expression nameExpression;
    /** Where the call starts in its fragment, when the builder recorded it — see {@link SourcePosition}. */
    private SourcePosition position;
    // Uppercase column names to omit when a `*` argument is expanded — e.g. OBJECT_CONSTRUCT(* EXCLUDE src).
    private List<String> starExcludes = new ArrayList<>();
    // Canonical per-identifier parts of the call's (possibly qualified) name, read from the parse
    // tree at build: quoted parts keep their case with the quotes stripped, unquoted parts fold.
    // Null when the name has no identifier parts (LIKE/ILIKE keyword calls, IDENTIFIER(...) dynamic
    // names) — the flattened functionName is the only spelling then.
    private List<String> nameParts;

    public FunctionCallExpression(final String functionName, final List<Expression> arguments) {
        this(functionName, arguments, false, false);
    }

    public FunctionCallExpression(final String functionName, final List<Expression> arguments, final boolean distinct, final boolean star) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.distinct = distinct;
        this.star = star;
        this.argumentNames = null;
        this.nameExpression = null;
    }

    public FunctionCallExpression(final String functionName, final List<Expression> arguments,
            final List<String> argumentNames) {
        this.functionName = functionName;
        this.arguments = arguments;
        this.distinct = false;
        this.star = false;
        this.argumentNames = argumentNames;
        this.nameExpression = null;
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

    /** Uppercase column names to omit when expanding a {@code *} argument (OBJECT_CONSTRUCT(* EXCLUDE …)). */
    public List<String> getStarExcludes() {
        return starExcludes;
    }

    public void setStarExcludes(final List<String> starExcludes) {
        this.starExcludes = starExcludes;
    }

    /** Canonical name parts ({@code db, schema, fn}), or null when the name has none — see the field note. */
    public List<String> getNameParts() {
        return nameParts;
    }

    public void setNameParts(final List<String> nameParts) {
        this.nameParts = nameParts;
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
        final StringBuilder sb = new StringBuilder();
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

    /** A dynamically named call — IDENTIFIER('fn')(args): {@code displayName} keeps the source text
     *  (for printing/HAVING matching); {@code nameExpression} yields the actual function name. */
    public FunctionCallExpression(final String displayName, final Expression nameExpression,
                                  final List<Expression> arguments) {
        this.functionName = displayName;
        this.arguments = arguments;
        this.distinct = false;
        this.star = false;
        this.argumentNames = null;
        this.nameExpression = nameExpression;
    }

    public Expression getNameExpression() {
        return nameExpression;
    }

    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

}
