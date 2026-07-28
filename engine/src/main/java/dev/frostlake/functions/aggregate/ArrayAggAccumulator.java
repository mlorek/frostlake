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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;

import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@code ARRAY_AGG(expr)}. SQL NULL inputs are SKIPPED (Snowflake: ARRAY_AGG ignores
 * NULLs, so an all-NULL group yields {@code []}) — keeping them produced {@code [null]} where a loader's
 * {@code ARRAY_AGG(IFF(cond, obj, NULL))} idiom expects an empty array. A VARIANT JSON null (the text
 * {@code "null"}) is a value, not a SQL NULL, and is kept.
 */
public class ArrayAggAccumulator implements AggregateFunction.Accumulator {

    private final List<Object> values = new ArrayList<>();

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        values.add(value);
    }

    @Override
    public Object getResult() {
        // Render as canonical JSON text, the engine's representation for every ARRAY value (what
        // ARRAY_CONSTRUCT and PARSE_JSON produce). Returning the raw ArrayList leaked java.time objects
        // into the value domain — a timestamp element then rendered as 2024-11-26T04:43:38.604 instead of
        // Snowflake's space+FF3 form, and the List's toString wasn't even valid JSON.
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final Object value : values) {
            array.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        }
        return ArrayFunctionHelper.toCanonicalJson(array);
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((ArrayAggAccumulator) other).values);
    }
}
