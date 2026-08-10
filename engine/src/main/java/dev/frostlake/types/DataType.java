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

import java.io.Serializable;

public abstract class DataType implements Serializable {

    private final String name;
    private final TypeCategory category;

    protected DataType(final String name, final TypeCategory category) {
        this.name = name;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public TypeCategory getCategory() {
        return category;
    }

    public abstract Object parseValue(final String value);
    public abstract String formatValue(final Object value);
    public abstract boolean isCompatible(final DataType other);
    public abstract DataType getCommonType(final DataType other);
    public abstract int getSize();

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final DataType dataType = (DataType) obj;
        return name.equals(dataType.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
