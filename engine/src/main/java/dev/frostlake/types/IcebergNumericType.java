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

/**
 * One of the Iceberg format's own numbers, which a column definition of an Iceberg table types its column with
 * (live-verified): the int of INT and INTEGER and the long of BIGINT, which report NUMBER(10,0) and NUMBER(19,0) and
 * hold what 32 and 64 bits do, and the float of FLOAT, FLOAT4 and FLOAT8 and the double of DOUBLE, DOUBLE PRECISION
 * and REAL, which both report FLOAT, a float holding a 32-bit value. The type carries the name of the NUMBER or the
 * FLOAT it reports, so everything that reads a type reads it as that one; only an Iceberg table's own rules ask which
 * of the format's numbers it is.
 */
public final class IcebergNumericType extends NumericType {

    private static final long serialVersionUID = 1L;

    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String FLOAT = "float";
    private static final String DOUBLE = "double";

    private final String formatName;

    private IcebergNumericType(final String formatName, final String name, final int precision, final int scale) {
        super(name, precision, scale);
        this.formatName = formatName;
    }

    /**
     * The int.
     *
     * @return a NUMBER(10,0) holding 32 bits
     */
    public static IcebergNumericType integer() {
        return new IcebergNumericType(INT, "NUMBER", 10, 0);
    }

    /**
     * The long.
     *
     * @return a NUMBER(19,0) holding 64 bits
     */
    public static IcebergNumericType longInteger() {
        return new IcebergNumericType(LONG, "NUMBER", 19, 0);
    }

    /**
     * The float.
     *
     * @return a FLOAT holding 32 bits
     */
    public static IcebergNumericType singleFloat() {
        return new IcebergNumericType(FLOAT, "FLOAT", 38, 9);
    }

    /**
     * The double.
     *
     * @return a FLOAT holding 64 bits
     */
    public static IcebergNumericType doubleFloat() {
        return new IcebergNumericType(DOUBLE, "FLOAT", 38, 9);
    }

    /**
     * The format's name for the type.
     *
     * @return int, long, float or double
     */
    public String formatName() {
        return formatName;
    }

    /**
     * Whether the type is a whole number.
     *
     * @return true for an int and a long
     */
    public boolean isWhole() {
        return INT.equals(formatName) || LONG.equals(formatName);
    }

    /**
     * Whether the type is the 32-bit float.
     *
     * @return true for a float
     */
    public boolean isSingle() {
        return FLOAT.equals(formatName);
    }

    /**
     * Whether a whole number is one the type holds.
     *
     * @param value the number
     * @return true when it fits the type's bits
     */
    public boolean holds(final BigDecimal value) {
        if (INT.equals(formatName)) {
            return value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0
                && value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        }
        if (LONG.equals(formatName)) {
            return value.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0
                && value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0;
        }
        return true;
    }
}
