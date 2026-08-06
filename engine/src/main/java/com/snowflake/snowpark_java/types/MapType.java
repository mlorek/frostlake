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

/** Snowpark's {@code Map} type, carrying the key and value types. */
public class MapType extends DataType {

    private final DataType keyType;
    private final DataType valueType;

    public MapType(final DataType keyType, final DataType valueType) {
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
    public String sqlTypeName() {
        return "OBJECT";
    }

    @Override
    public String typeName() {
        return "Map[" + keyType.typeName() + ", " + valueType.typeName() + "]";
    }
}
