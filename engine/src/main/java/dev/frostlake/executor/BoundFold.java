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

import org.antlr.v4.runtime.ParserRuleContext;

import java.math.BigDecimal;

/**
 * What an expression is known to be before any row is read: a constant - with its value where that was worked
 * out - or something only a row decides, with the part of it that reads the row.
 */
final class BoundFold {

    /** Stands for the value of a constant that was not worked out. */
    private static final Object UNVALUED = new Object();

    private final ParserRuleContext residual;
    private final Object value;

    private BoundFold(final ParserRuleContext residual, final Object value) {
        this.residual = residual;
        this.value = value;
    }

    /**
     * An expression a row decides.
     *
     * @param residual what of it reads the row, as written
     * @return the fold
     */
    static BoundFold rowDecided(final ParserRuleContext residual) {
        return new BoundFold(residual, UNVALUED);
    }

    /**
     * A constant of a known value.
     *
     * @param value the value, null for a NULL
     * @return the fold
     */
    static BoundFold valued(final Object value) {
        return new BoundFold(null, value);
    }

    /** A constant whose value was not worked out. */
    static BoundFold constant() {
        return new BoundFold(null, UNVALUED);
    }

    boolean isConstant() {
        return residual == null;
    }

    /** Whether the expression is NULL whatever the row holds. */
    boolean isNull() {
        return residual == null && value == null;
    }

    /** Whether the expression is the number zero whatever the row holds. */
    boolean isZero() {
        if (residual != null || !(value instanceof Number)) {
            return false;
        }
        return value instanceof BigDecimal ? ((BigDecimal) value).signum() == 0
            : ((Number) value).doubleValue() == 0.0d;
    }

    /** Whether the expression is a constant whose value is known. */
    boolean isValued() {
        return residual == null && value != UNVALUED;
    }

    /** Whether the expression is TRUE whatever the row holds; a NULL is not. */
    boolean isTrue() {
        return residual == null && Boolean.TRUE.equals(value);
    }

    ParserRuleContext residual() {
        return residual;
    }
}
