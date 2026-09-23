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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantUndefined;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

/** ARRAY_TO_STRING(array, delimiter) — joins array elements with a delimiter. */
public class ArrayToString extends VariantAccessorFunction {
    public ArrayToString() { super("ARRAY_TO_STRING", StringType.VARCHAR); }

    /**
     * The ARRAY position takes a plain ARRAY and refuses a STRUCTURED one. Live,
     * {@code ARRAY_TO_STRING(a, ',')} joins to {@code 1,2} while {@code ARRAY_TO_STRING(sa, ',')} is
     * "Invalid argument types for function 'ARRAY_TO_STRING': (ARRAY(NUMBER(38,0)), VARCHAR(1))" — and
     * a MAP is refused there too. Only position 0 is declared: what a structured value does in the
     * DELIMITER position has not been measured, and undeclared means unconstrained.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        // The account's own sentence names the conversion rather than the function.
        final ArrayNode arr = ArrayFunctionHelper.requireArray(args.get(0), "Left argument of string is not an array");
        final String delimiter = args.get(1).toString();
        final List<String> parts = new ArrayList<>();
        for (final JsonNode el : arr) {
            if (VariantUndefined.isUndefined(el)) {
                // An `undefined` element is ABSENT, so it contributes an empty segment and keeps its
                // separators (live-verified: ARRAY_TO_STRING(ARRAY_CONSTRUCT(1,NULL,2),'|') = '1||2').
                parts.add("");
                continue;
            }
            if (el.isNull()) {
                // A JSON null is a VALUE of type NULL_VALUE, and Snowflake has no string cast for it —
                // the whole call fails (live-verified: ARRAY_TO_STRING(PARSE_JSON('[1,null,2]'),'|')).
                // Only a TOP-LEVEL element is cast: a null nested inside an object or a nested array
                // rides along inside that element's JSON text, so `[{"a":null}]` and `[[null]]` join fine.
                throw new RuntimeException("Failed to cast variant value from array to string");
            }
            parts.add(el.isTextual() ? el.asText() : el.toString());
        }
        return String.join(delimiter, parts);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * A predicate as ARRAY_TO_STRING's separator is refused by the argument types, where a BOOLEAN
     * value is read as text (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
