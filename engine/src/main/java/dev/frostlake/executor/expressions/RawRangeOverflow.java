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

import dev.frostlake.executor.NumericRangeRefusal;

import java.math.BigDecimal;

/**
 * An exact arithmetic step ran out of the 128-bit carrier, and the sentence still needs the refused
 * operand's NULLABILITY — which only the evaluator holding the expression tree can resolve. The
 * value-level arithmetic throws this with a complete DEFAULT message (the {@code &#123;nullable&#125;}
 * spelling, the common case), so any caller that cannot resolve nullability still reports the full
 * live sentence; the expression evaluator catches it and rebuilds via {@link #messageWith} when it can
 * prove the operand not-null (a literal, or a column declared NOT NULL).
 *
 * <p>The two shapes carried here render their value differently (live-verified): a RESCALE refusal
 * prints the operand's own digits PLAIN, while a QUOTIENT refusal prints the quotient as a DOUBLE in
 * the six-significant-digit form even when its digits would fit the carrier.
 */
public class RawRangeOverflow extends RuntimeException {

    private final RawOverflowKind kind;
    private final BigDecimal shownValue;
    private final int typeScale;

    public RawRangeOverflow(final RawOverflowKind kind, final BigDecimal shownValue, final int typeScale) {
        super(defaultMessage(kind, shownValue, typeScale));
        this.kind = kind;
        this.shownValue = shownValue;
        this.typeScale = typeScale;
    }

    private static String defaultMessage(final RawOverflowKind kind, final BigDecimal shownValue,
                                         final int typeScale) {
        return message(kind, shownValue, typeScale, true);
    }

    private static String message(final RawOverflowKind kind, final BigDecimal shownValue,
                                  final int typeScale, final boolean nullable) {
        if (kind == RawOverflowKind.QUOTIENT) {
            return NumericRangeRefusal.typedDouble("SB16", 38, typeScale, nullable, shownValue);
        }
        return NumericRangeRefusal.typed("SB16", 38, typeScale, nullable, shownValue);
    }

    /** Which step overflowed, so the catcher knows WHOSE nullability the sentence takes. */
    public RawOverflowKind getKind() {
        return kind;
    }

    /** The full sentence with the operand's resolved nullability. */
    public String messageWith(final boolean nullable) {
        return message(kind, shownValue, typeScale, nullable);
    }
}
