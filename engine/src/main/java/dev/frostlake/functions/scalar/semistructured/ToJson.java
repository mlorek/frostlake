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
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

public class ToJson extends StructuredArgumentFunction {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public ToJson() { super("TO_JSON", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        if (value instanceof VariantValue) {
            // TO_JSON of a semi-structured value is its JSON text (a JSON null yields the text null).
            return ((VariantValue) value).text();
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if (!text.isEmpty()) {
                try {
                    // VARIANT/ARRAY/OBJECT values are carried by the engine as JSON text; TO_JSON of
                    // such a value is that JSON re-serialized compactly — Snowflake returns ["a"] for
                    // an ARRAY, never a re-encoded "[\"a\"]" string.
                    return MAPPER.writeValueAsString(MAPPER.readTree(text));
                } catch (final RuntimeException notJson) {
                    // A plain VARCHAR: serialize below as a JSON string.
                }
            }
            try {
                return MAPPER.writeValueAsString(value);
            } catch (final Exception e) {
                return value.toString();
            }
        }
        if (value instanceof Number || value instanceof Boolean
                || value instanceof List || value instanceof Map) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (final Exception e) {
                return value.toString();
            }
        }
        // Temporals and any other scalar: a variant STRING of the value's text form.
        try {
            return MAPPER.writeValueAsString(value.toString());
        } catch (final Exception e) {
            return value.toString();
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
