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

package dev.frostlake.functions.scalar.conditional;

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

public class NullIfZero extends BuiltInFunction {
    public NullIfZero() { super("NULLIFZERO", NumericType.NUMBER); }

    /**
     * The argument, or NULL when it reads as zero. A SQL BOOLEAN is read as its number, so FALSE is
     * the zero and TRUE is handed back (live: {@code NULLIFZERO(FALSE)} is NULL, {@code NULLIFZERO(TRUE)}
     * is TRUE). A text is read as a number the way every numeric reader reads one — {@code '0'} is
     * NULL, {@code '5'} comes back as itself — and a text that reads as no number is live's row-time
     * sentence, "Numeric value 'x' is not recognized". A VARIANT holding something else (a JSON
     * true) is not zero and comes back unchanged.
     */
    @Override
    public Object evaluate(final List<Object> args) {
        final Object v = args.get(0);
        if (v == null) return null;
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue() ? v : null;
        }
        final String text = v.toString().trim();
        try {
            if (new BigDecimal(text).compareTo(BigDecimal.ZERO) == 0) return null;
        } catch (final NumberFormatException notANumber) {
            if (v instanceof String) {
                throw new RuntimeException(NumericRangeRefusal.unreadableText(text));
            }
        }
        return v;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
