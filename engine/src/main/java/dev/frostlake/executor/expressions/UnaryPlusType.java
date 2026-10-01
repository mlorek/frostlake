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

import dev.frostlake.types.NumericType;

/**
 * The type a unary plus declares over an exact number. It is no pass-through: the operand gains integer
 * digits up to two, its scale kept and the precision capped at thirty-eight (live-verified over literals,
 * columns and computed operands alike):
 *
 * <pre>
 *   +1        NUMBER(2,0)      +99       NUMBER(2,0)      +100      NUMBER(3,0)
 *   +1.5      NUMBER(3,1)      +.05      NUMBER(4,2)      +123.45   NUMBER(5,2)
 *   +a        NUMBER(2,0)      over a NUMBER(1,0) column, and NUMBER(3,2) becomes NUMBER(4,2)
 *   +g        NUMBER(38,37)    over a NUMBER(37,37) column; NUMBER(38,37) stays NUMBER(38,37)
 *   +(+1)     NUMBER(2,0)      nesting adds nothing once two integer digits are there
 *   +NULL     NUMBER(2,0)
 * </pre>
 *
 * <p>A FLOAT stays a FLOAT, and a text or a VARIANT operand converts to one, as a negation does.
 */
final class UnaryPlusType {

    /** A unary plus over the bare word NULL. */
    static final NumericType OVER_NULL = new NumericType("NUMBER", 2, 0);

    /** The integer digits a unary plus leaves at least. */
    private static final int LEAST_INTEGER_DIGITS = 2;

    /** The widest precision an exact number declares. */
    private static final int MAX_PRECISION = 38;

    private UnaryPlusType() {
    }

    /**
     * The declared type of a unary plus over an exact number.
     *
     * @param operand the operand's exact NUMBER type
     * @return the widened type
     */
    static NumericType of(final NumericType operand) {
        final int scale = operand.getScale();
        final int integerDigits = Math.max(operand.getPrecision() - scale, LEAST_INTEGER_DIGITS);
        return new NumericType("NUMBER", Math.min(MAX_PRECISION, scale + integerDigits), scale);
    }
}
