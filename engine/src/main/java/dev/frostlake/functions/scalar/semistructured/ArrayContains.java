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
import dev.frostlake.values.UuidTextNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_CONTAINS(value, array) — returns TRUE if the array contains the value. */
public class ArrayContains extends BuiltInFunction {
    public ArrayContains() { super("ARRAY_CONTAINS", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: a NULL ARRAY yields NULL, not FALSE.
        final Object value = args.get(0);
        if (args.get(1) == null) return null;
        final ArrayNode arr = ArrayFunctionHelper.parseArray(args.get(1));
        if (arr == null) return false;
        // A SQL NULL needle looks for the VARIANT `undefined` element — and only for it. Live-verified
        // ARRAY_CONTAINS(NULL::VARIANT, ARRAY_CONSTRUCT(1,NULL,2)) is TRUE, over
        // PARSE_JSON('[1,null,2]') it is SQL NULL and over ARRAY_CONSTRUCT(1,2) it is SQL NULL too — so a
        // needle that is not found propagates the SQL NULL instead of returning FALSE. Symmetrically a JSON
        // null needle matches only a JSON null: ARRAY_CONTAINS(PARSE_JSON('null'), ac) is FALSE.
        final JsonNode target = ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, value);
        for (final JsonNode el : arr) {
            if (ArrayFunctionHelper.nodesEqual(el, target)) return true;
            // Also compare as string for text values
            if (value != null && el.isTextual() && UuidTextNode.holds(el) == UuidTextNode.holds(target)
                    && el.asText().equals(value.toString())) return true;
        }
        return value == null ? null : Boolean.FALSE;
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
