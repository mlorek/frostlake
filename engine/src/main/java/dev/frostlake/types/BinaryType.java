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

import dev.frostlake.values.BinaryValue;

public class BinaryType extends DataType {

    private final int maxLength;

    public BinaryType(final String name, final int maxLength) {
        super(name, TypeCategory.BINARY);
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
        // BINARY text is hex (an optional 0x prefix is accepted), matching Snowflake's
        // VARCHAR-to-BINARY conversion.
        return BinaryValue.fromHex(value);
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value).toHex();
        }
        return value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.BINARY;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof BinaryType)) {
            return null;
        }
        BinaryType otherBinary = (BinaryType) other;
        int maxLen = Math.max(this.maxLength, otherBinary.maxLength);
        return new BinaryType("VARBINARY", maxLen);
    }

    @Override
    public int getSize() {
        return maxLength > 0 ? maxLength : 8388608; // Default max size (8MB)
    }

    public static BinaryType BINARY = new BinaryType("BINARY", 8388608);
    public static BinaryType VARBINARY = new BinaryType("VARBINARY", 8388608);
}
