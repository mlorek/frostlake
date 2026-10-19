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

import dev.frostlake.functions.ArrayArgumentKind;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_FLATTEN(array) — the elements of an array of arrays in one array, one level only.
 *
 * <p>Every element must itself be an array: a number, a text, an OBJECT or a JSON null among them is
 * "Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'", and so is a VARIANT that
 * holds no array. An element that is a SQL NULL makes the whole answer NULL instead, wherever it stands
 * and whatever stands beside it; a NULL inside one of the inner arrays is an element like any other
 * (live-verified).
 */
public class ArrayFlatten extends BuiltInFunction {
    private static final String NOT_ARRAYS =
        "Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'";

    public ArrayFlatten() { super("ARRAY_FLATTEN", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) {
            throw new RuntimeException(NOT_ARRAYS);
        }
        for (final JsonNode el : src) {
            if (VariantUndefined.isUndefined(el)) {
                return null;
            }
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : src) {
            if (!el.isArray()) {
                throw new RuntimeException(NOT_ARRAYS);
            }
            for (final JsonNode inner : el) {
                result.add(inner);
            }
        }
        return VariantValue.ofNode(result);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        if (position == 0) {
            return ArrayArgumentKind.ARRAY;
        }
        return null;
    }
}
