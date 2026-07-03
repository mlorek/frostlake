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

package com.snowflake.snowpark_java.types;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Stub implementation of Snowflake Snowpark Variant type for inline Java procedures.
 */
public class Variant {

    private final JsonNode value;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Variant(final String json) {
        try {
            this.value = MAPPER.readTree(json);
        } catch (final JacksonException e) {
            throw new RuntimeException("Invalid JSON: " + json, e);
        }
    }

    public Variant(final Map<?, ?> map) {
        this.value = MAPPER.valueToTree(map);
    }

    public Variant(final Number number) {
        try {
            this.value = MAPPER.readTree(number.toString());
        } catch (final JacksonException e) {
            throw new RuntimeException("Invalid number: " + number, e);
        }
    }

    public Variant(final Boolean bool) {
        this.value = MAPPER.getNodeFactory().booleanNode(bool);
    }

    public JsonNode asJsonNode() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
