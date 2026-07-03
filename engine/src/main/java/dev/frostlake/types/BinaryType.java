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

import java.util.Base64;

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
        // Parse hex string or base64 encoded binary data
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return hexStringToBytes(value.substring(2));
        }
        // Assume base64 encoding
        try {
            return Base64.getDecoder().decode(value);
        } catch (final IllegalArgumentException e) {
            // If not valid base64, treat as raw bytes
            return value.getBytes();
        }
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        if (value instanceof byte[]) {
            return "0x" + bytesToHex((byte[]) value);
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

    private byte[] hexStringToBytes(final String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(final byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (final byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    public static BinaryType BINARY = new BinaryType("BINARY", 8388608);
    public static BinaryType VARBINARY = new BinaryType("VARBINARY", 8388608);
}
