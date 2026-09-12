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

import java.math.BigDecimal;

public class NumericType extends DataType {

    private final int precision;
    private final int scale;

    public NumericType(final String name, final int precision, final int scale) {
        super(name, TypeCategory.NUMERIC);
        this.precision = precision;
        this.scale = scale;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return new BigDecimal(value);
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        return value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.NUMERIC;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof NumericType)) {
            return null;
        }
        final NumericType otherNumeric = (NumericType) other;
        final int maxPrecision = Math.max(this.precision, otherNumeric.precision);
        final int maxScale = Math.max(this.scale, otherNumeric.scale);
        return new NumericType("NUMBER", maxPrecision, maxScale);
    }

    @Override
    public int getSize() {
        return 16; // Approximate size for numeric values
    }

    /**
     * Whether a type is one of the APPROXIMATE numeric spellings, which carry no precision and scale.
     *
     * <p>Live-verified on every metadata surface: a FLOAT column is {@code {"type":"REAL"}} in SHOW
     * COLUMNS with no numbers beside it, and leaves NUMERIC_PRECISION and NUMERIC_SCALE NULL in
     * INFORMATION_SCHEMA. The nominal pair these constants carry is an engine-internal placeholder and
     * must not be published to a client as though it were declared.
     *
     * @param type the type to test
     * @return true for FLOAT and its aliases
     */
    public static boolean isApproximate(final DataType type) {
        return type instanceof NumericType && isApproximateName(type.getName());
    }

    /**
     * Whether a type NAME is one of the approximate spellings — for a caller that has the name and not
     * the type, such as a driver reading column metadata off the wire.
     *
     * @param name the declared type's name
     * @return true for FLOAT and its aliases
     */
    public static boolean isApproximateName(final String name) {
        if (name == null) {
            return false;
        }
        final String upper = name.trim().toUpperCase(java.util.Locale.ROOT);
        return upper.equals("FLOAT") || upper.equals("DOUBLE") || upper.equals("REAL")
            || upper.equals("FLOAT4") || upper.equals("FLOAT8") || upper.equals("DOUBLE PRECISION");
    }

    // Snowflake gives EVERY integer alias the same precision and scale — live-verified,
    // INT / INTEGER / BIGINT / SMALLINT / TINYINT / BYTEINT columns all report NUMERIC_PRECISION 38 and
    // NUMERIC_SCALE 0. It keeps no narrower range for SMALLINT (was 5 here) or TINYINT (was 3), which
    // made INFORMATION_SCHEMA.COLUMNS disagree with Snowflake for those two spellings.
    public static NumericType INTEGER = new NumericType("INTEGER", 38, 0);
    public static NumericType BIGINT = new NumericType("BIGINT", 38, 0);
    public static NumericType SMALLINT = new NumericType("SMALLINT", 38, 0);
    public static NumericType TINYINT = new NumericType("TINYINT", 38, 0);
    public static NumericType NUMBER = new NumericType("NUMBER", 38, 0);
    public static NumericType DECIMAL = new NumericType("DECIMAL", 38, 0);
    public static NumericType FLOAT = new NumericType("FLOAT", 38, 9);
    public static NumericType DOUBLE = new NumericType("DOUBLE", 38, 9);
}
