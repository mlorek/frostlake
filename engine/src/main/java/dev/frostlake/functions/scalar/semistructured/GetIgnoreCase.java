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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** GET_IGNORE_CASE(obj, key) — like GET but matches object keys case-insensitively. */
public class GetIgnoreCase extends BuiltInFunction {
    public GetIgnoreCase() { super("GET_IGNORE_CASE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        String key = args.get(1).toString();
        JsonNode node = ArrayFunctionHelper.parseNode(args.get(0));
        if (node == null || !node.isObject()) return null;
        for (final String name : node.propertyNames()) {
            if (name.equalsIgnoreCase(key)) {
                JsonNode v = node.get(name);
                if (v == null) return null;
                // A key that IS present but holds JSON null keeps the typed VARIANT NULL_VALUE; only a
                // missing key is SQL NULL (live:
                // TYPEOF(GET_IGNORE_CASE(PARSE_JSON('{"b":null}'),'B')) = 'NULL_VALUE').
                // An `undefined` element reads as SQL NULL, a JSON null as the NULL_VALUE variant.
                if (VariantUndefined.isUndefined(v)) return null;
                if (v.isNull()) return VariantValue.of("null");
                if (v.isTextual()) return v.asText();
                if (v.isNumber()) return v.isLong() || v.isInt() ? v.asLong() : v.asDouble();
                if (v.isBoolean()) return v.asBoolean();
                return v.toString();
            }
        }
        return null;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
