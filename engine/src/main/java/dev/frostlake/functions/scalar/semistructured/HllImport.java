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
import dev.frostlake.functions.aggregate.HllAccumulator;
import dev.frostlake.functions.aggregate.HllSketch;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * {@code HLL_IMPORT(object)} — the BINARY state an exported OBJECT stands for, the other half of
 * {@code HLL_EXPORT}. The object carries its own precision, so a state exported ANYWHERE reads back here
 * and estimates from the registers it declares.
 */
public class HllImport extends BuiltInFunction {

    /** Registers the function as {@code HLL_IMPORT} returning BINARY. */
    public HllImport() {
        super("HLL_IMPORT", BinaryType.UNSIZED);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object exported = args.get(0);
        if (exported == null) {
            return null;
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(exported);
        if (node == null || !node.isObject()) {
            // A text argument is refused by the type rule above, before any value is read; anything else
            // that is no state carries no registers at all.
            return BinaryValue.of(new byte[0]);
        }
        final int precision = node.has("precision") && node.get("precision").isNumber()
            ? node.get("precision").asInt() : HllAccumulator.precision();
        final byte[] registers = new byte[1 << precision];
        final JsonNode dense = node.get("dense");
        if (dense != null && dense.isArray()) {
            for (int i = 0; i < dense.size() && i < registers.length; i++) {
                registers[i] = (byte) dense.get(i).asInt();
            }
            return new HllSketch(precision, registers).toBinary();
        }
        final JsonNode sparse = node.get("sparse");
        if (sparse != null && sparse.isObject()) {
            final JsonNode indices = sparse.get("indices");
            final JsonNode counts = sparse.get("maxLzCounts");
            if (indices != null && indices.isArray() && counts != null && counts.isArray()) {
                for (int i = 0; i < indices.size() && i < counts.size(); i++) {
                    final int index = indices.get(i).asInt();
                    if (index >= 0 && index < registers.length) {
                        registers[index] = (byte) counts.get(i).asInt();
                    }
                }
            }
            return new HllSketch(precision, registers).toBinary();
        }
        return BinaryValue.of(new byte[0]);
    }

    /**
     * A TEXT argument is no exported state and is refused by its type, where the account refuses it:
     * "Invalid argument types for function 'HLL_IMPORT': (VARCHAR(8))", positioned at the call.
     */
    @Override
    public SemiStructuredRejection textRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
