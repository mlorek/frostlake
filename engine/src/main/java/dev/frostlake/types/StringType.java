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

public class StringType extends DataType {

    private final int maxLength;

    public StringType(final String name, final int maxLength) {
        super(name, TypeCategory.STRING);
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return value;
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        return "'" + value.toString() + "'";
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.STRING;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof StringType)) {
            return null;
        }
        StringType otherString = (StringType) other;
        int maxLen = Math.max(this.maxLength, otherString.maxLength);
        return new StringType("VARCHAR", maxLen);
    }

    @Override
    public int getSize() {
        return maxLength > 0 ? maxLength : 16777216; // Default max size
    }

    public static StringType VARCHAR = new StringType("VARCHAR", 16777216);
    public static StringType STRING = new StringType("STRING", 16777216);
    public static StringType TEXT = new StringType("TEXT", 16777216);
    public static StringType CHAR = new StringType("CHAR", 1);
}
