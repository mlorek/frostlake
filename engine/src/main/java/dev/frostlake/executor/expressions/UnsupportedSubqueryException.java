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
import dev.frostlake.metastore.model.Function;

/**
 * Live's refusal of a correlated subquery whose shape its planner cannot evaluate ("Unsupported subquery
 * type cannot be evaluated at line L, position P"). A type of its own, so a fallback that retries a failed
 * evaluation another way — an ORDER BY key read as a column name — passes it through instead of reporting
 * a failure of its own. Inside the arguments of a SQL function the refusal names that function and gives no
 * place (see {@link SqlFunctionArgument}), and inside a view's body it names the view (see {@link #insideView}).
 */
public class UnsupportedSubqueryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean namesFunction;

    public UnsupportedSubqueryException(final String message) {
        super(message);
        this.namesFunction = false;
    }

    private UnsupportedSubqueryException(final String message, final boolean namesFunction) {
        super(message);
        this.namesFunction = namesFunction;
    }

    /**
     * The refusal of a subquery written inside a SQL function's arguments.
     *
     * @param called the function
     * @return the refusal, naming the function as it is stored
     */
    public static UnsupportedSubqueryException inside(final Function called) {
        return new UnsupportedSubqueryException(SqlCompilationError.of(
            "Unsupported subquery type cannot be evaluated inside Function object: " + called.getName()), true);
    }

    /**
     * The refusal of a subquery a view's body holds, as a statement reading the view is refused: it names the view
     * and gives no place. A view read through another view is named by the outer one, so the statement is told of
     * the view it reads itself (live-verified: a view over a view over the subquery names the outer view).
     *
     * @param viewName the view, as it is stored
     * @return the refusal, naming the view
     */
    public static UnsupportedSubqueryException insideView(final String viewName) {
        return new UnsupportedSubqueryException(SqlCompilationError.of(
            "Unsupported subquery type cannot be evaluated inside VIEW object: " + viewName));
    }

    /**
     * Whether this refusal names the function its subquery is an argument of.
     *
     * @return true for a refusal made by {@link #inside}
     */
    public boolean namesFunction() {
        return namesFunction;
    }
}
