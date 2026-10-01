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
import dev.frostlake.functions.aggregate.HllSketch;
import dev.frostlake.functions.aggregate.HllStates;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ObjectType;

import java.util.List;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code HLL_EXPORT(state)} — a HyperLogLog state as an OBJECT, which is the form a state travels in:
 * {@code {"precision": p, "sparse": {"indices": […], "maxLzCounts": […]}, "version": 4}} while few
 * registers are set, and {@code {"dense": […], "precision": p, "version": 4}} once many are.
 * {@code HLL_IMPORT} reads either back.
 */
public class HllExport extends BuiltInFunction {

    /** Registers the function as {@code HLL_EXPORT} returning OBJECT. */
    public HllExport() {
        super("HLL_EXPORT", ObjectType.OBJECT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object state = args.get(0);
        if (state == null) {
            return null;
        }
        final HllSketch sketch = HllStates.sketchOf(state);
        final ObjectNode exported = ArrayFunctionHelper.MAPPER.createObjectNode();
        if (sketch == null) {
            exported.put("precision", dev.frostlake.functions.aggregate.HllAccumulator.precision());
            final ObjectNode sparse = exported.putObject("sparse");
            sparse.putArray("indices");
            sparse.putArray("maxLzCounts");
            exported.put("version", HllSketch.VERSION);
            return ArrayFunctionHelper.toCanonicalVariant(exported);
        }
        final byte[] registers = sketch.registers();
        if (sketch.isSparse()) {
            exported.put("precision", sketch.precision());
            final ObjectNode sparse = exported.putObject("sparse");
            final ArrayNode indices = sparse.putArray("indices");
            final ArrayNode counts = sparse.putArray("maxLzCounts");
            for (int index = 0; index < registers.length; index++) {
                if (registers[index] != 0) {
                    indices.add(index);
                    counts.add(registers[index]);
                }
            }
        } else {
            final ArrayNode dense = exported.putArray("dense");
            for (final byte register : registers) {
                dense.add(register);
            }
            exported.put("precision", sketch.precision());
        }
        exported.put("version", HllSketch.VERSION);
        return ArrayFunctionHelper.toCanonicalVariant(exported);
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
