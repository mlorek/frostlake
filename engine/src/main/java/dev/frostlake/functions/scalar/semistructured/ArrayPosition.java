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
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.UuidTextNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_POSITION(value, array) — 0-based index of the first element equal to value, or NULL. */
public class ArrayPosition extends BuiltInFunction {
    public ArrayPosition() { super("ARRAY_POSITION", IntegerResultWidths.POSITION); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        final ArrayNode arr = ArrayFunctionHelper.parseArray(args.get(1));
        if (arr == null) return null;
        // A SQL NULL needle looks for the VARIANT `undefined` element — live:
        // ARRAY_POSITION(NULL::VARIANT, ARRAY_CONSTRUCT(1,NULL,2)) is 1, while over
        // PARSE_JSON('[1,null,2]') and over ARRAY_CONSTRUCT(1,2) it is SQL NULL. A JSON null needle
        // matches only a JSON null: ARRAY_POSITION(PARSE_JSON('null'), PARSE_JSON('[1,null,2]')) is 1.
        final JsonNode target = ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, value);
        for (int i = 0; i < arr.size(); i++) {
            final JsonNode el = arr.get(i);
            if (ArrayFunctionHelper.nodesEqual(el, target)) return (long) i;
            if (value != null && el.isTextual() && UuidTextNode.holds(el) == UuidTextNode.holds(target)
                    && el.asText().equals(value.toString())) return (long) i;
        }
        return null;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
