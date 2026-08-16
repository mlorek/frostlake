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
 * A window function call (one carrying an {@code OVER} clause) that appears nested inside a larger
 * expression — e.g. {@code revenue - LAG(revenue) OVER (ORDER BY yr)} in a SELECT item, or an inline
 * {@code ROW_NUMBER() OVER (...)} in a QUALIFY predicate.
 *
 * <p>Window functions are not evaluated per row by {@link ExpressionEvaluatorVisitor}; they are computed
 * over a whole partition by the window stage. This node lets a window call keep its place in the
 * expression AST (instead of being string-substituted out and re-parsed): the window stage precomputes
 * each call's per-row value and supplies it through the evaluator's result context, keyed by the call's
 * exact source text ({@link #getCallText()}). The evaluator resolves this node to that value.
 *
 * <p>Carrying the call as a node — rather than swapping its text for a synthetic name and re-parsing —
 * keeps the AST the single source of truth about the expression's structure.
 */
public class WindowFunctionExpression implements Expression {

    private final String callText;
    private String functionName;
    private List<Expression> arguments;

    public WindowFunctionExpression(final String callText) {
        this.callText = callText;
    }

    /** The window call's exact source text; the key under which its per-row value is supplied. */
    public String getCallText() {
        return callText;
    }

    /**
     * Record what the call IS, read off the parse tree when this node was built. Evaluation never uses
     * it — the value still arrives by call text — but the static channel needs the name and arguments
     * to type the window functions that hand their argument back, and re-parsing the text to recover
     * them would make the text the source of truth again.
     *
     * @param name      the function's name, upper-cased
     * @param callArgs  its argument expressions
     */
    public void describeCall(final String name, final List<Expression> callArgs) {
        this.functionName = name;
        this.arguments = callArgs;
    }

    /** The window function's name, or null when this node was built without one. */
    public String getFunctionName() {
        return functionName;
    }

    /** The window call's argument expressions, or null when this node was built without them. */
    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitWindowFunction(this);
    }
}
