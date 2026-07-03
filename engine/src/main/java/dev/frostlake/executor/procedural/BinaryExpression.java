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

import dev.frostlake.executor.expressions.BinaryOperator;

public class BinaryExpression extends BaseExpression {
    private final BaseExpression left;
    private final BinaryOperator operator;
    private final BaseExpression right;

    public BinaryExpression(final BaseExpression left, final BinaryOperator operator, final BaseExpression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public BaseExpression getLeft() {
        return left;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public BaseExpression getRight() {
        return right;
    }
}
