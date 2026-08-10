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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.types.DataType;

/**
 * Static return-type inference for scalar SQL routine bodies: the type the body expression is KNOWN
 * to produce over the routine's declared parameters, or null when undetermined. Parameter references
 * resolve against a synthetic single-table context built from the declared parameter list, so a body
 * like {@code x + 1} types from {@code x}'s declared type exactly as a column reference would; a
 * body the expression grammar cannot read stays undetermined, never refused.
 */
public final class RoutineReturnTypeOracle {

    private RoutineReturnTypeOracle() {
    }

    /** The statically-known type of {@code expressionText}, or null (undetermined never guesses). */
    public static DataType bodyType(final String expressionText, final Table parameters,
                                    final FunctionRegistry functionRegistry, final Catalog catalog) {
        final Expression ast;
        try {
            ast = AntlrExpressionParser.parse(expressionText);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
        if (ast == null) {
            return null;
        }
        final ExpressionEvaluatorVisitor visitor =
            new ExpressionEvaluatorVisitor(parameters, null, functionRegistry, catalog);
        return new TypeInferencer(visitor).infer(ast);
    }
}
