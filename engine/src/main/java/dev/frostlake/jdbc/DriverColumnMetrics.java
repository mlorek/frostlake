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

package dev.frostlake.jdbc;

import java.util.Locale;

/**
 * The precision, scale and display size Snowflake's JDBC driver reports for a result column, by the driver's
 * name for the column's type ({@link JdbcMarshaling#driverTypeName}), so both transports answer alike.
 * Live-verified through the account's driver, JSON and ARROW results agreeing:
 *
 * <pre>
 *   type                               precision   scale   display size
 *   NUMBER(p,s)                        p           s       p + 1, one more when s &gt; 0
 *   DECFLOAT                           38          0       40
 *   DOUBLE (every approximate alias)   0           0       24
 *   DATE                               10          0       10
 *   TIME(f)                            8           f       8
 *   TIMESTAMPNTZ(f)                    23          f       23
 *   TIMESTAMPLTZ(f), TIMESTAMPTZ(f)    29          f       29
 *   BOOLEAN                            0           0       5
 *   VECTOR                             0           0       25
 *   INTERVAL_DAY_TIME, _YEAR_MONTH     0           fields  25
 *   VARCHAR(n), BINARY(n)              n           0       n
 *   VARIANT, OBJECT, ARRAY, GEOGRAPHY  0           0       0
 * </pre>
 *
 * <p>An interval's scale is no fractional precision but the code the driver uses for the fields the interval
 * spans — see {@link dev.frostlake.types.IntervalQualifier}.
 */
final class DriverColumnMetrics {

    private DriverColumnMetrics() {
    }

    /**
     * The precision the driver reports.
     *
     * @param driverTypeName   the driver's name for the column's type
     * @param numericPrecision a fixed-point NUMBER's precision; ignored for any other type
     * @param length           a text or binary column's length, or null for any other type
     * @return the precision
     */
    static int precision(final String driverTypeName, final int numericPrecision, final Integer length) {
        if (length != null) {
            return length.intValue();
        }
        switch (family(driverTypeName)) {
            case "NUMBER":
                return numericPrecision;
            case "DECFLOAT":
                return 38;
            case "DATE":
                return 10;
            case "TIME":
                return 8;
            case "TIMESTAMPNTZ":
                return 23;
            case "TIMESTAMPLTZ":
            case "TIMESTAMPTZ":
                return 29;
            default:
                return 0;
        }
    }

    /**
     * The scale the driver reports: a NUMBER's scale, and the fractional digits of a time or timestamp.
     *
     * @param driverTypeName   the driver's name for the column's type
     * @param numericScale     a fixed-point NUMBER's scale, or an interval's field code; ignored for any other type
     * @param fractionalDigits a time or timestamp's fractional precision; ignored for any other type
     * @return the scale
     */
    static int scale(final String driverTypeName, final int numericScale, final int fractionalDigits) {
        switch (family(driverTypeName)) {
            case "NUMBER":
            case "INTERVAL_DAY_TIME":
            case "INTERVAL_YEAR_MONTH":
                return numericScale;
            case "TIME":
            case "TIMESTAMPNTZ":
            case "TIMESTAMPLTZ":
            case "TIMESTAMPTZ":
                return fractionalDigits;
            default:
                return 0;
        }
    }

    /**
     * The display size the driver reports.
     *
     * @param driverTypeName   the driver's name for the column's type
     * @param numericPrecision a fixed-point NUMBER's precision; ignored for any other type
     * @param numericScale     a fixed-point NUMBER's scale; ignored for any other type
     * @param length           a text or binary column's length, or null for any other type
     * @return the display size
     */
    static int displaySize(final String driverTypeName, final int numericPrecision, final int numericScale,
                           final Integer length) {
        if (length != null) {
            return length.intValue();
        }
        switch (family(driverTypeName)) {
            case "NUMBER":
                // A sign, and a decimal point when there is a fraction.
                return numericPrecision + 1 + (numericScale > 0 ? 1 : 0);
            case "DECFLOAT":
                return 40;
            case "DOUBLE":
                return 24;
            case "BOOLEAN":
                return 5;
            case "VECTOR":
            case "INTERVAL_DAY_TIME":
            case "INTERVAL_YEAR_MONTH":
                return 25;
            default:
                // A date, a time and a timestamp display at their precision; the rest at 0.
                return precision(driverTypeName, numericPrecision, null);
        }
    }

    /**
     * Whether the driver's name for a type is an interval family.
     *
     * @param driverTypeName the driver's name for the column's type
     * @return true for INTERVAL_DAY_TIME and INTERVAL_YEAR_MONTH
     */
    static boolean isInterval(final String driverTypeName) {
        return driverTypeName != null && driverTypeName.toUpperCase(Locale.ROOT).startsWith("INTERVAL");
    }

    /**
     * Whether a column of this type compares case-SENSITIVELY, as {@code ResultSetMetaData} reports it.
     *
     * <p>True for the text and semi-structured families and false for every other, measured through the
     * account's own driver: VARCHAR (which is what CHAR and TEXT report as), VARIANT, OBJECT and ARRAY
     * answer true; BINARY, NUMBER, DOUBLE, BOOLEAN, DATE, TIME, the three TIMESTAMP flavours and both
     * interval types answer false.
     *
     * @param driverTypeName the type as {@code getColumnTypeName} reports it
     * @return what {@code isCaseSensitive} answers for it
     */
    static boolean isCaseSensitive(final String driverTypeName) {
        final String family = family(driverTypeName);
        return "VARCHAR".equals(family) || "CHAR".equals(family) || "TEXT".equals(family)
            || "STRING".equals(family) || "VARIANT".equals(family) || "OBJECT".equals(family)
            || "ARRAY".equals(family);
    }

    /** The type name without any parenthesized parameters, upper-cased: {@code VECTOR(INT, 2)} is VECTOR. */
    private static String family(final String driverTypeName) {
        if (driverTypeName == null) {
            return "";
        }
        final int open = driverTypeName.indexOf('(');
        return (open < 0 ? driverTypeName : driverTypeName.substring(0, open)).trim().toUpperCase(Locale.ROOT);
    }
}
