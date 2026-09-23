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

import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VectorValue;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * GET(object_or_array, field_or_index) — extracts a value by key (object) or index (array).
 * GET_PATH(object, path_string) — navigates a dot/bracket path like 'a.b[0].c'.
 */
public class GetPath extends VariantAccessorFunction {
    private final boolean pathMode;

    public GetPath(final boolean pathMode) {
        super(pathMode ? "GET_PATH" : "GET", VariantType.VARIANT);
        this.pathMode = pathMode;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A VECTOR is no container a path reaches into (live-verified: GET(v, 0) is NULL).
        if (args.get(0) instanceof VectorValue) return null;
        final JsonNode src = ArrayFunctionHelper.parseNode(args.get(0));
        if (src == null || args.get(1) == null) return null;
        final String accessor = args.get(1).toString();
        JsonNode result;
        if (pathMode) {
            result = navigatePath(src, accessor);
        } else if (src.isArray()) {
            try { result = src.get(Integer.parseInt(accessor)); }
            catch (final NumberFormatException e) { result = src.get(accessor); }
        } else {
            result = src.get(accessor);
        }
        // A key that IS present but holds JSON null stays a VARIANT NULL_VALUE — only a MISSING key is
        // SQL NULL (live: TYPEOF(GET(PARSE_JSON('{"b":null}'),'b')) = 'NULL_VALUE', while
        // TYPEOF(GET(PARSE_JSON('{"a":1}'),'zz')) is SQL NULL). Same for an array element: live,
        // TYPEOF(GET(PARSE_JSON('[1,null,2]'),1)) = 'NULL_VALUE' but index 99 is SQL NULL.
        return ArrayFunctionHelper.fromNodeKeepingJsonNull(result);
    }

    private JsonNode navigatePath(JsonNode node, final String path) {
        // Support paths like: a.b.c or a[0].b
        final String[] parts = path.split("(?<=\\])|(?=[\\[.])");
        for (String part : parts) {
            if (node == null) return null;
            part = part.replaceAll("^[\\[.]|\\]$", "").trim();
            if (part.isEmpty()) continue;
            try { node = node.get(Integer.parseInt(part)); }
            catch (final NumberFormatException e) { node = node.get(part); }
        }
        return node;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
