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
import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.ExtractionPath;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VectorValue;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * GET(object_or_array, field_or_index) — extracts a value by key (object) or index (array).
 * GET_PATH(object, path_string) — follows an {@link ExtractionPath} such as {@code a.b[0]["c d"]}.
 *
 * <p>A path GET_PATH cannot read is refused when the row is read — {@code Bad compound object's field path
 * name 'a.' in GET_PATH}, the empty path included, over a NULL source too (live-verified). A path written as a
 * literal is judged before that, by the stricter {@link dev.frostlake.functions.scalar.ExtractionPathSyntax}.
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
        if (args.get(1) == null) return null;
        if (src == null) {
            // A path read with the row is judged over a NULL source too: GET_PATH(NULL, p) over p holding 'a.', 'a['
            // or '' is refused (live-verified).
            if (pathMode && ExtractionPath.read(args.get(1).toString()) == null) {
                throw new RuntimeException(ExtractionPath.malformed(args.get(1).toString(), getName()));
            }
            return null;
        }
        final String accessor = args.get(1).toString();
        final JsonNode result;
        if (pathMode) {
            result = navigatePath(src, accessor);
        } else if (src.isArray() && (args.get(1) instanceof Double || args.get(1) instanceof Float)) {
            // A FLOAT index names no element, its NaN and infinities included: GET([1,2,3], 1::FLOAT) is NULL
            // (live-verified). One written out is read as the number it writes before the row is.
            result = null;
        } else if (src.isArray() && args.get(1) instanceof Number) {
            // An index written with a fraction goes to the nearest whole number: GET(arr, 1.6) reads the third
            // element (live-verified).
            result = src.get(wholeNumber(args.get(1)));
        } else if (src.isArray()) {
            // An array is read by a number alone: a text or a VARIANT index names no element, GET([1], '0') and
            // GET([1], TO_VARIANT(0)) are NULL (live-verified).
            result = null;
        } else {
            result = src.get(accessor);
        }
        // A key that IS present but holds JSON null stays a VARIANT NULL_VALUE — only a MISSING key is
        // SQL NULL (live: TYPEOF(GET(PARSE_JSON('{"b":null}'),'b')) = 'NULL_VALUE', while
        // TYPEOF(GET(PARSE_JSON('{"a":1}'),'zz')) is SQL NULL). Same for an array element: live,
        // TYPEOF(GET(PARSE_JSON('[1,null,2]'),1)) = 'NULL_VALUE' but index 99 is SQL NULL.
        return ArrayFunctionHelper.fromNodeKeepingJsonNull(result);
    }

    private JsonNode navigatePath(final JsonNode node, final String text) {
        final ExtractionPath path = ExtractionPath.read(text);
        if (path == null) {
            throw new RuntimeException(ExtractionPath.malformed(text, getName()));
        }
        return path.in(node);
    }

    /**
     * GET's index is a text or a number: a BINARY, an OBJECT or an ARRAY is refused while the statement compiles,
     * {@code Invalid argument types for function 'GET': (ARRAY, BINARY(1))} at the call (live-verified).
     */
    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        return !pathMode && position == 1 ? ArrayArgumentKind.SCALAR : null;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
