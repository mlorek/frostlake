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
 * {@link #CALLER} (runs with the caller's privileges) or {@link #RESTRICTED_CALLER}, told as
 * {@code RESTRICTED CALLER}, which runs as its caller with only those of the caller's privileges a caller grant
 * names (documented: "an executable cannot run with a specific privilege unless an administrator expressly allows
 * it"). This engine models no caller grant ({@code GRANT CALLER …}), so such a procedure runs with none of its
 * caller's privileges at all.
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

    /**
     * Whether a procedure of this mode runs as its caller, where it lives and creates: CALLER and RESTRICTED CALLER
     * alike. Which of the caller's privileges a RESTRICTED CALLER body may use is {@link #isRestricted}'s to say.
     */
    public boolean isCallersRights() {
        return this != OWNER;
    }

    /** Whether a procedure of this mode runs with none of its caller's privileges but those caller grants name. */
    public boolean isRestricted() {
        return this == RESTRICTED_CALLER;
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
