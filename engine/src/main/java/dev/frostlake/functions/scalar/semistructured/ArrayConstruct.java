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
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_CONSTRUCT(val1, val2, ...) — builds a JSON array from the given values.
 */
public class ArrayConstruct extends BuiltInFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArrayConstruct() {
        super("ARRAY_CONSTRUCT", ArrayType.ARRAY);
    }

    /**
     * A plain OBJECT or ARRAY nests happily, a STRUCTURED one does not — and the constructors say so
     * with the ORDERING-aggregate sentence rather than an argument-type list. Live,
     * {@code ARRAY_CONSTRUCT(1, o)} yields {@code [1,{"k":"v1"}]} while {@code ARRAY_CONSTRUCT(so)},
     * {@code ARRAY_CONSTRUCT(1, so)} and {@code ARRAY_CONSTRUCT(so, 1)} are all "Function
     * ARRAY_CONSTRUCT does not support OBJECT(x VARCHAR(16777216)) argument type" — every position,
     * which is why no position is singled out here.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode array = MAPPER.createArrayNode();
        for (final Object arg : args) {
            // A SQL NULL argument becomes the VARIANT `undefined` element, not a JSON null — live
            // ARRAY_CONSTRUCT(1, NULL, 2) is [1,undefined,2] while
            // ARRAY_CONSTRUCT(PARSE_JSON('null')) keeps [null], and the two compare UNEQUAL.
            array.add(ArrayFunctionHelper.toElementNode(MAPPER, arg));
        }
        return VariantValue.ofNode(array);
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
