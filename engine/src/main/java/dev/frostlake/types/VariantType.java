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

package dev.frostlake.types;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class VariantType extends DataType {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public VariantType() {
        super("VARIANT", TypeCategory.SEMI_STRUCTURED);
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            return node.toString();
        } catch (final Exception e) {
            return value;
        }
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        if (value instanceof JsonNode) {
            return value.toString();
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (final Exception e) {
            return value.toString();
        }
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return true; // VARIANT can hold any type
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return this; // VARIANT is the universal type
    }

    @Override
    public int getSize() {
        return -1; // Variable size
    }

    public static VariantType VARIANT = new VariantType();
}
