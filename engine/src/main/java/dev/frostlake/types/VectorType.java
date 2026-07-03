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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Snowflake VECTOR(element_type, dimension) type.
 * Stores a fixed-length array of numeric values (FLOAT or INT).
 * Values are represented as List<Double> or List<Long> at runtime.
 */
public class VectorType extends DataType {

    public enum ElementType { FLOAT, INT }

    private final ElementType elementType;
    private final int dimension;

    public VectorType(final ElementType elementType, final int dimension) {
        super("VECTOR", TypeCategory.VECTOR);
        this.elementType = elementType;
        this.dimension = dimension;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    public String getName() {
        return "VECTOR(" + elementType.name() + ", " + dimension + ")";
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) return null;
        // Parse "[1.0, 2.0, 3.0]" or "1.0:2.0:3.0" notation
        String cleaned = value.trim().replaceAll("^\\[|\\]$", "");
        String[] parts = cleaned.split("[,:]");
        if (elementType == ElementType.INT) {
            List<Long> vec = new ArrayList<>();
            for (final String p : parts) vec.add(Long.parseLong(p.trim()));
            return vec;
        } else {
            List<Double> vec = new ArrayList<>();
            for (final String p : parts) vec.add(Double.parseDouble(p.trim()));
            return vec;
        }
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        if (value instanceof List) return value.toString();
        return value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        if (!(other instanceof VectorType)) return false;
        VectorType o = (VectorType) other;
        return o.elementType == this.elementType && o.dimension == this.dimension;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return isCompatible(other) ? this : null;
    }

    @Override
    public int getSize() {
        return dimension * (elementType == ElementType.INT ? 8 : 8);
    }
}
