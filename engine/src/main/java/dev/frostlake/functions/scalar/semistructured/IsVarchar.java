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
import dev.frostlake.types.BooleanType;
import dev.frostlake.values.UuidTextNode;
import java.util.List;
import tools.jackson.databind.JsonNode;

public class IsVarchar extends StructuredArgumentFunction {
    public IsVarchar() { super("IS_VARCHAR", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — a SQL NULL input yields NULL, not FALSE
        for (int i = 0; i < 1; i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        if (args.get(0) == null) return false;
        final Object v = args.get(0);
        // Try to parse as JSON first — PARSE_JSON output arrives as a JSON-encoded string
        final JsonNode node = JsonTypeHelper.parse(v);
        // A UUID is a string node only in its JSON text: live, IS_VARCHAR(TO_VARIANT(u)) is FALSE.
        if (node != null) return node.isTextual() && !UuidTextNode.holds(node);
        // If not valid JSON, it's a bare Java String = VARCHAR
        return v instanceof String;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
