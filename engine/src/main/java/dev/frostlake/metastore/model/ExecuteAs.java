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
 * A stored procedure's caller-rights mode: {@link #OWNER} (the default — runs with the owner's privileges),
 * {@link #CALLER} (runs with the caller's privileges) or {@link #RESTRICTED_CALLER}, which runs with the
 * caller's too and is told as {@code RESTRICTED CALLER}.
 */
public enum ExecuteAs {
    OWNER("OWNER"), CALLER("CALLER"), RESTRICTED_CALLER("RESTRICTED CALLER");

    private final String sqlText;

    ExecuteAs(final String sqlText) {
        this.sqlText = sqlText;
    }

    /** The mode as SQL writes it and a description tells it. */
    public String sqlText() {
        return sqlText;
    }

    /** Whether a procedure of this mode runs with its caller's privileges. */
    public boolean isCallersRights() {
        return this != OWNER;
    }

    /** Parse an {@code EXECUTE AS} value case-insensitively; null/unrecognized → the default {@link #OWNER}. */
    public static ExecuteAs fromString(final String value) {
        if (value != null) {
            for (final ExecuteAs mode : values()) {
                if (mode.sqlText.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        return OWNER;
    }
}
