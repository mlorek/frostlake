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

import dev.frostlake.functions.VariantArgumentFunction;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.StringType;
import dev.frostlake.values.UuidTextNode;
import tools.jackson.databind.JsonNode;

import java.util.List;

public class AsVarchar extends VariantArgumentFunction {
    public AsVarchar() { super("AS_VARCHAR", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final Object v = args.get(0);
        final JsonNode node = JsonTypeHelper.parse(v);
        // Only a text is one: a number, a BOOLEAN, a date or a UUID held in the VARIANT is NULL here, as
        // every cast of the family answers for another kind (live-verified).
        if (node != null) {
            return node.isTextual() && !UuidTextNode.holds(node) ? node.asText() : null;
        }
        return v instanceof String ? v : null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
