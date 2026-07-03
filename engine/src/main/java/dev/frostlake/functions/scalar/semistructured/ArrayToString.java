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
import dev.frostlake.types.StringType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/** ARRAY_TO_STRING(array, delimiter) — joins array elements with a delimiter. */
public class ArrayToString extends BuiltInFunction {
    public ArrayToString() { super("ARRAY_TO_STRING", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode arr = ArrayFunctionHelper.parseArray(args.get(0));
        if (arr == null) return null;
        String delimiter = args.get(1) != null ? args.get(1).toString() : "";
        List<String> parts = new ArrayList<>();
        for (final JsonNode el : arr) {
            if (!el.isNull()) parts.add(el.isTextual() ? el.asText() : el.toString());
        }
        return String.join(delimiter, parts);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
