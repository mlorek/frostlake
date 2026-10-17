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

package dev.frostlake.executor.udf;

import dev.frostlake.metastore.model.Function;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.StringNode;

/**
 * What a guest-language handler answers, as the function's declared result holds it. A handler hands OBJECT and
 * ARRAY values back as JSON text; a function DECLARED to return a semi-structured type hands that text back as a
 * real semi-structured value, so a container embeds it structurally - OBJECT_CONSTRUCT('k', js_array_fn(...)) nests
 * the array instead of quoting its text - and a scalar string under a VARIANT declaration becomes a VARIANT STRING,
 * exactly as the SQL-side cast would make it.
 */
public final class GuestResults {

    /** Static helpers only — never instantiated. */
    private GuestResults() {
    }

    /**
     * The handler's answer as the function's declared result.
     *
     * @param function the function
     * @param result   what its handler answered
     * @return the value
     */
    public static Object declared(final Function function, final Object result) {
        if (!(result instanceof String)) {
            return result;
        }
        final DataType returnType = function.getReturnType();
        if (!(returnType instanceof ArrayType || returnType instanceof ObjectType
                || returnType instanceof VariantType)) {
            return result;
        }
        final String text = ((String) result).trim();
        if (text.startsWith("[") || text.startsWith("{") || text.startsWith("\"")
                || "null".equals(text)) {
            return VariantValue.of(text);
        }
        if (returnType instanceof VariantType) {
            return VariantValue.of(VariantText.canonical(StringNode.valueOf((String) result)));
        }
        return result;
    }
}
