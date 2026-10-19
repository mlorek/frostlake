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
import java.util.Collections;

/**
 * A constant the plan keeps nullable where it folds a conditional that may also answer NULL down to that constant:
 * {@code NVL('x', NULL)}, {@code DECODE(1, 1, 'a')} and {@code CASE 1 WHEN 1 THEN 'a' END} are
 * {@code ENSURE_NULLABLE('x')} and {@code ENSURE_NULLABLE('a')} (live-verified through GETVARIABLE's constant-name
 * sentence). It is typed as the constant it holds and printed wrapped so; only the folded form of a constant
 * argument holds one ({@link ConstantRootFold}).
 */
final class NullableConstantExpression extends FunctionCallExpression {

    /**
     * @param value the constant
     */
    NullableConstantExpression(final Expression value) {
        super("ENSURE_NULLABLE", new ArrayList<Expression>(Collections.singletonList(value)));
    }

    /** @return the constant it keeps nullable */
    Expression getValue() {
        return getArguments().get(0);
    }
}
