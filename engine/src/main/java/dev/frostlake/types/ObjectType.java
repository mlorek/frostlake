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

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class ObjectType extends DataType {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ObjectType() {
        super("OBJECT", TypeCategory.SEMI_STRUCTURED);
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        try {
            return MAPPER.readValue(value, Map.class);
        } catch (final Exception e) {
            return value;
        }
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        try {
            return MAPPER.writeValueAsString(value);
        } catch (final Exception e) {
            return value.toString();
        }
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof ObjectType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof ObjectType)) {
            return null;
        }
        return this;
    }

    @Override
    public int getSize() {
        return -1; // Variable size
    }

    public static ObjectType OBJECT = new ObjectType();
}
