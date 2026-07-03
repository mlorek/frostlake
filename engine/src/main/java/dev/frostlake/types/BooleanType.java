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

public class BooleanType extends DataType {

    public BooleanType() {
        super("BOOLEAN", TypeCategory.BOOLEAN);
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        return value.toString().toUpperCase();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof BooleanType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return other instanceof BooleanType ? this : null;
    }

    @Override
    public int getSize() {
        return 1;
    }

    public static BooleanType BOOLEAN = new BooleanType();
}
