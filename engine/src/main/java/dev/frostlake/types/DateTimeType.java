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

import java.time.*;

public class DateTimeType extends DataType {

    private final int precision;
    private final boolean hasTimeZone;

    public DateTimeType(final String name, final int precision, final boolean hasTimeZone) {
        super(name, TypeCategory.DATE_TIME);
        this.precision = precision;
        this.hasTimeZone = hasTimeZone;
    }

    public int getPrecision() {
        return precision;
    }

    public boolean hasTimeZone() {
        return hasTimeZone;
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }

        switch (getName()) {
            case "DATE":
                return LocalDate.parse(value);
            case "TIME":
                return LocalTime.parse(value);
            case "TIMESTAMP":
            case "TIMESTAMP_NTZ":
            case "DATETIME":
                return LocalDateTime.parse(value);
            case "TIMESTAMP_TZ":
            case "TIMESTAMP_LTZ":
                return ZonedDateTime.parse(value);
            default:
                return Instant.parse(value);
        }
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        return "'" + value.toString() + "'";
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.DATE_TIME;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (other.getCategory() != TypeCategory.DATE_TIME) {
            return null;
        }
        return TIMESTAMP_NTZ;
    }

    @Override
    public int getSize() {
        return 8;
    }

    public static DateTimeType DATE = new DateTimeType("DATE", 0, false);
    public static DateTimeType TIME = new DateTimeType("TIME", 9, false);
    public static DateTimeType DATETIME = new DateTimeType("DATETIME", 9, false);
    public static DateTimeType TIMESTAMP_NTZ = new DateTimeType("TIMESTAMP_NTZ", 9, false);
    public static DateTimeType TIMESTAMP_LTZ = new DateTimeType("TIMESTAMP_LTZ", 9, true);
    public static DateTimeType TIMESTAMP_TZ = new DateTimeType("TIMESTAMP_TZ", 9, true);
}
