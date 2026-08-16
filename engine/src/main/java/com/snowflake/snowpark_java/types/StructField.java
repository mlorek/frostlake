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

import java.util.Locale;

/** One column of a {@link StructType}: its name, its type, and whether it admits NULL. */
public class StructField {

    private final String name;
    private final DataType dataType;
    private final boolean nullable;

    public StructField(final String name, final DataType dataType, final boolean nullable) {
        this.name = normalizeName(name);
        this.dataType = dataType;
        this.nullable = nullable;
    }

    /**
     * A field name follows SQL identifier folding: given unquoted it is upper-cased, given already
     * quoted it is kept verbatim, quotes and case included. Live-verified.
     */
    private static String normalizeName(final String given) {
        if (given.length() > 1 && given.charAt(0) == '"' && given.charAt(given.length() - 1) == '"') {
            return given;
        }
        return given.toUpperCase(Locale.ROOT);
    }

    /** Snowpark defaults a field to nullable. */
    public StructField(final String name, final DataType dataType) {
        this(name, dataType, true);
    }

    public String name() {
        return name;
    }

    public DataType dataType() {
        return dataType;
    }

    public boolean nullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return "StructField(" + name + ", " + dataType.shortName() + ", Nullable = " + nullable + ")";
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof StructField)) {
            return false;
        }
        final StructField that = (StructField) other;
        return nullable == that.nullable && name.equals(that.name) && dataType.equals(that.dataType);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + dataType.hashCode();
    }
}
