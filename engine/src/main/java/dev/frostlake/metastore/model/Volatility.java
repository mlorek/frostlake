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

package dev.frostlake.metastore.model;

/**
 * A UDF's volatility: {@link #VOLATILE} (the default — may return different results across calls) or
 * {@link #IMMUTABLE} (a pure function of its arguments). The enum name is the canonical SQL spelling.
 */
public enum Volatility {
    VOLATILE, IMMUTABLE;

    /** Parse a clause value case-insensitively; null/unrecognized → the default {@link #VOLATILE}. */
    public static Volatility fromString(final String value) {
        if (value != null && value.trim().equalsIgnoreCase("IMMUTABLE")) {
            return IMMUTABLE;
        }
        return VOLATILE;
    }
}
