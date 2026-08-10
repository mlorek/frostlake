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

/**
 * Base of the Snowpark type hierarchy, as inline Java handlers see it.
 *
 * <p>Snowpark gives every SQL type its own class so a handler can write
 * {@code field.dataType() instanceof StringType}. The stub keeps that shape rather than collapsing the
 * types into one value class, because collapsing them is exactly what stops such a handler compiling.
 */
public abstract class DataType {

    /**
     * The name Snowpark prints for a type: its CLASS name — {@code LongType}, {@code StringType},
     * {@code DecimalType}. Live-verified, including for DECIMAL, whose precision and scale appear only
     * in {@link #toString()} and never here.
     */
    public String typeName() {
        return getClass().getSimpleName();
    }

    /**
     * The short spelling a {@link StructField} prints for this type — {@code Long}, not
     * {@code LongType}, so a field renders as {@code StructField(ID, Long, Nullable = false)}.
     * Internal: real Snowpark publishes no such accessor.
     */
    String shortName() {
        final String simple = getClass().getSimpleName();
        return simple.endsWith("Type") ? simple.substring(0, simple.length() - "Type".length()) : simple;
    }

    @Override
    public String toString() {
        return typeName();
    }

    @Override
    public boolean equals(final Object other) {
        return other != null && getClass() == other.getClass() && typeName().equals(((DataType) other).typeName());
    }

    @Override
    public int hashCode() {
        return typeName().hashCode();
    }
}
