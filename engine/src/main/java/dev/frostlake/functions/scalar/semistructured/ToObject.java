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

import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.ObjectType;

import java.util.List;
import java.util.Map;

/**
 * TO_OBJECT(expr) — NULL (or JSON null) returns NULL; an OBJECT (or a VARIANT containing an OBJECT) is
 * returned as an object. Any other, non-object input is an error.
 */
public class ToObject extends BuiltInFunction {
    public ToObject() { super("TO_OBJECT", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object v = args.get(0);
        if (v == null) return null;
        if (v instanceof Map) return v;
        // The conversion is the cast's: a VARIANT string spelling an object reads as it, and anything else is
        // "Failed to cast variant value 1 to OBJECT" (live-verified). Other sources were refused while the
        // statement compiled.
        return ValueCaster.castValue(v, "OBJECT");
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
