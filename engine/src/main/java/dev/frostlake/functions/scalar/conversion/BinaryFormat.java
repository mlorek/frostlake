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

package dev.frostlake.functions.scalar.conversion;

/**
 * The decoding format of {@code TO_BINARY(string [, format])}: hex digits ({@link #HEX}, the default), base64
 * ({@link #BASE64}), or the string's UTF-8 bytes ({@link #UTF8}).
 */
enum BinaryFormat {
    HEX, BASE64, UTF8;

    /** Parse a format name case-insensitively (accepting both {@code UTF-8} and {@code UTF8}); null if unrecognized. */
    static BinaryFormat fromString(final String value) {
        if (value == null) {
            return null;
        }
        switch (value.trim().toUpperCase()) {
            case "HEX": return HEX;
            case "BASE64": return BASE64;
            case "UTF-8": case "UTF8": return UTF8;
            default: return null;
        }
    }
}
