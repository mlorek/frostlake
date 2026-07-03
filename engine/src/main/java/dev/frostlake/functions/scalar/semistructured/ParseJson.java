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
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class ParseJson extends BuiltInFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParseJson() { super("PARSE_JSON", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        String input = args.get(0).toString().trim();
        // A literal JSON null parses to a JSON null VARIANT — represented as the text "null" so it stays
        // DISTINCT from a SQL NULL (Snowflake: "The JSON null value is distinct from the SQL NULL value").
        if (input.equalsIgnoreCase("null")) return "null";
        try {
            // Validate and normalise via Jackson, then return as compact JSON string
            JsonNode node = MAPPER.readTree(input);
            return node.toString();
        } catch (final Exception e) {
            // Snowflake is lenient with \' inside JSON strings — replace and retry
            String relaxed = input.replace("\\'", "'");
            if (!relaxed.equals(input)) {
                try {
                    JsonNode node = MAPPER.readTree(relaxed);
                    return node.toString();
                } catch (final Exception ignored) {}
            }
            throw new RuntimeException("Invalid JSON: " + args.get(0));
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
