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

/**
 * The structured {@code MAP(keyType, valueType)} type. Object-backed at runtime but a type of its
 * OWN — it is deliberately NOT an {@link ObjectType} subclass, because the MAP-strict argument
 * family rejects an {@code ObjectType} argument and a MAP must stay accepted there.
 *
 * <p>Snowflake groups MAP with structured OBJECT for cast purposes (both are {@code
 * STRUCTURED_OBJECT}): live, {@code CAST(<OBJECT(x VARCHAR)> AS MAP(VARCHAR,VARCHAR)
 * RENAME FIELDS)} succeeds while {@code CAST(<ARRAY(INT)> AS OBJECT(x INT) RENAME FIELDS)} fails
 * "incompatible types: [STRUCTURED_ARRAY] and [STRUCTURED_OBJECT]".
 */
public class MapType extends DataType {

    private final DataType keyType;
    private final DataType valueType;

    public MapType(final DataType keyType, final DataType valueType) {
        super("MAP", TypeCategory.SEMI_STRUCTURED);
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public DataType getKeyType() {
        return keyType;
    }

    public DataType getValueType() {
        return valueType;
    }

    @Override
    public Object parseValue(final String value) {
        return ObjectType.OBJECT.parseValue(value);
    }

    @Override
    public String formatValue(final Object value) {
        return ObjectType.OBJECT.formatValue(value);
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof MapType || other instanceof ObjectType || other instanceof VariantType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return isCompatible(other) ? this : null;
    }

    @Override
    public int getSize() {
        return -1;   // variable size
    }

    @Override
    public String toString() {
        return StructuredTypes.describe(this);
    }
}
