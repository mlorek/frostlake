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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.storage.Row;

/**
 * Interface for evaluating a parsed expression AST on a row. Operators parse their expression text
 * once and pass the {@link Expression} here per row (no per-row re-parse).
 */
public interface RowExpressionEvaluator {
    /**
     * Evaluate a parsed expression AST on a row.
     *
     * @param expression The parsed expression to evaluate
     * @param row The row to evaluate against
     * @return The result of the evaluation
     */
    Object evaluate(final Expression expression, final Row row);
}
