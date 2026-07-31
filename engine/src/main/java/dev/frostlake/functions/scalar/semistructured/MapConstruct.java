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
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * MAP_CONSTRUCT(key, value [, key, value …]) — a MAP built from alternating key/value arguments. The
 * one member of the family that MAKES a map rather than reading one, so it takes no MAP argument and
 * is not subject to the MAP-requiring strictness rule.
 *
 * <p>Its arguments must PAIR UP: live, an odd count is "not enough arguments for function
 * [MAP_CONSTRUCT], expected 4, got 3" — the count named is the next EVEN one, not the maximum — and
 * fewer than two is the same sentence with the call quoted back.
 *
 * <p>NULL handling was measured over a two-row table so that no all-NULL column could be folded to a
 * NULL literal by the optimiser: a NULL KEY drops the whole pair ({@code MAP_CONSTRUCT(k, v)} is
 * {@code {}} on the row where {@code k} is NULL) while a NULL VALUE is KEPT as a JSON null member
 * ({@code {"a":null}}, which {@code MAP_SIZE} counts as one entry). A duplicate key is a run-time
 * error, "Duplicate field key 'a'", exactly as in {@code MAP_INSERT}.
 */
public class MapConstruct extends BuiltInFunction {

    public MapConstruct() {
        super("MAP_CONSTRUCT", MapFunctionHelper.MAP);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.size() % 2 != 0) {
            throw new RuntimeException("not enough arguments for function [MAP_CONSTRUCT], expected "
                + (args.size() + 1) + ", got " + args.size());
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (int i = 0; i < args.size(); i += 2) {
            final String key = MapFunctionHelper.keyName(args.get(i));
            if (key == null) {
                continue;
            }
            if (result.has(key)) {
                throw new RuntimeException("Duplicate field key '" + key + "'");
            }
            result.set(key, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, args.get(i + 1)));
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return Integer.MAX_VALUE;
    }
}
