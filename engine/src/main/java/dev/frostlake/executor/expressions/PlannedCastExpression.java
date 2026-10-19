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

/**
 * A conversion the plan writes where it folds a conditional to one of its values, kept in an echo whatever the value's
 * own width: {@code NVL(NULL, 'x')} is {@code CAST('x' AS VARCHAR(134217728))} and {@code ZEROIFNULL(1)}
 * {@code CAST(1 AS NUMBER(2,0))} (live-verified through GETVARIABLE's constant-name sentence). Only the folded form
 * of a constant argument holds one ({@link ConstantRootFold}).
 */
final class PlannedCastExpression extends CastExpression {

    /**
     * @param value      the value converted
     * @param targetType the conditional's type, as the plan spells it
     */
    PlannedCastExpression(final Expression value, final String targetType) {
        super(value, targetType);
    }
}
