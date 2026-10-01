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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;

public class Chr extends TextArgumentFunction {
    public Chr() { super("CHR", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final int codePoint = ((Number) args.get(0)).intValue();
        if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) return null;
        return new String(Character.toChars(codePoint));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /** A BOOLEAN code point is refused by the argument types as the call compiles (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
