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

package dev.frostlake.ai;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The JSON texts the object- and array-returning Cortex functions answer with. Built through Jackson
 * rather than by string concatenation so a model reply carrying a quote or a newline stays one value
 * instead of breaking the document around it.
 */
final class CortexJson {

    private CortexJson() {
    }

    /** {@code {"<key>": "<value>"}}. */
    static String object(final String key, final String value) {
        final ObjectNode node = OllamaClient.json().createObjectNode();
        node.put(key, value);
        return node.toString();
    }

    /** {@code {"<key>": ["<v1>", …]}}. */
    static String objectOfArray(final String key, final List<String> values) {
        final ObjectNode node = OllamaClient.json().createObjectNode();
        node.set(key, OllamaClient.arrayOf(values));
        return node.toString();
    }

    /** An array of one-key objects, which is how EXTRACT_ANSWER and ENTITY_SENTIMENT shape a list. */
    static ArrayNode array() {
        return OllamaClient.json().createArrayNode();
    }

    static ObjectNode entry() {
        return OllamaClient.json().createObjectNode();
    }
}
