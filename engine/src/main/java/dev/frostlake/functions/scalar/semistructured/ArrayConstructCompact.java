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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.ArrayType;

import java.util.ArrayList;
import java.util.List;

/**
 * ARRAY_CONSTRUCT_COMPACT(val1, val2, …) — builds a JSON array from the given values, OMITTING every SQL NULL
 * (so the result can be shorter than the argument list). A VARIANT JSON null is a value, not a SQL NULL, and is
 * therefore kept — matching Snowflake.
 *
 * <p>The surviving values are converted by {@link ArrayConstruct}, so both functions render identically.
 */
public class ArrayConstructCompact extends BuiltInFunction {

    private static final ArrayConstruct CONSTRUCT = new ArrayConstruct();

    public ArrayConstructCompact() {
        super("ARRAY_CONSTRUCT_COMPACT", ArrayType.ARRAY);
    }

    /**
     * The same structured refusal {@link ArrayConstruct} declares, measured for this name too: live
     * {@code ARRAY_CONSTRUCT_COMPACT(o)} builds its array while
     * {@code ARRAY_CONSTRUCT_COMPACT(so)} is "Function ARRAY_CONSTRUCT_COMPACT does not support
     * OBJECT(x VARCHAR(16777216)) argument type". It is declared again rather than inherited because
     * this class delegates to an {@link ArrayConstruct} INSTANCE instead of extending it.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final List<Object> present = new ArrayList<>(args.size());
        for (final Object arg : args) {
            if (arg != null) {
                present.add(arg);
            }
        }
        return CONSTRUCT.evaluate(present);
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
