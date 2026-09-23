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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.BooleanType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ARRAYS_OVERLAP(array1, array2) — returns TRUE if the arrays share at least one element. */
public class ArraysOverlap extends BuiltInFunction {
    public ArraysOverlap() { super("ARRAYS_OVERLAP", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode a1 = ArrayFunctionHelper.parseArray(args.get(0));
        final ArrayNode a2 = ArrayFunctionHelper.parseArray(args.get(1));
        if (a1 == null || a2 == null) return null;
        final Set<String> set = new HashSet<>();
        for (final JsonNode el : a1) set.add(ArrayFunctionHelper.elementKey(el));
        for (final JsonNode el : a2) { if (set.contains(ArrayFunctionHelper.elementKey(el))) return true; }
        return false;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
