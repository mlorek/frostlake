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
 * A stored procedure's caller-rights mode: {@link #OWNER} (the default — runs with the owner's privileges) or
 * {@link #CALLER} (runs with the caller's privileges). The enum name is the canonical SQL spelling.
 */
public enum ExecuteAs {
    OWNER, CALLER;

    /** Parse an {@code EXECUTE AS} value case-insensitively; null/unrecognized → the default {@link #OWNER}. */
    public static ExecuteAs fromString(final String value) {
        if (value != null && value.trim().equalsIgnoreCase("CALLER")) {
            return CALLER;
        }
        return OWNER;
    }
}
