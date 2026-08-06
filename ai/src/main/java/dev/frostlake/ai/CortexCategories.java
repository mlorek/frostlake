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

import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the category-list argument the classifying functions take. Snowflake is given an ARRAY, which
 * reaches a function here either as the engine's own list or as a semi-structured value, so both are
 * accepted rather than only the shape one call site happens to produce.
 */
final class CortexCategories {

    private CortexCategories() {
    }

    static List<String> of(final Object argument) {
        final List<String> categories = new ArrayList<>();
        if (argument == null) {
            return categories;
        }
        if (argument instanceof List) {
            for (final Object element : (List<?>) argument) {
                if (element != null) {
                    categories.add(String.valueOf(element));
                }
            }
            return categories;
        }
        final JsonNode node = argument instanceof VariantValue
            ? ((VariantValue) argument).node()
            : OllamaClient.json().readTree(String.valueOf(argument));
        if (node != null && node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                categories.add(node.get(i).asString());
            }
        }
        return categories;
    }
}
