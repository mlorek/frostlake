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

package dev.frostlake.executor.procedural;

import dev.frostlake.executor.expressions.Expression;

/**
 * A procedural expression backed by the full query-side expression AST ({@link Expression}). Used
 * for scalar constructs the lightweight procedural builder doesn't model directly — CASE, BETWEEN,
 * IN, LIKE, CAST, IS NULL, semi-structured access, etc. ProceduralExecutor evaluates the wrapped AST
 * through the shared ExpressionEvaluator (with the current procedural variables supplied as a
 * resolution context), so these constructs are evaluated by the real AST instead of degrading to
 * their source text.
 *
 * <p>This is what makes procedural expression evaluation fully AST-based: the builder covers the
 * common operators with dedicated nodes (so e.g. function calls keep their UDF-resolving path), and
 * everything else is wrapped here rather than falling back to a text literal.
 */
public class SqlScalarExpression extends BaseExpression {

    private final Expression expression;

    public SqlScalarExpression(final Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }
}
