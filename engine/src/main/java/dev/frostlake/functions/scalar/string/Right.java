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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.executor.expressions.ImpliedTextNumber;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.util.Arrays;
import java.util.List;

/** RIGHT(expr, n) — the trailing n characters of a VARCHAR, or the trailing n BYTES of a BINARY. */
public class Right extends TextArgumentFunction {
    public Right() {
        super("RIGHT", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        // A text count is read as the number it spells, and refused on the row where it spells none.
        final Object count = args.get(1);
        final int length = count instanceof CharSequence
            ? ImpliedTextNumber.read((CharSequence) count).intValue() : ((Number) count).intValue();
        if (value instanceof BinaryValue) {
            final byte[] bytes = ((BinaryValue) value).bytes();
            if (length < 0) return BinaryValue.of(new byte[0]);
            if (length >= bytes.length) return value;
            return BinaryValue.of(Arrays.copyOfRange(bytes, bytes.length - length, bytes.length));
        }
        final String str = value.toString();
        if (length < 0) return "";
        final int characters = CodePointText.length(str);
        if (length >= characters) return str;
        return CodePointText.slice(str, characters - length, characters);
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * A BOOLEAN count is refused by the argument types, as RIGHT2 (live-verified); a BOOLEAN text reads as its
     * text.
     */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
