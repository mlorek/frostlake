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

import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A thread-local mode in which a subquery is COMPILED rather than run: it is planned for its shape alone
 * (see {@link RelationShapeOnly}) with the names of the query around it bound, and every plan-time rule a
 * statement answers to judges it, so a name it cannot resolve is refused before any row exists. Live
 * compiles a subquery with its statement: over an empty table, {@code SELECT (SELECT nosuch FROM u) FROM t}
 * and {@code UPDATE t SET a = (SELECT nosuch FROM u)} are both {@code invalid identifier 'NOSUCH'}.
 *
 * <p>The mode belongs to one run: the one whose lateral context is the map of outer names it was entered
 * with, and whatever that run executes without a lateral context of its own (a derived table, a CTE, a
 * view). A lateral run inside it with any other context, such as a nested subquery evaluated for a row,
 * runs as it runs anywhere else.
 */
public final class SubqueryCompilation {
    private static final ThreadLocal<Map<String, Object>> OUTER_NAMES = new ThreadLocal<Map<String, Object>>();
    private static final ThreadLocal<ExpressionEvaluatorVisitor> OUTER_SCOPE = new ThreadLocal<ExpressionEvaluatorVisitor>();
    /**
     * Every scope named with {@link #beginScope} and not yet ended, innermost last; a null entry marks a
     * boundary no correlation reaches across. A name two levels out is typed by walking it.
     */
    private static final ThreadLocal<List<ExpressionEvaluatorVisitor>> SCOPE_CHAIN =
        new ThreadLocal<List<ExpressionEvaluatorVisitor>>() {
            @Override
            protected List<ExpressionEvaluatorVisitor> initialValue() {
                return new ArrayList<ExpressionEvaluatorVisitor>();
            }
        };

    private SubqueryCompilation() {
    }

    /**
     * Enter the mode for a subquery whose enclosing query binds {@code outerNames}.
     *
     * @param outerNames the enclosing query's names, each bound to NULL; null leaves the mode
     * @return the names it displaced, to put back with {@link #end}
     */
    public static Map<String, Object> begin(final Map<String, Object> outerNames) {
        final Map<String, Object> previous = OUTER_NAMES.get();
        if (outerNames == null) {
            OUTER_NAMES.remove();
        } else {
            OUTER_NAMES.set(outerNames);
        }
        return previous;
    }

    public static void end(final Map<String, Object> previous) {
        begin(previous);
    }

    /** The names the subquery being compiled may read from the query around it, or null outside the mode. */
    public static Map<String, Object> outerNames() {
        return OUTER_NAMES.get();
    }

    /**
     * Name the scope of the query around the subquery being compiled or run, which a refusal's echo reads a
     * correlation through: {@code CORRELATION(RT.N)}.
     *
     * @param outerScope the enclosing query's scope; null leaves it unnamed
     * @return the scope it displaced, to put back with {@link #endScope}
     */
    public static ExpressionEvaluatorVisitor beginScope(final ExpressionEvaluatorVisitor outerScope) {
        final ExpressionEvaluatorVisitor previous = OUTER_SCOPE.get();
        if (outerScope == null) {
            OUTER_SCOPE.remove();
        } else {
            OUTER_SCOPE.set(outerScope);
        }
        SCOPE_CHAIN.get().add(outerScope);
        return previous;
    }

    public static void endScope(final ExpressionEvaluatorVisitor previous) {
        final List<ExpressionEvaluatorVisitor> chain = SCOPE_CHAIN.get();
        if (!chain.isEmpty()) {
            chain.remove(chain.size() - 1);
        }
        if (previous == null) {
            OUTER_SCOPE.remove();
        } else {
            OUTER_SCOPE.set(previous);
        }
    }

    /**
     * The scopes around {@code scope}, innermost first: every scope named below its own place in the chain,
     * or the whole chain when it is not one of them, up to the nearest boundary. So a subquery two levels
     * down types a name of the outermost query: {@code (SELECT (SELECT fz.id + 1))} reads fz through both.
     *
     * @param scope the scope asking
     * @return the scopes that may type its correlations
     */
    public static List<ExpressionEvaluatorVisitor> enclosingScopes(final ExpressionEvaluatorVisitor scope) {
        final List<ExpressionEvaluatorVisitor> chain = SCOPE_CHAIN.get();
        int start = chain.size() - 1;
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (chain.get(i) == scope) {
                start = i - 1;
                break;
            }
        }
        final List<ExpressionEvaluatorVisitor> enclosing = new ArrayList<>();
        for (int i = start; i >= 0; i--) {
            final ExpressionEvaluatorVisitor around = chain.get(i);
            if (around == null) {
                break;
            }
            if (around != scope) {
                enclosing.add(around);
            }
        }
        return enclosing;
    }

    /** The scope of the query around the subquery being compiled or run, or null outside one. */
    public static ExpressionEvaluatorVisitor outerScope() {
        return OUTER_SCOPE.get();
    }

    /** Whether a query run with {@code lateralContext} is the subquery being compiled. */
    public static boolean compiles(final Map<String, Object> lateralContext) {
        return lateralContext != null && lateralContext == OUTER_NAMES.get();
    }
}
