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

package dev.frostlake.functions.table;

/**
 * The {@code MODE} argument of the FLATTEN table function: expand object fields ({@link #OBJECT}), array
 * elements ({@link #ARRAY}), or both ({@link #BOTH}, the default).
 */
enum FlattenMode {
    OBJECT, ARRAY, BOTH;

    /** Parse a {@code MODE} argument value case-insensitively; returns {@code null} if it is not a valid mode. */
    static FlattenMode fromString(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
