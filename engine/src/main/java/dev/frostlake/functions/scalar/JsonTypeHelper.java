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

package dev.frostlake.functions.scalar;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared utilities for JSON variant type-checking functions. */
public class JsonTypeHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode parse(final Object value) {
        if (value == null) return null;
        // Already a JsonNode (shouldn't happen but handle defensively)
        if (value instanceof JsonNode) return (JsonNode) value;
        String s = value.toString().trim();
        try { return MAPPER.readTree(s); }
        catch (final Exception e) { return null; }
    }
}
