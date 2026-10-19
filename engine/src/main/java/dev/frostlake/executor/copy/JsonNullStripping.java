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

package dev.frostlake.executor.copy;

import dev.frostlake.values.VariantUndefined;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * STRIP_NULL_VALUES = TRUE over a JSON record, at every depth: an object loses each field whose value is null, and a
 * null array element becomes a hole, {@code undefined}, so {@code {"a":1,"b":null,"c":[1,null,2]}} reads
 * {@code {"a":1,"c":[1,undefined,2]}} (live-verified through a staged-file query).
 */
public final class JsonNullStripping {

    private JsonNullStripping() {
    }

    /**
     * Strip a record's null values in place.
     *
     * @param node the record
     */
    public static void strip(final JsonNode node) {
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> nulls = new ArrayList<>();
            for (final Map.Entry<String, JsonNode> field : object.properties()) {
                if (field.getValue().isNull()) {
                    nulls.add(field.getKey());
                } else {
                    strip(field.getValue());
                }
            }
            for (final String name : nulls) {
                object.remove(name);
            }
        } else if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i).isNull()) {
                    array.set(i, VariantUndefined.node());
                } else {
                    strip(array.get(i));
                }
            }
        }
    }
}
