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

import dev.frostlake.functions.StructuredArgumentFunction;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.NumericType;
import tools.jackson.databind.JsonNode;

import java.util.List;

public class AsDouble extends StructuredArgumentFunction {
    public AsDouble() { super("AS_DOUBLE", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        Object v = args.get(0);
        if (v instanceof Number) return ((Number) v).doubleValue();
        JsonNode node = JsonTypeHelper.parse(v);
        if (node != null) {
            if (node.isNumber()) return node.asDouble();
            return null;
        }
        try { return Double.parseDouble(v.toString()); } catch (final Exception e) { return null; }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
