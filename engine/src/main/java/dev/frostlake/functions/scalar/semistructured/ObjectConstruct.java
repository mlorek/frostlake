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
import dev.frostlake.types.VariantType;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * OBJECT_CONSTRUCT(k1, v1, k2, v2, ...) — builds an object from key-value pairs.
 * Also supports OBJECT_CONSTRUCT(*) which is handled as no-args → empty object.
 */
public class ObjectConstruct extends BuiltInFunction {
    public ObjectConstruct() { super("OBJECT_CONSTRUCT", VariantType.VARIANT); }

    protected ObjectConstruct(final String name) { super(name, VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        return build(args, false);
    }

    protected static String build(final List<Object> args, final boolean keepNull) {
        ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (int i = 0; i + 1 < args.size(); i += 2) {
            if (args.get(i) == null) continue;
            Object value = args.get(i + 1);
            if (!keepNull && value == null) continue;
            String key = args.get(i).toString();
            obj.set(key, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        }
        return ArrayFunctionHelper.toCanonicalJson(obj);
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
