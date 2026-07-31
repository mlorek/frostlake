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

import dev.frostlake.values.VectorValue;

/**
 * Snowflake's {@code VECTOR(element_type, dimension)} type; values are {@link VectorValue}.
 *
 * <p>The ELEMENT TYPE and the DIMENSION are both part of the type's identity, and Snowflake checks
 * them at COMPILE time: live-verified,
 * {@code VECTOR_COSINE_SIMILARITY([1,2]::VECTOR(FLOAT,2), [1,2,3]::VECTOR(FLOAT,3))} fails
 * "Invalid argument types for function 'VECTOR_COSINE_SIMILARITY': (VECTOR(FLOAT, 2),
 * VECTOR(FLOAT, 3))", as does mixing {@code VECTOR(FLOAT,3)} with {@code VECTOR(INT,3)}. That is why
 * {@link #getName()} renders the full parameterization — the error quotes it verbatim — and why
 * {@link #equals} compares both parameters.
 */
public class VectorType extends DataType {

    private final VectorElementType elementType;
    private final int dimension;

    public VectorType(final VectorElementType elementType, final int dimension) {
        super("VECTOR", TypeCategory.VECTOR);
        this.elementType = elementType;
        this.dimension = dimension;
    }

    public VectorElementType getElementType() {
        return elementType;
    }

    public int getDimension() {
        return dimension;
    }

    /** {@code VECTOR(FLOAT, 3)} — the exact spelling Snowflake uses in argument-type errors. */
    @Override
    public String getName() {
        return "VECTOR(" + elementType.name() + ", " + dimension + ")";
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return VectorValue.cast(value, this);
    }

    @Override
    public String formatValue(final Object value) {
        return value == null ? "NULL" : value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return equals(other);
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return isCompatible(other) ? this : null;
    }

    @Override
    public int getSize() {
        return dimension * 4;   // both element types are 32-bit
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VectorType)) {
            return false;
        }
        final VectorType other = (VectorType) obj;
        return other.elementType == this.elementType && other.dimension == this.dimension;
    }

    @Override
    public int hashCode() {
        return elementType.hashCode() * 31 + dimension;
    }
}
