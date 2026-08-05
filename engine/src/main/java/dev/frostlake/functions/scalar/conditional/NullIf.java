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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.VariantType;

import java.util.List;

public class NullIf extends BuiltInFunction {
    public NullIf() { super("NULLIF", VariantType.VARIANT); }

    /**
     * {@code NULLIF} compares its two arguments, and a GEOSPATIAL value does not compare: live
     * {@code NULLIF(g, g)} is "Invalid argument types for function 'NULLIF': (GEOGRAPHY,
     * GEOGRAPHY)" (SQLSTATE 42P13), while {@code COALESCE(g, NULL)} — which chooses without comparing
     * — returns the geo value.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        Object val1 = args.get(0);
        Object val2 = args.get(1);
        if (val1 == null || val2 == null) return val1;
        if (val1.equals(val2)) return null;
        return val1;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
