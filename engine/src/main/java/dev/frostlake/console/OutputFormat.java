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

package dev.frostlake.console;

/**
 * The result-rendering format for the console clients, chosen with {@code set output_format <name>}. Replaces a
 * raw {@code String} field previously duplicated across the two clients: {@link #fromString} validates the
 * user-supplied name (returning {@code null} when it is not a known format) and {@link #displayName} renders the
 * lowercase name echoed back to the user.
 */
enum OutputFormat {
    TABLE,
    CSV,
    JSON,
    XML,
    HTML;

    /** Parse a user-supplied format name case-insensitively; returns {@code null} if it is not a known format. */
    static OutputFormat fromString(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /** The lowercase name shown to the user (e.g. {@code "table"}). */
    String displayName() {
        return name().toLowerCase();
    }
}
