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
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;

import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;

import java.util.List;

public class ParseJson extends BuiltInFunction {

    public ParseJson() { super("PARSE_JSON", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        String input = args.get(0).toString().trim();
        // A variant STRING whose content looks like JSON arrives in quoted JSON form (the path-extraction
        // marker). Snowflake's implicit VARIANT→VARCHAR coercion hands PARSE_JSON the raw inner text, so
        // a metadata field holding embedded JSON parses to its object — not to a string of it.
        final String quotedVariantString = JsonTypeHelper.quotedJsonStringText(input);
        if (quotedVariantString != null) {
            final String inner = quotedVariantString.trim();
            if (inner.startsWith("{") || inner.startsWith("[")) {
                input = inner;
            }
        }
        // A literal JSON null parses to a JSON null VARIANT — represented as the text "null" so it stays
        // DISTINCT from a SQL NULL (Snowflake: "The JSON null value is distinct from the SQL NULL value").
        if (input.equalsIgnoreCase("null")) return VariantValue.of("null");
        // Parse leniently (Snowflake tolerates \' and invalid backslash escapes such as a regex \d).
        final JsonNode node = JsonTypeHelper.parseLenient(input);
        // A WHOLE-VALUE `undefined` is SQL NULL — live: PARSE_JSON('undefined') IS NULL is
        // TRUE and its TYPEOF is SQL NULL, while PARSE_JSON('[undefined]') keeps the array element.
        if (VariantUndefined.isUndefined(node)) {
            return null;
        }
        if (node == null) {
            throw new RuntimeException("Invalid JSON: " + args.get(0));
        }
        return ArrayFunctionHelper.toCanonicalVariant(node);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
